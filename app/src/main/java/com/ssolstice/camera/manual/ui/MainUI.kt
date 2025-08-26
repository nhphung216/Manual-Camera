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
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.AccelerateDecelerateInterpolator
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
    var test_saved_popup_width: Int = 0
    var test_saved_popup_height: Int = 0

    @Volatile
    var test_navigation_gap: Int = 0

    @Volatile
    var test_navigation_gap_landscape: Int = 0

    @Volatile
    var test_navigation_gap_reversed_landscape: Int = 0

    private fun setSeekbarColors() {
        if (MyDebug.LOG) Log.d(TAG, "setSeekbarColors")
        run {
            val progressColor = ColorStateList.valueOf(Color.argb(255, 240, 240, 240))
            val thumbColor = ColorStateList.valueOf(Color.argb(255, 255, 255, 255))

            var seekBar = mainActivity.findViewById<SeekBar>(R.id.zoom_seekbar)
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.findViewById<SeekBar>(R.id.focus_seekbar)
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.findViewById<SeekBar>(R.id.focus_bracketing_target_seekbar)
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.findViewById<SeekBar>(R.id.exposure_seekbar)
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.findViewById<SeekBar>(R.id.iso_seekbar)
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.findViewById<SeekBar>(R.id.exposure_time_seekbar)
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor

            seekBar = mainActivity.findViewById<SeekBar>(R.id.white_balance_seekbar)
            seekBar.progressTintList = progressColor
            seekBar.thumbTintList = thumbColor
        }
    }

    /** Similar view.setRotation(ui_rotation), but achieves this via an animation.
     */
    private fun setViewRotation(view: View, uiRotation: Float) {
        if (!view_rotate_animation) {
            view.rotation = uiRotation
        }
        if (!MainActivity.lockToLandscape) {
            var startRotation = view_rotate_animation_start + uiRotation
            if (startRotation >= 360.0f) startRotation -= 360.0f
            view.rotation = startRotation
        }
        var rotateBy = uiRotation - view.rotation
        if (rotateBy > 181.0f) rotateBy -= 360.0f
        else if (rotateBy < -181.0f) rotateBy += 360.0f
        // view.animate() modifies the view's rotation attribute, so it ends up equivalent to view.setRotation()
        // we use rotationBy() instead of rotation(), so we get the minimal rotation for clockwise vs anti-clockwise
        /*if( main_activity.is_test && Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2 ) {
            // We randomly get a java.lang.ArrayIndexOutOfBoundsException crash when running MainTests suite
            // on Android emulator with Android 4.3, from deep below ViewPropertyAnimator.start().
            // Unclear why this is - I haven't seen this on real devices and can't find out info about it.
            view.setRotation(ui_rotation);
        }
        else*/
        run {
            view.animate().rotationBy(rotateBy)
                .setDuration(view_rotate_animation_duration.toLong())
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }

    fun layoutUI() {
        layoutUI(false)
    }

    fun layoutUIWithRotation(viewRotateAnimationStart: Float) {
        if (MyDebug.LOG) {
            Log.d(TAG, "layoutUIWithRotation: $viewRotateAnimationStart")
        }
        this.view_rotate_animation = true
        this.view_rotate_animation_start = viewRotateAnimationStart
        layoutUI()
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

    private fun layoutUI(popup_container_only: Boolean) {
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            Log.d(TAG, "layoutUI")
            debug_time = System.currentTimeMillis()
        }

        val system_orientation = mainActivity.systemOrientation
        val system_orientation_portrait = system_orientation == SystemOrientation.PORTRAIT
        val system_orientation_reversed_landscape =
            system_orientation == SystemOrientation.REVERSE_LANDSCAPE
        if (MyDebug.LOG) {
            Log.d(TAG, "    system_orientation = $system_orientation")
            Log.d(TAG, "    system_orientation_portrait? $system_orientation_portrait")
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        // we cache the preference_ui_placement to save having to check it in the draw() method
        this.uIPlacement = computeUIPlacement()
        if (MyDebug.LOG) Log.d(TAG, "ui_placement: " + this.uIPlacement)
        val relativeOrientation: Int
        if (MainActivity.lockToLandscape) {
            // new code for orientation fixed to landscape
            // the display orientation should be locked to landscape, but how many degrees is that?
            val rotation = mainActivity.windowManager.defaultDisplay.rotation
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
                else -> {}
            }
            // getRotation is anti-clockwise, but current_orientation is clockwise, so we add rather than subtract
            // relative_orientation is clockwise from landscape-left
            //int relative_orientation = (current_orientation + 360 - degrees) % 360;
            relativeOrientation = (current_orientation + degrees) % 360
            if (MyDebug.LOG) {
                Log.d(TAG, "    current_orientation = $current_orientation")
                Log.d(TAG, "    degrees = $degrees")
                Log.d(TAG, "    relative_orientation = $relativeOrientation")
            }
        } else {
            relativeOrientation = 0
        }
        val ui_rotation = (360 - relativeOrientation) % 360
        mainActivity.preview!!.setUIRotation(ui_rotation)
        // naming convention for variables is for system_orientation==LANDSCAPE, right-handed UI
        var align_left =
            if (system_orientation_portrait) RelativeLayout.ALIGN_TOP else RelativeLayout.ALIGN_LEFT
        var align_right =
            if (system_orientation_portrait) RelativeLayout.ALIGN_BOTTOM else RelativeLayout.ALIGN_RIGHT
        var align_top =
            if (system_orientation_portrait) RelativeLayout.ALIGN_RIGHT else RelativeLayout.ALIGN_TOP
        var align_bottom =
            if (system_orientation_portrait) RelativeLayout.ALIGN_LEFT else RelativeLayout.ALIGN_BOTTOM
        var left_of =
            if (system_orientation_portrait) RelativeLayout.ABOVE else RelativeLayout.LEFT_OF
        var right_of =
            if (system_orientation_portrait) RelativeLayout.BELOW else RelativeLayout.RIGHT_OF
        var above =
            if (system_orientation_portrait) RelativeLayout.RIGHT_OF else RelativeLayout.ABOVE
        var below =
            if (system_orientation_portrait) RelativeLayout.LEFT_OF else RelativeLayout.BELOW
        var ui_independent_left_of = left_of
        var ui_independent_right_of = right_of
        var ui_independent_above = above
        var ui_independent_below = below
        var align_parent_left =
            if (system_orientation_portrait) RelativeLayout.ALIGN_PARENT_TOP else RelativeLayout.ALIGN_PARENT_LEFT
        var align_parent_right =
            if (system_orientation_portrait) RelativeLayout.ALIGN_PARENT_BOTTOM else RelativeLayout.ALIGN_PARENT_RIGHT
        var align_parent_top =
            if (system_orientation_portrait) RelativeLayout.ALIGN_PARENT_RIGHT else RelativeLayout.ALIGN_PARENT_TOP
        var align_parent_bottom =
            if (system_orientation_portrait) RelativeLayout.ALIGN_PARENT_LEFT else RelativeLayout.ALIGN_PARENT_BOTTOM
        val center_horizontal =
            if (system_orientation_portrait) RelativeLayout.CENTER_VERTICAL else RelativeLayout.CENTER_HORIZONTAL
        val center_vertical =
            if (system_orientation_portrait) RelativeLayout.CENTER_HORIZONTAL else RelativeLayout.CENTER_VERTICAL

        var iconpanel_left_of = left_of
        var iconpanel_right_of = right_of
        var iconpanel_above = above
        var iconpanel_below = below
        var iconpanel_align_parent_left = align_parent_left
        var iconpanel_align_parent_right = align_parent_right
        var iconpanel_align_parent_top = align_parent_top
        var iconpanel_align_parent_bottom = align_parent_bottom

        if (system_orientation_reversed_landscape) {
            var temp = align_left
            align_left = align_right
            align_right = temp
            temp = align_top
            align_top = align_bottom
            align_bottom = temp
            temp = left_of
            left_of = right_of
            right_of = temp
            temp = above
            above = below
            below = temp

            ui_independent_left_of = left_of
            ui_independent_right_of = right_of
            ui_independent_above = above
            ui_independent_below = below

            temp = align_parent_left
            align_parent_left = align_parent_right
            align_parent_right = temp
            temp = align_parent_top
            align_parent_top = align_parent_bottom
            align_parent_bottom = temp

            iconpanel_left_of = left_of
            iconpanel_right_of = right_of
            iconpanel_above = above
            iconpanel_below = below
            iconpanel_align_parent_left = align_parent_left
            iconpanel_align_parent_right = align_parent_right
            iconpanel_align_parent_top = align_parent_top
            iconpanel_align_parent_bottom = align_parent_bottom
        }

        if (this.uIPlacement == UIPlacement.UIPLACEMENT_LEFT) {
            var temp = above
            above = below
            below = temp
            temp = align_parent_top
            align_parent_top = align_parent_bottom
            align_parent_bottom = temp
            iconpanel_align_parent_top = align_parent_top
            iconpanel_align_parent_bottom = align_parent_bottom
        } else if (this.uIPlacement == UIPlacement.UIPLACEMENT_TOP) {
            iconpanel_left_of = below
            iconpanel_right_of = above
            iconpanel_above = left_of
            iconpanel_below = right_of
            iconpanel_align_parent_left = align_parent_bottom
            iconpanel_align_parent_right = align_parent_top
            iconpanel_align_parent_top = align_parent_left
            iconpanel_align_parent_bottom = align_parent_right
        }

        val display_size = Point()
        mainActivity.applicationInterface!!.getDisplaySize(display_size, true)
        this.layoutUI_display_w = display_size.x
        this.layoutUI_display_h = display_size.y
        if (MyDebug.LOG) {
            Log.d(TAG, "layoutUI_display_w: $layoutUI_display_w")
            Log.d(TAG, "layoutUI_display_h: $layoutUI_display_h")
        }
        val display_height = min(display_size.x, display_size.y)

        val scale = mainActivity.getResources().displayMetrics.density
        if (MyDebug.LOG) Log.d(TAG, "scale: $scale")

        val navigation_gap = mainActivity.navigationGap
        val navigation_gap_landscape = mainActivity.navigationGapLandscape
        val navigation_gap_reverse_landscape = mainActivity.navigationGapReverseLandscape
        // navigation gaps for UI elements that are aligned to align_parent_bottom (the landscape edge, or reversed landscape edge if left-handed):
        this.navigation_gap_landscape_align_parent_bottom = navigation_gap_landscape
        this.navigation_gap_reverse_landscape_align_parent_bottom = navigation_gap_reverse_landscape
        if (this.uIPlacement == UIPlacement.UIPLACEMENT_LEFT) {
            navigation_gap_landscape_align_parent_bottom = 0
        } else {
            navigation_gap_reverse_landscape_align_parent_bottom = 0
        }
        var gallery_navigation_gap = navigation_gap

        var gallery_top_gap = 0
        run {
            // Leave space for the Android 12+ camera privacy indicator, as gallery icon would
            // otherwise overlap when in landscape orientation.
            // In theory we should use WindowInsets.getPrivacyIndicatorBounds() for this, but it seems
            // to give a much larger value when required (leaving to a much larger gap), as well as
            // obviously changing depending on orientation - but whilst this is only an issue for
            // landscape orientation, it looks better to keep the position consistent for any
            // orientation (otherwise the icons jump about when changing orientation, which looks
            // especially bad for UIPLACEMENT_RIGHT.
            // Not needed for UIPLACEMENT_LEFT - although still adjust the right hand side margin
            // for consistency.
            // We do for all Android versions for consistency (avoids testing overhead due to
            // different behaviour on different Android versions).
            if (this.uIPlacement != UIPlacement.UIPLACEMENT_LEFT) {
                // if we did want to do this for UIPLACEMENT_LEFT for consistency, it'd be the
                // "bottom" margin we need to change.
                gallery_top_gap =
                    (privacy_indicator_gap_dp * scale + 0.5f).toInt() // convert dps to pixels
            }
            val privacy_indicator_gap =
                (privacy_indicator_gap_dp * scale + 0.5f).toInt() // convert dps to pixels
            gallery_navigation_gap += privacy_indicator_gap
        }
        test_navigation_gap = navigation_gap
        test_navigation_gap_landscape = navigation_gap_landscape
        test_navigation_gap_reversed_landscape = navigation_gap_reverse_landscape
        if (MyDebug.LOG) {
            Log.d(TAG, "navigation_gap: $navigation_gap")
            Log.d(TAG, "gallery_navigation_gap: $gallery_navigation_gap")
        }

        if (!popup_container_only) {
            // reset:
            this.topIcon = null

            // we use a dummy view, so that the GUI buttons keep their positioning even if the Settings button is hidden (visibility set to View.GONE)
            var view = mainActivity.findViewById<View>(R.id.gui_anchor)
            var layoutParams = view.layoutParams as RelativeLayout.LayoutParams
            layoutParams.addRule(iconpanel_align_parent_left, 0)
            layoutParams.addRule(iconpanel_align_parent_right, RelativeLayout.TRUE)
            layoutParams.addRule(iconpanel_align_parent_top, RelativeLayout.TRUE)
            layoutParams.addRule(iconpanel_align_parent_bottom, 0)
            layoutParams.addRule(iconpanel_above, 0)
            layoutParams.addRule(iconpanel_below, 0)
            layoutParams.addRule(iconpanel_left_of, 0)
            layoutParams.addRule(iconpanel_right_of, 0)
            view.layoutParams = layoutParams
            setViewRotation(view, ui_rotation.toFloat())
            var previousView = view

            val buttonsPermanent: MutableList<View> = ArrayList<View>()
//            if (this.uIPlacement == UIPlacement.UIPLACEMENT_TOP) {
//                // not part of the icon panel in TOP mode
//                view = mainActivity.findViewById<View>(R.id.gallery)
//                layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//                layoutParams.addRule(align_parent_left, 0)
//                layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
//                layoutParams.addRule(align_parent_top, RelativeLayout.TRUE)
//                layoutParams.addRule(align_parent_bottom, 0)
//                layoutParams.addRule(above, 0)
//                layoutParams.addRule(below, 0)
//                layoutParams.addRule(left_of, 0)
//                layoutParams.addRule(right_of, 0)
//                setMarginsForSystemUI(layoutParams, 0, gallery_top_gap, gallery_navigation_gap, 0)
//                view.layoutParams = layoutParams
//                setViewRotation(view, ui_rotation.toFloat())
//            } else {
//                buttonsPermanent.add(mainActivity.findViewById(R.id.gallery))
//            }
            buttonsPermanent.add(mainActivity.findViewById(R.id.settings))
//            buttonsPermanent.add(mainActivity.findViewById(R.id.popup))
            buttonsPermanent.add(mainActivity.findViewById(R.id.exposure))
            buttonsPermanent.add(mainActivity.findViewById(R.id.exposure_lock))
            buttonsPermanent.add(mainActivity.findViewById(R.id.white_balance_lock))
            buttonsPermanent.add(mainActivity.findViewById(R.id.cycle_raw))
            buttonsPermanent.add(mainActivity.findViewById(R.id.store_location))
            buttonsPermanent.add(mainActivity.findViewById(R.id.text_stamp))
            buttonsPermanent.add(mainActivity.findViewById(R.id.stamp))
            buttonsPermanent.add(mainActivity.findViewById(R.id.focus_peaking))
            buttonsPermanent.add(mainActivity.findViewById(R.id.auto_level))
            buttonsPermanent.add(mainActivity.findViewById(R.id.cycle_flash))
            buttonsPermanent.add(mainActivity.findViewById(R.id.face_detection))
            buttonsPermanent.add(mainActivity.findViewById(R.id.audio_control))
            buttonsPermanent.add(mainActivity.findViewById(R.id.kraken_icon))

            val buttonsAll: MutableList<View> = ArrayList<View>(buttonsPermanent)
            // icons which only sometimes show on the icon panel:
            buttonsAll.add(mainActivity.findViewById(R.id.trash))
            buttonsAll.add(mainActivity.findViewById(R.id.share))

            for (v in buttonsAll) {
                layoutParams = v.layoutParams as RelativeLayout.LayoutParams
                layoutParams.addRule(iconpanel_align_parent_left, 0)
                layoutParams.addRule(iconpanel_align_parent_right, 0)
                layoutParams.addRule(iconpanel_align_parent_top, RelativeLayout.TRUE)
                layoutParams.addRule(iconpanel_align_parent_bottom, 0)
                layoutParams.addRule(iconpanel_above, 0)
                layoutParams.addRule(iconpanel_below, 0)
                layoutParams.addRule(iconpanel_left_of, previousView.getId())
                layoutParams.addRule(iconpanel_right_of, 0)
                v.layoutParams = layoutParams
                setViewRotation(v, ui_rotation.toFloat())
                previousView = v
            }

            var buttonSize =
                mainActivity.getResources().getDimensionPixelSize(R.dimen.onscreen_button_size)
            if (this.uIPlacement == UIPlacement.UIPLACEMENT_TOP) {
                // need to dynamically lay out the permanent icons

                var count = 0
                var first_visible_view: View? = null
                var last_visible_view: View? = null
                for (this_view in buttonsPermanent) {
                    if (this_view.visibility == View.VISIBLE) {
                        if (first_visible_view == null) first_visible_view = this_view
                        last_visible_view = this_view
                        count++
                    }
                }
                //count = 10; // test
                if (MyDebug.LOG) {
                    Log.d(TAG, "count: $count")
                    Log.d(TAG, "display_height: $display_height")
                }
                if (count > 0) {
                    val totalButtonSize = count * buttonSize
                    var margin = 0
                    if (totalButtonSize > display_height) {
                        if (MyDebug.LOG) Log.d(TAG, "need to reduce button size")
                        buttonSize = display_height / count
                    } else {
                        if (MyDebug.LOG) Log.d(TAG, "need to increase margin")
                        if (count > 1) margin = (display_height - totalButtonSize) / (count - 1)
                    }
                    if (MyDebug.LOG) {
                        Log.d(TAG, "button_size: $buttonSize")
                        Log.d(TAG, "total_button_size: $totalButtonSize")
                        Log.d(TAG, "margin: $margin")
                    }
                    for (v in buttonsPermanent) {
                        if (v.visibility == View.VISIBLE) {
                            if (MyDebug.LOG) {
                                Log.d(
                                    TAG,
                                    "set view layout for: " + v.contentDescription
                                )
                                if (v === first_visible_view) {
                                    Log.d(TAG, "    first visible view")
                                }
                            }
                            //this_view.setPadding(0, margin/2, 0, margin/2);
                            layoutParams = v.layoutParams as RelativeLayout.LayoutParams
                            // be careful if we change how the margins are laid out: it looks nicer when only the settings icon
                            // is displayed (when taking a photo) if it is still shown left-most, rather than centred; also
                            // needed for "pause preview" trash/icons to be shown properly (test by rotating the phone to update
                            // the layout)
                            val marginFirst =
                                if (v === first_visible_view) navigation_gap_reverse_landscape else margin / 2
                            val marginLast =
                                if (v === last_visible_view) navigation_gap_landscape else margin / 2
                            // avoid risk of privacy dot appearing on top of icon - in practice this is only a risk when in
                            // reverse landscape mode, but we apply in all orientations to avoid icons jumping about;
                            // similarly, as noted above we use a hardcoded dp rather than
                            // WindowInsets.getPrivacyIndicatorBounds(), as we want the icons to stay in the same location even as
                            // the device is rotated
                            val privacyGapLeft =
                                (12 * scale + 0.5f).toInt() // convert dps to pixels
                            setMarginsForSystemUI(
                                layoutParams,
                                privacyGapLeft,
                                marginFirst,
                                0,
                                marginLast
                            )
                            layoutParams.width = buttonSize
                            layoutParams.height = buttonSize
                            v.layoutParams = layoutParams
                        }
                    }
                    this.topIcon = first_visible_view
                }
            } else {
                // need to reset size/margins to their default
                // except for gallery, which still needs its margins set for navigation gap! (and we
                // shouldn't change it's size, which isn't necessarily button_size)
                // other icons still needs margins set for navigation_gap_landscape and navigation_gap_reverse_landscape
//                view = mainActivity.findViewById<View>(R.id.gallery)
//                layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//                setMarginsForSystemUI(
//                    layoutParams,
//                    0,
//                    max(gallery_top_gap, navigation_gap_reverse_landscape),
//                    gallery_navigation_gap,
//                    navigation_gap_landscape
//                )
//                view.layoutParams = layoutParams
//                for (v in buttonsPermanent) {
//                    if (v !== view) {
//                        layoutParams = v.layoutParams as RelativeLayout.LayoutParams
//                        setMarginsForSystemUI(
//                            layoutParams,
//                            0,
//                            navigation_gap_reverse_landscape,
//                            0,
//                            navigation_gap_landscape
//                        )
//                        layoutParams.width = buttonSize
//                        layoutParams.height = buttonSize
//                        v.layoutParams = layoutParams
//                    }
//                }
            }

            // end icon panel
//            view = mainActivity.findViewById<View>(R.id.take_photo)
//            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//            layoutParams.addRule(align_parent_left, 0)
//            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
//            layoutParams.addRule(align_parent_top, 0)
//            layoutParams.addRule(align_parent_bottom, 0)
//            layoutParams.addRule(center_vertical, RelativeLayout.TRUE)
//            layoutParams.addRule(center_horizontal, 0)
//            setMarginsForSystemUI(layoutParams, 0, 0, navigation_gap, 0)
//            view.layoutParams = layoutParams
//            setViewRotation(view, ui_rotation.toFloat())
//
//            view = mainActivity.findViewById<View>(R.id.switch_camera)
//            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//            layoutParams.addRule(align_parent_left, 0)
//            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
//            layoutParams.addRule(align_parent_top, 0)
//            layoutParams.addRule(align_parent_bottom, 0)
//            layoutParams.addRule(ui_independent_above, R.id.take_photo)
//            layoutParams.addRule(ui_independent_below, 0)
//            layoutParams.addRule(ui_independent_left_of, 0)
//            layoutParams.addRule(ui_independent_right_of, 0)
//            setMarginsForSystemUI(layoutParams, 0, 0, navigation_gap, 0)
//            view.layoutParams = layoutParams
//            setViewRotation(view, ui_rotation.toFloat())
//
//            view = mainActivity.findViewById<View>(R.id.switch_multi_camera)
//            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//            layoutParams.addRule(ui_independent_above, 0)
//            layoutParams.addRule(ui_independent_below, 0)
//            layoutParams.addRule(ui_independent_left_of, R.id.switch_camera)
//            layoutParams.addRule(ui_independent_right_of, 0)
//            layoutParams.addRule(align_top, R.id.switch_camera)
//            layoutParams.addRule(align_bottom, R.id.switch_camera)
//            layoutParams.addRule(align_left, 0)
//            layoutParams.addRule(align_right, 0)
//            run {
//                val margin = (5 * scale + 0.5f).toInt() // convert dps to pixels
//                setMarginsForSystemUI(layoutParams, 0, 0, margin, 0)
//            }
//            view.layoutParams = layoutParams
//            setViewRotation(view, ui_rotation.toFloat())
//
//            view = mainActivity.findViewById<View>(R.id.pause_video)
//            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//            layoutParams.addRule(align_parent_left, 0)
//            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
//            layoutParams.addRule(align_parent_top, 0)
//            layoutParams.addRule(align_parent_bottom, 0)
//            layoutParams.addRule(ui_independent_above, R.id.take_photo)
//            layoutParams.addRule(ui_independent_below, 0)
//            layoutParams.addRule(ui_independent_left_of, 0)
//            layoutParams.addRule(ui_independent_right_of, 0)
//            setMarginsForSystemUI(layoutParams, 0, 0, navigation_gap, 0)
//            view.layoutParams = layoutParams
//            setViewRotation(view, ui_rotation.toFloat())
//
//            view = mainActivity.findViewById<View>(R.id.cancel_panorama)
//            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//            layoutParams.addRule(align_parent_left, 0)
//            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
//            layoutParams.addRule(align_parent_top, 0)
//            layoutParams.addRule(align_parent_bottom, 0)
//            layoutParams.addRule(above, R.id.take_photo)
//            layoutParams.addRule(below, 0)
//            layoutParams.addRule(left_of, 0)
//            layoutParams.addRule(right_of, 0)
//            setMarginsForSystemUI(layoutParams, 0, 0, navigation_gap, 0)
//            view.layoutParams = layoutParams
//            setViewRotation(view, ui_rotation.toFloat())
//
//            view = mainActivity.findViewById<View>(R.id.switch_video)
//            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//            layoutParams.addRule(align_parent_left, 0)
//            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
//            layoutParams.addRule(align_parent_top, 0)
//            layoutParams.addRule(align_parent_bottom, 0)
//            layoutParams.addRule(ui_independent_above, 0)
//            layoutParams.addRule(ui_independent_below, R.id.take_photo)
//            layoutParams.addRule(ui_independent_left_of, 0)
//            layoutParams.addRule(ui_independent_right_of, 0)
//            setMarginsForSystemUI(layoutParams, 0, 0, navigation_gap, 0)
//            view.layoutParams = layoutParams
//            setViewRotation(view, ui_rotation.toFloat())
//
//            view = mainActivity.findViewById<View>(R.id.take_photo_when_video_recording)
//            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
//            layoutParams.addRule(align_parent_left, 0)
//            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
//            layoutParams.addRule(align_parent_top, 0)
//            layoutParams.addRule(align_parent_bottom, 0)
//            layoutParams.addRule(ui_independent_above, 0)
//            layoutParams.addRule(ui_independent_below, R.id.take_photo)
//            layoutParams.addRule(ui_independent_left_of, 0)
//            layoutParams.addRule(ui_independent_right_of, 0)
//            setMarginsForSystemUI(layoutParams, 0, 0, navigation_gap, 0)
//            view.layoutParams = layoutParams
//            setViewRotation(view, ui_rotation.toFloat())

            view = mainActivity.findViewById<View>(R.id.zoom)
            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
            layoutParams.addRule(align_parent_left, 0)
            layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
            layoutParams.addRule(align_parent_top, 0)
            layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE)
            view.layoutParams = layoutParams
            setFixedRotation(
                mainActivity.findViewById(R.id.zoom),
                0,
                navigation_gap_reverse_landscape_align_parent_bottom,
                navigation_gap,
                navigation_gap_landscape_align_parent_bottom
            )
            view.rotation =
                view.rotation + 180.0f // should always match the zoom_seekbar, so that zoom in and out are in the same directions

            view = mainActivity.findViewById<View>(R.id.zoom_seekbar)
            layoutParams = view.layoutParams as RelativeLayout.LayoutParams
            // if we are showing the zoom control, then align next to that; otherwise have it aligned close to the edge of screen
            if (sharedPreferences.getBoolean(PreferenceKeys.ShowZoomControlsPreferenceKey, false)) {
                layoutParams.addRule(align_parent_left, 0)
                layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
                layoutParams.addRule(align_parent_top, 0)
                layoutParams.addRule(align_parent_bottom, 0)
                layoutParams.addRule(above, R.id.zoom)
                layoutParams.addRule(below, 0)
                layoutParams.addRule(left_of, 0)
                layoutParams.addRule(right_of, 0)
                // margins set below in setFixedRotation()
            } else {
                layoutParams.addRule(align_parent_left, 0)
                layoutParams.addRule(align_parent_right, RelativeLayout.TRUE)
                layoutParams.addRule(align_parent_top, 0)
                layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE)
                // margins set below in setFixedRotation()
                // need to clear the others, in case we turn zoom controls on/off
                layoutParams.addRule(above, 0)
                layoutParams.addRule(below, 0)
                layoutParams.addRule(left_of, 0)
                layoutParams.addRule(right_of, 0)
            }
            view.layoutParams = layoutParams
            val margin = (20 * scale + 0.5f).toInt() // convert dps to pixels
            if (sharedPreferences.getBoolean(PreferenceKeys.ShowZoomControlsPreferenceKey, false)) {
                // if zoom control is being shown, we don't need to offset the zoom seekbar from landscape navigation gaps
                setFixedRotation(
                    mainActivity.findViewById(R.id.zoom_seekbar),
                    0,
                    0,
                    margin + navigation_gap,
                    0
                )
            } else {
                setFixedRotation(
                    mainActivity.findViewById(R.id.zoom_seekbar),
                    0,
                    navigation_gap_reverse_landscape_align_parent_bottom,
                    margin + navigation_gap,
                    navigation_gap_landscape_align_parent_bottom
                )
            }

            view = mainActivity.findViewById<View>(R.id.focus_seekbar)
            layoutParams = view.getLayoutParams() as RelativeLayout.LayoutParams
            layoutParams.addRule(left_of, R.id.zoom_seekbar)
            layoutParams.addRule(right_of, 0)
            layoutParams.addRule(above, 0)
            layoutParams.addRule(below, 0)
            layoutParams.addRule(align_parent_top, 0)
            layoutParams.addRule(align_parent_bottom, RelativeLayout.TRUE)
            layoutParams.addRule(align_parent_left, 0)
            layoutParams.addRule(align_parent_right, 0)
            view.setLayoutParams(layoutParams)

            view = mainActivity.findViewById<View>(R.id.focus_bracketing_target_seekbar)
            layoutParams = view.getLayoutParams() as RelativeLayout.LayoutParams
            layoutParams.addRule(left_of, R.id.zoom_seekbar)
            layoutParams.addRule(right_of, 0)
            layoutParams.addRule(above, R.id.focus_seekbar)
            layoutParams.addRule(below, 0)
            view.setLayoutParams(layoutParams)

            setFocusSeekbarsRotation()
        }

        if (!popup_container_only) {
            // set seekbar info
            var width_dp: Int
            if (!system_orientation_portrait && (ui_rotation == 0 || ui_rotation == 180)) {
                // landscape
                width_dp = 350
            } else {
                // portrait
                width_dp = 250
                // prevent being too large on smaller devices (e.g., Galaxy Nexus or smaller)
                val max_width_dp = getMaxHeightDp(true)
                if (width_dp > max_width_dp) width_dp = max_width_dp
            }
            if (MyDebug.LOG) Log.d(TAG, "width_dp: " + width_dp)
            val height_dp = 50
            val width_pixels = (width_dp * scale + 0.5f).toInt() // convert dps to pixels
            val height_pixels = (height_dp * scale + 0.5f).toInt() // convert dps to pixels

            var view = mainActivity.findViewById<View>(R.id.sliders_container)
            setViewRotation(view, ui_rotation.toFloat())
            view.setTranslationX(0.0f)
            view.setTranslationY(0.0f)

            if (system_orientation_portrait || ui_rotation == 90 || ui_rotation == 270) {
                // portrait
                if (system_orientation_portrait) view.setTranslationY((2 * height_pixels).toFloat())
                else view.setTranslationX((2 * height_pixels).toFloat())
            } else if (ui_rotation == 0) {
                // landscape
                view.setTranslationY(height_pixels.toFloat())
            } else {
                // upside-down landscape
                view.setTranslationY((-1 * height_pixels).toFloat())
            }

            view = mainActivity.findViewById<View>(R.id.exposure_seekbar)
            var lp = view.getLayoutParams() as RelativeLayout.LayoutParams
            lp.width = width_pixels
            lp.height = height_pixels
            view.layoutParams = lp

            view = mainActivity.findViewById<View>(R.id.exposure_seekbar_zoom)
            view.alpha = 0.5f

            view = mainActivity.findViewById<View>(R.id.iso_seekbar)
            lp = view.getLayoutParams() as RelativeLayout.LayoutParams
            lp.width = width_pixels
            lp.height = height_pixels
            view.setLayoutParams(lp)

            view = mainActivity.findViewById<View>(R.id.exposure_time_seekbar)
            lp = view.getLayoutParams() as RelativeLayout.LayoutParams
            lp.width = width_pixels
            lp.height = height_pixels
            view.setLayoutParams(lp)

            view = mainActivity.findViewById<View>(R.id.white_balance_seekbar)
            lp = view.getLayoutParams() as RelativeLayout.LayoutParams
            lp.width = width_pixels
            lp.height = height_pixels
            view.setLayoutParams(lp)
        }

//        if (popupIsOpen()) {
//            val view = mainActivity.findViewById<View>(R.id.popup_container)
//            val layoutParams = view.getLayoutParams() as RelativeLayout.LayoutParams
//            if (this.uIPlacement == UIPlacement.UIPLACEMENT_TOP) {
//                layoutParams.addRule(align_right, 0)
//                layoutParams.addRule(align_bottom, 0)
//                layoutParams.addRule(align_left, 0)
//                layoutParams.addRule(align_top, 0)
//                layoutParams.addRule(above, 0)
//                layoutParams.addRule(below, 0)
//                layoutParams.addRule(left_of, 0)
//                layoutParams.addRule(right_of, R.id.popup)
//                layoutParams.addRule(
//                    align_parent_top,
//                    if (system_orientation_portrait) 0 else RelativeLayout.TRUE
//                )
//                layoutParams.addRule(
//                    align_parent_bottom,
//                    if (system_orientation_portrait) 0 else RelativeLayout.TRUE
//                )
//                layoutParams.addRule(align_parent_left, 0)
//                layoutParams.addRule(align_parent_right, 0)
//            } else {
//                layoutParams.addRule(align_right, R.id.popup)
//                layoutParams.addRule(align_bottom, 0)
//                layoutParams.addRule(align_left, 0)
//                layoutParams.addRule(align_top, 0)
//                layoutParams.addRule(above, 0)
//                layoutParams.addRule(below, R.id.popup)
//                layoutParams.addRule(left_of, 0)
//                layoutParams.addRule(right_of, 0)
//                layoutParams.addRule(align_parent_top, 0)
//                layoutParams.addRule(
//                    align_parent_bottom,
//                    if (system_orientation_portrait) 0 else RelativeLayout.TRUE
//                )
//                layoutParams.addRule(align_parent_left, 0)
//                layoutParams.addRule(align_parent_right, 0)
//            }
//            if (system_orientation_portrait) {
//                // limit height so doesn't take up full height of screen
//                layoutParams.height = display_height
//            }
//            view.setLayoutParams(layoutParams)
//
//            //setPopupViewRotation(ui_rotation, display_height);
//            view.getViewTreeObserver().addOnGlobalLayoutListener(
//                object : OnGlobalLayoutListener {
//                    override fun onGlobalLayout() {
//                        if (MyDebug.LOG) Log.d(TAG, "onGlobalLayout()")
//                        // We need to call setPopupViewRotation after the above layout param changes
//                        // have taken effect, otherwise we can have problems due to popup_height being incorrect.
//                        // Example bugs:
//                        // Left-handed UI, portrait: Restart and open popup, it doesn't appear until device is rotated.
//                        // Top UI, reverse-portrait: Restart and open popup, it appears in wrong location.
//                        // Top UI, reverse-landscape: Restart and open popup, it appears in wrong location.
//                        setPopupViewRotation(ui_rotation, display_height)
//
//                        // stop listening - only want to call this once!
//                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this)
//                    }
//                }
//            )
//        }

        if (!popup_container_only) {
            setTakePhotoIcon()
        }

        if (MyDebug.LOG) {
            Log.d(TAG, "layoutUI: total time: " + (System.currentTimeMillis() - debug_time))
        }
    }

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
        view.setRotation(rotation.toFloat())
        // set margins due to rotation
        val layoutParams = view.getLayoutParams() as RelativeLayout.LayoutParams
        if (system_orientation == SystemOrientation.PORTRAIT) {
            val diff = (layoutParams.width - layoutParams.height) / 2
            if (MyDebug.LOG) Log.d(TAG, "diff: " + diff)
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
        view.setLayoutParams(layoutParams)
    }

    fun setFocusSeekbarsRotation() {
        setFixedRotation(
            mainActivity.findViewById(R.id.focus_seekbar),
            0,
            navigation_gap_reverse_landscape_align_parent_bottom,
            0,
            navigation_gap_landscape_align_parent_bottom
        )
        // don't need to set margins for navigation gap landscape for focus_bracketing_target_seekbar, as it sits above the source focus_seekbar
        setFixedRotation(
            mainActivity.findViewById(R.id.focus_bracketing_target_seekbar),
            0,
            0,
            0,
            0
        )
    }

    private fun setPopupViewRotation(ui_rotation: Int, display_height: Int) {
        if (MyDebug.LOG) Log.d(TAG, "setPopupViewRotation")
        val view = mainActivity.findViewById<View>(R.id.popup_container)
        setViewRotation(view, ui_rotation.toFloat())
        // reset:
        view.translationX = 0.0f
        view.translationY = 0.0f

        val popup_width = view.width
        val popup_height = view.height
        test_saved_popup_width = popup_width
        test_saved_popup_height = popup_height
        if (MyDebug.LOG) {
            Log.d(TAG, "popup_width: $popup_width")
            Log.d(TAG, "popup_height: $popup_height")
            if (this.popupView != null) Log.d(
                TAG,
                "popup total width: " + popupView!!.totalWidth
            )
        }
        if (this.popupView != null && popup_width > popupView!!.totalWidth * 1.2) {
            // This is a workaround for the rare but annoying bug where the popup window is too large
            // (and appears partially off-screen). Unfortunately have been unable to fix - and trying
            // to force the popup container to have a particular width just means some of the contents
            // (e.g., Timer) are missing. But at least stop caching it, so that reopening the popup
            // should fix it, rather than having to restart or pause/resume ManualCamera.
            // Also note, normally we should expect popup_width == popup_view.getTotalWidth(), but
            // have put a fudge factor of 1.2 just in case it's normally slightly larger on some
            // devices.
            Log.e(TAG, "### popup view is too big?!")
            force_destroy_popup = true
            /*popup_width = popup_view.getTotalWidth();
			ViewGroup.LayoutParams params = new RelativeLayout.LayoutParams(
					popup_width,
					RelativeLayout.LayoutParams.WRAP_CONTENT);
			view.setLayoutParams(params);*/
        } else {
            force_destroy_popup = false
        }

        if (ui_rotation == 0 || ui_rotation == 180) {
            view.setPivotX(popup_width / 2.0f)
            view.setPivotY(popup_height / 2.0f)
        } else if (this.uIPlacement == UIPlacement.UIPLACEMENT_TOP) {
            view.setPivotX(0.0f)
            view.setPivotY(0.0f)
            if (ui_rotation == 90) {
                view.setTranslationX(popup_height.toFloat())
            } else if (ui_rotation == 270) {
                view.setTranslationY(display_height.toFloat())
            }
        } else {
            view.setPivotX(popup_width.toFloat())
            view.setPivotY(if (this.uIPlacement == UIPlacement.UIPLACEMENT_RIGHT) 0.0f else popup_height.toFloat())
            if (this.uIPlacement == UIPlacement.UIPLACEMENT_RIGHT) {
                if (ui_rotation == 90) {
                    view.setTranslationY(popup_width.toFloat())
                } else if (ui_rotation == 270) view.setTranslationX(-popup_height.toFloat())
            } else {
                if (ui_rotation == 90) view.setTranslationX(-popup_height.toFloat())
                else if (ui_rotation == 270) view.setTranslationY(-popup_width.toFloat())
            }
        }
    }

    /** Set icons for taking photos vs videos.
     * Also handles content descriptions for the take photo button and switch video button.
     */
    fun setTakePhotoIcon() {
        if (MyDebug.LOG) {
            Log.d(TAG, "setTakePhotoIcon()")
        }
        if (mainActivity.preview != null) {
            var view = mainActivity.findViewById<ImageButton>(R.id.take_photo)
            var resource: Int
            val contentDescription: Int
            val switchVideoContentDescription: Int

            if (mainActivity.preview!!.isVideo) {
                cameraViewModel.setPhotoMode(false)
                if (MyDebug.LOG) {
                    Log.d(TAG, "set icon to video")
                }
                resource = if (mainActivity.preview!!.isVideoRecording) {
                    R.drawable.take_video_recording
                } else {
                    R.drawable.take_video_selector
                }
                contentDescription = if (mainActivity.preview!!.isVideoRecording) {
                    R.string.stop_video
                } else {
                    R.string.start_video
                }
                switchVideoContentDescription = R.string.switch_to_photo
            } else if (mainActivity.applicationInterface!!.getPhotoMode() == MyApplicationInterface.PhotoMode.Panorama &&
                mainActivity.applicationInterface!!.gyroSensor.isRecording
            ) {
                if (MyDebug.LOG) {
                    Log.d(TAG, "set icon to recording panorama")
                }
                resource = R.drawable.baseline_check_white_48
                contentDescription = R.string.finish_panorama
                switchVideoContentDescription = R.string.switch_to_video
            } else {
                if (MyDebug.LOG) {
                    Log.d(TAG, "set icon to photo")
                }
                resource = R.drawable.take_photo_selector
                contentDescription = R.string.take_photo
                switchVideoContentDescription = R.string.switch_to_video
            }
            view.setImageResource(resource)
            view.contentDescription = mainActivity.getResources().getString(contentDescription)
            view.tag = resource // for testing

//            view = mainActivity.findViewById(R.id.switch_video)
//            view.contentDescription =
//                mainActivity.getResources().getString(switchVideoContentDescription)

            resource = if (mainActivity.preview!!.isVideo) {
                cameraViewModel.setPhotoMode(false)
                R.drawable.take_photo
            } else {
                cameraViewModel.setPhotoMode(true)
                R.drawable.take_video
            }
            view.setImageResource(resource)
            view.tag = resource // for testing

            cameraViewModel.setVideoRecording(mainActivity.preview!!.isVideoRecording)
        }
    }

    /** Set content description for switch camera button.
     */
    fun setSwitchCameraContentDescription() {
        if (MyDebug.LOG) Log.d(TAG, "setSwitchCameraContentDescription()")
//        if (mainActivity.preview != null && mainActivity.preview!!.canSwitchCamera()) {
//            val view = mainActivity.findViewById<ImageButton>(R.id.switch_camera)
//            val cameraId = mainActivity.nextCameraId
//            val contentDescription =
//                when (mainActivity.preview!!.cameraControllerManager.getFacing(cameraId)) {
//                    Facing.FACING_FRONT -> R.string.switch_to_front_camera
//                    Facing.FACING_BACK -> R.string.switch_to_back_camera
//                    Facing.FACING_EXTERNAL -> R.string.switch_to_external_camera
//                    else -> R.string.switch_to_unknown_camera
//                }
//            if (MyDebug.LOG) Log.d(
//                TAG,
//                "content_description: " + mainActivity.getResources()
//                    .getString(contentDescription)
//            )
//            view.contentDescription = mainActivity.getResources().getString(contentDescription)
//        }
    }

    /** Set content description for pause video button.
     */
    fun setPauseVideoContentDescription() {
        if (MyDebug.LOG) Log.d(TAG, "setPauseVideoContentDescription()")
        val pauseVideoButton = mainActivity.findViewById<ImageButton>(R.id.pause_video)
        val contentDescription: Int
        if (mainActivity.preview!!.isVideoRecordingPaused) {
            contentDescription = R.string.resume_video
            pauseVideoButton.setImageResource(R.drawable.ic_play_circle_outline_white_48dp)
            cameraViewModel.setVideoRecordingPaused(true)
        } else {
            contentDescription = R.string.pause_video
            pauseVideoButton.setImageResource(R.drawable.ic_pause_circle_outline_white_48dp)
            cameraViewModel.setVideoRecordingPaused(false)
        }
        if (MyDebug.LOG) Log.d(
            TAG,
            "content_description: " + mainActivity.getResources().getString(contentDescription)
        )
        pauseVideoButton.contentDescription =
            mainActivity.getResources().getString(contentDescription)
    }

    fun updateRemoteConnectionIcon() {
        val remoteConnectedIcon = mainActivity.findViewById<View>(R.id.kraken_icon)
        if (mainActivity.getBluetoothRemoteControl().remoteConnected()) {
            if (MyDebug.LOG) Log.d(TAG, "Remote control connected")
            remoteConnectedIcon.visibility = View.VISIBLE
        } else {
            if (MyDebug.LOG) Log.d(TAG, "Remote control DISconnected")
            remoteConnectedIcon.visibility = View.GONE
        }
    }

    // ParameterCanBeLocal warning suppressed as it's incorrect here! (Or
    // possibly it's due to effect of MainActivity.lock_to_landscape always
    // being false.)
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
                if (MyDebug.LOG) {
                    Log.d(TAG, "current_orientation is now: $current_orientation")
                }
                view_rotate_animation = true
                layoutUI()
                view_rotate_animation = false

                // Call DrawPreview.updateSettings() so that we reset calculations that depend on
                // getLocationOnScreen() - since the result is affected by a View's rotation, we need
                // to recompute - this also means we need to delay slightly until after the rotation
                // animation is complete.
                // To reproduce issues, rotate from upside-down-landscape to portrait, and observe
                // the info-text placement (when using icons-along-top), or with on-screen angle
                // displayed when in 16:9 preview.
                // Potentially we could use Animation.setAnimationListener(), but we set a separate
                // animation for every icon.
                // Note, this seems to be unneeded due to the fix in DrawPreview for
                // "getRotation() == 180.0f", but good to clear the cached values (e.g., in case we
                // compute them during when the icons are being rotated).
                val handler = Handler()
                handler.postDelayed({
                    if (MyDebug.LOG) Log.d(TAG, "onOrientationChanged->postDelayed()")
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
        if (MyDebug.LOG) Log.d(TAG, "setImmersiveMode: $immersiveMode")
        this.immersive_mode = immersiveMode
        mainActivity.runOnUiThread {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
            // if going into immersive mode, the we should set GONE the ones that are set GONE in showGUI(false)
            //final int visibility_gone = immersive_mode ? View.GONE : View.VISIBLE;
            val visibility = if (immersiveMode) View.GONE else View.VISIBLE

            if (MyDebug.LOG) Log.d(TAG, "setImmersiveMode: set visibility: $visibility")
            // n.b., don't hide share and trash buttons, as they require immediate user input for us to continue
            val exposureButton = mainActivity.findViewById<View>(R.id.exposure)
            val exposureLockButton = mainActivity.findViewById<View>(R.id.exposure_lock)
            val whiteBalanceLockButton = mainActivity.findViewById<View>(R.id.white_balance_lock)
            val cycleRawButton = mainActivity.findViewById<View>(R.id.cycle_raw)
            val storeLocationButton = mainActivity.findViewById<View>(R.id.store_location)
            val textStampButton = mainActivity.findViewById<View>(R.id.text_stamp)
            val stampButton = mainActivity.findViewById<View>(R.id.stamp)
            val focusPeakingButton = mainActivity.findViewById<View>(R.id.focus_peaking)
            val autoLevelButton = mainActivity.findViewById<View>(R.id.auto_level)
            val cycleFlashButton = mainActivity.findViewById<View>(R.id.cycle_flash)
            val faceDetectionButton = mainActivity.findViewById<View>(R.id.face_detection)
            val audioControlButton = mainActivity.findViewById<View>(R.id.audio_control)
//            val popupButton = mainActivity.findViewById<View>(R.id.popup)
            val settingsButton = mainActivity.findViewById<View>(R.id.settings)
            val zoomControls = mainActivity.findViewById<View>(R.id.zoom)
            val zoomSeekBar = mainActivity.findViewById<View>(R.id.zoom_seekbar)
            val focusSeekBar = mainActivity.findViewById<View>(R.id.focus_seekbar)
            val focusBracketingTargetSeekBar = mainActivity.findViewById<View>(R.id.focus_bracketing_target_seekbar)

            if (mainActivity.supportsExposureButton()) exposureButton.visibility = visibility
            if (showExposureLockIcon()) exposureLockButton.visibility = visibility
            if (showWhiteBalanceLockIcon()) whiteBalanceLockButton.visibility = visibility
            if (showCycleRawIcon()) cycleRawButton.visibility = visibility
            if (showStoreLocationIcon()) storeLocationButton.visibility = visibility
            if (showTextStampIcon()) textStampButton.visibility = visibility
            if (showStampIcon()) stampButton.visibility = visibility
            if (showFocusPeakingIcon()) focusPeakingButton.visibility = visibility
            if (showAutoLevelIcon()) autoLevelButton.visibility = visibility
            if (showCycleFlashIcon()) cycleFlashButton.visibility = visibility
            if (showFaceDetectionIcon()) faceDetectionButton.visibility = visibility
            if (mainActivity.hasAudioControl()) audioControlButton.visibility = visibility

//            popupButton.visibility = visibility
            settingsButton.visibility = visibility
            if (MyDebug.LOG) {
                Log.d(TAG, "has_zoom: " + mainActivity.preview!!.supportsZoom())
            }
            if (mainActivity.preview!!.supportsZoom()
                && sharedPreferences.getBoolean(PreferenceKeys.ShowZoomControlsPreferenceKey, false)) {
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

            if (mainActivity.showManualFocusSeekbar(true)) focusBracketingTargetSeekBar.visibility = visibility

            val prefImmersiveMode: String = sharedPreferences.getString(
                PreferenceKeys.ImmersiveModePreferenceKey,
                "immersive_mode_off"
            )!!
            if (prefImmersiveMode == "immersive_mode_everything") {
                if (sharedPreferences.getBoolean(
                        PreferenceKeys.ShowTakePhotoPreferenceKey,
                        true
                    )
                ) {
                    val takePhotoButton = mainActivity.findViewById<View>(R.id.take_photo)
                    takePhotoButton.visibility = visibility
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mainActivity.preview!!.isVideoRecording) {
                    val pauseVideoButton = mainActivity.findViewById<View>(R.id.pause_video)
                    pauseVideoButton.visibility = visibility
                }
                if (mainActivity.preview!!.supportsPhotoVideoRecording()
                    && mainActivity.applicationInterface!!.usePhotoVideoRecording()
                    && mainActivity.preview!!.isVideoRecording
                ) {
                    val takePhotoVideoButton =
                        mainActivity.findViewById<View>(R.id.take_photo_when_video_recording)
                    takePhotoVideoButton.visibility = visibility
                }
                if (mainActivity.applicationInterface!!.gyroSensor.isRecording) {
                    val cancelPanoramaButton =
                        mainActivity.findViewById<View>(R.id.cancel_panorama)
                    cancelPanoramaButton.visibility = visibility
                }
            }
            if (!immersiveMode) {
                // make sure the GUI is set up as expected
                showGUI()
            }
        }
    }

    fun inImmersiveMode(): Boolean {
        return immersive_mode
    }

    fun showGUI(show: Boolean, isVideo: Boolean) {
        if (MyDebug.LOG) {
            Log.d(TAG, "showGUI: $show")
            Log.d(TAG, "is_video: $isVideo")
        }
        if (isVideo) this.show_gui_video = show
        else this.show_gui_photo = show
        showGUI()
    }

    fun showGUI() {
        if (MyDebug.LOG) {
            Log.d(TAG, "showGUI")
            Log.d(TAG, "show_gui_photo: " + show_gui_photo)
            Log.d(TAG, "show_gui_video: " + show_gui_video)
        }
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
            val visibility_video =
                if (is_panorama_recording) View.GONE else if (show_gui_photo) View.VISIBLE else View.GONE // for UI that is only hidden while taking photo
            val exposureButton = mainActivity.findViewById<View>(R.id.exposure)
            val exposureLockButton = mainActivity.findViewById<View>(R.id.exposure_lock)
            val whiteBalanceLockButton =
                mainActivity.findViewById<View>(R.id.white_balance_lock)
            val cycleRawButton = mainActivity.findViewById<View>(R.id.cycle_raw)
            val storeLocationButton = mainActivity.findViewById<View>(R.id.store_location)
            val textStampButton = mainActivity.findViewById<View>(R.id.text_stamp)
            val stampButton = mainActivity.findViewById<View>(R.id.stamp)
            val focusPeakingButton = mainActivity.findViewById<View>(R.id.focus_peaking)
            val autoLevelButton = mainActivity.findViewById<View>(R.id.auto_level)
            val cycleFlashButton = mainActivity.findViewById<View>(R.id.cycle_flash)
            val faceDetectionButton = mainActivity.findViewById<View>(R.id.face_detection)
            val audioControlButton = mainActivity.findViewById<View>(R.id.audio_control)
//            val popupButton = mainActivity.findViewById<View>(R.id.popup)
            if (mainActivity.supportsExposureButton()) exposureButton.visibility =
                visibility_video // still allow exposure when recording video

            if (showExposureLockIcon()) exposureLockButton.visibility =
                visibility_video // still allow exposure lock when recording video

            if (showWhiteBalanceLockIcon()) whiteBalanceLockButton.visibility =
                visibility_video // still allow white balance lock when recording video

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

            val remoteConnectedIcon = mainActivity.findViewById<View>(R.id.kraken_icon)
            if (mainActivity.getBluetoothRemoteControl().remoteConnected()) {
                if (MyDebug.LOG) Log.d(TAG, "Remote control connected")
                remoteConnectedIcon.visibility = View.VISIBLE
            } else {
                if (MyDebug.LOG) Log.d(TAG, "Remote control DISconnected")
                remoteConnectedIcon.visibility = View.GONE
            }
//            popupButton.visibility = if (mainActivity.preview!!.supportsFlash()) visibility_video else visibility // still allow popup in order to change flash mode when recording video

            if (show_gui_photo && show_gui_video) {
                layoutUI() // needed for "top" UIPlacement, to auto-arrange the buttons
            }
        }
    }

    fun updateExposureLockIcon() {
        val view = mainActivity.findViewById<ImageButton>(R.id.exposure_lock)
        val enabled = mainActivity.preview!!.isExposureLocked
        view.setImageResource(if (enabled) R.drawable.exposure_locked else R.drawable.exposure_unlocked)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.exposure_unlock else R.string.exposure_lock)
    }

    fun updateWhiteBalanceLockIcon() {
        val view = mainActivity.findViewById<ImageButton>(R.id.white_balance_lock)
        val enabled = mainActivity.preview!!.isWhiteBalanceLocked
        view.setImageResource(if (enabled) R.drawable.white_balance_locked else R.drawable.white_balance_unlocked)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.white_balance_unlock else R.string.white_balance_lock)
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
        val view = mainActivity.findViewById<ImageButton>(R.id.store_location)
        val enabled = mainActivity.applicationInterface!!.geotaggingPref
        view.setImageResource(if (enabled) R.drawable.ic_gps_fixed_red_48dp else R.drawable.ic_gps_fixed_white_48dp)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.preference_location_disable else R.string.preference_location_enable)
    }

    fun updateTextStampIcon() {
        val view = mainActivity.findViewById<ImageButton>(R.id.text_stamp)
        val enabled = !mainActivity.applicationInterface!!.textStampPref.isEmpty()
        view.setImageResource(if (enabled) R.drawable.baseline_text_fields_red_48 else R.drawable.baseline_text_fields_white_48)
    }

    fun updateStampIcon() {
        val view = mainActivity.findViewById<ImageButton>(R.id.stamp)
        val enabled = mainActivity.applicationInterface!!.stampPref == "preference_stamp_yes"
        view.setImageResource(if (enabled) R.drawable.ic_text_format_red_48dp else R.drawable.ic_text_format_white_48dp)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.stamp_disable else R.string.stamp_enable)
    }

    fun updateFocusPeakingIcon() {
        val view = mainActivity.findViewById<ImageButton>(R.id.focus_peaking)
        val enabled = mainActivity.applicationInterface!!.getFocusPeakingPref()
        view.setImageResource(if (enabled) R.drawable.key_visualizer_red else R.drawable.key_visualizer)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.focus_peaking_disable else R.string.focus_peaking_enable)
    }

    fun updateAutoLevelIcon() {
        val view = mainActivity.findViewById<ImageButton>(R.id.auto_level)
        val enabled = mainActivity.applicationInterface!!.getAutoStabilisePref()
        view.setImageResource(if (enabled) R.drawable.auto_stabilise_icon_red else R.drawable.auto_stabilise_icon)
        view.contentDescription = mainActivity.getResources()
            .getString(if (enabled) R.string.auto_level_disable else R.string.auto_level_enable)
    }

    fun updateCycleFlashIcon() {
        // n.b., read from preview rather than saved application preference - so the icon updates correctly when in flash
        // auto mode, but user switches to manual ISO where flash auto isn't supported
//        val flash_value = mainActivity.preview!!.getCurrentFlashValue()
//        if (flash_value != null) {
//            val view = mainActivity.findViewById<ImageButton>(R.id.cycle_flash)
//            when (flash_value) {
//                "flash_off" -> view.setImageResource(R.drawable.flash_off)
//                "flash_auto", "flash_frontscreen_auto" -> view.setImageResource(R.drawable.flash_auto)
//                "flash_on", "flash_frontscreen_on" -> view.setImageResource(R.drawable.flash_on)
//                "flash_torch", "flash_frontscreen_torch" -> view.setImageResource(R.drawable.baseline_highlight_white_48)
//                "flash_red_eye" -> view.setImageResource(R.drawable.baseline_remove_red_eye_white_48)
//                else -> {
//                    // just in case??
//                    Log.e(TAG, "unknown flash value $flash_value")
//                    view.setImageResource(R.drawable.flash_off)
//                }
//            }
//        } else {
//            val view = mainActivity.findViewById<ImageButton>(R.id.cycle_flash)
//            view.setImageResource(R.drawable.flash_off)
//        }
    }

    fun updateFaceDetectionIcon() {
        val view = mainActivity.findViewById<ImageButton>(R.id.face_detection)
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
        val view = mainActivity.findViewById<ImageButton>(R.id.audio_control)
        view.setImageResource(R.drawable.ic_mic_red_48dp)
        view.contentDescription = mainActivity.getResources().getString(R.string.audio_control_stop)
    }

    fun audioControlStopped() {
        val view = mainActivity.findViewById<ImageButton>(R.id.audio_control)
        view.setImageResource(R.drawable.ic_mic_white_48dp)
        view.contentDescription =
            mainActivity.getResources().getString(R.string.audio_control_start)
    }

    val isExposureUIOpen: Boolean
        get() {
            val exposure_seek_bar =
                mainActivity.findViewById<View>(R.id.exposure_container)
            val exposure_visibility = exposure_seek_bar.visibility
            val manual_exposure_seek_bar =
                mainActivity.findViewById<View>(R.id.manual_exposure_container)
            val manual_exposure_visibility = manual_exposure_seek_bar.visibility
            return exposure_visibility == View.VISIBLE || manual_exposure_visibility == View.VISIBLE
        }

    /**
     * Opens or close the exposure settings (ISO, white balance, etc)
     */
    fun toggleExposureUI() {
        if (MyDebug.LOG) Log.d(TAG, "toggleExposureUI")
        closePopup()
        mSelectingExposureUIElement = false
        if (this.isExposureUIOpen) {
            closeExposureUI()
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

    private fun clearRemoteControlForExposureUI() {
        if (MyDebug.LOG) Log.d(TAG, "clearRemoteControlForExposureUI")
        if (this.isExposureUIOpen && remote_control_mode) {
            remote_control_mode = false
            resetExposureUIHighlights()
        }
    }

    private fun resetExposureUIHighlights() {
        if (MyDebug.LOG) Log.d(TAG, "resetExposureUIHighlights")
        val iso_buttons_container =
            mainActivity.findViewById<ViewGroup>(R.id.iso_buttons) // Shown when Camera API2 enabled
        val exposure_seek_bar = mainActivity.findViewById<View>(R.id.exposure_container)
        val shutter_seekbar = mainActivity.findViewById<View>(R.id.exposure_time_seekbar)
        val iso_seekbar = mainActivity.findViewById<View>(R.id.iso_seekbar)
        val wb_seekbar = mainActivity.findViewById<View>(R.id.white_balance_seekbar)
        // Set all lines to black
        iso_buttons_container.setBackgroundColor(Color.TRANSPARENT)
        exposure_seek_bar.setBackgroundColor(Color.TRANSPARENT)
        shutter_seekbar.setBackgroundColor(Color.TRANSPARENT)
        iso_seekbar.setBackgroundColor(Color.TRANSPARENT)
        wb_seekbar.setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Highlights the relevant line on the Exposure UI based on
     * the value of mExposureLine
     *
     */
    private fun highlightExposureUILine(selectNext: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "highlightExposureUILine: $selectNext")
        if (!this.isExposureUIOpen) { // Safety check
            return
        }
        val iso_buttons_container =
            mainActivity.findViewById<ViewGroup>(R.id.iso_buttons) // Shown when Camera API2 enabled
        val exposure_seek_bar = mainActivity.findViewById<View>(R.id.exposure_container)
        val shutter_seekbar = mainActivity.findViewById<View>(R.id.exposure_time_seekbar)
        val iso_seekbar = mainActivity.findViewById<View>(R.id.iso_seekbar)
        val wb_seekbar = mainActivity.findViewById<View>(R.id.white_balance_seekbar)
        // Our order for lines is:
        // - ISO buttons
        // - ISO slider
        // - Shutter speed
        // - exposure seek bar
        if (MyDebug.LOG) Log.d(TAG, "mExposureLine: $mExposureLine")
        mExposureLine = (mExposureLine + 5) % 5
        if (MyDebug.LOG) Log.d(TAG, "mExposureLine modulo: $mExposureLine")
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
        if (MyDebug.LOG) Log.d(TAG, "after skipping: mExposureLine: $mExposureLine")
        mExposureLine = (mExposureLine + 5) % 5
        if (MyDebug.LOG) Log.d(TAG, "after skipping: mExposureLine modulo: $mExposureLine")
        resetExposureUIHighlights()

        if (mExposureLine == 0) {
            iso_buttons_container.setBackgroundColor(highlightColor)
            //iso_buttons_container.setAlpha(0.5f);
        } else if (mExposureLine == 1) {
            iso_seekbar.setBackgroundColor(highlightColor)
            //iso_seekbar.setAlpha(0.5f);
        } else if (mExposureLine == 2) {
            shutter_seekbar.setBackgroundColor(highlightColor)
            //shutter_seekbar.setAlpha(0.5f);
        } else if (mExposureLine == 3) { //
            exposure_seek_bar.setBackgroundColor(highlightColor)
            //exposure_seek_bar.setAlpha(0.5f);
        } else if (mExposureLine == 4) {
            wb_seekbar.setBackgroundColor(highlightColor)
            //wb_seekbar.setAlpha(0.5f);
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

    /**
     * Our order for lines is:
     * -0: ISO buttons
     * -1: ISO slider
     * -2: Shutter speed
     * -3: exposure seek bar
     */
    private fun nextExposureUIItem() {
        if (MyDebug.LOG) Log.d(TAG, "nextExposureUIItem")
        when (mExposureLine) {
            0 -> nextIsoItem(false)
            1 -> changeSeekbar(R.id.iso_seekbar, 10)
            2 -> changeSeekbar(R.id.exposure_time_seekbar, 5)
            3 ->                 //changeSeekbar(R.id.exposure_seekbar, 1);
                // call via MainActivity.changeExposure(), to handle repeated zeroes
                mainActivity.changeExposure(1)

            4 -> changeSeekbar(R.id.white_balance_seekbar, 3)
        }
    }

    private fun previousExposureUIItem() {
        if (MyDebug.LOG) Log.d(TAG, "previousExposureUIItem")
        when (mExposureLine) {
            0 -> nextIsoItem(true)
            1 -> changeSeekbar(R.id.iso_seekbar, -10)
            2 -> changeSeekbar(R.id.exposure_time_seekbar, -5)
            3 ->                 //changeSeekbar(R.id.exposure_seekbar, -1);
                // call via MainActivity.changeExposure(), to handle repeated zeroes
                mainActivity.changeExposure(-1)

            4 -> changeSeekbar(R.id.white_balance_seekbar, -3)
        }
    }

    private fun nextIsoItem(previous: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "nextIsoItem: " + previous)
        // Find current ISO
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val current_iso: String = sharedPreferences.getString(
            PreferenceKeys.ISOPreferenceKey,
            CameraController.ISO_DEFAULT
        )!!
        val count = isoButtons!!.size
        val step = if (previous) -1 else 1
        var found = false
        for (i in 0..<count) {
            val button = isoButtons!!.get(i) as Button
            val button_text = button.getText().toString()
            if (ISOTextEquals(button_text, current_iso)) {
                found = true
                // Select next one, unless it's "Manual", which we skip since
                // it's not practical in remote mode.
                var nextButton = isoButtons!!.get((i + count + step) % count) as Button
                val nextButton_text = nextButton.getText().toString()
                if (nextButton_text.contains("m")) {
                    nextButton = isoButtons!!.get((i + count + 2 * step) % count) as Button
                }
                nextButton.callOnClick()
                break
            }
        }
        if (!found) {
            // For instance, we are in ISO manual mode and "M" is selected. default
            // back to "Auto" to avoid being stuck since we're with a remote control
            isoButtons!!.get(0)!!.callOnClick()
        }
    }

    /**
     * Select element on exposure UI. Based on the value of mExposureLine
     * // Our order for lines is:
     * // - ISO buttons
     * // - ISO slider
     * // - Shutter speed
     * // - exposure seek bar
     */
    private fun selectExposureUILine() {
        if (MyDebug.LOG) Log.d(TAG, "selectExposureUILine")
        if (!this.isExposureUIOpen) { // Safety check
            return
        }

        if (mExposureLine == 0) { // ISO presets
            val iso_buttons_container = mainActivity.findViewById<ViewGroup>(R.id.iso_buttons)
            iso_buttons_container.setBackgroundColor(highlightColorExposureUIElement)
            //iso_buttons_container.setAlpha(1f);
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
            val current_iso: String = sharedPreferences.getString(
                PreferenceKeys.ISOPreferenceKey,
                CameraController.ISO_DEFAULT
            )!!
            // if the manual ISO value isn't one of the "preset" values, then instead highlight the manual ISO icon
            var found = false
            var manualButton: Button? = null
            for (view in isoButtons!!) {
                val button = view as Button
                val button_text = button.getText().toString()
                if (ISOTextEquals(button_text, current_iso)) {
                    PopupView.setButtonSelected(button, true)
                    //button.setBackgroundColor(highlightColorExposureUIElement);
                    //button.setAlpha(0.3f);
                    found = true
                } else {
                    if (button_text.contains("m")) {
                        manualButton = button
                    }
                    PopupView.setButtonSelected(button, false)
                    button.setBackgroundColor(Color.TRANSPARENT)
                }
            }
            if (!found && manualButton != null) {
                // We are in manual ISO, highlight the "M" button
                PopupView.setButtonSelected(manualButton, true)
                manualButton.setBackgroundColor(highlightColorExposureUIElement)
                //manualButton.setAlpha(0.3f);
            }
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 1) {
            // ISO seek bar - change color
            val seek_bar = mainActivity.findViewById<View>(R.id.iso_seekbar)
            //seek_bar.setAlpha(0.1f);
            seek_bar.setBackgroundColor(highlightColorExposureUIElement)
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 2) {
            // ISO seek bar - change color
            val seek_bar = mainActivity.findViewById<View>(R.id.exposure_time_seekbar)
            //seek_bar.setAlpha(0.1f);
            seek_bar.setBackgroundColor(highlightColorExposureUIElement)
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 3) {
            // Exposure compensation
            val container = mainActivity.findViewById<View>(R.id.exposure_container)
            //container.setAlpha(0.1f);
            container.setBackgroundColor(highlightColorExposureUIElement)
            mSelectingExposureUIElement = true
        } else if (mExposureLine == 4) {
            // Manual white balance
            val container = mainActivity.findViewById<View>(R.id.white_balance_seekbar)
            //container.setAlpha(0.1f);
            container.setBackgroundColor(highlightColorExposureUIElement)
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
        val view = mainActivity.findViewById<ImageButton>(R.id.exposure)
        view.setImageResource(R.drawable.ic_exposure_red_48dp)

        val slidersContainer = mainActivity.findViewById<View>(R.id.sliders_container)
        slidersContainer.visibility = View.VISIBLE

        val animation = AnimationUtils.loadAnimation(mainActivity, R.anim.fade_in)
        slidersContainer.startAnimation(animation)

        val isoButtonsContainer = mainActivity.findViewById<ViewGroup>(R.id.iso_buttons)
        isoButtonsContainer.removeAllViews()

        var supportedIsos: MutableList<String>
        if (preview!!.isVideoRecording) {
            supportedIsos = arrayListOf()
        } else if (preview.supportsISORange()) {
            if (MyDebug.LOG) Log.d(TAG, "supports ISO range")
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
                                val exposureTime = preview.cameraController.captureResultExposureTime()
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
        val isoContainerView = mainActivity.findViewById<View>(R.id.iso_container)
        isoContainerView.visibility = View.VISIBLE

        val exposureSeekBar = mainActivity.findViewById<View>(R.id.exposure_container)
        val manualExposureSeekBar = mainActivity.findViewById<View>(R.id.manual_exposure_container)
        val isoValue = mainActivity.applicationInterface!!.isoPref
        if (mainActivity.preview!!.usingCamera2API() && isoValue != CameraController.ISO_DEFAULT) {
            exposureSeekBar.visibility = View.GONE

            // with Camera2 API, when using manual ISO we instead show sliders for ISO range and exposure time
            if (mainActivity.preview!!.supportsISORange()) {
                manualExposureSeekBar.visibility = View.VISIBLE
                val exposureTimeSeekBar = mainActivity.findViewById<SeekBar>(R.id.exposure_time_seekbar)
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

        val manual_white_balance_seek_bar =
            mainActivity.findViewById<View>(R.id.manual_white_balance_container)
        if (mainActivity.preview!!.supportsWhiteBalanceTemperature()) {
            // we also show slider for manual white balance, if in that mode
            val white_balance_value = mainActivity.applicationInterface!!.whiteBalancePref
            if (mainActivity.preview!!.usingCamera2API() && white_balance_value == "manual") {
                manual_white_balance_seek_bar.visibility = View.VISIBLE
            } else {
                manual_white_balance_seek_bar.visibility = View.GONE
            }
        } else {
            manual_white_balance_seek_bar.visibility = View.GONE
        }

        //layoutUI(); // needed to update alignment of exposure UI
    }

    /** If the exposure panel is open, updates the selected ISO button to match the current ISO value,
     * if a continuous range of ISO values are supported by the camera.
     */
    fun updateSelectedISOButton() {
        if (MyDebug.LOG) Log.d(TAG, "updateSelectedISOButton")
        val preview = mainActivity.preview
        if (preview!!.supportsISORange() && this.isExposureUIOpen) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
            val currentIso: String = sharedPreferences.getString(
                PreferenceKeys.ISOPreferenceKey,
                CameraController.ISO_DEFAULT
            )!!
            // if the manual ISO value isn't one of the "preset" values, then instead highlight the manual ISO icon
            if (MyDebug.LOG) Log.d(TAG, "current_iso: $currentIso")
            var found = false
            for (view in isoButtons!!) {
                val button = view as Button
                if (MyDebug.LOG) Log.d(TAG, "button: " + button.text)
                val buttonText = button.text.toString()
                if (ISOTextEquals(buttonText, currentIso)) {
                    PopupView.setButtonSelected(button, true)
                    found = true
                } else {
                    PopupView.setButtonSelected(button, false)
                }
            }
            if (!found && currentIso != CameraController.ISO_DEFAULT) {
                if (MyDebug.LOG) Log.d(TAG, "must be manual")
                if (isoButtonManualIndex >= 0 && isoButtonManualIndex < isoButtons!!.size) {
                    val button = isoButtons!![isoButtonManualIndex] as Button
                    PopupView.setButtonSelected(button, true)
                }
            }
        }
    }

    fun setSeekbarZoom(newZoom: Int) {
        if (MyDebug.LOG) Log.d(TAG, "setSeekbarZoom: $newZoom")
        val zoomSeekBar = mainActivity.findViewById<SeekBar>(R.id.zoom_seekbar)
        if (MyDebug.LOG) Log.d(TAG, "progress was: " + zoomSeekBar.progress)
        zoomSeekBar.progress = mainActivity.preview!!.maxZoom - newZoom
        if (MyDebug.LOG) Log.d(TAG, "progress is now: " + zoomSeekBar.progress)
    }

    fun changeSeekbar(seekBarId: Int, change: Int) {
        if (MyDebug.LOG) Log.d(TAG, "changeSeekbar: $change")
        val seekBar = mainActivity.findViewById<SeekBar>(seekBarId)
        val value = seekBar.progress
        var newValue = value + change
        if (newValue < 0) newValue = 0
        else if (newValue > seekBar.max) newValue = seekBar.max
        if (MyDebug.LOG) {
            Log.d(TAG, "value: $value")
            Log.d(TAG, "new_value: $newValue")
            Log.d(TAG, "max: " + seekBar.max)
        }
        if (newValue != value) {
            seekBar.progress = newValue
        }
    }

    /** Closes the exposure UI.
     */
    fun closeExposureUI() {
        val imageButton = mainActivity.findViewById<ImageButton>(R.id.exposure)
        imageButton.setImageResource(R.drawable.ic_exposure_white_48dp)

        clearRemoteControlForExposureUI() // must be called before we actually close the exposure panel
        var view = mainActivity.findViewById<View>(R.id.sliders_container)
        view.visibility = View.GONE
        view = mainActivity.findViewById(R.id.iso_container)
        view.visibility = View.GONE
        view = mainActivity.findViewById(R.id.exposure_container)
        view.visibility = View.GONE
        view = mainActivity.findViewById(R.id.manual_exposure_container)
        view.visibility = View.GONE
        view = mainActivity.findViewById(R.id.manual_white_balance_container)
        view.visibility = View.GONE
    }

    fun setPopupIcon() {
        if (MyDebug.LOG) Log.d(TAG, "setPopupIcon")
//        val popup = mainActivity.findViewById<ImageButton>(R.id.popup)
//        val flashValue = mainActivity.preview!!.getCurrentFlashValue()
//        if (MyDebug.LOG) Log.d(TAG, "flash_value: $flashValue")
//        if (mainActivity.mainUI!!.showCycleFlashIcon()) {
//            popup.setImageResource(R.drawable.popup)
//        } else if (flashValue != null && flashValue == "flash_off") {
//            popup.setImageResource(R.drawable.popup_flash_off)
//        } else if (flashValue != null && (flashValue == "flash_torch" || flashValue == "flash_frontscreen_torch")) {
//            popup.setImageResource(R.drawable.popup_flash_torch)
//        } else if (flashValue != null && (flashValue == "flash_auto" || flashValue == "flash_frontscreen_auto")) {
//            popup.setImageResource(R.drawable.popup_flash_auto)
//        } else if (flashValue != null && (flashValue == "flash_on" || flashValue == "flash_frontscreen_on")) {
//            popup.setImageResource(R.drawable.popup_flash_on)
//        } else if (flashValue != null && flashValue == "flash_red_eye") {
//            popup.setImageResource(R.drawable.popup_flash_red_eye)
//        } else {
//            popup.setImageResource(R.drawable.popup)
//        }
    }

    fun closePopup() {
        if (MyDebug.LOG) Log.d(TAG, "close popup")

        mainActivity.enablePopupOnBackPressedCallback(false)

        if (popupIsOpen()) {
            clearRemoteControlForPopup() // must be called before we set popup_view_is_open to false; and before clearSelectionState() so we know which highlighting to disable
            clearSelectionState()

            popup_view_is_open = false
            /* Not destroying the popup doesn't really gain any performance.
             * Also there are still outstanding bugs to fix if we wanted to do this:
             *   - Not resetting the popup menu when switching between photo and video mode. See test testVideoPopup().
             *   - When changing options like flash/focus, the new option isn't selected when reopening the popup menu. See test
             *     testPopup().
             *   - Changing settings potentially means we have to recreate the popup, so the natural place to do this is in
             *     MainActivity.updateForSettings(), but doing so makes the popup close when checking photo or video resolutions!
             *     See test testSwitchResolution().
             */
            if (cache_popup && !force_destroy_popup) {
                popupView!!.visibility = View.GONE
            } else {
                destroyPopup()
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

        closeExposureUI()
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
                    layoutUI(true)
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
                            this.destroyPopup() // need to recreate popup in order to update the auto-level checkbox
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
