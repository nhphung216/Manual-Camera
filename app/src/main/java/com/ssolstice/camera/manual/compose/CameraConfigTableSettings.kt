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
import com.ssolstice.camera.manual.R

@Preview
@Composable
fun CameraConfigTableSettingsPreview() {
    CameraConfigTableSettings()
}

@Composable
fun CameraConfigTableSettings(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
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
                stringResource(R.string.preference_category_camera_controls),
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
        Spacer(modifier = Modifier.height(8.dp))

        // flash
        Text(stringResource(R.string.more_light), color = WhiteColor)
        // raw
        Text(stringResource(R.string.raw_photos), color = WhiteColor)
        // resolution
        Text(stringResource(R.string.resolution), color = WhiteColor)
        // timer (photo mode)
        Text(stringResource(R.string.preference_timer), color = WhiteColor)
        // repeat (photo mode)
        Text(stringResource(R.string.repeat), color = WhiteColor)
        // speed (video mode)
        Text(stringResource(R.string.speed), color = WhiteColor)
    }
}