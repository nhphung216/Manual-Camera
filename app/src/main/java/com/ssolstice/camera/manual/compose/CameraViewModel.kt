package com.ssolstice.camera.manual.compose

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssolstice.camera.manual.MainActivity
import com.ssolstice.camera.manual.MyApplicationInterface
import com.ssolstice.camera.manual.MyApplicationInterface.PhotoMode
import com.ssolstice.camera.manual.PreferenceKeys
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.models.SettingItemModel
import com.ssolstice.camera.manual.preview.Preview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val TAG = "PopupView"
    }

    // flash
    private val _flashList = MutableStateFlow(mutableListOf<SettingItemModel>())
    val flashList: StateFlow<MutableList<SettingItemModel>> = _flashList
    fun setFlashList(resolution: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _flashList.value = resolution
        }
    }

    private val _flashSelected = MutableLiveData<SettingItemModel>()
    val flashSelected: LiveData<SettingItemModel> = _flashSelected

    // raw
    private val _rawList = MutableStateFlow(mutableListOf<SettingItemModel>())
    val rawList: StateFlow<MutableList<SettingItemModel>> = _rawList
    fun setRawList(resolution: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _rawList.value = resolution
        }
    }

    private val _rawSelected = MutableLiveData<SettingItemModel>()
    val rawSelected: LiveData<SettingItemModel> = _rawSelected

    // _resolutions
    private val _resolutionsOfPhoto = MutableStateFlow(mutableListOf<SettingItemModel>())
    val resolutionsOfPhoto: StateFlow<MutableList<SettingItemModel>> = _resolutionsOfPhoto
    fun setResolutionOfPhoto(resolution: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _resolutionsOfPhoto.value = resolution
        }
    }

    private val _resolutionSelected = MutableLiveData<SettingItemModel>()
    val resolutionSelected: LiveData<SettingItemModel> = _resolutionSelected

    // _repeats
    private val _repeats = MutableStateFlow(mutableListOf<SettingItemModel>())
    val repeats: StateFlow<MutableList<SettingItemModel>> = _repeats
    fun setRepeat(repeat: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _repeats.value = repeat
        }
    }

    private val _repeatSelected = MutableLiveData<SettingItemModel>()
    val repeatSelected: LiveData<SettingItemModel> = _repeatSelected

    // _timers
    private val _timers = MutableStateFlow(mutableListOf<SettingItemModel>())
    val timers: StateFlow<MutableList<SettingItemModel>> = _timers
    fun setTimer(timer: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _timers.value = timer
        }
    }

    private val _timerSelected = MutableLiveData<SettingItemModel>()
    val timerSelected: LiveData<SettingItemModel> = _timerSelected

    // _speed
    private val _speed = MutableStateFlow(mutableListOf<SettingItemModel>())
    val speeds: StateFlow<MutableList<SettingItemModel>> = _speed
    fun setSpeeds(speed: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _speed.value = speed
        }
    }

    private val _speedSelected = MutableLiveData<SettingItemModel>()
    val speedSelected: LiveData<SettingItemModel> = _speedSelected

    // _resolutionsOfVideo
    private val _resolutionsOfVideo = MutableStateFlow(mutableListOf<SettingItemModel>())
    val resolutionsOfVideo: StateFlow<MutableList<SettingItemModel>> = _resolutionsOfVideo
    fun setResolutionOfVideo(resolutionsVideo: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _resolutionsOfVideo.value = resolutionsVideo
        }
    }

    private val _resolutionsOfVideoSelected = MutableLiveData<SettingItemModel>()
    val resolutionsOfVideoSelected: LiveData<SettingItemModel> = _resolutionsOfVideoSelected

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPhotoMode = MutableStateFlow(false)
    val isPhotoMode: StateFlow<Boolean> = _isPhotoMode

    private val _isVideoRecordingPaused = MutableStateFlow(false)
    val isVideoRecordingPaused: StateFlow<Boolean> = _isVideoRecordingPaused

    fun setVideoRecordingPaused(isPaused: Boolean) {
        viewModelScope.launch {
            _isVideoRecordingPaused.value = isPaused
        }
    }

    fun setPhotoMode(isPhotoMode: Boolean) {
        viewModelScope.launch {
            _isPhotoMode.value = isPhotoMode
        }
    }

    fun setVideoRecording(isRecording: Boolean) {
        viewModelScope.launch {
            _isRecording.value = isRecording
        }
    }

    private val _galleryBitmap = MutableStateFlow<Bitmap?>(null)
    val galleryBitmap: StateFlow<Bitmap?> = _galleryBitmap

    fun setGalleryBitmap(galleryBitmap: Bitmap?) {
        viewModelScope.launch {
            _galleryBitmap.value = galleryBitmap
        }
    }


    @SuppressLint("DefaultLocale")
    private fun getAspectRatio(width: Int, height: Int): String {
        val ratio = width.toDouble() / height
        if (abs(ratio - 16.0 / 9) < 0.01) return "16:9"
        if (abs(ratio - 4.0 / 3) < 0.01) return "4:3"
        if (abs(ratio - 3.0 / 2) < 0.01) return "3:2"
        if (abs(ratio - 1.0) < 0.01) return "1:1"
        if (abs(ratio - 5.0 / 4) < 0.01) return "5:4"
        if (abs(ratio - 21.0 / 9) < 0.01) return "21:9"
        if (abs(ratio - 2) < 0.01) return "2:1"
        return String.format("%.2f:1", ratio) // fallback dạng 1.78:1
    }

    fun setResolutionOfVideoSelected(
        activity: MainActivity,
        applicationInterface: MyApplicationInterface,
        preview: Preview, item: SettingItemModel
    ) {
        _resolutionsOfVideoSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(
                PreferenceKeys.getVideoQualityPreferenceKey(
                    preview.getCameraId(),
                    applicationInterface.cameraIdSPhysicalPref,
                    applicationInterface.fpsIsHighSpeed()
                ), item.id
            )
        }
        viewModelScope.launch {
            delay(500)
            activity.updateForSettings(true, "", true, false)
        }
    }

    fun setResolutionSelected(
        activity: MainActivity,
        applicationInterface: MyApplicationInterface,
        preview: Preview,
        item: SettingItemModel
    ) {
        _resolutionSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(
                PreferenceKeys.getResolutionPreferenceKey(
                    preview.getCameraId(),
                    applicationInterface.cameraIdSPhysicalPref
                ), item.id
            )
        }
        viewModelScope.launch {
            delay(500)
            activity.updateForSettings(true, "", true, false)
        }
    }

    fun setFlashSelected(
        activity: MainActivity,
        preview: Preview, item: SettingItemModel
    ) {
        Log.e(TAG, "setFlashSelected: $item")
        _flashSelected.value = item
        preview.updateFlash(item.id)
    }

    fun setRawSelected(
        activity: MainActivity,
        preview: Preview,
        item: SettingItemModel
    ) {
        Log.e(TAG, "setRawSelected: $item")
        _rawSelected.value = item

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(PreferenceKeys.RawPreferenceKey, item.id)
        }

        activity.mainUI?.updateCycleRawIcon()
        activity.applicationInterface?.drawPreview?.updateSettings()
        preview.reopenCamera() // needed for RAW options to take effect
    }

    fun setRepeatSelected(
        activity: MainActivity,
        item: SettingItemModel
    ) {
        Log.e(TAG, "setRepeatSelected: $item")
        _repeatSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(PreferenceKeys.RepeatModePreferenceKey, item.id)
        }
    }

    fun setTimerSelected(
        activity: MainActivity,
        item: SettingItemModel
    ) {
        Log.e(TAG, "setTimerSelected: $item")
        _timerSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(PreferenceKeys.TimerPreferenceKey, item.id)
        }
    }

    fun setSpeedSelected(
        activity: MainActivity,
        applicationInterface: MyApplicationInterface,
        preview: Preview, item: SettingItemModel
    ) {
        Log.e(TAG, "setSpeedSelected: $item")
        _speedSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putFloat(
                PreferenceKeys.getVideoCaptureRatePreferenceKey(
                    preview.getCameraId(),
                    applicationInterface.cameraIdSPhysicalPref
                ), item.id.toFloat()
            )
        }
        viewModelScope.launch {
            delay(500)
            activity.updateForSettings(true, "", true, false)
        }
    }

    fun setupCameraData(
        activity: MainActivity,
        applicationInterface: MyApplicationInterface,
        preview: Preview
    ) {
        Log.e(TAG, "setupCameraData")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val photoMode = applicationInterface.photoMode
        Log.e(TAG, "setupCameraData")
        Log.e(TAG, "photoMode: $photoMode")

        // collect photo resolutions
        if (!preview.isVideo && photoMode != PhotoMode.Panorama) {
            val pictureSizes = preview.getSupportedPictureSizes(true).asReversed()
            Log.e(TAG, "pictureSizes: $pictureSizes")

            val currentPictureSize = preview.getCurrentPictureSize()

            val photoResolutions: MutableList<SettingItemModel> = pictureSizes.map { size ->
                val fileSizeView = "(${Preview.getMPString(size.width, size.height)})"
                val aspectView = getAspectRatio(size.width, size.height)

                val model = SettingItemModel(
                    id = "${size.width} ${size.height}",
                    text = "${size.width}x${size.height}",
                    sub = "$aspectView $fileSizeView",
                    selected = size == currentPictureSize
                )
                if (model.selected) setResolutionSelected(
                    activity,
                    applicationInterface,
                    preview,
                    model
                )
                model
            }.toMutableList()

            if (photoResolutions.isNotEmpty()) setResolutionOfPhoto(photoResolutions)
        }


        // timers
        if (photoMode != PhotoMode.Panorama) {
            val timerValues = activity.resources.getStringArray(R.array.preference_timer_values)
            val timerEntries = activity.resources.getStringArray(R.array.preference_timer_entries)
            val timerValue =
                sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0") ?: "0"

            val timerIndex = timerValues.indexOf(timerValue).takeIf { it >= 0 } ?: 0

            val timers: MutableList<SettingItemModel> = timerValues.mapIndexed { index, value ->
                val model = SettingItemModel(
                    id = value,
                    text = timerEntries.getOrNull(index).orEmpty(),
                    selected = index == timerIndex
                )
                if (model.selected) setTimerSelected(
                    activity,
                    model
                )
                model
            }.toMutableList()

            if (timers.isNotEmpty()) setTimer(timers)
        }

        // repeats
        if (photoMode != PhotoMode.Panorama) {
            val repeatModeValues =
                activity.resources.getStringArray(R.array.preference_burst_mode_values)
            val repeatModeEntries =
                activity.resources.getStringArray(R.array.preference_burst_mode_entries)
            val repeatModeValue: String =
                sharedPreferences.getString(PreferenceKeys.RepeatModePreferenceKey, "1")!!
            val repeats: MutableList<SettingItemModel> =
                repeatModeValues.mapIndexed { index, value ->
                    val model = SettingItemModel(
                        id = value,
                        text = repeatModeEntries.getOrNull(index).orEmpty(),
                        selected = value == repeatModeValue
                    )
                    if (model.selected) setRepeatSelected(
                        activity,
                        model
                    )
                    model
                }.toMutableList()

            if (repeats.isNotEmpty()) setRepeat(repeats)
        }

        // resolutions of video
        if (preview.isVideo) {
            var videoSizes =
                preview.getSupportedVideoQuality(applicationInterface.getVideoFPSPref())
            if (videoSizes.isEmpty()) {
                Log.e(TAG, "can't find any supported video sizes for current fps!")
                videoSizes = preview.videoQualityHander.getSupportedVideoQuality()
            }
            videoSizes = ArrayList<String>(videoSizes)
            videoSizes.reverse()

            val resolutionOfVideo: MutableList<SettingItemModel> =
                videoSizes.mapIndexed { index, value ->
                    val model = SettingItemModel(
                        id = value,
                        text = preview.getCamcorderProfileDescriptionShort(value),
                        selected = value == preview.videoQualityHander.getCurrentVideoQuality()
                    )
                    if (model.selected) setResolutionOfVideoSelected(
                        activity,
                        applicationInterface,
                        preview,
                        model
                    )
                    model
                }.toMutableList()

            if (resolutionOfVideo.isNotEmpty()) setResolutionOfVideo(resolutionOfVideo)
        }

        // speed of video
        if (preview.isVideo) {
            val captureRateValues = applicationInterface.getSupportedVideoCaptureRates()
            if (captureRateValues.size > 1) {
                val captureRateValue = sharedPreferences.getFloat(
                    PreferenceKeys.getVideoCaptureRatePreferenceKey(
                        preview.getCameraId(),
                        applicationInterface.cameraIdSPhysicalPref
                    ), 1.0f
                )
                val speeds: MutableList<SettingItemModel> =
                    captureRateValues.mapIndexed { index, value ->
                        val text = if (abs(1.0f - value) < 1.0e-5) {
                            activity.getString(R.string.preference_video_capture_rate_normal)
                        } else {
                            value.toString() + "x"
                        }
                        val model = SettingItemModel(
                            id = "$value",
                            text = text,
                            selected = value == captureRateValue
                        )
                        if (model.selected) setSpeedSelected(
                            activity,
                            applicationInterface,
                            preview,
                            model
                        )
                        model
                    }.toMutableList()

                if (speeds.isNotEmpty()) {
                    setSpeeds(speeds)
                }
            }
        }

        // flash
        if (preview.supportsFlash()) {
            val flashList: MutableList<SettingItemModel> = mutableListOf()
            val supportedFlashValues = preview.supportedFlashValues
            if (supportedFlashValues != null && supportedFlashValues.size > 1) {
                for (index in supportedFlashValues.indices) {
                    val flashValue = supportedFlashValues[index]
                    val icon = when (flashValue) {
                        "flash_off" -> R.drawable.flash_off
                        "flash_auto", "flash_frontscreen_auto" -> R.drawable.flash_auto
                        "flash_on", "flash_frontscreen_on" -> R.drawable.flash_on
                        "flash_torch", "flash_frontscreen_torch" -> R.drawable.baseline_highlight_white_48
                        "flash_red_eye" -> R.drawable.baseline_remove_red_eye_white_48
                        else -> R.drawable.flash_off
                    }
                    val model = SettingItemModel(
                        id = flashValue,
                        selected = flashValue == preview.currentFlashValue,
                        icon = icon
                    )
                    flashList.add(model)
                    if (model.selected) setRepeatSelected(activity, model)
                }
            }

            if (flashList.isNotEmpty()) setFlashList(flashList)
        }

        // raw
        val rawValues = activity.resources.getStringArray(R.array.raw_values)
        val rawEntries = activity.resources.getStringArray(R.array.raw_entries)
        val typedArray = activity.resources.obtainTypedArray(R.array.raw_icons)
        val rawIcons = IntArray(typedArray.length()) { i ->
            typedArray.getResourceId(i, 0)
        }
        typedArray.recycle()
        val rawModeValue: String =
            sharedPreferences.getString(PreferenceKeys.RawPreferenceKey, "preference_raw_no")
                ?: "preference_raw_no"
        val rawModeIndex = rawValues.indexOf(rawModeValue).takeIf { it >= 0 } ?: 0

        val rawList: MutableList<SettingItemModel> =
            rawValues.mapIndexed { index, flashValue ->
                val model = SettingItemModel(
                    id = flashValue,
                    text = rawEntries[index],
                    selected = index == rawModeIndex,
                    icon = rawIcons[index]
                )
                if (model.selected) setRawSelected(
                    activity,
                    preview,
                    model
                )
                model
            }.toMutableList()

        if (rawList.isNotEmpty()) setRawList(rawList)
    }
}