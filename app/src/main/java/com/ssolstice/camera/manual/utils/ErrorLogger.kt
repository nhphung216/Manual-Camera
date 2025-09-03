package com.ssolstice.camera.manual.utils

import com.google.firebase.crashlytics.FirebaseCrashlytics

object ErrorLogger {

    fun logWarning(tag: String, message: String) {
        FirebaseCrashlytics.getInstance().log("$tag: $message")
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        FirebaseCrashlytics.getInstance().log("$tag: $message")
        throwable?.let { FirebaseCrashlytics.getInstance().recordException(it) }
    }
}
