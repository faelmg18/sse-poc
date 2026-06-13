# Server-Sent Events on Android with Kotlin, Coroutines, and Retrofit

## The problem SSE solves

Imagine you need to show real-time notifications, a log feed, or the status of a long-running server process. The naive solution is polling: the app asks the server every N seconds "anything new?". It works, but it's wasteful — you're making requests even when there's nothing to report.

**WebSocket** solves that, but it's a bidirectional connection. If your app only needs to *receive* data from the server, you're bringing a sledgehammer to crack a nut.

**Server-Sent Events (SSE)** is the right tool: a unidirectional, persistent HTTP connection where the server pushes data to the client whenever it wants — no polling, no WebSocket overhead.

---

## How SSE works

The client makes a plain HTTP request. The server responds with `Content-Type: text/event-stream` and keeps the connection open, sending messages in this format:

```
id: 1
event: update
data: {"message": "hello"}

id: 2
event: update
data: {"message": "world"}

```

Each event is separated by a **blank line**. The fields are:
- `id` — event identifier (optional, used for reconnection)
- `event` — event type (optional, defaults to `message`)
- `data` — the payload (required)

That's it. No special protocol — just plain HTTP.

---

## The server (Node.js)

For the POC, an Express server that emits events on demand via a `/send` endpoint:

```javascript
const express = require('express');
const app = express();
app.use(express.json());

const clients = new Set();

app.get('/events', (req, res) => {
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.flushHeaders(); // send headers immediately

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

The important detail is `res.flushHeaders()` — without it, many HTTP servers buffer the response until there's content, leaving the client hanging.

---

## The Android side

### Dependencies

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

### The data model

```kotlin
data class SseEvent(
    val id: String?,
    val event: String?,
    val data: String?,
)
```

### The Retrofit interface — and the annotation that changes everything

```kotlin
interface SseApi {
    @Streaming          // <-- essential
    @GET("events")
    suspend fun streamEvents(): Response<ResponseBody>
}
```

The `@Streaming` annotation tells Retrofit **not to buffer the entire response body in memory**. Without it, Retrofit would wait for the HTTP response to finish before returning — which never happens with an SSE stream. With `@Streaming`, you get direct access to the response body as it arrives.

### The OkHttpClient — and the timeout you need to disable

```kotlin
private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.MINUTES)   // 0 = no read timeout
    .writeTimeout(10, TimeUnit.SECONDS)
    .build()
```

OkHttp's default `readTimeout` is 10 seconds — the maximum time without receiving bytes. On an SSE stream with infrequent events, that would kill the connection between events. **Set it to 0 to disable it.**

### The repository — stream parsing with Okio

This is the heart of the implementation. OkHttp's `ResponseBody` exposes a `BufferedSource` (Okio), which lets you read the stream line by line without loading everything into memory:

```kotlin
fun observeEvents(): Flow<SseEvent> = flow {
    val response = api.streamEvents()
    val body = response.body() ?: return@flow

    body.source().use { source ->
        var id: String? = null
        var event: String? = null
        var data: String? = null

        while (!source.exhausted()) {        // while there's data to read
            val line = source.readUtf8Line() ?: break

            when {
                line.startsWith("id:")    -> id    = line.removePrefix("id:").trim()
                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                line.startsWith("data:")  -> data  = line.removePrefix("data:").trim()
                line.isEmpty() -> {
                    // blank line = complete event
                    if (data != null) emit(SseEvent(id, event, data))
                    id = null; event = null; data = null
                }
            }
        }
    }
}.flowOn(Dispatchers.IO)
```

`!source.exhausted()` means "while there are still bytes to read". The loop ends naturally when the server closes the connection.

### The ViewModel

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

`streamJob?.cancel()` cancels the coroutine, which closes the `BufferedSource` via the `use {}` block in the repository — no connection leak.

### Compose UI

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
                Text("Connect")
            }
            OutlinedButton(onClick = { vm.disconnect() }, enabled = state.isConnected) {
                Text("Disconnect")
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

`collectAsStateWithLifecycle()` ensures collection stops when the app goes to the background.

### XML Fragment UI

For View-based UI, collect the Flow in a Fragment using `repeatOnLifecycle`:

```kotlin
class SseFragment : Fragment() {
    private val vm: SseViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    // update views
                }
            }
        }
    }
}
```

`repeatOnLifecycle(STARTED)` ensures the Flow is only collected while the Fragment is visible.

---

## Deserializing the payload as an object

If the `data` field is JSON, you can deserialize it directly with Gson instead of handling raw strings:

```kotlin
data class CheckoutEvent(
    val id: String?,
    val status: String?,
    val transactionId: String?,
    val paymentInfo: PaymentInfo?,
    // ...
)

// In the repository, when emitting:
val checkout = gson.fromJson(data, CheckoutEvent::class.java)
emit(checkout)
```

---

## Pitfalls you will run into

**1. HTTP blocked on Android 9+**

Android blocks cleartext HTTP traffic by default. For local development, add a `network_security_config.xml`:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

**2. The emulator can't reach `localhost`**

On the Android emulator, `localhost` points to the emulator itself. To reach your machine, use `10.0.2.2`.

**3. `readTimeout` killing the connection**

Already mentioned, but worth repeating: `readTimeout(0)` is mandatory. Any finite value will drop the connection when no bytes arrive within the deadline — which is exactly the normal behavior of an SSE stream with spaced-out events.

**4. Forgetting `@Streaming`**

Without this annotation, Retrofit tries to read the entire response before returning. With an infinite stream, it will hang forever (or OOM).

**5. Fragment inside Compose losing state on tab switch**

If you embed a Fragment in an `AndroidView` inside a `when(selectedTab)`, Compose destroys and recreates the composable on every tab switch — taking the Fragment and its ViewModel with it. The fix is to keep both screens always in the Compose tree, toggling only their size:

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

## SSE vs WebSocket — when to use each

| | SSE | WebSocket |
|---|---|---|
| Direction | Server → Client | Bidirectional |
| Protocol | Plain HTTP | HTTP Upgrade to WS |
| Auto-reconnect | Yes (native in browsers) | Manual |
| Complexity | Low | Medium/High |
| Use case | Feeds, notifications, payment status | Chat, games, real-time collaboration |

If your app only needs to **receive** data from the server, SSE is simpler, lighter, and works naturally over HTTP/2.

---

## Conclusion

In under 100 lines of Kotlin, you have a working SSE stream:

- `@Streaming` on Retrofit to avoid buffering
- `readTimeout(0)` on OkHttp to keep the connection alive
- `BufferedSource` from Okio to read line by line
- `Flow<SseEvent>` for natural coroutine integration
- `StateFlow` + ViewModel for correct lifecycle handling

The full source code is available on GitHub: *[link]*
