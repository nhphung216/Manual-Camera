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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.MyApplicationInterface
import com.ssolstice.camera.manual.compose.ui.theme.colorBackground
import com.ssolstice.camera.manual.compose.widgets.CaptureCameraControls
import com.ssolstice.camera.manual.compose.widgets.CaptureRateSelector
import com.ssolstice.camera.manual.compose.widgets.OptionSelector
import com.ssolstice.camera.manual.compose.widgets.PhotoModeSelector
import com.ssolstice.camera.manual.compose.widgets.VideoModeSelector
import com.ssolstice.camera.manual.models.OptionRes
import com.ssolstice.camera.manual.models.PhotoModeUiModel
import com.ssolstice.camera.manual.models.VideoModeUiModel
import com.ssolstice.camera.manual.utils.Logger

val photoModes = listOf(
    PhotoModeUiModel(
        text = "Panorama",
        selected = false,
        mode = MyApplicationInterface.PhotoMode.Panorama
    ),
    PhotoModeUiModel(
        text = "FastBurst",
        selected = true,
        mode = MyApplicationInterface.PhotoMode.FastBurst
    ),
    PhotoModeUiModel(
        text = "Focus Bracketing",
        selected = false,
        mode = MyApplicationInterface.PhotoMode.FocusBracketing
    ),
    PhotoModeUiModel(
        text = "Standard",
        selected = false,
        mode = MyApplicationInterface.PhotoMode.Standard
    ),
    PhotoModeUiModel(
        text = "Expo Bracketing",
        selected = false,
        mode = MyApplicationInterface.PhotoMode.ExpoBracketing
    ),
    PhotoModeUiModel(
        text = "Noise Reduction",
        selected = false,
        mode = MyApplicationInterface.PhotoMode.NoiseReduction
    ),
    PhotoModeUiModel(
        text = "DRO",
        selected = false,
        mode = MyApplicationInterface.PhotoMode.DRO
    )
)

val videoModes = listOf(
    VideoModeUiModel(
        text = "Slow Motion",
        selected = true,
        mode = MyApplicationInterface.VideoMode.Slow_Motion,
        captureRates = arrayListOf(0.125f, 0.25f, 0.5f)
    ),
    VideoModeUiModel(
        text = "Standard",
        selected = false,
        mode = MyApplicationInterface.VideoMode.Video,
        captureRates = arrayListOf(1f)
    ),
    VideoModeUiModel(
        text = "Time Lapse",
        selected = false,
        mode = MyApplicationInterface.VideoMode.Time_Lapse,
        captureRates = arrayListOf(2f, 3f, 4f, 5f, 10f, 20f, 30f)
    )
)

@Preview
@Composable
fun CameraPreview() {
    CameraScreen(
        isRecording = false,
        isVideoRecordingPaused = false,
        isPhotoMode = true,
        galleryBitmap = null,

        photoModes = photoModes,
        onChangePhotoMode = {
            Logger.d("CameraPreview", "onChangePhotoMode: $it")
        },
        currentPhotoMode = photoModes[0],

        videoModes = videoModes,
        onChangeVideoMode = {
            Logger.d("CameraPreview", "onChangeVideoMode: $it")
        },

        currentVideoMode = videoModes[0],
        captureRate = 0.25f,
        onCaptureRateSelected = {
            Logger.d("CameraPreview", "onCaptureRateSelected: $it")
        }
    )
}

val RecordColor = Color(0xFFDD2C00)
val WhiteColor = Color(0xFFFFFFFF)

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    isVideoRecordingPaused: Boolean = false,
    isPhotoMode: Boolean = true,
    galleryBitmap: Bitmap? = null,
    onOpenGallery: () -> Unit = {},
    onTogglePhotoVideoMode: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onTakeLongPhoto: () -> Unit = {},
    takePhotoButtonLongClickCancelled: () -> Unit = {},
    onTakePhotoVideoSnapshot: () -> Unit = {},
    onPauseVideo: () -> Unit = {},
    onSwitchCamera: () -> Unit = {},
    showCameraSettings: () -> Unit = {},
    showCameraControls: () -> Unit = {},

    photoModes: List<PhotoModeUiModel> = emptyList(),
    onChangePhotoMode: (PhotoModeUiModel) -> Unit = {},
    currentPhotoMode: PhotoModeUiModel? = null,
    selectedPhotoOption: OptionRes = OptionRes(),
    onSelectedPhotoOption: (OptionRes) -> Unit = {},

    videoModes: List<VideoModeUiModel> = emptyList(),
    onChangeVideoMode: (VideoModeUiModel) -> Unit = {},
    currentVideoMode: VideoModeUiModel? = videoModes[0],

    captureRate: Float = 1f,
    onCaptureRateSelected: (Float) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x00101010))
    ) {
        if (isPhotoMode && currentPhotoMode != null && currentPhotoMode.options.isNotEmpty()) {
            OptionSelector(
                icon = Icons.Default.PhotoLibrary,
                options = currentPhotoMode.options,
                selectedOption = selectedPhotoOption,
                onSelectedOption = { onSelectedPhotoOption(it) }
            )
        }

        if (!isPhotoMode && currentVideoMode != null && currentVideoMode.captureRates.isNotEmpty()) {
            CaptureRateSelector(
                captureRates = currentVideoMode.captureRates,
                selectedRate = captureRate,
                onCaptureRateSelected = { onCaptureRateSelected(it) },
                icon = if (captureRate > 1f) Icons.Default.Timelapse else Icons.Default.SlowMotionVideo,
            )
        }

        CaptureCameraControls(
            isRecording = isRecording,
            isVideoRecordingPaused = isVideoRecordingPaused,
            isPhotoMode = isPhotoMode,
            galleryBitmap = galleryBitmap,
            openGallery = { onOpenGallery() },
            takePhoto = { onTakePhoto() },
            takeLongPhoto = { onTakeLongPhoto() },
            takePhotoButtonLongClickCancelled = { takePhotoButtonLongClickCancelled() },
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
                imageVector = if (!isPhotoMode) Icons.Default.VideoSettings else Icons.Default.SettingsApplications,
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
                                colorBackground()
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
                                colorBackground()
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
                            showCameraControls()
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