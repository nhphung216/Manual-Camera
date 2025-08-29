package com.ssolstice.camera.manual.compose

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.compose.ui.theme.colorMain
import com.ssolstice.camera.manual.compose.widgets.CaptureCameraControls
import com.ssolstice.camera.manual.compose.widgets.CaptureRateSelector
import com.ssolstice.camera.manual.compose.widgets.PhotoModeSelector
import com.ssolstice.camera.manual.compose.widgets.VideoModeSelector
import com.ssolstice.camera.manual.models.PhotoModeUiModel
import com.ssolstice.camera.manual.models.VideoModeUiModel

@Preview
@Composable
fun CameraPreview() {
    CameraScreen()
}

val RecordColor = Color(0xFFDD2C00)
val WhiteColor = Color(0xFFD9D4D4)

@Composable
fun CameraScreen(
    isRecording: Boolean = false,
    isVideoRecordingPaused: Boolean = false,
    isPhotoMode: Boolean = true,
    galleryBitmap: Bitmap? = null,
    onOpenGallery: () -> Unit = {},

    onTogglePhotoVideoMode: () -> Unit = {},

    onTakePhoto: () -> Unit = {},
    onTakePhotoVideoSnapshot: () -> Unit = {},
    onPauseVideo: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    showCameraSettings: () -> Unit = {},
    showConfigTableSettings: () -> Unit = {},

    photoModes: List<PhotoModeUiModel> = emptyList(),
    onChangePhotoMode: (PhotoModeUiModel) -> Unit = {},

    videoModes: List<VideoModeUiModel> = emptyList(),
    onChangeVideoMode: (VideoModeUiModel) -> Unit = {},
    currentVideoMode: VideoModeUiModel? = null,

    captureRate: Float = 1f,
    onCaptureRateSelected: (Float) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x00101010))
    ) {

        if (!isPhotoMode && currentVideoMode != null && currentVideoMode.captureRates.isNotEmpty()) {
            CaptureRateSelector(
                captureRates = currentVideoMode.captureRates,
                selectedRate = captureRate,
                onCaptureRateSelected = { onCaptureRateSelected(it) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        CaptureCameraControls(
            isRecording = isRecording,
            isVideoRecordingPaused = isVideoRecordingPaused,
            isPhotoMode = isPhotoMode,
            galleryBitmap = galleryBitmap,
            openGallery = { onOpenGallery() },
            takePhoto = { onTakePhoto() },
            takePhotoVideoSnapshot = { onTakePhotoVideoSnapshot() },
            pauseVideo = { onPauseVideo() },
            switchCamera = { onSwitchCamera() },
        )

        if (isPhotoMode && photoModes.isNotEmpty()) {
            PhotoModeSelector(
                modes = photoModes,
                onModeSelected = { mode -> onChangePhotoMode(mode) }
            )
        }

        if (!isPhotoMode && videoModes.isNotEmpty()) {
            VideoModeSelector(
                modes = videoModes,
                onModeSelected = { mode -> onChangeVideoMode(mode) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SettingsApplications,
                contentDescription = null,
                tint = WhiteColor,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.DarkGray, shape = CircleShape)
                    .padding(8.dp)
                    .clickable {
                        showCameraSettings()
                    }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        Color.DarkGray, shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = WhiteColor,
                    modifier = Modifier
                        .clickable { onTogglePhotoVideoMode() }
                        .size(38.dp)
                        .background(
                            if (isPhotoMode) {
                                colorMain()
                            } else {
                                Color.DarkGray
                            }, shape = CircleShape
                        )
                        .padding(8.dp)
                )
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    tint = WhiteColor,
                    modifier = Modifier
                        .clickable { onTogglePhotoVideoMode() }
                        .size(38.dp)
                        .background(
                            if (!isPhotoMode) {
                                colorMain()
                            } else {
                                Color.DarkGray
                            },
                            shape = CircleShape
                        )
                        .padding(8.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = WhiteColor,
                    modifier = Modifier
                        .clickable {
                            showConfigTableSettings()
                        }
                        .size(38.dp)
                        .background(Color.DarkGray, shape = CircleShape)
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(34.dp))
    }
}