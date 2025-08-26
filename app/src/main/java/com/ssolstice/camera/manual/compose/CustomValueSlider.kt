package com.ssolstice.camera.manual.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    value: Float,
    onValueChange: (Float, String) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    labels: List<String> = listOf("-1", "-0.5", "-0.25", "0", "0.25", "0.5", "1+"),
    steps: Int = 16,
    valueColor: Color = Color.Green,
    thumbColor: Color = Color.Yellow,
    activeColor: Color = Color.Yellow,
    inactiveColor: Color = Color.Gray,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                text = mapDisplayValue(name, value),
                color = valueColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Các nhãn
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Slider với custom thumb
            Slider(
                value = value,
                onValueChange = {
                    onValueChange(it, mapDisplayValue(name, value))
                },
                valueRange = valueRange,
                steps = steps - 1,
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp),
                colors = SliderDefaults.colors(
                    thumbColor = thumbColor,              // màu nút
                    activeTrackColor = Color.Transparent,        // track bên trái
                    inactiveTrackColor = Color.Transparent, // track bên phải
                    activeTickColor = activeColor,            // dot bên trái
                    inactiveTickColor = inactiveColor          // dot bên phải
                )
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}