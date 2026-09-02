package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WritingCanvas(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag("writing_canvas")
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_text_field"),
            textStyle = TextStyle(
                color = Color(0xFFE8E8E8),
                fontSize = 16.sp,
                lineHeight = 26.sp, // ~1.6x comfortable line height
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.3.sp
            ),
            cursorBrush = SolidColor(Color(0xFFD0BCFF)),
            decorationBox = { innerTextField ->
                if (text.isEmpty()) {
                    Text(
                        text = "Start writing here...",
                        style = TextStyle(
                            color = Color(0xFF555555),
                            fontSize = 16.sp,
                            lineHeight = 26.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
                innerTextField()
            }
        )
    }
}
