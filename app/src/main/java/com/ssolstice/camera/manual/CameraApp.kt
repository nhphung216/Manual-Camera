package com.ssolstice.camera.manual

import android.app.Application
import android.os.Process
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CameraApp: Application() {

    companion object{
        const val TAG: String = "OpenCameraApplication"
    }

    override fun onCreate() {
        if (MyDebug.LOG) Log.d(TAG, "onCreate")
        super.onCreate()
        checkAppReplacingState()
    }

    private fun checkAppReplacingState() {
        if (MyDebug.LOG) Log.d(TAG, "checkAppReplacingState")
        if (resources == null) {
            Log.e(TAG, "app is replacing, kill")
            Process.killProcess(Process.myPid())
        }
    }
}