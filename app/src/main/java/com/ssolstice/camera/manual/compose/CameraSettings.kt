package com.ssolstice.camera.manual.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.models.ResolutionModel
import com.ssolstice.camera.manual.models.SettingItemModel

@Preview
@Composable
fun CameraSettingsPreview() {
    CameraSettings()
}

@Composable
fun CameraSettings(
    modifier: Modifier = Modifier,
    isPhotoMode: Boolean = true,
    resolutionSelected: String = "1920x1080",
    timerSelected: String = "Off",
    repeatSelected: String = "Off",
    speedSelected: String = "Off",
    onOpenSettings: () -> Unit = {},
    onResolutionChange: (String) -> Unit = {},
    onTimerChange: (String) -> Unit = {},
    onRepeatChange: (String) -> Unit = {},
    onSpeedChange: (String) -> Unit = {},
    onClose: () -> Unit = {},
    resolutions: ArrayList<ResolutionModel>,
    timers: ArrayList<SettingItemModel>,
    repeats: ArrayList<SettingItemModel>,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // title
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.photo_settings),
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFAEEA00),
                modifier = Modifier
                    .align(Alignment.CenterStart)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = WhiteColor,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterEnd)
                    .clickable {
                        onClose()
                    }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // flash
        TitleSettingRow(stringResource(R.string.more_light))
        SubTitleSettingRow(stringResource(R.string.flash_on))
        Spacer(modifier = Modifier.height(12.dp))

        // raw
        TitleSettingRow(stringResource(R.string.raw_photos))
        SubTitleSettingRow(stringResource(R.string.only_raw))
        Spacer(modifier = Modifier.height(12.dp))

        TitleSettingRow(stringResource(R.string.resolution))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            items(resolutions) { item ->
                ItemResolution(
                    item.res,
                    item.ratio,
                    isSelect = item.id == resolutionSelected,
                    onClick = {
                        onResolutionChange(item.id)
                    }
                )
            }
        }

        // timer (photo mode)
        TitleSettingRow(stringResource(R.string.preference_timer))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            items(timers) { item ->
                ItemResolution(
                    item.text, isSelect = item.id == timerSelected,
                    onClick = {
                        onTimerChange(item.id)
                    }
                )
            }
        }

        // repeat (photo mode)
        TitleSettingRow(stringResource(R.string.repeat))
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            items(repeats) { item ->
                ItemResolution(
                    item.text, isSelect = item.id == repeatSelected,
                    onClick = {
                        onRepeatChange(item.id)
                    }
                )
            }
        }

        // speed (video mode)
        if (!isPhotoMode) {
            TitleSettingRow(stringResource(R.string.speed))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                items(speeds) { item ->
                    ItemResolution(
                        item.text, isSelect = item.id == speedSelected,
                        onClick = {
                            onSpeedChange(item.id)
                        }
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = WhiteColor,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.End)
                .clickable {
                    onOpenSettings()
                }
        )
    }
}

@Composable
fun ItemResolution(
    text: String,
    value: String? = "",
    isSelect: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .background(Color.Black)
            .padding(8.dp)
            .clickable {
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 16.sp,
            color = Color(0xFFADABA8)
        )
        if (value != "") {
            Text(
                text = value ?: "",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 14.sp,
                color = Color(0xFF6B6A68)
            )
        }
    }
}