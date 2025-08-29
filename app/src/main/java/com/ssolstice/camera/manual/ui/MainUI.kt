package com.ssolstice.camera.manual.ui

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.ZoomControls
import com.ssolstice.camera.manual.MainActivity
import com.ssolstice.camera.manual.MainActivity.Companion.getRotationFromSystemOrientation
import com.ssolstice.camera.manual.MainActivity.SystemOrientation
import com.ssolstice.camera.manual.MyApplicationInterface
import com.ssolstice.camera.manual.MyDebug
import com.ssolstice.camera.manual.PreferenceKeys
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.cameracontroller.CameraController
import com.ssolstice.camera.manual.compose.CameraViewModel
import com.ssolstice.camera.manual.preview.ApplicationInterface.RawPref
import com.ssolstice.camera.manual.ui.PopupView.ButtonOptionsPopupListener
import java.util.Hashtable
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.min

/** This contains functionality related to the main UI.
 */
class MainUI(mainActivity: MainActivity, cameraViewModel: CameraViewModel) {

    private val mainActivity: MainActivity

    private val cameraViewModel: CameraViewModel

    @Volatile
    private var popup_view_is_open = false // must be volatile for test project reading the state
    var popupView: PopupView? = null
        private set
    private var force_destroy_popup =
        false // if true, then the popup isn't cached for only the next time the popup is closed

    private var current_orientation = 0

    enum class UIPlacement {
        UIPLACEMENT_RIGHT,
        UIPLACEMENT_LEFT,
        UIPLACEMENT_TOP
    }

    var uIPlacement: UIPlacement = UIPlacement.UIPLACEMENT_RIGHT
        private set

    var topIcon: View? = null
        private set

    private var navigation_gap_landscape_align_parent_bottom = 0
    private var navigation_gap_reverse_landscape_align_parent_bottom = 0
    private var view_rotate_animation = false
    private var view_rotate_animation_start = 0f // for MainActivity.lock_to_landscape==false
    private var immersive_mode = false
    private var show_gui_photo =
        true // result of call to showGUI() - false means a "reduced" GUI is displayed, whilst taking photo or video
    private var show_gui_video = true

    private var keydown_volume_up = false
    private var keydown_volume_down = false

    // For remote control: keep track of the currently highlighted
    // line and icon within the line
    private var remote_control_mode = false // whether remote control mode is enabled
    private var mPopupLine = 0
    private var mPopupIcon = 0
    private var mHighlightedLine: LinearLayout? = null
    private var mHighlightedIcon: View? = null
    private var mSelectingIcons = false
    private var mSelectingLines = false
    private var mExposureLine = 0
    private var mSelectingExposureUIElement = false
    private val highlightColor = Color.rgb(183, 28, 28) // Red 900
    private val highlightColorExposureUIElement = Color.rgb(244, 67, 54) // Red 500

    // for testing:
    val testUIButtonsMap: MutableMap<String?, View?> = Hashtable<String?, View?>()

    private fun setSeekbarColors() {
        if (MyDebug.LOG) Log.d(TAG, "setSeekbarColors")
        run {
            val progressColor = ColorStateList.valueOf(Color.argb(255, 240, 240, 240))
            val thumbColor = ColorStateList.valueOf(Color.argb(255, 255, 255, 255))

            var seekBar = mainActivity.binding.zoomSeekbar
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.binding.focusSeekbar
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.binding.focusBracketingTargetSeekbar
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.binding.exposureSeekbar
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.binding.isoSeekbar
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.binding.exposureTimeSeekbar
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.binding.whiteBalanceSeekbar
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor
        }
    }

    fun layoutUIWithRotation(viewRotateAnimationStart: Float) {
        if (MyDebug.LOG) {
            Log.d(TAG, "layoutUIWithRotation: $viewRotateAnimationStart")
        }
        this.view_rotate_animation = true
        this.view_rotate_animation_start = viewRotateAnimationStart
        this.view_rotate_animation = false
        this.view_rotate_animation_start = 0.0f
    }

    private fun computeUIPlacement(): UIPlacement {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val uiPlacementString: String =
            sharedPreferences.getString(PreferenceKeys.UIPlacementPreferenceKey, "ui_top")!!
        return when (uiPlacementString) {
            "ui_left" -> UIPlacement.UIPLACEMENT_LEFT
            "ui_top" -> UIPlacement.UIPLACEMENT_TOP
            else -> UIPlacement.UIPLACEMENT_RIGHT
        }
    }

    // stores with width and height of the last time we laid out the UI
    var layoutUI_display_w: Int = -1
    var layoutUI_display_h: Int = -1

    /** Wrapper for layoutParams.setMargins, but where the margins are supplied for landscape orientation,
     * and if in portrait these are automatically rotated.
     */
    fun setMarginsForSystemUI(
        layoutParams: RelativeLayout.LayoutParams,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        val system_orientation = mainActivity.systemOrientation
        if (system_orientation == SystemOrientation.PORTRAIT) {
            layoutParams.setMargins(bottom, left, top, right)
        } else if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) {
            layoutParams.setMargins(right, bottom, left, top)
        } else {
            layoutParams.setMargins(left, top, right, bottom)
        }
    }

    /** Some views (e.g. seekbars and zoom controls) are ones where we want to have a fixed
     * orientation as if in landscape mode, even if the system UI is portrait. So this method
     * sets a rotation so that the view appears as if in landscape orentation, and also sets
     * margins.
     * Note that Android has poor support for a rotated seekbar - we use view.setRotation(), but
     * this doesn't affect the bounds of the view! So as a hack, we modify the margins so the
     * view is positioned correctly. For this to work, the view must have a specified width
     * (which can be computed programmatically), rather than having both left and right sides being
     * aligned to another view.
     * The left/top/right/bottom margins should be supply for landscape orientation - these will
     * be automatically rotated if we're actually in portrait orientation.
     */
    private fun setFixedRotation(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        val system_orientation = mainActivity.systemOrientation
        val rotation = (360 - getRotationFromSystemOrientation(system_orientation)) % 360
        view.rotation = rotation.toFloat()
        // set margins due to rotation
        val layoutParams = view.layoutParams as RelativeLayout.LayoutParams
        if (system_orientation == SystemOrientation.PORTRAIT) {
            val diff = (layoutParams.width - layoutParams.height) / 2
            if (MyDebug.LOG) Log.d(TAG, "diff: $diff")
            setMarginsForSystemUI(
                layoutParams,
                diff + left,
                -diff + top,
                diff + right,
                -diff + bottom
            )
        } else {
            setMarginsForSystemUI(layoutParams, left, top, right, bottom)
        }
        view.layoutParams = layoutParams
    }

    fun setFocusSeekbarsRotation() {
        setFixedRotation(
            mainActivity.binding.focusSeekbar,
            0,
            navigation_gap_reverse_landscape_align_parent_bottom,
            0,
            navigation_gap_landscape_align_parent_bottom
        )
        // don't need to set margins for navigation gap landscape for focus_bracketing_target_seekbar, as it sits above the source focus_seekbar
        setFixedRotation(
            mainActivity.binding.focusBracketingTargetSeekbar,
            0,
            0,
            0,
            0
        )
    }

    /** Set icons for taking photos vs videos.
     * Also handles content descriptions for the take photo button and switch video button.
     */
    fun setTakePhotoIcon() {
        if (MyDebug.LOG) {
            Log.d(TAG, "setTakePhotoIcon()")
        }
        if (mainActivity.preview != null) {
            if (mainActivity.preview!!.isVideo) {
                cameraViewModel.setPhotoMode(false)
                if (MyDebug.LOG) {
                    Log.d(TAG, "set icon to video")
                }
            } else if (mainActivity.applicationInterface!!.getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama &&
                mainActivity.applicationInterface!!.gyroSensor.isRecording
            ) {
                if (MyDebug.LOG) {
                    Log.d(TAG, "set icon to recording panorama")
                }
            } else {
                if (MyDebug.LOG) {
                    Log.d(TAG, "set icon to photo")
                }
            }

            if (mainActivity.preview!!.isVideo) {
                cameraViewModel.setPhotoMode(false)
            } else {
                cameraViewModel.setPhotoMode(true)
            }

            cameraViewModel.setVideoRecording(mainActivity.preview!!.isVideoRecording)
        }
    }

    /** Set content description for switch camera button.
     */
    fun setSwitchCameraContentDescription() {
        if (mainActivity.preview != null && mainActivity.preview!!.canSwitchCamera()) {
            val cameraId = mainActivity.nextCameraId
            val contentDescription =
                when (mainActivity.preview!!.cameraControllerManager.getFacing(cameraId)) {
                    CameraController.Facing.FACING_FRONT -> R.string.switch_to_front_camera
                    CameraController.Facing.FACING_BACK -> R.string.switch_to_back_camera
                    CameraController.Facing.FACING_EXTERNAL -> R.string.switch_to_external_camera
                    else -> R.string.switch_to_unknown_camera
                }
        }
    }

    /** Set content description for pause video button.
     */
    fun setPauseVideoContentDescription() {
        if (MyDebug.LOG) Log.d(TAG, "setPauseVideoContentDescription()")
        if (mainActivity.preview!!.isVideoRecordingPaused) {
            cameraViewModel.setVideoRecordingPaused(true)
        } else {
            cameraViewModel.setVideoRecordingPaused(false)
        }
    }

    fun updateRemoteConnectionIcon() {
        val remoteConnectedIcon = mainActivity.findViewById<View>(R.id.kraken_icon)
        if (mainActivity.getBluetoothRemoteControl().remoteConnected()) {
            remoteConnectedIcon.visibility = View.VISIBLE
        } else {
            remoteConnectedIcon.visibility = View.GONE
        }
    }

    fun onOrientationChanged(orientation: Int) {
        var orientation = orientation
        if (!MainActivity.lockToLandscape) return
        // if locked to landscape, we need to handle the orientation change ourselves
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
        var diff = abs(orientation - current_orientation)
        if (diff > 180) diff = 360 - diff
        // only change orientation when sufficiently changed
        if (diff > 60) {
            orientation = (orientation + 45) / 90 * 90
            orientation = orientation % 360
            if (orientation != current_orientation) {
                this.current_orientation = orientation
                view_rotate_animation = true
                view_rotate_animation = false
                val handler = Handler()
                handler.postDelayed({
                    mainActivity.applicationInterface!!.drawPreview.updateSettings()
                }, (view_rotate_animation_duration + 20).toLong())
            }
        }
    }

    fun showExposureLockIcon(): Boolean {
        if (!mainActivity.preview!!.supportsExposureLock()) return false
        if (mainActivity.applicationInterface!!.isCameraExtensionPref()) {
            // not supported for camera extensions
            return false
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowExposureLockPreferenceKey, true)
    }

    fun showWhiteBalanceLockIcon(): Boolean {
        if (!mainActivity.preview!!.supportsWhiteBalanceLock()) return false
        if (mainActivity.applicationInterface!!.isCameraExtensionPref()) {
            // not supported for camera extensions
            return false
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowWhiteBalanceLockPreferenceKey, false)
    }

    fun showCycleRawIcon(): Boolean {
        if (!mainActivity.preview!!.supportsRaw()) return false
        if (!mainActivity.applicationInterface!!.isRawAllowed(mainActivity.applicationInterface!!.getPhotoMode())) return false
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowCycleRawPreferenceKey, false)
    }

    fun showStoreLocationIcon(): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowStoreLocationPreferenceKey, false)
    }

    fun showTextStampIcon(): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowTextStampPreferenceKey, false)
    }

    fun showStampIcon(): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowStampPreferenceKey, false)
    }

    fun showFocusPeakingIcon(): Boolean {
        if (!mainActivity.supportsPreviewBitmaps()) return false
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowFocusPeakingPreferenceKey, false)
    }

    fun showAutoLevelIcon(): Boolean {
        if (!mainActivity.supportsAutoStabilise()) return false
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowAutoLevelPreferenceKey, false)
    }

    fun showCycleFlashIcon(): Boolean {
        if (!mainActivity.preview!!.supportsFlash()) return false
        if (mainActivity.preview!!.isVideo) return false // no point showing flash icon in video mode, as we only allow flash auto and flash torch, and we don't support torch on the on-screen cycle flash icon

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowCycleFlashPreferenceKey, false)
    }

    fun showFaceDetectionIcon(): Boolean {
        if (!mainActivity.preview!!.supportsFaceDetection()) return false
        if (mainActivity.applicationInterface!!.isCameraExtensionPref()) {
            // not supported for camera extensions
            return false
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        return sharedPreferences.getBoolean(PreferenceKeys.ShowFaceDetectionPreferenceKey, false)
    }

    fun setImmersiveMode(immersiveMode: Boolean) {
        this.immersive_mode = immersiveMode
        mainActivity.runOnUiThread {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
            val visibility = View.VISIBLE
            val cycleRawButton = mainActivity.binding.cycleRaw
            val storeLocationButton = mainActivity.binding.storeLocation
            val textStampButton = mainActivity.binding.textStamp
            val stampButton = mainActivity.binding.stamp
            val focusPeakingButton = mainActivity.binding.focusPeaking
            val autoLevelButton = mainActivity.binding.autoLevel
            val cycleFlashButton = mainActivity.binding.cycleFlash
            val faceDetectionButton = mainActivity.binding.faceDetection
            val audioControlButton = mainActivity.binding.audioControl
            val settingsButton = mainActivity.binding.settings
            val zoomControls = mainActivity.binding.zoom
            val zoomSeekBar = mainActivity.binding.zoomSeekbar
            val focusSeekBar = mainActivity.binding.focusSeekbar
            val focusBracketingTargetSeekBar = mainActivity.binding.focusBracketingTargetSeekbar

            if (showCycleRawIcon()) cycleRawButton.visibility = visibility
            if (showStoreLocationIcon()) storeLocationButton.visibility = visibility
            if (showTextStampIcon()) textStampButton.visibility = visibility
            if (showStampIcon()) stampButton.visibility = visibility
            if (showFocusPeakingIcon()) focusPeakingButton.visibility = visibility
            if (showAutoLevelIcon()) autoLevelButton.visibility = visibility
            if (showCycleFlashIcon()) cycleFlashButton.visibility = visibility
            if (showFaceDetectionIcon()) faceDetectionButton.visibility = visibility
            if (mainActivity.hasAudioControl()) audioControlButton.visibility = visibility

            settingsButton.visibility = visibility
            if (mainActivity.preview!!.supportsZoom() && sharedPreferences.getBoolean(
                    PreferenceKeys.ShowZoomControlsPreferenceKey,
                    false
                )
            ) {
                zoomControls.visibility = visibility
            }
            if (mainActivity.preview!!.supportsZoom()
                && sharedPreferences.getBoolean(
                    PreferenceKeys.ShowZoomSliderControlsPreferenceKey,
                    true
                )
            ) {
                zoomSeekBar.visibility = visibility
            }

            if (mainActivity.showManualFocusSeekbar(false)) focusSeekBar.visibility = visibility

            if (mainActivity.showManualFocusSeekbar(true)) focusBracketingTargetSeekBar.visibility =
                visibility

            val prefImmersiveMode: String = sharedPreferences.getString(
                PreferenceKeys.ImmersiveModePreferenceKey,
                "immersive_mode_off"
            )!!
            if (prefImmersiveMode == "immersive_mode_everything") {
                if (mainActivity.applicationInterface!!.gyroSensor.isRecording) {
                    val cancelPanoramaButton =
                        mainActivity.binding.cancelPanorama
                    cancelPanoramaButton.visibility = visibility
                }
            }
            if (!immersiveMode) {
                showGUI()
            }
        }
    }

    fun inImmersiveMode(): Boolean {
        return immersive_mode
    }

    fun showGUI(show: Boolean, isVideo: Boolean) {
        if (isVideo) this.show_gui_video = show
        else this.show_gui_photo = show
        showGUI()
    }

    fun showGUI() {
        if (inImmersiveMode()) return
        if ((show_gui_photo || show_gui_video) && mainActivity.usingKitKatImmersiveMode()) {
            // call to reset the timer
            mainActivity.initImmersiveMode()
        }
        mainActivity.runOnUiThread {
            val is_panorama_recording =
                mainActivity.applicationInterface!!.getGyroSensor().isRecording()
            val visibility =
                if (is_panorama_recording) View.GONE else if (show_gui_photo && show_gui_video) View.VISIBLE else View.GONE // for UI that is hidden while taking photo or video
            val cycleRawButton = mainActivity.binding.cycleRaw
            val storeLocationButton = mainActivity.binding.storeLocation
            val textStampButton = mainActivity.binding.textStamp
            val stampButton = mainActivity.binding.stamp
            val focusPeakingButton = mainActivity.binding.focusPeaking
            val autoLevelButton = mainActivity.binding.autoLevel
            val cycleFlashButton = mainActivity.binding.cycleFlash
            val faceDetectionButton = mainActivity.binding.faceDetection
            val audioControlButton = mainActivity.binding.audioControl

            if (showCycleRawIcon()) cycleRawButton.visibility = visibility
            if (showStoreLocationIcon()) storeLocationButton.visibility = visibility
            if (showTextStampIcon()) textStampButton.visibility = visibility
            if (showStampIcon()) stampButton.visibility = visibility
            if (showFocusPeakingIcon()) focusPeakingButton.visibility = visibility
            if (showAutoLevelIcon()) autoLevelButton.visibility = visibility
            if (showCycleFlashIcon()) cycleFlashButton.visibility = visibility
            if (showFaceDetectionIcon()) faceDetectionButton.visibility = visibility
            if (mainActivity.hasAudioControl()) audioControlButton.visibility = visibility
            if (!(show_gui_photo && show_gui_video)) {
                closePopup() // we still allow the popup when recording video, but need to update the UI (so it only shows flash options), so easiest to just close
            }

            val remoteConnectedIcon = mainActivity.binding.krakenIcon
            if (mainActivity.getBluetoothRemoteControl().remoteConnected()) {
                remoteConnectedIcon.visibility = View.VISIBLE
            } else {
                remoteConnectedIcon.visibility = View.GONE
            }
        }
    }

    fun updateExposureLockIcon() {
//        val view = mainActivity.findViewById<ImageButton>(R.id.exposure_lock)
//        val enabled = mainActivity.preview!!.isExposureLocked
//        view.setImageResource(if (enabled) R.drawable.exposure_locked else R.drawable.exposure_unlocked)
//        view.contentDescription = mainActivity.getResources()
//            .getString(if (enabled) R.string.exposure_unlock else R.string.exposure_lock)
    }

    fun updateWhiteBalanceLockIcon() {
//        val view = mainActivity.findViewById<ImageButton>(R.id.white_balance_lock)
//        val enabled = mainActivity.preview!!.isWhiteBalanceLocked
//        view.setImageResource(if (enabled) R.drawable.white_balance_locked else R.drawable.white_balance_unlocked)
//        view.contentDescription = mainActivity.getResources()
//            .getString(if (enabled) R.string.white_balance_unlock else R.string.white_balance_lock)
    }

    fun updateCycleRawIcon() {
        val raw_pref = mainActivity.applicationInterface!!.getRawPref()
        val view = mainActivity.findViewById<ImageButton>(R.id.cycle_raw)
        if (raw_pref == RawPref.RAWPREF_JPEG_DNG) {
            if (mainActivity.applicationInterface!!.isRawOnly()) {
                // actually RAW only
                view.setImageResource(R.drawable.raw_only_icon)
            } else {
                view.setImageResource(R.drawable.raw_icon)
            }
        } else {
            view.setImageResource(R.drawable.raw_off_icon)
        }
    }

    fun updateStoreLocationIcon() {
        mainActivity.applicationInterface?.let {
            val view = mainActivity.binding.storeLocation
            val enabled = mainActivity.applicationInterface!!.geotaggingPref
            view.setImageResource(if (enabled) R.drawable.ic_gps_fixed_red_48dp else R.drawable.ic_gps_fixed_white_48dp)
            view.contentDescription = mainActivity.getResources()
                .getString(if (enabled) R.string.preference_location_disable else R.string.preference_location_enable)
        }
    }

    fun updateTextStampIcon() {
        val view = mainActivity.binding.textStamp
        val enabled = !mainActivity.applicationInterface!!.textStampPref.isEmpty()
        view.setImageResource(if (enabled) R.drawable.baseline_text_fields_red_48 else R.drawable.baseline_text_fields_white_48)
    }

    fun updateStampIcon() {
        val view = mainActivity.binding.stamp
        val enabled = mainActivity.applicationInterface!!.stampPref == "preference_stamp_yes"
        view.setImageResource(if (enabled) R.drawable.ic_text_format_red_48dp else R.drawable.ic_text_format_white_48dp)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.stamp_disable else R.string.stamp_enable)
    }

    fun updateFocusPeakingIcon() {
        val view = mainActivity.binding.focusPeaking
        val enabled = mainActivity.applicationInterface!!.getFocusPeakingPref()
        view.setImageResource(if (enabled) R.drawable.key_visualizer_red else R.drawable.key_visualizer)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.focus_peaking_disable else R.string.focus_peaking_enable)
    }

    fun updateAutoLevelIcon() {
        val view = mainActivity.binding.autoLevel
        val enabled = mainActivity.applicationInterface!!.getAutoStabilisePref()
        view.setImageResource(if (enabled) R.drawable.auto_stabilise_icon_red else R.drawable.auto_stabilise_icon)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.auto_level_disable else R.string.auto_level_enable)
    }

    fun updateCycleFlashIcon() {
        // n.b., read from preview rather than saved application preference - so the icon updates correctly when in flash
        // auto mode, but user switches to manual ISO where flash auto isn't supported
        val flashValue = mainActivity.preview!!.getCurrentFlashValue()
        if (flashValue != null) {
            when (flashValue) {
                "flash_off" -> mainActivity.binding.cycleFlash.setImageResource(R.drawable.flash_off)
                "flash_auto", "flash_frontscreen_auto" -> mainActivity.binding.cycleFlash.setImageResource(
                    R.drawable.flash_auto
                )

                "flash_on", "flash_frontscreen_on" -> mainActivity.binding.cycleFlash.setImageResource(
                    R.drawable.flash_on
                )

                "flash_torch", "flash_frontscreen_torch" -> mainActivity.binding.cycleFlash.setImageResource(
                    R.drawable.baseline_highlight_white_48
                )

                "flash_red_eye" -> mainActivity.binding.cycleFlash.setImageResource(R.drawable.baseline_remove_red_eye_white_48)
                else -> {
                    // just in case??
                    Log.e(TAG, "unknown flash value $flashValue")
                    mainActivity.binding.cycleFlash.setImageResource(R.drawable.flash_off)
                }
            }
        } else {
            mainActivity.binding.cycleFlash.setImageResource(R.drawable.flash_off)
        }
    }

    fun updateFaceDetectionIcon() {
        val view = mainActivity.binding.faceDetection
        val enabled = mainActivity.applicationInterface!!.getFaceDetectionPref()
        view.setImageResource(if (enabled) R.drawable.ic_face_red_48dp else R.drawable.ic_face_white_48dp)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.face_detection_disable else R.string.face_detection_enable)
    }

    fun updateOnScreenIcons() {
        if (MyDebug.LOG) Log.d(TAG, "updateOnScreenIcons")
        this.updateExposureLockIcon()
        this.updateWhiteBalanceLockIcon()
        this.updateCycleRawIcon()
        this.updateStoreLocationIcon()
        this.updateTextStampIcon()
        this.updateStampIcon()
        this.updateFocusPeakingIcon()
        this.updateAutoLevelIcon()
        this.updateCycleFlashIcon()
        this.updateFaceDetectionIcon()
    }

    fun audioControlStarted() {
        mainActivity.binding.audioControl.setImageResource(R.drawable.ic_mic_red_48dp)
        mainActivity.binding.audioControl.contentDescription = mainActivity.getResources().getString(R.string.audio_control_stop)
    }

    fun audioControlStopped() {
        mainActivity.binding.audioControl.setImageResource(R.drawable.ic_mic_white_48dp)
        mainActivity.binding.audioControl.contentDescription =
            mainActivity.getResources().getString(R.string.audio_control_start)
    }

    val isExposureUIOpen: Boolean
        get() {
            val exposureVisibility = mainActivity.binding.exposureContainer.visibility
            val manualExposureVisibility = mainActivity.binding.manualExposureContainer.visibility
            return exposureVisibility == View.VISIBLE || manualExposureVisibility == View.VISIBLE
        }

    /**
     * Opens or close the exposure settings (ISO, white balance, etc)
     */
    fun toggleExposureUI() {
        if (MyDebug.LOG) Log.d(TAG, "toggleExposureUI")
        closePopup()
        mSelectingExposureUIElement = false
        if (this.isExposureUIOpen) {
        } else if (mainActivity.preview!!.cameraController != null && mainActivity.supportsExposureButton()) {
            setupExposureUI()
            if (mainActivity.getBluetoothRemoteControl().remoteEnabled()) {
                initRemoteControlForExposureUI()
            }
        }
    }

    private fun initRemoteControlForExposureUI() {
        if (MyDebug.LOG) Log.d(TAG, "initRemoteControlForExposureUI")
        if (this.isExposureUIOpen) { // just in case
            remote_control_mode = true
            mExposureLine = 0
            highlightExposureUILine(true)
        }
    }

    private fun resetExposureUIHighlights() {
        mainActivity.binding.isoButtons.setBackgroundColor(Color.TRANSPARENT)
        mainActivity.binding.exposureContainer.setBackgroundColor(Color.TRANSPARENT)
        mainActivity.binding.exposureTimeSeekbar.setBackgroundColor(Color.TRANSPARENT)
        mainActivity.binding.isoSeekbar.setBackgroundColor(Color.TRANSPARENT)
        mainActivity.binding.whiteBalanceSeekbar.setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Highlights the relevant line on the Exposure UI based on
     * the value of mExposureLine
     *
     */
    private fun highlightExposureUILine(selectNext: Boolean) {
        if (!this.isExposureUIOpen) { // Safety check
            return
        }
        val iso_buttons_container = mainActivity.findViewById<ViewGroup>(R.id.iso_buttons) // Shown when Camera API2 enabled
        val exposure_seek_bar = mainActivity.binding.exposureContainer
        val shutter_seekbar = mainActivity.binding.exposureTimeSeekbar
        val iso_seekbar = mainActivity.binding.isoSeekbar
        val wb_seekbar = mainActivity.binding.whiteBalanceSeekbar
        mExposureLine = (mExposureLine + 5) % 5
        if (selectNext) {
            if (mExposureLine == 0 && !iso_buttons_container.isShown) mExposureLine++
            if (mExposureLine == 1 && !iso_seekbar.isShown) mExposureLine++
            if (mExposureLine == 2 && !shutter_seekbar.isShown) mExposureLine++
            if ((mExposureLine == 3) && !exposure_seek_bar.isShown) mExposureLine++
            if ((mExposureLine == 4) && !wb_seekbar.isShown) mExposureLine++
        } else {
            // Select previous
            if (mExposureLine == 4 && !wb_seekbar.isShown) mExposureLine--
            if (mExposureLine == 3 && !exposure_seek_bar.isShown) mExposureLine--
            if (mExposureLine == 2 && !shutter_seekbar.isShown) mExposureLine--
            if (mExposureLine == 1 && !iso_seekbar.isShown) mExposureLine--
            if (mExposureLine == 0 && !iso_buttons_container.isShown) mExposureLine--
        }
        mExposureLine = (mExposureLine + 5) % 5
        resetExposureUIHighlights()

        when (mExposureLine) {
            0 -> iso_buttons_container.setBackgroundColor(highlightColor)
            1 -> iso_seekbar.setBackgroundColor(highlightColor)
            2 -> shutter_seekbar.setBackgroundColor(highlightColor)
            3 -> exposure_seek_bar.setBackgroundColor(highlightColor)
            4 -> wb_seekbar.setBackgroundColor(highlightColor)
        }
    }

    private fun nextExposureUILine() {
        mExposureLine++
        highlightExposureUILine(true)
    }

    private fun previousExposureUILine() {
        mExposureLine--
        highlightExposureUILine(false)
    }

    private fun nextExposureUIItem() {
        when (mExposureLine) {
            0 -> nextIsoItem(false)
            1 -> changeSeekbar(R.id.iso_seekbar, 10)
            2 -> changeSeekbar(R.id.exposure_time_seekbar, 5)
            3 ->                 //changeSeekbar(R.id.exposure_seekbar, 1);
                mainActivity.changeExposure(1)

            4 -> changeSeekbar(R.id.white_balance_seekbar, 3)
        }
    }

    private fun previousExposureUIItem() {
        when (mExposureLine) {
            0 -> nextIsoItem(true)
            1 -> changeSeekbar(R.id.iso_seekbar, -10)
            2 -> changeSeekbar(R.id.exposure_time_seekbar, -5)
            3 ->                 //changeSeekbar(R.id.exposure_seekbar, -1);
                mainActivity.changeExposure(-1)

            4 -> changeSeekbar(R.id.white_balance_seekbar, -3)
        }
    }

    private fun nextIsoItem(previous: Boolean) {
        // Find current ISO
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val currentIso: String = sharedPreferences.getString(
            PreferenceKeys.ISOPreferenceKey,
            CameraController.ISO_DEFAULT
        )!!
        val count = isoButtons!!.size
        val step = if (previous) -1 else 1
        var found = false
        for (i in 0..<count) {
            val button = isoButtons!![i] as Button
            val button_text = button.text.toString()
            if (ISOTextEquals(button_text, currentIso)) {
                found = true
                // Select next one, unless it's "Manual", which we skip since
                // it's not practical in remote mode.
                var nextButton = isoButtons!![(i + count + step) % count] as Button
                val nextButton_text = nextButton.text.toString()
                if (nextButton_text.contains("m")) {
                    nextButton = isoButtons!![(i + count + 2 * step) % count] as Button
                }
                nextButton.callOnClick()
                break
            }
        }
        if (!found) {
            isoButtons!![0]!!.callOnClick()
        }
    }

    private fun selectExposureUILine() {
        if (!this.isExposureUIOpen) { // Safety check
            return
        }

        if (mExposureLine == 0) { // ISO presets
            val isoButtonsContainer = mainActivity.findViewById<ViewGroup>(R.id.iso_buttons)
            isoButtonsContainer.setBackgroundColor(highlightColorExposureUIElement)
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
            val currentIso: String = sharedPreferences.getString(
                PreferenceKeys.ISOPreferenceKey,
                CameraController.ISO_DEFAULT
            )!!
            // if the manual ISO value isn't one of the "preset" values, then instead highlight the manual ISO icon
            var found = false
            var manualButton: Button? = null
            for (view in isoButtons!!) {
                val button = view as Button
                val buttonText = button.text.toString()
                if (ISOTextEquals(buttonText, currentIso)) {
                    PopupView.setButtonSelected(button, true)
                    found = true
                } else {
                    if (buttonText.contains("m")) {
                        manualButton = button
                    }
                    PopupView.setButtonSelected(button, false)
                    button.setBackgroundColor(Color.TRANSPARENT)
                }
            }
            if (!found && manualButton != null) {
                PopupView.setButtonSelected(manualButton, true)
                manualButton.setBackgroundColor(highlightColorExposureUIElement)
            }
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 1) {
            mainActivity.binding.isoSeekbar.setBackgroundColor(highlightColorExposureUIElement)
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 2) {
            mainActivity.binding.exposureTimeSeekbar.setBackgroundColor(
                highlightColorExposureUIElement
            )
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 3) {
            mainActivity.binding.exposureContainer.setBackgroundColor(
                highlightColorExposureUIElement
            )
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 4) {
            mainActivity.binding.whiteBalanceSeekbar.setBackgroundColor(
                highlightColorExposureUIElement
            )
            mSelectingExposureUIElement = true
        }
    }

    /** Returns the height of the device in dp (or width in portrait mode), allowing for space for the
     * on-screen UI icons.
     * @param centred If true, then find the max height for a view that will be centred.
     */
    fun getMaxHeightDp(centred: Boolean): Int {
        // ensure we have display for landscape orientation (even if we ever allow ManualCamera
        val display_size = Point()
        mainActivity.applicationInterface!!.getDisplaySize(display_size, true)

        // normally we should always have heightPixels < widthPixels, but good not to assume we're running in landscape orientation
        val smaller_dim = min(display_size.x, display_size.y)
        // the smaller dimension should limit the width, due to when held in portrait
        val scale = mainActivity.getResources().getDisplayMetrics().density
        var dpHeight = (smaller_dim / scale).toInt()
        if (MyDebug.LOG) {
            Log.d(TAG, "display size: " + display_size.x + " x " + display_size.y)
            Log.d(TAG, "dpHeight: " + dpHeight)
        }
        // allow space for the icons at top/right of screen
        val margin = if (centred) 120 else 50
        dpHeight -= margin
        return dpHeight
    }

    val isSelectingExposureUIElement: Boolean
        get() {
            if (MyDebug.LOG) Log.d(
                TAG,
                "isSelectingExposureUIElement returns:" + mSelectingExposureUIElement
            )
            return mSelectingExposureUIElement
        }


    /**
     * Process a press to the "Up" button on a remote. Called from MainActivity.
     * @return true if an action was taken
     */
    fun processRemoteUpButton(): Boolean {
        if (MyDebug.LOG) Log.d(TAG, "processRemoteUpButton")
        var didProcess = false
        if (popupIsOpen()) {
            didProcess = true
            if (selectingIcons()) {
                previousPopupIcon()
            } else if (selectingLines()) {
                previousPopupLine()
            }
        } else if (this.isExposureUIOpen) {
            didProcess = true
            if (this.isSelectingExposureUIElement) {
                nextExposureUIItem()
            } else {
                previousExposureUILine()
            }
        }
        return didProcess
    }

    /**
     * Process a press to the "Down" button on a remote. Called from MainActivity.
     * @return true if an action was taken
     */
    fun processRemoteDownButton(): Boolean {
        if (MyDebug.LOG) Log.d(TAG, "processRemoteDownButton")
        var didProcess = false
        if (popupIsOpen()) {
            if (selectingIcons()) {
                nextPopupIcon()
            } else if (selectingLines()) {
                nextPopupLine()
            }
            didProcess = true
        } else if (this.isExposureUIOpen) {
            if (this.isSelectingExposureUIElement) {
                previousExposureUIItem()
            } else {
                nextExposureUILine()
            }
            didProcess = true
        }
        return didProcess
    }

    private var isoButtons: MutableList<View?>? = null
    private var isoButtonManualIndex = -1

    init {
        if (MyDebug.LOG) Log.d(TAG, "MainUI")
        this.mainActivity = mainActivity
        this.cameraViewModel = cameraViewModel

        this.setSeekbarColors()
    }

    /** Opens the exposure UI if not already open, and sets up or updates the UI.
     */
    fun setupExposureUI() {
        if (MyDebug.LOG) Log.d(TAG, "setupExposureUI")
        testUIButtonsMap.clear()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val preview = mainActivity.preview
//        val view = mainActivity.findViewById<ImageButton>(R.id.exposure)
//        view.setImageResource(R.drawable.ic_exposure_red_48dp)

        mainActivity.binding.slidersContainer.visibility = View.VISIBLE

        val animation = AnimationUtils.loadAnimation(mainActivity, R.anim.fade_in)
        mainActivity.binding.slidersContainer.startAnimation(animation)

        val isoButtonsContainer = mainActivity.findViewById<ViewGroup>(R.id.iso_buttons)
        isoButtonsContainer.removeAllViews()

        var supportedIsos: MutableList<String>
        if (preview!!.isVideoRecording) {
            supportedIsos = arrayListOf()
        } else if (preview.supportsISORange()) {
            val minIso = preview.getMinimumISO()
            val maxIso = preview.getMaximumISO()
            val values: MutableList<String> = ArrayList()
            values.add(CameraController.ISO_DEFAULT)
            values.add(manual_iso_value)
            isoButtonManualIndex = 1 // must match where we place the manual button!
            val isoValues = intArrayOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
            values.add(ISOToButtonText(minIso))
            for (isoValue in isoValues) {
                if (isoValue > minIso && isoValue < maxIso) {
                    values.add(ISOToButtonText(isoValue))
                }
            }
            values.add(ISOToButtonText(maxIso))
            supportedIsos = values
        } else {
            supportedIsos = preview.getSupportedISOs()
            isoButtonManualIndex = -1
        }
        var currentIso: String = sharedPreferences.getString(
            PreferenceKeys.ISOPreferenceKey,
            CameraController.ISO_DEFAULT
        )!!
        // if the manual ISO value isn't one of the "preset" values, then instead highlight the manual ISO icon
        if ((currentIso != CameraController.ISO_DEFAULT) && supportedIsos.contains(
                manual_iso_value
            ) && !supportedIsos.contains(currentIso)
        ) currentIso = manual_iso_value


        var totalWidthDp = 280
        val maxWidthDp = getMaxHeightDp(true)
        if (totalWidthDp > maxWidthDp) totalWidthDp = maxWidthDp
        if (MyDebug.LOG) Log.d(TAG, "total_width_dp: $totalWidthDp")

        // n.b., we hardcode the string "ISO" as this isn't a user displayed string, rather it's used to filter out "ISO" included in old Camera API parameters
        isoButtons = PopupView.createButtonOptions(
            isoButtonsContainer,
            mainActivity,
            totalWidthDp,
            this.testUIButtonsMap,
            supportedIsos,
            -1,
            -1,
            "ISO",
            false,
            currentIso,
            0,
            "TEST_ISO",
            object : ButtonOptionsPopupListener() {
                override fun onClick(option: String?) {
                    if (MyDebug.LOG) Log.d(TAG, "clicked iso: $option")
                    val editor = sharedPreferences.edit()
                    val oldIso: String = sharedPreferences.getString(
                        PreferenceKeys.ISOPreferenceKey,
                        CameraController.ISO_DEFAULT
                    )!!
                    if (MyDebug.LOG) {
                        Log.d(TAG, "old_iso: $oldIso")
                    }
                    editor.putString(PreferenceKeys.ISOPreferenceKey, option)
                    var toastOption: String? = option

                    if (preview.supportsISORange()) {
                        if (option == CameraController.ISO_DEFAULT) {
                            if (MyDebug.LOG) Log.d(TAG, "switched from manual to auto iso")
                            // also reset exposure time when changing from manual to auto from the popup menu:
                            editor.putLong(
                                PreferenceKeys.ExposureTimePreferenceKey,
                                CameraController.EXPOSURE_TIME_DEFAULT
                            )
                            editor.apply()
                            preview.showToast(
                                null,
                                "ISO: $toastOption",
                                0,
                                true
                            ) // supply offset_y_dp to be consistent with preview.setExposure(), preview.setISO()
                            mainActivity.updateForSettings(
                                true,
                                ""
                            ) // already showed the toast, so block from showing again
                        } else if (oldIso == CameraController.ISO_DEFAULT) {
                            if (MyDebug.LOG) Log.d(TAG, "switched from auto to manual iso")
                            if (option == "m") {
                                // if we used the generic "manual", then instead try to preserve the current iso if it exists
                                if (preview.cameraController != null && preview.cameraController.captureResultHasIso()
                                ) {
                                    val iso = preview.cameraController.captureResultIso()
                                    if (MyDebug.LOG) Log.d(TAG, "apply existing iso of $iso")
                                    editor.putString(
                                        PreferenceKeys.ISOPreferenceKey,
                                        iso.toString()
                                    )
                                    toastOption = iso.toString()
                                } else {
                                    if (MyDebug.LOG) Log.d(TAG, "no existing iso available")
                                    // use a default
                                    val iso = 800
                                    editor.putString(PreferenceKeys.ISOPreferenceKey, "" + iso)
                                    toastOption = "" + iso
                                }
                            }

                            // if changing from auto to manual, preserve the current exposure time if it exists
                            if (preview.cameraController != null && preview.cameraController
                                    .captureResultHasExposureTime()
                            ) {
                                val exposureTime =
                                    preview.cameraController.captureResultExposureTime()
                                if (MyDebug.LOG) Log.d(
                                    TAG,
                                    "apply existing exposure time of $exposureTime"
                                )
                                editor.putLong(
                                    PreferenceKeys.ExposureTimePreferenceKey,
                                    exposureTime
                                )
                            } else {
                                if (MyDebug.LOG) Log.d(TAG, "no existing exposure time available")
                            }

                            editor.apply()
                            preview.showToast(
                                null,
                                "ISO: $toastOption",
                                0,
                                true
                            ) // supply offset_y_dp to be consistent with preview.setExposure(), preview.setISO()
                            mainActivity.updateForSettings(
                                true,
                                ""
                            ) // already showed the toast, so block from showing again
                        } else {
                            if (MyDebug.LOG) Log.d(TAG, "changed manual iso")
                            if (option == "m") {
                                // if user selected the generic "manual", then just keep the previous non-ISO option
                                if (MyDebug.LOG) Log.d(TAG, "keep existing iso of $oldIso")
                                editor.putString(PreferenceKeys.ISOPreferenceKey, oldIso)
                            }

                            editor.apply()
                            val iso = preview.parseManualISOValue(option)
                            if (iso >= 0) {
                                // if changing between manual ISOs, no need to call updateForSettings, just change the ISO directly (as with changing the ISO via manual slider)
                                //preview.setISO(iso);
                                //updateSelectedISOButton();
                                // rather than set ISO directly, we move the seekbar, and the ISO will be changed via the seekbar listener
                                val isoSeekBar =
                                    mainActivity.findViewById<SeekBar?>(R.id.iso_seekbar)
                                mainActivity.getManualSeekbars()
                                    .setISOProgressBarToClosest(isoSeekBar, iso.toLong())
                            }
                        }
                    } else {
                        editor.apply()
                        preview.cameraController?.setISO(option)
                    }

                    setupExposureUI()
                }
            })
        val isoContainerView = mainActivity.binding.isoContainer
        isoContainerView.visibility = View.VISIBLE

        val exposureSeekBar = mainActivity.binding.exposureContainer
        val manualExposureSeekBar = mainActivity.binding.manualExposureContainer
        val isoValue = mainActivity.applicationInterface!!.isoPref
        if (mainActivity.preview!!.usingCamera2API() && isoValue != CameraController.ISO_DEFAULT) {
            exposureSeekBar.visibility = View.GONE

            // with Camera2 API, when using manual ISO we instead show sliders for ISO range and exposure time
            if (mainActivity.preview!!.supportsISORange()) {
                manualExposureSeekBar.visibility = View.VISIBLE
                val exposureTimeSeekBar = mainActivity.binding.exposureTimeSeekbar
                if (mainActivity.preview!!.supportsExposureTime()) {
                    exposureTimeSeekBar.visibility = View.VISIBLE
                } else {
                    exposureTimeSeekBar.visibility = View.GONE
                }
            } else {
                manualExposureSeekBar.visibility = View.GONE
            }
        } else {
            manualExposureSeekBar.visibility = View.GONE

            if (mainActivity.preview!!.supportsExposures()) {
                exposureSeekBar.visibility = View.VISIBLE
                val seek_bar_zoom =
                    mainActivity.findViewById<ZoomControls>(R.id.exposure_seekbar_zoom)
                seek_bar_zoom.visibility = View.VISIBLE
            } else {
                exposureSeekBar.visibility = View.GONE
            }
        }

        if (mainActivity.preview!!.supportsWhiteBalanceTemperature()) {
            if (mainActivity.preview!!.usingCamera2API() && mainActivity.applicationInterface!!.whiteBalancePref == "manual") {
                mainActivity.binding.manualWhiteBalanceContainer.visibility = View.VISIBLE
            } else {
                mainActivity.binding.manualWhiteBalanceContainer.visibility = View.GONE
            }
        } else {
            mainActivity.binding.manualWhiteBalanceContainer.visibility = View.GONE
        }
    }

    /** If the exposure panel is open, updates the selected ISO button to match the current ISO value,
     * if a continuous range of ISO values are supported by the camera.
     */
    fun updateSelectedISOButton() {
        val preview = mainActivity.preview
        if (preview!!.supportsISORange() && this.isExposureUIOpen) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
            val currentIso: String = sharedPreferences.getString(
                PreferenceKeys.ISOPreferenceKey,
                CameraController.ISO_DEFAULT
            )!!
            // if the manual ISO value isn't one of the "preset" values, then instead highlight the manual ISO icon
            var found = false
            for (view in isoButtons!!) {
                val button = view as Button
                val buttonText = button.text.toString()
                if (ISOTextEquals(buttonText, currentIso)) {
                    PopupView.setButtonSelected(button, true)
                    found = true
                } else {
                    PopupView.setButtonSelected(button, false)
                }
            }
            if (!found && currentIso != CameraController.ISO_DEFAULT) {
                if (isoButtonManualIndex >= 0 && isoButtonManualIndex < isoButtons!!.size) {
                    val button = isoButtons!![isoButtonManualIndex] as Button
                    PopupView.setButtonSelected(button, true)
                }
            }
        }
    }

    fun setSeekbarZoom(newZoom: Int) {
        mainActivity.binding.zoomSeekbar.progress = mainActivity.preview!!.maxZoom - newZoom
    }

    fun changeSeekbar(seekBarId: Int, change: Int) {
        val seekBar = mainActivity.findViewById<SeekBar>(seekBarId)
        val value = seekBar.progress
        var newValue = value + change
        if (newValue < 0) newValue = 0
        else if (newValue > seekBar.max) newValue = seekBar.max
        if (newValue != value) {
            seekBar.progress = newValue
        }
    }

    fun closeExposureUI() {

    }

    fun closePopup() {
        if (MyDebug.LOG) Log.d(TAG, "close popup")

        mainActivity.enablePopupOnBackPressedCallback(false)

        if (popupIsOpen()) {
            clearRemoteControlForPopup() // must be called before we set popup_view_is_open to false; and before clearSelectionState() so we know which highlighting to disable
            clearSelectionState()

            popup_view_is_open = false
            if (cache_popup && !force_destroy_popup) {
                popupView!!.visibility = View.GONE
            }
            mainActivity.initImmersiveMode() // to reset the timer when closing the popup
        }
    }

    fun popupIsOpen(): Boolean {
        return popup_view_is_open
    }

    fun selectingIcons(): Boolean {
        return mSelectingIcons
    }

    fun selectingLines(): Boolean {
        return mSelectingLines
    }

    fun destroyPopup() {
        if (MyDebug.LOG) Log.d(TAG, "destroyPopup")
        force_destroy_popup = false
        if (popupIsOpen()) {
            closePopup()
        }
        val popupContainer = mainActivity.findViewById<ViewGroup>(R.id.popup_container)
        popupContainer.removeAllViews()
        this.popupView = null
    }

    /**
     * Higlights the next LinearLayout view
     */
    private fun highlightPopupLine(highlight: Boolean, goUp: Boolean) {
        if (MyDebug.LOG) {
            Log.d(TAG, "highlightPopupLine")
            Log.d(TAG, "highlight: $highlight")
            Log.d(TAG, "goUp: $goUp")
        }
        if (!popupIsOpen()) { // Safety check
            clearSelectionState()
            return
        }
        val popup_container = mainActivity.findViewById<ViewGroup>(R.id.popup_container)
        val scrollBounds = Rect()
        popup_container.getDrawingRect(scrollBounds)
        val inside = popup_container.getChildAt(0) as LinearLayout?
        if (inside == null) return  // Safety check

        val count = inside.childCount
        var foundLine = false
        while (!foundLine) {
            // Ensure we stay within our bounds:
            mPopupLine = (mPopupLine + count) % count
            var v = inside.getChildAt(mPopupLine)
            if (MyDebug.LOG) Log.d(TAG, "line: $mPopupLine view: $v")
            // to test example with HorizontalScrollView, see popup menu on Nokia 8 with Camera2 API, the flash icons row uses a HorizontalScrollView
            if (v is HorizontalScrollView && v.childCount > 0) v = v.getChildAt(0)
            if (v.isShown && v is LinearLayout) {
                if (highlight) {
                    v.setBackgroundColor(highlightColor)
                    //v.setAlpha(0.3f);
                    if (v.bottom > scrollBounds.bottom || v.top < scrollBounds.top) popup_container.scrollTo(
                        0,
                        v.top
                    )
                    mHighlightedLine = v
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT)
                    v.alpha = 1f
                }
                foundLine = true
                if (MyDebug.LOG) Log.d(TAG, "found at line: $foundLine")
            } else {
                mPopupLine += if (goUp) -1 else 1
            }
        }
        if (MyDebug.LOG) Log.d(TAG, "Current line: $mPopupLine")
    }

    /**
     * Highlights an icon on a horizontal line, such as flash mode,
     * focus mode, etc. Checks that the popup is open in case it is
     * wrongly called, so that it doesn't crash the app.
     */
    private fun highlightPopupIcon(highlight: Boolean, goLeft: Boolean) {
        if (MyDebug.LOG) {
            Log.d(TAG, "highlightPopupIcon")
            Log.d(TAG, "highlight: $highlight")
            Log.d(TAG, "goLeft: $goLeft")
        }
        if (!popupIsOpen()) { // Safety check
            clearSelectionState()
            return
        }
        highlightPopupLine(false, false)
        val count = mHighlightedLine!!.childCount
        var foundIcon = false
        while (!foundIcon) {
            // Ensure we stay within our bounds:
            // (careful, modulo in Java will allow negative numbers, hence the line below:
            mPopupIcon = (mPopupIcon + count) % count
            val v = mHighlightedLine!!.getChildAt(mPopupIcon)
            if (MyDebug.LOG) Log.d(TAG, "row: $mPopupIcon view: $v")
            if (v is ImageButton || v is Button) {
                if (highlight) {
                    v.setBackgroundColor(highlightColor)
                    //v.setAlpha(0.5f);
                    mHighlightedIcon = v
                    mSelectingIcons = true
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT)
                }
                if (MyDebug.LOG) Log.d(TAG, "found icon at row: $mPopupIcon")
                foundIcon = true
            } else {
                mPopupIcon += if (goLeft) -1 else 1
            }
        }
    }

    /**
     * Select the next line on the settings popup. Called by MainActivity
     * when receiving a remote control command.
     */
    private fun nextPopupLine() {
        highlightPopupLine(false, false)
        mPopupLine++
        highlightPopupLine(true, false)
    }

    private fun previousPopupLine() {
        highlightPopupLine(false, true)
        mPopupLine--
        highlightPopupLine(true, true)
    }

    private fun nextPopupIcon() {
        highlightPopupIcon(false, false)
        mPopupIcon++
        highlightPopupIcon(true, false)
    }

    private fun previousPopupIcon() {
        highlightPopupIcon(false, true)
        mPopupIcon--
        highlightPopupIcon(true, true)
    }

    /**
     * Simulates a press on the currently selected icon
     */
    private fun clickSelectedIcon() {
        if (MyDebug.LOG) Log.d(TAG, "clickSelectedIcon: $mHighlightedIcon")
        if (mHighlightedIcon != null) {
            mHighlightedIcon!!.callOnClick()
        }
    }

    /**
     * Ensure all our selection tracking variables are cleared when we
     * exit menu selection (used in remote control mode)
     */
    private fun clearSelectionState() {
        if (MyDebug.LOG) Log.d(TAG, "clearSelectionState")
        mPopupLine = 0
        mPopupIcon = 0
        mSelectingIcons = false
        mSelectingLines = false
        mHighlightedIcon = null
        mHighlightedLine = null
    }

    /**
     * Opens or closes the settings popup on the camera preview. The popup that
     * differs depending whether we're in photo or video mode
     */
    fun togglePopupSettings() {
        val popup_container = mainActivity.findViewById<ViewGroup>(R.id.popup_container)
        if (popupIsOpen()) {
            closePopup()
            return
        }
        if (mainActivity.preview!!.cameraController == null) {
            if (MyDebug.LOG) Log.d(TAG, "camera not opened!")
            return
        }

        if (MyDebug.LOG) Log.d(TAG, "open popup")

        mainActivity.enablePopupOnBackPressedCallback(true) // so that back button will close the popup instead of exiting the application

        mainActivity.preview!!.cancelTimer() // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
        mainActivity.stopAudioListeners()

        val time_s = System.currentTimeMillis()

        run {
            // prevent popup being transparent
            popup_container.setBackgroundColor(Color.BLACK)
            popup_container.alpha = 0.9f
        }

        if (this.popupView == null) {
            if (MyDebug.LOG) Log.d(TAG, "create new popup_view")
            testUIButtonsMap.clear()
            this.popupView = PopupView(mainActivity)
            popup_container.addView(this.popupView)
        } else {
            if (MyDebug.LOG) Log.d(TAG, "use cached popup_view")
            popupView!!.visibility = View.VISIBLE
        }
        popup_view_is_open = true

        if (mainActivity.getBluetoothRemoteControl().remoteEnabled()) {
            initRemoteControlForPopup()
        }

        // need to call layoutUI to make sure the new popup is oriented correctly
        // but need to do after the layout has been done, so we have a valid width/height to use
        // n.b., even though we only need the portion of layoutUI for the popup container, there
        // doesn't seem to be any performance benefit in only calling that part
        popup_container.viewTreeObserver.addOnGlobalLayoutListener(
            object : OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (MyDebug.LOG) Log.d(TAG, "onGlobalLayout()")
                    if (MyDebug.LOG) Log.d(
                        TAG,
                        "time after global layout: " + (System.currentTimeMillis() - time_s)
                    )
                    //layoutUI(true)
                    if (MyDebug.LOG) Log.d(
                        TAG,
                        "time after layoutUI: " + (System.currentTimeMillis() - time_s)
                    )
                    // stop listening - only want to call this once!
                    popup_container.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    val ui_placement = computeUIPlacement()
                    val system_orientation = mainActivity.systemOrientation
                    val pivot_x: Float
                    val pivot_y: Float
                    when (ui_placement) {
                        UIPlacement.UIPLACEMENT_TOP -> if (mainActivity.preview!!.uiRotation == 270) {
                            // portrait (when not locked)
                            pivot_x = 0.0f
                            pivot_y = 1.0f
                        } else if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) {
                            pivot_x = 1.0f
                            pivot_y = 1.0f
                        } else {
                            pivot_x = 0.0f
                            pivot_y = 0.0f
                        }

                        UIPlacement.UIPLACEMENT_LEFT -> if (system_orientation == SystemOrientation.PORTRAIT) {
                            pivot_x = 0.0f
                            pivot_y = 1.0f
                        } else if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) {
                            pivot_x = 0.0f
                            pivot_y = 0.0f
                        } else {
                            pivot_x = 1.0f
                            pivot_y = 1.0f
                        }

                        else -> if (system_orientation == SystemOrientation.PORTRAIT) {
                            pivot_x = 1.0f
                            pivot_y = 1.0f
                        } else if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) {
                            pivot_x = 0.0f
                            pivot_y = 1.0f
                        } else {
                            pivot_x = 1.0f
                            pivot_y = 0.0f
                        }
                    }
                    val animation = ScaleAnimation(
                        0.0f,
                        1.0f,
                        0.0f,
                        1.0f,
                        Animation.RELATIVE_TO_SELF,
                        pivot_x,
                        Animation.RELATIVE_TO_SELF,
                        pivot_y
                    )
                    animation.duration = 200
                    //popup_container.setAnimation(animation);
                    val fadeAnimation = AlphaAnimation(0.0f, 1.0f)
                    fadeAnimation.duration = 200
                    val animationSet = AnimationSet(false)
                    animationSet.addAnimation(animation)
                    animationSet.addAnimation(fadeAnimation)
                    popup_container.animation = animationSet
                }
            }
        )

        if (MyDebug.LOG) Log.d(
            TAG,
            "time to create popup: " + (System.currentTimeMillis() - time_s)
        )
    }

    private fun initRemoteControlForPopup() {
        if (MyDebug.LOG) Log.d(TAG, "initRemoteControlForPopup")
        if (popupIsOpen()) { // just in case
            // For remote control, we want to highlight lines and icons on the popup view
            // so that we can control those just with the up/down buttons and "OK"
            clearSelectionState()
            remote_control_mode = true
            mSelectingLines = true
            highlightPopupLine(true, false)
        }
    }

    private fun clearRemoteControlForPopup() {
        if (MyDebug.LOG) Log.d(TAG, "clearRemoteControlForPopup")
        if (popupIsOpen() && remote_control_mode) {
            remote_control_mode = false

            // reset highlighting
            val popup_container = mainActivity.findViewById<ViewGroup>(R.id.popup_container)
            val scrollBounds = Rect()
            popup_container.getDrawingRect(scrollBounds)
            val inside = popup_container.getChildAt(0) as LinearLayout?
            if (inside == null) return  // Safety check

            var v = inside.getChildAt(mPopupLine)
            if (v.isShown && v is LinearLayout) {
                if (MyDebug.LOG) Log.d(TAG, "reset " + mPopupLine + "th view: " + v)
                v.setBackgroundColor(Color.TRANSPARENT)
                v.alpha = 1f
            }
            if (mHighlightedLine != null) {
                v = mHighlightedLine!!.getChildAt(mPopupIcon)
                if (v is ImageButton || v is Button) {
                    v.setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (MyDebug.LOG) Log.d(TAG, "onKeyDown: $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keydown_volume_up = true
                else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keydown_volume_down = true

                val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
                val volumeKeys: String = sharedPreferences.getString(
                    PreferenceKeys.VolumeKeysPreferenceKey,
                    "volume_take_photo"
                )!!

                when (volumeKeys) {
                    "volume_take_photo" -> {
                        var done = false
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mainActivity.preview!!.isVideoRecording()) {
                            done = true
                            mainActivity.pauseVideo()
                        }
                        if (!done) {
                            mainActivity.takePicture(false)
                        }
                        return true
                    }

                    "volume_focus" -> {
                        if (keydown_volume_up && keydown_volume_down) {
                            if (MyDebug.LOG) Log.d(
                                TAG,
                                "take photo rather than focus, as both volume keys are down"
                            )
                            mainActivity.takePicture(false)
                        } else if (mainActivity.preview!!.getCurrentFocusValue() != null && mainActivity.preview!!.getCurrentFocusValue() == "focus_mode_manual2") {
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) mainActivity.changeFocusDistance(
                                -1,
                                false
                            )
                            else mainActivity.changeFocusDistance(1, false)
                        } else {
                            // important not to repeatedly request focus, even though main_activity.getPreview().requestAutoFocus() will cancel, as causes problem if key is held down (e.g., flash gets stuck on)
                            // also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down
                            if (event.downTime == event.eventTime && !mainActivity.preview!!.isFocusWaiting) {
                                if (MyDebug.LOG) Log.d(TAG, "request focus due to volume key")
                                mainActivity.preview!!.requestAutoFocus()
                            }
                        }
                        return true
                    }

                    "volume_zoom" -> {
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) mainActivity.zoomIn()
                        else mainActivity.zoomOut()
                        return true
                    }

                    "volume_exposure" -> {
                        if (mainActivity.preview!!.cameraController != null) {
                            val value: String = sharedPreferences.getString(
                                PreferenceKeys.ISOPreferenceKey,
                                CameraController.ISO_DEFAULT
                            )!!
                            val manualIso = value != CameraController.ISO_DEFAULT
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                                if (manualIso) {
                                    mainActivity.changeISO(1)
                                } else mainActivity.changeExposure(1)
                            } else {
                                if (manualIso) {
                                    mainActivity.changeISO(-1)
                                } else mainActivity.changeExposure(-1)
                            }
                        }
                        return true
                    }

                    "volume_auto_stabilise" -> {
                        if (mainActivity.supportsAutoStabilise()) {
                            var autoStabilise = sharedPreferences.getBoolean(
                                PreferenceKeys.AutoStabilisePreferenceKey,
                                false
                            )
                            autoStabilise = !autoStabilise
                            val editor = sharedPreferences.edit()
                            editor.putBoolean(
                                PreferenceKeys.AutoStabilisePreferenceKey,
                                autoStabilise
                            )
                            editor.apply()
                            val message = mainActivity.getResources()
                                .getString(R.string.preference_auto_stabilise) + ": " + mainActivity.getResources()
                                .getString(if (autoStabilise) R.string.on else R.string.off)
                            mainActivity.preview!!.showToast(
                                mainActivity.changedAutoStabiliseToastBoxer,
                                message,
                                true
                            )
                            mainActivity.applicationInterface!!.drawPreview.updateSettings() // because we cache the auto-stabilise setting
                        } else if (!mainActivity.deviceSupportsAutoStabilise()) {
                            // n.b., need to check deviceSupportsAutoStabilise() - if we're in e.g. Panorama mode, we shouldn't display a toast (as then supportsAutoStabilise() returns false even if auto-level is supported on the device)
                            mainActivity.preview!!.showToast(
                                mainActivity.changedAutoStabiliseToastBoxer,
                                R.string.auto_stabilise_not_supported
                            )
                        }
                        return true
                    }

                    "volume_really_nothing" ->                         // do nothing, but still return true so we don't change volume either
                        return true
                }
            }

            KeyEvent.KEYCODE_MENU -> {
                // needed to support hardware menu button
                // tested successfully on Samsung S3 (via RTL)
                // see http://stackoverflow.com/questions/8264611/how-to-detect-when-user-presses-menu-key-on-their-android-device
                mainActivity.openSettings()
                return true
            }

            KeyEvent.KEYCODE_CAMERA -> {
                run {
                    if (event.repeatCount == 0) {
                        mainActivity.takePicture(false)
                        return true
                    }
                }
                run {
                    // important not to repeatedly request focus, even though main_activity.getPreview().requestAutoFocus() will cancel - causes problem with hardware camera key where a half-press means to focus
                    // also check DownTime vs EventTime to prevent repeated focusing whilst the key is held down - see https://sourceforge.net/p/opencamera/tickets/174/ ,
                    // or same issue above for volume key focus
                    if (event.downTime == event.eventTime && !mainActivity.preview!!.isFocusWaiting) {
                        if (MyDebug.LOG) Log.d(TAG, "request focus due to focus key")
                        mainActivity.preview!!.requestAutoFocus()
                    }
                    return true
                }
            }

            KeyEvent.KEYCODE_FOCUS -> {
                if (event.downTime == event.eventTime && !mainActivity.preview!!.isFocusWaiting) {
                    if (MyDebug.LOG) Log.d(TAG, "request focus due to focus key")
                    mainActivity.preview!!.requestAutoFocus()
                }
                return true
            }

            KeyEvent.KEYCODE_ZOOM_IN, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> {
                mainActivity.zoomIn()
                return true
            }

            KeyEvent.KEYCODE_ZOOM_OUT, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                mainActivity.zoomOut()
                return true
            }

            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_NUMPAD_5 -> {
                if (this.isExposureUIOpen && remote_control_mode) {
                    commandMenuExposure()
                    return true
                } else if (popupIsOpen() && remote_control_mode) {
                    commandMenuPopup()
                    return true
                } else if (event.getRepeatCount() == 0) {
                    mainActivity.takePicture(false)
                    return true
                }
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_NUMPAD_8 ->                 //case KeyEvent.KEYCODE_VOLUME_UP: // test
                if (!remote_control_mode) {
                    if (popupIsOpen()) {
                        initRemoteControlForPopup()
                        return true
                    } else if (this.isExposureUIOpen) {
                        initRemoteControlForExposureUI()
                        return true
                    }
                } else if (processRemoteUpButton()) return true

            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_NUMPAD_2 ->                 //case KeyEvent.KEYCODE_VOLUME_DOWN: // test
                if (!remote_control_mode) {
                    if (popupIsOpen()) {
                        initRemoteControlForPopup()
                        return true
                    } else if (this.isExposureUIOpen) {
                        initRemoteControlForExposureUI()
                        return true
                    }
                } else if (processRemoteDownButton()) return true

            KeyEvent.KEYCODE_FUNCTION, KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> togglePopupSettings()
            KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_NUMPAD_DIVIDE -> toggleExposureUI()
        }
        return false
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?) {
        if (MyDebug.LOG) Log.d(TAG, "onKeyUp: $keyCode")
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keydown_volume_up = false
        else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keydown_volume_down = false
    }

    /** If the exposure menu is open, selects a current line or option. Else does nothing.
     */
    fun commandMenuExposure() {
        if (MyDebug.LOG) Log.d(TAG, "commandMenuExposure")
        if (this.isExposureUIOpen) {
            if (this.isSelectingExposureUIElement) {
                // Close Exposure UI if new press on MENU
                // while already selecting
                toggleExposureUI()
            } else {
                // Select current element in Exposure UI
                selectExposureUILine()
            }
        }
    }

    /** If the popup menu is open, selects a current line or option. Else does nothing.
     */
    fun commandMenuPopup() {
        if (MyDebug.LOG) Log.d(TAG, "commandMenuPopup")
        if (popupIsOpen()) {
            if (selectingIcons()) {
                clickSelectedIcon()
            } else {
                highlightPopupIcon(true, false)
            }
        }
    }

    /** Shows an information dialog, with a button to request not to show again.
     * Note it's up to the caller to check whether the info_preference_key (to not show again) was
     * already set.
     * @param title_id Resource id for title string.
     * @param info_id Resource id for dialog text string.
     * @param info_preference_key Preference key to set in SharedPreferences if the user selects to
     * not show the dialog again.
     * @return The AlertDialog that was created.
     */
    fun showInfoDialog(title_id: Int, info_id: Int, info_preference_key: String?): AlertDialog {
        val alertDialog = AlertDialog.Builder(mainActivity)
        alertDialog.setTitle(title_id)
        if (info_id != 0) alertDialog.setMessage(info_id)
        alertDialog.setPositiveButton(android.R.string.ok, null)
        alertDialog.setNegativeButton(
            R.string.dont_show_again
        ) { dialog, which ->
            if (MyDebug.LOG) Log.d(TAG, "user clicked dont_show_again for info dialog")
            val sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(mainActivity)
            val editor = sharedPreferences.edit()
            editor.putBoolean(info_preference_key, true)
            editor.apply()
        }

        //main_activity.showPreview(false);
        //main_activity.setWindowFlagsForSettings(false); // set set_lock_protect to false, otherwise if screen is locked, user will need to unlock to see the info dialog!
        val alert = alertDialog.create()
        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
        alert.setOnDismissListener {
            if (MyDebug.LOG) Log.d(TAG, "info dialog dismissed")
            //main_activity.setWindowFlagsForCamera();
            //main_activity.showPreview(true);
        }
        //main_activity.showAlert(alert);
        alert.show()
        return alert
    }


    fun getIconForFocus(value: String?): Int {
        var value = value
        if (value == null) value = ""
        return when (value) {
            "focus_mode_macro" -> R.drawable.ic_macro_focus
            "focus_mode_locked" -> R.drawable.baseline_lock_24
            "focus_mode_infinity" -> R.drawable.focus_mode_infinity
            "focus_mode_manual2" -> R.drawable.ic_manual
            "focus_mode_edof" -> R.drawable.focus_mode_edof
            "focus_mode_continuous_picture" -> R.drawable.ic_rotate
            else -> R.drawable.ic_auto_focus
        }
    }

    fun getEntryForFocus(value: String?): String {
        var value = value
        if (value == null) value = ""
        val id = when (value) {
            "focus_mode_macro" -> R.string.focus_mode_macro
            "focus_mode_locked" -> R.string.focus_mode_locked
            "focus_mode_infinity" -> R.string.focus_mode_infinity
            "focus_mode_manual2" -> R.string.focus_mode_manual2
            "focus_mode_edof" -> R.string.focus_mode_edof
            "focus_mode_continuous_picture" -> R.string.focus_mode_continuous_picture
            else -> R.string.focus_mode_auto
        }
        return mainActivity.getResources().getString(id)
    }

    /** Returns a (possibly translated) user readable string for a white balance preference value.
     * If the value is not recognised (this can happen for the old Camera API, some devices can
     * have device-specific options), then the received value is returned.
     */
    fun getEntryForWhiteBalance(value: String): String {
        var id = -1
        when (value) {
            CameraController.WHITE_BALANCE_DEFAULT -> id = R.string.white_balance_auto
            "cloudy-daylight" -> id = R.string.white_balance_cloudy
            "daylight" -> id = R.string.white_balance_daylight
            "fluorescent" -> id = R.string.white_balance_fluorescent
            "incandescent" -> id = R.string.white_balance_incandescent
            "shade" -> id = R.string.white_balance_shade
            "twilight" -> id = R.string.white_balance_twilight
            "warm-fluorescent" -> id = R.string.white_balance_warm
            "manual" -> id = R.string.white_balance_manual
            else -> {}
        }
        val entry: String
        if (id != -1) {
            entry = mainActivity.getResources().getString(id)
        } else {
            entry = value
        }
        return entry
    }

    fun getIconForWhiteBalance(value: String?): Int {
        var value = value
        if (value == null) value = ""
        return when (value) {
            "cloudy-daylight" -> R.drawable.white_balance_cloudy
            "daylight" -> R.drawable.baseline_wb_sunny_24
            "fluorescent" -> R.drawable.baseline_fluorescent_24
            "incandescent" -> R.drawable.baseline_wb_incandescent_24
            "shade" -> R.drawable.baseline_wb_shade_24
            "twilight" -> R.drawable.baseline_wb_twilight_24
            "warm-fluorescent" -> R.drawable.white_balance_warm
            "manual" -> R.drawable.custom_options
            "lock" -> R.drawable.baseline_lock_24
            else -> R.drawable.outline_wb_auto_24
        }
    }

    /** Returns a (possibly translated) user readable string for a scene mode preference value.
     * If the value is not recognised (this can happen for the old Camera API, some devices can
     * have device-specific options), then the received value is returned.
     */
    fun getEntryForSceneMode(value: String): String {
        var id = -1
        when (value) {
            "action" -> id = R.string.scene_mode_action
            "barcode" -> id = R.string.scene_mode_barcode
            "beach" -> id = R.string.scene_mode_beach
            "candlelight" -> id = R.string.scene_mode_candlelight
            CameraController.SCENE_MODE_DEFAULT -> id = R.string.scene_mode_auto
            "fireworks" -> id = R.string.scene_mode_fireworks
            "landscape" -> id = R.string.scene_mode_landscape
            "night" -> id = R.string.scene_mode_night
            "night-portrait" -> id = R.string.scene_mode_night_portrait
            "party" -> id = R.string.scene_mode_party
            "portrait" -> id = R.string.scene_mode_portrait
            "snow" -> id = R.string.scene_mode_snow
            "sports" -> id = R.string.scene_mode_sports
            "steadyphoto" -> id = R.string.scene_mode_steady_photo
            "sunset" -> id = R.string.scene_mode_sunset
            "theatre" -> id = R.string.scene_mode_theatre
            else -> {}
        }
        val entry: String
        if (id != -1) {
            entry = mainActivity.getResources().getString(id)
        } else {
            entry = value
        }
        return entry
    }

    fun getIconForSceneMode(value: String?): Int {
        var value = value
        if (value == null) value = ""
        return when (value) {
            "action" -> R.drawable.outline_directions_run_24
            "barcode" -> R.drawable.outline_barcode_24
            "beach" -> R.drawable.outline_beach_access_24
            "candlelight" -> R.drawable.scene_mode_candlelight
            "fireworks" -> R.drawable.scene_mode_fireworks
            "landscape" -> R.drawable.baseline_landscape_24
            "night" -> R.drawable.baseline_mode_night_24
            "night-portrait" -> R.drawable.scene_mode_night_portrait
            "party" -> R.drawable.baseline_party_mode_24
            "portrait" -> R.drawable.baseline_portrait_24
            "snow" -> R.drawable.outline_snowing_24
            "sports" -> R.drawable.outline_sports_handball_24
            "steadyphoto" -> R.drawable.ic_outline_photo
            "sunset" -> R.drawable.rounded_sunny_snowing_24
            "theatre" -> R.drawable.outline_theaters_24
            else -> R.drawable.ic_auto
        }
    }

    /** Returns a (possibly translated) user readable string for a color effect preference value.
     * If the value is not recognised (this can happen for the old Camera API, some devices can
     * have device-specific options), then the received value is returned.
     */
    fun getEntryForColorEffect(value: String): String {
        var id = -1
        when (value) {
            "aqua" -> id = R.string.color_effect_aqua
            "blackboard" -> id = R.string.color_effect_blackboard
            "mono" -> id = R.string.color_effect_mono
            "negative" -> id = R.string.color_effect_negative
            CameraController.COLOR_EFFECT_DEFAULT -> id = R.string.color_effect_none
            "posterize" -> id = R.string.color_effect_posterize
            "sepia" -> id = R.string.color_effect_sepia
            "solarize" -> id = R.string.color_effect_solarize
            "whiteboard" -> id = R.string.color_effect_whiteboard
            else -> {}
        }
        val entry: String
        if (id != -1) {
            entry = mainActivity.getResources().getString(id)
        } else {
            entry = value
        }
        return entry
    }

    fun getIconForColorEffect(value: String?): Int {
        var value = value
        if (value == null) value = ""
        return when (value) {
            "aqua" -> R.drawable.color_effect_aqua
            "blackboard" -> R.drawable.color_effect_blackboard
            "mono" -> R.drawable.color_effect_mono
            "negative" -> R.drawable.color_effect_negative
            "posterize" -> R.drawable.color_effect_posterize
            "sepia" -> R.drawable.color_effect_sepia
            "solarize" -> R.drawable.color_effect_solarize
            "whiteboard" -> R.drawable.color_effect_whiteboard
            else -> R.drawable.ic_auto
        }
    }

    /** Returns a (possibly translated) user readable string for an antibanding preference value.
     * If the value is not recognised, then the received value is returned.
     */
    fun getEntryForAntiBanding(value: String): String {
        var id = -1
        when (value) {
            CameraController.ANTIBANDING_DEFAULT -> id = R.string.anti_banding_auto
            "50hz" -> id = R.string.anti_banding_50hz
            "60hz" -> id = R.string.anti_banding_60hz
            "off" -> id = R.string.anti_banding_off
            else -> {}
        }
        val entry: String
        if (id != -1) {
            entry = mainActivity.getResources().getString(id)
        } else {
            entry = value
        }
        return entry
    }

    /** Returns a (possibly translated) user readable string for an noise reduction mode preference value.
     * If the value is not recognised, then the received value is returned.
     * Also used for edge mode.
     */
    fun getEntryForNoiseReductionMode(value: String): String {
        var id = -1
        when (value) {
            CameraController.NOISE_REDUCTION_MODE_DEFAULT -> id =
                R.string.noise_reduction_mode_default

            "off" -> id = R.string.noise_reduction_mode_off
            "minimal" -> id = R.string.noise_reduction_mode_minimal
            "fast" -> id = R.string.noise_reduction_mode_fast
            "high_quality" -> id = R.string.noise_reduction_mode_high_quality
            else -> {}
        }
        val entry: String
        if (id != -1) {
            entry = mainActivity.getResources().getString(id)
        } else {
            entry = value
        }
        return entry
    }

    // for testing
    fun getUIButton(key: String?): View? {
        if (MyDebug.LOG) {
            Log.d(TAG, "getPopupButton(" + key + "): " + testUIButtonsMap[key])
            Log.d(TAG, "this: $this")
            Log.d(TAG, "popup_buttons: " + this.testUIButtonsMap)
        }
        return testUIButtonsMap[key]
    }

    fun testGetRemoteControlMode(): Boolean {
        return remote_control_mode
    }

    fun testGetPopupLine(): Int {
        return mPopupLine
    }

    fun testGetPopupIcon(): Int {
        return mPopupIcon
    }

    fun testGetExposureLine(): Int {
        return mExposureLine
    }

    companion object {
        private const val TAG = "MainUI"

        private const val cache_popup = true // if false, we recreate the popup each time
        private const val view_rotate_animation_duration =
            100 // duration in ms of the icon rotation animation
        const val privacy_indicator_gap_dp: Int = 24

        private const val manual_iso_value = "m"

        /** Returns whether the ISO button with the supplied text is a match for the supplied iso.
         * Should only be used for Preview.supportsISORange()==true (i.e., full manual ISO).
         */
        fun ISOTextEquals(button_text: String, iso: String): Boolean {
            // Can't use equals(), due to the \n that Popupview.getButtonOptionString() inserts, and
            // also good to make this general in case in future we support other text formats.
            // We really want to check that iso is the last word in button_text.
            if (button_text.endsWith(iso)) {
                return button_text.length == iso.length || Character.isWhitespace(
                    button_text[button_text.length - iso.length - 1]
                )
            }
            return false
        }

        /** Returns the ISO button text for the supplied iso.
         * Should only be used for Preview.supportsISORange()==true (i.e., full manual ISO).
         */
        fun ISOToButtonText(iso: Int): String {
            // n.b., if we change how the ISO is converted to a string for the button, will also need
            // to update updateSelectedISOButton()
            return iso.toString()
        }
    }
}
