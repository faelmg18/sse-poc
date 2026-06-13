package com.example.ssepoc.data

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming

interface SseApi {
    // @Streaming impede o Retrofit de bufferizar a resposta inteira na memória,
    // essencial para streams de longa duração.
    @Streaming
    @GET("events")
    suspend fun streamEvents(): Response<ResponseBody>
}
