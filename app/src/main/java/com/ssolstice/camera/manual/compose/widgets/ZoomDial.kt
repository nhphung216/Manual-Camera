package com.ssolstice.camera.manual.compose.widgets

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import androidx.core.graphics.withSave

/**
 * A semicircular zoom dial.
 *
 * @param zoomSteps list of available zoom ratios (e.g. [1f, 2f, 3f, 4f, ...])
 * @param currentZoom initial zoom ratio
 * @param modifier usual
 * @param onZoomChanged called when zoom ratio changed (user interaction)
 */
@SuppressLint("UnusedBoxWithConstraintsScope", "DefaultLocale")
@Composable
fun ZoomDial(
    zoomSteps: List<Float>,
    currentZoom: Float,
    modifier: Modifier = Modifier,
    minAngle: Float = -180f, // degrees for leftmost
    maxAngle: Float = 0f,  // degrees for rightmost
    majorTickEvery: Int = 1, // how often to show numeric labels (index steps)
    onZoomChanged: (Float) -> Unit = {}
) {
    require(zoomSteps.isNotEmpty())
    val sortedSteps = remember(zoomSteps) { zoomSteps.sorted() }
    val minZoom = sortedSteps.first().coerceAtLeast(0.01f)
    val maxZoom = sortedSteps.last()

    // We work in "t" normalized 0..1 on log scale
    fun zoomToT(z: Float): Float {
        val lmin = ln(minZoom)
        val lmax = ln(maxZoom)
        return ((ln(z.coerceIn(minZoom, maxZoom)) - lmin) / (lmax - lmin)).toFloat()
    }

    fun tToZoom(t: Float): Float {
        val lmin = ln(minZoom)
        val lmax = ln(maxZoom)
        val v = exp(lmin + (lmax - lmin) * t.toDouble()).toFloat()
        return v
    }

    fun tToAngle(t: Float): Float = minAngle + (maxAngle - minAngle) * t
    fun angleToT(angle: Float): Float =
        ((angle - minAngle) / (maxAngle - minAngle)).coerceIn(0f, 1f)

    val scope = rememberCoroutineScope()
    val animT = remember { Animatable(zoomToT(currentZoom)) }

    // When external currentZoom changes, update animT
    LaunchedEffect(currentZoom) {
        val target = zoomToT(currentZoom)
        animT.animateTo(target, animationSpec = spring(stiffness = Spring.StiffnessMedium))
    }

    // Precompute tick angles for provided zoom steps
    val stepAngles = remember(sortedSteps) {
        sortedSteps.map { z -> tToAngle(zoomToT(z)) }
    }

    // Controls how fast drag changes t relative to px
    val dragSensitivity = 0.0035f // tweak to taste

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val w = constraints.maxWidth.toFloat().takeIf { it > 0 }
            ?: with(LocalDensity.current) { 360.dp.toPx() }
        val h = constraints.maxHeight.toFloat().takeIf { it > 0 }
            ?: with(LocalDensity.current) { 220.dp.toPx() }

        // Canvas drawing area
        Canvas(
            modifier = Modifier
                .size(
                    width = Dp(w / LocalDensity.current.density),
                    height = Dp(h / LocalDensity.current.density)
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x
                            val deltaT = dx * dragSensitivity
                            scope.launch {
                                val newT = (animT.value + deltaT).coerceIn(0f, 1f)
                                animT.snapTo(newT)
                                onZoomChanged(tToZoom(newT))
                            }
                        },
                        onDragEnd = {
                            // optional: snap to nearest step
                            scope.launch {
                                val current = animT.value
                                // find nearest zoomStep t
                                val ts = sortedSteps.map { zoomToT(it) }
                                val nearest = ts.minByOrNull { abs(it - current) } ?: current
                                animT.animateTo(
                                    nearest,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                                )
                                onZoomChanged(tToZoom(animT.value))
                            }
                        },
                        onDragStart = { /* nothing */ }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Dial geometry
            val center = Offset(
                x = canvasWidth / 2f,
                y = canvasHeight * 0.9f
            ) // center below so arc occupies above
            val radius = min(canvasWidth, canvasHeight) * 0.55f

            // arc rect
            val arcRect = Rect(center = center, radius = radius)

            // draw semicircular arc (background)
            drawArc(
                color = Color(0x22000000),
                startAngle = minAngle,
                sweepAngle = maxAngle - minAngle,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round)
            )

            // ticks
            val longTick = radius * 0.12f
            val shortTick = radius * 0.06f
            stepAngles.forEachIndexed { index, angleDeg ->
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val outer = Offset(
                    x = center.x + cos(angleRad).toFloat() * radius,
                    y = center.y + sin(angleRad).toFloat() * radius
                )
                val inner = Offset(
                    x = center.x + cos(angleRad).toFloat() * (radius - if (index % (majorTickEvery) == 0) longTick else shortTick),
                    y = center.y + sin(angleRad).toFloat() * (radius - if (index % (majorTickEvery) == 0) longTick else shortTick)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = inner,
                    end = outer,
                    strokeWidth = radius * 0.015f,
                    cap = StrokeCap.Round
                )
            }

            // numeric labels for major ticks
            val labelOffset = radius - longTick - 24f
            sortedSteps.forEachIndexed { index, zoom ->
                if (index % majorTickEvery == 0) {
                    val angleDeg = stepAngles[index]
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val txtPos = Offset(
                        x = center.x + cos(angleRad).toFloat() * labelOffset,
                        y = center.y + sin(angleRad).toFloat() * labelOffset - 12f
                    )
                    drawContext.canvas.nativeCanvas.apply {
                        withSave {
                            // rotate text so it's readable (but here we simply draw horizontally)
                            val label =
                                if (zoom >= 1f) String.format("%.1fx", zoom) else String.format(
                                    "%.2fx",
                                    zoom
                                )
                            drawText(
                                label,
                                txtPos.x,
                                txtPos.y,
                                android.graphics.Paint().apply {
                                    isAntiAlias = true
                                    textSize = (radius * 0.10f)
                                    color = android.graphics.Color.WHITE
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.create(
                                        android.graphics.Typeface.DEFAULT,
                                        android.graphics.Typeface.BOLD
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Draw progress arc up to current animT
            val filledStart = minAngle
            val filledSweep = (tToAngle(animT.value) - minAngle)
            drawArc(
                color = Color(0xAAFFA500),
                startAngle = filledStart,
                sweepAngle = filledSweep,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = radius * 0.06f, cap = StrokeCap.Round)
            )

            // indicator triangle
            val indAngleDeg = tToAngle(animT.value)
            val indRad = Math.toRadians(indAngleDeg.toDouble())
            val indOuter = Offset(
                x = center.x + cos(indRad).toFloat() * (radius - radius * 0.06f / 2f),
                y = center.y + sin(indRad).toFloat() * (radius - radius * 0.06f / 2f)
            )
            // small triangle pointing inward
            val triSize = radius * 0.08f
            val perp = Offset(-sin(indRad).toFloat(), cos(indRad).toFloat())
            val p1 = indOuter + perp * (triSize * 0.5f)
            val p2 = indOuter - perp * (triSize * 0.5f)
            val p3 = indOuter - (Offset(cos(indRad).toFloat(), sin(indRad).toFloat()) * (triSize))
            drawPath(
                Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    close()
                },
                color = Color(0xFFFFA500)
            )

            // center circle showing numeric zoom
            val centerCircleRadius = radius * 0.34f
            val centerCircleCenter = Offset(center.x, center.y - radius * 0.32f)
            drawCircle(
                color = Color(0xFF111111),
                radius = centerCircleRadius,
                center = centerCircleCenter
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = centerCircleRadius,
                center = centerCircleCenter,
                style = Stroke(width = 2f)
            )

            // numeric zoom label (draw using native canvas for nice text)
            drawContext.canvas.nativeCanvas.apply {
                withSave {
                    val label = String.format("%.1fx", tToZoom(animT.value))
                    val p = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = (centerCircleRadius * 0.9f)
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.DEFAULT,
                            android.graphics.Typeface.BOLD
                        )
                    }
                    drawText(
                        label,
                        centerCircleCenter.x,
                        centerCircleCenter.y + (p.textSize * 0.35f),
                        p
                    )
                }
            }
        } // Canvas
    } // BoxWithConstraints
}

/**
 * Example usage preview
 */
@Composable
@Preview(showBackground = true, widthDp = 360, heightDp = 420)
fun ZoomDialPreview() {
    // Example zoomSteps similar to camera zoom ratios
    val zoomSteps = listOf(1f, 1.5f, 2f, 3f, 4f, 5f, 7f, 10f, 12f, 15f)
    var current by remember { androidx.compose.runtime.mutableFloatStateOf(2f) } // current zoom
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZoomDial(
            zoomSteps = zoomSteps,
            currentZoom = current,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            majorTickEvery = 1,
            onZoomChanged = { newZoom ->
                current = newZoom
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Current: ${"%.2fx".format(current)}",
            color = Color.White,
            modifier = Modifier.padding(8.dp)
        )
    }
}
