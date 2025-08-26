package com.ssolstice.camera.manual.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ssolstice.camera.manual.R
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

    onControlChanged: (CameraControlModel, Float) -> Unit,
    onExposureChanged: (Float) -> Unit = {},
    exposureValue: Float = 0f,
    exposureValueRange: ClosedFloatingPointRange<Float> = -2f..2f,
    exposureLabels: List<String> = listOf("-2", "-1", "0", "1", "2"),
    exposureSteps: Int = 30,

    onIsoChanged: (Float) -> Unit = {},
    isoValue: Float = EXPOSURE_DEFAULT,
    isoValueRange: ClosedFloatingPointRange<Float> = -2f..2f,
    isoLabels: List<String> = listOf("-2", "-1", "0", "1", "2"),
    isoSteps: Int = 30,

    onShutterChanged: (Float) -> Unit = {},
    shutterValue: Float = EXPOSURE_DEFAULT,
    shutterValueRange: ClosedFloatingPointRange<Float> = -2f..2f,
    shutterLabels: List<String> = listOf("-2", "-1", "0", "1", "2"),
    shutterSteps: Int = 30,

    onWhiteBalanceChanged: (Float) -> Unit = {},
    whiteBalanceValue: Float = EXPOSURE_DEFAULT,

    onFocusChanged: (Float) -> Unit = {},
    focusValue: Float = EXPOSURE_DEFAULT,

    onSceneModeChanged: (Float) -> Unit = {},
    sceneModeValue: Float = EXPOSURE_DEFAULT,

    onColorEffectChanged: (Float) -> Unit = {},
    colorEffectValue: Float = EXPOSURE_DEFAULT,

    cameraControls: MutableList<CameraControlModel>,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        var itemSelected by remember { mutableStateOf("") }

        var showResetParams by remember { mutableStateOf(false) }

        Column(
            modifier = modifier
                .fillMaxWidth(),
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

            cameraControls.forEach { controlModel ->
                if (controlModel.id == itemSelected) {
                    CustomValueSlider(
                        modifier = Modifier,
                        name = itemSelected,
                        value = shutterValue,
                        onValueChange = { value, formatDisplay ->
                            onControlChanged(controlModel, value)
                        },
                        valueRange = controlModel.valueRange,
                        labels = controlModel.labels,
                        steps = controlModel.steps
                    )
                }
            }

            LazyRow(
                modifier = modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cameraControls) { cameraControl ->
                    MenuItem(
                        icon = painterResource(cameraControl.icon),
                        label = cameraControl.text,
                        onClick = {
                            itemSelected = cameraControl.id
                        },
                        isChanged = false,
                        selected = itemSelected == cameraControl.id,
                    )
                }
            }

//            // shutter
//            if (itemSelected == "shutter") {
//                CustomValueSlider(
//                    modifier = Modifier,
//                    name = itemSelected,
//                    value = shutterValue,
//                    onValueChange = { v, formatDisplay ->
//                        onShutterChanged(v)
//                    },
//                    valueRange = shutterValueRange,
//                    labels = shutterLabels,
//                    steps = shutterSteps
//                )
//            }
//
//            // iso
//            if (itemSelected == "iso") {
//                CustomValueSlider(
//                    modifier = Modifier,
//                    name = itemSelected,
//                    value = isoValue,
//                    onValueChange = { v, formatDisplay ->
//                        onIsoChanged(v)
//                    },
//                    valueRange = isoValueRange,
//                    labels = isoLabels,
//                    steps = isoSteps
//                )
//            }
//
//            // exposure
//            if (itemSelected == "exposure") {
//                CustomValueSlider(
//                    modifier = Modifier,
//                    name = itemSelected,
//                    value = exposureValue,
//                    onValueChange = { v, formatDisplay ->
//                        onExposureChanged(v)
//                    },
//                    valueRange = exposureValueRange,
//                    labels = exposureLabels,
//                    steps = exposureSteps
//                )
//            }

//            LazyRow(
//                modifier = modifier
//                    .fillMaxWidth(),
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.spacedBy(8.dp),
//            ) {
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.baseline_close_white_48),
//                        label = stringResource(R.string.close),
//                        onClick = { onClose() },
//                    )
//                }
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.outline_reset_settings),
//                        label = stringResource(R.string.reset_all),
//                        onClick = {
//                            showResetParams = !showResetParams
//                            if (showResetParams) {
//                                itemSelected = ""
//                            }
//                        },
//                        isChanged = false,
//                        selected = false,
//                    )
//                }
//
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.ic_wb_auto),
//                        label = stringResource(R.string.white_balance),
//                        onClick = {
//                            itemSelected =
//                                if (itemSelected == "white_balance") "" else "white_balance"
//                        },
//                        isChanged = whiteBalanceValue != STRENGTH_DEFAULT,
//                        selected = itemSelected == "white_balance",
//                    )
//                }
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.ic_exposure_24),
//                        label = stringResource(R.string.exposure),
//                        onClick = {
//                            showResetParams = false
//                            itemSelected = if (itemSelected == "exposure") "" else "exposure"
//                        },
//                        isChanged = exposureValue != BRIGHTNESS_DEFAULT,
//                        selected = itemSelected == "exposure",
//                    )
//                }
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.iso_icon),
//                        label = stringResource(R.string.iso),
//                        onClick = {
//                            showResetParams = false
//                            itemSelected = if (itemSelected == "iso") "" else "iso"
//                        },
//                        isChanged = isoValue != BRIGHTNESS_DEFAULT,
//                        selected = itemSelected == "iso",
//                    )
//                }
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.ic_shutter_speed_24),
//                        label = stringResource(R.string.shutter),
//                        onClick = {
//                            showResetParams = false
//                            itemSelected = if (itemSelected == "shutter") "" else "shutter"
//                        },
//                        isChanged = shutterValue != CONTRAST_DEFAULT,
//                        selected = itemSelected == "shutter",
//                    )
//                }
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.ic_center_focus_24),
//                        label = stringResource(R.string.focus),
//                        onClick = {
//                            showResetParams = false
//                            itemSelected = if (itemSelected == "focus") "" else "focus"
//                        },
//                        isChanged = focusValue != EXPOSURE_DEFAULT,
//                        selected = itemSelected == "focus",
//                    )
//                }
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.ic_auto),
//                        label = stringResource(R.string.scene_mode),
//                        onClick = {
//                            showResetParams = false
//                            itemSelected = if (itemSelected == "scene_mode") "" else "scene_mode"
//                        },
//                        isChanged = sceneModeValue != EXPOSURE_DEFAULT,
//                        selected = itemSelected == "scene_mode",
//                    )
//                }
//                item {
//                    MenuItem(
//                        icon = painterResource(R.drawable.color_effect_negative),
//                        label = stringResource(R.string.color_effect),
//                        onClick = {
//                            showResetParams = false
//                            itemSelected =
//                                if (itemSelected == "color_effect") "" else "color_effect"
//                        },
//                        isChanged = colorEffectValue != EXPOSURE_DEFAULT,
//                        selected = itemSelected == "color_effect",
//                    )
//                }
            //    }
        }
    }
}