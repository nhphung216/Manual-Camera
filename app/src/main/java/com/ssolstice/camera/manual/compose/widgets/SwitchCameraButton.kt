package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.compose.WhiteColor

@Composable
fun SwitchCameraButton(
    switchCamera: () -> Unit
) {
    var rotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "rotationAnim"
    )

    OuterRing(
        onClick = {
            rotation += 180f
            switchCamera()
        },
        modifier = Modifier.size(56.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FlipCameraAndroid,
            contentDescription = "Switch Camera",
            tint = WhiteColor,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { rotationZ = animatedRotation }
        )
    }
}
