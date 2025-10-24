package com.ssolstice.camera.manual.models

import com.ssolstice.camera.manual.MyApplicationInterface

data class PhotoModeUiModel(
    val mode: MyApplicationInterface.PhotoMode,
    val text: String = "",
    val selected: Boolean = false,
    val options: List<OptionRes> = emptyList()
)
