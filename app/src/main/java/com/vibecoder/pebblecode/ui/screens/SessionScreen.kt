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
    onLaunchVoice: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // =======================================================
    // HISTORY: bridge sends archived history on join,
    // then we keep accumulating locally
    // =======================================================
    val savedHistory = remember { mutableStateListOf<String>() }
    var bridgeHistoryLoaded by remember { mutableStateOf(false) }
    var lastFullSummary by remember { mutableStateOf("") }
    var lastArchivedHash by remember { mutableIntStateOf(0) }
    var lastUserCmd by remember { mutableStateOf("") }
    var wasWorking by remember { mutableStateOf(false) }

    // Load bridge history once on join
    LaunchedEffect(bridgeHistory) {
        if (bridgeHistory.isNotEmpty() && !bridgeHistoryLoaded) {
            savedHistory.clear()
            for (line in bridgeHistory) {
                if (line == "---SEP---") {
                    savedHistory.add("\u2500") // separator marker
                } else {
                    savedHistory.add(line)
                }
            }
            bridgeHistoryLoaded = true
        }
    }

    // Archive helper: save current summary to history if not already archived
    fun archiveSummary() {
        if (lastFullSummary.isNotEmpty() && lastFullSummary.hashCode() != lastArchivedHash) {
            val oldLines = lastFullSummary.split("\n").filter { it.isNotBlank() }
            savedHistory.addAll(oldLines)
            savedHistory.add("\u2500") // separator
            while (savedHistory.size > 300) savedHistory.removeAt(0)
            lastArchivedHash = lastFullSummary.hashCode()
        }
    }

    // Trigger 1: user command changed = new dictation sent
    LaunchedEffect(cleanData.userCmd) {
        if (cleanData.userCmd.isNotEmpty() && cleanData.userCmd != lastUserCmd) {
            archiveSummary()
            lastUserCmd = cleanData.userCmd
        }
    }

    // Trigger 2: Claude finished working (idle after working)
    LaunchedEffect(cleanData.isIdle, cleanData.status) {
        if (cleanData.isIdle && wasWorking) {
            // Small delay to let final summary arrive
            delay(1500)
            archiveSummary()
        }
        wasWorking = !cleanData.isIdle && !cleanData.isQuestion
    }

    // Track current summary
    LaunchedEffect(cleanData.summary) {
        if (cleanData.summary.isNotEmpty()) {
            lastFullSummary = cleanData.summary
        }
    }

    val currentLines = remember(cleanData.summary) {
        if (cleanData.summary.isEmpty()) emptyList()
        else cleanData.summary.split("\n").filter { it.isNotBlank() }
    }

    // Display: archived history + current turn (skip current if it was just archived)
    val displayCurrentLines = remember(currentLines, lastArchivedHash) {
        if (lastFullSummary.hashCode() == lastArchivedHash) emptyList()
        else currentLines
    }

    val allLines = remember(savedHistory.size, displayCurrentLines) {
        savedHistory.toList() + displayCurrentLines
    }
    val reversedLines = remember(allLines) { allLines.asReversed() }

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
    var lastActiveTask by remember { mutableStateOf("") }
    LaunchedEffect(cleanData.status, cleanData.activeTask) {
        if (isWorking) lastWorkingStatus = cleanData.status
        if (cleanData.activeTask.isNotEmpty()) lastActiveTask = cleanData.activeTask
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
            lastActiveTask = ""
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
    // ORANGE COMMAND LINE (fixed bar)
    // Working → activeTask | Idle → diff+tool (cleaned)
    // =======================================================
    val displayTask = cleanData.activeTask.ifEmpty { if (showWorking) lastActiveTask else "" }

    // Strip literal "..." and "…" from bridge text
    fun cleanDots(s: String) = s.trim()
        .removePrefix("...").removeSuffix("...")
        .removePrefix("\u2026").removeSuffix("\u2026")
        .trim()

    val commandLineText = when {
        showWorking && displayTask.isNotEmpty() ->
            "\u25B8 ${cleanDots(displayTask)}"
        cleanData.diff.isNotEmpty() && cleanData.lastTool.isNotEmpty() ->
            "\u25C7 ${cleanDots(cleanData.lastTool)}: ${cleanDots(cleanData.diff)}"
        cleanData.diff.isNotEmpty() ->
            "\u25C7 ${cleanDots(cleanData.diff)}"
        cleanData.lastTool.isNotEmpty() ->
            "\u25C7 ${cleanDots(cleanData.lastTool)}"
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
                                    .background(PebbleColors.ThinkingAmber.copy(alpha = 0.5f))
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
                // --- Orange command line: basicMarquee (no "...", no empty space) ---
                if (commandLineText.isNotEmpty()) {
                    Text(
                        text = commandLineText,
                        color = PebbleColors.ThinkingAmber,
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
                                velocity = 30.dp
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
                                velocity = 30.dp
                            )
                    )
                }

                // --- Suggestion/prompt: 12sp, basicMarquee ---
                if (barText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .background(
                                PebbleColors.SurfaceBright.copy(alpha = 0.8f),
                                RoundedCornerShape(8.dp)
                            )
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
