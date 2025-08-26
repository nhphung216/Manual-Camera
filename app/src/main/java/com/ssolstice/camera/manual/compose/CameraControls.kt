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

    onControlChanged: (CameraControlModel, Float) -> Unit,
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
                    if (controlModel.options.isNotEmpty()) {
                        LazyRow(
                            modifier = modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(controlModel.options) { optionModel ->
                                MenuItem(
                                    icon = painterResource(optionModel.icon),
                                    label = optionModel.text,
                                    onClick = {
                                        itemSelected = optionModel.id
                                    },
                                    isChanged = false,
                                    selected = itemSelected == optionModel.id,
                                )
                            }
                        }
                    } else {
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
        }
    }
}

@Composable
fun CameraControlItem(
    control: CameraControlModel,
    onControlChanged: (CameraControlModel, Float) -> Unit
) {

}