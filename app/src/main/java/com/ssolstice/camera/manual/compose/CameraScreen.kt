package com.ssolstice.camera.manual.compose

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.R

@Preview
@Composable
fun CameraPreview() {
    CameraScreen()
}

val RecordColor = Color(0xFFDD2C00)
val WhiteColor = Color(0xFFD9D4D4)

val list = arrayListOf(
    "Option 1",
    "Option 2",
    "Option 3",
    "Option 4",
    "Option 5",
)


@Composable
fun CameraScreen(
    isRecording: Boolean = false,
    isVideoRecordingPaused: Boolean = false,
    isPhotoMode: Boolean = true,
    galleryBitmap: Bitmap? = null,
    openGallery: () -> Unit = {},
    switchVideoMode: () -> Unit = {},
    takePhoto: () -> Unit = {},
    takePhotoVideoSnapshot: () -> Unit = {},
    pauseVideo: () -> Unit = {},
    switchCamera: () -> Unit = {},
    showCameraSettings: () -> Unit = {},
    showConfigTableSettings: () -> Unit = {},
) {
    Log.e("CameraScreen", "isRecording: $isRecording")
    Log.e("CameraScreen", "isPhotoMode: $isPhotoMode")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x00101010))
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth(),
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

        if (list.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(list.size) {
                    Text(
                        text = list[it],
                        modifier = Modifier
                            .background(Color.DarkGray, shape = RoundedCornerShape(16.dp))
                            .padding(vertical = 4.dp, horizontal = 12.dp),
                        color = WhiteColor
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        Color.DarkGray, shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CameraAlt,
                    contentDescription = null,
                    tint = WhiteColor,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isPhotoMode) {
                                Color(0xFF007AFF)
                            } else {
                                Color.DarkGray
                            }, shape = CircleShape
                        )
                        .padding(8.dp)
                        .clickable {
                            switchVideoMode()
                        }
                )
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    tint = WhiteColor,
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (!isPhotoMode) {
                                RecordColor
                            } else {
                                Color.DarkGray
                            },
                            shape = CircleShape
                        )
                        .padding(8.dp)
                        .clickable {
                            switchVideoMode()
                        }
                )
            }
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = WhiteColor,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.DarkGray, shape = CircleShape)
                    .padding(8.dp)
                    .clickable {
                        showConfigTableSettings()
                    }
            )
        }

        Spacer(modifier = Modifier.height(34.dp))
    }
}