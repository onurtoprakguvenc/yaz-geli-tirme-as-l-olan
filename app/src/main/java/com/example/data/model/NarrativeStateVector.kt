package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Anlatı Durum Vektörü (State JSON)
 * Represents the exact payload specified in Chapter 4 of the architectural specification.
 */
@JsonClass(generateAdapter = true)
data class NarrativeStateVector(
    @field:Json(name = "cadence")
    val cadence: String = "staccato", // "staccato" | "fluid / relaxed" | "dense / rhythmic"

    @field:Json(name = "avg_words_per_sentence")
    val avgWordsPerSentence: Double = 4.1,

    @field:Json(name = "format_mode")
    val formatMode: String = "stage_direction_interleaved", // "stage_direction_interleaved" | "dialogue_heavy" | "standard_prose"

    @field:Json(name = "sensory_budget")
    val sensoryBudget: Int = 1, // 0 to 3 max concrete sensory anchors

    @field:Json(name = "cynicism_index")
    val cynicismIndex: Double = 0.85, // 0.0 to 1.0 (gritty realism & terse tension)

    @field:Json(name = "banned_stems")
    val bannedStems: List<String> = listOf("suddenly", "gently", "felt", "seemed", "realized", "aniden", "yavaşça", "hissetti"),

    // Local Telemetry & UI Diagnostics
    val dialogueRatio: Double = 0.45,
    val punctuationDensity: Double = 0.12,
    val keystrokeLatencyMs: Long = 180L,
    val wordsPerMinute: Int = 42,
    val sentenceCount: Int = 0,
    val totalWordCount: Int = 0,
    val antiCollapseWatchdog: Boolean = false, // True if repetitive structural lock detected
    val activeContextSnippet: String = "",
    val detectedTone: String = "Kinetik Gerilim"
)

enum class AnalysisTier(val tierNumber: Int, val title: String, val subtitle: String, val shortLabel: String) {
    K1(1, "K1: Hafif / Light", "Saf Yerel Regex (Sıfır ek yük)", "K1"),
    K2(2, "K2: Dengeli / Balanced", "Uyarlanabilir Bağlam (Akıllı Debounce)", "K2"),
    K3(3, "K3: Derin / Deep", "Tam Spektrum (Derin Kısıtlar)", "K3")
}
