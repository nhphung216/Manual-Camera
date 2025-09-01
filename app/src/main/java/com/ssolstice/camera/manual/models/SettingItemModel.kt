package com.ssolstice.camera.manual.models

data class SettingItemModel(
    val id: String,
    val text: String = "",
    val sub: String = "",
    val icon: Int = 0,
    val selected: Boolean = false,
    val isPremium: Boolean = false,
)
