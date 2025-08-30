package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssolstice.camera.manual.compose.ui.theme.colorMain
import com.ssolstice.camera.manual.compose.ui.theme.colorSecondary
import com.ssolstice.camera.manual.models.PhotoModeUiModel
import com.ssolstice.camera.manual.models.VideoModeUiModel

@Composable
fun PhotoModeSelector(
    modes: List<PhotoModeUiModel>, onModeSelected: (PhotoModeUiModel) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(modes) { item ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        if (item.selected) colorSecondary()
                        else Color.Transparent
                    )
                    .clickable { onModeSelected(item) }
                    .padding(8.dp)) {
                Text(
                    text = item.text, color = Color.White, fontSize = 16.sp
                )
            }
        }
    }
}


@Composable
fun VideoModeSelector(
    modes: List<VideoModeUiModel>, onModeSelected: (VideoModeUiModel) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier
                .padding(top = 32.dp, bottom = 1.dp)
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(modes) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (item.selected) colorSecondary()
                            else Color.Transparent
                        )
                        .clickable { onModeSelected(item) }
                        .padding(8.dp)) {
                    Text(
                        text = item.text, color = Color.White, fontSize = 16.sp
                    )
                }
            }
        }
    }
}