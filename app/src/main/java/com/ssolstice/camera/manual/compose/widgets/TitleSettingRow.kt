package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.compose.ui.theme.colorMain

@Composable
fun TitleSettingRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraLight),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp)
    )
}

@Composable
fun SubTitleSettingRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = colorMain(),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp)
    )
}