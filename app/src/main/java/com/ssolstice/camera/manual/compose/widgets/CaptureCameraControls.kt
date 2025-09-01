package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.compose.CircleBitmapImage
import com.ssolstice.camera.manual.compose.WhiteColor

@Preview
@Composable
fun CameraControlsPreview() {
    CaptureCameraControls(
        isRecording = false,
        isVideoRecordingPaused = false,
        isPhotoMode = true,
        galleryBitmap = null,
        openGallery = {},
        takePhoto = {},
        takePhotoVideoSnapshot = {},
        pauseVideo = {},
        switchCamera = {},
    )
}

@Composable
fun CaptureCameraControls(
    isRecording: Boolean = false,
    isVideoRecordingPaused: Boolean = false,
    isPhotoMode: Boolean = true,
    galleryBitmap: android.graphics.Bitmap? = null,
    openGallery: () -> Unit = {},
    takePhoto: () -> Unit = {},
    takePhotoVideoSnapshot: () -> Unit = {},
    pauseVideo: () -> Unit = {},
    switchCamera: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            OuterRing(
                onClick = { pauseVideo() },
                modifier =
                    Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (isVideoRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    tint = WhiteColor,
                    modifier = Modifier
                        .size(24.dp)
                )
            }
        } else {
            if (galleryBitmap != null) {
                CircleBitmapImage(
                    bitmap = galleryBitmap,
                    size = 56,
                    onClick = { openGallery() })
            } else {
                OuterRing(
                    onClick = { openGallery() },
                    modifier =
                        Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = WhiteColor,
                        modifier = Modifier
                            .size(24.dp)
                    )
                }
            }

        }

        ShutterButton(
            isRecording = isRecording,
            isPhotoMode = isPhotoMode,
            onClick = {
                takePhoto()
            },
            onLongPress = {
                takePhoto()
            }
        )

        if (isRecording) {
            OuterRing(
                onClick = {}, modifier =
                    Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraEnhance,
                    contentDescription = null,
                    tint = WhiteColor,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            takePhotoVideoSnapshot()
                        })
            }
        } else {
            SwitchCameraButton(switchCamera = { switchCamera() })
        }
    }
}