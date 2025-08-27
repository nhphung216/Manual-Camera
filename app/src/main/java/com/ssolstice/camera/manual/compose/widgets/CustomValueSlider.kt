package com.ssolstice.camera.manual.compose.widgets

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
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
    formated: String = "",
    value: Float,
    showReset: Boolean = true,
    showBackgroundColor: Boolean = false,
    onValueChange: (Float, String) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    labels: List<String> = listOf("-1", "-0.5", "-0.25", "0", "0.25", "0.5", "1+"),
    steps: Int = 16,
    valueColor: Color = Color.Yellow,
    thumbColor: Color = Color.Yellow,
    activeColor: Color = Color.Yellow,
    inactiveColor: Color = Color.Gray,
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
                .padding(bottom = 24.dp, top = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 24.dp, end = 24.dp)
            ) {
//                Text(
//                    text = formated,
//                    color = valueColor,
//                    fontSize = 16.sp,
//                    textAlign = TextAlign.Center
//                )
//                Spacer(modifier = Modifier.height(8.dp))

                // Labels khớp value
//                Box(modifier = Modifier.fillMaxWidth()) {
//                    labels.forEachIndexed { pos, text ->
//                        val percent = (pos - valueRange.start) / (valueRange.endInclusive - valueRange.start)
//                        Text(
//                            text = text,
//                            color = Color.White,
//                            fontSize = 11.sp,
//                            textAlign = TextAlign.Center,
//                            modifier = Modifier
//                                .align(Alignment.CenterStart)
//                                .fillMaxWidth(percent.toFloat()) // căn đúng vị trí
//                        )
//                    }
//                }

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
                            thumbColor = thumbColor,              // màu nút
                            activeTrackColor = Color.White,        // track bên trái
                            inactiveTrackColor = Color.White, // track bên phải
                            activeTickColor = activeColor,            // dot bên trái
                            inactiveTickColor = inactiveColor          // dot bên phải
                        )
                    )
                }
            }
            if (showReset) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = Color.White,
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

@SuppressLint("DefaultLocale")
@Composable
fun PillSlider(
    modifier: Modifier = Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 20,
) {
    var isDragging by remember { mutableStateOf(false) }
    var sliderWidth by remember { mutableFloatStateOf(0f) }
    val thumbRadiusDp = 12.dp
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadiusDp.toPx() }
    val labelAlpha = animateFloatAsState(if (isDragging) 1f else 0f)

    Box(
        modifier = modifier
            .height(40.dp)
            .onSizeChanged { sliderWidth = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        val x = change.position.x.coerceIn(0f, sliderWidth)
                        val newValue =
                            range.start + (x / sliderWidth) * (range.endInclusive - range.start)
                        onValueChange(newValue.coerceIn(range.start, range.endInclusive))
                    }
                )
            }
    ) {
        // Pill background + ticks
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) {
            val trackHeight = size.height / 6
            drawRoundRect(
                color = Color.DarkGray.copy(alpha = 0.3f),
                cornerRadius = CornerRadius(trackHeight, trackHeight),
                size = Size(width = size.width, height = trackHeight),
                topLeft = Offset(0f, (size.height - trackHeight) / 2)
            )

            // Tick marks
            val stepPx = size.width / (steps - 1)
            for (i in 0 until steps) {
                val x = i * stepPx
                val stroke = if (i % (steps / 5).coerceAtLeast(1) == 0) 3f else 1.5f
                drawLine(
                    color = Color.White,
                    start = Offset(x, (size.height - trackHeight) / 2),
                    end = Offset(x, (size.height + trackHeight) / 2),
                    strokeWidth = stroke
                )
            }
        }

        // Thumb position
        val thumbX = (value - range.start) / (range.endInclusive - range.start) * sliderWidth
        Box(
            modifier = Modifier
                .offset { IntOffset((thumbX - thumbRadiusPx).toInt(), 0) }
                .size(thumbRadiusDp * 2)
                .background(Color.White, CircleShape)
                .border(2.dp, Color.Cyan, CircleShape),
            contentAlignment = Alignment.Center
        ) { }

        // Value label above thumb
        if (sliderWidth > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((thumbX - 20).toInt(), -28) }
                    .alpha(labelAlpha.value)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = String.format("%.1fx", value),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun PillSliderPreview() {
//    var zoom by remember { mutableFloatStateOf(5f) }
//    Column(
//        Modifier
//            .fillMaxWidth()
//            .padding(20.dp),
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text("Zoom: %.1fx".format(zoom), color = Color.White)
//        PillSlider(
//            value = zoom,
//            onValueChange = { zoom = it },
//            range = 0.6f..10f,
//            steps = 20,
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = 16.dp)
//        )
//    }
//}
