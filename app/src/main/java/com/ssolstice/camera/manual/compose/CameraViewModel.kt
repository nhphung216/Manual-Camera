package com.ssolstice.camera.manual.compose

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.hardware.camera2.CameraExtensionCharacteristics
import android.preference.PreferenceManager
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ssolstice.camera.manual.MainActivity
import com.ssolstice.camera.manual.MyApplicationInterface
import com.ssolstice.camera.manual.MyApplicationInterface.PhotoMode
import com.ssolstice.camera.manual.MyApplicationInterface.VideoMode
import com.ssolstice.camera.manual.PreferenceKeys
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.billing.BillingManager
import com.ssolstice.camera.manual.models.CameraControlModel
import com.ssolstice.camera.manual.models.ControlOptionModel
import com.ssolstice.camera.manual.models.OptionRes
import com.ssolstice.camera.manual.models.PhotoModeUiModel
import com.ssolstice.camera.manual.models.SettingItemModel
import com.ssolstice.camera.manual.models.VideoModeUiModel
import com.ssolstice.camera.manual.preview.Preview
import com.ssolstice.camera.manual.utils.AnalyticsLogger
import com.ssolstice.camera.manual.utils.Logger
import com.ssolstice.camera.manual.utils.RemoteConfigManager
import com.ssolstice.camera.manual.utils.SharedPrefManager
import com.ssolstice.camera.manual.utils.UpdatePromptManager
import com.ssolstice.camera.manual.utils.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val sharedPrefManager: SharedPrefManager,
    private val analyticsLogger: AnalyticsLogger,
    private val remoteConfigManager: RemoteConfigManager,
    private val updatePromptManager: UpdatePromptManager,
    private val application: Application
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CameraViewModel"
    }

    private val _updateState = MutableLiveData<UpdateState>()
    val updateState: LiveData<UpdateState> = _updateState

    fun checkUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            updatePromptManager.increaseOpenCount()

            remoteConfigManager.fetchAndActivate()

            val currentVersionCode = getApplication<Application>().packageManager.getPackageInfo(
                    getApplication<Application>().packageName,
                    0
                ).versionCode

            val state = remoteConfigManager.checkUpdateState(currentVersionCode)

            when (state) {
                is UpdateState.Force -> _updateState.postValue(state)
                is UpdateState.Recommended, is UpdateState.Optional -> {
                    val shouldShow = updatePromptManager.shouldShowPrompt(minOpensPerDay = 3)
                    if (shouldShow) _updateState.postValue(state)
                }

                UpdateState.None -> Unit
            }
        }
    }

    fun logFeatureUsed(featureName: String?) {
        featureName?.let { analyticsLogger.logFeatureUsed(featureName) }
    }

    fun logViewClicked(viewName: String) {
        analyticsLogger.logViewClicked(viewName)
    }

    fun logCustom(eventName: String, params: Map<String, String>) {
        analyticsLogger.logCustom(eventName, params)
    }

    fun savePhotoModesToDb(modes: ArrayList<PhotoModeUiModel>) {
        sharedPrefManager.savePhotoModes(modes)
    }

    fun getPhotoModesDb(): ArrayList<PhotoModeUiModel> {
        return sharedPrefManager.getPhotoModes()
    }

    fun saveVideoModesDb(modes: ArrayList<VideoModeUiModel>) {
        sharedPrefManager.saveVideoModes(modes)
    }

    fun getVideoModes(): ArrayList<VideoModeUiModel> {
        return sharedPrefManager.getVideoModes()
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

    // flash
    private val _flashList = MutableStateFlow(mutableListOf<SettingItemModel>())
    val flashList: StateFlow<MutableList<SettingItemModel>> = _flashList
    fun setFlashList(list: MutableList<SettingItemModel>) {
        _flashList.value = list
    }

    private val _flashSelected = MutableLiveData<SettingItemModel>()
    val flashSelected: LiveData<SettingItemModel> = _flashSelected

    // raw
    private val _rawList = MutableStateFlow(mutableListOf<SettingItemModel>())
    val rawList: StateFlow<MutableList<SettingItemModel>> = _rawList
    fun setRawList(resolution: MutableList<SettingItemModel>) {
        _rawList.value = resolution
    }

    private val _rawSelected = MutableLiveData<SettingItemModel>()
    val rawSelected: LiveData<SettingItemModel> = _rawSelected

    // _resolutions
    private val _resolutionsOfPhoto = MutableStateFlow(mutableListOf<SettingItemModel>())
    val resolutionsOfPhoto: StateFlow<MutableList<SettingItemModel>> = _resolutionsOfPhoto
    fun setResolutionOfPhoto(resolution: MutableList<SettingItemModel>) {
        _resolutionsOfPhoto.value = resolution
    }

    private val _resolutionSelected = MutableLiveData<SettingItemModel>()
    val resolutionSelected: LiveData<SettingItemModel> = _resolutionSelected

    // _repeats
    private val _repeats = MutableStateFlow(mutableListOf<SettingItemModel>())
    val repeats: StateFlow<MutableList<SettingItemModel>> = _repeats
    fun setRepeat(repeat: MutableList<SettingItemModel>) {
        _repeats.value = repeat
    }

    private val _repeatSelected = MutableLiveData<SettingItemModel>()
    val repeatSelected: LiveData<SettingItemModel> = _repeatSelected

    // _timers
    private val _timers = MutableStateFlow(mutableListOf<SettingItemModel>())
    val timers: StateFlow<MutableList<SettingItemModel>> = _timers
    fun setTimer(timer: MutableList<SettingItemModel>) {
        _timers.value = timer
    }

    private val _timerSelected = MutableLiveData<SettingItemModel>()
    val timerSelected: LiveData<SettingItemModel> = _timerSelected

    val controlOptionModel = MutableLiveData<ControlOptionModel?>()

    // _resolutionsOfVideo
    private val _resolutionsOfVideo = MutableStateFlow(mutableListOf<SettingItemModel>())
    val resolutionsOfVideo: StateFlow<MutableList<SettingItemModel>> = _resolutionsOfVideo
    fun setResolutionOfVideo(resolutionsVideo: MutableList<SettingItemModel>) {
        _resolutionsOfVideo.value = resolutionsVideo
    }

    private val _resolutionsOfVideoSelected = MutableLiveData<SettingItemModel>()
    val resolutionsOfVideoSelected: LiveData<SettingItemModel> = _resolutionsOfVideoSelected

    fun setResolutionOfVideoSelected(
        activity: MainActivity,
        appInterface: MyApplicationInterface,
        preview: Preview,
        item: SettingItemModel
    ) {
        _resolutionsOfVideoSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(
                PreferenceKeys.getVideoQualityPreferenceKey(
                    preview.getCameraId(),
                    appInterface.cameraIdSPhysicalPref,
                    appInterface.fpsIsHighSpeed()
                ), item.id
            )
        }
        viewModelScope.launch {
            delay(500)
            viewModelScope.launch {
                delay(100)
                updateForSettings(activity)
            }
        }
    }

    fun setResolutionSelected(
        activity: MainActivity,
        appInterface: MyApplicationInterface,
        preview: Preview,
        item: SettingItemModel
    ) {
        logFeatureUsed("setResolutionSelected ${item.id}")

        _resolutionSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(
                PreferenceKeys.getResolutionPreferenceKey(
                    preview.getCameraId(), appInterface.cameraIdSPhysicalPref
                ), item.id
            )
        }
        viewModelScope.launch {
            delay(500)
            updateForSettings(activity)
        }
    }

    fun setFlashSelected(
        preview: Preview, item: SettingItemModel
    ) {
        Logger.d(TAG, "setFlashSelected: $item")
        logFeatureUsed("setFlashSelected ${item.id}")
        _flashSelected.value = item
        preview.updateFlash(item.id)
    }

    fun setRawSelected(
        activity: MainActivity, item: SettingItemModel
    ) {
        Logger.d(TAG, "setRawSelected: $item")
        logFeatureUsed("setRawSelected ${item.id}")
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
        Logger.d(TAG, "setRepeatSelected: $item")
        logFeatureUsed("setRepeatSelected ${item.id}")
        _repeatSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(PreferenceKeys.RepeatModePreferenceKey, item.id)
        }
    }

    fun setTimerSelected(
        activity: MainActivity, item: SettingItemModel
    ) {
        Logger.d(TAG, "setTimerSelected: $item")
        logFeatureUsed("setTimerSelected ${item.id}")
        _timerSelected.value = item
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putString(PreferenceKeys.TimerPreferenceKey, item.id)
        }
    }

    fun setSpeedSelected(
        activity: MainActivity,
        appInterface: MyApplicationInterface,
        preview: Preview,
        item: SettingItemModel
    ) {
        logFeatureUsed("setSpeedSelected ${item.id}")
        _speedSelected.value = item
        applySpeedSelectedPreview(activity, appInterface, preview, item.id.toFloat())
    }

    fun applySpeedSelectedPreview(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview, rate: Float
    ) {
        Logger.d(TAG, "setSpeedSelected: $rate")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        sharedPreferences.edit {
            putFloat(
                PreferenceKeys.getVideoCaptureRatePreferenceKey(
                    preview.getCameraId(), appInterface.cameraIdSPhysicalPref
                ), rate
            )
        }
        viewModelScope.launch {
            delay(500)
            updateForSettings(activity)
        }
    }

    fun isPremiumUser(activity: MainActivity): Boolean {
        return activity.getSharedPreferences(BillingManager.PREF_BILLING_NAME, MODE_PRIVATE)
            .getBoolean(
                BillingManager.PREF_PREMIUM_KEY, false
            )
    }

    fun setupCameraData(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ) {
        Logger.d(TAG, "setupCameraData()")
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val photoResolutions =
                    async { setupPhotoResolutions(appInterface, preview) }.await()
                val timers = async { setupTimers(activity, appInterface) }.await()
                val repeats = async { setupRepeats(activity, appInterface) }.await()
                val videoOptions = async { setupVideo(activity, appInterface, preview) }.await()
                val speedVideo = async { setupSpeedVideo(activity, appInterface, preview) }.await()
                val flashModes = async { setupFlash(activity, preview) }.await()
                val rawModes = async { setupRaw(activity) }.await()

                // chuyển sang Main thread update UI
                withContext(Dispatchers.Main) {
                    Logger.d(TAG, "setupCameraData() withContext(Dispatchers.Main)")

                    Logger.d(TAG, "photoResolutions: ${photoResolutions.size}")
                    if (photoResolutions.isNotEmpty()) {
                        setResolutionOfPhoto(photoResolutions)
                        photoResolutions.find { it.selected }?.let { selectedModel ->
                            setResolutionSelected(
                                activity, appInterface, preview, selectedModel
                            )
                        }
                    }

                    Logger.d(TAG, "timers: ${timers.size}")
                    if (timers.isNotEmpty()) {
                        setTimer(timers)
                        timers.find { it.selected }?.let { selectedModel ->
                            setTimerSelected(activity, selectedModel)
                        }
                    }

                    Logger.d(TAG, "repeats: ${repeats.size}")
                    if (repeats.isNotEmpty()) {
                        setRepeat(repeats)
                        repeats.find { it.selected }?.let { selectedModel ->
                            setRepeatSelected(activity, selectedModel)
                        }
                    }

                    Logger.d(TAG, "videoOptions: ${videoOptions.size}")
                    if (videoOptions.isNotEmpty()) {
                        setResolutionOfVideo(videoOptions)
                        videoOptions.find { it.selected }?.let { selectedModel ->
                            setResolutionOfVideoSelected(
                                activity, appInterface, preview, selectedModel
                            )
                        }
                    }

                    Logger.d(TAG, "speedVideo: ${speedVideo.size}")
                    if (speedVideo.isNotEmpty()) {
                        setSpeeds(speedVideo)
                    }

                    Logger.d(TAG, "flashModes: ${flashModes.size}")
                    if (flashModes.isNotEmpty()) {
                        setFlashList(flashModes)
                        flashModes.find { it.selected }?.let { selectedModel ->
                            setFlashSelected(preview, selectedModel)
                        }
                    }

                    Logger.d(TAG, "rawModes: ${rawModes.size}")
                    if (rawModes.isNotEmpty()) {
                        setRawList(rawModes)
                        rawModes.find { it.selected }?.let { selectedModel ->
                            setRawSelected(activity, selectedModel)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.d(TAG, "setupCameraData() failed: ${e.message}" + e)
            }
        }
    }

    private val _controlsMapData = MutableLiveData<HashMap<String, CameraControlModel>>()
    val controlsMapData: LiveData<HashMap<String, CameraControlModel>> = _controlsMapData


    fun buildWhiteBalanceControls(
        activity: MainActivity, preview: Preview
    ): CameraControlModel? {
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
                        text = activity.mainUI?.getEntryForWhiteBalance(value) ?: "",
                        icon = activity.mainUI?.getIconForWhiteBalance(value) ?: R.drawable.ic_auto,
                        valueRange = minWhiteBalance..maxWhiteBalance,
                        currentValue = temperature.toFloat(),
                        labels = generateExposureLabels(
                            minWhiteBalance.toInt(), maxWhiteBalance.toInt(), 6
                        ),
                    )
                } else {
                    ControlOptionModel(
                        id = value,
                        text = activity.mainUI?.getEntryForWhiteBalance(value) ?: "",
                        icon = activity.mainUI?.getIconForWhiteBalance(value) ?: R.drawable.ic_auto
                    )
                }
                options.add(model)
            }
            return CameraControlModel(
                id = "white_balance",
                text = activity.getString(R.string.white_balance),
                icon = R.drawable.ic_white_balance,
                options = options,
            )
        }
        return null
    }

    fun buildExposureControls(
        activity: MainActivity, preview: Preview
    ): CameraControlModel? {
        if (preview.supportsExposures()) {
            val minExposure = preview.minimumExposure.toFloat()
            val maxExposure = preview.maximumExposure.toFloat()
            val currentExposure = preview.currentExposure.toFloat()
            return CameraControlModel(
                id = "exposure",
                text = activity.getString(R.string.exposure),
                icon = R.drawable.ic_exposure_24,
                valueRange = minExposure..maxExposure,
                currentValue = currentExposure,
                labels = generateExposureLabels(minExposure.toInt(), maxExposure.toInt(), 6),
                steps = 30
            )
        }
        return null
    }

    fun buildIsoControls(
        activity: MainActivity, preview: Preview
    ): CameraControlModel? {
        if (preview.supportsISORange()) {
            val minISO = preview.minimumISO.toFloat()
            val maxISO = preview.maximumISO.toFloat()
            return CameraControlModel(
                id = "iso",
                text = activity.getString(R.string.iso),
                icon = R.drawable.iso_icon,
                valueRange = minISO..maxISO,
                labels = generateIsoLabels(minISO.toInt(), maxISO.toInt()),
                steps = 30
            )
        }
        return null
    }

    fun buildShutterControls(
        activity: MainActivity, preview: Preview
    ): CameraControlModel? {
        if (preview.supportsExposureTime()) {
            val minExposure = preview.minimumExposureTime.toFloat()
            val maxExposure = preview.maximumExposureTime.toFloat()
            Logger.d(TAG, "minExposure: $minExposure, maxExposure: $maxExposure")
            return CameraControlModel(
                id = "shutter",
                text = activity.getString(R.string.shutter),
                icon = R.drawable.ic_shutter_speed_24,
                valueRange = minExposure..maxExposure,
                labels = generateShutterSpeedLabels(
                    minExposure.toDouble(), maxExposure.toDouble()
                ),
                steps = 30
            )
        }
        return null
    }

    fun buildFocusControls(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ): CameraControlModel? {
        var supportedFocusValues = preview.supportedFocusValues
        if (!preview.isVideo && appInterface.photoMode == PhotoMode.FocusBracketing) {
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
                            text = activity.mainUI?.getEntryForFocus(value) ?: "",
                            icon = activity.mainUI?.getIconForFocus(value) ?: R.drawable.ic_auto,
                            selected = value == currentFocus
                        )
                    )
                }
                return CameraControlModel(
                    id = "focus",
                    text = activity.getString(R.string.focus),
                    icon = R.drawable.ic_center_focus_24,
                    options = options,
                )
            }
        }
        return null
    }

    fun buildSceneControls(
        activity: MainActivity, preview: Preview
    ): CameraControlModel? {
        val supportedSceneModes = preview.supportedSceneModes
        if (supportedSceneModes != null) {
            val options: ArrayList<ControlOptionModel> = arrayListOf()
            for (value in supportedSceneModes) {
                options.add(
                    ControlOptionModel(
                        id = value,
                        text = activity.mainUI?.getEntryForSceneMode(value) ?: "",
                        icon = activity.mainUI?.getIconForSceneMode(value) ?: R.drawable.ic_auto
                    )
                )
            }
            return CameraControlModel(
                id = "scene_mode",
                text = activity.getString(R.string.scene_mode),
                icon = R.drawable.scene_mode_fireworks,
                options = options,
            )
        }
        return null
    }

    fun buildColorEffectControls(
        activity: MainActivity, preview: Preview
    ): CameraControlModel? {
        val supportedColorEffects = preview.supportedColorEffects
        if (supportedColorEffects != null) {
            val options: ArrayList<ControlOptionModel> = arrayListOf()
            for (value in supportedColorEffects) {
                options.add(
                    ControlOptionModel(
                        id = value,
                        text = activity.mainUI?.getEntryForColorEffect(value) ?: "",
                        icon = activity.mainUI?.getIconForColorEffect(value) ?: R.drawable.ic_auto
                    )
                )
            }
            return CameraControlModel(
                id = "color_effect",
                text = activity.getString(R.string.color_effect),
                icon = R.drawable.color_effect_negative,
                options = options,
            )
        }
        return null
    }

    fun setupCameraControlsData(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ) {
        Logger.d(TAG, "setupCameraData")
        viewModelScope.launch(Dispatchers.Default) {
            val wb = async { buildWhiteBalanceControls(activity, preview) }
            val exp = async { buildExposureControls(activity, preview) }
            val iso = async { buildIsoControls(activity, preview) }
            val shutter = async { buildShutterControls(activity, preview) }
            val focus = async { buildFocusControls(activity, appInterface, preview) }
            val scene = async { buildSceneControls(activity, preview) }
            val color = async { buildColorEffectControls(activity, preview) }

            val controlsMap = hashMapOf<String, CameraControlModel>()

            listOf(wb, exp, iso, shutter, focus, scene, color).mapNotNull { it.await() }
                .forEach { controlsMap[it.id] = it }

            withContext(Dispatchers.Main) {
                _controlsMapData.value = controlsMap
            }
        }
    }

    fun setControlOptionModel(item: ControlOptionModel?) {
        controlOptionModel.value = item
    }

    private val _photoModes = MutableStateFlow<List<PhotoModeUiModel>>(emptyList())
    val photoModes: StateFlow<List<PhotoModeUiModel>> = _photoModes

    var currentPhotoModeUiModel = MutableStateFlow(PhotoModeUiModel(PhotoMode.Standard))

    fun setCurrentPhotoMode(item: PhotoModeUiModel) {
        currentPhotoModeUiModel.value = item
    }

    private fun getFastBurstConfig(activity: MainActivity): MutableList<OptionRes> {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(activity)
        val entries =
            activity.getResources().getStringArray(R.array.preference_fast_burst_n_images_entries)
        val values =
            activity.getResources().getStringArray(R.array.preference_fast_burst_n_images_values)

        var maxBurstImages = (activity.applicationInterface?.imageSaver?.queueSize ?: 0) + 1
        maxBurstImages = max(2, maxBurstImages)

        // filter number of burst images - don't allow more than max_burst_images
        val burstModeValuesL: MutableList<String> = arrayListOf()
        for (i in values.indices) {
            val nImages: Int
            try {
                nImages = values[i].toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                continue
            }
            if (nImages > maxBurstImages) {
                continue
            }
            burstModeValuesL.add(values[i])
        }
        val burstModeValue: String =
            sharedPref.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5") ?: "5"
        var selectedIndex = burstModeValuesL.indexOf(burstModeValue)
        if (selectedIndex == -1) selectedIndex = 0

        return burstModeValuesL.mapIndexed { index, value ->
            OptionRes(
                text = entries[index], value = value, selected = index == selectedIndex
            )
        }.toMutableList()
    }

    fun loadPhotoModeViews(
        act: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ) {
        val photoModes = ArrayList<PhotoModeUiModel>()
        if (act.supportsPanorama()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.Panorama,
                    act.getString(R.string.photo_mode_panorama_full),
                )
            )
        }
        if (act.supportsFocusBracketing()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.FocusBracketing,
                    act.getString(R.string.photo_mode_focus_bracketing_full),
                )
            )
        }
        photoModes.add(
            PhotoModeUiModel(
                PhotoMode.Standard,
                act.getString(R.string.photo_mode_standard_full),
            )
        )
        if (act.supportsHDR()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.HDR,
                    act.getString(R.string.photo_mode_hdr),
                )
            )
        }
        if (act.supportsDRO()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.DRO,
                    act.getString(R.string.photo_mode_dro),
                )
            )
        }
        if (act.supportsNoiseReduction()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.NoiseReduction,
                    act.getString(R.string.photo_mode_noise_reduction_full),
                )
            )
        }
        if (act.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT)) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Night,
                    act.getString(R.string.Night),
                )
            )
        }
        if (act.supportsFastBurst()) {
            photoModes.add(
                PhotoModeUiModel(
                    mode = PhotoMode.FastBurst,
                    text = act.getString(R.string.photo_mode_fast_burst_full),
                    options = getFastBurstConfig(act)
                )
            )
        }
        if (act.supportsExpoBracketing()) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.ExpoBracketing,
                    act.getString(R.string.photo_mode_expo_bracketing_full),
                )
            )
        }
        if (act.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_AUTOMATIC)) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Auto,
                    act.getString(R.string.photo_mode_x_auto),
                )
            )
        }
        if (act.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_HDR)) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_HDR,
                    act.getString(R.string.photo_mode_x_hdr),
                )
            )
        }
        if (act.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BOKEH)) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Bokeh,
                    act.getString(R.string.photo_mode_x_bokeh),
                )
            )
        }
        if (act.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BEAUTY)) {
            photoModes.add(
                PhotoModeUiModel(
                    PhotoMode.X_Beauty,
                    act.getString(R.string.photo_mode_x_beauty_full),
                )
            )
        }

        savePhotoModesToDb(photoModes)
        handlePhotoModes(photoModes, act, appInterface, preview)
    }

    fun loadPhotoModes(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ) {
        val list = getPhotoModesDb()
        if (list.isNotEmpty()) {
            handlePhotoModes(list, activity, appInterface, preview)
        }
        loadPhotoModeViews(activity, appInterface, preview)
    }

    fun handlePhotoModes(
        photoModes: ArrayList<PhotoModeUiModel>,
        act: MainActivity,
        appInterface: MyApplicationInterface,
        preview: Preview,
    ) {
        var currentPhotoMode: PhotoModeUiModel? = null
        for (mode in photoModes) {
            if (mode.mode == appInterface.photoMode) {
                currentPhotoMode = mode
                break
            }
        }
        if (currentPhotoMode != null) {
            setCurrentPhotoMode(currentPhotoMode)
            val sharedPref = PreferenceManager.getDefaultSharedPreferences(act)
            if (currentPhotoMode.mode == PhotoMode.FastBurst) {
                val burstModeValue =
                    sharedPref.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5") ?: "5"
                currentPhotoMode.options.forEachIndexed { index, option ->
                    if (option.value == burstModeValue) {
                        currentPhotoMode.options[index] = option.copy(selected = true)
                        setSelectedPhotoOption(act, preview, option)
                    }
                }
            }
            _photoModes.value = photoModes.map {
                it.copy(selected = it == currentPhotoMode)
            }
        }
    }

    fun changePhotoModeUiModel(mode: PhotoModeUiModel) {
        currentPhotoModeUiModel.value = mode
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

    private val _captureRate = MutableStateFlow(1f)
    val captureRate: StateFlow<Float> = _captureRate

    fun setCaptureRate(rate: Float) {
        logFeatureUsed("setCaptureRate $rate")
        _captureRate.value = rate
    }

    private val _videoModes = MutableStateFlow<List<VideoModeUiModel>>(emptyList())
    val videoModes: StateFlow<List<VideoModeUiModel>> = _videoModes

    private val _currentVideoMode = MutableStateFlow(VideoModeUiModel())
    val currentVideoMode: StateFlow<VideoModeUiModel> = _currentVideoMode

    fun setVideoModeSelected(newMode: VideoModeUiModel) {
        logFeatureUsed("setVideoModeSelected ${newMode.mode.name}")
        _currentVideoMode.value = newMode
        _videoModes.value = _videoModes.value.map { it.copy(selected = it.mode == newMode.mode) }
    }

    private val _selectedPhotoOption = MutableStateFlow(OptionRes())
    val selectedPhotoOption: StateFlow<OptionRes> = _selectedPhotoOption

    fun setSelectedPhotoOption(act: MainActivity, preview: Preview, option: OptionRes) {
        logFeatureUsed("setSelectedPhotoOption ${option.text}")
        _selectedPhotoOption.value = option

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(act)
        if (currentPhotoModeUiModel.value.mode == PhotoMode.FastBurst) {
            sharedPreferences.edit {
                putString(PreferenceKeys.FastBurstNImagesPreferenceKey, option.value)
            }
            preview.cameraController?.setBurstNImages(
                act.applicationInterface?.getBurstNImages() ?: 2
            )
        }
    }

    fun loadVideoModes(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        val captureRateValues = appInterface.supportedVideoCaptureRates
        Logger.d(TAG, "captureRateValues: $captureRateValues")

        val captureRate = sharedPreferences.getFloat(
            PreferenceKeys.getVideoCaptureRatePreferenceKey(
                preview.getCameraId(), appInterface.cameraIdSPhysicalPref
            ), 1.0f
        )
        Logger.d(TAG, "captureRateValue: $captureRate")

        val videoModes = arrayListOf<VideoModeUiModel>()
        val slowMotion =
            VideoModeUiModel(VideoMode.Slow_Motion, activity.getString(R.string.slow_motion))
        val normalMode = VideoModeUiModel(VideoMode.Video, activity.getString(R.string.video))
        val timeLapse =
            VideoModeUiModel(VideoMode.Time_Lapse, activity.getString(R.string.time_lapse))

        captureRateValues.forEach {
            if (it != null) {
                if (it < 1f) {
                    slowMotion.captureRates.add(it)
                } else if (it > 1f) {
                    timeLapse.captureRates.add(it)
                }
            }
        }

        if (slowMotion.captureRates.isNotEmpty()) videoModes.add(slowMotion)
        videoModes.add(normalMode)
        if (timeLapse.captureRates.isNotEmpty()) videoModes.add(timeLapse)

        if (captureRate < 1f) {
            _currentVideoMode.value = slowMotion
        } else if (captureRate > 1f) {
            _currentVideoMode.value = timeLapse
        } else {
            _currentVideoMode.value = normalMode
        }
        _captureRate.value = captureRate

        _videoModes.value = videoModes.map { model ->
            model.copy(selected = model.mode == _currentVideoMode.value.mode)
        }

        saveVideoModesDb(videoModes)

        val getCameraId = preview.getCameraId()
        val cameraIdSPhysicalPref = PreferenceKeys.getVideoCaptureRatePreferenceKey(
            getCameraId, appInterface.cameraIdSPhysicalPref
        )

        sharedPreferences.edit { putFloat(cameraIdSPhysicalPref, captureRate) }

        viewModelScope.launch {
            delay(100)
            updateForSettings(activity)
        }
    }

    fun updateForSettings(activity: MainActivity) {
        if (!activity.isDestroyed && !activity.isFinishing) {
            activity.updateForSettings(
                true, "", true, false
            )
        }
    }

    fun checkSwitchCamera(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ) {
        if (!isPhotoMode.value) {
            viewModelScope.launch {
                delay(1000)
                loadVideoModes(activity, appInterface, preview)
            }
        }
    }

    private fun setupPhotoResolutions(
        appInterface: MyApplicationInterface,
        preview: Preview,
    ): MutableList<SettingItemModel> {
        val result = mutableListOf<SettingItemModel>()
        try {
            if (!preview.isVideo && appInterface.photoMode != PhotoMode.Panorama) {
                val supportedPictureSizes = preview.getSupportedPictureSizes(true)
                supportedPictureSizes?.let {
                    val pictureSizes = it.asReversed()
                    val currentPictureSize = preview.getCurrentPictureSize()
                    pictureSizes.map { size ->
                        val fileSizeView = "(${Preview.getMPString(size.width, size.height)})"
                        val aspectView = getAspectRatio(size.width, size.height)

                        val model = SettingItemModel(
                            id = "${size.width} ${size.height}",
                            text = "${size.width}x${size.height}",
                            sub = "$aspectView $fileSizeView",
                            selected = size == currentPictureSize
                        )
                        result.add(model)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "setupPhotoResolutions failed: ${e.message}")
        }
        return result
    }

    private fun setupTimers(
        activity: MainActivity, appInterface: MyApplicationInterface
    ): MutableList<SettingItemModel> {
        val result = mutableListOf<SettingItemModel>()
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            if (appInterface.photoMode != PhotoMode.Panorama) {
                val timerValues = activity.resources.getStringArray(R.array.preference_timer_values)
                val timerEntries =
                    activity.resources.getStringArray(R.array.preference_timer_entries)
                val timerValue =
                    sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0") ?: "0"

                val timerIndex = timerValues.indexOf(timerValue).takeIf { it >= 0 } ?: 0
                timerValues.mapIndexed { index, value ->
                    val model = SettingItemModel(
                        id = value,
                        text = timerEntries.getOrNull(index).orEmpty(),
                        selected = index == timerIndex
                    )
                    result.add(model)
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "setupTimers failed: ${e.message}")
        }
        return result
    }

    private fun setupRepeats(
        activity: MainActivity, appInterface: MyApplicationInterface
    ): MutableList<SettingItemModel> {
        val result = mutableListOf<SettingItemModel>()
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            if (appInterface.photoMode != PhotoMode.Panorama) {
                val repeatModeValues =
                    activity.resources.getStringArray(R.array.preference_burst_mode_values)
                val repeatModeEntries =
                    activity.resources.getStringArray(R.array.preference_burst_mode_entries)
                val repeatModeValue: String =
                    sharedPreferences.getString(PreferenceKeys.RepeatModePreferenceKey, "1")!!
                repeatModeValues.mapIndexed { index, value ->
                    val model = SettingItemModel(
                        id = value,
                        text = repeatModeEntries.getOrNull(index).orEmpty(),
                        selected = value == repeatModeValue
                    )
                    result.add(model)
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "setupRepeats failed: ${e.message}")
        }
        return result
    }

    private fun setupVideo(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ): MutableList<SettingItemModel> {
        val result = mutableListOf<SettingItemModel>()
        try {
            val isPremiumUser = isPremiumUser(activity)
            if (preview.isVideo) {
                var videoSizes = preview.getSupportedVideoQuality(appInterface.getVideoFPSPref())
                if (videoSizes.isEmpty()) {
                    Logger.d(TAG, "can't find any supported video sizes for current fps!")
                    videoSizes = preview.videoQualityHander.getSupportedVideoQuality()
                }
                videoSizes = ArrayList<String>(videoSizes)
                videoSizes.reverse()

                val resolutionsPremium =
                    arrayListOf("2560x1920", "3264x1836", "4000x2000", "3840x2160", "3840x2160 4K")

                videoSizes.mapIndexed { index, value ->
                    val text = preview.getCamcorderProfileDescriptionShort(value)
                    val requirePremium = !isPremiumUser && resolutionsPremium.contains(text)
                    val model = SettingItemModel(
                        id = value,
                        text = text + (if (requirePremium) " (PRO)" else ""),
                        selected = value == preview.videoQualityHander.getCurrentVideoQuality(),
                        isPremium = requirePremium
                    )
                    Logger.d("resolutionOfVideo ", "${model.id} ${model.text}")
                    result.add(model)
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "setupVideo failed: ${e.message}")
        }
        return result
    }

    private fun setupSpeedVideo(
        activity: MainActivity, appInterface: MyApplicationInterface, preview: Preview
    ): MutableList<SettingItemModel> {
        val result = mutableListOf<SettingItemModel>()
        try {
            val isPremiumUser = isPremiumUser(activity)
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
            if (preview.isVideo) {
                val captureRateValues = appInterface.supportedVideoCaptureRates
                if (captureRateValues.size > 1) {
                    val captureRateValue = sharedPreferences.getFloat(
                        PreferenceKeys.getVideoCaptureRatePreferenceKey(
                            preview.getCameraId(), appInterface.cameraIdSPhysicalPref
                        ), 1.0f
                    )
                    captureRateValues.mapIndexed { index, value ->
                        Logger.d("speeds ", "$index $value")
                        val text = if (abs(1.0f - (value ?: 0f)) < 1.0e-5) {
                            activity.getString(R.string.preference_video_capture_rate_normal)
                        } else {
                            value.toString() + "x"
                        }
                        val requirePremium = !isPremiumUser && (value?.toInt() ?: 0) >= 120
                        val model = SettingItemModel(
                            id = "$value",
                            text = text + (if (requirePremium) " (PRO)" else ""),
                            selected = value == captureRateValue,
                            isPremium = requirePremium
                        )
                        result.add(model)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "setupVideo failed: ${e.message}")
        }
        return result
    }

    private fun setupFlash(
        activity: MainActivity, preview: Preview
    ): MutableList<SettingItemModel> {
        val result = mutableListOf<SettingItemModel>()
        try {
            val supportedFlashValues = preview.supportedFlashValues.orEmpty()
            if (supportedFlashValues.isNotEmpty()) {
                val flashEntries = activity.getResources().getStringArray(R.array.flash_entries)
                val flashValues = activity.getResources().getStringArray(R.array.flash_values)
                supportedFlashValues.map { flashValue ->
                    val flashIndex = flashValues.indexOf(flashValue).takeIf { it >= 0 } ?: 0
                    val flashEntry = flashEntries.getOrNull(flashIndex).orEmpty()
                    val model = SettingItemModel(
                        id = flashValue,
                        selected = flashValue == preview.currentFlashValue,
                        icon = getFlashIcon(flashValue),
                        text = flashEntry
                    )
                    result.add(model)
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "setupFlash failed: ${e.message}")
        }
        return result
    }

    private fun setupRaw(
        activity: MainActivity
    ): MutableList<SettingItemModel> {
        val result = mutableListOf<SettingItemModel>()
        try {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
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
            rawValues.mapIndexed { index, flashValue ->
                val model = SettingItemModel(
                    id = flashValue,
                    text = rawEntries[index],
                    selected = index == rawModeIndex,
                    icon = rawIcons[index]
                )
                result.add(model)
            }
        } catch (e: Exception) {
            Logger.d(TAG, "setupRaw failed: ${e.message}")
        }
        return result
    }
}