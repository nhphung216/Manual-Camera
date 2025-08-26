package com.ssolstice.camera.manual.models

data class ControlOptionModel(
    val id: String,
    val text: String = "",
    val icon: Int = 0,
    val selected: Boolean = false,
)