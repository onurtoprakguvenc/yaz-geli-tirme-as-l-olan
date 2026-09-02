package com.example.engine

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.NarrativeStateVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Katman 3: Dinamik Kısıt Derleyici ve LLM İnfazı (Execution Engine)
 *
 * Enforces Chapter 4 technical specifications:
 * - Dynamic GenerationConfig mapping (staccato vs fluid)
 * - Safety Settings Override (BLOCK_NONE)
 * - Modular System Prompt Assembly
 * - Strict I/O Contract ([ACTIVE_CONTEXT] / [NEXT_BEAT])
 * - Few-Shot Format Forcing
 * - OkHttp Streaming with Real-Time Token Post-Filter
 * - Graceful Fallback
 */
class StaccatoExecutionEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "StaccatoEngine"
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME"
    }

    /**
     * Executes generation stream based on Narrative State Vector and User Next Beat.
     */
    fun executeStream(
        activeContext: String,
        nextBeat: String = "",
        vector: NarrativeStateVector,
        customApiKey: String? = null
    ): Flow<String> = flow {
        val effectiveKey = customApiKey?.trim().takeIf { !it.isNullOrBlank() }
            ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }

        // Check if API key is valid / present
        if (effectiveKey.isNullOrBlank()) {
            Log.w(TAG, "No API key provided, executing smooth contextual fallback stream")
            emitGracefulFallbackStream(activeContext, nextBeat, vector) { emit(it) }
            return@flow
        }

        try {
            val requestJson = buildModularPayload(activeContext, nextBeat, vector)
            val streamUrl = "$BASE_URL:streamGenerateContent?key=$effectiveKey&alt=sse"

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(streamUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "HTTP ${response.code}"
                Log.e(TAG, "API call failed with code ${response.code}: $errorBody")
                // Graceful fallback on network/quota error
                emitGracefulFallbackStream(activeContext, nextBeat, vector) { emit(it) }
                return@flow
            }

            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                emitGracefulFallbackStream(activeContext, nextBeat, vector) { emit(it) }
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            val emittedBuffer = StringBuilder()
            var isPreambleFiltered = false

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data:")) {
                    val dataJson = currentLine.removePrefix("data:").trim()
                    if (dataJson.isNotEmpty() && dataJson != "[DONE]") {
                        val token = extractTokenFromSseChunk(dataJson)
                        if (!token.isNullOrEmpty()) {
                            // Filter preamble conversational noise
                            val filteredToken = if (!isPreambleFiltered) {
                                val sanitized = stripConversationalPreamble(token)
                                if (sanitized.isNotBlank()) {
                                    isPreambleFiltered = true
                                }
                                sanitized
                            } else {
                                token
                            }

                            if (filteredToken.isNotEmpty()) {
                                emittedBuffer.append(filteredToken)
                                emit(filteredToken)

                                // Double newline check: Cut stream if stop sequence encountered
                                if (emittedBuffer.contains("\n\n")) {
                                    break
                                }
                            }
                        }
                    }
                }
            }
            reader.close()

            if (emittedBuffer.isEmpty()) {
                emitGracefulFallbackStream(activeContext, nextBeat, vector) { emit(it) }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error streaming from Gemini API: ${e.message}", e)
            emitGracefulFallbackStream(activeContext, nextBeat, vector) { emit(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Builds the exact Modular System Instruction & Dynamic Payload specified in the PDF.
     */
    fun buildModularPayload(
        activeContext: String,
        nextBeat: String,
        vector: NarrativeStateVector
    ): JSONObject {
        val root = JSONObject()

        // 1. Modular System Prompt Assembly
        val systemPrompt = buildString {
            append("You are a mechanical narrative craft apparatus. ")
            append("Never summarize, never explain, never output conversational intros ('Here is the continuation:'). ")
            append("Your job is to seamlessly continue the narrative from the exact last word with strict stylistic discipline.\n\n")

            // Format Constraint
            if (vector.formatMode == "stage_direction_interleaved") {
                append("MANDATORY FORMAT: Strict interleaved physical action and dialogue.\n")
                append("Pattern: [Physical action and environmental feedback] followed by \"Spoken dialogue\".\n")
                append("Do NOT write loose emotional exposition.\n\n")
            }

            // Sensory / Showing Module
            if (vector.sensoryBudget <= 1) {
                append("STRICT RULE: Maximum 1 concrete sensory detail (cold, sweat, dust, iron, rust, copper) per action block. ")
                append("ZERO emotional state adjectives (sadly, angrily, happily, hopelessly).\n\n")
            }

            // Dialogue Module
            if (vector.dialogueRatio > 0.4 || vector.formatMode == "dialogue_heavy") {
                append("DIALOGUE DIRECTIVE: Keep spoken lines terse, punchy, cynical, and heavy with subtext. No flowery speech tags.\n\n")
            }

            // Negative Constraints (Banned Stems)
            val bannedJoined = vector.bannedStems.take(20).joinToString(", ")
            append("NEGATIVE CONSTRAINTS (LOGIT PENALTY SIMULATION):\n")
            append("Under NO circumstances use these words, stems, or clichés: [$bannedJoined].\n")
            append("No melodrama. No moralizing. Deliver pure visceral action skeleton.\n\n")

            // One-Shot Anchor (Few-Shot Format Forcing)
            append("ONE-SHOT ANCHOR EXAMPLE:\n")
            append("[ACTIVE_CONTEXT]\n[Yağmur saçaklardan dökülüyor]\n\"Nerede?\"\n[Demir sürgüyü geri çekti]\n\n")
            append("[NEXT_BEAT]\nKapıyı açar ve hedefi görür\n\n")
            append("OUTPUT:\n\"İçeride. Kıpırdamadı.\"\n[Ceketinin yakasını kaldırdı. Soğuk ter ensesinden aktı]\n\"İçeri gir.\"")
        }

        val systemInstructionObj = JSONObject()
        val systemParts = JSONArray()
        systemParts.put(JSONObject().put("text", systemPrompt))
        systemInstructionObj.put("parts", systemParts)
        root.put("systemInstruction", systemInstructionObj)

        // 2. Strict I/O Contract User Content
        val truncatedContext = ShadowAnalysisCore.extractActiveContext(activeContext)
        val userPrompt = buildString {
            append("[ACTIVE_CONTEXT]\n")
            append(truncatedContext)
            append("\n\n[NEXT_BEAT]\n")
            append(nextBeat.ifBlank { "Sahneyi aynı vuruş temposunda bir sonraki eylem ve diyalogla ilerlet." })
        }

        val contentsArray = JSONArray()
        val contentObj = JSONObject()
        val contentParts = JSONArray()
        contentParts.put(JSONObject().put("text", userPrompt))
        contentObj.put("parts", contentParts)
        contentsArray.put(contentObj)
        root.put("contents", contentsArray)

        // 3. Dynamic GenerationConfig Mapping
        val genConfig = JSONObject()
        if (vector.cadence == "staccato") {
            genConfig.put("temperature", 0.25)
            genConfig.put("maxOutputTokens", 150)
            val stopSeqs = JSONArray()
            stopSeqs.put("\n\n\n")
            stopSeqs.put("The end")
            stopSeqs.put("---")
            genConfig.put("stopSequences", stopSeqs)
        } else {
            genConfig.put("temperature", 0.55)
            genConfig.put("maxOutputTokens", 300)
        }
        root.put("generationConfig", genConfig)

        // 4. Safety Settings Override (BLOCK_NONE for adult grit, street jargon, and kinetic action)
        val safetyArray = JSONArray()
        val categories = listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        )
        for (cat in categories) {
            val safetyObj = JSONObject()
            safetyObj.put("category", cat)
            safetyObj.put("threshold", "BLOCK_NONE")
            safetyArray.put(safetyObj)
        }
        root.put("safetySettings", safetyArray)

        return root
    }

    private fun extractTokenFromSseChunk(jsonStr: String): String? {
        return try {
            val json = JSONObject(jsonStr)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            val text = parts.getJSONObject(0).optString("text", "")
            text.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun stripConversationalPreamble(token: String): String {
        var clean = token
        val preambles = listOf(
            "Here is the draft:", "Here's the continuation:", "Sure,", "Sure:",
            "Devamı şöyle:", "İşte devamı:", "Taslak:"
        )
        for (pre in preambles) {
            if (clean.startsWith(pre, ignoreCase = true)) {
                clean = clean.substring(pre.length).trimStart()
            }
        }
        return clean
    }

    /**
     * Graceful Fallback Stream (Runs completely offline when network fails or keys are missing)
     */
    private suspend fun emitGracefulFallbackStream(
        activeContext: String,
        nextBeat: String,
        vector: NarrativeStateVector,
        emit: suspend (String) -> Unit
    ) {
        val trimmed = activeContext.trim()
        val isTurkish = trimmed.contains("ve") || trimmed.contains("bir") || trimmed.contains("için") || trimmed.contains("ı") || trimmed.contains("ş") || trimmed.contains("ğ")

        val tokens = if (trimmed.isEmpty()) {
            if (isTurkish) {
                listOf("İlk satırı ", "yazmaya başlayın. ", "Model bağlamınıza ", "uyum sağlayacaktır.")
            } else {
                listOf("Begin writing ", "your first line. ", "The engine will adapt ", "to your cadence.")
            }
        } else {
            val lastPunctuation = if (trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?")) " " else ". "
            if (isTurkish) {
                listOf(
                    lastPunctuation,
                    if (nextBeat.isNotBlank()) "[$nextBeat] " else "",
                    "Sessizlik uzadı. ",
                    "Adımlarını yavaşlattı. ",
                    "\"Devam et.\""
                ).filter { it.isNotEmpty() }
            } else {
                listOf(
                    lastPunctuation,
                    if (nextBeat.isNotBlank()) "[$nextBeat] " else "",
                    "The silence stretched. ",
                    "He checked his footing. ",
                    "\"Keep moving.\""
                ).filter { it.isNotEmpty() }
            }
        }

        for (token in tokens) {
            kotlinx.coroutines.delay(50)
            emit(token)
        }
    }
}
