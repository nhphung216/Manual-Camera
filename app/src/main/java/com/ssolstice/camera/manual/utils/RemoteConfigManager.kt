package com.ssolstice.camera.manual.utils

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class RemoteConfigManager @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) {

    suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            false
        }
    }

    fun showAd(): Boolean {
        return remoteConfig.getBoolean("show_ad")
    }

    fun checkUpdateState(currentVersionCode: Int): UpdateState {
        val required = remoteConfig.getBoolean("update_required")
        val recommended = remoteConfig.getBoolean("update_recommended")
        val minVersion = remoteConfig.getLong("update_version").toInt()
        val updateUrl = remoteConfig.getString("update_url")

        return when {
            required && currentVersionCode < minVersion -> UpdateState.Force(updateUrl)
            recommended && currentVersionCode < minVersion -> UpdateState.Recommended(updateUrl)
            currentVersionCode < minVersion -> UpdateState.Optional(updateUrl)
            else -> UpdateState.None
        }
    }
}

sealed class UpdateState {
    object None : UpdateState()
    data class Optional(val url: String) : UpdateState()
    data class Recommended(val url: String) : UpdateState()
    data class Force(val url: String) : UpdateState()
}
