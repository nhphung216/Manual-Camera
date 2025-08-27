package com.ssolstice.camera.manual.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.models.CameraControlModel

@Preview
@Composable
fun CameraControlsPreview() {
    //CameraControls()
}

@Composable
fun CameraControls(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    valueFormated: String = "",

    isoMode: String = "",
    controlIdSelected: String = "white_balance",
    onControlIdSelected: (String) -> Unit = {},

    exposureValue: Float = 0f,
    onExposureChanged: (Float) -> Unit = {},
    onExposureReset: () -> Unit = {},

    isoValue: Float = 0f,
    onIsoChanged: (Float) -> Unit = {},
    onIsoReset: () -> Unit = {},

    shutterValue: Float = 0f,
    onShutterChanged: (Float) -> Unit = {},
    onShutterReset: () -> Unit = {},

    whiteBalanceValue: Float = 0f,
    focusValue: Float = 0f,
    sceneModeValue: Float = 0f,
    colorEffectValue: Float = 0f,

    onControlChanged: (CameraControlModel, Float) -> Unit,
    cameraControls: HashMap<String, CameraControlModel> = hashMapOf(),
) {
    Column(
        modifier = modifier
            .background(Color(0xF0000000))
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(end = 24.dp, bottom = 16.dp)
                    .align(Alignment.End)
                    .size(24.dp)
                    .clickable { onClose() }
            )

            val controlModel = cameraControls[controlIdSelected]
            controlModel?.let {
                when (controlIdSelected) {
                    "white_balance" -> {
                        LazyRow(
                            modifier = modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(controlModel.options) { optionModel ->
                                MenuItem(
                                    icon = painterResource(optionModel.icon),
                                    label = optionModel.text,
                                    onClick = {

                                    },
                                    isChanged = false,
                                    selected = controlIdSelected == optionModel.id,
                                )
                            }
                        }
                    }

                    "exposure" -> {
                        CustomValueSlider(
                            modifier = Modifier,
                            name = controlIdSelected,
                            value = exposureValue,
                            formated = valueFormated,
                            onValueChange = { value, formatDisplay ->
                                onExposureChanged(value)
                            },
                            onReset = { onExposureReset() },
                            valueRange = controlModel.valueRange,
                            labels = controlModel.labels,
                            steps = controlModel.steps
                        )
                    }

                    "iso" -> {
                        CustomValueSlider(
                            modifier = Modifier,
                            name = controlIdSelected,
                            value = isoValue,
                            formated = valueFormated,
                            onValueChange = { value, formatDisplay ->
                                onIsoChanged(value)
                            },
                            onReset = { onIsoReset() },
                            valueRange = controlModel.valueRange,
                            labels = controlModel.labels,
                            steps = controlModel.steps
                        )
                    }

                    "shutter" -> {
                        CustomValueSlider(
                            modifier = Modifier,
                            name = controlIdSelected,
                            value = shutterValue,
                            formated = valueFormated,
                            onValueChange = { value, formatDisplay ->
                                onShutterChanged(value)
                            },
                            onReset = { onShutterReset() },
                            valueRange = controlModel.valueRange,
                            labels = controlModel.labels,
                            steps = controlModel.steps
                        )
                    }

                    "focus" -> {
                        LazyRow(
                            modifier = modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(controlModel.options) { optionModel ->
                                MenuItem(
                                    icon = painterResource(optionModel.icon),
                                    label = optionModel.text,
                                    onClick = {

                                    },
                                    isChanged = false,
                                    selected = controlIdSelected == optionModel.id,
                                )
                            }
                        }
                    }

                    "scene_mode" -> {
                        LazyRow(
                            modifier = modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(controlModel.options) { optionModel ->
                                MenuItem(
                                    icon = painterResource(optionModel.icon),
                                    label = optionModel.text,
                                    onClick = {

                                    },
                                    isChanged = false,
                                    selected = controlIdSelected == optionModel.id,
                                )
                            }
                        }
                    }

                    "color_effect" -> {
                        LazyRow(
                            modifier = modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(controlModel.options) { optionModel ->
                                MenuItem(
                                    icon = painterResource(optionModel.icon),
                                    label = optionModel.text,
                                    onClick = {

                                    },
                                    isChanged = false,
                                    selected = controlIdSelected == optionModel.id,
                                )
                            }
                        }
                    }
                }
            }

            LazyRow(
                modifier = modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val controls = arrayListOf(
                    "white_balance",
                    "exposure",
                    "iso",
                    "shutter",
                    "focus",
                    "scene_mode",
                    "color_effect"
                )
                controls.forEach {
                    val control = cameraControls[it] ?: return@forEach
                    item {
                        MenuItem(
                            icon = painterResource(control.icon),
                            label = control.text,
                            onClick = {
                                onControlIdSelected(control.id)
                            },
                            isChanged = false,
                            selected = controlIdSelected == control.id,
                        )
                    }
                }
            }
        }
    }
}