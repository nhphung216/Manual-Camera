package com.ssolstice.camera.manual.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp


@Composable
fun CircleBitmapImage(
    modifier: Modifier = Modifier,
    bitmap: Bitmap,
    size: Int = 100,
    border: Int = 2,
    borderColour: Color = Color.White,
    contentDescription: String = "Circular Image",
    onClick: () -> Unit = {}
) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(border.dp, borderColour, CircleShape)
            .clickable {
                onClick()
            },
        contentScale = ContentScale.Crop
    )
}