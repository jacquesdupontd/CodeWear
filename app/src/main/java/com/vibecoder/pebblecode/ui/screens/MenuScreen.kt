package com.vibecoder.pebblecode.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.vibecoder.pebblecode.ui.theme.PebbleColors
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch

@Composable
fun MenuScreen(
    sessions: List<String>,
    connected: Boolean,
    onJoin: (String) -> Unit,
    onCreate: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit = {},
    onBridge: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()

    // Pulsing connection dot
    val infiniteTransition = rememberInfiniteTransition(label = "menu")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot"
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(PebbleColors.Background)
                .onRotaryScrollEvent { event ->
                    scope.launch { listState.scrollBy(event.verticalScrollPixels) }
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            // Title + connection status
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PebbleCode",
                        color = PebbleColors.Cyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .alpha(if (connected) dotAlpha else 0.3f)
                                .background(
                                    if (connected) PebbleColors.Malachite else PebbleColors.ErrorRed,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (connected) "Connected" else "Connecting...",
                            color = if (connected) PebbleColors.Malachite else PebbleColors.TextDimmed,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Sessions list
            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = "No sessions",
                        color = PebbleColors.TextDimmed,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            } else {
                itemsIndexed(sessions) { _, session ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(1.dp, PebbleColors.CardBorder, RoundedCornerShape(12.dp))
                            .background(PebbleColors.Surface, RoundedCornerShape(12.dp))
                            .clickable { onJoin(session) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ">",
                                color = PebbleColors.Malachite,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = session,
                                color = PebbleColors.TextPrimary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(6.dp)) }

            // New session button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(
                            1.dp,
                            PebbleColors.Malachite.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .background(
                            PebbleColors.DarkGreen.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onCreate() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ New session",
                        color = PebbleColors.MintGreen,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Refresh button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(1.dp, PebbleColors.CardBorder, RoundedCornerShape(12.dp))
                        .clickable { onRefresh() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Refresh",
                        color = PebbleColors.TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Themes button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(1.dp, PebbleColors.Cyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onSettings() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Themes",
                        color = PebbleColors.Cyan,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Bridge host button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(1.dp, PebbleColors.ChromeYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onBridge() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bridge",
                        color = PebbleColors.ChromeYellow,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
