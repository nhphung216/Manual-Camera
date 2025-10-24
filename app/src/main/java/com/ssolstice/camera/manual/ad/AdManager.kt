package com.ssolstice.camera.manual.ad

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds

object AdManager {
    fun initialize(application: Application) {
        MobileAds.initialize(application) {
            Log.d("AdMob", "SDK initialized")
        }
    }
}
