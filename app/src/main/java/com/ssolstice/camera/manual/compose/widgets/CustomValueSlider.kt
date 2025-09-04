package com.ssolstice.camera.manual.compose.widgets

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun CustomValueSliderPreview() {
    CustomValueSlider(
        modifier = Modifier,
        value = 0.25f,
        name = "itemSelected",
        onValueChange = { _, _ -> },
        valueRange = -1f..1f
    )
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)

fun mapDisplayValue(paramName: String, value: Float): String {
    return when (paramName.lowercase()) {
        "brightness" -> "${(value * 2000).toInt()}k"        // -2000k → 2000k
        "contrast" -> "${(value * 100).toInt()}%"         // 0% → 200%
        "exposure" -> "${value.format(2)} EV"                        // -2EV → 2EV
        "saturation" -> "${(value * 100).toInt()}%"         // 0% → 200%
        "hue" -> "${(value * 360).toInt()}°"         // 0° → 360°
        "vignette" -> "${(value * 100).toInt()}%"         // 0% → 100%
        "blur" -> "${(value * 10).format(1)}px"       // 0px → 10px
        "strength" -> "${(value * 100).toInt()}%"         // 0% → 100%
        "sharpen" -> "${(value * 10).toInt()}%"         // 0% → 100% độ nét
        else -> value.format(2)                             // fallback: float 2 số thập phân
    }
}


@SuppressLint("DefaultLocale")
@Composable
fun CustomValueSlider(
    modifier: Modifier,
    name: String,
    formated: String = "",
    value: Float,
    showReset: Boolean = true,
    showBackgroundColor: Boolean = false,
    onValueChange: (Float, String) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    onReset: () -> Unit = {}
) {
    Box(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, top = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 24.dp, end = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Slider với custom thumb
                Box {
                    if (showBackgroundColor) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            // Gradient từ xanh -> trắng -> đỏ
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF0091EA), // xanh dương (cool ~ 2000K)
                                        Color.Gray,       // neutral
                                        Color(0xFFFF6D00)  // đỏ cam (warm ~ 8000K)
                                    )
                                ),
                                cornerRadius = CornerRadius(30f, 30f)
                            )
                        }
                    }

                    Slider(
                        value = value,
                        onValueChange = {
                            onValueChange(it, mapDisplayValue(name, value))
                        },
                        valueRange = valueRange,
                        //steps = 55,
                        modifier = Modifier
                            .height(24.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 1.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,              // màu nút
                            activeTrackColor = MaterialTheme.colorScheme.secondaryContainer,        // track bên trái
                            inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer, // track bên phải
                            activeTickColor = Color.Yellow,            // dot bên trái
                            inactiveTickColor = Color.Gray          // dot bên phải
                        )
                    )
                }
            }
            if (showReset) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .size(24.dp)
                        .clickable { onReset() }
                        .align(Alignment.Bottom)
                )
            }
        }
    }
}