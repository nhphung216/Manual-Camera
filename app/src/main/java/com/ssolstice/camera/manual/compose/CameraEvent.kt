package com.ssolstice.camera.manual.compose

sealed class CameraEvent {
    object PhotoTaken : CameraEvent()
    data class ZoomChanged(val value: Float) : CameraEvent()
}