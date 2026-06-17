package com.example.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class MistralMessage(
    val role: String,
    val content: String
)

@Serializable
data class MistralRequest(
    val model: String = "mistral-small-latest",
    val messages: List<MistralMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 500,
    val temperature: Double = 0.7
)

@Serializable
data class MistralChoice(
    val index: Int,
    val message: MistralMessage
)

@Serializable
data class MistralResponse(
    val id: String,
    val obj: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<MistralChoice>
)

interface MistralApiService {
    @POST("v1/chat/completions")
    suspend fun generateContent(
        @Header("Authorization") authHeader: String,
        @Body request: MistralRequest
    ): MistralResponse
}

object MistralRetrofitClient {
    private const val BASE_URL = "https://api.mistral.ai/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: MistralApiService by lazy {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(MistralApiService::class.java)
    }
}
