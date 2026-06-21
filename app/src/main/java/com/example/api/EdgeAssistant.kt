package com.example.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Generic OpenAI-compatible chat client for the Edge "Codespace" assistant.
 *
 * Unlike the fixed Retrofit clients, the endpoint is fully runtime-configurable
 * (base URL, model, key, system prompt) so it can target any free API workspace
 * — Mistral, NVIDIA, or a hosted Dolphin-Mistral endpoint — without rebuilding.
 * No locally installed model is used.
 */
object EdgeAssistant {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json".toMediaType()

    data class Turn(val role: String, val content: String)

    /** POSTs an OpenAI-style chat completion and returns the assistant reply text. */
    suspend fun complete(
        baseUrl: String,
        model: String,
        apiKey: String,
        systemPrompt: String,
        history: List<Turn>
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "[ERR] No API key set for this Edge endpoint. " +
                "Set one with: EDGE KEY <key>  (or switch preset: EDGE PRESET MISTRAL|NVIDIA)"
        }

        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        history.forEach { turn ->
            messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("max_tokens", 1024)
            .put("temperature", 0.4)
            .toString()

        val request = Request.Builder()
            .url(chatCompletionsUrl(baseUrl))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext "[ERR ${resp.code}] ${body.take(400)}"
            }
            parseReply(body)
        }
    }

    private fun parseReply(body: String): String = try {
        JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    } catch (e: Exception) {
        "[ERR] Could not parse response: ${e.message}\n${body.take(400)}"
    }

    /** Accepts either a host base (".../") or a full chat-completions URL. */
    private fun chatCompletionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/v1/") -> trimmed + "chat/completions"
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            trimmed.endsWith("/") -> trimmed + "v1/chat/completions"
            else -> "$trimmed/v1/chat/completions"
        }
    }
}
