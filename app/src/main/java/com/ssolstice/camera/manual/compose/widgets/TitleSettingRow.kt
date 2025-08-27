package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Composable
fun TitleSettingRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFFD7D4D4),
        modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
    )
}