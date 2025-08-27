package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.compose.RecordColor
import com.ssolstice.camera.manual.compose.WhiteColor

@Composable
fun ShutterButton(
    isPhotoMode: Boolean,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val innerColor = if (isPhotoMode) {
        WhiteColor
    } else {
        if (isRecording) RecordColor else WhiteColor
    }

    val scale by animateFloatAsState(
        targetValue = if (isRecording) 1f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Outer ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = WhiteColor,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // Inner circle
        Box(
            modifier = Modifier
                .size(
                    if (isPhotoMode) {
                        60.dp
                    } else {
                        if (isRecording) 60.dp else 40.dp
                    }
                )
                .clip(CircleShape)
                .background(innerColor)
        )
    }
}