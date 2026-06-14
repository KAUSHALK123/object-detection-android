package com.programminghut.realtime_object

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Alia Agent: High-performance intelligence layer powered by Groq (Llama 3.3 70B).
 * Optimized for real-time spatial reasoning and conversational assistance.
 */
class AliaAgent {

    private val TAG = "AliaAgent"
    private val apiKey = "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"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ChatRequest(
        val model: String, 
        val messages: List<ChatMessage>,
        val temperature: Double = 0.3,
        val max_tokens: Int = 150,
        val top_p: Double = 0.9
    )
    data class ChatMessage(val role: String, val content: String)
    data class ChatResponse(val choices: List<Choice>?, val error: ErrorInfo?)
    data class Choice(val message: ChatMessage)
    data class ErrorInfo(val message: String)

    /**
     * Core spatial reasoning engine. Translates raw obstacle data and user intent
     * into natural language instructions.
     */
    suspend fun analyzeScene(
        obstacles: List<NavigationMemory.TrackedObstacle>, 
        roomName: String?, 
        userQuery: String? = null
    ): String = withContext(Dispatchers.IO) {
        
        val contextPrompt = buildSpatialContext(obstacles, roomName)
        
        val systemPrompt = """
            You are Alia, a highly intelligent and empathetic spatial assistant for the visually impaired.
            You are their eyes and their navigator.
            
            CORE PRINCIPLES:
            - ACCURACY: Be precise about object locations and distances.
            - BREVITY: Use maximum 25 words. People need quick info, not long speeches.
            - CLARITY: Use clock positions (e.g., 'Person at 2 o'clock') and simple directions.
            - PRIORITY: Safety first. Flag hazards like cars or stairs immediately.
            - PERSONALITY: Be professional, calm, and reassuring.
        """.trimIndent()

        val userMessage = if (userQuery.isNullOrBlank()) {
            "Describe the scene and tell me where to go. $contextPrompt"
        } else {
            "User asked: \"$userQuery\". Based on this data: $contextPrompt, provide an answer."
        }

        return@withContext callGroq(userMessage, systemPrompt) 
            ?: "I'm detecting a ${obstacles.firstOrNull()?.label ?: "path"} ahead. Please proceed with caution."
    }

    private fun buildSpatialContext(obstacles: List<NavigationMemory.TrackedObstacle>, roomName: String?): String {
        if (obstacles.isEmpty()) return "The area in the ${roomName ?: "current location"} is completely clear."
        
        val desc = obstacles.sortedBy { it.distance }.take(4).joinToString("; ") {
            val clock = calculateClock(it.rect.centerX())
            "${it.label} at $clock o'clock, ${String.format("%.1f", it.distance)} feet"
        }
        return "Current Location: ${roomName ?: "Unknown"}. Detected Objects: $desc."
    }

    private fun calculateClock(centerX: Float): Int {
        return when {
            centerX < 0.15f -> 9
            centerX < 0.35f -> 10
            centerX < 0.45f -> 11
            centerX < 0.55f -> 12
            centerX < 0.65f -> 1
            centerX < 0.85f -> 2
            else -> 3
        }
    }

    suspend fun processVoiceIntent(text: String): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
            You are an intent parser for a blind user's assistant. 
            Extract the destination for a ride-share request.
            - If a destination is clear, output ONLY the name (e.g., 'Starbucks').
            - If they want a ride but no destination is specified, output 'MISSING'.
            - If it's not a ride request, output 'NONE'.
        """.trimIndent()

        return@withContext callGroq(text, systemPrompt)?.trim()?.removeSurrounding("\"") ?: "NONE"
    }

    suspend fun testApiConnection(): Boolean = withContext(Dispatchers.IO) {
        val res = callGroq("Ping", "Respond with 'Online'")
        return@withContext res?.contains("Online", ignoreCase = true) == true
    }

    private fun callGroq(prompt: String, systemPrompt: String): String? {
        val messages = listOf(ChatMessage("system", systemPrompt), ChatMessage("user", prompt))
        val requestBody = ChatRequest(model = "llama-3.3-70b-versatile", messages = messages)
        
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(gson.toJson(requestBody).toRequestBody(jsonMediaType))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                val chatResponse = gson.fromJson(body, ChatResponse::class.java)
                chatResponse.choices?.firstOrNull()?.message?.content?.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq API Error: ${e.message}")
            null
        }
    }
}
