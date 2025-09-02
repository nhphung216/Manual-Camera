package com.ssolstice.camera.manual.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.compose.widgets.CustomValueSlider
import com.ssolstice.camera.manual.compose.widgets.MenuItem
import com.ssolstice.camera.manual.models.CameraControlModel
import com.ssolstice.camera.manual.models.ControlOptionModel

@Preview
@Composable
fun CameraControlsPreview() {
    CameraControls(
        modifier = Modifier,
        onClose = {},
        onResetAllSettings = {},
        valueFormated = "",
        controlOptionModel = ControlOptionModel(
            id = "white_balance",
            text = "White Balance",
            icon = R.drawable.ic_white_balance
        )
    )
}

@Composable
fun CameraControls(
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onResetAllSettings: () -> Unit = {},
    valueFormated: String = "",
    controlOptionModel: ControlOptionModel?,

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

    whiteBalanceValue: String = "",
    onWhiteBalanceChanged: (ControlOptionModel) -> Unit = {},

    onWhiteBalanceManualChanged: (Float) -> Unit = {},
    whiteBalanceManualValue: Float = 0f,

    focusValue: String = "",
    onFocusChanged: (ControlOptionModel) -> Unit = {},

    onFocusManualChanged: (Float) -> Unit = {},
    focusManualValue: Float = 0f,

    sceneModeValue: String = "",
    onSceneModeChanged: (ControlOptionModel) -> Unit = {},

    colorEffectValue: String = "",
    onColorEffectChanged: (ControlOptionModel) -> Unit = {},

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

                    "exposure" -> {
                        CustomValueSlider(
                            modifier = Modifier,
                            name = controlIdSelected,
                            value = exposureValue,
                            formated = valueFormated,
                            onValueChange = { value, formatDisplay -> onExposureChanged(value) },
                            onReset = { onExposureReset() },
                            valueRange = controlModel.valueRange
                        )
                    }

                    "iso" -> {
                        CustomValueSlider(
                            modifier = Modifier,
                            name = controlIdSelected,
                            value = isoValue,
                            formated = valueFormated,
                            onValueChange = { value, formatDisplay -> onIsoChanged(value) },
                            onReset = { onIsoReset() },
                            valueRange = controlModel.valueRange
                        )
                    }

                    "shutter" -> {
                        CustomValueSlider(
                            modifier = Modifier,
                            name = controlIdSelected,
                            value = shutterValue,
                            formated = valueFormated,
                            onValueChange = { value, formatDisplay -> onShutterChanged(value) },
                            onReset = { onShutterReset() },
                            valueRange = controlModel.valueRange
                        )
                    }

                    "white_balance" -> {
                        if (controlOptionModel != null && controlOptionModel.id == "manual") {
                            CustomValueSlider(
                                modifier = Modifier,
                                name = controlIdSelected,
                                value = whiteBalanceManualValue,
                                formated = valueFormated,
                                onValueChange = { value, formatDisplay ->
                                    onWhiteBalanceManualChanged(value)
                                },
                                showReset = false,
                                showBackgroundColor = true,
                                valueRange = controlOptionModel.valueRange
                            )
                        }

                        LazyRow(
                            modifier = modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(controlModel.options) { option ->
                                MenuItem(
                                    icon = painterResource(option.icon),
                                    label = option.text,
                                    onClick = { onWhiteBalanceChanged(option) },
                                    selected = whiteBalanceValue == option.id,
                                    size = 40,
                                    iconSize = 18,
                                )
                            }
                        }
                    }

                    "focus" -> {
                        if (controlOptionModel != null && controlOptionModel.id == "focus_mode_manual2") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    painter = painterResource(R.drawable.focus_mode_infinity),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                CustomValueSlider(
                                    modifier = Modifier.weight(1f),
                                    name = controlIdSelected,
                                    value = focusManualValue,
                                    formated = valueFormated,
                                    onValueChange = { value, formatDisplay ->
                                        onFocusManualChanged(value)
                                    },
                                    showReset = false,
                                    valueRange = 0f..100f
                                )
                                Icon(
                                    painter = painterResource(R.drawable.ic_macro_focus),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
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
                                        onFocusChanged(optionModel)
                                    },
                                    selected = focusValue == optionModel.id,
                                    size = 40,
                                    iconSize = 18,
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
                                    onClick = { onSceneModeChanged(optionModel) },
                                    selected = sceneModeValue == optionModel.id,
                                    size = 40,
                                    iconSize = 18,
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
                                    onClick = { onColorEffectChanged(optionModel) },
                                    selected = colorEffectValue == optionModel.id,
                                    size = 40,
                                    iconSize = 18,
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
                item {
                    MenuItem(
                        icon = painterResource(R.drawable.outline_reset_settings),
                        label = stringResource(R.string.reset_all),
                        onClick = { onResetAllSettings() },
                    )
                }
                controls.forEach {
                    val control = cameraControls[it] ?: return@forEach
                    var isChanged = false
                    when (it) {
                        "exposure" -> {
                            isChanged = exposureValue != 0f
                        }
                        "iso" -> { // auto
                            isChanged = isoValue != 0f
                        }
                        "shutter" -> { // auto
                            isChanged = shutterValue != 0f
                        }
                    }
                    item {
                        MenuItem(
                            icon = painterResource(control.icon),
                            label = control.text,
                            onClick = { onControlIdSelected(control.id) },
                            selected = controlIdSelected == control.id,
                            isChanged = isChanged
                        )
                    }
                }
            }
        }
    }
}