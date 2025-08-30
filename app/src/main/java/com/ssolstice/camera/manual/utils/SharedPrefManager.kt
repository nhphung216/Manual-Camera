package com.ssolstice.camera.manual.utils

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ssolstice.camera.manual.models.PhotoModeUiModel
import com.ssolstice.camera.manual.models.VideoModeUiModel
import androidx.core.content.edit

class SharedPrefManager(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson
) {

    companion object {
        private const val PREF_PHOTO_MODES = "PREF_PHOTO_MODES"
        private const val PREF_VIDEO_MODES = "PREF_VIDEO_MODES"
    }

    fun <T> saveList(key: String, list: ArrayList<T>) {
        val json = gson.toJson(list)
        sharedPreferences.edit { putString(key, json) }
    }

    fun <T> getList(key: String, typeToken: TypeToken<ArrayList<T>>): ArrayList<T> {
        val json = sharedPreferences.getString(key, null) ?: return arrayListOf()
        return gson.fromJson(json, typeToken.type)
    }

    fun savePhotoModes(modes: ArrayList<PhotoModeUiModel>) {
        saveList(PREF_PHOTO_MODES, modes)
    }

    fun getPhotoModes(): ArrayList<PhotoModeUiModel> {
        return getList(PREF_PHOTO_MODES, object : TypeToken<ArrayList<PhotoModeUiModel>>() {})
    }

    fun saveVideoModes(modes: ArrayList<VideoModeUiModel>) {
        saveList(PREF_VIDEO_MODES, modes)
    }

    fun getVideoModes(): ArrayList<VideoModeUiModel> {
        return getList(PREF_VIDEO_MODES, object : TypeToken<ArrayList<VideoModeUiModel>>() {})
    }
}

