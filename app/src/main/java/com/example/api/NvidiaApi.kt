package com.example.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@Serializable
data class NvidiaMessage(
    val role: String,
    val content: String
)

@Serializable
data class NvidiaRequest(
    val model: String = "qwen/qwen3-next-80b-a3b-instruct",
    val messages: List<NvidiaMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 1024,
    val temperature: Double = 0.2
)

@Serializable
data class NvidiaChoice(
    val index: Int = 0,
    val message: NvidiaMessage
)

@Serializable
data class NvidiaResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<NvidiaChoice> = emptyList()
)

interface NvidiaApiService {
    @POST("v1/chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: NvidiaRequest
    ): NvidiaResponse
}

object NvidiaRetrofitClient {
    private const val BASE_URL = "https://integrate.api.nvidia.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: NvidiaApiService by lazy {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NvidiaApiService::class.java)
    }
}
