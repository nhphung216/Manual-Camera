package com.ssolstice.camera.manual.models

import com.ssolstice.camera.manual.MyApplicationInterface

data class VideoModeUiModel(
    val mode: MyApplicationInterface.VideoMode = MyApplicationInterface.VideoMode.Video,
    val text: String = "",
    val selected: Boolean = false,
    val captureRates: ArrayList<Float> = arrayListOf()
)
