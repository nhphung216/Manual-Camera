package com.ssolstice.camera.manual.utils

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsLogger @Inject constructor(
    @ApplicationContext context: Context
) {
    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    fun logFeatureUsed(featureName: String) {
        val bundle = Bundle().apply {
            putString("feature_name", featureName)
        }
        firebaseAnalytics.logEvent("feature_used", bundle)
    }

    fun logViewClicked(viewName: String) {
        val bundle = Bundle().apply {
            putString("view_name", viewName)
        }
        firebaseAnalytics.logEvent("view_clicked", bundle)
    }

    fun logCustom(eventName: String, params: Map<String, String>) {
        val bundle = Bundle()
        params.forEach { (k, v) -> bundle.putString(k, v) }
        firebaseAnalytics.logEvent(eventName, bundle)
    }
}