package com.ssolstice.camera.manual.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.ssolstice.camera.manual.MyApplicationInterface
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.compose.ui.theme.colorMain
import com.ssolstice.camera.manual.compose.widgets.ItemResolution
import com.ssolstice.camera.manual.compose.widgets.SubTitleSettingRow
import com.ssolstice.camera.manual.compose.widgets.TitleSettingRow
import com.ssolstice.camera.manual.models.PhotoModeUiModel
import com.ssolstice.camera.manual.models.SettingItemModel

val resolutions = arrayListOf(
    SettingItemModel(
        id = "1920x10801",
        text = "1920x1080",
        sub = "16:9 (1280x720)",
        selected = true
    ),
    SettingItemModel(
        id = "1920x1080",
        text = "1920x1080",
        sub = "16:9 (1280x720)",
        selected = false
    ),
    SettingItemModel(
        id = "1920x1080",
        text = "1920x1080",
        sub = "16:9 (1280x720)",
        selected = false
    ),
    SettingItemModel(
        id = "1920x1080",
        text = "1920x1080",
        sub = "16:9 (1280x720)",
        selected = false
    ),
    SettingItemModel(
        id = "1920x1080",
        text = "1920x1080",
        sub = "16:9 (1280x720)",
        selected = false
    ),
    SettingItemModel(
        id = "1920x1080",
        text = "1920x1080",
        sub = "16:9 (1280x720)",
        selected = false
    ),
)

@Preview
@Composable
fun CameraSettingsPreview() {
    CameraSettings(
        isPhotoMode = true,
        resolutions = resolutions,
        timers = arrayListOf(),
        repeats = arrayListOf(),
        speeds = arrayListOf(),
        flashList = mutableListOf(
            SettingItemModel(
                id = "flash_off",
                text = "Flash off",
                selected = true,
                icon = R.drawable.flash_off
            ),
            SettingItemModel(
                id = "flash_off",
                text = "Flash off",
                selected = false,
                icon = R.drawable.flash_on
            ),
            SettingItemModel(
                id = "flash_off",
                text = "Flash off",
                selected = false,
                icon = R.drawable.flash_auto
            ),
        )
    )
}

@Composable
fun CameraSettings(
    modifier: Modifier = Modifier,
    isPhotoMode: Boolean = true,
    currentPhotoMode: PhotoModeUiModel? = null,
    resolutionSelected: SettingItemModel? = null,
    resolutionOfVideoSelected: SettingItemModel? = null,
    timerSelected: SettingItemModel? = null,
    repeatSelected: SettingItemModel? = null,
    speedSelected: SettingItemModel? = null,
    flashSelected: SettingItemModel? = null,
    rawSelected: SettingItemModel? = null,
    onOpenSettings: () -> Unit = {},
    onResolutionChange: (SettingItemModel) -> Unit = {},
    onResolutionOfVideoChange: (SettingItemModel) -> Unit = {},
    onTimerChange: (SettingItemModel) -> Unit = {},
    onRepeatChange: (SettingItemModel) -> Unit = {},
    onSpeedChange: (SettingItemModel) -> Unit = {},
    onFlashChange: (SettingItemModel) -> Unit = {},
    onRawChange: (SettingItemModel) -> Unit = {},
    onClose: () -> Unit = {},
    flashList: MutableList<SettingItemModel> = mutableListOf(),
    rawList: MutableList<SettingItemModel> = mutableListOf(),
    resolutions: MutableList<SettingItemModel> = mutableListOf(),
    timers: MutableList<SettingItemModel> = mutableListOf(),
    repeats: MutableList<SettingItemModel> = mutableListOf(),
    resolutionsVideo: MutableList<SettingItemModel> = mutableListOf(),
    speeds: MutableList<SettingItemModel> = mutableListOf(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xF0000000))
            .padding(top = 16.dp)
    ) {
        // title
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(if (isPhotoMode) R.string.photo_settings else R.string.video_settings),
                style = MaterialTheme.typography.titleLarge,
                color = colorMain(),
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = WhiteColor,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.CenterEnd)
                    .clickable { onClose() }
            )
        }

        // flash
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Column {
                TitleSettingRow(stringResource(R.string.more_light))
                SubTitleSettingRow(stringResource(R.string.flash_on))
            }
            LazyRow(modifier = Modifier.align(Alignment.CenterEnd)) {
                items(flashList) { item ->
                    ItemResolution(
                        shape = CircleShape,
                        text = item.text,
                        isSelect = item.id == flashSelected?.id,
                        icon = item.icon,
                        onClick = { onFlashChange(item) }
                    )
                }
            }
        }

        if (isPhotoMode) {
            if (currentPhotoMode?.mode != MyApplicationInterface.PhotoMode.Panorama) {
                // raw jpeg
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column {
                        TitleSettingRow(stringResource(R.string.raw_photos))
                        SubTitleSettingRow(stringResource(R.string.only_raw))
                    }
                    LazyRow(modifier = Modifier.align(Alignment.CenterEnd)) {
                        items(rawList) { item ->
                            ItemResolution(
                                shape = CircleShape,
                                text = item.text,
                                icon = item.icon,
                                isSelect = item.id == rawSelected?.id,
                                onClick = { onRawChange(item) },
                            )
                        }
                    }
                }

                // resolution
                TitleSettingRow(stringResource(R.string.resolution))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(resolutions) { item ->
                        ItemResolution(
                            text = item.text,
                            sub = item.sub,
                            isSelect = item.id == resolutionSelected?.id,
                            onClick = { onResolutionChange(item) },
                        )
                    }
                }

                // timer (photo mode)
                if (timers.isNotEmpty()) {
                    TitleSettingRow(stringResource(R.string.preference_timer))
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        items(timers) { item ->
                            ItemResolution(
                                item.text, isSelect = item.id == timerSelected?.id,
                                onClick = { onTimerChange(item) },
                            )
                        }
                    }
                }

                // repeat (photo mode)
                if (repeats.isNotEmpty()) {
                    TitleSettingRow(stringResource(R.string.repeat))
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        items(repeats) { item ->
                            ItemResolution(
                                item.text, isSelect = item.id == repeatSelected?.id,
                                onClick = { onRepeatChange(item) },
                            )
                        }
                    }
                }
            }
        } else {
            // speed (video mode)
            TitleSettingRow(stringResource(R.string.speed))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                items(speeds) { item ->
                    ItemResolution(
                        item.text, isSelect = item.id == speedSelected?.id,
                        onClick = { onSpeedChange(item) },
                    )
                }
            }

            TitleSettingRow(stringResource(R.string.resolution))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                items(resolutionsVideo) { item ->
                    ItemResolution(
                        item.text, isSelect = item.id == resolutionOfVideoSelected?.id,
                        onClick = { onResolutionOfVideoChange(item) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(top = 4.dp, end = 16.dp, bottom = 16.dp)
                .border(
                    width = 1.dp,
                    color = colorMain(),
                    shape = RoundedCornerShape(16.dp) // bo góc
                )
                .align(Alignment.End)
                .clickable { onOpenSettings() }
        ) {
            Text(
                stringResource(R.string.more_settings),
                style = MaterialTheme.typography.bodySmall,
                color = colorMain(),
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
            )
        }
    }
}