package com.example.ssepoc.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class SseRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES)   // sem timeout de leitura — essencial para SSE
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val api: SseApi = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:3000/") // 10.0.2.2 = localhost no emulador Android
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(SseApi::class.java)

    fun observeEvents(): Flow<SseEvent> = flow {
        val response = api.streamEvents()
        val body = response.body() ?: return@flow

        body.source().use { source ->
            var id: String? = null
            var event: String? = null
            var data: String? = null

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break

                when {
                    line.startsWith("id:")    -> id    = line.removePrefix("id:").trim()
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:")  -> data  = line.removePrefix("data:").trim()
                    line.isEmpty() -> {
                        if (data != null) emit(SseEvent(id = id, event = event, data = data))
                        id = null; event = null; data = null
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
