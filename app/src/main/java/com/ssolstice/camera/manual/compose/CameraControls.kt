package com.ssolstice.camera.manual.compose

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        onResetAllSettings = {},
        valueFormated = "",
        controlOptionModel = ControlOptionModel(
            id = "white_balance",
        ),
        cameraControls = hashMapOf(
            "white_balance" to CameraControlModel(
                id = "white_balance",
                text = "White Balance",
                icon = R.drawable.ic_white_balance,
            ),
            "exposure" to CameraControlModel(
                id = "exposure",
                text = "Exposure",
                icon = R.drawable.ic_exposure_24,
            ),
            "iso" to CameraControlModel(
                id = "iso",
                text = "ISO",
                icon = R.drawable.ic_iso_film,
            ),
            "shutter" to CameraControlModel(
                id = "shutter",
                text = "Shutter",
                icon = R.drawable.ic_shutter,
            ),
            "focus" to CameraControlModel(
                id = "focus",
                text = "Focus",
                icon = R.drawable.ic_auto_focus,
            ),
            "scene_mode" to CameraControlModel(
                id = "scene_mode",
                text = "Scene Mode",
                icon = R.drawable.scene_mode_fireworks,
            ),
            "color_effect" to CameraControlModel(
                id = "color_effect",
                text = "Color Effect",
                icon = R.drawable.color_effect_negative,
            )
        )
    )
}

@Composable
fun CameraControls(
    modifier: Modifier = Modifier,
    onResetAllSettings: () -> Unit = {},
    valueFormated: String = "",
    controlOptionModel: ControlOptionModel?,

    controlIdSelected: String = "",
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
            .fillMaxWidth()
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
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
                                    tint = MaterialTheme.colorScheme.primary,
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
                                    tint = MaterialTheme.colorScheme.primary,
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
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
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
                    item {
                        MenuItem(
                            icon = painterResource(control.icon),
                            label = control.text,
                            onClick = { onControlIdSelected(control.id) },
                            selected = controlIdSelected == control.id,
                        )
                    }
                }
            }
        }
    }
}