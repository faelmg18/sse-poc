# Server-Sent Events no Android com Kotlin, Coroutines e Retrofit

## O problema que SSE resolve

Imagine que você precisa mostrar notificações em tempo real, um feed de logs, ou o status de um processo longo rodando no servidor. A solução ingênua é fazer polling: o app pergunta ao servidor a cada N segundos "tem novidade?". Funciona, mas é ineficiente — você faz requisições mesmo quando não há nada novo.

**WebSocket** resolve isso, mas é uma conexão bidirecional. Se o app só precisa *receber* dados do servidor (e não enviar), você está carregando uma bazooka para matar um mosquito.

**Server-Sent Events (SSE)** é a bala certa: uma conexão HTTP unidirecional e persistente onde o servidor empurra dados para o cliente quando quiser, sem polling, sem overhead de WebSocket.

---

## Como SSE funciona

O cliente faz uma requisição HTTP comum. O servidor responde com `Content-Type: text/event-stream` e mantém a conexão aberta, enviando mensagens no formato:

```
id: 1
event: update
data: {"mensagem": "olá"}

id: 2
event: update
data: {"mensagem": "mundo"}

```

Cada evento é separado por uma **linha em branco**. Os campos são:
- `id` — identificador do evento (opcional, usado para reconexão)
- `event` — tipo do evento (opcional, default é `message`)
- `data` — o payload (obrigatório)

Simples assim. Nenhum protocolo especial — é HTTP puro.

---

## O servidor (Node.js)

Para a POC, um servidor Express que emite eventos sob demanda via endpoint `/send`:

```javascript
const express = require('express');
const app = express();
app.use(express.json());

const clients = new Set();

app.get('/events', (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders(); // envia os headers imediatamente

  clients.add(res);

  req.on('close', () => {
    clients.delete(res);
  });
});

app.post('/send', (req, res) => {
  const { message } = req.body;
  const payload = JSON.stringify({ message, timestamp: new Date().toISOString() });

  clients.forEach(client => {
    client.write(`event: update\n`);
    client.write(`data: ${payload}\n\n`);
  });

  res.json({ ok: true, clients: clients.size });
});

app.listen(3000);
```

O detalhe importante é `res.flushHeaders()` — sem ele, muitos servidores HTTP esperam ter conteúdo antes de enviar os headers, e o cliente fica travado esperando a resposta começar.

---

## O Android

### Dependências

```kotlin
// app/build.gradle.kts
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
implementation("com.google.code.gson:gson:2.11.0")
implementation(libs.androidx.lifecycle.viewmodel.ktx)
implementation(libs.androidx.lifecycle.runtime.compose)
```

### O modelo de dados

```kotlin
data class SseEvent(
    val id: String?,
    val event: String?,
    val data: String?,
)
```

### A interface Retrofit — e o detalhe que muda tudo

```kotlin
interface SseApi {
    @Streaming          // <-- essencial
    @GET("events")
    suspend fun streamEvents(): Response<ResponseBody>
}
```

A anotação `@Streaming` instrui o Retrofit a **não bufferizar a resposta na memória**. Sem ela, o Retrofit esperaria a resposta HTTP terminar antes de retornar — o que nunca acontece num stream SSE. Com `@Streaming`, você recebe acesso direto ao corpo da resposta enquanto ele chega.

### O OkHttpClient — e o timeout que você precisa desativar

```kotlin
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MINUTES)   // 0 = sem timeout de leitura
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()
```

O `readTimeout` padrão do OkHttp é de 10 segundos — o tempo máximo sem receber bytes. Num stream SSE com eventos espaçados, isso mataria a conexão entre um evento e outro. **Defina como 0 para desativar.**

### O repositório — parsing do stream com Okio

Aqui está o coração da implementação. O `ResponseBody` do OkHttp expõe um `BufferedSource` (Okio), que permite ler o stream linha por linha sem carregar tudo na memória:

```kotlin
fun observeEvents(): Flow<SseEvent> = flow {
    val response = api.streamEvents()
    val body = response.body() ?: return@flow

    body.source().use { source ->
        var id: String? = null
        var event: String? = null
        var data: String? = null

        while (!source.exhausted()) {        // enquanto houver dados
            val line = source.readUtf8Line() ?: break

            when {
                line.startsWith("id:")    -> id    = line.removePrefix("id:").trim()
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:")  -> data  = line.removePrefix("data:").trim()
                line.isEmpty() -> {
                    // linha em branco = evento completo
                    if (data != null) emit(SseEvent(id, event, data))
                    id = null; event = null; data = null
                }
            }
        }
    }
}.flowOn(Dispatchers.IO)
```

`!source.exhausted()` significa "enquanto ainda houver bytes para ler". O loop termina naturalmente quando o servidor fecha a conexão.

### O ViewModel

```kotlin
data class SseUiState(
    val events: List<SseEvent> = emptyList(),
    val isConnected: Boolean = false,
    val error: String? = null,
)

class SseViewModel : ViewModel() {

    private val repository = SseRepository()
    private val _uiState = MutableStateFlow(SseUiState())
    val uiState: StateFlow<SseUiState> = _uiState

    private var streamJob: Job? = null

    fun connect() {
        if (streamJob?.isActive == true) return
        _uiState.value = SseUiState(isConnected = true)

        streamJob = viewModelScope.launch {
            repository.observeEvents()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        isConnected = false,
                        error = e.message
                    )
                }
                .collect { event ->
                    _uiState.value = _uiState.value.copy(
                        events = _uiState.value.events + event
                    )
                }
            _uiState.value = _uiState.value.copy(isConnected = false)
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        _uiState.value = _uiState.value.copy(isConnected = false)
    }
}
```

`streamJob?.cancel()` cancela a coroutine, o que fecha o `BufferedSource` via o bloco `use {}` no repositório — sem leak de conexão.

### A UI em Compose

```kotlin
@Composable
fun SseScreen(vm: SseViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.events.size) {
        if (state.events.isNotEmpty())
            listState.animateScrollToItem(state.events.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.connect() }, enabled = !state.isConnected) {
                Text("Conectar")
            }
            OutlinedButton(onClick = { vm.disconnect() }, enabled = state.isConnected) {
                Text("Desconectar")
            }
        }

        LazyColumn(state = listState) {
            items(state.events) { event ->
                Text(text = event.data ?: "")
            }
        }
    }
}
```

`collectAsStateWithLifecycle()` garante que a coleta para quando o app vai para background.

### UI em XML Fragment

Para quem usa o modelo tradicional de View, a coleta do Flow no Fragment é feita com `repeatOnLifecycle`:

```kotlin
class SseFragment : Fragment() {
    private val vm: SseViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    // atualiza views
                }
            }
        }
    }
}
```

`repeatOnLifecycle(STARTED)` garante que o Flow só é coletado enquanto o Fragment está visível — sem processar eventos em background.

---

## Desserializando o payload como objeto

Se o campo `data` é JSON, você pode desserializar direto com Gson ao invés de lidar com String:

```kotlin
data class CheckoutEvent(
    val id: String?,
    val status: String?,
    val transactionId: String?,
    val paymentInfo: PaymentInfo?,
    // ...
)

// No repositório, ao emitir:
val checkout = gson.fromJson(data, CheckoutEvent::class.java)
emit(checkout)
```

---

## Armadilhas que você vai encontrar

**1. HTTP bloqueado no Android 9+**

Android bloqueia tráfego HTTP por padrão. Para desenvolvimento local, adicione um `network_security_config.xml`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

**2. O emulador não acessa `localhost`**

No emulador Android, `localhost` aponta para o próprio emulador. Para acessar sua máquina, use `10.0.2.2`.

**3. `readTimeout` matando a conexão**

Já mencionado, mas vale reforçar: `readTimeout(0)` é obrigatório. Qualquer valor finito vai encerrar a conexão quando não chegarem bytes dentro do prazo.

**4. Esquecer `@Streaming`**

Sem essa anotação, o Retrofit tenta ler toda a resposta antes de retornar. Com um stream sem fim, isso trava para sempre (ou até dar OOM).

**5. Fragment dentro de Compose perdendo estado ao trocar de aba**

Se você embute um Fragment num `AndroidView` dentro de um `when(selectedTab)`, o Compose destrói e recria o composable a cada troca de aba — destruindo o Fragment e o ViewModel junto. A solução é manter ambas as telas sempre na árvore do Compose, alternando apenas o tamanho:

```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    SseComposeScreen(
        modifier = if (selectedTab == 0) Modifier.fillMaxSize() else Modifier.size(0.dp)
    )
    XmlFragmentContainer(
        modifier = if (selectedTab == 1) Modifier.fillMaxSize() else Modifier.size(0.dp)
    )
}
```

---

## SSE vs WebSocket — quando usar cada um

| | SSE | WebSocket |
|---|---|---|
| Direção | Servidor → Cliente | Bidirecional |
| Protocolo | HTTP puro | Upgrade para WS |
| Reconexão automática | Sim (browser nativo) | Manual |
| Complexidade | Baixa | Média/Alta |
| Caso de uso | Feeds, notificações, status de pagamento | Chat, jogos, colaboração em tempo real |

Se o app só precisa **receber** dados do servidor, SSE é mais simples, mais leve e funciona sobre HTTP/2 naturalmente.

---

## Conclusão

Com menos de 100 linhas de código Kotlin, você tem um stream SSE funcional:

- `@Streaming` no Retrofit para não bufferizar
- `readTimeout(0)` no OkHttp para não matar a conexão
- `BufferedSource` do Okio para ler linha a linha
- `Flow<SseEvent>` para integrar com coroutines naturalmente
- `StateFlow` + ViewModel para um ciclo de vida correto

O código completo está disponível no GitHub: *[link]*
