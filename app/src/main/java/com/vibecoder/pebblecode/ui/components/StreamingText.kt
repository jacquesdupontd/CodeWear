package com.vibecoder.pebblecode.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

@Composable
fun StreamingText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 14.sp,
    charDelay: Long = 12L
) {
    var displayedText by remember { mutableStateOf("") }
    var isStreaming by remember { mutableStateOf(false) }
    var skipAnimation by remember { mutableStateOf(false) }

    // When text changes, start streaming
    LaunchedEffect(text) {
        if (text.isEmpty()) {
            displayedText = ""
            isStreaming = false
            return@LaunchedEffect
        }
        // If new text starts with current displayed text, continue from there
        val startIdx = if (text.startsWith(displayedText) && displayedText.isNotEmpty()) {
            displayedText.length
        } else {
            displayedText = ""
            0
        }
        skipAnimation = false
        isStreaming = true
        for (i in startIdx until text.length) {
            if (skipAnimation) {
                displayedText = text
                break
            }
            displayedText = text.substring(0, i + 1)
            delay(charDelay)
        }
        isStreaming = false
    }

    // Blinking cursor
    val cursor = if (isStreaming) {
        "\u2588" // Block cursor
    } else ""

    Text(
        text = displayedText + cursor,
        color = color,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        lineHeight = (fontSize.value * 1.3f).sp,
        modifier = modifier.clickable {
            if (isStreaming) {
                skipAnimation = true
            }
        }
    )
}
