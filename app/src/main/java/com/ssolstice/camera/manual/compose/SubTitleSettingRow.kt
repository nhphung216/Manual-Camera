package com.ssolstice.camera.manual.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


@Composable
fun SubTitleSettingRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFFFFAB00)
    )
}