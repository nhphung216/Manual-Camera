package com.ssolstice.camera.manual.compose

import kotlinx.coroutines.flow.MutableSharedFlow

class CameraBridge {
    val actions = MutableSharedFlow<CameraAction>()
    val events = MutableSharedFlow<CameraEvent>()

    suspend fun sendAction(action: CameraAction) = actions.emit(action)
    suspend fun sendEvent(event: CameraEvent) = events.emit(event)
}