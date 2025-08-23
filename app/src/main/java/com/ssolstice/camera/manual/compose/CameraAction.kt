package com.ssolstice.camera.manual.compose

sealed class CameraAction {
    object TakePhoto : CameraAction()
    object SwitchCamera : CameraAction()
    object ToggleFlash : CameraAction()
}