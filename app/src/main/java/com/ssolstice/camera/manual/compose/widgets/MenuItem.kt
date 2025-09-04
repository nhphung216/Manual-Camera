package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssolstice.camera.manual.R

@Preview
@Composable
fun MenuItemPreview() {
    MenuItem(
        icon = painterResource(R.drawable.ic_white_balance),
        label = "White Balance",
        onClick = {},
        isChanged = false
    )
}

@Composable
fun MenuItem(
    icon: Painter,
    label: String,
    size: Int = 46,
    iconSize: Int = 24,
    isChanged: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable {
                onClick()
            }
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .size(size.dp)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = CircleShape
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                modifier = Modifier
                    .size(iconSize.dp)
                    .align(Alignment.Center),
                painter = icon,
                contentDescription = label,
                colorFilter = ColorFilter.tint(
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ),
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
            fontSize = 14.sp,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}