package com.vibecoder.pebblecode.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.vibecoder.pebblecode.data.CleanData
import com.vibecoder.pebblecode.ui.components.RingState
import com.vibecoder.pebblecode.ui.components.StateRing
import com.vibecoder.pebblecode.ui.theme.PebbleColors
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionScreen(
    cleanData: CleanData,
    hasPrompt: Boolean,
    promptText: String,
    bridgeHistory: List<String> = emptyList(),
    onLaunchVoice: () -> Unit,
    onAcceptSuggestion: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // =======================================================
    // HISTORY: accumulate ALL updates in real-time
    // Every summary/tool change appends to the scrollable log
    // =======================================================
    val historyLines = remember { mutableStateListOf<String>() }
    var bridgeHistoryLoaded by remember { mutableStateOf(false) }
    var lastSummaryHash by remember { mutableIntStateOf(0) }
    var lastUserCmd by remember { mutableStateOf("") }

    // Load bridge history once on join
    LaunchedEffect(bridgeHistory) {
        if (bridgeHistory.isNotEmpty() && !bridgeHistoryLoaded) {
            historyLines.clear()
            for (line in bridgeHistory) {
                if (line == "---SEP---") {
                    historyLines.add("\u2500")
                } else {
                    historyLines.add(line)
                }
            }
            bridgeHistoryLoaded = true
        }
    }

    // Accumulate summary changes in real-time
    LaunchedEffect(cleanData.summary) {
        val hash = cleanData.summary.hashCode()
        if (cleanData.summary.isNotEmpty() && hash != lastSummaryHash) {
            val newLines = cleanData.summary.split("\n").filter { it.isNotBlank() }
            historyLines.addAll(newLines)
            while (historyLines.size > 500) historyLines.removeAt(0)
            lastSummaryHash = hash
        }
    }

    // Add separator when user sends new command
    LaunchedEffect(cleanData.userCmd) {
        if (cleanData.userCmd.isNotEmpty() && cleanData.userCmd != lastUserCmd) {
            if (historyLines.isNotEmpty()) {
                historyLines.add("\u2500") // separator
            }
            lastUserCmd = cleanData.userCmd
        }
    }

    // Skip trailing separator (avoids double orange bar on launch)
    val reversedLines = remember(historyLines.size) {
        val list = historyLines.toList()
        val trimmed = if (list.lastOrNull() == "\u2500") list.dropLast(1) else list
        trimmed.asReversed()
    }

    // Auto-scroll to bottom (index 0 with reverseLayout)
    LaunchedEffect(cleanData.summary, cleanData.lastTool) {
        delay(150)
        listState.animateScrollToItem(0)
    }

    // Re-request focus so crown always works
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }
    LaunchedEffect(cleanData.summary) {
        focusRequester.requestFocus()
    }

    // =======================================================
    // STATUS LOGIC
    // =======================================================
    val isWorking = !cleanData.isIdle
        && !cleanData.isQuestion
        && !cleanData.status.contains("error", ignoreCase = true)

    var lastWorkingStatus by remember { mutableStateOf("") }
    LaunchedEffect(cleanData.status, cleanData.activeTask) {
        if (isWorking) lastWorkingStatus = cleanData.status
    }

    var stableIdle by remember { mutableStateOf(true) }
    var recentlyWorking by remember { mutableStateOf(false) }
    LaunchedEffect(cleanData.status) {
        if (cleanData.isIdle) {
            recentlyWorking = lastWorkingStatus.isNotEmpty()
            delay(3000)
            stableIdle = true
            recentlyWorking = false
            lastWorkingStatus = ""
        } else if (!cleanData.isQuestion) {
            stableIdle = false
            recentlyWorking = false
        }
    }

    val showWorking = isWorking || recentlyWorking

    val ringState = when {
        cleanData.isQuestion -> RingState.QUESTION
        cleanData.status.contains("error", ignoreCase = true) -> RingState.ERROR
        isWorking -> RingState.THINKING
        recentlyWorking -> RingState.THINKING
        stableIdle -> RingState.READY
        else -> RingState.IDLE
    }

    val statusBgColor by animateColorAsState(
        targetValue = when {
            cleanData.status.contains("error", ignoreCase = true) -> PebbleColors.ErrorRed
            showWorking -> PebbleColors.ThinkingAmber
            stableIdle -> PebbleColors.Malachite
            else -> PebbleColors.Status
        },
        animationSpec = tween(400),
        label = "statusColor"
    )

    val barColor = when {
        cleanData.suggestion.isNotEmpty() -> PebbleColors.Yellow
        hasPrompt -> PebbleColors.ChromeYellow
        else -> PebbleColors.Command
    }

    // Pulse animation for status pill when working
    val pillPulse = rememberInfiniteTransition(label = "pillPulse")
    val pillAlpha by pillPulse.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pillAlpha"
    )

    // =======================================================
    // COMMAND LINE (fixed bar) — always shows lastTool first
    // =======================================================

    // Strip literal "..." and "…" from bridge text
    fun cleanDots(s: String) = s.trim()
        .removePrefix("...").removeSuffix("...")
        .removePrefix("\u2026").removeSuffix("\u2026")
        .trim()

    val commandLineText = when {
        // Tool always wins (shows "$ gradle build", "read Theme.kt", etc.)
        cleanData.lastTool.isNotEmpty() && cleanData.diff.isNotEmpty() ->
            "\u25C7 ${cleanDots(cleanData.lastTool)}: ${cleanDots(cleanData.diff)}"
        cleanData.lastTool.isNotEmpty() ->
            "\u25C7 ${cleanDots(cleanData.lastTool)}"
        cleanData.diff.isNotEmpty() ->
            "\u25C7 ${cleanDots(cleanData.diff)}"
        cleanData.activeTask.isNotEmpty() ->
            "\u25B8 ${cleanDots(cleanData.activeTask)}"
        else -> ""
    }

    // Prompt/suggestion bar text
    val barText = when {
        cleanData.suggestion.isNotEmpty() -> cleanData.suggestion
        hasPrompt && promptText.isNotEmpty() -> promptText
        else -> null
    }

    // Status pill: short text only
    val pillText = when {
        isWorking && cleanData.status.isNotEmpty() ->
            cleanData.status.removeSuffix("...").removeSuffix("\u2026").trim()
        isWorking -> "Working"
        recentlyWorking && lastWorkingStatus.isNotEmpty() ->
            lastWorkingStatus.removeSuffix("...").removeSuffix("\u2026").trim()
        recentlyWorking -> "Done"
        stableIdle -> "Ready"
        else -> "Connected"
    }

    // =======================================================
    // LAYOUT: Box overlay — bottom bar CANNOT scroll
    // =======================================================
    Scaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PebbleColors.Background)
                .onRotaryScrollEvent { event ->
                    scope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            // ===== SCROLLABLE CONTENT =====
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = 90.dp,
                    start = 16.dp, end = 16.dp
                )
            ) {
                // Summary lines only — diff/tool are in the fixed bar
                itemsIndexed(reversedLines, key = { idx, _ -> "sl_$idx" }) { _, line ->
                    if (line == "\u2500") {
                        // Turn separator — visible line with padding
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(2.dp)
                                    .background(PebbleColors.Cyan.copy(alpha = 0.4f))
                            )
                        }
                    } else {
                        Text(
                            text = line,
                            color = PebbleColors.TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ===== FIXED BOTTOM BAR (overlay — never scrolls) =====
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(PebbleColors.Background),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Command line: theme accent color, basicMarquee ---
                if (commandLineText.isNotEmpty()) {
                    Text(
                        text = commandLineText,
                        color = PebbleColors.Cyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(bottom = 2.dp)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                                initialDelayMillis = 1500,
                                repeatDelayMillis = 2000,
                                spacing = MarqueeSpacing(0.dp),
                                velocity = 90.dp
                            )
                    )
                }

                // --- User command: WHITE, 13sp, basicMarquee ---
                if (cleanData.userCmd.isNotEmpty()) {
                    Text(
                        text = "> ${cleanData.userCmd}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(bottom = 2.dp)
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                                initialDelayMillis = 2000,
                                repeatDelayMillis = 3000,
                                spacing = MarqueeSpacing(0.dp),
                                velocity = 90.dp
                            )
                    )
                }

                // --- Suggestion/prompt: 12sp, basicMarquee, tap to accept ---
                if (barText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .background(
                                PebbleColors.SurfaceBright.copy(alpha = 0.8f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onAcceptSuggestion() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = barText,
                            color = barColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                                initialDelayMillis = 1500,
                                repeatDelayMillis = 2000,
                                velocity = 30.dp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }

                // --- Status pill ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .alpha(if (showWorking) pillAlpha else 1f)
                        .background(statusBgColor, RoundedCornerShape(14.dp))
                        .clickable { onLaunchVoice() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pillText,
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // Ring ON TOP — drawn last so bottom bar never covers it
            StateRing(state = ringState)
        }
    }
}
