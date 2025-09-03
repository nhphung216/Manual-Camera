package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssolstice.camera.manual.compose.ui.theme.colorBackground

@Composable
fun ItemResolution(
    text: String,
    sub: String? = "",
    isSelect: Boolean = false,
    isPremium: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit = {},
    icon: Int = 0
) {
    Column(
        modifier = Modifier
            .clickable {
                onClick()
            }
            .padding(8.dp)
            .background(
                if (isSelect) {
                    colorBackground()
                } else {
                    Color.Black
                },
                shape = shape
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != 0) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = Color.White
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontSize = 16.sp,
                color = if (isPremium) {
                    Color.Yellow
                } else {
                    if (isSelect) Color.Black else Color.White
                }
            )
        }
        if (sub != "") {
            Text(
                text = sub ?: "",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 14.sp,
                color = if (isSelect) Color.Black else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}