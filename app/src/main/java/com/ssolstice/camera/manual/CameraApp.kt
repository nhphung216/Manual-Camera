package com.ssolstice.camera.manual

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.ssolstice.camera.manual.utils.LocaleHelper
import com.ssolstice.camera.manual.utils.Logger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CameraApp : MultiDexApplication() {

    companion object {
        const val TAG: String = "OpenCameraApplication"
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(base))
    }

    override fun onCreate() {
        Logger.d(TAG, "onCreate")
        super.onCreate()
        checkAppReplacingState()
    }

    private fun checkAppReplacingState() {
        Logger.d(TAG, "checkAppReplacingState")
        if (resources == null) {
            Log.e(TAG, "app is replacing, kill")
            Process.killProcess(Process.myPid())
        }
    }
}