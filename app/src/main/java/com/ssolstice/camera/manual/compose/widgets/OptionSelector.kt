package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssolstice.camera.manual.compose.WhiteColor
import com.ssolstice.camera.manual.compose.ui.theme.colorMain
import com.ssolstice.camera.manual.models.OptionRes

@Preview
@Composable
fun OptionSelectorPreview() {
    OptionSelector(
        options = listOf(
            OptionRes(
                text = "2",
                value = "2"
            ),
            OptionRes(
                text = "3",
                value = "3"
            ),
            OptionRes(
                text = "4",
                value = "4"
            ),
            OptionRes(
                text = "5",
                value = "5"
            ),
            OptionRes(
                text = "6",
                value = "6"
            ),
            OptionRes(
                text = "7",
                value = "7"
            ),
            OptionRes(
                text = "8",
                value = "8"
            )
        ),
        selectedOption = OptionRes(
            text = "3",
            value = "3"
        ),
        onSelectedOption = {}
    )
}

@Composable
fun OptionSelector(
    options: List<OptionRes>,
    selectedOption: OptionRes,
    onSelectedOption: (OptionRes) -> Unit,
    icon: ImageVector = Icons.Default.Timelapse,
) {
    Row(
        modifier = Modifier
            .padding(top = 48.dp, bottom = 24.dp)
            .fillMaxWidth()
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WhiteColor,
            modifier = Modifier
                .size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        LazyRow(
            modifier = Modifier
                .widthIn(max = 250.dp)
                .background(
                    Color.DarkGray.copy(alpha = 0.7f), shape = RoundedCornerShape(24.dp)
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = {
                items(options) { item ->
                    OptionSelectorItem(
                        text = item.text,
                        isSelected = item.value == selectedOption.value,
                        onClick = { onSelectedOption(item) }
                    )
                }
            }
        )
    }
}

@Composable
fun OptionSelectorItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isSelected) colorMain() else Color.Transparent
            )
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .widthIn(min = 32.dp)
                .padding(horizontal = 2.dp),
            textAlign = TextAlign.Center
        )
    }
}
