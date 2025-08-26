package com.ssolstice.camera.manual.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun MenuItem(
    icon: Painter,
    label: String,
    isChanged: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .clickable {
                onClick()
            }
            .padding(vertical = 8.dp, horizontal = 8.dp)
            .background(Color.Black)) {
        Box(
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Black),
                painter = icon,
                contentDescription = label,
                tint = if (selected) Color.Green else Color.White
            )
            if (isChanged) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color.Red, shape = RoundedCornerShape(50))
                        .align(Alignment.TopEnd)
                )
            }
        }
        Text(
            text = label,
            color = if (selected) Color.Green else Color.White,
            fontSize = 10.sp,
        )
    }
}