# SSE POC — Android (Retrofit + Coroutines + Flow)

> Proof of concept for consuming Server-Sent Events (SSE) on Android using Kotlin, Coroutines, Retrofit, and OkHttp. The UI is implemented in both **Jetpack Compose** and **XML Fragment** so both approaches can be compared side by side.
>
> This repository accompanies a Medium article — link coming soon.

---

## Project structure

```
sse-poc/
├── server/          # Node.js SSE server with a web panel to fire events manually
├── android-poc/     # Android app (Kotlin, Coroutines, Retrofit, Compose + XML Fragment)
├── post-en.md       # Medium article draft (English)
└── post-pt.md       # Medium article draft (Portuguese)
```

---

## 1. Running the server

```bash
cd server
npm install
node server.js
```

Open **http://localhost:3000** in your browser to access the control panel. Type a message and click **Send** — the event is broadcast to all connected SSE clients instantly.

The SSE endpoint is available at `http://localhost:3000/events`.

---

## 2. Running the Android app

Open the `android-poc/` folder in **Android Studio**, sync Gradle, and run it on an **emulator**.

> **Physical device:** replace `10.0.2.2` with your machine's local IP address in `SseRepository.kt`. Also make sure both devices are on the same network.

---

## How it works

### Why `@Streaming`?

Without this annotation, Retrofit buffers the entire response body in memory before delivering it. With SSE the body never closes until the server explicitly ends the stream — so without `@Streaming` the app hangs forever waiting for a response that never finishes.

### Why `readTimeout(0)`?

SSE is a long-lived connection by design. A finite read timeout would drop the connection whenever no bytes arrive within the deadline — exactly what happens between events. Setting it to `0` disables the timeout entirely.

### Flow + Coroutines pipeline

```
SseApi (@Streaming)
  └─► ResponseBody.source()     ← Okio BufferedSource (Dispatchers.IO)
        └─► read line by line → parse id / event / data fields
              └─► emit(SseEvent) → Flow<SseEvent>
                    └─► ViewModel collects → StateFlow<UiState>
                          └─► UI observes (Compose or Fragment)
```

### Cancellation

`streamJob?.cancel()` in `disconnect()` cancels the coroutine, which closes the Okio source, which terminates the HTTP connection — no leaks.

---

## Key dependencies

```kotlin
// Retrofit + OkHttp
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

// JSON parsing
implementation("com.google.code.gson:gson:2.11.0")
```

---

## SSE vs WebSocket — quick comparison

| | SSE | WebSocket |
|---|---|---|
| Direction | Server → Client only | Bidirectional |
| Protocol | Plain HTTP | HTTP upgrade to WS |
| Auto-reconnect | Yes (native in browsers) | Manual |
| Complexity | Low | Medium / High |
| Best for | Feeds, notifications, payment status | Chat, games, real-time collaboration |
