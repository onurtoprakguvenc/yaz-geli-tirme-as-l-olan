package com.example.engine

import com.example.data.model.NarrativeStateVector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Katman 2: Yerel Gölge Analiz Çekirdeği (Local Shadow Core)
 *
 * Runs locally on CPU in worker thread.
 * Sub-millisecond execution time, zero API overhead.
 * Generates the "Anlatı Durum Vektörü" (Narrative State JSON).
 */
object ShadowAnalysisCore {

    private val DEFAULT_BANNED_STEMS = listOf(
        "suddenly", "gently", "felt", "seemed", "realized", "began to",
        "couldn't help but", "a testament to", "shiver down", "palpable",
        "little did he know", "in that moment",
        "aniden", "yavaşça", "hissetti", "görünüyordu", "fark etti",
        "adeta", "adeta bir", "ürperti", "derin bir nefes", "gözlerine inanamadı",
        "büyük bir şaşkınlıkla", "içten içe", "kalbi yerinden fırlayacak"
    )

    private val CONCRETE_SENSORY_KEYWORDS = setOf(
        "cold", "sweat", "dust", "iron", "steel", "rust", "wire", "concrete",
        "blood", "damp", "smoke", "copper", "glass", "asphalt", "rain", "mud",
        "soğuk", "ter", "toz", "demir", "çelik", "pas", "tel", "beton",
        "kan", "nem", "duman", "bakır", "cam", "asfalt", "yağmur", "çamur"
    )

    private val CYNICAL_ACTION_VERBS = setOf(
        "cut", "slammed", "fired", "shoved", "dropped", "snapped", "jammed", "dragged",
        "stared", "spit", "broke", "locked", "kicked", "pulled", "stepped",
        "vurdu", "çekti", "bastı", "fırlattı", "kırdı", "kilitledi", "tepki",
        "sustu", "baktı", "fısıldadı", "tuttu", "ezdi", "kesti", "ititti", "çatırdadı"
    )

    /**
     * Analyzes document text and returns the complete NarrativeStateVector
     */
    fun analyze(
        text: String,
        keystrokeLatencyMs: Long = 180L,
        customBannedWords: List<String> = emptyList()
    ): NarrativeStateVector {
        if (text.isBlank()) {
            return NarrativeStateVector(
                cadence = "staccato",
                avgWordsPerSentence = 4.0,
                formatMode = "stage_direction_interleaved",
                sensoryBudget = 1,
                cynicismIndex = 0.85,
                bannedStems = DEFAULT_BANNED_STEMS + customBannedWords,
                activeContextSnippet = ""
            )
        }

        // 1. Circular Buffer Truncation (Last 300 words or max 1500 chars)
        val activeContext = extractActiveContext(text)

        // 2. Sentence and Word Tokenization
        val sentences = splitSentences(activeContext)
        val words = tokenizeWords(activeContext)
        val totalWords = words.size
        val sentenceCount = max(1, sentences.size)

        // 3. Average Words per Sentence
        val avgWords = if (sentenceCount > 0 && totalWords > 0) {
            String.format("%.1f", totalWords.toDouble() / sentenceCount).toDoubleOrNull() ?: 4.0
        } else {
            4.0
        }

        // 4. Cadence Classification
        val cadence = when {
            avgWords <= 6.5 -> "staccato"
            avgWords <= 12.0 -> "fluid / relaxed"
            else -> "dense / rhythmic"
        }

        // 5. Format Mode Detection (Stage Direction vs Standard Dialogue vs Prose)
        val stageDirectionRegex = Regex("""\[(.*?)\]|\((.*?)\)""")
        val dialogueRegex = Regex("""["“«](.*?)[”"»]|—(.*?)(\n|$)""")

        val stageDirectionMatches = stageDirectionRegex.findAll(activeContext).count()
        val dialogueMatches = dialogueRegex.findAll(activeContext).count()

        val formatMode = when {
            stageDirectionMatches > 0 && dialogueMatches > 0 -> "stage_direction_interleaved"
            dialogueMatches >= 2 -> "dialogue_heavy"
            else -> "standard_prose"
        }

        // 6. Dialogue vs Action Ratio
        val dialogueCharCount = dialogueRegex.findAll(activeContext).sumOf { it.value.length }
        val dialogueRatio = if (activeContext.isNotEmpty()) {
            min(1.0, dialogueCharCount.toDouble() / activeContext.length)
        } else 0.4

        // 7. Punctuation Density
        val punctuationCount = activeContext.count { it in setOf('.', ',', '!', '?', ';', ':', '-', '—', '[', ']', '"', '“', '”') }
        val punctuationDensity = if (activeContext.isNotEmpty()) {
            min(1.0, punctuationCount.toDouble() / activeContext.length)
        } else 0.1

        // 8. Sensory Budget (Concrete physical details in last section)
        val sensoryMatches = words.count { it.lowercase() in CONCRETE_SENSORY_KEYWORDS }
        val sensoryBudget = min(3, max(1, sensoryMatches))

        // 9. Cynicism & Grit Index
        val activeVerbMatches = words.count { it.lowercase() in CYNICAL_ACTION_VERBS }
        val rawCynicism = 0.5 + (activeVerbMatches * 0.08) - (avgWords * 0.02)
        val cynicismIndex = min(0.98, max(0.20, String.format("%.2f", rawCynicism).toDoubleOrNull() ?: 0.85))

        // 10. Anti-Collapse Watchdog (Monotonous sentence length or syntax lock)
        val sentenceLengths = sentences.map { tokenizeWords(it).size }
        val antiCollapseWarning = detectAntiCollapseLock(sentenceLengths)

        // 11. Cliché and Banned Stems Assembly
        val combinedBanned = (DEFAULT_BANNED_STEMS + customBannedWords).distinct()

        // 12. Tone Label
        val detectedTone = when {
            cynicismIndex >= 0.75 && cadence == "staccato" -> "Kinetik Gerilim (Hardboiled)"
            dialogueRatio >= 0.60 -> "Dramatik Diyalog Çatışması"
            cadence == "fluid / relaxed" -> "Dengeli Atmosferik Akış"
            else -> "Hızlı Aksiyon Sekansı"
        }

        return NarrativeStateVector(
            cadence = cadence,
            avgWordsPerSentence = avgWords,
            formatMode = formatMode,
            sensoryBudget = sensoryBudget,
            cynicismIndex = cynicismIndex,
            bannedStems = combinedBanned,
            dialogueRatio = String.format("%.2f", dialogueRatio).toDoubleOrNull() ?: 0.45,
            punctuationDensity = String.format("%.2f", punctuationDensity).toDoubleOrNull() ?: 0.12,
            keystrokeLatencyMs = keystrokeLatencyMs,
            wordsPerMinute = if (keystrokeLatencyMs > 0) min(160, (60000 / keystrokeLatencyMs / 5).toInt()) else 45,
            sentenceCount = sentenceCount,
            totalWordCount = totalWords,
            antiCollapseWatchdog = antiCollapseWarning,
            activeContextSnippet = activeContext,
            detectedTone = detectedTone
        )
    }

    /**
     * Circular Buffer Truncation:
     * Mandate: Maximum last 300 words or last 1500 characters.
     */
    fun extractActiveContext(fullText: String): String {
        if (fullText.length <= 1500) {
            val words = fullText.trim().split(Regex("""\s+"""))
            return if (words.size <= 300) fullText else words.takeLast(300).joinToString(" ")
        }
        val charSlice = fullText.takeLast(1500)
        val words = charSlice.trim().split(Regex("""\s+"""))
        return if (words.size <= 300) charSlice else words.takeLast(300).joinToString(" ")
    }

    private fun splitSentences(text: String): List<String> {
        return text.split(Regex("""(?<=[.!?])\s+|\n+""")).filter { it.isNotBlank() }
    }

    private fun tokenizeWords(text: String): List<String> {
        return text.split(Regex("""[\s.,!?;:\[\]"“”—\-()]+""")).filter { it.isNotBlank() }
    }

    private fun detectAntiCollapseLock(lengths: List<Int>): Boolean {
        if (lengths.size < 5) return false
        val recent = lengths.takeLast(5)
        val mean = recent.average()
        val variance = recent.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        // If 5 sentences have virtually identical length or standard deviation is < 0.5, trigger watchdog
        return stdDev < 0.6 && mean > 2.0
    }

    /**
     * Generates a formatted JSON representation of the Narrative State Vector
     */
    fun formatStateJson(vector: NarrativeStateVector): String {
        val bannedListFormatted = vector.bannedStems.take(8).joinToString(", ") { "\"$it\"" }
        return """{
  "cadence": "${vector.cadence}",
  "avg_words_per_sentence": ${vector.avgWordsPerSentence},
  "format_mode": "${vector.formatMode}",
  "sensory_budget": ${vector.sensoryBudget},
  "cynicism_index": ${vector.cynicismIndex},
  "banned_stems": [$bannedListFormatted]
}"""
    }
}
