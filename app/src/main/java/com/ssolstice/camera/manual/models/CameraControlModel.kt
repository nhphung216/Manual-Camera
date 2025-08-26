package com.ssolstice.camera.manual.models

data class CameraControlModel(
    val id: String,
    val text: String = "",
    val icon: Int = 0,
    val selected: Boolean = false,
    val valueRange: ClosedFloatingPointRange<Float> = -1f..1f,
    val labels: ArrayList<String> = arrayListOf(),
    val steps: Int = 30,
    val options: ArrayList<ControlOptionModel> = arrayListOf(),
)