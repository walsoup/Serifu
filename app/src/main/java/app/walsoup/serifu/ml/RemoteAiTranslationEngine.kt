package app.walsoup.serifu.ml

import android.util.Log
import app.serifu.core.Tuning
import app.serifu.core.model.ReasoningEffort
import app.serifu.core.translate.EngineAvailability
import app.serifu.core.translate.EngineId
import app.serifu.core.translate.LlmResponseParser
import app.serifu.core.translate.PageTranslationRequest
import app.serifu.core.translate.PageTranslationResult
import app.serifu.core.translate.PromptBuilder
import app.serifu.core.translate.TranslationCache
import app.serifu.core.translate.TranslationEngine
import app.walsoup.serifu.settings.Settings
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
import java.net.URLEncoder
import java.security.MessageDigest

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
        settings.aiApiKey.isNotBlank() || settings.aiEndpoint.isNotBlank()

    /**
     * Non-secret identity of the remote configuration, mixed into cache keys so a
     * model, endpoint or effort change cannot serve translations from the old config.
     * The endpoint is hashed so query distinctions (api versions, regions) still
     * invalidate while credentials like Gemini's ?key= never appear in keys.
     */
    internal fun cacheConfigTag(settings: Settings = settingsProvider()): String =
        "${settings.aiModel.trim()}|${endpointFingerprint(settings.aiEndpoint)}|${settings.reasoningEffort.name}"

    private fun endpointFingerprint(endpoint: String): String {
        val normalized = endpoint.trim()
        if (normalized.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    override suspend fun availability(
        source: app.serifu.core.model.SourceLanguage,
        target: String,
    ): EngineAvailability {
        return if (isConfigured()) {
            EngineAvailability.Ready
        } else {
            EngineAvailability.Unsupported("API endpoint or key not configured")
        }
    }

    override suspend fun translatePage(request: PageTranslationRequest): PageTranslationResult =
        withContext(Dispatchers.IO) {
            val settings = settingsProvider()
            if (!isConfigured(settings)) {
                throw IllegalStateException("API not configured")
            }
            val configTag = cacheConfigTag(settings)

            val translated = HashMap<Int, String>()
            val pending = ArrayList<app.serifu.core.translate.BubbleText>()
            for (bubble in request.bubbles) {
                if (bubble.text.isBlank()) continue
                val cached = cache.get(cache.key(id, request.target, "$configTag|${request.source.tag}|${bubble.text}"))
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
                callApiWithFallback(settings, prompt)
            }

            val parsed = LlmResponseParser.parse(rawContent, fallback)
            Log.i(TAG, "Translated ${pending.size} bubbles via AI")
            for (bubble in pending) {
                val text = parsed[bubble.id] ?: bubble.text
                translated[bubble.id] = text
                if (text != bubble.text) {
                    cache.put(cache.key(id, request.target, "$configTag|${request.source.tag}|${bubble.text}"), text)
                }
            }

            PageTranslationResult(id, translated)
        }

    private fun callApiWithFallback(settings: Settings, prompt: String): String {
        val initialEffort = when (settings.reasoningEffort) {
            ReasoningEffort.AUTO -> null
            ReasoningEffort.NONE -> "none"
            ReasoningEffort.LOW -> "low"
            ReasoningEffort.MEDIUM -> "medium"
            ReasoningEffort.HIGH -> "high"
        }

        try {
            return callApi(settings, prompt, initialEffort)
        } catch (e: ApiException) {
            if (e.isReasoningEffortError) {
                if (initialEffort == "none") {
                    Log.i(TAG, "Model rejected 'none' reasoning_effort; retrying with 'low'")
                    try {
                        return callApi(settings, prompt, "low")
                    } catch (e2: ApiException) {
                        if (e2.isReasoningEffortError) {
                            Log.i(TAG, "Model rejected reasoning_effort; retrying without reasoning_effort")
                            return callApi(settings, prompt, null)
                        }
                        throw e2
                    }
                } else if (initialEffort != null) {
                    Log.i(TAG, "Model rejected reasoning_effort '$initialEffort'; retrying without reasoning_effort")
                    return callApi(settings, prompt, null)
                }
            }
            throw e
        }
    }

    private fun callApi(settings: Settings, prompt: String, effort: String? = null): String {
        val apiKey = settings.aiApiKey.trim()
        val model = settings.aiModel.trim().ifEmpty { "gpt-4o-mini" }
        val rawEndpoint = settings.aiEndpoint.trim()

        val isGemini = isGeminiEndpoint(rawEndpoint)
        Log.i(
            TAG,
            if (isGemini) {
                "Calling Gemini (model comes from the endpoint URL), effort=$effort"
            } else {
                "Calling AI: model=$model, effort=$effort"
            },
        )

        val targetUrl = buildRemoteRequestUrl(rawEndpoint, apiKey)

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
                    if (effort != null) {
                        putJsonObject("thinkingConfig") {
                            val budget = when (effort) {
                                "none" -> 0
                                "low" -> 1024
                                "medium" -> 4096
                                "high" -> 16384
                                else -> 0
                            }
                            put("thinkingBudget", budget)
                        }
                    }
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
                if (effort != null) {
                    put("reasoning_effort", effort)
                }
            }.toString()
        }

        val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = Tuning.AI_REMOTE_TIMEOUT_MS.toInt()
            readTimeout = Tuning.AI_REMOTE_TIMEOUT_MS.toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!isGemini && apiKey.isNotBlank()) {
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
                throw ApiException(responseCode, errorBody)
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

internal fun isGeminiEndpoint(endpoint: String): Boolean =
    endpoint.contains("googleapis.com", ignoreCase = true)

internal fun buildRemoteRequestUrl(endpoint: String, apiKey: String): String {
    val raw = endpoint.trim()
    if (isGeminiEndpoint(raw)) return appendQueryParameter(raw, "key", apiKey.trim())
    return when {
        raw.isEmpty() -> "https://api.openai.com/v1/chat/completions"
        raw.endsWith("/chat/completions") -> raw
        raw.endsWith("/") -> "${raw}chat/completions"
        else -> "$raw/chat/completions"
    }
}

private fun appendQueryParameter(url: String, name: String, value: String): String {
    if (value.isEmpty() || queryParameterNames(url).any { it == name }) return url
    val hash = url.indexOf('#')
    val beforeFragment = if (hash >= 0) url.substring(0, hash) else url
    val fragment = if (hash >= 0) url.substring(hash) else ""
    val base = beforeFragment.trimEnd('?', '&')
    val separator = if ('?' in base) '&' else '?'
    val encoded = URLEncoder.encode(value, Charsets.UTF_8.name())
    return "$base$separator$name=$encoded$fragment"
}

private fun queryParameterNames(url: String): List<String> {
    val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    if (query.isEmpty()) return emptyList()
    return query.split('&').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
}

private class ApiException(val statusCode: Int, val errorBody: String) :
    RuntimeException("AI API error ($statusCode): $errorBody") {
    val isReasoningEffortError: Boolean
        get() = statusCode == 400 && (
            errorBody.contains("reasoning_effort", ignoreCase = true) ||
            errorBody.contains("reasoning effort", ignoreCase = true) ||
            errorBody.contains("thinking_budget", ignoreCase = true) ||
            errorBody.contains("thinkingConfig", ignoreCase = true)
        )
}
