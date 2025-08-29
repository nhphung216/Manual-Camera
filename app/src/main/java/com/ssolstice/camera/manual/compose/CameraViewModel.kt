package com.ssolstice.camera.manual.compose

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.hardware.camera2.CameraExtensionCharacteristics
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
import com.ssolstice.camera.manual.MyApplicationInterface.VideoMode
import com.ssolstice.camera.manual.PreferenceKeys
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.models.CameraControlModel
import com.ssolstice.camera.manual.models.ControlOptionModel
import com.ssolstice.camera.manual.models.PhotoModeUiModel
import com.ssolstice.camera.manual.models.SettingItemModel
import com.ssolstice.camera.manual.models.VideoModeUiModel
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
        private const val TAG = "CameraViewModel"
    }

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

    val controlOptionModel = MutableLiveData<ControlOptionModel?>()

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

    fun setResolutionOfVideoSelected(
        activity: MainActivity,
        applicationInterface: MyApplicationInterface,
        preview: Preview,
        item: SettingItemModel
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
                    preview.getCameraId(), applicationInterface.cameraIdSPhysicalPref
                ), item.id
            )
        }
        viewModelScope.launch {
            delay(500)
            activity.updateForSettings(true, "", true, false)
        }
    }

    fun setFlashSelected(
        preview: Preview, item: SettingItemModel
    ) {
        Log.e(TAG, "setFlashSelected: $item")
        _flashSelected.value = item
        preview.updateFlash(item.id)
    }

    fun setRawSelected(
        activity: MainActivity, preview: Preview, item: SettingItemModel
    ) {
        Log.e(TAG, "setRawSelected: $item")
        _rawSelected.value = item

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(PreferenceKeys.RawPreferenceKey, item.id)
        }

        activity.mainUI?.updateCycleRawIcon()
        activity.applicationInterface?.drawPreview?.updateSettings()
        //preview.reopenCamera() // needed for RAW options to take effect
    }

    fun setRepeatSelected(
        activity: MainActivity, item: SettingItemModel
    ) {
        Log.e(TAG, "setRepeatSelected: $item")
        _repeatSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(PreferenceKeys.RepeatModePreferenceKey, item.id)
        }
    }

    fun setTimerSelected(
        activity: MainActivity, item: SettingItemModel
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
        preview: Preview,
        item: SettingItemModel
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

    fun applySpeedSelectedPreview(
        activity: MainActivity,
        applicationInterface: MyApplicationInterface,
        preview: Preview,
        rate: Float
    ) {
        Log.e(TAG, "setSpeedSelected: $rate")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putFloat(
                PreferenceKeys.getVideoCaptureRatePreferenceKey(
                    preview.getCameraId(),
                    applicationInterface.cameraIdSPhysicalPref
                ), rate
            )
        }
        viewModelScope.launch {
            delay(500)
            activity.updateForSettings(true, "", true, false)
        }
    }

    fun setupCameraData(
        activity: MainActivity, applicationInterface: MyApplicationInterface, preview: Preview
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
                    activity, applicationInterface, preview, model
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
                    activity, model
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
                        activity, model
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
                        activity, applicationInterface, preview, model
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
                        preview.getCameraId(), applicationInterface.cameraIdSPhysicalPref
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
                            id = "$value", text = text, selected = value == captureRateValue
                        )
                        if (model.selected) setSpeedSelected(
                            activity, applicationInterface, preview, model
                        )
                        model
                    }.toMutableList()

                if (speeds.isNotEmpty()) {
                    setSpeeds(speeds)
                }
            }
        }

        // flash
        val supportedFlashValues = preview.supportedFlashValues.orEmpty()
        if (supportedFlashValues.isNotEmpty()) {
            val flashList = supportedFlashValues.map { flashValue ->
                val model = SettingItemModel(
                    id = flashValue,
                    selected = flashValue == preview.currentFlashValue,
                    icon = getFlashIcon(flashValue)
                )
                if (model.selected) {
                    setFlashSelected(preview, model)
                }
                model
            }.toMutableList()

            if (flashList.isNotEmpty()) {
                setFlashList(flashList)
            }
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

        val rawList: MutableList<SettingItemModel> = rawValues.mapIndexed { index, flashValue ->
            val model = SettingItemModel(
                id = flashValue,
                text = rawEntries[index],
                selected = index == rawModeIndex,
                icon = rawIcons[index]
            )
            if (model.selected) setRawSelected(
                activity, preview, model
            )
            model
        }.toMutableList()

        if (rawList.isNotEmpty()) setRawList(rawList)
    }

    private fun getFlashIcon(flashValue: String): Int = when (flashValue) {
        "flash_off" -> R.drawable.flash_off
        "flash_auto", "flash_frontscreen_auto" -> R.drawable.flash_auto
        "flash_on", "flash_frontscreen_on" -> R.drawable.flash_on
        "flash_torch", "flash_frontscreen_torch" -> R.drawable.baseline_highlight_white_48
        "flash_red_eye" -> R.drawable.baseline_remove_red_eye_white_48
        else -> R.drawable.flash_off
    }

    private val _controlsMapData = MutableLiveData<HashMap<String, CameraControlModel>>()
    val controlsMapData: LiveData<HashMap<String, CameraControlModel>> = _controlsMapData

    fun generateExposureLabels(
        min: Int,       // ví dụ -24
        max: Int,       // ví dụ +24
        stepsPerEv: Int // ví dụ 6 (tức 6 steps = 1 EV)
    ): ArrayList<String> {
        val minEv = min / stepsPerEv
        val maxEv = max / stepsPerEv

        val list = ArrayList<String>()
        for (ev in minEv..maxEv) {
            list.add(
                if (ev > 0) "+$ev" else ev.toString()
            )
        }
        return list
    }

    fun generateIsoLabels(
        minIso: Int, maxIso: Int
    ): ArrayList<String> {
        val labels = arrayListOf<String>()

        // Chọn ISO chuẩn gần min và max
        var iso = 50
        if (minIso > iso) iso = minIso

        while (iso <= maxIso) {
            labels.add(iso.toString())
            iso *= 2 // tăng theo bội số 2
        }

        // đảm bảo có giá trị min và max
        if (!labels.contains(minIso.toString())) labels.add(0, minIso.toString())
        if (!labels.contains(maxIso.toString())) labels.add(maxIso.toString())

        return labels
    }

    fun generateShutterSpeedLabels(
        minSpeed: Double, // giây, ví dụ 1/8000s = 0.000125
        maxSpeed: Double  // giây, ví dụ 30s
    ): ArrayList<String> {
        val labels = ArrayList<String>()
        var speed = minSpeed

        while (speed <= maxSpeed) {
            labels.add(formatShutter(speed))
            speed *= 2 // mỗi bước nhân đôi thời gian
        }
        return labels
    }

    private fun formatShutter(seconds: Double): String {
        return if (seconds >= 1) {
            "${seconds.toInt()}s"
        } else {
            "1/${(1 / seconds).toInt()}"
        }
    }

    fun setupCameraControlsData(
        activity: MainActivity, applicationInterface: MyApplicationInterface, preview: Preview
    ) {
        Log.e(TAG, "setupCameraData")
        val photoMode = applicationInterface.photoMode
        val controlsMap = hashMapOf<String, CameraControlModel>()

        // white balance
        val supportedWhiteBalances = preview.getSupportedWhiteBalances()
        if (supportedWhiteBalances != null) {
            val options: ArrayList<ControlOptionModel> = arrayListOf()
            for (value in supportedWhiteBalances) {
                val model = if (value == "manual") {
                    val temperature = preview.cameraController.getWhiteBalanceTemperature()
                    val minWhiteBalance = preview.getMinimumWhiteBalanceTemperature().toFloat()
                    val maxWhiteBalance = preview.getMaximumWhiteBalanceTemperature().toFloat()
                    ControlOptionModel(
                        id = value,
                        text = activity.mainUI!!.getEntryForWhiteBalance(value),
                        icon = activity.mainUI!!.getIconForWhiteBalance(value),
                        valueRange = minWhiteBalance..maxWhiteBalance,
                        currentValue = temperature.toFloat(),
                        labels = generateExposureLabels(
                            minWhiteBalance.toInt(), maxWhiteBalance.toInt(), 6
                        ),
                    )
                } else {
                    ControlOptionModel(
                        id = value,
                        text = activity.mainUI!!.getEntryForWhiteBalance(value),
                        icon = activity.mainUI!!.getIconForWhiteBalance(value)
                    )
                }
                options.add(model)
            }
            controlsMap["white_balance"] = CameraControlModel(
                id = "white_balance",
                text = activity.getString(R.string.white_balance),
                icon = R.drawable.ic_white_balance,
                options = options,
            )
        }

        // exposure
        if (preview.supportsExposures()) {
            val minExposure = preview.minimumExposure.toFloat()
            val maxExposure = preview.maximumExposure.toFloat()
            val currentExposure = preview.currentExposure.toFloat()
            controlsMap["exposure"] = CameraControlModel(
                id = "exposure",
                text = activity.getString(R.string.exposure),
                icon = R.drawable.ic_exposure_24,
                valueRange = minExposure..maxExposure,
                currentValue = currentExposure,
                labels = generateExposureLabels(minExposure.toInt(), maxExposure.toInt(), 6),
                steps = 30
            )
        }

        // iso
        if (preview.supportsISORange()) {
            val minISO = preview.minimumISO.toFloat()
            val maxISO = preview.maximumISO.toFloat()
            controlsMap["iso"] = CameraControlModel(
                id = "iso",
                text = activity.getString(R.string.iso),
                icon = R.drawable.iso_icon,
                valueRange = minISO..maxISO,
                labels = generateIsoLabels(minISO.toInt(), maxISO.toInt()),
                steps = 30
            )
        }

        // shutter
        if (preview.supportsExposureTime()) {
            val minExposure = preview.minimumExposureTime.toFloat()
            val maxExposure = preview.maximumExposureTime.toFloat()
            Log.e(TAG, "minExposure: $minExposure, maxExposure: $maxExposure")
            controlsMap["shutter"] = CameraControlModel(
                id = "shutter",
                text = activity.getString(R.string.shutter),
                icon = R.drawable.ic_shutter_speed_24,
                valueRange = minExposure..maxExposure,
                labels = generateShutterSpeedLabels(minExposure.toDouble(), maxExposure.toDouble()),
                steps = 30
            )
        }

        // focus
        var supportedFocusValues = preview.supportedFocusValues
        if (!preview.isVideo && photoMode == PhotoMode.FocusBracketing) {
            // don't show focus modes in focus bracketing mode (as we'll always run in manual focus mode)
            supportedFocusValues = null
        }
        if (supportedFocusValues != null) {
            val focusValues = ArrayList(supportedFocusValues)
            // only show appropriate continuous focus mode
            if (preview.isVideo) {
                focusValues.remove("focus_mode_continuous_picture")
            } else {
                focusValues.remove("focus_mode_continuous_video")
            }
            if (!focusValues.isEmpty()) {
                val currentFocus = preview.currentFocusValue
                val options: ArrayList<ControlOptionModel> = arrayListOf()
                for (value in focusValues) {
                    options.add(
                        ControlOptionModel(
                            id = value,
                            text = activity.mainUI!!.getEntryForFocus(value),
                            icon = activity.mainUI!!.getIconForFocus(value),
                            selected = value == currentFocus
                        )
                    )
                }
                controlsMap["focus"] = CameraControlModel(
                    id = "focus",
                    text = activity.getString(R.string.focus),
                    icon = R.drawable.ic_center_focus_24,
                    options = options,
                )
            }
        }

        // scene mode
        val supportedSceneModes = preview.supportedSceneModes
        if (supportedSceneModes != null) {
            val options: ArrayList<ControlOptionModel> = arrayListOf()
            for (value in supportedSceneModes) {
                options.add(
                    ControlOptionModel(
                        id = value,
                        text = activity.mainUI!!.getEntryForSceneMode(value),
                        icon = activity.mainUI!!.getIconForSceneMode(value)
                    )
                )
            }
            controlsMap["scene_mode"] = CameraControlModel(
                id = "scene_mode",
                text = activity.getString(R.string.scene_mode),
                icon = R.drawable.scene_mode_fireworks,
                options = options,
            )
        }

        // color effect
        val supportedColorEffects = preview.supportedColorEffects
        if (supportedColorEffects != null) {
            val options: ArrayList<ControlOptionModel> = arrayListOf()
            for (value in supportedColorEffects) {
                options.add(
                    ControlOptionModel(
                        id = value,
                        text = activity.mainUI!!.getEntryForColorEffect(value),
                        icon = activity.mainUI!!.getIconForColorEffect(value)
                    )
                )
            }
            controlsMap["color_effect"] = CameraControlModel(
                id = "color_effect",
                text = activity.getString(R.string.color_effect),
                icon = R.drawable.color_effect_negative,
                options = options,
            )
        }
        _controlsMapData.value = controlsMap
    }

    fun setControlOptionModel(item: ControlOptionModel?) {
        controlOptionModel.value = item
    }

    private val _photoModes = MutableStateFlow<List<PhotoModeUiModel>>(emptyList())
    val photoModes: StateFlow<List<PhotoModeUiModel>> = _photoModes

    var currentPhotoMode = MutableStateFlow(PhotoMode.Standard)

    fun setCurrentPhotoMode(item: PhotoMode) {
        currentPhotoMode.value = item
    }

    fun loadPhotoModeViews(mainActivity: MainActivity) {
        val photoModes = ArrayList<PhotoModeUiModel>()

        if (mainActivity.supportsPanorama()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.Panorama,
                    mainActivity.getString(R.string.photo_mode_panorama_full),
                )
            )
        }

        if (mainActivity.supportsFocusBracketing()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.FocusBracketing,
                    mainActivity.getString(R.string.photo_mode_focus_bracketing_full),
                )
            )
        }

        if (mainActivity.supportsHDR()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.HDR,
                    mainActivity.getString(R.string.photo_mode_hdr),
                )
            )
        }

        // Standard luôn có
        photoModes.add(
            PhotoModeUiModel(
                PhotoMode.Standard,
                mainActivity.getString(R.string.photo_mode_standard_full),
            )
        )

        if (mainActivity.supportsDRO()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.DRO,
                    mainActivity.getString(R.string.photo_mode_dro),
                )
            )
        }

        if (mainActivity.supportsNoiseReduction()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.NoiseReduction,
                    mainActivity.getString(R.string.photo_mode_noise_reduction_full),
                )
            )
        }

        if (mainActivity.supportsCameraExtension(
                CameraExtensionCharacteristics.EXTENSION_NIGHT
            )
        ) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Night,
                    mainActivity.getString(R.string.Night),
                )
            )
        }

        if (mainActivity.supportsFastBurst()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.FastBurst,
                    mainActivity.getString(R.string.photo_mode_fast_burst_full),
                )
            )
        }

        if (mainActivity.supportsExpoBracketing()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.ExpoBracketing,
                    mainActivity.getString(R.string.photo_mode_expo_bracketing_full),
                )
            )
        }

        if (mainActivity.supportsCameraExtension(
                CameraExtensionCharacteristics.EXTENSION_AUTOMATIC
            )
        ) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Auto,
                    mainActivity.getString(R.string.photo_mode_x_auto),
                )
            )
        }

        if (mainActivity.supportsCameraExtension(
                CameraExtensionCharacteristics.EXTENSION_HDR
            )
        ) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_HDR,
                    mainActivity.getString(R.string.photo_mode_x_hdr),
                )
            )
        }

        if (mainActivity.supportsCameraExtension(
                CameraExtensionCharacteristics.EXTENSION_BOKEH
            )
        ) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Bokeh,
                    mainActivity.getString(R.string.photo_mode_x_bokeh),
                )
            )
        }

        if (mainActivity.supportsCameraExtension(
                CameraExtensionCharacteristics.EXTENSION_BEAUTY
            )
        ) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Beauty,
                    mainActivity.getString(R.string.photo_mode_x_beauty_full),
                )
            )
        }

        // Mark selected
        _photoModes.value = photoModes.map {
            it.copy(selected = it.mode == currentPhotoMode.value)
        }
    }

    fun changePhotoMode(mode: PhotoModeUiModel) {
        currentPhotoMode.value = mode.mode
        _photoModes.value = _photoModes.value.map {
            it.copy(selected = it.mode == mode.mode)
        }
    }

    private val _speed = MutableStateFlow(mutableListOf<SettingItemModel>())
    val speeds: StateFlow<MutableList<SettingItemModel>> = _speed
    fun setSpeeds(speed: MutableList<SettingItemModel>) {
        viewModelScope.launch {
            _speed.value = speed
        }
    }

    private val _speedSelected = MutableLiveData<SettingItemModel>()
    val speedSelected: LiveData<SettingItemModel> = _speedSelected

    private val _videoModes = MutableStateFlow<List<VideoModeUiModel>>(emptyList())
    val videoModes: StateFlow<List<VideoModeUiModel>> = _videoModes

    private val _currentVideoMode = MutableStateFlow(VideoModeUiModel())
    val currentVideoMode: StateFlow<VideoModeUiModel> = _currentVideoMode

    fun setVideoModeSelected(newMode: VideoModeUiModel) {
        _currentVideoMode.value = newMode
        _videoModes.value = _videoModes.value.map { it.copy(selected = it.mode == newMode.mode) }
    }

    private val _captureRate = MutableStateFlow(1f)
    val captureRate: StateFlow<Float> = _captureRate

    fun setCaptureRate(rate: Float) {
        _captureRate.value = rate
    }

    fun loadVideoModes(
        activity: MainActivity, applicationInterface: MyApplicationInterface, preview: Preview
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val captureRateValues = applicationInterface.getSupportedVideoCaptureRates()
        Log.e(TAG, "captureRateValues: $captureRateValues")

        val captureRateValue = sharedPreferences.getFloat(
            PreferenceKeys.getVideoCaptureRatePreferenceKey(
                preview.getCameraId(), applicationInterface.cameraIdSPhysicalPref
            ), 1.0f
        )
        Log.e(TAG, "captureRateValue: $captureRateValue")

        val videoModes = mutableListOf<VideoModeUiModel>()
        val slowMotion =
            VideoModeUiModel(VideoMode.Slow_Motion, activity.getString(R.string.slow_motion))
        val normalMode = VideoModeUiModel(VideoMode.Video, activity.getString(R.string.video))
        val timeLapse =
            VideoModeUiModel(VideoMode.Time_Lapse, activity.getString(R.string.time_lapse))

        captureRateValues.forEach {
            if (it < 1f) {
                slowMotion.captureRates.add(it)
            } else if (it > 1f) {
                timeLapse.captureRates.add(it)
            }
        }

        if (slowMotion.captureRates.isNotEmpty()) videoModes.add(slowMotion)
        videoModes.add(normalMode)
        if (timeLapse.captureRates.isNotEmpty()) videoModes.add(timeLapse)

        if (captureRateValue < 1f && _currentVideoMode.value.mode != VideoMode.Slow_Motion
            || captureRateValue > 1f && _currentVideoMode.value.mode != VideoMode.Video) {
            setCaptureRate(1f)
            applySpeedSelectedPreview(activity, applicationInterface, preview, _captureRate.value)
            viewModelScope.launch {
                delay(1000)
                _currentVideoMode.value = normalMode
            }
        }

        _videoModes.value = videoModes.map { model ->
            model.copy(selected = model.mode == _currentVideoMode.value.mode)
        }
    }

    fun checkSwitchCamera(
        activity: MainActivity,
        applicationInterface: MyApplicationInterface,
        preview: Preview
    ) {
        if (!isPhotoMode.value) {
            viewModelScope.launch {
                delay(1000)
                loadVideoModes(activity, applicationInterface, preview)
            }
        }
    }
}