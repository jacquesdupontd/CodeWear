package com.vibecoder.pebblecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.vibecoder.pebblecode.ui.theme.PebbleColors
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch

data class HostPreset(val host: String, val label: String)

private val presets = listOf(
    HostPreset("192.168.1.118", "Home WiFi"),
    HostPreset("macbook-pro", "MacBook (Funnel)"),
    HostPreset("vnc", "VNC Server (Funnel)")
)

@Composable
fun BridgeHostScreen(
    currentHost: String,
    onHostSelected: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    var customHost by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    val customFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
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
            item {
                Text(
                    text = "Bridge",
                    color = PebbleColors.Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Text(
                    text = currentHost,
                    color = PebbleColors.Malachite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Preset hosts
            items(presets.size) { index ->
                val preset = presets[index]
                val isSelected = preset.host == currentHost
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) PebbleColors.Malachite else PebbleColors.CardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (isSelected) PebbleColors.DarkGreen.copy(alpha = 0.3f) else PebbleColors.Surface,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onHostSelected(preset.host) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = preset.label,
                            color = if (isSelected) PebbleColors.Malachite else PebbleColors.TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = preset.host,
                            color = PebbleColors.TextDimmed,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Custom input
            item {
                if (!showCustom) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(1.dp, PebbleColors.ChromeYellow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { showCustom = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Custom...",
                            color = PebbleColors.ChromeYellow,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .border(1.dp, PebbleColors.ChromeYellow, RoundedCornerShape(12.dp))
                            .background(PebbleColors.Surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BasicTextField(
                            value = customHost,
                            onValueChange = { customHost = it },
                            textStyle = TextStyle(
                                color = PebbleColors.TextPrimary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(PebbleColors.Cyan),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (customHost.isNotBlank()) {
                                        onHostSelected(customHost.trim())
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(customFocus)
                                .background(PebbleColors.Background, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            decorationBox = { innerTextField ->
                                if (customHost.isEmpty()) {
                                    Text(
                                        text = "IP or hostname",
                                        color = PebbleColors.TextDimmed,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                innerTextField()
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PebbleColors.Malachite, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (customHost.isNotBlank()) {
                                        onHostSelected(customHost.trim())
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Connect",
                                color = PebbleColors.Background,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LaunchedEffect(showCustom) {
                        customFocus.requestFocus()
                    }
                }
            }
        }
    }
}
