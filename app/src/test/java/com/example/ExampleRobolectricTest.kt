package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ShadowAnalysisCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Staccato", appName)
  }

  @Test
  fun `verify shadow analysis detects staccato cadence`() {
    val sampleText = """[Yağmur saçaklardan dökülüyor]
"Nerede?"
[Demir sürgüyü geri çekti]
"İçeride. Kıpırdamadı."
[Kapıyı açtı]"""
    val state = ShadowAnalysisCore.analyze(sampleText)
    assertEquals("staccato", state.cadence)
    assertEquals("stage_direction_interleaved", state.formatMode)
    assertTrue(state.avgWordsPerSentence <= 6.5)
  }
}

