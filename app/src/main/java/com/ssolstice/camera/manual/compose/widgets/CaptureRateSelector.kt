package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssolstice.camera.manual.compose.WhiteColor

@Preview
@Composable
fun CaptureRateSelectorPreview() {
    CaptureRateSelector(
        captureRates = listOf(0.5f, 1f, 2f, 3f, 4f, 5f),
        selectedRate = 1f,
        onCaptureRateSelected = {}
    )
}

@Composable
fun CaptureRateSelector(
    captureRates: List<Float>,
    selectedRate: Float,
    onCaptureRateSelected: (Float) -> Unit,
    icon: ImageVector = Icons.Default.Timelapse,
) {
    Row(
        modifier = Modifier
            .padding(top = 48.dp, bottom = 24.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WhiteColor,
            modifier = Modifier
                .size(24.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        LazyRow(
            modifier = Modifier
                .widthIn(max = 250.dp)
                .background(
                    Color.DarkGray.copy(alpha = 0.7f), shape = RoundedCornerShape(24.dp)
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = {
                items(captureRates) { item ->
                    OptionSelectorItem(
                        text = (if (item < 1f) item.toString() else item.toInt().toString()) + "x",
                        isSelected = item == selectedRate,
                        onClick = { onCaptureRateSelected(item) }
                    )
                }
            }
        )
    }
}
