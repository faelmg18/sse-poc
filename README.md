# SSE POC — Android (Retrofit + Coroutines + Flow)

## Estrutura

```
sse-poc/
├── server/          # Servidor SSE em Node.js
└── android-poc/     # App Android com Compose e XML
```

## 1. Rodar o servidor

```bash
cd server
npm install
npm start
# → http://localhost:3000/events
```

O servidor emite 1 evento por segundo durante 50 segundos, depois encerra o stream.

## 2. Rodar o app Android

Abra `android-poc/` no Android Studio, sincronize o Gradle e rode num **emulador**.

> **Dispositivo físico:** troque `10.0.2.2` por o IP da sua máquina na rede local em `SseRepository.kt`.

---

## Como funciona

### Por que `@Streaming`?

Sem essa anotação o Retrofit bufferiza a resposta inteira na memória antes de entregar.
Com SSE o body nunca fecha até o servidor encerrar, então sem `@Streaming` a app trava.

### Por que `readTimeout(0)`?

SSE é uma conexão longa por design. Timeout de leitura zero = sem timeout, a conexão
fica aberta enquanto o servidor continuar enviando.

### Flow + Coroutines

```
SseApi (@Streaming)
  └─► ResponseBody.source()  ← Okio Source (Dispatchers.IO)
        └─► linha a linha → parseia campos id/event/data
              └─► emit(SseEvent) → Flow<SseEvent>
                    └─► ViewModel coleta → StateFlow<UiState>
                          └─► UI observa (Compose ou Fragment)
```

### Cancelamento

`streamJob?.cancel()` no `disconnect()` cancela a coroutine, que fecha o Okio Source,
que encerra a conexão HTTP — sem leaks.

---

## Dependências adicionadas

```kotlin
// Retrofit + OkHttp
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
```
