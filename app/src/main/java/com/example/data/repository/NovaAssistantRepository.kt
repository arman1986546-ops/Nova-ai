package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.InlineData
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.api.toBase64
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.data.local.NovaDatabase
import com.example.model.Persona
import com.example.model.PersonaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class NovaAssistantRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    companion object {
        private const val TAG = "NovaRepository"

        fun create(context: Context): NovaAssistantRepository {
            val db = NovaDatabase.getDatabase(context)
            return NovaAssistantRepository(db.conversationDao(), db.messageDao())
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun createNewConversation(title: String = "New Chat", personaId: String = "general"): Long {
        return conversationDao.insertConversation(
            ConversationEntity(
                title = title,
                personaId = personaId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getConversation(id: Long): ConversationEntity? =
        conversationDao.getConversationById(id)

    suspend fun renameConversation(id: Long, newTitle: String) =
        conversationDao.renameConversation(id, newTitle)

    suspend fun deleteConversation(id: Long) =
        conversationDao.deleteConversationById(id)

    suspend fun deleteAllConversations() =
        conversationDao.deleteAllConversations()

    suspend fun saveUserMessage(
        conversationId: Long,
        text: String,
        bitmap: Bitmap?
    ): MessageEntity {
        val base64Image = bitmap?.toBase64()
        val userMsg = MessageEntity(
            conversationId = conversationId,
            role = "user",
            content = text,
            imageBase64 = base64Image,
            timestamp = System.currentTimeMillis()
        )
        val id = messageDao.insertMessage(userMsg)
        // Auto-update conversation title from initial message if default
        val summaryTitle = if (text.length > 32) text.take(30).trim() + "..." else text
        conversationDao.touchConversation(conversationId, System.currentTimeMillis(), summaryTitle)
        return userMsg.copy(id = id)
    }

    suspend fun saveAssistantPlaceholder(conversationId: Long): Long {
        val modelMsg = MessageEntity(
            conversationId = conversationId,
            role = "model",
            content = "",
            timestamp = System.currentTimeMillis()
        )
        return messageDao.insertMessage(modelMsg)
    }

    suspend fun updateAssistantMessage(id: Long, content: String, hasError: Boolean = false) {
        messageDao.updateMessageContent(id, content, hasError)
    }

    /**
     * Streams the generated content response from Gemini API using SSE streaming.
     */
    suspend fun streamGenerateResponse(
        conversationId: Long,
        persona: Persona,
        apiKey: String,
        latestUserBitmap: Bitmap?,
        onChunk: suspend (accumulatedText: String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                val errorMsg = "Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel in AI Studio or enter a key in Settings."
                return@withContext Result.failure(IllegalStateException(errorMsg))
            }

            // Fetch previous history up to 10 messages for context
            val history = messageDao.getMessagesList(conversationId)

            val apiContents = mutableListOf<Content>()
            // Map past turns into Gemini Content format
            for (msg in history.takeLast(12)) {
                if (msg.role == "user") {
                    val parts = mutableListOf<Part>()
                    if (!msg.imageBase64.isNullOrBlank()) {
                        parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = msg.imageBase64)))
                    }
                    if (msg.content.isNotBlank()) {
                        parts.add(Part(text = msg.content))
                    }
                    if (parts.isNotEmpty()) {
                        apiContents.add(Content(role = "user", parts = parts))
                    }
                } else if (msg.role == "model" && msg.content.isNotBlank() && !msg.hasError) {
                    apiContents.add(Content(role = "model", parts = listOf(Part(text = msg.content))))
                }
            }

            if (apiContents.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No prompt contents found"))
            }

            val request = GenerateContentRequest(
                contents = apiContents,
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    topP = 0.95f,
                    topK = 40
                ),
                systemInstruction = Content(
                    parts = listOf(Part(text = persona.systemPrompt))
                )
            )

            val accumulated = StringBuilder()

            try {
                val responseBody = RetrofitClient.service.generateContentStream(apiKey, request)
                responseBody.byteStream().bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val rawLine = line?.trim() ?: continue
                        if (!rawLine.startsWith("data:")) continue
                        val jsonPayload = rawLine.removePrefix("data:").trim()
                        if (jsonPayload.isEmpty() || jsonPayload == "[DONE]") continue

                        try {
                            val chunk = json.parseToJsonElement(jsonPayload).jsonObject
                            val textChunk = chunk["candidates"]?.jsonArray
                                ?.getOrNull(0)?.jsonObject
                                ?.get("content")?.jsonObject
                                ?.get("parts")?.jsonArray
                                ?.getOrNull(0)?.jsonObject
                                ?.get("text")?.jsonPrimitive?.content

                            if (!textChunk.isNullOrEmpty()) {
                                accumulated.append(textChunk)
                                onChunk(accumulated.toString())
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing chunk: ${e.message}")
                        }
                    }
                }
            } catch (streamEx: Exception) {
                Log.w(TAG, "Stream failed, attempting standard generateContent fallback: ${streamEx.message}")
                // Fallback to non-streaming endpoint
                val response = RetrofitClient.service.generateContent(apiKey, request)
                val directText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!directText.isNullOrBlank()) {
                    accumulated.append(directText)
                    onChunk(directText)
                } else {
                    val errMsg = response.error?.message ?: streamEx.localizedMessage ?: "Unknown generation error"
                    throw RuntimeException(errMsg)
                }
            }

            if (accumulated.isEmpty()) {
                Result.failure(RuntimeException("No response received from assistant."))
            } else {
                Result.success(accumulated.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation failure", e)
            Result.failure(e)
        }
    }
}
