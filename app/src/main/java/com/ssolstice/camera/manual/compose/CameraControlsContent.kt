package com.ssolstice.camera.manual.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.R

const val STRENGTH_DEFAULT = 1f
const val BRIGHTNESS_DEFAULT = 0f
const val EXPOSURE_DEFAULT = 0f
const val CONTRAST_DEFAULT = 1f

@Preview
@Composable
fun CameraMenuPreview() {
    CameraControlsContent()
}

@SuppressLint("DefaultLocale")
@Composable
fun CameraControlsContent(
    modifier: Modifier = Modifier,
    onExposureChanged: (Float) -> Unit = {},
    onShutterChanged: (Float) -> Unit = {},
    onIsoChanged: (Float) -> Unit = {},
    exposureValue: Float = EXPOSURE_DEFAULT,
    shutterValue: Float = STRENGTH_DEFAULT,
    isoValue: Float = BRIGHTNESS_DEFAULT,

) {
    var itemSelected by remember { mutableStateOf("") }

    val shutterValueRange: ClosedFloatingPointRange<Float> = 0f..2f
    val shutterLabels = listOf("0%", "50%", "100%", "150%", "200%")
    val shutterSteps = 20

    val isoValueRange: ClosedFloatingPointRange<Float> = 0f..1f
    val isoLabels = listOf("0°", "90°", "180°", "270°", "360°")
    val isoSteps = 20

    val exposureValueRange: ClosedFloatingPointRange<Float> = 0f..10f
    val exposureLabels = listOf("0%", "25%", "50%", "75%", "100%")
    val exposureSteps = 50

    var showResetParams by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        // confirm reset parameters
        if (showResetParams) {
            ConfirmResetParameters(onCancel = {
                showResetParams = false
            }, onReset = {
                showResetParams = false
                itemSelected = ""
                onExposureChanged(EXPOSURE_DEFAULT)
            })
        }

        // shutter
        if (itemSelected == "shutter") {
            CustomValueSlider(
                modifier = Modifier,
                name = itemSelected,
                value = shutterValue,
                onValueChange = { v, formatDisplay ->
                    onShutterChanged(v)
                },
                valueRange = shutterValueRange,
                labels = shutterLabels,
                steps = shutterSteps
            )
        }

        // iso
        if (itemSelected == "iso") {
            CustomValueSlider(
                modifier = Modifier,
                name = itemSelected,
                value = isoValue,
                onValueChange = { v, formatDisplay ->
                    onIsoChanged(v)
                },
                valueRange = isoValueRange,
                labels = isoLabels,
                steps = isoSteps
            )
        }

        // exposure
        if (itemSelected == "exposure") {
            CustomValueSlider(
                modifier = Modifier,
                name = itemSelected,
                value = exposureValue,
                onValueChange = { v, formatDisplay ->
                    onExposureChanged(v)
                },
                valueRange = exposureValueRange,
                labels = exposureLabels,
                steps = exposureSteps
            )
        }

        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .background(Color.Black),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            item {
                MenuItem(
                    icon = painterResource(R.drawable.outline_reset_settings),
                    label = stringResource(R.string.reset_all),
                    onClick = {
                        showResetParams = !showResetParams
                        if (showResetParams) {
                            itemSelected = ""
                        }
                    },
                    isChanged = false,
                    selected = false,
                )
            }

//            item {
//                MenuItem(
//                    icon = painterResource(R.drawable.ic_wb_auto),
//                    label = stringResource(R.string.white_balance),
//                    onClick = {
//                        itemSelected = if (itemSelected == "white_balance") "" else "white_balance"
//                    },
//                    isChanged = strengthValue != STRENGTH_DEFAULT,
//                    selected = itemSelected == "white_balance",
//                )
//            }
            item {
                MenuItem(
                    icon = painterResource(R.drawable.ic_exposure_24),
                    label = stringResource(R.string.exposure),
                    onClick = {
                        showResetParams = false
                        itemSelected = if (itemSelected == "exposure") "" else "exposure"
                    },
                    isChanged = exposureValue != BRIGHTNESS_DEFAULT,
                    selected = itemSelected == "exposure",
                )
            }
            item {
                MenuItem(
                    icon = painterResource(R.drawable.iso_icon),
                    label = stringResource(R.string.iso),
                    onClick = {
                        showResetParams = false
                        itemSelected = if (itemSelected == "iso") "" else "iso"
                    },
                    isChanged = isoValue != BRIGHTNESS_DEFAULT,
                    selected = itemSelected == "iso",
                )
            }
            item {
                MenuItem(
                    icon = painterResource(R.drawable.ic_shutter_speed_24),
                    label = stringResource(R.string.shutter),
                    onClick = {
                        showResetParams = false
                        itemSelected = if (itemSelected == "shutter") "" else "shutter"
                    },
                    isChanged = shutterValue != CONTRAST_DEFAULT,
                    selected = itemSelected == "shutter",
                )
            }
//            item {
//                MenuItem(
//                    icon = painterResource(R.drawable.ic_center_focus_24),
//                    label = stringResource(R.string.focus),
//                    onClick = {
//                        showResetParams = false
//                        itemSelected = if (itemSelected == "focus") "" else "focus"
//                    },
//                    isChanged = focusValue != EXPOSURE_DEFAULT,
//                    selected = itemSelected == "focus",
//                )
//            }
//            item {
//                MenuItem(
//                    icon = painterResource(R.drawable.ic_auto),
//                    label = stringResource(R.string.scene_mode),
//                    onClick = {
//                        showResetParams = false
//                        itemSelected = if (itemSelected == "scene_mode") "" else "scene_mode"
//                    },
//                    isChanged = sceneModeValue != EXPOSURE_DEFAULT,
//                    selected = itemSelected == "scene_mode",
//                )
//            }
//            item {
//                MenuItem(
//                    icon = painterResource(R.drawable.color_effect_negative),
//                    label = stringResource(R.string.color_effect),
//                    onClick = {
//                        showResetParams = false
//                        itemSelected = if (itemSelected == "color_effect") "" else "color_effect"
//                    },
//                    isChanged = colorEffectValue != EXPOSURE_DEFAULT,
//                    selected = itemSelected == "color_effect",
//                )
//            }
        }
    }
}

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