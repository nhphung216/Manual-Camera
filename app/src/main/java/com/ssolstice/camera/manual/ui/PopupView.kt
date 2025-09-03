package com.ssolstice.camera.manual.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.camera2.CameraExtensionCharacteristics
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.ssolstice.camera.manual.MainActivity
import com.ssolstice.camera.manual.MyApplicationInterface.PhotoMode
import com.ssolstice.camera.manual.MyDebug
import com.ssolstice.camera.manual.PreferenceKeys
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.cameracontroller.CameraController
import com.ssolstice.camera.manual.preview.Preview
import java.text.DecimalFormat
import java.util.Arrays
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.core.view.size
import androidx.core.view.isNotEmpty
import com.ssolstice.camera.manual.utils.Logger

/** This defines the UI for the "popup" button, that provides quick access to a
 * range of options.
 */
class PopupView(context: Context?) : LinearLayout(context) {
    private val arrow_button_w: Int
    private val arrow_button_h: Int

    private var total_width_dp: Int

    private var picture_size_index = -1
    private var nr_mode_index = -1
    private var burst_n_images_index = -1
    private var video_size_index = -1
    private var video_capture_rate_index = -1
    private var timer_index = -1
    private var repeat_mode_index = -1
    private var grid_index = -1

    private val decimal_format_1dp_force0 = DecimalFormat("0.0")

    init {
        Logger.d(TAG, "new PopupView: $this")

        val debug_time = System.nanoTime()
        Logger.d(TAG, "PopupView time 1: " + (System.nanoTime() - debug_time))
        this.orientation = VERTICAL

        val scale = resources.displayMetrics.density

        arrow_button_w = (arrow_button_w_dp * scale + 0.5f).toInt() // convert dps to pixels
        arrow_button_h = (arrow_button_h_dp * scale + 0.5f).toInt() // convert dps to pixels

        val main_activity = this.context as MainActivity

        var small_screen = false
        total_width_dp = 280
        val max_width_dp = main_activity.mainUI!!.getMaxHeightDp(false)
        if (total_width_dp > max_width_dp) {
            total_width_dp = max_width_dp
            small_screen = true
        }
        if (MyDebug.LOG) {
            Logger.d(TAG, "max_width_dp: $max_width_dp")
            Logger.d(TAG, "total_width_dp: $total_width_dp")
            Logger.d(TAG, "small_screen: $small_screen")
        }

        val preview = main_activity.preview
        val isCameraExtension = main_activity.applicationInterface!!.isCameraExtensionPref()
        Logger.d(TAG, "PopupView time 2: " + (System.nanoTime() - debug_time))

        if (!main_activity.mainUI!!.showCycleFlashIcon()) {
            var supportedFlashValues = preview!!.supportedFlashValues
            if (preview.isVideo && supportedFlashValues != null) {
                // filter flash modes we don't want to show
                val filter: MutableList<String> = ArrayList()
                for (flashValue in supportedFlashValues) {
                    if (Preview.isFlashSupportedForVideo(flashValue)) filter.add(flashValue)
                }
                supportedFlashValues = filter
            }
            if (supportedFlashValues != null && supportedFlashValues.size > 1) { // no point showing flash options if only one available!
                addButtonOptionsToPopup(
                    supportedFlashValues,
                    R.array.flash_icons,
                    R.array.flash_values,
                    getResources().getString(R.string.flash_mode),
                    preview.getCurrentFlashValue(),
                    0,
                    "TEST_FLASH",
                    object : ButtonOptionsPopupListener() {
                        override fun onClick(option: String?) {
                            Logger.d(TAG, "clicked flash: $option")
                            preview.updateFlash(option)
                        }
                    })
            }
        }
        Logger.d(TAG, "PopupView time 3: " + (System.nanoTime() - debug_time))

        if (preview!!.isVideo && preview.isVideoRecording) {
            // don't add any more options
        } else {
            // make a copy of getSupportedFocusValues() so we can modify it
            var supported_focus_values = preview.supportedFocusValues
            val photo_mode = main_activity.applicationInterface!!.photoMode
            if (!preview.isVideo && photo_mode == PhotoMode.FocusBracketing) {
                // don't show focus modes in focus bracketing mode (as we'll always run in manual focus mode)
                supported_focus_values = null
            }
            if (supported_focus_values != null) {
                supported_focus_values = ArrayList<String>(supported_focus_values)
                // only show appropriate continuous focus mode
                if (preview.isVideo) {
                    supported_focus_values.remove("focus_mode_continuous_picture")
                } else {
                    supported_focus_values.remove("focus_mode_continuous_video")
                }
            }
            addButtonOptionsToPopup(
                supported_focus_values,
                R.array.focus_mode_icons,
                R.array.focus_mode_values,
                getResources().getString(R.string.focus_mode),
                preview.getCurrentFocusValue(),
                0,
                "TEST_FOCUS",
                object : ButtonOptionsPopupListener() {
                    override fun onClick(option: String?) {
                        Logger.d(TAG, "clicked focus: $option")
                        preview.updateFocus(option, false, true)
                    }
                })
            Logger.d(TAG, "PopupView time 4: " + (System.nanoTime() - debug_time))

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity)

            //final boolean use_expanded_menu = true;
            val use_expanded_menu = false
            val photo_modes: MutableList<String> = ArrayList<String>()
            val photo_mode_values: MutableList<PhotoMode> = ArrayList<PhotoMode>()
            photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_standard_full else R.string.photo_mode_standard))
            photo_mode_values.add(PhotoMode.Standard)
            if (main_activity.supportsNoiseReduction()) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_noise_reduction_full else R.string.photo_mode_noise_reduction))
                photo_mode_values.add(PhotoMode.NoiseReduction)
            }
            if (main_activity.supportsDRO()) {
                photo_modes.add(getResources().getString(R.string.photo_mode_dro))
                photo_mode_values.add(PhotoMode.DRO)
            }
            if (main_activity.supportsHDR()) {
                photo_modes.add(getResources().getString(R.string.photo_mode_hdr))
                photo_mode_values.add(PhotoMode.HDR)
            }
            if (main_activity.supportsPanorama()) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_panorama_full else R.string.photo_mode_panorama))
                photo_mode_values.add(PhotoMode.Panorama)
            }
            if (main_activity.supportsFastBurst()) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_fast_burst_full else R.string.photo_mode_fast_burst))
                photo_mode_values.add(PhotoMode.FastBurst)
            }
            if (main_activity.supportsExpoBracketing()) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_expo_bracketing_full else R.string.photo_mode_expo_bracketing))
                photo_mode_values.add(PhotoMode.ExpoBracketing)
            }
            if (main_activity.supportsFocusBracketing()) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_focus_bracketing_full else R.string.photo_mode_focus_bracketing))
                photo_mode_values.add(PhotoMode.FocusBracketing)
            }
            if (main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_AUTOMATIC)) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_x_auto_full else R.string.photo_mode_x_auto))
                photo_mode_values.add(PhotoMode.X_Auto)
            }
            if (main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_HDR)) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_x_hdr_full else R.string.photo_mode_x_hdr))
                photo_mode_values.add(PhotoMode.X_HDR)
            }
            if (main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_NIGHT)) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_x_night_full else R.string.photo_mode_x_night))
                photo_mode_values.add(PhotoMode.X_Night)
            }
            if (main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BOKEH)) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_x_bokeh_full else R.string.photo_mode_x_bokeh))
                photo_mode_values.add(PhotoMode.X_Bokeh)
            }
            if (main_activity.supportsCameraExtension(CameraExtensionCharacteristics.EXTENSION_BEAUTY)) {
                photo_modes.add(getResources().getString(if (use_expanded_menu) R.string.photo_mode_x_beauty_full else R.string.photo_mode_x_beauty))
                photo_mode_values.add(PhotoMode.X_Beauty)
            }
            if (preview.isVideo) {
                // only show photo modes when in photo mode, not video mode!
                // (photo modes not supported for photo snapshot whilst recording video)
            } else if (photo_modes.size > 1) {
                var current_mode: String? = null
                var i = 0
                while (i < photo_modes.size && current_mode == null) {
                    if (photo_mode_values.get(i) == photo_mode) {
                        current_mode = photo_modes.get(i)
                    }
                    i++
                }
                if (current_mode == null) {
                    // applicationinterface should only report we're in a mode if it's supported, but just in case...
                    if (MyDebug.LOG) Log.e(TAG, "can't find current mode for mode: " + photo_mode)
                    current_mode = "" // this will mean no photo mode is highlighted in the UI
                }

                if (use_expanded_menu) {
                    addRadioOptionsToPopup(
                        sharedPreferences,
                        photo_modes,
                        photo_modes,
                        getResources().getString(R.string.photo_mode),
                        null,
                        null,
                        current_mode,
                        "TEST_PHOTO_MODE",
                        object : RadioOptionsListener() {
                            override fun onClick(selected_value: String?) {
                                Logger.d(TAG, "clicked photo mode: $selected_value")
                                selected_value?.let {
                                    changePhotoMode(
                                        photo_modes,
                                        photo_mode_values,
                                        selected_value
                                    )
                                }
                            }
                        })
                } else {
                    addTitleToPopup(getResources().getString(R.string.photo_mode))
                    Logger.d(
                        TAG,
                        "PopupView time 6: " + (System.nanoTime() - debug_time)
                    )

                    addButtonOptionsToPopup(
                        photo_modes,
                        -1,
                        -1,
                        "",
                        current_mode,
                        4,
                        "TEST_PHOTO_MODE",
                        object : ButtonOptionsPopupListener() {
                            override fun onClick(option: String?) {
                                Logger.d(TAG, "clicked photo mode: $option")
                                option?.let {
                                    changePhotoMode(
                                        photo_modes,
                                        photo_mode_values,
                                        option
                                    )
                                }
                            }
                        })
                }
            }
            Logger.d(TAG, "PopupView time 7: " + (System.nanoTime() - debug_time))

            if (!preview.isVideo && photo_mode == PhotoMode.NoiseReduction) {
                Logger.d(TAG, "add noise reduction options")

                val nr_mode_values =
                    getResources().getStringArray(R.array.preference_nr_mode_values)
                val nr_mode_entries =
                    getResources().getStringArray(R.array.preference_nr_mode_entries)

                if (nr_mode_values.size != nr_mode_entries.size) {
                    Log.e(
                        TAG,
                        "preference_nr_mode_values and preference_nr_mode_entries are different lengths"
                    )
                    throw RuntimeException()
                }

                //String nr_mode_value = sharedPreferences.getString(PreferenceKeys.NRModePreferenceKey, "preference_nr_mode_normal");
                val nr_mode_value = main_activity.applicationInterface!!.nRMode
                nr_mode_index = Arrays.asList<String?>(*nr_mode_values).indexOf(nr_mode_value)
                if (nr_mode_index == -1) {
                    Logger.d(
                        TAG,
                        "can't find nr_mode_value " + nr_mode_value + " in nr_mode_values!"
                    )
                    nr_mode_index = 0
                }
                addArrayOptionsToPopup(
                    Arrays.asList<String?>(*nr_mode_entries),
                    getResources().getString(R.string.preference_nr_mode),
                    true,
                    true,
                    nr_mode_index,
                    false,
                    "NR_MODE",
                    object : ArrayOptionsPopupListener() {
                        private fun update() {
                            if (nr_mode_index == -1) return
                            val new_nr_mode_value = nr_mode_values[nr_mode_index]
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            //editor.putString(PreferenceKeys.NRModePreferenceKey, new_nr_mode_value);
                            main_activity.applicationInterface!!.nRMode = new_nr_mode_value
                            editor.apply()
                            if (preview.cameraController != null) {
                                preview.setupBurstMode()
                            }
                        }

                        override fun onClickPrev(): Int {
                            if (nr_mode_index != -1 && nr_mode_index > 0) {
                                nr_mode_index--
                                update()
                                return nr_mode_index
                            }
                            return -1
                        }

                        override fun onClickNext(): Int {
                            if (nr_mode_index != -1 && nr_mode_index < nr_mode_values.size - 1) {
                                nr_mode_index++
                                update()
                                return nr_mode_index
                            }
                            return -1
                        }
                    })
            }

            if (main_activity.supportsAutoStabilise() && !main_activity.mainUI!!.showAutoLevelIcon()) {
                // don't show auto-stabilise checkbox on popup if there's an on-screen icon
                val checkBox = CheckBox(main_activity)
                checkBox.text = getResources().getString(R.string.preference_auto_stabilise)
                checkBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, standard_text_size_dip)
                checkBox.setTextColor(Color.WHITE)
                run {
                    // align the checkbox a bit better
                    val params = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )
                    val left_padding = (10 * scale + 0.5f).toInt() // convert dps to pixels
                    params.setMargins(left_padding, 0, 0, 0)
                    checkBox.layoutParams = params
                }

                val auto_stabilise =
                    sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false)
                if (auto_stabilise) checkBox.isChecked = auto_stabilise
                checkBox.setOnCheckedChangeListener { buttonView, isChecked -> main_activity.clickedAutoLevel() }

                this.addView(checkBox)
            }
            Logger.d(TAG, "PopupView time 8: " + (System.nanoTime() - debug_time))

            if (!preview.isVideo && photo_mode != PhotoMode.Panorama) {
                // Only show photo resolutions in photo mode - even if photo snapshots whilst recording video is supported, the
                // resolutions for that won't match what the user has requested for photo mode resolutions.
                // And Panorama mode chooses its own resolution.
                val picture_sizes: MutableList<CameraController.Size> =
                    ArrayList(preview.getSupportedPictureSizes(true))
                // take a copy so that we can reorder
                // picture_sizes is sorted high to low, but we want to order low to high
                picture_sizes.reverse()
                picture_size_index = -1
                val current_picture_size = preview.getCurrentPictureSize()
                val picture_size_strings: MutableList<String?> = ArrayList()
                for (i in picture_sizes.indices) {
                    val picture_size = picture_sizes[i]
                    val size_string =
                        picture_size.width.toString() + " x " + picture_size.height + " (" + Preview.getMPString(
                            picture_size.width,
                            picture_size.height
                        ) + ")"
                    picture_size_strings.add(size_string)
                    if (picture_size == current_picture_size) {
                        picture_size_index = i
                    }
                }
                if (picture_size_index == -1) {
                    Log.e(TAG, "couldn't find index of current picture size")
                } else {
                    Logger.d(TAG, "picture_size_index: $picture_size_index")
                }
                addArrayOptionsToPopup(
                    picture_size_strings,
                    getResources().getString(R.string.preference_resolution),
                    false,
                    false,
                    picture_size_index,
                    false,
                    "PHOTO_RESOLUTIONS",
                    object : ArrayOptionsPopupListener() {
                        val handler: Handler = Handler()
                        val update_runnable: Runnable = Runnable {
                            Logger.d(
                                TAG,
                                "update settings due to resolution change"
                            )
                            main_activity.updateForSettings(
                                true,
                                "",
                                true,
                                false
                            ) // keep the popupview open
                        }

                        private fun update() {
                            if (picture_size_index == -1) return
                            val new_size = picture_sizes.get(picture_size_index)
                            val resolution_string =
                                new_size.width.toString() + " " + new_size.height
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            editor.putString(
                                PreferenceKeys.getResolutionPreferenceKey(
                                    preview.getCameraId(),
                                    main_activity.applicationInterface!!.getCameraIdSPhysicalPref()
                                ), resolution_string
                            )
                            editor.apply()

                            // make it easier to scroll through the list of resolutions without a pause each time
                            // need a longer time for extension modes, due to the need to camera reopening (which will cause the
                            // popup menu to close)
                            val delay_time =
                                (if (main_activity.applicationInterface!!.isCameraExtensionPref()) 800 else 400).toLong()
                            handler.removeCallbacks(update_runnable)
                            handler.postDelayed(update_runnable, delay_time)
                        }

                        override fun onClickPrev(): Int {
                            if (picture_size_index != -1 && picture_size_index > 0) {
                                picture_size_index--
                                update()
                                return picture_size_index
                            }
                            return -1
                        }

                        override fun onClickNext(): Int {
                            if (picture_size_index != -1 && picture_size_index < picture_sizes.size - 1) {
                                picture_size_index++
                                update()
                                return picture_size_index
                            }
                            return -1
                        }
                    })
            }
            Logger.d(TAG, "PopupView time 9: " + (System.nanoTime() - debug_time))

            if (preview.isVideo()) {
                // only show video resolutions in video mode
                //final List<String> video_sizes = preview.getVideoQualityHander().getSupportedVideoQuality();
                //video_size_index = preview.getVideoQualityHander().getCurrentVideoQualityIndex();
                var video_sizes =
                    preview.getSupportedVideoQuality(main_activity.applicationInterface!!.getVideoFPSPref())
                if (video_sizes.size == 0) {
                    Log.e(TAG, "can't find any supported video sizes for current fps!")
                    // fall back to unfiltered list
                    video_sizes = preview.getVideoQualityHander().getSupportedVideoQuality()
                }
                // take a copy so that we can reorder
                video_sizes = ArrayList<String>(video_sizes)
                // video_sizes is sorted high to low, but we want to order low to high
                Collections.reverse(video_sizes)

                val video_sizes_f: MutableList<String> = video_sizes
                video_size_index =
                    video_sizes.size - 1 // default to largest (just in case current size not found??)
                for (i in video_sizes.indices) {
                    val video_size = video_sizes[i]
                    if (video_size == preview.videoQualityHander.getCurrentVideoQuality()) {
                        video_size_index = i
                        break
                    }
                }
                Logger.d(TAG, "video_size_index:$video_size_index")
                val video_size_strings: MutableList<String?> = ArrayList<String?>()
                for (video_size in video_sizes) {
                    val quality_string = preview.getCamcorderProfileDescriptionShort(video_size)
                    video_size_strings.add(quality_string)
                }
                addArrayOptionsToPopup(
                    video_size_strings,
                    getResources().getString(R.string.video_quality),
                    false,
                    false,
                    video_size_index,
                    false,
                    "VIDEO_RESOLUTIONS",
                    object : ArrayOptionsPopupListener() {
                        val handler: Handler = Handler()
                        val update_runnable: Runnable = Runnable {
                            Logger.d(
                                TAG,
                                "update settings due to video resolution change"
                            )
                            main_activity.updateForSettings(
                                true,
                                "",
                                true,
                                false
                            ) // keep the popupview open
                        }

                        private fun update() {
                            if (video_size_index == -1) return
                            val quality: String? = video_sizes_f.get(video_size_index)
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            editor.putString(
                                PreferenceKeys.getVideoQualityPreferenceKey(
                                    preview.getCameraId(),
                                    main_activity.applicationInterface!!.cameraIdSPhysicalPref,
                                    main_activity.applicationInterface!!.fpsIsHighSpeed()
                                ), quality
                            )
                            editor.apply()

                            // make it easier to scroll through the list of resolutions without a pause each time
                            handler.removeCallbacks(update_runnable)
                            handler.postDelayed(update_runnable, 400)
                        }

                        override fun onClickPrev(): Int {
                            if (video_size_index != -1 && video_size_index > 0) {
                                video_size_index--
                                update()
                                return video_size_index
                            }
                            return -1
                        }

                        override fun onClickNext(): Int {
                            if (video_size_index != -1 && video_size_index < video_sizes_f.size - 1) {
                                video_size_index++
                                update()
                                return video_size_index
                            }
                            return -1
                        }
                    })
            }
            Logger.d(TAG, "PopupView time 10: " + (System.nanoTime() - debug_time))

            // apertures probably not supported for camera extensions anyway
            if (preview.getSupportedApertures() != null && !isCameraExtension) {
                Logger.d(TAG, "add apertures")

                addTitleToPopup(getResources().getString(R.string.aperture))

                val apertures: MutableList<Float?> = ArrayList<Float?>()
                val apertures_strings: MutableList<String> = ArrayList<String>()
                var current_aperture = main_activity.applicationInterface!!.getAperturePref()
                val prefix = "F/"

                var found_default = false
                var current_aperture_s = ""
                for (aperture in preview.getSupportedApertures()) {
                    apertures.add(aperture)
                    val aperture_string =
                        prefix + decimal_format_1dp_force0.format(aperture.toDouble())
                    apertures_strings.add(aperture_string)
                    if (current_aperture == aperture) {
                        found_default = true
                        current_aperture_s = aperture_string
                    }
                }

                if (!found_default) {
                    // read from Camera API
                    if (preview.getCameraController() != null && preview.getCameraController()
                            .captureResultHasAperture()
                    ) {
                        current_aperture = preview.getCameraController().captureResultAperture()
                        current_aperture_s =
                            prefix + decimal_format_1dp_force0.format(current_aperture.toDouble())
                    }
                }

                addButtonOptionsToPopup(
                    apertures_strings,
                    -1,
                    -1,
                    "",
                    current_aperture_s,
                    0,
                    "TEST_APERTURE",
                    object : ButtonOptionsPopupListener() {
                        override fun onClick(option: String?) {
                            Logger.d(TAG, "clicked aperture: " + option)
                            val index = apertures_strings.indexOf(option)
                            if (index != -1) {
                                val new_aperture: Float = apertures.get(index)!!
                                Logger.d(TAG, "new_aperture: " + new_aperture)
                                preview.showToast(
                                    null,
                                    getResources().getString(R.string.aperture) + ": " + option,
                                    true
                                )
                                main_activity.applicationInterface!!.setAperture(new_aperture)
                                if (preview.getCameraController() != null) {
                                    preview.getCameraController().setAperture(new_aperture)
                                }
                            } else {
                                Log.e(TAG, "unknown aperture: " + option)
                            }
                        }
                    })
            }

            if (!preview.isVideo && photo_mode == PhotoMode.FastBurst) {
                Logger.d(TAG, "add fast burst options")

                val all_burst_mode_values =
                    getResources().getStringArray(R.array.preference_fast_burst_n_images_values)
                val all_burst_mode_entries =
                    getResources().getStringArray(R.array.preference_fast_burst_n_images_entries)

                //String [] burst_mode_values = new String[all_burst_mode_values.length];
                //String [] burst_mode_entries = new String[all_burst_mode_entries.length];
                if (all_burst_mode_values.size != all_burst_mode_entries.size) {
                    Log.e(
                        TAG,
                        "preference_fast_burst_n_images_values and preference_fast_burst_n_images_entries are different lengths"
                    )
                    throw RuntimeException()
                }

                var max_burst_images =
                    main_activity.applicationInterface!!.imageSaver?.queueSize ?: (0 + 1)
                max_burst_images = max(
                    2,
                    max_burst_images
                ) // make sure we at least allow the minimum of 2 burst images!
                Logger.d(TAG, "max_burst_images: " + max_burst_images)

                // filter number of burst images - don't allow more than max_burst_images
                val burst_mode_values_l: MutableList<String?> = ArrayList()
                val burst_mode_entries_l: MutableList<String?> = ArrayList()
                for (i in all_burst_mode_values.indices) {
                    val n_images: Int
                    try {
                        n_images = all_burst_mode_values[i]!!.toInt()
                    } catch (e: NumberFormatException) {
                        Log.e(
                            TAG,
                            "failed to parse " + i + "th preference_fast_burst_n_images_values value: " + all_burst_mode_values[i]
                        )
                        e.printStackTrace()
                        continue
                    }
                    if (n_images > max_burst_images) {
                        Logger.d(
                            TAG,
                            "n_images " + n_images + " is more than max_burst_images: " + max_burst_images
                        )
                        continue
                    }
                    Logger.d(TAG, "n_images " + n_images)
                    burst_mode_values_l.add(all_burst_mode_values[i])
                    burst_mode_entries_l.add(all_burst_mode_entries[i])
                }
                val burst_mode_values = burst_mode_values_l.toTypedArray<String?>()
                val burst_mode_entries = burst_mode_entries_l.toTypedArray<String?>()

                val burst_mode_value: String =
                    sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5")!!
                burst_n_images_index =
                    Arrays.asList<String?>(*burst_mode_values).indexOf(burst_mode_value)
                if (burst_n_images_index == -1) {
                    Logger.d(
                        TAG,
                        "can't find burst_mode_value " + burst_mode_value + " in burst_mode_values!"
                    )
                    burst_n_images_index = 0
                }
                addArrayOptionsToPopup(
                    Arrays.asList<String?>(*burst_mode_entries),
                    getResources().getString(R.string.preference_fast_burst_n_images),
                    true,
                    false,
                    burst_n_images_index,
                    false,
                    "FAST_BURST_N_IMAGES",
                    object : ArrayOptionsPopupListener() {
                        private fun update() {
                            if (burst_n_images_index == -1) return
                            val new_burst_mode_value = burst_mode_values[burst_n_images_index]
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            editor.putString(
                                PreferenceKeys.FastBurstNImagesPreferenceKey,
                                new_burst_mode_value
                            )
                            editor.apply()
                            if (preview.getCameraController() != null) {
                                preview.getCameraController()
                                    .setBurstNImages(main_activity.applicationInterface!!.getBurstNImages())
                            }
                        }

                        override fun onClickPrev(): Int {
                            if (burst_n_images_index != -1 && burst_n_images_index > 0) {
                                burst_n_images_index--
                                update()
                                return burst_n_images_index
                            }
                            return -1
                        }

                        override fun onClickNext(): Int {
                            if (burst_n_images_index != -1 && burst_n_images_index < burst_mode_values.size - 1) {
                                burst_n_images_index++
                                update()
                                return burst_n_images_index
                            }
                            return -1
                        }
                    })
            } else if (!preview.isVideo && photo_mode == PhotoMode.FocusBracketing) {
                Logger.d(TAG, "add focus bracketing options")

                val burst_mode_values =
                    getResources().getStringArray(R.array.preference_focus_bracketing_n_images_values)
                val burst_mode_entries =
                    getResources().getStringArray(R.array.preference_focus_bracketing_n_images_entries)

                if (burst_mode_values.size != burst_mode_entries.size) {
                    Log.e(
                        TAG,
                        "preference_focus_bracketing_n_images_values and preference_focus_bracketing_n_images_entries are different lengths"
                    )
                    throw RuntimeException()
                }

                val burst_mode_value: String = sharedPreferences.getString(
                    PreferenceKeys.FocusBracketingNImagesPreferenceKey,
                    "3"
                )!!
                burst_n_images_index =
                    Arrays.asList<String?>(*burst_mode_values).indexOf(burst_mode_value)
                if (burst_n_images_index == -1) {
                    Logger.d(
                        TAG,
                        "can't find burst_mode_value " + burst_mode_value + " in burst_mode_values!"
                    )
                    burst_n_images_index = 0
                }
                addArrayOptionsToPopup(
                    Arrays.asList<String?>(*burst_mode_entries),
                    getResources().getString(R.string.preference_focus_bracketing_n_images),
                    true,
                    false,
                    burst_n_images_index,
                    false,
                    "FOCUS_BRACKETING_N_IMAGES",
                    object : ArrayOptionsPopupListener() {
                        private fun update() {
                            if (burst_n_images_index == -1) return
                            val new_burst_mode_value = burst_mode_values[burst_n_images_index]
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            editor.putString(
                                PreferenceKeys.FocusBracketingNImagesPreferenceKey,
                                new_burst_mode_value
                            )
                            editor.apply()
                            if (preview.getCameraController() != null) {
                                preview.getCameraController()
                                    .setFocusBracketingNImages(main_activity.applicationInterface!!.getFocusBracketingNImagesPref())
                            }
                        }

                        override fun onClickPrev(): Int {
                            if (burst_n_images_index != -1 && burst_n_images_index > 0) {
                                burst_n_images_index--
                                update()
                                return burst_n_images_index
                            }
                            return -1
                        }

                        override fun onClickNext(): Int {
                            if (burst_n_images_index != -1 && burst_n_images_index < burst_mode_values.size - 1) {
                                burst_n_images_index++
                                update()
                                return burst_n_images_index
                            }
                            return -1
                        }
                    })

                addCheckBox(
                    context,
                    scale,
                    getResources().getString(R.string.focus_bracketing_add_infinity),
                    sharedPreferences.getBoolean(
                        PreferenceKeys.FocusBracketingAddInfinityPreferenceKey,
                        false
                    ),
                    object : CompoundButton.OnCheckedChangeListener {
                        override fun onCheckedChanged(
                            buttonView: CompoundButton?,
                            isChecked: Boolean
                        ) {
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            editor.putBoolean(
                                PreferenceKeys.FocusBracketingAddInfinityPreferenceKey,
                                isChecked
                            )
                            editor.apply()
                            if (preview.getCameraController() != null) {
                                preview.getCameraController().setFocusBracketingAddInfinity(
                                    main_activity.applicationInterface!!.getFocusBracketingAddInfinityPref()
                                )
                            }
                        }
                    })

                if (main_activity.supportsFocusBracketingSourceAuto()) {
                    addCheckBox(
                        context,
                        scale,
                        getResources().getString(R.string.focus_bracketing_auto_source_distance),
                        sharedPreferences.getBoolean(
                            PreferenceKeys.FocusBracketingAutoSourceDistancePreferenceKey,
                            false
                        ),
                        object : CompoundButton.OnCheckedChangeListener {
                            override fun onCheckedChanged(
                                buttonView: CompoundButton?,
                                isChecked: Boolean
                            ) {
                                main_activity.applicationInterface!!.setFocusBracketingSourceAutoPref(
                                    isChecked
                                )
                                if (!isChecked) {
                                    preview.setFocusDistance(
                                        main_activity.preview!!.getCameraController()
                                            .captureResultFocusDistance(), false, false
                                    )
                                }
                            }
                        })
                }
            }

            if (preview.isVideo) {
                val capture_rate_values =
                    main_activity.applicationInterface!!.supportedVideoCaptureRates
                if (capture_rate_values.size > 1) {
                    Logger.d(TAG, "add slow motion / timelapse video options")
                    val capture_rate_value = sharedPreferences.getFloat(
                        PreferenceKeys.getVideoCaptureRatePreferenceKey(
                            preview.getCameraId(),
                            main_activity.applicationInterface!!.getCameraIdSPhysicalPref()
                        ), 1.0f
                    )
                    val capture_rate_str: MutableList<String?> = ArrayList<String?>()
                    var capture_rate_std_index = -1
                    for (i in capture_rate_values.indices) {
                        val this_capture_rate: Float = capture_rate_values.get(i)!!
                        if (abs(1.0f - this_capture_rate) < 1.0e-5) {
                            capture_rate_str.add(getResources().getString(R.string.preference_video_capture_rate_normal))
                            capture_rate_std_index = i
                        } else {
                            capture_rate_str.add(this_capture_rate.toString() + "x")
                        }
                        if (abs(capture_rate_value - this_capture_rate) < 1.0e-5) {
                            video_capture_rate_index = i
                        }
                    }
                    if (video_capture_rate_index == -1) {
                        Logger.d(TAG, "can't find video_capture_rate_index")
                        // default to no slow motion or timelapse
                        video_capture_rate_index = capture_rate_std_index
                        if (video_capture_rate_index == -1) {
                            Log.e(TAG, "can't find capture_rate_std_index")
                            video_capture_rate_index = 0
                        }
                    }
                    addArrayOptionsToPopup(
                        capture_rate_str,
                        getResources().getString(R.string.preference_video_capture_rate),
                        true,
                        false,
                        video_capture_rate_index,
                        false,
                        "VIDEOCAPTURERATE",
                        object : ArrayOptionsPopupListener() {
                            private var old_video_capture_rate_index = video_capture_rate_index

                            val handler: Handler = Handler()
                            val update_runnable: Runnable = object : Runnable {
                                override fun run() {
                                    Logger.d(
                                        TAG,
                                        "update settings due to video capture rate change"
                                    )
                                    main_activity.updateForSettings(
                                        true,
                                        "",
                                        true,
                                        false
                                    ) // keep the popupview open
                                }
                            }

                            private fun update() {
                                if (video_capture_rate_index == -1) return
                                val new_capture_rate_value: Float =
                                    capture_rate_values.get(video_capture_rate_index)!!
                                val sharedPreferences =
                                    PreferenceManager.getDefaultSharedPreferences(main_activity)
                                val editor = sharedPreferences.edit()
                                editor.putFloat(
                                    PreferenceKeys.getVideoCaptureRatePreferenceKey(
                                        preview.getCameraId(),
                                        main_activity.applicationInterface!!.getCameraIdSPhysicalPref()
                                    ), new_capture_rate_value
                                )
                                editor.apply()

                                val old_capture_rate_value: Float =
                                    capture_rate_values.get(old_video_capture_rate_index)!!
                                val old_slow_motion = (old_capture_rate_value < 1.0f - 1.0e-5f)
                                val new_slow_motion = (new_capture_rate_value < 1.0f - 1.0e-5f)
                                // if changing to/from a slow motion mode, this will in general switch on/off high fps frame
                                // rates, which changes the available video resolutions, so we need to re-open the popup
                                val keep_popup = (old_slow_motion == new_slow_motion)
                                // only display a toast if the popup is closing
                                //String toast_message = getResources().getString(R.string.preference_video_capture_rate) + ": " + capture_rate_str.get(video_capture_rate_index);
                                var toast_message = ""
                                if (!keep_popup) {
                                    if (new_slow_motion) toast_message =
                                        getResources().getString(R.string.slow_motion_enabled) + "\n" + getResources().getString(
                                            R.string.preference_video_capture_rate
                                        ) + ": " + capture_rate_str.get(video_capture_rate_index)
                                    else toast_message =
                                        getResources().getString(R.string.slow_motion_disabled)
                                }
                                if (MyDebug.LOG) {
                                    Logger.d(
                                        TAG,
                                        "update settings due to capture rate change"
                                    )
                                    Logger.d(
                                        TAG,
                                        "old_capture_rate_value: " + old_capture_rate_value
                                    )
                                    Logger.d(
                                        TAG,
                                        "new_capture_rate_value: " + new_capture_rate_value
                                    )
                                    Logger.d(TAG, "old_slow_motion: " + old_slow_motion)
                                    Logger.d(TAG, "new_slow_motion: " + new_slow_motion)
                                    Logger.d(TAG, "keep_popup: " + keep_popup)
                                    Logger.d(TAG, "toast_message: " + toast_message)
                                }
                                old_video_capture_rate_index = video_capture_rate_index

                                if (keep_popup) {
                                    // make it easier to scroll through the list of capture rates without a pause each time
                                    handler.removeCallbacks(update_runnable)
                                    handler.postDelayed(update_runnable, 400)
                                } else {
                                    main_activity.updateForSettings(
                                        true,
                                        toast_message,
                                        keep_popup,
                                        false
                                    )
                                }
                            }

                            override fun onClickPrev(): Int {
                                if (video_capture_rate_index != -1 && video_capture_rate_index > 0) {
                                    video_capture_rate_index--
                                    update()
                                    return video_capture_rate_index
                                }
                                return -1
                            }

                            override fun onClickNext(): Int {
                                if (video_capture_rate_index != -1 && video_capture_rate_index < capture_rate_values.size - 1) {
                                    video_capture_rate_index++
                                    update()
                                    return video_capture_rate_index
                                }
                                return -1
                            }
                        })
                }
            }

            if (photo_mode != PhotoMode.Panorama) {
                // timer not supported with panorama

                val timer_values = getResources().getStringArray(R.array.preference_timer_values)
                val timer_entries = getResources().getStringArray(R.array.preference_timer_entries)
                val timer_value: String =
                    sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0")!!
                timer_index = Arrays.asList<String?>(*timer_values).indexOf(timer_value)
                if (timer_index == -1) {
                    Logger.d(
                        TAG,
                        "can't find timer_value " + timer_value + " in timer_values!"
                    )
                    timer_index = 0
                }
                // title_in_options should be false for small screens: e.g., problems with pt-rBR or pt-rPT on 4.5" screens or less, see https://sourceforge.net/p/opencamera/discussion/photography/thread/3aa940c636/
                addArrayOptionsToPopup(
                    Arrays.asList<String?>(*timer_entries),
                    getResources().getString(R.string.preference_timer),
                    !small_screen,
                    false,
                    timer_index,
                    false,
                    "TIMER",
                    object : ArrayOptionsPopupListener() {
                        private fun update() {
                            if (timer_index == -1) return
                            val new_timer_value = timer_values[timer_index]
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            editor.putString(PreferenceKeys.TimerPreferenceKey, new_timer_value)
                            editor.apply()
                        }

                        override fun onClickPrev(): Int {
                            if (timer_index != -1 && timer_index > 0) {
                                timer_index--
                                update()
                                return timer_index
                            }
                            return -1
                        }

                        override fun onClickNext(): Int {
                            if (timer_index != -1 && timer_index < timer_values.size - 1) {
                                timer_index++
                                update()
                                return timer_index
                            }
                            return -1
                        }
                    })
            }
            Logger.d(TAG, "PopupView time 11: " + (System.nanoTime() - debug_time))

            if (photo_mode != PhotoMode.Panorama) {
                // auto-repeat not supported with panorama

                val repeat_mode_values =
                    getResources().getStringArray(R.array.preference_burst_mode_values)
                val repeat_mode_entries =
                    getResources().getStringArray(R.array.preference_burst_mode_entries)
                val repeat_mode_value: String =
                    sharedPreferences.getString(PreferenceKeys.RepeatModePreferenceKey, "1")!!
                repeat_mode_index =
                    Arrays.asList<String?>(*repeat_mode_values).indexOf(repeat_mode_value)
                if (repeat_mode_index == -1) {
                    Logger.d(
                        TAG,
                        "can't find repeat_mode_value " + repeat_mode_value + " in repeat_mode_values!"
                    )
                    repeat_mode_index = 0
                }
                // title_in_options should be false for small screens: e.g., problems with pt-rBR or pt-rPT on 4.5" screens or less, see https://sourceforge.net/p/opencamera/discussion/photography/thread/3aa940c636/
                // set title_in_options_first_only to true, as displaying "Repeat: Unlimited" can be too long in some languages, e.g., Vietnamese (vi)
                addArrayOptionsToPopup(
                    Arrays.asList<String?>(*repeat_mode_entries),
                    getResources().getString(R.string.preference_burst_mode),
                    !small_screen,
                    true,
                    repeat_mode_index,
                    false,
                    "REPEAT_MODE",
                    object : ArrayOptionsPopupListener() {
                        private fun update() {
                            if (repeat_mode_index == -1) return
                            val new_repeat_mode_value = repeat_mode_values[repeat_mode_index]
                            val sharedPreferences =
                                PreferenceManager.getDefaultSharedPreferences(main_activity)
                            val editor = sharedPreferences.edit()
                            editor.putString(
                                PreferenceKeys.RepeatModePreferenceKey,
                                new_repeat_mode_value
                            )
                            editor.apply()
                        }

                        override fun onClickPrev(): Int {
                            if (repeat_mode_index != -1 && repeat_mode_index > 0) {
                                repeat_mode_index--
                                update()
                                return repeat_mode_index
                            }
                            return -1
                        }

                        override fun onClickNext(): Int {
                            if (repeat_mode_index != -1 && repeat_mode_index < repeat_mode_values.size - 1) {
                                repeat_mode_index++
                                update()
                                return repeat_mode_index
                            }
                            return -1
                        }
                    })
                Logger.d(
                    TAG,
                    "PopupView time 12: " + (System.nanoTime() - debug_time)
                )
            }

            val grid_values = resources.getStringArray(R.array.preference_grid_values)
            val grid_entries = resources.getStringArray(R.array.preference_grid_entries)
            val grid_value: String = sharedPreferences.getString(
                PreferenceKeys.ShowGridPreferenceKey,
                "preference_grid_none"
            )!!
            grid_index = Arrays.asList<String?>(*grid_values).indexOf(grid_value)
            if (grid_index == -1) {
                Logger.d(
                    TAG,
                    "can't find grid_value $grid_value in grid_values!"
                )
                grid_index = 0
            }
            addArrayOptionsToPopup(
                Arrays.asList<String?>(*grid_entries),
                getResources().getString(R.string.grid),
                true,
                true,
                grid_index,
                true,
                "GRID",
                object : ArrayOptionsPopupListener() {
                    private fun update() {
                        if (grid_index == -1) return
                        val new_grid_value = grid_values[grid_index]
                        val sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(main_activity)
                        val editor = sharedPreferences.edit()
                        editor.putString(PreferenceKeys.ShowGridPreferenceKey, new_grid_value)
                        editor.apply()
                        main_activity.applicationInterface!!.drawPreview?.updateSettings() // because we cache the grid
                    }

                    override fun onClickPrev(): Int {
                        if (grid_index != -1) {
                            grid_index--
                            if (grid_index < 0) grid_index += grid_values.size
                            update()
                            return grid_index
                        }
                        return -1
                    }

                    override fun onClickNext(): Int {
                        if (grid_index != -1) {
                            grid_index++
                            if (grid_index >= grid_values.size) grid_index -= grid_values.size
                            update()
                            return grid_index
                        }
                        return -1
                    }
                })
            Logger.d(TAG, "PopupView time 13: " + (System.nanoTime() - debug_time))

            // white balance modes, scene modes, color effects
            // all of these are only supported when not using extension mode
            // popup should only be opened if we have a camera controller, but check just to be safe
            if (preview.cameraController != null && !isCameraExtension) {
                val supported_white_balances = preview.getSupportedWhiteBalances()
                var supported_white_balances_entries: MutableList<String>? = null
                if (supported_white_balances != null) {
                    supported_white_balances_entries = ArrayList<String>()
                    for (value in supported_white_balances) {
                        val entry = main_activity.mainUI!!.getEntryForWhiteBalance(value)
                        supported_white_balances_entries.add(entry)
                    }
                }
                addRadioOptionsToPopup(
                    sharedPreferences,
                    supported_white_balances_entries,
                    supported_white_balances!!,
                    getResources().getString(R.string.white_balance),
                    PreferenceKeys.WhiteBalancePreferenceKey,
                    CameraController.WHITE_BALANCE_DEFAULT,
                    null,
                    "TEST_WHITE_BALANCE",
                    object : RadioOptionsListener() {
                        override fun onClick(selectedValue: String?) {
                            selectedValue?.let { switchToWhiteBalance(selectedValue) }
                        }
                    })
                Logger.d(
                    TAG,
                    "PopupView time 14: " + (System.nanoTime() - debug_time)
                )

                val supported_scene_modes = preview.getSupportedSceneModes()
                var supported_scene_modes_entries: MutableList<String>? = null
                if (supported_scene_modes != null) {
                    supported_scene_modes_entries = ArrayList<String>()
                    for (value in supported_scene_modes) {
                        val entry = main_activity.mainUI!!.getEntryForSceneMode(value)
                        supported_scene_modes_entries.add(entry)
                    }
                }
                addRadioOptionsToPopup(
                    sharedPreferences,
                    supported_scene_modes_entries,
                    supported_scene_modes!!,
                    getResources().getString(R.string.scene_mode),
                    PreferenceKeys.SceneModePreferenceKey,
                    CameraController.SCENE_MODE_DEFAULT,
                    null,
                    "TEST_SCENE_MODE",
                    object : RadioOptionsListener() {
                        override fun onClick(selected_value: String?) {
                            if (preview.cameraController != null && selected_value != null) {
                                if (preview.cameraController
                                        .sceneModeAffectsFunctionality()
                                ) {
                                    // need to call updateForSettings() and close the popup, as changing scene mode can change available camera features
                                    main_activity.updateForSettings(
                                        true,
                                        getResources().getString(R.string.scene_mode) + ": " + main_activity.mainUI!!.getEntryForSceneMode(
                                            selected_value
                                        )
                                    )
                                    main_activity.closePopup()
                                } else {
                                    preview.cameraController.setSceneMode(selected_value)
                                    // keep popup open
                                }
                            }
                        }
                    })
                Logger.d(
                    TAG,
                    "PopupView time 15: " + (System.nanoTime() - debug_time)
                )

                val supported_color_effects = preview.getSupportedColorEffects()
                var supported_color_effects_entries: MutableList<String>? = null
                if (supported_color_effects != null) {
                    supported_color_effects_entries = ArrayList<String>()
                    for (value in supported_color_effects) {
                        val entry = main_activity.mainUI!!.getEntryForColorEffect(value)
                        supported_color_effects_entries.add(entry)
                    }
                }
                addRadioOptionsToPopup(
                    sharedPreferences,
                    supported_color_effects_entries,
                    supported_color_effects!!,
                    getResources().getString(R.string.color_effect),
                    PreferenceKeys.ColorEffectPreferenceKey,
                    CameraController.COLOR_EFFECT_DEFAULT,
                    null,
                    "TEST_COLOR_EFFECT",
                    object : RadioOptionsListener() {
                        override fun onClick(selected_value: String?) {
                            if (preview.cameraController != null) {
                                preview.cameraController.setColorEffect(selected_value)
                            }
                            // keep popup open
                        }
                    })
                Logger.d(
                    TAG,
                    "PopupView time 16: " + (System.nanoTime() - debug_time)
                )
            }
        }

        Logger.d(TAG, "Overall PopupView time: " + (System.nanoTime() - debug_time))
    }

    val totalWidth: Int
        get() {
            val scale = resources.displayMetrics.density
            return (total_width_dp * scale + 0.5f).toInt() // convert dps to pixels;
        }

    private fun changePhotoMode(
        photo_modes: MutableList<String>,
        photo_mode_values: MutableList<PhotoMode>,
        option: String
    ) {
        Logger.d(TAG, "changePhotoMode: $option")

        val main_activity = this.context as MainActivity
        var option_id = -1
        var i = 0
        while (i < photo_modes.size && option_id == -1) {
            if (option == photo_modes[i]) option_id = i
            i++
        }
        Logger.d(TAG, "mode id: $option_id")
        if (option_id == -1) {
            if (MyDebug.LOG) Log.e(TAG, "unknown mode id: $option_id")
        } else {
            val new_photo_mode = photo_mode_values.get(option_id)
            var toast_message: String? = option
            when (new_photo_mode) {
                PhotoMode.Standard -> toast_message =
                    getResources().getString(R.string.photo_mode_standard_full)

                PhotoMode.ExpoBracketing -> toast_message =
                    getResources().getString(R.string.photo_mode_expo_bracketing_full)

                PhotoMode.FocusBracketing -> toast_message =
                    getResources().getString(R.string.photo_mode_focus_bracketing_full)

                PhotoMode.FastBurst -> toast_message =
                    getResources().getString(R.string.photo_mode_fast_burst_full)

                PhotoMode.NoiseReduction -> toast_message =
                    getResources().getString(R.string.photo_mode_noise_reduction_full)

                PhotoMode.Panorama -> toast_message =
                    getResources().getString(R.string.photo_mode_panorama_full)

                PhotoMode.X_Auto -> toast_message =
                    getResources().getString(R.string.photo_mode_x_auto_full)

                PhotoMode.X_HDR -> toast_message =
                    getResources().getString(R.string.photo_mode_x_hdr_full)

                PhotoMode.X_Night -> toast_message =
                    getResources().getString(R.string.photo_mode_x_night_full)

                PhotoMode.X_Bokeh -> toast_message =
                    getResources().getString(R.string.photo_mode_x_bokeh_full)

                PhotoMode.X_Beauty -> toast_message =
                    getResources().getString(R.string.photo_mode_x_beauty_full)

                PhotoMode.DRO -> TODO()
                PhotoMode.HDR -> TODO()
            }
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity)
            val editor = sharedPreferences.edit()
            when (new_photo_mode) {
                PhotoMode.Standard -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_std"
                )

                PhotoMode.DRO -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_dro"
                )

                PhotoMode.HDR -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_hdr"
                )

                PhotoMode.ExpoBracketing -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_expo_bracketing"
                )

                PhotoMode.FocusBracketing -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_focus_bracketing"
                )

                PhotoMode.FastBurst -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_fast_burst"
                )

                PhotoMode.NoiseReduction -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_noise_reduction"
                )

                PhotoMode.Panorama -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_panorama"
                )

                PhotoMode.X_Auto -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_x_auto"
                )

                PhotoMode.X_HDR -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_x_hdr"
                )

                PhotoMode.X_Night -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_x_night"
                )

                PhotoMode.X_Bokeh -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_x_bokeh"
                )

                PhotoMode.X_Beauty -> editor.putString(
                    PreferenceKeys.PhotoModePreferenceKey,
                    "preference_photo_mode_x_beauty"
                )

                else -> if (MyDebug.LOG) Log.e(TAG, "unknown new_photo_mode: " + new_photo_mode)
            }
            editor.apply()

            var done_dialog = false
            if (new_photo_mode == PhotoMode.HDR) {
                val done_hdr_info = sharedPreferences.contains(PreferenceKeys.HDRInfoPreferenceKey)
                if (!done_hdr_info) {
                    main_activity.mainUI!!.showInfoDialog(
                        R.string.photo_mode_hdr,
                        R.string.hdr_info,
                        PreferenceKeys.HDRInfoPreferenceKey
                    )
                    done_dialog = true
                }
            } else if (new_photo_mode == PhotoMode.Panorama) {
                val done_panorama_info =
                    sharedPreferences.contains(PreferenceKeys.PanoramaInfoPreferenceKey)
                if (!done_panorama_info) {
                    main_activity.mainUI!!.showInfoDialog(
                        R.string.photo_mode_panorama_full,
                        R.string.panorama_info,
                        PreferenceKeys.PanoramaInfoPreferenceKey
                    )
                    done_dialog = true
                }
            }

            if (done_dialog) {
                // no need to show toast
                toast_message = null
            }

            main_activity.applicationInterface!!.drawPreview?.updateSettings() // because we cache the photomode
            main_activity.updateForSettings(
                true,
                toast_message,
                false,
                true
            ) // need to setup the camera again, as options may change (e.g., required burst mode, or whether RAW is allowed in this mode)
        }
    }

    fun switchToWhiteBalance(selected_value: String) {
        Logger.d(TAG, "switchToWhiteBalance: " + selected_value)
        val main_activity = this.getContext() as MainActivity
        val preview = main_activity.preview
        var close_popup = false
        var temperature = -1
        if (selected_value == "manual") {
            if (preview!!.getCameraController() != null) {
                val current_white_balance = preview.getCameraController().getWhiteBalance()
                if (current_white_balance == null || current_white_balance != "manual") {
                    // try to choose a default manual white balance temperature as close as possible to the current auto
                    Logger.d(TAG, "changed to manual white balance")
                    close_popup = true
                    if (preview.getCameraController().captureResultHasWhiteBalanceTemperature()) {
                        temperature =
                            preview.getCameraController().captureResultWhiteBalanceTemperature()
                        Logger.d(
                            TAG,
                            "default to manual white balance temperature: " + temperature
                        )
                        val sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(main_activity)
                        val editor = sharedPreferences.edit()
                        editor.putInt(
                            PreferenceKeys.WhiteBalanceTemperaturePreferenceKey,
                            temperature
                        )
                        editor.apply()
                    }

                    // otherwise default to the saved value
                    if (!main_activity.mainUI!!.isExposureUIOpen) {
                        // also open the exposure UI, to show the
                        main_activity.mainUI!!.toggleExposureUI()
                    }
                }
            }
        }

        if (preview!!.getCameraController() != null) {
            preview.getCameraController().setWhiteBalance(selected_value)
            if (temperature > 0) {
                preview.getCameraController().setWhiteBalanceTemperature(temperature)
                // also need to update the slider!
                main_activity.setManualWBSeekbar()
            }
        }
        // keep popup open, unless switching to manual
        if (close_popup) {
            main_activity.closePopup()
        }
        //main_activity.updateForSettings(getResources().getString(R.string.white_balance) + ": " + selected_value);
        //main_activity.closePopup();
    }

    abstract class ButtonOptionsPopupListener {
        abstract fun onClick(option: String?)
    }

    private fun addCheckBox(
        context: Context?,
        scale: Float,
        text: CharSequence?,
        checked: Boolean,
        listener: CompoundButton.OnCheckedChangeListener?
    ) {
        @SuppressLint("InflateParams") val switch_view =
            LayoutInflater.from(context).inflate(R.layout.popupview_switch, null)
        val checkBox = switch_view.findViewById<SwitchCompat>(R.id.popupview_switch)
        checkBox.setText(text)
        run {
            // align the checkbox a bit better
            checkBox.setGravity(Gravity.RIGHT)
            val params = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            val right_padding = (20 * scale + 0.5f).toInt() // convert dps to pixels
            params.setMargins(0, 0, right_padding, 0)
            checkBox.setLayoutParams(params)
        }
        if (checked) checkBox.setChecked(checked)
        checkBox.setOnCheckedChangeListener(listener)
        this.addView(checkBox)
    }

    /** Creates UI for selecting an option for multiple possibilites, by placing buttons in one or
     * more rows.
     * @param max_buttons_per_row If 0, then all buttons will be placed on the same row. Otherwise,
     * this is the number of buttons per row, multiple rows will be
     * created if necessary.
     */
    private fun addButtonOptionsToPopup(
        supported_options: MutableList<String>?,
        icons_id: Int,
        values_id: Int,
        prefix_string: String,
        current_value: String?,
        max_buttons_per_row: Int,
        test_key: String?,
        listener: ButtonOptionsPopupListener
    ) {
        Logger.d(TAG, "addButtonOptionsToPopup")
        val main_activity = this.getContext() as MainActivity
        createButtonOptions(
            this,
            this.getContext(),
            total_width_dp,
            main_activity.mainUI!!.testUIButtonsMap,
            supported_options,
            icons_id,
            values_id,
            prefix_string,
            true,
            current_value,
            max_buttons_per_row,
            test_key,
            listener
        )
    }

    private fun addTitleToPopup(title: String?) {
        val debugTime = System.nanoTime()

        @SuppressLint("InflateParams") val view =
            LayoutInflater.from(this.context).inflate(R.layout.popupview_textview, null)
        val textView = view.findViewById<TextView>(R.id.text_view)
        textView.text = "$title:"
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, title_text_size_dip)
        textView.setTypeface(null, Typeface.BOLD)
        //text_view.setBackgroundColor(Color.GRAY); // debug
        this.addView(textView)
        Logger.d(TAG, "addTitleToPopup time: " + (System.nanoTime() - debugTime))
    }

    private abstract class RadioOptionsListener {
        /** Called when a radio option is selected.
         * @param selectedValue The entry in the supplied supported_options_values list (received
         * by addRadioOptionsToPopup) that corresponds to the selected radio
         * option.
         */
        abstract fun onClick(selectedValue: String?)
    }

    /** Adds a set of radio options to the popup menu.
     * @param sharedPreferences         The SharedPreferences.
     * @param supported_options_entries The strings to display on the radio options.
     * @param supported_options_values  A corresponding array of values. These aren't shown to the
     * user, but are the values that will be set in the
     * sharedPreferences, and passed to the listener.
     * @param title                     The text to display as a title for this radio group.
     * @param preference_key            The preference key to use for the values in the
     * sharedPreferences. May be null, in which case it's up to
     * the user to save the new preference via a listener.
     * @param default_value             The default value for the preference_key in the
     * sharedPreferences. Only needed if preference_key is
     * non-null.
     * @param current_option_value      If preference_key is null, this should be the currently
     * selected value. Otherwise, this is ignored.
     * @param test_key                  Used for testing, a tag to identify the RadioGroup that's
     * created.
     * @param listener                  If null, selecting an option will call
     * MainActivity.updateForSettings() and close the popup. If
     * not null, instead selecting an option will call the
     * listener.
     */
    @SuppressLint("SetTextI18n")
    private fun addRadioOptionsToPopup(
        sharedPreferences: SharedPreferences,
        supported_options_entries: MutableList<String>?,
        supported_options_values: MutableList<String>,
        title: String?,
        preference_key: String?,
        default_value: String?,
        current_option_value: String?,
        test_key: String?,
        listener: RadioOptionsListener?
    ) {
        Logger.d(TAG, "addRadioOptionsToPopup: $title")
        if (supported_options_entries != null) {
            val main_activity = this.context as MainActivity
            val debug_time = System.nanoTime()

            @SuppressLint("InflateParams") val button_view =
                LayoutInflater.from(this.context).inflate(R.layout.popupview_button, null)
            val button = button_view.findViewById<Button>(R.id.button)

            button.setBackgroundColor(Color.TRANSPARENT) // workaround for Android 6 crash!
            button.text = "$title..."
            button.isAllCaps = false
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, title_text_size_dip)
            this.addView(button)
            Logger.d(
                TAG,
                "addRadioOptionsToPopup time 1: " + (System.nanoTime() - debug_time)
            )

            val rg = RadioGroup(this.context)
            rg.orientation = VERTICAL
            rg.visibility = GONE
            main_activity.mainUI!!.testUIButtonsMap.put(test_key, rg)
            Logger.d(
                TAG,
                "addRadioOptionsToPopup time 2: " + (System.nanoTime() - debug_time)
            )

            button.setOnClickListener(object : OnClickListener {
                private var opened = false
                private var created = false

                override fun onClick(view: View?) {
                    Logger.d(TAG, "clicked to open radio buttons menu: $title")
                    if (opened) {
                        rg.visibility = GONE
                        val popupContainer =
                            main_activity.findViewById<ScrollView>(R.id.popup_container)
                        // need to invalidate/requestLayout so that the scrollview's scroll positions update - otherwise scrollBy below doesn't work properly, when the user reopens the radio buttons
                        popupContainer.invalidate()
                        popupContainer.requestLayout()
                    } else {
                        if (!created) {
                            addRadioOptionsToGroup(
                                rg,
                                sharedPreferences,
                                supported_options_entries,
                                supported_options_values,
                                title,
                                preference_key,
                                default_value,
                                current_option_value,
                                test_key,
                                listener
                            )
                            created = true
                        }
                        rg.visibility = VISIBLE
                        val popupContainer =
                            main_activity.findViewById<ScrollView>(R.id.popup_container)
                        popupContainer.viewTreeObserver.addOnGlobalLayoutListener(
                            object : OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    Logger.d(TAG, "onGlobalLayout()")
                                    // stop listening - only want to call this once!
                                    popupContainer.viewTreeObserver.removeOnGlobalLayoutListener(
                                        this
                                    )

                                    // so that the user sees the options appear, if the button is at the bottom of the current scrollview position
                                    if (rg.isNotEmpty()) {
                                        val id = rg.checkedRadioButtonId
                                        if (id >= 0 && id < rg.size) {
                                            popupContainer.smoothScrollBy(
                                                0,
                                                rg.getChildAt(id).bottom
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                    opened = !opened
                }
            })

            this.addView(rg)
            Logger.d(
                TAG,
                "addRadioOptionsToPopup time 5: " + (System.nanoTime() - debug_time)
            )
        }
    }

    private fun addRadioOptionsToGroup(
        rg: RadioGroup,
        sharedPreferences: SharedPreferences,
        supported_options_entries: MutableList<String>,
        supported_options_values: MutableList<String>,
        title: String?,
        preference_key: String?,
        default_value: String?,
        current_option_value: String?,
        test_key: String?,
        listener: RadioOptionsListener?
    ) {
        var current_option_value = current_option_value
        Logger.d(TAG, "addRadioOptionsToGroup: " + title)
        if (preference_key != null) current_option_value =
            sharedPreferences.getString(preference_key, default_value)
        val debug_time = System.nanoTime()
        val main_activity = this.getContext() as MainActivity
        var count = 0
        for (i in supported_options_entries.indices) {
            val supported_option_entry: String? = supported_options_entries.get(i)
            val supported_option_value = supported_options_values.get(i)
            if (MyDebug.LOG) {
                Logger.d(TAG, "supported_option_entry: " + supported_option_entry)
                Logger.d(TAG, "supported_option_value: " + supported_option_value)
            }
            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 1: " + (System.nanoTime() - debug_time)
            )

            // Inflating from XML made opening the radio button sub-menus much slower on old devices (e.g., Galaxy Nexus),
            // however testing showed this is also just as slow if we programmatically create a new AppCompatRadioButton().
            // I.e., the slowdown is due to using AppCompatRadioButton (which AppCompat will automatically use if creating
            // a RadioButton from XML) rather than inflating from XML.
            // Whilst creating a new RadioButton() was faster, we can't do that anymore due to emoji policy!
            @SuppressLint("InflateParams") val view =
                LayoutInflater.from(this.getContext()).inflate(R.layout.popupview_radiobutton, null)
            val button = view.findViewById<RadioButton>(R.id.popupview_radiobutton)

            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 2: " + (System.nanoTime() - debug_time)
            )

            button.setId(count)

            button.setText(supported_option_entry)
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, standard_text_size_dip)
            button.setTextColor(Color.WHITE)
            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 3: " + (System.nanoTime() - debug_time)
            )
            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 4: " + (System.nanoTime() - debug_time)
            )
            rg.addView(button)
            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 5: " + (System.nanoTime() - debug_time)
            )

            if (supported_option_value == current_option_value) {
                //button.setChecked(true);
                rg.check(count)
            }
            count++

            button.setContentDescription(supported_option_entry)
            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 6: " + (System.nanoTime() - debug_time)
            )
            button.setOnClickListener(object : OnClickListener {
                override fun onClick(v: View?) {
                    if (MyDebug.LOG) {
                        Logger.d(
                            TAG,
                            "clicked current_option entry: " + supported_option_entry
                        )
                        Logger.d(
                            TAG,
                            "clicked current_option entry: " + supported_option_value
                        )
                    }
                    if (preference_key != null) {
                        val sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(main_activity)
                        val editor = sharedPreferences.edit()
                        editor.putString(preference_key, supported_option_value)
                        editor.apply()
                    }

                    if (listener != null) {
                        listener.onClick(supported_option_value)
                    } else {
                        main_activity.updateForSettings(true, title + ": " + supported_option_entry)
                        main_activity.closePopup()
                    }
                }
            })
            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 7: " + (System.nanoTime() - debug_time)
            )
            main_activity.mainUI!!.testUIButtonsMap.put(
                test_key + "_" + supported_option_value,
                button
            )
            Logger.d(
                TAG,
                "addRadioOptionsToGroup time 8: " + (System.nanoTime() - debug_time)
            )
        }
        Logger.d(
            TAG,
            "addRadioOptionsToGroup time total: " + (System.nanoTime() - debug_time)
        )
    }

    private abstract class ArrayOptionsPopupListener {
        abstract fun onClickPrev(): Int
        abstract fun onClickNext(): Int
    }

    private fun setArrayOptionsText(
        supported_options: MutableList<String?>,
        title: String?,
        textView: TextView,
        title_in_options: Boolean,
        title_in_options_first_only: Boolean,
        current_index: Int
    ) {
        if (title_in_options && !(current_index != 0 && title_in_options_first_only)) textView.setText(
            title + ": " + supported_options.get(current_index)
        )
        else textView.setText(supported_options.get(current_index))
    }

    /** Adds a set of options to the popup menu, where there user can select one option out of an array of values, using previous or
     * next buttons to switch between them.
     * @param supported_options The strings for the array of values to choose from.
     * @param title Title to display.
     * @param title_in_options Prepend the title to each of the values, rather than above the values.
     * @param title_in_options_first_only If title_in_options is true, only prepend to the first option.
     * @param current_index Index in the supported_options array of the currently selected option.
     * @param cyclic Whether the user can cycle beyond the start/end, to wrap around.
     * @param test_key Used to keep track of the UI elements created, for testing.
     * @param listener Listener called when previous/next buttons are clicked (and hence the option
     * changed).
     */
    private fun addArrayOptionsToPopup(
        supported_options: MutableList<String?>?,
        title: String?,
        title_in_options: Boolean,
        title_in_options_first_only: Boolean,
        current_index: Int,
        cyclic: Boolean,
        test_key: String?,
        listener: ArrayOptionsPopupListener
    ) {
        if (supported_options != null && current_index != -1) {
            if (!title_in_options) {
                addTitleToPopup(title)
            }

            val main_activity = this.context as MainActivity

            val debug_time = System.nanoTime()

            @SuppressLint("InflateParams") val ll2 = LayoutInflater.from(this.getContext())
                .inflate(R.layout.popupview_arrayoptions, null)
            val text_view = ll2.findViewById<TextView>(R.id.text_view)
            val prev_button = ll2.findViewById<Button>(R.id.button_left)
            val next_button = ll2.findViewById<Button>(R.id.button_right)

            setArrayOptionsText(
                supported_options,
                title,
                text_view,
                title_in_options,
                title_in_options_first_only,
                current_index
            )
            //text_view.setBackgroundColor(Color.GRAY); // debug
            text_view.setTextSize(TypedValue.COMPLEX_UNIT_SP, standard_text_size_dip)
            text_view.isSingleLine =
                true // if text too long for the button, we'd rather not have wordwrap, even if it means cutting some text off
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1.0f)
            // Yuck! We want the arrow_button_w to be fairly large so that users can touch the arrow buttons easily, but if
            // the text is too much for the button size, we'd rather it extend into the arrow buttons (which the user won't see
            // anyway, since the button backgrounds are transparent).
            // Needed for OnePlus 3T and Nokia 8, for camera resolution
            params.setMargins(-arrow_button_w / 2, 0, -arrow_button_w / 2, 0)
            text_view.layoutParams = params

            val scale = resources.displayMetrics.density
            val padding = (0 * scale + 0.5f).toInt() // convert dps to pixels
            prev_button.setBackgroundColor(Color.TRANSPARENT) // workaround for Android 6 crash!
            //ll2.addView(prev_button);
            prev_button.setText("<")
            prev_button.setTextSize(TypedValue.COMPLEX_UNIT_SP, arrow_text_size_dip)
            prev_button.setTypeface(null, Typeface.BOLD)
            prev_button.setPadding(padding, padding, padding, padding)
            var vg_params = prev_button.layoutParams
            vg_params.width = arrow_button_w
            vg_params.height = arrow_button_h
            prev_button.layoutParams = vg_params
            prev_button.visibility = if (cyclic || current_index > 0) VISIBLE else INVISIBLE
            prev_button.contentDescription = resources.getString(R.string.previous) + " " + title
            main_activity.mainUI!!.testUIButtonsMap.put(test_key + "_PREV", prev_button)

            //ll2.addView(text_view);
            main_activity.mainUI!!.testUIButtonsMap.put(test_key, text_view)

            next_button.setBackgroundColor(Color.TRANSPARENT) // workaround for Android 6 crash!
            //ll2.addView(next_button);
            next_button.text = ">"
            next_button.setTextSize(TypedValue.COMPLEX_UNIT_SP, arrow_text_size_dip)
            next_button.setTypeface(null, Typeface.BOLD)
            next_button.setPadding(padding, padding, padding, padding)
            vg_params = next_button.layoutParams
            vg_params.width = arrow_button_w
            vg_params.height = arrow_button_h
            next_button.layoutParams = vg_params
            next_button.visibility =
                if (cyclic || current_index < supported_options.size - 1) VISIBLE else INVISIBLE
            next_button.contentDescription = resources.getString(R.string.next) + " " + title
            main_activity.mainUI!!.testUIButtonsMap.put(test_key + "_NEXT", next_button)

            prev_button.setOnClickListener {
                val new_index = listener.onClickPrev()
                if (new_index != -1) {
                    setArrayOptionsText(
                        supported_options,
                        title,
                        text_view,
                        title_in_options,
                        title_in_options_first_only,
                        new_index
                    )
                    prev_button.visibility = if (cyclic || new_index > 0) VISIBLE else INVISIBLE
                    next_button.visibility =
                        if (cyclic || new_index < supported_options.size - 1) VISIBLE else INVISIBLE
                }
            }
            next_button.setOnClickListener {
                val new_index = listener.onClickNext()
                if (new_index != -1) {
                    setArrayOptionsText(
                        supported_options,
                        title,
                        text_view,
                        title_in_options,
                        title_in_options_first_only,
                        new_index
                    )
                    prev_button.setVisibility(if (cyclic || new_index > 0) VISIBLE else INVISIBLE)
                    next_button.setVisibility(if (cyclic || new_index < supported_options.size - 1) VISIBLE else INVISIBLE)
                }
            }

            this.addView(ll2)

            Logger.d(
                TAG,
                "addArrayOptionsToPopup time: " + (System.nanoTime() - debug_time)
            )
        }
    }

    companion object {
        private const val TAG = "PopupView"
        const val ALPHA_BUTTON_SELECTED: Float = 1.0f
        const val ALPHA_BUTTON: Float = 0.6f // 0.4f tends to be hard to see in bright light

        private const val button_text_size_dip = 12.0f
        private const val title_text_size_dip = 17.0f
        private const val standard_text_size_dip = 16.0f
        private const val arrow_text_size_dip = 16.0f
        private const val arrow_button_w_dp = 60.0f
        private const val arrow_button_h_dp =
            48.0f // should be at least 48.0 (Google Play's prelaunch warnings)

        fun getButtonOptionString(
            include_prefix: Boolean,
            prefix_string: String?,
            supported_option: String?
        ): String {
            return (if (include_prefix) prefix_string else "") + "\n" + supported_option
        }

        fun createButtonOptions(
            parent: ViewGroup,
            context: Context,
            total_width_dp: Int,
            test_ui_buttons: MutableMap<String?, View?>?,
            supported_options: MutableList<String>?,
            icons_id: Int,
            values_id: Int,
            prefix_string: String,
            include_prefix: Boolean,
            current_value: String?,
            max_buttons_per_row: Int,
            test_key: String?,
            listener: ButtonOptionsPopupListener
        ): MutableList<View?> {
            Logger.d(TAG, "createButtonOptions")
            val buttons: MutableList<View?> = ArrayList<View?>()
            if (supported_options != null) {
                val debug_time = System.nanoTime()
                var ll2 = LinearLayout(context)
                ll2.setOrientation(HORIZONTAL)
                Logger.d(
                    TAG,
                    "addButtonOptionsToPopup time 1: " + (System.nanoTime() - debug_time)
                )
                val icons =
                    if (icons_id != -1) context.getResources().getStringArray(icons_id) else null
                val values =
                    if (values_id != -1) context.getResources().getStringArray(values_id) else null
                Logger.d(
                    TAG,
                    "addButtonOptionsToPopup time 2: " + (System.nanoTime() - debug_time)
                )

                val scale = context.getResources().getDisplayMetrics().density
                val scale_font = context.getResources().getDisplayMetrics().scaledDensity
                Logger.d(
                    TAG,
                    "addButtonOptionsToPopup time 2.04: " + (System.nanoTime() - debug_time)
                )
                var actual_max_per_row = supported_options.size
                if (max_buttons_per_row > 0) actual_max_per_row =
                    min(actual_max_per_row, max_buttons_per_row)
                var button_width_dp = total_width_dp / actual_max_per_row
                var use_scrollview = false
                val min_button_width_dp =
                    48 // needs to be at least 48dp to avoid Google Play pre-launch accessibility report warnings
                if (button_width_dp < min_button_width_dp && max_buttons_per_row == 0) {
                    button_width_dp = min_button_width_dp
                    use_scrollview = true
                }
                var button_width = (button_width_dp * scale + 0.5f).toInt() // convert dps to pixels
                if (MyDebug.LOG) {
                    Logger.d(TAG, "actual_max_per_row: " + actual_max_per_row)
                    Logger.d(TAG, "button_width_dp: " + button_width_dp)
                    Logger.d(TAG, "button_width: " + button_width)
                    Logger.d(TAG, "use_scrollview: " + use_scrollview)
                }

                val on_click_listener: OnClickListener = object : OnClickListener {
                    override fun onClick(v: View) {
                        val supported_option = v.getTag() as String?
                        Logger.d(TAG, "clicked: " + supported_option)
                        listener.onClick(supported_option)
                    }
                }
                var current_view: View? = null
                Logger.d(
                    TAG,
                    "addButtonOptionsToPopup time 2.05: " + (System.nanoTime() - debug_time)
                )

                for (button_indx in supported_options.indices) {
                    val supported_option = supported_options[button_indx]
                    Logger.d(
                        TAG,
                        "addButtonOptionsToPopup time 2.06: " + (System.nanoTime() - debug_time)
                    )
                    Logger.d(TAG, "button_index = $button_indx")

                    if (max_buttons_per_row > 0 && button_indx > 0 && button_indx % max_buttons_per_row == 0) {
                        Logger.d(TAG, "start a new row")
                        // add the previous row
                        // no need to handle use_scrollview, as we don't support scrollviews with multiple rows
                        parent.addView(ll2)
                        ll2 = LinearLayout(context)
                        ll2.setOrientation(HORIZONTAL)

                        val n_remaining = supported_options.size - button_indx
                        Logger.d(TAG, "n_remaining: $n_remaining")
                        if (n_remaining <= max_buttons_per_row) {
                            Logger.d(TAG, "final row")
                            button_width_dp = total_width_dp / n_remaining
                            button_width =
                                (button_width_dp * scale + 0.5f).toInt() // convert dps to pixels
                        }
                    }

                    Logger.d(TAG, "supported_option: $supported_option")
                    var resource = -1
                    Logger.d(
                        TAG,
                        "addButtonOptionsToPopup time 2.08: " + (System.nanoTime() - debug_time)
                    )
                    if (icons != null && values != null) {
                        var index = -1
                        var i = 0
                        while (i < values.size && index == -1) {
                            if (values[i] == supported_option) index = i
                            i++
                        }
                        if (MyDebug.LOG) Logger.d(TAG, "index: $index")
                        if (index != -1) {
                            resource = context.resources.getIdentifier(
                                icons[index],
                                null,
                                context.applicationContext.packageName
                            )
                        }
                    }
                    if (MyDebug.LOG) Logger.d(
                        TAG,
                        "addButtonOptionsToPopup time 2.1: " + (System.nanoTime() - debug_time)
                    )

                    val button_string: String?
                    // hacks for ISO mode ISO_HJR (e.g., on Samsung S5)
                    // also some devices report e.g. "ISO100" etc
                    if (prefix_string.isEmpty()) {
                        button_string = supported_option
                    } else if (prefix_string.equals(
                            "ISO",
                            ignoreCase = true
                        ) && supported_option.length >= 4 && supported_option.substring(0, 4)
                            .equals("ISO_", ignoreCase = true)
                    ) {
                        button_string = getButtonOptionString(
                            include_prefix,
                            prefix_string,
                            supported_option.substring(4)
                        )
                    } else if (prefix_string.equals(
                            "ISO",
                            ignoreCase = true
                        ) && supported_option.length >= 3 && supported_option.substring(0, 3)
                            .equals("ISO", ignoreCase = true)
                    ) {
                        button_string = getButtonOptionString(
                            include_prefix,
                            prefix_string,
                            supported_option.substring(3)
                        )
                    } else {
                        button_string =
                            getButtonOptionString(include_prefix, prefix_string, supported_option)
                    }
                    if (MyDebug.LOG) Logger.d(TAG, "button_string: $button_string")
                    if (MyDebug.LOG) Logger.d(
                        TAG,
                        "addButtonOptionsToPopup time 2.105: " + (System.nanoTime() - debug_time)
                    )
                    val view: View?
                    if (resource != -1) {
                        val image_button = ImageButton(context)
                        if (MyDebug.LOG) Logger.d(
                            TAG,
                            "addButtonOptionsToPopup time 2.11: " + (System.nanoTime() - debug_time)
                        )
                        view = image_button
                        buttons.add(view)
                        ll2.addView(view)
                        if (MyDebug.LOG) Logger.d(
                            TAG,
                            "addButtonOptionsToPopup time 2.12: " + (System.nanoTime() - debug_time)
                        )

                        //image_button.setImageResource(resource);
                        val main_activity = context as MainActivity
                        val bm = main_activity.getPreloadedBitmap(resource)
                        if (bm != null) image_button.setImageBitmap(bm)
                        else {
                            if (MyDebug.LOG) Logger.d(
                                TAG,
                                "failed to find bitmap for resource $resource!"
                            )
                        }
                        if (MyDebug.LOG) Logger.d(
                            TAG,
                            "addButtonOptionsToPopup time 2.13: " + (System.nanoTime() - debug_time)
                        )
                        image_button.scaleType = ScaleType.FIT_CENTER
                        image_button.setBackgroundColor(Color.TRANSPARENT)
                        val padding = (10 * scale + 0.5f).toInt() // convert dps to pixels
                        view.setPadding(padding, padding, padding, padding)
                    } else {
                        @SuppressLint("InflateParams") val button_view =
                            LayoutInflater.from(context).inflate(R.layout.popupview_button, null)
                        val button = button_view.findViewById<Button>(R.id.button)

                        button.setBackgroundColor(Color.TRANSPARENT) // workaround for Android 6 crash! Also looks nicer anyway...
                        view = button
                        buttons.add(view)
                        ll2.addView(view)

                        button.text = button_string
                        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, button_text_size_dip)
                        button.setTextColor(Color.WHITE)
                        // need 0 padding so we have enough room to display text for ISO buttons, when there are 6 ISO settings
                        val padding = (0 * scale + 0.5f).toInt() // convert dps to pixels
                        view.setPadding(padding, padding, padding, padding)
                    }
                    if (MyDebug.LOG) Logger.d(
                        TAG,
                        "addButtonOptionsToPopup time 2.2: " + (System.nanoTime() - debug_time)
                    )

                    val params = view.layoutParams
                    params.width = button_width
                    // be careful of making the height too smaller, as harder to touch buttons; remember that this also affects the
                    // ISO buttons on exposure panel, and not just the main popup!
                    params.height =
                        (55 * (if (resource != -1) scale else scale_font) + 0.5f).toInt() // convert dps to pixels
                    view.layoutParams = params

                    view.contentDescription = button_string
                    if (supported_option == current_value) {
                        setButtonSelected(view, true)
                        current_view = view
                    } else {
                        setButtonSelected(view, false)
                    }
                    if (MyDebug.LOG) Logger.d(
                        TAG,
                        "addButtonOptionsToPopup time 2.3: " + (System.nanoTime() - debug_time)
                    )
                    view.tag = supported_option
                    view.setOnClickListener(on_click_listener)
                    if (MyDebug.LOG) Logger.d(
                        TAG,
                        "addButtonOptionsToPopup time 2.35: " + (System.nanoTime() - debug_time)
                    )
                    if (test_ui_buttons != null) test_ui_buttons.put(
                        test_key + "_" + supported_option,
                        view
                    )
                    if (MyDebug.LOG) {
                        Logger.d(
                            TAG,
                            "addButtonOptionsToPopup time 2.4: " + (System.nanoTime() - debug_time)
                        )
                        Logger.d(
                            TAG,
                            "added to popup_buttons: " + test_key + "_" + supported_option + " view: " + view
                        )
                        if (test_ui_buttons != null) Logger.d(
                            TAG,
                            "test_ui_buttons is now: $test_ui_buttons"
                        )
                    }
                }
                if (MyDebug.LOG) Logger.d(
                    TAG,
                    "addButtonOptionsToPopup time 3: " + (System.nanoTime() - debug_time)
                )
                if (use_scrollview) {
                    if (MyDebug.LOG) Logger.d(TAG, "using scrollview")
                    val total_width =
                        (total_width_dp * scale + 0.5f).toInt() // convert dps to pixels;
                    val scroll = HorizontalScrollView(context)
                    scroll.addView(ll2)
                    run {
                        val params: LayoutParams = LayoutParams(
                            total_width,
                            LayoutParams.WRAP_CONTENT
                        )
                        scroll.setLayoutParams(params)
                    }
                    parent.addView(scroll)
                    if (current_view != null) {
                        // scroll to the selected button
                        val final_current_view: View? = current_view
                        val final_button_width = button_width
                        parent.viewTreeObserver.addOnGlobalLayoutListener(
                            ViewTreeObserver.OnGlobalLayoutListener { // scroll so selected button is centred
                                var jump_x =
                                    final_current_view!!.left - (total_width - final_button_width) / 2
                                // scrollTo should automatically clamp to the bounds of the view, but just in case
                                jump_x = min(jump_x, total_width - 1)
                                if (jump_x > 0) {
                                    scroll.scrollTo(jump_x, 0)
                                }
                            }
                        )
                    }
                } else {
                    if (MyDebug.LOG) Logger.d(TAG, "not using scrollview")
                    parent.addView(ll2)
                }
                if (MyDebug.LOG) Logger.d(
                    TAG,
                    "addButtonOptionsToPopup time 4: " + (System.nanoTime() - debug_time)
                )
            }
            return buttons
        }

        fun setButtonSelected(view: View, selected: Boolean) {
            view.alpha = if (selected) ALPHA_BUTTON_SELECTED else ALPHA_BUTTON
        }
    }
}
