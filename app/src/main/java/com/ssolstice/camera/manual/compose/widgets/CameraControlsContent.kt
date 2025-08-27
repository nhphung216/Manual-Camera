package com.ssolstice.camera.manual.compose.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.R

const val STRENGTH_DEFAULT = 1f
const val BRIGHTNESS_DEFAULT = 0f
const val EXPOSURE_DEFAULT = 0f
const val CONTRAST_DEFAULT = 1f

@Composable
fun ConfirmResetParameters(onCancel: () -> Unit = {}, onReset: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF000000)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.reset_all),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ElevatedButton(
                onClick = {
                    onCancel()
                },
                shape = RoundedCornerShape(12),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.LightGray
                ),
            ) {
                Text(
                    stringResource(R.string.cancel),
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            ElevatedButton(
                onClick = {
                    onReset()
                },
                shape = RoundedCornerShape(12),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Yellow
                ),
            ) {
                Text(
                    stringResource(R.string.yes),
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}