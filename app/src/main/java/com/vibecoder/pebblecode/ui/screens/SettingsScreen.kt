package com.vibecoder.pebblecode.ui.screens

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.vibecoder.pebblecode.ui.theme.PebbleColors
import com.vibecoder.pebblecode.ui.theme.allThemes
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentThemeKey: String,
    onThemeSelected: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()

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
                    text = "Themes",
                    color = PebbleColors.Cyan,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            item { Spacer(modifier = Modifier.height(6.dp)) }

            itemsIndexed(allThemes) { _, theme ->
                val isSelected = theme.key == currentThemeKey
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) theme.accent else PebbleColors.CardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .background(
                            if (isSelected) theme.selected else PebbleColors.Surface,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onThemeSelected(theme.key) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Color preview dots
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(theme.accent, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(theme.accentSecondary, CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(theme.readyGlow, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = theme.displayName,
                            color = if (isSelected) theme.accent else PebbleColors.TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
