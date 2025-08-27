package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun WhiteBalanceSliderPreview() {
    WhiteBalanceSlider(value = 6500f, onValueChange = {})
}

@Composable
fun WhiteBalanceSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 2000f..8000f // Kelvin
) {
    var sliderPosition by remember { mutableFloatStateOf(value) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Hiển thị giá trị Kelvin
        Text(
            text = "${sliderPosition.toInt()} K",
            color = Color.White
        )


        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val percent = (offset.x / size.width).coerceIn(0f, 1f)
                        val newValue = valueRange.start + percent * (valueRange.endInclusive - valueRange.start)
                        sliderPosition = newValue
                        onValueChange(newValue)
                    }
                }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                // Gradient từ xanh -> trắng -> đỏ
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF05D2F1), // xanh dương (cool ~ 2000K)
                            Color.White,       // neutral
                            Color(0xFFFF6D00)  // đỏ cam (warm ~ 8000K)
                        )
                    ),
                    cornerRadius = CornerRadius(20f, 20f)
                )

                // Vẽ thumb (nút)
                val percent = (sliderPosition - valueRange.start) / (valueRange.endInclusive - valueRange.start)
                val thumbX = percent * size.width
                drawCircle(
                    color = Color.White,
                    radius = 15f,
                    center = Offset(thumbX, size.height / 2)
                )
                drawCircle(
                    color = Color.Black,
                    radius = 15f,
                    center = Offset(thumbX, size.height / 2),
                    alpha = 0.2f
                )
            }
        }
    }
}
