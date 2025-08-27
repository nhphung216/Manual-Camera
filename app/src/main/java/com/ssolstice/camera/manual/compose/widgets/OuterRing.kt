package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.compose.WhiteColor


@Composable
fun OuterRing(
    modifier: Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = WhiteColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        content()
    }
}