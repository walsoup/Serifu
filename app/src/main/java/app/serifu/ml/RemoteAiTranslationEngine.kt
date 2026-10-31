package app.serifu.ml

import android.util.Log
import app.serifu.core.Tuning
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.LlmResponseParser
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.PromptBuilder
import app.serifu.core.translate.TranslationCache
import app.serifu.core.translate.TranslationEngine
import app.serifu.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class RemoteAiTranslationEngine(
    private val settingsProvider: () -> Settings,
    private val cache: TranslationCache = TranslationCache(),
) : TranslationEngine {

    override val id: EngineId = EngineId.AI_REMOTE

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun isConfigured(settings: Settings = settingsProvider()): Boolean =
        settings.aiApiKey.isNotBlank()

    override suspend fun availability(
        source: app.serifu.core.model.SourceLanguage,
        target: String,
    ): EngineAvailability {
        return if (isConfigured()) {
            EngineAvailability.Ready
        } else {
            EngineAvailability.Unsupported("AI API key not configured")
        }
    }

    override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult =
        withContext(Dispatchers.IO) {
            val settings = settingsProvider()
            if (!isConfigured(settings)) {
                throw IllegalStateException("AI API key not configured")
            }

            val translated = HashMap<Int, String>()
            val pending = ArrayList<app.serifu.core.translate.BubbleText>()
            for (bubble in request.bubbles) {
                if (bubble.text.isBlank()) continue
                val cached = cache.get(cache.key(id, request.target, "${request.source.tag}|${bubble.text}"))
                if (cached != null) {
                    translated[bubble.id] = cached
                } else {
                    pending.add(bubble)
                }
            }

            if (pending.isEmpty()) {
                return@withContext PageTranslationResult(id, translated)
            }

            val prompt = PromptBuilder.build(request.copy(bubbles = pending))
            val fallback = pending.associate { it.id to it.text }

            val rawContent = withTimeout(Tuning.AI_REMOTE_TIMEOUT_MS) {
                callApi(settings, prompt)
            }

            val parsed = LlmResponseParser.parse(rawContent, fallback)
            for (bubble in pending) {
                val text = parsed[bubble.id] ?: bubble.text
                translated[bubble.id] = text
                if (text != bubble.text) {
                    cache.put(cache.key(id, request.target, "${request.source.tag}|${bubble.text}"), text)
                }
            }

            PageTranslationResult(id, translated)
        }

    private fun callApi(settings: Settings, prompt: String): String {
        val apiKey = settings.aiApiKey.trim()
        val model = settings.aiModel.trim().ifEmpty { "gemini-2.0-flash" }
        val endpoint = settings.aiEndpoint.trim()

        val isGemini = endpoint.isEmpty() || endpoint.contains("googleapis.com")

        val targetUrl = if (isGemini) {
            if (endpoint.isNotEmpty()) {
                if (endpoint.contains("key=")) endpoint else "$endpoint?key=$apiKey"
            } else {
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            }
        } else {
            endpoint
        }

        val requestBody = if (isGemini) {
            buildJsonObject {
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            putJsonArray("parts") {
                                add(buildJsonObject { put("text", prompt) })
                            }
                        },
                    )
                }
                putJsonObject("generationConfig") {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                }
            }.toString()
        } else {
            // OpenAI-compatible format
            buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", prompt)
                        },
                    )
                }
                put("temperature", 0.2)
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
            }.toString()
        }

        val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = Tuning.AI_REMOTE_TIMEOUT_MS.toInt()
            readTimeout = Tuning.AI_REMOTE_TIMEOUT_MS.toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!isGemini) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }

        try {
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorStream = connection.errorStream ?: connection.inputStream
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.w(TAG, "API call failed with status $responseCode: $errorBody")
                throw RuntimeException("AI API error ($responseCode): $errorBody")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            return extractTextContent(responseBody, isGemini)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractTextContent(responseBody: String, isGemini: Boolean): String {
        val root = json.parseToJsonElement(responseBody).jsonObject
        return if (isGemini) {
            val candidates = root["candidates"]?.jsonArray
                ?: throw RuntimeException("No candidates in Gemini response")
            val firstCandidate = candidates.firstOrNull()?.jsonObject
                ?: throw RuntimeException("Empty candidates in Gemini response")
            val content = firstCandidate["content"]?.jsonObject
                ?: throw RuntimeException("Missing content in candidate")
            val parts = content["parts"]?.jsonArray
                ?: throw RuntimeException("Missing parts in content")
            parts.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: throw RuntimeException("Missing text in part")
        } else {
            val choices = root["choices"]?.jsonArray
                ?: throw RuntimeException("No choices in OpenAI response")
            val firstChoice = choices.firstOrNull()?.jsonObject
                ?: throw RuntimeException("Empty choices in response")
            val message = firstChoice["message"]?.jsonObject
                ?: throw RuntimeException("Missing message in choice")
            message["content"]?.jsonPrimitive?.content
                ?: throw RuntimeException("Missing content in message")
        }
    }

    override fun close() {
        // No persistent resources to close
    }

    private companion object {
        const val TAG = "RemoteAiTranslate"
    }
}
