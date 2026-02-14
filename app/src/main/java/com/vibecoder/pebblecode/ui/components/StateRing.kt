package com.vibecoder.pebblecode.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.vibecoder.pebblecode.ui.theme.PebbleColors

enum class RingState { IDLE, THINKING, READY, ERROR, QUESTION }

@Composable
fun StateRing(state: RingState, modifier: Modifier = Modifier) {
    if (state == RingState.IDLE) return

    val infiniteTransition = rememberInfiniteTransition(label = "ring")

    // Main rotation for thinking spinner
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    // Slower trailing rotation
    val trailRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "trail"
    )

    // Alpha pulse for ready/question
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    // Flicker for error
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "flicker"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val mainStroke = 6f
        val glowStroke = 18f
        val inset = glowStroke / 2 + 1f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val arcOffset = Offset(inset, inset)

        when (state) {
            RingState.THINKING -> {
                // Trailing ghost arc (slow, wide, very dim)
                drawArc(
                    color = PebbleColors.ThinkingAmber.copy(alpha = 0.08f),
                    startAngle = trailRotation - 90f,
                    sweepAngle = 220f,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = glowStroke, cap = StrokeCap.Round)
                )
                // Main arc glow
                drawArc(
                    color = PebbleColors.ThinkingAmber.copy(alpha = 0.2f),
                    startAngle = rotation - 90f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = glowStroke, cap = StrokeCap.Round)
                )
                // Main arc (crisp)
                drawArc(
                    color = PebbleColors.ThinkingAmber.copy(alpha = 0.95f),
                    startAngle = rotation - 90f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                )
            }
            RingState.READY -> {
                // Glow layer
                drawArc(
                    color = PebbleColors.ReadyGlow.copy(alpha = pulse * 0.15f),
                    startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = glowStroke, cap = StrokeCap.Round)
                )
                // Main ring
                drawArc(
                    color = PebbleColors.ReadyGlow.copy(alpha = pulse),
                    startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                )
            }
            RingState.ERROR -> {
                // Glow
                drawArc(
                    color = PebbleColors.ErrorRed.copy(alpha = flicker * 0.25f),
                    startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = glowStroke, cap = StrokeCap.Round)
                )
                // Main
                drawArc(
                    color = PebbleColors.ErrorRed.copy(alpha = flicker),
                    startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                )
            }
            RingState.QUESTION -> {
                // Glow
                drawArc(
                    color = PebbleColors.QuestionCyan.copy(alpha = pulse * 0.15f),
                    startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = glowStroke, cap = StrokeCap.Round)
                )
                // Main
                drawArc(
                    color = PebbleColors.QuestionCyan.copy(alpha = pulse),
                    startAngle = 0f, sweepAngle = 360f,
                    useCenter = false, topLeft = arcOffset,
                    size = arcSize,
                    style = Stroke(width = mainStroke, cap = StrokeCap.Round)
                )
            }
            else -> {} // IDLE handled by early return
        }
    }
}
