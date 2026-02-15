package com.vibecoder.pebblecode.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

data class AppTheme(
    val key: String,
    val displayName: String,
    // Core
    val background: Color = Color.Black,
    val surface: Color = Color(0xFF0D0D0D),
    val surfaceBright: Color = Color(0xFF1A1A1A),
    val cardBorder: Color = Color(0xFF2A2A2A),
    // Text
    val textPrimary: Color = Color.White,
    val textSecondary: Color = Color(0xFFBBBBBB),
    val textDimmed: Color = Color(0xFF666666),
    // Brand / accent
    val accent: Color,
    val accentSecondary: Color,
    val yellow: Color = Color(0xFFFFFF00),
    val mintGreen: Color = Color(0xFFAAFFAA),
    val darkGreen: Color = Color(0xFF003300),
    val chromeYellow: Color = Color(0xFFFFAA00),
    // Semantic
    val suggestion: Color = Color(0xFFFFFF00),
    val command: Color = Color.White,
    val status: Color = Color(0xFF00CCCC),
    val separator: Color = Color(0xFF333333),
    val selected: Color = Color(0xFF002200),
    val selectedBorder: Color,
    val question: Color,
    val other: Color = Color(0xFFFFFF00),
    // Animation / ring
    val thinkingAmber: Color = Color(0xFFFF8800),
    val readyGlow: Color = Color(0xFF00FF44),
    val errorRed: Color = Color(0xFFFF2222),
    val questionCyan: Color = Color(0xFF00DDFF),
)

// ── Terminal (default) ──────────────────────────
val terminalTheme = AppTheme(
    key = "terminal",
    displayName = "Terminal",
    accent = Color(0xFF00FFFF),
    accentSecondary = Color(0xFF00DD00),
    selectedBorder = Color(0xFF00DD00),
    question = Color(0xFF00FFFF),
)

// ── Cyberpunk ───────────────────────────────────
val cyberpunkTheme = AppTheme(
    key = "cyberpunk",
    displayName = "Cyberpunk",
    accent = Color(0xFFFF00FF),
    accentSecondary = Color(0xFFBB44FF),
    yellow = Color(0xFFFFD700),
    mintGreen = Color(0xFFDDAAFF),
    darkGreen = Color(0xFF1A0033),
    chromeYellow = Color(0xFFFF66CC),
    suggestion = Color(0xFFFFD700),
    status = Color(0xFFCC44FF),
    selected = Color(0xFF220033),
    selectedBorder = Color(0xFFBB44FF),
    question = Color(0xFFFF00FF),
    other = Color(0xFFFFD700),
    thinkingAmber = Color(0xFFBB44FF),
    readyGlow = Color(0xFFFF44FF),
    questionCyan = Color(0xFFDD44FF),
)

// ── Ocean ───────────────────────────────────────
val oceanTheme = AppTheme(
    key = "ocean",
    displayName = "Ocean",
    accent = Color(0xFF44BBFF),
    accentSecondary = Color(0xFF00AACC),
    yellow = Color(0xFF88FFCC),
    mintGreen = Color(0xFF88FFEE),
    darkGreen = Color(0xFF001133),
    chromeYellow = Color(0xFF44DDAA),
    suggestion = Color(0xFF88FFCC),
    status = Color(0xFF0099BB),
    selected = Color(0xFF001133),
    selectedBorder = Color(0xFF00AACC),
    question = Color(0xFF44BBFF),
    other = Color(0xFF88FFCC),
    thinkingAmber = Color(0xFF0088DD),
    readyGlow = Color(0xFF00DDFF),
    questionCyan = Color(0xFF44BBFF),
)

// ── Sunset ──────────────────────────────────────
val sunsetTheme = AppTheme(
    key = "sunset",
    displayName = "Sunset",
    accent = Color(0xFFFF6633),
    accentSecondary = Color(0xFFFFAA00),
    yellow = Color(0xFFFFEE88),
    mintGreen = Color(0xFFFFCCAA),
    darkGreen = Color(0xFF331100),
    chromeYellow = Color(0xFFFFCC44),
    suggestion = Color(0xFFFFEE88),
    status = Color(0xFFFF8844),
    selected = Color(0xFF331100),
    selectedBorder = Color(0xFFFFAA00),
    question = Color(0xFFFF6633),
    other = Color(0xFFFFEE88),
    thinkingAmber = Color(0xFFFF6633),
    readyGlow = Color(0xFFFFCC00),
    questionCyan = Color(0xFFFF8844),
)

// ── Arctic ──────────────────────────────────────
val arcticTheme = AppTheme(
    key = "arctic",
    displayName = "Arctic",
    accent = Color(0xFFAACCFF),
    accentSecondary = Color(0xFF88AADD),
    yellow = Color(0xFFEEFFAA),
    mintGreen = Color(0xFFCCDDEE),
    darkGreen = Color(0xFF0A1020),
    chromeYellow = Color(0xFFBBCCFF),
    suggestion = Color(0xFFEEFFAA),
    status = Color(0xFF7799CC),
    selected = Color(0xFF0A1020),
    selectedBorder = Color(0xFF88AADD),
    question = Color(0xFFAACCFF),
    other = Color(0xFFEEFFAA),
    thinkingAmber = Color(0xFF88AADD),
    readyGlow = Color(0xFFCCDDFF),
    questionCyan = Color(0xFFAABBFF),
)

val allThemes = listOf(terminalTheme, cyberpunkTheme, oceanTheme, sunsetTheme, arcticTheme)

private val _currentTheme = mutableStateOf(terminalTheme)

fun getThemeByKey(key: String): AppTheme =
    allThemes.find { it.key == key } ?: terminalTheme

fun loadThemeKey(context: Context): String =
    context.getSharedPreferences("pebblecode", Context.MODE_PRIVATE)
        .getString("theme", "terminal") ?: "terminal"

fun saveThemeKey(context: Context, key: String) {
    context.getSharedPreferences("pebblecode", Context.MODE_PRIVATE)
        .edit().putString("theme", key).apply()
}

/**
 * PebbleColors delegates to _currentTheme so all existing screens
 * keep using PebbleColors.Xxx without any changes.
 */
object PebbleColors {
    private val t get() = _currentTheme.value

    val Background: Color get() = t.background
    val Surface: Color get() = t.surface
    val SurfaceBright: Color get() = t.surfaceBright
    val CardBorder: Color get() = t.cardBorder

    val TextPrimary: Color get() = t.textPrimary
    val TextSecondary: Color get() = t.textSecondary
    val TextDimmed: Color get() = t.textDimmed

    val Cyan: Color get() = t.accent
    val Malachite: Color get() = t.accentSecondary
    val Yellow: Color get() = t.yellow
    val MintGreen: Color get() = t.mintGreen
    val DarkGreen: Color get() = t.darkGreen
    val ChromeYellow: Color get() = t.chromeYellow

    val Suggestion: Color get() = t.suggestion
    val Command: Color get() = t.command
    val Status: Color get() = t.status
    val Separator: Color get() = t.separator
    val Selected: Color get() = t.selected
    val SelectedBorder: Color get() = t.selectedBorder
    val Question: Color get() = t.question
    val Other: Color get() = t.other

    val ThinkingAmber: Color get() = t.thinkingAmber
    val ReadyGlow: Color get() = t.readyGlow
    val ErrorRed: Color get() = t.errorRed
    val QuestionCyan: Color get() = t.questionCyan
}

@Composable
fun PebbleCodeTheme(
    theme: AppTheme = terminalTheme,
    content: @Composable () -> Unit
) {
    _currentTheme.value = theme
    MaterialTheme(
        colors = Colors(
            primary = theme.accent,
            primaryVariant = theme.accentSecondary,
            secondary = theme.chromeYellow,
            secondaryVariant = theme.yellow,
            background = theme.background,
            surface = theme.surface,
            error = theme.errorRed,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = theme.textPrimary,
            onSurface = theme.textPrimary,
            onSurfaceVariant = theme.textSecondary,
            onError = Color.White
        ),
        content = content
    )
}
