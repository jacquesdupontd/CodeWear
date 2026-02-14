package com.vibecoder.pebblecode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun QuestionScreen(
    questionText: String,
    options: List<String>,
    selectedIndex: Int,
    onNavigate: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
    onOther: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberScalingLazyListState()
    val totalItems = options.size + 1

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
                    val current = selectedIndex
                    val next = if (event.verticalScrollPixels > 0) {
                        (current + 1).coerceAtMost(totalItems - 1)
                    } else {
                        (current - 1).coerceAtLeast(0)
                    }
                    if (next != current) onNavigate(next)
                    true
                }
                .focusRequester(focusRequester)
                .focusable(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            // Question text
            item {
                Text(
                    text = questionText,
                    color = PebbleColors.Question,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Separator
            item {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(1.dp)
                        .background(PebbleColors.Separator)
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Options as styled cards
            itemsIndexed(options) { index, option ->
                val isSelected = index == selectedIndex
                val borderColor = if (isSelected) PebbleColors.SelectedBorder else PebbleColors.CardBorder
                val bgColor = if (isSelected) PebbleColors.Selected else PebbleColors.Surface

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .background(bgColor, RoundedCornerShape(10.dp))
                        .clickable { onConfirm(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            color = if (isSelected) PebbleColors.Malachite else PebbleColors.TextDimmed,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = option,
                            color = if (isSelected) PebbleColors.TextPrimary else PebbleColors.TextSecondary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Other (speak) option
            item {
                val isSelected = selectedIndex == options.size
                val borderColor = if (isSelected) PebbleColors.Other else PebbleColors.CardBorder

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .then(
                            if (isSelected) Modifier.background(
                                PebbleColors.Selected,
                                RoundedCornerShape(10.dp)
                            ) else Modifier
                        )
                        .clickable { onOther() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\uD83C\uDFA4 Other (speak)",
                        color = if (isSelected) PebbleColors.TextPrimary else PebbleColors.Other,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
