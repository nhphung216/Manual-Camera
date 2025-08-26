package com.ssolstice.camera.manual.models

data class CameraControlModel(
    val id: String,
    val text: String = "",
    val icon: Int = 0,
    val selected: Boolean = false,
    val valueRange: ClosedFloatingPointRange<Float> = 0f..0f,
    val labels: ArrayList<String> = arrayListOf(),
    val currentValue: Float = 0f,
    val steps: Int = 0,
    val options: ArrayList<ControlOptionModel> = arrayListOf(),
)