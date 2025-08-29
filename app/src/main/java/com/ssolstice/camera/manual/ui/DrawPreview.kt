package com.ssolstice.camera.manual.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.ssolstice.camera.manual.ImageSaver
import com.ssolstice.camera.manual.LocationSupplier
import com.ssolstice.camera.manual.MainActivity
import com.ssolstice.camera.manual.MainActivity.Companion.getRotationFromSystemOrientation
import com.ssolstice.camera.manual.MainActivity.SystemOrientation
import com.ssolstice.camera.manual.MyApplicationInterface
import com.ssolstice.camera.manual.MyApplicationInterface.PhotoMode
import com.ssolstice.camera.manual.MyDebug
import com.ssolstice.camera.manual.PreferenceKeys
import com.ssolstice.camera.manual.R
import com.ssolstice.camera.manual.cameracontroller.CameraController
import com.ssolstice.camera.manual.preview.ApplicationInterface
import com.ssolstice.camera.manual.preview.Preview
import com.ssolstice.camera.manual.ui.MainUI.UIPlacement
import java.io.IOException
import java.text.DateFormat
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import androidx.core.view.isVisible
import androidx.core.graphics.toColorInt

class DrawPreview(mainActivity: MainActivity, applicationInterface: MyApplicationInterface) {

    private val mainActivity: MainActivity

    private val applicationInterface: MyApplicationInterface

    // In some cases when reopening the camera or pausing preview, we apply a dimming effect (only
    // supported when using Camera2 API, since we need to know when frames have been received).
    internal enum class DimPreview {
        DIM_PREVIEW_OFF,  // don't dim the preview
        DIM_PREVIEW_ON,  // do dim the preview
        DIM_PREVIEW_UNTIL // dim the preview until the camera_controller is non-null and has received frames, then switch to DIM_PREVIEW_OFF
    }

    private var dim_preview = DimPreview.DIM_PREVIEW_OFF

    private var cover_preview = false // whether to cover the preview for Camera2 API
    private var camera_inactive_time_ms: Long =
        -1 // if != -1, the time when the camera became inactive

    // store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
    private val sharedPreferences: SharedPreferences

    // cached preferences (need to call updateSettings() to refresh):
    private var has_settings = false
    private var photoMode: PhotoMode? = null
    private var show_time_pref = false
    private var show_camera_id_pref = false
    private var show_free_memory_pref = false
    private var show_iso_pref = false
    private var show_video_max_amp_pref = false
    private var show_zoom_pref = false
    private var show_battery_pref = false
    private var show_angle_pref = false
    private var angle_highlight_color_pref = 0
    private var show_geo_direction_pref = false
    private var take_photo_border_pref = false
    private var preview_size_wysiwyg_pref = false
    private var store_location_pref = false
    private var show_angle_line_pref = false
    private var show_pitch_lines_pref = false
    private var show_geo_direction_lines_pref = false
    private var immersive_mode_everything_pref = false

    // for testing:
    var storedHasStampPref: Boolean = false
        private set
    private var is_raw_pref = false // whether in RAW+JPEG or RAW only mode
    private var is_raw_only_pref = false // whether in RAW only mode
    private var is_face_detection_pref = false
    private var is_audio_enabled_pref = false
    private var is_high_speed = false
    private var capture_rate_factor = 0f
    var storedAutoStabilisePref: Boolean = false
        private set
    private var preference_grid_pref: String? = null
    private var ghost_image_pref: String? = null
    private var ghost_selected_image_pref = ""
    private var ghost_selected_image_bitmap: Bitmap? = null
    private var ghost_image_alpha = 0
    private var want_histogram = false
    private var histogram_type: Preview.HistogramType? = null
    private var want_zebra_stripes = false
    private var zebra_stripes_threshold = 0
    private var zebra_stripes_color_foreground = 0
    private var zebra_stripes_color_background = 0
    private var want_focus_peaking = false
    private var focus_peaking_color_pref = 0
    private var want_pre_shots = false

    // avoid doing things that allocate memory every frame!
    private val p = Paint()
    private val draw_rect = RectF()
    private val gui_location = IntArray(2)
    private val scale_font: Float // SP scaling
    private val scale_dp: Float // DP scaling
    private val stroke_width: Float // stroke_width used for various UI elements
    private var calendar: Calendar? = null
    private var dateFormatTimeInstance: DateFormat? = null
    private val ybounds_text: String
    private val temp_histogram_channel = IntArray(256)
    private val locationInfo = LocationSupplier.LocationInfo()
    private val auto_stabilise_crop = IntArray(2)

    //private final DecimalFormat decimal_format_1dp_force0 = new DecimalFormat("0.0");
    // cached Rects for drawTextWithBackground() calls
    private var text_bounds_time: Rect? = null
    private var text_bounds_camera_id: Rect? = null
    private var text_bounds_free_memory: Rect? = null
    private var text_bounds_angle_single: Rect? = null
    private var text_bounds_angle_double: Rect? = null

    private var angle_string: String? = null // cached for UI performance
    private var cached_angle = 0.0 // the angle that we used for the cached angle_string
    private var last_angle_string_time: Long = 0

    private var free_memory_gb = -1.0f
    private var free_memory_gb_string: String? = null
    private var last_free_memory_time: Long = 0
    private var free_memory_future: Future<*>? = null

    // Important to call StorageUtils.freeMemory() on background thread: we've had ANRs reported
    // from StorageUtils.freeMemory()->freeMemorySAF()->ContentResolver.openFileDescriptor(); also
    // pauses can be seen if running on UI thread if there are a large number of files in the save
    // folder.
    private val free_memory_executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val free_memory_runnable: Runnable = object : Runnable {
        val handler: Handler = Handler(Looper.getMainLooper())

        override fun run() {
            if (MyDebug.LOG) Log.d(TAG, "free_memory_runnable: run")
            val free_mb = mainActivity.storageUtils!!.freeMemory()
            if (free_mb >= 0) {
                val new_free_memory_gb = free_mb / 1024.0f
                handler.post(object : Runnable {
                    override fun run() {
                        onPostExecute(true, new_free_memory_gb)
                    }
                })
            } else {
                handler.post(object : Runnable {
                    override fun run() {
                        onPostExecute(false, -1.0f)
                    }
                })
            }
        }

        /** Runs on UI thread, after background work is complete.
         */
        private fun onPostExecute(has_new_free_memory: Boolean, new_free_memory_gb: Float) {
            if (MyDebug.LOG) Log.d(TAG, "free_memory_runnable: onPostExecute")
            if (free_memory_future != null && free_memory_future!!.isCancelled()) {
                if (MyDebug.LOG) Log.d(TAG, "was cancelled")
                free_memory_future = null
                return
            }

            if (MyDebug.LOG) {
                Log.d(TAG, "has_new_free_memory: " + has_new_free_memory)
                Log.d(TAG, "free_memory_gb: " + free_memory_gb)
                Log.d(TAG, "new_free_memory_gb: " + new_free_memory_gb)
            }
            if (has_new_free_memory && abs(new_free_memory_gb - free_memory_gb) > 0.001f) {
                free_memory_gb = new_free_memory_gb
                free_memory_gb_string =
                    decimalFormat.format(free_memory_gb.toDouble()) + context.resources
                        .getString(R.string.gb_abbreviation)
            }

            free_memory_future = null
        }
    }

    private var current_time_string: String? = null
    private var last_current_time_time: Long = 0

    private var camera_id_string: String? = null
    private var last_camera_id_time: Long = 0

    private var iso_exposure_string: String? = null
    private var is_scanning = false
    private var last_iso_exposure_time: Long = 0

    private var need_flash_indicator = false
    private var last_need_flash_indicator_time: Long = 0

    private val battery_ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private var has_battery_frac = false
    private var battery_frac = 0f
    private var last_battery_time: Long = 0

    private var has_video_max_amp = false
    private var video_max_amp = 0
    private var last_video_max_amp_time: Long = 0
    private var video_max_amp_prev2 = 0
    private var video_max_amp_peak = 0

    private var location_bitmap: Bitmap?
    private var location_off_bitmap: Bitmap?
    private var raw_jpeg_bitmap: Bitmap?
    private var raw_only_bitmap: Bitmap?
    private var auto_stabilise_bitmap: Bitmap?
    private var dro_bitmap: Bitmap?
    private var hdr_bitmap: Bitmap?
    private var panorama_bitmap: Bitmap?
    private var expo_bitmap: Bitmap?

    //private Bitmap focus_bracket_bitmap;
    // no longer bother with a focus bracketing icon - hard to come up with a clear icon, and should be obvious from the two on-screen seekbars
    private var burst_bitmap: Bitmap?
    private var nr_bitmap: Bitmap?
    private var x_night_bitmap: Bitmap?
    private var x_bokeh_bitmap: Bitmap?
    private var x_beauty_bitmap: Bitmap?
    private var photostamp_bitmap: Bitmap?
    private var flash_bitmap: Bitmap?
    private var face_detection_bitmap: Bitmap?
    private var audio_disabled_bitmap: Bitmap?
    private var high_speed_fps_bitmap: Bitmap?
    private var slow_motion_bitmap: Bitmap?
    private var time_lapse_bitmap: Bitmap?
    private var rotate_left_bitmap: Bitmap?
    private var rotate_right_bitmap: Bitmap?

    private val icon_dest = Rect()
    private var needs_flash_time: Long =
        -1 // time when flash symbol comes on (used for fade-in effect)
    private val path = Path()

    private var last_thumbnail: Bitmap? = null // thumbnail of last picture taken

    @Volatile
    private var thumbnail_anim =
        false // whether we are displaying the thumbnail animation; must be volatile for test project reading the state
    private var thumbnail_anim_start_ms: Long = -1 // time that the thumbnail animation started

    @Volatile
    var test_thumbnail_anim_count: Int = 0
    private val thumbnail_anim_src_rect = RectF()
    private val thumbnail_anim_dst_rect = RectF()
    private val thumbnail_anim_matrix = Matrix()
    private var last_thumbnail_is_video = false // whether thumbnail is for video

    private var show_last_image = false // whether to show the last image as part of "pause preview"
    private val last_image_src_rect = RectF()
    private val last_image_dst_rect = RectF()
    private val last_image_matrix = Matrix()
    private var allow_ghost_last_image = false // whether to allow ghosting the last image

    private var ae_started_scanning_ms: Long = -1 // time when ae started scanning

    private var taking_picture =
        false // true iff camera is in process of capturing a picture (including any necessary prior steps such as autofocus, flash/precapture)
    private var capture_started = false // true iff the camera is capturing
    private var front_screen_flash =
        false // true iff the front screen display should maximise to simulate flash
    private var image_queue_full =
        false // whether we can no longer take new photos due to image queue being full (or rather, would become full if a new photo taken)

    private var continuous_focus_moving = false
    private var continuous_focus_moving_ms: Long = 0

    private var enable_gyro_target_spot = false
    private val gyro_directions: MutableList<FloatArray?> = ArrayList<FloatArray?>()
    private val transformed_gyro_direction = FloatArray(3)
    private val gyro_direction_up = FloatArray(3)
    private val transformed_gyro_direction_up = FloatArray(3)

    // call updateCachedViewAngles() before reading these values
    private var view_angle_x_preview = 0f
    private var view_angle_y_preview = 0f
    private var last_view_angles_time: Long = 0

    private var take_photo_top =
        0 // coordinate (in canvas x coordinates, or y coords if system_orientation_portrait==true) of top of the take photo icon
    private var last_take_photo_top_time: Long = 0

    private var top_icon_shift =
        0 // shift that may be needed for on-screen text to avoid clashing with icons (when arranged "along top")
    private var last_top_icon_shift_time: Long = 0

    private var focus_seekbars_margin_left =
        -1 // margin left that's been set for the focus seekbars

    private var last_update_focus_seekbar_auto_time: Long = 0

    // OSD extra lines
    private var OSDLine1: String? = null
    private var OSDLine2: String? = null

    init {
        if (MyDebug.LOG) Log.d(TAG, "DrawPreview")
        this.mainActivity = mainActivity
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        this.applicationInterface = applicationInterface

        // n.b., don't call updateSettings() here, as it may rely on things that aren't yet initialise (e.g., the preview)
        // see testHDRRestart
        p.isAntiAlias = true
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.strokeCap = Paint.Cap.ROUND
        scale_dp = context.resources.displayMetrics.density
        scale_font = context.resources.displayMetrics.scaledDensity
        this.stroke_width = (1.0f * scale_dp + 0.5f) // convert dps to pixels
        p.strokeWidth = this.stroke_width

        location_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_gps_fixed_white_48dp
        )
        location_off_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_gps_off_white_48dp
        )
        raw_jpeg_bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.raw_icon)
        raw_only_bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.raw_only_icon)
        auto_stabilise_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.auto_stabilise_icon
        )
        dro_bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.dro_icon)
        hdr_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_hdr_on_white_48dp
        )
        panorama_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.baseline_panorama_horizontal_white_48
        )
        expo_bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.expo_icon)
        //focus_bracket_bitmap = BitmapFactory.decodeResource(getContext().resources, R.drawable.focus_bracket_icon);
        burst_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_burst_mode_white_48dp
        )
        nr_bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.nr_icon)
        x_night_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.baseline_bedtime_white_48
        )
        x_bokeh_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.baseline_portrait_white_48
        )
        x_beauty_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.baseline_face_retouching_natural_white_48
        )
        photostamp_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_text_format_white_48dp
        )
        flash_bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.flash_on)
        face_detection_bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_face_white_48dp)
        audio_disabled_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_mic_off_white_48dp
        )
        high_speed_fps_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_fast_forward_white_48dp
        )
        slow_motion_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_slow_motion_video_white_48dp
        )
        time_lapse_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.ic_timelapse_white_48dp
        )
        rotate_left_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.baseline_rotate_left_white_48
        )
        rotate_right_bitmap = BitmapFactory.decodeResource(
            context.resources,
            R.drawable.baseline_rotate_right_white_48
        )

        ybounds_text =
            context.resources.getString(R.string.zoom) + context.resources
                .getString(R.string.angle) + context.resources
                .getString(R.string.direction)
    }

    fun onDestroy() {
        if (MyDebug.LOG) Log.d(TAG, "onDestroy")
        if (free_memory_future != null) {
            if (MyDebug.LOG) Log.d(TAG, "cancel free_memory_future")
            free_memory_future!!.cancel(true)
        }
        // clean up just in case
        if (location_bitmap != null) {
            location_bitmap!!.recycle()
            location_bitmap = null
        }
        if (location_off_bitmap != null) {
            location_off_bitmap!!.recycle()
            location_off_bitmap = null
        }
        if (raw_jpeg_bitmap != null) {
            raw_jpeg_bitmap!!.recycle()
            raw_jpeg_bitmap = null
        }
        if (raw_only_bitmap != null) {
            raw_only_bitmap!!.recycle()
            raw_only_bitmap = null
        }
        if (auto_stabilise_bitmap != null) {
            auto_stabilise_bitmap!!.recycle()
            auto_stabilise_bitmap = null
        }
        if (dro_bitmap != null) {
            dro_bitmap!!.recycle()
            dro_bitmap = null
        }
        if (hdr_bitmap != null) {
            hdr_bitmap!!.recycle()
            hdr_bitmap = null
        }
        if (panorama_bitmap != null) {
            panorama_bitmap!!.recycle()
            panorama_bitmap = null
        }
        if (expo_bitmap != null) {
            expo_bitmap!!.recycle()
            expo_bitmap = null
        }
        if (burst_bitmap != null) {
            burst_bitmap!!.recycle()
            burst_bitmap = null
        }
        if (nr_bitmap != null) {
            nr_bitmap!!.recycle()
            nr_bitmap = null
        }
        if (x_night_bitmap != null) {
            x_night_bitmap!!.recycle()
            x_night_bitmap = null
        }
        if (x_bokeh_bitmap != null) {
            x_bokeh_bitmap!!.recycle()
            x_bokeh_bitmap = null
        }
        if (x_beauty_bitmap != null) {
            x_beauty_bitmap!!.recycle()
            x_beauty_bitmap = null
        }
        if (photostamp_bitmap != null) {
            photostamp_bitmap!!.recycle()
            photostamp_bitmap = null
        }
        if (flash_bitmap != null) {
            flash_bitmap!!.recycle()
            flash_bitmap = null
        }
        if (face_detection_bitmap != null) {
            face_detection_bitmap!!.recycle()
            face_detection_bitmap = null
        }
        if (audio_disabled_bitmap != null) {
            audio_disabled_bitmap!!.recycle()
            audio_disabled_bitmap = null
        }
        if (high_speed_fps_bitmap != null) {
            high_speed_fps_bitmap!!.recycle()
            high_speed_fps_bitmap = null
        }
        if (slow_motion_bitmap != null) {
            slow_motion_bitmap!!.recycle()
            slow_motion_bitmap = null
        }
        if (time_lapse_bitmap != null) {
            time_lapse_bitmap!!.recycle()
            time_lapse_bitmap = null
        }
        if (rotate_left_bitmap != null) {
            rotate_left_bitmap!!.recycle()
            rotate_left_bitmap = null
        }
        if (rotate_right_bitmap != null) {
            rotate_right_bitmap!!.recycle()
            rotate_right_bitmap = null
        }

        if (ghost_selected_image_bitmap != null) {
            ghost_selected_image_bitmap!!.recycle()
            ghost_selected_image_bitmap = null
        }
        ghost_selected_image_pref = ""
    }

    private val context: Context
        get() = mainActivity

    /** Computes the x coordinate on screen of left side of the view, equivalent to
     * view.getLocationOnScreen(), but we undo the effect of the view's rotation.
     * This is because getLocationOnScreen() will return the coordinates of the view's top-left
     * *after* applying the rotation, when we want the top left of the icon as shown on screen.
     * This should not be called every frame but instead should be cached, due to cost of calling
     * view.getLocationOnScreen().
     * Update: For supporting landscape and portrait (if MainActivity.lock_to_landscape==false),
     * instead this returns the top side if in portrait. Note though we still need to take rotation
     * into account, as we still apply rotation to the icons when changing orienations (e.g., this
     * is needed when rotating from reverse landscape to portrait, for on-screen text like level
     * angle to be offset correctly above the shutter button (see take_photo_top) when the preview
     * has a wide aspect ratio.
     */
    private fun getViewOnScreenX(view: View): Int {
        view.getLocationOnScreen(gui_location)

        val system_orientation = mainActivity.systemOrientation
        val system_orientation_portrait = system_orientation == SystemOrientation.PORTRAIT
        var xpos = gui_location[if (system_orientation_portrait) 1 else 0]
        var rotation = Math.round(view.getRotation())
        // rotation can be outside [0, 359] if the user repeatedly rotates in same direction!
        rotation =
            (rotation % 360 + 360) % 360 // version of (rotation % 360) that work if rotation is -ve
        /*if( MyDebug.LOG )
            Log.d(TAG, "    mod rotation: " + rotation);*/
        // undo annoying behaviour that getLocationOnScreen takes the rotation into account
        if (system_orientation_portrait) {
            if (rotation == 180 || rotation == 270) {
                xpos -= view.height
            }
        } else {
            if (rotation == 90 || rotation == 180) {
                xpos -= view.width
            }
        }
        return xpos
    }

    /** Sets a current thumbnail for a photo or video just taken. Used for thumbnail animation,
     * and when ghosting the last image.
     */
    fun updateThumbnail(thumbnail: Bitmap?, is_video: Boolean, want_thumbnail_animation: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "updateThumbnail")
        if (want_thumbnail_animation && applicationInterface.thumbnailAnimationPref) {
            if (MyDebug.LOG) Log.d(TAG, "thumbnail_anim started")
            thumbnail_anim = true
            thumbnail_anim_start_ms = System.currentTimeMillis()
            test_thumbnail_anim_count++
            if (MyDebug.LOG) Log.d(
                TAG,
                "test_thumbnail_anim_count is now: " + test_thumbnail_anim_count
            )
        }
        val old_thumbnail = this.last_thumbnail
        this.last_thumbnail = thumbnail
        this.last_thumbnail_is_video = is_video
        this.allow_ghost_last_image = true
        if (old_thumbnail != null) {
            // only recycle after we've set the new thumbnail
            old_thumbnail.recycle()
        }
    }

    fun hasThumbnailAnimation(): Boolean {
        return this.thumbnail_anim
    }

    /** Displays the thumbnail as a fullscreen image (used for pause preview option).
     */
    fun showLastImage() {
        if (MyDebug.LOG) Log.d(TAG, "showLastImage")
        this.show_last_image = true
    }

    fun clearLastImage() {
        if (MyDebug.LOG) Log.d(TAG, "clearLastImage")
        this.show_last_image = false
    }

    fun allowGhostImage() {
        if (MyDebug.LOG) Log.d(TAG, "allowGhostImage")
        if (last_thumbnail != null) this.allow_ghost_last_image = true
    }

    fun clearGhostImage() {
        if (MyDebug.LOG) Log.d(TAG, "clearGhostImage")
        this.allow_ghost_last_image = false
    }

    fun cameraInOperation(in_operation: Boolean) {
        if (in_operation && !mainActivity.preview!!.isVideo()) {
            taking_picture = true
        } else {
            taking_picture = false
            front_screen_flash = false
            capture_started = false
        }
    }

    fun setImageQueueFull(image_queue_full: Boolean) {
        this.image_queue_full = image_queue_full
    }

    fun turnFrontScreenFlashOn() {
        if (MyDebug.LOG) Log.d(TAG, "turnFrontScreenFlashOn")
        front_screen_flash = true
    }

    fun onCaptureStarted() {
        if (MyDebug.LOG) Log.d(TAG, "onCaptureStarted")
        capture_started = true
    }

    fun onContinuousFocusMove(start: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "onContinuousFocusMove: " + start)
        if (start) {
            if (!continuous_focus_moving) { // don't restart the animation if already in motion
                continuous_focus_moving = true
                continuous_focus_moving_ms = System.currentTimeMillis()
            }
        }
        // if we receive start==false, we don't stop the animation - let it continue
    }

    fun clearContinuousFocusMove() {
        if (MyDebug.LOG) Log.d(TAG, "clearContinuousFocusMove")
        if (continuous_focus_moving) {
            continuous_focus_moving = false
            continuous_focus_moving_ms = 0
        }
    }

    fun setGyroDirectionMarker(x: Float, y: Float, z: Float) {
        enable_gyro_target_spot = true
        this.gyro_directions.clear()
        addGyroDirectionMarker(x, y, z)
        gyro_direction_up[0] = 0f
        gyro_direction_up[1] = 1f
        gyro_direction_up[2] = 0f
    }

    fun addGyroDirectionMarker(x: Float, y: Float, z: Float) {
        val vector = floatArrayOf(x, y, z)
        this.gyro_directions.add(vector)
    }

    fun clearGyroDirectionMarker() {
        enable_gyro_target_spot = false
    }

    /** For performance reasons, some of the SharedPreferences settings are cached. This method
     * should be used when the settings may have changed.
     */
    fun updateSettings() {
        if (MyDebug.LOG) Log.d(TAG, "updateSettings")

        photoMode = applicationInterface.photoMode
        if (MyDebug.LOG) Log.d(TAG, "photoMode: $photoMode")

        show_time_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowTimePreferenceKey, true)
        // reset in case user changes the preference:
        dateFormatTimeInstance = DateFormat.getTimeInstance()
        current_time_string = null
        last_current_time_time = 0
        text_bounds_time = null

        show_camera_id_pref = mainActivity.isMultiCam && sharedPreferences.getBoolean(
            PreferenceKeys.ShowCameraIDPreferenceKey,
            true
        )
        //show_camera_id_pref = true; // test
        show_free_memory_pref =
            sharedPreferences.getBoolean(PreferenceKeys.ShowFreeMemoryPreferenceKey, true)
        show_iso_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowISOPreferenceKey, true)
        show_video_max_amp_pref =
            sharedPreferences.getBoolean(PreferenceKeys.ShowVideoMaxAmpPreferenceKey, false)
        show_zoom_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowZoomPreferenceKey, true)
        show_battery_pref =
            sharedPreferences.getBoolean(PreferenceKeys.ShowBatteryPreferenceKey, true)

        show_angle_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowAnglePreferenceKey, false)
        val angle_highlight_color: String = sharedPreferences.getString(
            PreferenceKeys.ShowAngleHighlightColorPreferenceKey,
            "#14e715"
        )!!
        angle_highlight_color_pref = angle_highlight_color.toColorInt()
        show_geo_direction_pref =
            sharedPreferences.getBoolean(PreferenceKeys.ShowGeoDirectionPreferenceKey, false)

        take_photo_border_pref =
            sharedPreferences.getBoolean(PreferenceKeys.TakePhotoBorderPreferenceKey, true)
        preview_size_wysiwyg_pref = sharedPreferences.getString(
            PreferenceKeys.PreviewSizePreferenceKey,
            "preference_preview_size_wysiwyg"
        ) == "preference_preview_size_wysiwyg"
        store_location_pref =
            sharedPreferences.getBoolean(PreferenceKeys.LocationPreferenceKey, false)

        show_angle_line_pref =
            sharedPreferences.getBoolean(PreferenceKeys.ShowAngleLinePreferenceKey, false)
        show_pitch_lines_pref =
            sharedPreferences.getBoolean(PreferenceKeys.ShowPitchLinesPreferenceKey, false)
        show_geo_direction_lines_pref =
            sharedPreferences.getBoolean(PreferenceKeys.ShowGeoDirectionLinesPreferenceKey, false)

        val immersive_mode: String = sharedPreferences.getString(
            PreferenceKeys.ImmersiveModePreferenceKey,
            "immersive_mode_off"
        )!!
        immersive_mode_everything_pref = immersive_mode == "immersive_mode_everything"

        this.storedHasStampPref = applicationInterface.stampPref == "preference_stamp_yes"
        is_raw_pref =
            applicationInterface.getRawPref() != ApplicationInterface.RawPref.RAWPREF_JPEG_ONLY
        is_raw_only_pref = applicationInterface.isRawOnly
        is_face_detection_pref = applicationInterface.getFaceDetectionPref()
        is_audio_enabled_pref = applicationInterface.recordAudioPref

        is_high_speed = applicationInterface.fpsIsHighSpeed()
        capture_rate_factor = applicationInterface.getVideoCaptureRateFactor()

        this.storedAutoStabilisePref = applicationInterface.autoStabilisePref

        preference_grid_pref = sharedPreferences.getString(
            PreferenceKeys.ShowGridPreferenceKey,
            "preference_grid_none"
        )

        ghost_image_pref = sharedPreferences.getString(
            PreferenceKeys.GhostImagePreferenceKey,
            "preference_ghost_image_off"
        )
        if (ghost_image_pref == "preference_ghost_image_selected") {
            val new_ghost_selected_image_pref: String =
                sharedPreferences.getString(PreferenceKeys.GhostSelectedImageSAFPreferenceKey, "")!!
            if (MyDebug.LOG) Log.d(
                TAG,
                "new_ghost_selected_image_pref: " + new_ghost_selected_image_pref
            )

            val keyguard_manager =
                mainActivity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
            val is_locked =
                keyguard_manager != null && keyguard_manager.inKeyguardRestrictedInputMode()
            if (MyDebug.LOG) Log.d(TAG, "is_locked?: " + is_locked)

            if (is_locked) {
                // don't show selected image when device locked, as this could be a security flaw
                if (ghost_selected_image_bitmap != null) {
                    ghost_selected_image_bitmap!!.recycle()
                    ghost_selected_image_bitmap = null
                    ghost_selected_image_pref = "" // so we'll load the bitmap again when unlocked
                }
            } else if (new_ghost_selected_image_pref != ghost_selected_image_pref) {
                if (MyDebug.LOG) Log.d(TAG, "ghost_selected_image_pref has changed")
                ghost_selected_image_pref = new_ghost_selected_image_pref
                if (ghost_selected_image_bitmap != null) {
                    ghost_selected_image_bitmap!!.recycle()
                    ghost_selected_image_bitmap = null
                }
                val uri = Uri.parse(ghost_selected_image_pref)
                try {
                    ghost_selected_image_bitmap = loadBitmap(uri)
                } catch (e: IOException) {
                    Log.e(TAG, "failed to load ghost_selected_image uri: " + uri)
                    e.printStackTrace()
                    ghost_selected_image_bitmap = null
                    // don't set ghost_selected_image_pref to null, as we don't want to repeatedly try loading the invalid uri
                }
            }
        } else {
            if (ghost_selected_image_bitmap != null) {
                ghost_selected_image_bitmap!!.recycle()
                ghost_selected_image_bitmap = null
            }
            ghost_selected_image_pref = ""
        }
        ghost_image_alpha = applicationInterface.ghostImageAlpha

        val histogram_pref: String = sharedPreferences.getString(
            PreferenceKeys.HistogramPreferenceKey,
            "preference_histogram_off"
        )!!
        want_histogram =
            histogram_pref != "preference_histogram_off" && mainActivity.supportsPreviewBitmaps()
        histogram_type = Preview.HistogramType.HISTOGRAM_TYPE_VALUE
        if (want_histogram) {
            when (histogram_pref) {
                "preference_histogram_rgb" -> histogram_type =
                    Preview.HistogramType.HISTOGRAM_TYPE_RGB

                "preference_histogram_luminance" -> histogram_type =
                    Preview.HistogramType.HISTOGRAM_TYPE_LUMINANCE

                "preference_histogram_value" -> histogram_type =
                    Preview.HistogramType.HISTOGRAM_TYPE_VALUE

                "preference_histogram_intensity" -> histogram_type =
                    Preview.HistogramType.HISTOGRAM_TYPE_INTENSITY

                "preference_histogram_lightness" -> histogram_type =
                    Preview.HistogramType.HISTOGRAM_TYPE_LIGHTNESS
            }
        }

        val zebra_stripes_value: String =
            sharedPreferences.getString(PreferenceKeys.ZebraStripesPreferenceKey, "0")!!
        try {
            zebra_stripes_threshold = zebra_stripes_value.toInt()
        } catch (e: NumberFormatException) {
            if (MyDebug.LOG) Log.e(
                TAG,
                "failed to parse zebra_stripes_value: " + zebra_stripes_value
            )
            e.printStackTrace()
            zebra_stripes_threshold = 0
        }
        want_zebra_stripes =
            (zebra_stripes_threshold != 0) and mainActivity.supportsPreviewBitmaps()

        val zebra_stripes_color_foreground_value: String = sharedPreferences.getString(
            PreferenceKeys.ZebraStripesForegroundColorPreferenceKey,
            "#ff000000"
        )!!
        zebra_stripes_color_foreground = Color.parseColor(zebra_stripes_color_foreground_value)
        val zebra_stripes_color_background_value: String = sharedPreferences.getString(
            PreferenceKeys.ZebraStripesBackgroundColorPreferenceKey,
            "#ffffffff"
        )!!
        zebra_stripes_color_background = Color.parseColor(zebra_stripes_color_background_value)

        want_focus_peaking = applicationInterface.focusPeakingPref
        val focus_peaking_color: String =
            sharedPreferences.getString(PreferenceKeys.FocusPeakingColorPreferenceKey, "#ffffff")!!
        focus_peaking_color_pref = Color.parseColor(focus_peaking_color)

        want_pre_shots = applicationInterface.getPreShotsPref(photoMode)

        last_camera_id_time = 0 // in case camera id changed
        last_view_angles_time = 0 // force view angles to be recomputed
        last_take_photo_top_time = 0 // force take_photo_top to be recomputed
        last_top_icon_shift_time = 0 // for top_icon_shift to be recomputed

        focus_seekbars_margin_left =
            -1 // needed as the focus seekbars can only be updated when visible

        has_settings = true
    }

    /** Indicates that navigation gaps have changed, as a hint to avoid cached data.
     */
    fun onNavigationGapChanged() {
        // needed for OnePlus Pad when rotating, to avoid delay in updating last_take_photo_top_time (affects placement of on-screen text e.g. zoom)
        this.last_take_photo_top_time = 0
    }

    private fun updateCachedViewAngles(time_ms: Long) {
        if (last_view_angles_time == 0L || time_ms > last_view_angles_time + 10000) {
            if (MyDebug.LOG) Log.d(TAG, "update cached view angles")
            // don't call this too often, for UI performance
            // note that updateSettings will force the time to reset anyway, but we check every so often
            // again just in case...
            val preview = mainActivity.preview
            view_angle_x_preview = preview!!.getViewAngleX(true)
            view_angle_y_preview = preview.getViewAngleY(true)
            last_view_angles_time = time_ms
        }
    }

    /** Loads the bitmap from the uri.
     * The image will be downscaled if required to be comparable to the preview width.
     */
    @Throws(IOException::class)
    private fun loadBitmap(uri: Uri): Bitmap {
        if (MyDebug.LOG) Log.d(TAG, "loadBitmap: " + uri)
        var bitmap: Bitmap?
        try {
            //bitmap = MediaStore.Images.Media.getBitmap(main_activity.getContentResolver(), uri);

            var sample_size = 1
            run {
                // attempt to compute appropriate scaling
                val bounds = BitmapFactory.Options()
                bounds.inJustDecodeBounds = true
                val input = mainActivity.getContentResolver().openInputStream(uri)
                BitmapFactory.decodeStream(input, null, bounds)
                if (input != null) input.close()
                if (bounds.outWidth != -1 && bounds.outHeight != -1) {
                    // compute appropriate scaling
                    val image_size = max(bounds.outWidth, bounds.outHeight)

                    val point = Point()
                    applicationInterface.getDisplaySize(point, true)
                    val display_size = max(point.x, point.y)

                    val ratio = ceil(image_size.toDouble() / display_size).toInt()
                    sample_size = Integer.highestOneBit(ratio)
                    if (MyDebug.LOG) {
                        Log.d(TAG, "display_size: " + display_size)
                        Log.d(TAG, "image_size: " + image_size)
                        Log.d(TAG, "ratio: " + ratio)
                        Log.d(TAG, "sample_size: " + sample_size)
                    }
                } else {
                    if (MyDebug.LOG) Log.e(TAG, "failed to obtain width/height of bitmap")
                }
            }

            val options = BitmapFactory.Options()
            options.inMutable = false
            options.inSampleSize = sample_size
            val input = mainActivity.getContentResolver().openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(input, null, options)
            if (input != null) input.close()
            if (MyDebug.LOG && bitmap != null) {
                Log.d(TAG, "bitmap width: " + bitmap.getWidth())
                Log.d(TAG, "bitmap height: " + bitmap.getHeight())
            }
        } catch (e: Exception) {
            // Although Media.getBitmap() is documented as only throwing FileNotFoundException, IOException
            // (with the former being a subset of IOException anyway), I've had SecurityException from
            // Google Play - best to catch everything just in case.
            Log.e(TAG, "MediaStore.Images.Media.getBitmap exception")
            e.printStackTrace()
            throw IOException()
        }
        if (bitmap == null) {
            // just in case!
            Log.e(TAG, "MediaStore.Images.Media.getBitmap returned null")
            throw IOException()
        }

        // now need to take exif orientation into account, as some devices or camera apps store the orientation in the exif tag,
        // which getBitmap() doesn't account for
        bitmap = mainActivity.rotateForExif(bitmap, uri)

        return bitmap
    }

    private fun getTimeStringFromSeconds(time: Long): String {
        var time = time
        val secs = (time % 60).toInt()
        time /= 60
        val mins = (time % 60).toInt()
        time /= 60
        val hours = time
        return "$hours:" + String.format(
            Locale.getDefault(),
            "%02d",
            mins
        ) + ":" + String.format(
            Locale.getDefault(), "%02d", secs
        )
    }

    private fun drawGrids(canvas: Canvas) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        if (camera_controller == null) {
            return
        }

        p.strokeWidth = stroke_width

        when (preference_grid_pref) {
            "preference_grid_3x3" -> {
                p.color = Color.WHITE
                canvas.drawLine(
                    canvas.width / 3.0f,
                    0.0f,
                    canvas.width / 3.0f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    2.0f * canvas.width / 3.0f,
                    0.0f,
                    2.0f * canvas.width / 3.0f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    0.0f,
                    canvas.height / 3.0f,
                    canvas.width - 1.0f,
                    canvas.height / 3.0f,
                    p
                )
                canvas.drawLine(
                    0.0f,
                    2.0f * canvas.height / 3.0f,
                    canvas.width - 1.0f,
                    2.0f * canvas.height / 3.0f,
                    p
                )
            }

            "preference_grid_phi_3x3" -> {
                p.setColor(Color.WHITE)
                canvas.drawLine(
                    canvas.width / 2.618f,
                    0.0f,
                    canvas.width / 2.618f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    1.618f * canvas.width / 2.618f,
                    0.0f,
                    1.618f * canvas.width / 2.618f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    0.0f,
                    canvas.height / 2.618f,
                    canvas.width - 1.0f,
                    canvas.height / 2.618f,
                    p
                )
                canvas.drawLine(
                    0.0f,
                    1.618f * canvas.height / 2.618f,
                    canvas.width - 1.0f,
                    1.618f * canvas.height / 2.618f,
                    p
                )
            }

            "preference_grid_4x2" -> {
                p.setColor(Color.GRAY)
                canvas.drawLine(
                    canvas.width / 4.0f,
                    0.0f,
                    canvas.width / 4.0f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    canvas.width / 2.0f,
                    0.0f,
                    canvas.width / 2.0f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    3.0f * canvas.width / 4.0f,
                    0.0f,
                    3.0f * canvas.width / 4.0f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    0.0f,
                    canvas.height / 2.0f,
                    canvas.width - 1.0f,
                    canvas.height / 2.0f,
                    p
                )
                p.setColor(Color.WHITE)
                val crosshairs_radius = (20 * scale_dp + 0.5f).toInt() // convert dps to pixels

                canvas.drawLine(
                    canvas.width / 2.0f,
                    canvas.height / 2.0f - crosshairs_radius,
                    canvas.width / 2.0f,
                    canvas.height / 2.0f + crosshairs_radius,
                    p
                )
                canvas.drawLine(
                    canvas.width / 2.0f - crosshairs_radius,
                    canvas.height / 2.0f,
                    canvas.width / 2.0f + crosshairs_radius,
                    canvas.height / 2.0f,
                    p
                )
            }

            "preference_grid_crosshair" -> {
                p.setColor(Color.WHITE)
                canvas.drawLine(
                    canvas.width / 2.0f,
                    0.0f,
                    canvas.width / 2.0f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    0.0f,
                    canvas.height / 2.0f,
                    canvas.width - 1.0f,
                    canvas.height / 2.0f,
                    p
                )
            }

            "preference_grid_golden_spiral_right", "preference_grid_golden_spiral_left", "preference_grid_golden_spiral_upside_down_right", "preference_grid_golden_spiral_upside_down_left" -> {
                canvas.save()
                when (preference_grid_pref) {
                    "preference_grid_golden_spiral_left" -> canvas.scale(
                        -1.0f,
                        1.0f,
                        canvas.width * 0.5f,
                        canvas.height * 0.5f
                    )

                    "preference_grid_golden_spiral_right" -> {}
                    "preference_grid_golden_spiral_upside_down_left" -> canvas.rotate(
                        180.0f,
                        canvas.width * 0.5f,
                        canvas.height * 0.5f
                    )

                    "preference_grid_golden_spiral_upside_down_right" -> canvas.scale(
                        1.0f,
                        -1.0f,
                        canvas.width * 0.5f,
                        canvas.height * 0.5f
                    )
                }
                p.setColor(Color.WHITE)
                p.setStyle(Paint.Style.STROKE)
                p.setStrokeWidth(stroke_width)
                var fibb = 34
                var fibb_n = 21
                var left = 0
                var top = 0
                var full_width = canvas.width
                var full_height = canvas.height
                var width = (full_width * (fibb_n.toDouble()) / (fibb).toDouble()).toInt()
                var height = full_height

                var count = 0
                while (count < 2) {
                    canvas.save()
                    draw_rect.set(
                        left.toFloat(),
                        top.toFloat(),
                        (left + width).toFloat(),
                        (top + height).toFloat()
                    )
                    canvas.clipRect(draw_rect)
                    canvas.drawRect(draw_rect, p)
                    draw_rect.set(
                        left.toFloat(),
                        top.toFloat(),
                        (left + 2 * width).toFloat(),
                        (top + 2 * height).toFloat()
                    )
                    canvas.drawOval(draw_rect, p)
                    canvas.restore()

                    var old_fibb = fibb
                    fibb = fibb_n
                    fibb_n = old_fibb - fibb

                    left += width
                    full_width = full_width - width
                    width = full_width
                    height = (height * (fibb_n.toDouble()) / (fibb).toDouble()).toInt()

                    canvas.save()
                    draw_rect.set(
                        left.toFloat(),
                        top.toFloat(),
                        (left + width).toFloat(),
                        (top + height).toFloat()
                    )
                    canvas.clipRect(draw_rect)
                    canvas.drawRect(draw_rect, p)
                    draw_rect.set(
                        (left - width).toFloat(),
                        top.toFloat(),
                        (left + width).toFloat(),
                        (top + 2 * height).toFloat()
                    )
                    canvas.drawOval(draw_rect, p)
                    canvas.restore()

                    old_fibb = fibb
                    fibb = fibb_n
                    fibb_n = old_fibb - fibb

                    top += height
                    full_height = full_height - height
                    height = full_height
                    width = (width * (fibb_n.toDouble()) / (fibb).toDouble()).toInt()
                    left += full_width - width

                    canvas.save()
                    draw_rect.set(
                        left.toFloat(),
                        top.toFloat(),
                        (left + width).toFloat(),
                        (top + height).toFloat()
                    )
                    canvas.clipRect(draw_rect)
                    canvas.drawRect(draw_rect, p)
                    draw_rect.set(
                        (left - width).toFloat(),
                        (top - height).toFloat(),
                        (left + width).toFloat(),
                        (top + height).toFloat()
                    )
                    canvas.drawOval(draw_rect, p)
                    canvas.restore()

                    old_fibb = fibb
                    fibb = fibb_n
                    fibb_n = old_fibb - fibb

                    full_width = full_width - width
                    width = full_width
                    left -= width
                    height = (height * (fibb_n.toDouble()) / (fibb).toDouble()).toInt()
                    top += full_height - height

                    canvas.save()
                    draw_rect.set(
                        left.toFloat(),
                        top.toFloat(),
                        (left + width).toFloat(),
                        (top + height).toFloat()
                    )
                    canvas.clipRect(draw_rect)
                    canvas.drawRect(draw_rect, p)
                    draw_rect.set(
                        left.toFloat(),
                        (top - height).toFloat(),
                        (left + 2 * width).toFloat(),
                        (top + height).toFloat()
                    )
                    canvas.drawOval(draw_rect, p)
                    canvas.restore()

                    old_fibb = fibb
                    fibb = fibb_n
                    fibb_n = old_fibb - fibb

                    full_height = full_height - height
                    height = full_height
                    top -= height
                    width = (width * (fibb_n.toDouble()) / (fibb).toDouble()).toInt()
                    count++
                }

                canvas.restore()
                p.setStyle(Paint.Style.FILL) // reset
            }

            "preference_grid_golden_triangle_1", "preference_grid_golden_triangle_2" -> {
                p.setColor(Color.WHITE)
                val theta = atan2(canvas.width.toDouble(), canvas.height.toDouble())
                val dist = canvas.height * cos(theta)
                val dist_x = (dist * sin(theta)).toFloat()
                val dist_y = (dist * cos(theta)).toFloat()
                if (preference_grid_pref == "preference_grid_golden_triangle_1") {
                    canvas.drawLine(
                        0.0f,
                        canvas.height - 1.0f,
                        canvas.width - 1.0f,
                        0.0f,
                        p
                    )
                    canvas.drawLine(0.0f, 0.0f, dist_x, canvas.height - dist_y, p)
                    canvas.drawLine(
                        canvas.width - 1.0f - dist_x,
                        dist_y - 1.0f,
                        canvas.width - 1.0f,
                        canvas.height - 1.0f,
                        p
                    )
                } else {
                    canvas.drawLine(
                        0.0f,
                        0.0f,
                        canvas.width - 1.0f,
                        canvas.height - 1.0f,
                        p
                    )
                    canvas.drawLine(
                        canvas.width - 1.0f,
                        0.0f,
                        canvas.width - 1.0f - dist_x,
                        canvas.height - dist_y,
                        p
                    )
                    canvas.drawLine(dist_x, dist_y - 1.0f, 0.0f, canvas.height - 1.0f, p)
                }
            }

            "preference_grid_diagonals" -> {
                p.setColor(Color.WHITE)
                canvas.drawLine(0.0f, 0.0f, canvas.height - 1.0f, canvas.height - 1.0f, p)
                canvas.drawLine(canvas.height - 1.0f, 0.0f, 0.0f, canvas.height - 1.0f, p)
                val diff = canvas.width - canvas.height
                // n.b., diff is -ve in portrait orientation
                canvas.drawLine(
                    diff.toFloat(),
                    0.0f,
                    diff + canvas.height - 1.0f,
                    canvas.height - 1.0f,
                    p
                )
                canvas.drawLine(
                    diff + canvas.height - 1.0f,
                    0.0f,
                    diff.toFloat(),
                    canvas.height - 1.0f,
                    p
                )
            }
        }
    }

    private fun drawCropGuides(canvas: Canvas) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        if (preview.isVideo || preview_size_wysiwyg_pref) {
            val preference_crop_guide: String = sharedPreferences.getString(
                PreferenceKeys.ShowCropGuidePreferenceKey,
                "crop_guide_none"
            )!!
            if (camera_controller != null && preview.getTargetRatio() > 0.0 && (preference_crop_guide != "crop_guide_none")) {
                var crop_ratio = -1.0
                when (preference_crop_guide) {
                    "crop_guide_1" -> crop_ratio = 1.0
                    "crop_guide_1.25" -> crop_ratio = 1.25
                    "crop_guide_1.33" -> crop_ratio = 1.33333333
                    "crop_guide_1.4" -> crop_ratio = 1.4
                    "crop_guide_1.5" -> crop_ratio = 1.5
                    "crop_guide_1.78" -> crop_ratio = 1.77777778
                    "crop_guide_1.85" -> crop_ratio = 1.85
                    "crop_guide_2" -> crop_ratio = 2.0
                    "crop_guide_2.33" -> crop_ratio = 2.33333333
                    "crop_guide_2.35" -> crop_ratio = 2.35006120 // actually 1920:817
                    "crop_guide_2.4" -> crop_ratio = 2.4
                }
                if (crop_ratio > 0.0) {
                    // we should compare to getCurrentPreviewAspectRatio() not getTargetRatio(), as the actual preview
                    // aspect ratio may differ to the requested photo/video resolution's aspect ratio, in which case it's still useful
                    // to display the crop guide
                    var preview_aspect_ratio = preview.getCurrentPreviewAspectRatio()
                    val system_orientation = mainActivity.systemOrientation
                    val system_orientation_portrait =
                        system_orientation == SystemOrientation.PORTRAIT
                    if (system_orientation_portrait) {
                        // crop ratios are always drawn as if in landscape
                        crop_ratio = 1.0 / crop_ratio
                        preview_aspect_ratio = 1.0 / preview_aspect_ratio
                    }
                    if (abs(preview_aspect_ratio - crop_ratio) > 1.0e-5) {
                        /*if( MyDebug.LOG ) {
                            Log.d(TAG, "crop_ratio: " + crop_ratio);
                            Log.d(TAG, "preview_aspect_ratio: " + preview_aspect_ratio);
                            Log.d(TAG, "canvas width: " + canvas.getWidth());
                            Log.d(TAG, "canvas height: " + canvas.getHeight());
                        }*/
                        p.setStyle(Paint.Style.FILL)
                        p.setColor(Color.rgb(0, 0, 0))
                        p.setAlpha(crop_shading_alpha_c)
                        var left = 1
                        var top = 1
                        var right = canvas.width - 1
                        var bottom = canvas.height - 1
                        if (crop_ratio > preview_aspect_ratio) {
                            // crop ratio is wider, so we have to crop top/bottom
                            val new_hheight = (canvas.width.toDouble()) / (2.0f * crop_ratio)
                            top = (canvas.height / 2 - new_hheight.toInt())
                            bottom = (canvas.height / 2 + new_hheight.toInt())
                            // draw shaded area
                            canvas.drawRect(0f, 0f, canvas.width.toFloat(), top.toFloat(), p)
                            canvas.drawRect(
                                0f,
                                bottom.toFloat(),
                                canvas.width.toFloat(),
                                canvas.height.toFloat(),
                                p
                            )
                        } else {
                            // crop ratio is taller, so we have to crop left/right
                            val new_hwidth = ((canvas.height.toDouble()) * crop_ratio) / 2.0f
                            left = (canvas.width / 2 - new_hwidth.toInt())
                            right = (canvas.width / 2 + new_hwidth.toInt())
                            // draw shaded area
                            canvas.drawRect(0f, 0f, left.toFloat(), canvas.height.toFloat(), p)
                            canvas.drawRect(
                                right.toFloat(),
                                0f,
                                canvas.width.toFloat(),
                                canvas.height.toFloat(),
                                p
                            )
                        }
                        p.setStyle(Paint.Style.STROKE)
                        p.setStrokeWidth(stroke_width)
                        p.setColor(Color.rgb(255, 235, 59)) // Yellow 500
                        canvas.drawRect(
                            left.toFloat(),
                            top.toFloat(),
                            right.toFloat(),
                            bottom.toFloat(),
                            p
                        )
                        p.setStyle(Paint.Style.FILL) // reset
                        p.setAlpha(255) // reset
                    }
                }
            }
        }
    }

    private fun onDrawInfoLines(
        canvas: Canvas,
        top_x: Int,
        top_y: Int,
        bottom_y: Int,
        device_ui_rotation: Int,
        time_ms: Long
    ) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        val ui_rotation = preview.getUIRotation()

        // set up text etc for the multiple lines of "info" (time, free mem, etc)
        p.setTextSize(16 * scale_font + 0.5f) // convert dps to pixels
        p.setTextAlign(Paint.Align.LEFT)
        var location_x = top_x
        var location_y = top_y
        val gap_x = (8 * scale_font + 0.5f).toInt() // convert dps to pixels
        val gap_y = (0 * scale_font + 0.5f).toInt() // convert dps to pixels
        val icon_gap_y = (2 * scale_dp + 0.5f).toInt() // convert dps to pixels
        if (ui_rotation == 90 || ui_rotation == 270) {
            // n.b., this is only for when lock_to_landscape==true, so we don't look at device_ui_rotation
            val diff = canvas.width - canvas.height
            location_x += diff / 2
            location_y -= diff / 2
        }
        if (device_ui_rotation == 90) {
            location_y = canvas.height - location_y - (20 * scale_font + 0.5f).toInt()
        }
        var align_right = false
        if (device_ui_rotation == 180) {
            location_x = canvas.width - location_x
            p.setTextAlign(Paint.Align.RIGHT)
            align_right = true
        }

        var first_line_height = 0
        var first_line_xshift = 0
        if (show_time_pref) {
            if (current_time_string == null || time_ms / 1000 > last_current_time_time / 1000) {
                // avoid creating a new calendar object every time
                if (calendar == null) calendar = Calendar.getInstance()
                else calendar!!.setTimeInMillis(time_ms)

                current_time_string = dateFormatTimeInstance!!.format(calendar!!.getTime())
                //current_time_string = DateUtils.formatDateTime(getContext(), c.getTimeInMillis(), DateUtils.FORMAT_SHOW_TIME);
                last_current_time_time = time_ms
            }
            // n.b., DateFormat.getTimeInstance() ignores user preferences such as 12/24 hour or date format, but this is an Android bug.
            // Whilst DateUtils.formatDateTime doesn't have that problem, it doesn't print out seconds! See:
            // http://stackoverflow.com/questions/15981516/simpledateformat-gettimeinstance-ignores-24-hour-format
            // http://daniel-codes.blogspot.co.uk/2013/06/how-to-correctly-format-datetime.html
            // http://code.google.com/p/android/issues/detail?id=42104
            // update: now seems to be fixed
            // also possibly related https://code.google.com/p/android/issues/detail?id=181201
            //int height = applicationInterface.drawTextWithBackground(canvas, p, current_time_string, Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP);
            if (text_bounds_time == null) {
                if (MyDebug.LOG) Log.d(TAG, "compute text_bounds_time")
                text_bounds_time = Rect()
                // better to not use a fixed string like "00:00:00" as don't want to make assumptions - e.g., in 12 hour format we'll have the appended am/pm to account for!
                val calendar = Calendar.getInstance()
                calendar.set(100, 0, 1, 10, 59, 59)
                val bounds_time_string = dateFormatTimeInstance!!.format(calendar.getTime())
                if (MyDebug.LOG) Log.d(TAG, "bounds_time_string:" + bounds_time_string)
                p.getTextBounds(bounds_time_string, 0, bounds_time_string.length, text_bounds_time)
            }
            first_line_xshift += text_bounds_time!!.width() + gap_x
            var height = applicationInterface.drawTextWithBackground(
                canvas,
                p,
                current_time_string?:"",
                Color.WHITE,
                Color.BLACK,
                location_x,
                location_y,
                MyApplicationInterface.Alignment.ALIGNMENT_TOP,
                null,
                MyApplicationInterface.Shadow.SHADOW_OUTLINE,
                text_bounds_time
            )
            height += gap_y
            // don't update location_y yet, as we have time and cameraid shown on the same line
            first_line_height = max(first_line_height, height)
        }
        if (show_camera_id_pref && camera_controller != null) {
            if (camera_id_string == null || time_ms > last_camera_id_time + 10000) {
                // cache string for performance

                camera_id_string = context.resources
                    .getString(R.string.camera_id) + ":" + preview.getCameraId() // intentionally don't put a space
                last_camera_id_time = time_ms
            }
            if (text_bounds_camera_id == null) {
                if (MyDebug.LOG) Log.d(TAG, "compute text_bounds_camera_id")
                text_bounds_camera_id = Rect()
                p.getTextBounds(
                    camera_id_string,
                    0,
                    camera_id_string!!.length,
                    text_bounds_camera_id
                )
            }
            val xpos =
                if (align_right) location_x - first_line_xshift else location_x + first_line_xshift
            var height = applicationInterface.drawTextWithBackground(
                canvas,
                p,
                camera_id_string?:"",
                Color.WHITE,
                Color.BLACK,
                xpos,
                location_y,
                MyApplicationInterface.Alignment.ALIGNMENT_TOP,
                null,
                MyApplicationInterface.Shadow.SHADOW_OUTLINE,
                text_bounds_camera_id
            )
            height += gap_y
            // don't update location_y yet, as we have time and cameraid shown on the same line
            first_line_height = max(first_line_height, height)
        }
        // update location_y for first line (time and camera id)
        if (device_ui_rotation == 90) {
            // upside-down portrait
            location_y -= first_line_height
        } else {
            location_y += first_line_height
        }

        if (camera_controller != null && show_free_memory_pref) {
            if ((last_free_memory_time == 0L || time_ms > last_free_memory_time + 10000) && free_memory_future == null) {
                // don't call this too often, for UI performance

                free_memory_future = free_memory_executor.submit(free_memory_runnable)

                last_free_memory_time =
                    time_ms // always set this, so that in case of free memory not being available, we aren't calling freeMemory() every frame
            }
            if (free_memory_gb >= 0.0f && free_memory_gb_string != null) {
                //int height = applicationInterface.drawTextWithBackground(canvas, p, free_memory_gb_string, Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP);
                if (text_bounds_free_memory == null) {
                    if (MyDebug.LOG) Log.d(TAG, "compute text_bounds_free_memory")
                    text_bounds_free_memory = Rect()
                    p.getTextBounds(
                        free_memory_gb_string,
                        0,
                        free_memory_gb_string!!.length,
                        text_bounds_free_memory
                    )
                }
                var height = applicationInterface.drawTextWithBackground(
                    canvas,
                    p,
                    free_memory_gb_string?:"",
                    Color.WHITE,
                    Color.BLACK,
                    location_x,
                    location_y,
                    MyApplicationInterface.Alignment.ALIGNMENT_TOP,
                    null,
                    MyApplicationInterface.Shadow.SHADOW_OUTLINE,
                    text_bounds_free_memory
                )
                height += gap_y
                if (device_ui_rotation == 90) {
                    location_y -= height
                } else {
                    location_y += height
                }
            }
        }

        // Now draw additional info on the lower left corner if needed
        val y_offset = (27 * scale_font + 0.5f).toInt()
        p.setTextSize(24 * scale_font + 0.5f) // convert dps to pixels
        if (OSDLine1 != null && OSDLine1!!.length > 0) {
            applicationInterface.drawTextWithBackground(
                canvas,
                p,
                OSDLine1?:"",
                Color.WHITE,
                Color.BLACK,
                location_x,
                bottom_y - y_offset,
                MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM,
                null,
                MyApplicationInterface.Shadow.SHADOW_OUTLINE
            )
        }
        if (OSDLine2 != null && OSDLine2!!.length > 0) {
            applicationInterface.drawTextWithBackground(
                canvas,
                p,
                OSDLine2?:"",
                Color.WHITE,
                Color.BLACK,
                location_x,
                bottom_y,
                MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM,
                null,
                MyApplicationInterface.Shadow.SHADOW_OUTLINE
            )
        }
        p.textSize = 16 * scale_font + 0.5f // Restore text size

        if (camera_controller != null && show_iso_pref) {
            if (iso_exposure_string == null || time_ms > last_iso_exposure_time + 500) {
                iso_exposure_string = ""
                if (camera_controller.captureResultHasIso()) {
                    val iso = camera_controller.captureResultIso()
                    if (iso_exposure_string!!.isNotEmpty()) iso_exposure_string += " "
                    iso_exposure_string += preview.getISOString(iso)
                }
                if (camera_controller.captureResultHasExposureTime()) {
                    val exposureTime = camera_controller.captureResultExposureTime()
                    if (iso_exposure_string!!.isNotEmpty()) iso_exposure_string += " "
                    iso_exposure_string += preview.getExposureTimeString(exposureTime)
                }
                if (preview.isVideoRecording && camera_controller.captureResultHasFrameDuration()) {
                    val frame_duration = camera_controller.captureResultFrameDuration()
                    if (iso_exposure_string!!.isNotEmpty()) iso_exposure_string += " "
                    iso_exposure_string += preview.getFrameDurationString(frame_duration)
                }

                /*if( camera_controller.captureResultHasAperture() ) {
                    float aperture = camera_controller.captureResultAperture();
                    if( iso_exposure_string.length() > 0 )
                        iso_exposure_string += " F";
                    iso_exposure_string += decimal_format_1dp_force0.format(aperture);
                }*/
                is_scanning = false
                if (camera_controller.captureResultIsAEScanning()) {
                    // only show as scanning if in auto ISO mode (problem on Nexus 6 at least that if we're in manual ISO mode, after pausing and
                    // resuming, the camera driver continually reports CONTROL_AE_STATE_SEARCHING)
                    val value: String = sharedPreferences.getString(
                        PreferenceKeys.ISOPreferenceKey,
                        CameraController.ISO_DEFAULT
                    )!!
                    if (value == "auto") {
                        is_scanning = true
                    }
                }

                last_iso_exposure_time = time_ms
            }

            if (iso_exposure_string!!.length > 0) {
                var text_color = Color.rgb(255, 235, 59) // Yellow 500
                if (is_scanning) {
                    // we only change the color if ae scanning is at least a certain time, otherwise we get a lot of flickering of the color
                    if (ae_started_scanning_ms == -1L) {
                        ae_started_scanning_ms = time_ms
                    } else if (time_ms - ae_started_scanning_ms > 500) {
                        text_color = Color.rgb(244, 67, 54) // Red 500
                    }
                } else {
                    ae_started_scanning_ms = -1
                }
                // can't cache the bounds rect, as the width may change significantly as the ISO or exposure values change
                var height = applicationInterface.drawTextWithBackground(
                    canvas,
                    p,
                    iso_exposure_string?:"",
                    text_color,
                    Color.BLACK,
                    location_x,
                    location_y,
                    MyApplicationInterface.Alignment.ALIGNMENT_TOP,
                    ybounds_text,
                    MyApplicationInterface.Shadow.SHADOW_OUTLINE
                )
                height += gap_y
                // only move location_y if we actually print something (because on old camera API, even if the ISO option has
                // been enabled, we'll never be able to display the on-screen ISO)
                if (device_ui_rotation == 90) {
                    location_y -= height
                } else {
                    location_y += height
                }
            }
        }

        // padding to align with earlier text
        val flash_padding = (1 * scale_font + 0.5f).toInt() // convert dps to pixels

        if (camera_controller != null) {
            // draw info icons

            var location_x2 = location_x - flash_padding
            val icon_size = (16 * scale_dp + 0.5f).toInt() // convert dps to pixels
            if (device_ui_rotation == 180) {
                location_x2 = location_x - icon_size + flash_padding
            }

            if (store_location_pref) {
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)

                val location = applicationInterface.getLocation(locationInfo)
                if (location != null) {
                    canvas.drawBitmap(location_bitmap!!, null, icon_dest, p)
                    val location_radius = icon_size / 10
                    val indicator_x = location_x2 + icon_size - (location_radius * 1.5).toInt()
                    val indicator_y = location_y + (location_radius * 1.5).toInt()
                    p.setColor(
                        if (locationInfo.LocationWasCached()) Color.rgb(
                            127,
                            127,
                            127
                        ) else if (location.getAccuracy() < 25.01f) Color.rgb(
                            37,
                            155,
                            36
                        ) else Color.rgb(255, 235, 59)
                    ) // Green 500 or Yellow 500
                    canvas.drawCircle(
                        indicator_x.toFloat(),
                        indicator_y.toFloat(),
                        location_radius.toFloat(),
                        p
                    )
                } else {
                    canvas.drawBitmap(location_off_bitmap!!, null, icon_dest, p)
                }

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            }

            if (is_raw_pref && preview.supportsRaw() // RAW can be enabled, even if it isn't available for this camera (e.g., user enables RAW for back camera, but then
            // switches to front camera which doesn't support it)
            ) {
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                canvas.drawBitmap(
                    (if (is_raw_only_pref) raw_only_bitmap else raw_jpeg_bitmap)!!,
                    null,
                    icon_dest,
                    p
                )

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            }

            if (is_face_detection_pref && preview.supportsFaceDetection()) {
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                canvas.drawBitmap(face_detection_bitmap!!, null, icon_dest, p)

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            }

            if (this.storedAutoStabilisePref && preview.hasLevelAngleStable()) { // auto-level is supported for photos taken in video mode
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                canvas.drawBitmap(auto_stabilise_bitmap!!, null, icon_dest, p)

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            }

            if ((photoMode == PhotoMode.DRO || photoMode == PhotoMode.HDR || photoMode == PhotoMode.Panorama || photoMode == PhotoMode.ExpoBracketing ||  //photoMode == MyApplicationInterface.PhotoMode.FocusBracketing ||
                        photoMode == PhotoMode.FastBurst || photoMode == PhotoMode.NoiseReduction || photoMode == PhotoMode.X_Night || photoMode == PhotoMode.X_Bokeh || photoMode == PhotoMode.X_Beauty
                        ) &&
                !applicationInterface.isVideoPref()
            ) { // these photo modes not supported for video mode
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                val bitmap =
                    if (photoMode == PhotoMode.DRO) dro_bitmap else if (photoMode == PhotoMode.HDR) hdr_bitmap else if (photoMode == PhotoMode.Panorama) panorama_bitmap else if (photoMode == PhotoMode.ExpoBracketing) expo_bitmap else  //photoMode == MyApplicationInterface.PhotoMode.FocusBracketing ? focus_bracket_bitmap :
                        if (photoMode == PhotoMode.FastBurst) burst_bitmap else if (photoMode == PhotoMode.NoiseReduction) nr_bitmap else if (photoMode == PhotoMode.X_Night) x_night_bitmap else if (photoMode == PhotoMode.X_Bokeh) x_bokeh_bitmap else if (photoMode == PhotoMode.X_Beauty) x_beauty_bitmap else null
                if (bitmap != null) {
                    if (photoMode == PhotoMode.NoiseReduction && applicationInterface.getNRModePref() == ApplicationInterface.NRModePref.NRMODE_LOW_LIGHT) {
                        p.setColorFilter(
                            PorterDuffColorFilter(
                                Color.rgb(255, 235, 59),
                                PorterDuff.Mode.SRC_IN
                            )
                        ) // Yellow 500
                    }
                    canvas.drawBitmap(bitmap, null, icon_dest, p)
                    p.setColorFilter(null)

                    if (device_ui_rotation == 180) {
                        location_x2 -= icon_size + flash_padding
                    } else {
                        location_x2 += icon_size + flash_padding
                    }
                }
            }


            // photo-stamp is supported for photos taken in video mode
            // but it isn't supported in RAW-only mode
            if (this.storedHasStampPref && !(is_raw_only_pref && preview.supportsRaw())) {
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                canvas.drawBitmap(photostamp_bitmap!!, null, icon_dest, p)

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            }

            if (!is_audio_enabled_pref && applicationInterface.isVideoPref()) {
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                canvas.drawBitmap(audio_disabled_bitmap!!, null, icon_dest, p)

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            }

            // icons for slow motion, time lapse or high speed video
            if (abs(capture_rate_factor - 1.0f) > 1.0e-5 && applicationInterface.isVideoPref()) {
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                canvas.drawBitmap(
                    (if (capture_rate_factor < 1.0f) slow_motion_bitmap else time_lapse_bitmap)!!,
                    null,
                    icon_dest,
                    p
                )

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            } else if (is_high_speed && applicationInterface.isVideoPref()) {
                icon_dest.set(
                    location_x2,
                    location_y,
                    location_x2 + icon_size,
                    location_y + icon_size
                )
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.BLACK)
                p.setAlpha(64)
                canvas.drawRect(icon_dest, p)
                p.setAlpha(255)
                canvas.drawBitmap(high_speed_fps_bitmap!!, null, icon_dest, p)

                if (device_ui_rotation == 180) {
                    location_x2 -= icon_size + flash_padding
                } else {
                    location_x2 += icon_size + flash_padding
                }
            }

            if (time_ms > last_need_flash_indicator_time + 100) {
                need_flash_indicator = false
                val flash_value = preview.getCurrentFlashValue()
                // note, flash_frontscreen_auto not yet support for the flash symbol (as camera_controller.needsFlash() only returns info on the built-in actual flash, not frontscreen flash)
                if (flash_value != null &&
                    (flash_value == "flash_on"
                            || ((flash_value == "flash_auto" || flash_value == "flash_red_eye") && camera_controller.needsFlash())
                            || camera_controller.needsFrontScreenFlash()) && !applicationInterface.isVideoPref()
                ) { // flash-indicator not supported for photos taken in video mode
                    need_flash_indicator = true
                }

                last_need_flash_indicator_time = time_ms
            }
            if (need_flash_indicator) {
                if (needs_flash_time != -1L) {
                    val fade_ms: Long = 500
                    var alpha = (time_ms - needs_flash_time) / fade_ms.toFloat()
                    if (time_ms - needs_flash_time >= fade_ms) alpha = 1.0f
                    icon_dest.set(
                        location_x2,
                        location_y,
                        location_x2 + icon_size,
                        location_y + icon_size
                    )

                    /*if( MyDebug.LOG )
						Log.d(TAG, "alpha: " + alpha);*/
                    p.setStyle(Paint.Style.FILL)
                    p.setColor(Color.BLACK)
                    p.setAlpha((64 * alpha).toInt())
                    canvas.drawRect(icon_dest, p)
                    p.setAlpha((255 * alpha).toInt())
                    canvas.drawBitmap(flash_bitmap!!, null, icon_dest, p)
                    p.setAlpha(255)
                } else {
                    needs_flash_time = time_ms
                }
            } else {
                needs_flash_time = -1
            }

            if (device_ui_rotation == 90) {
                location_y -= icon_gap_y
            } else {
                location_y += (icon_size + icon_gap_y)
            }
        }

        if (camera_controller != null && !show_last_image) {
            // draw histogram
            if (preview.isPreviewBitmapEnabled()) {
                val histogram = preview.getHistogram()
                if (histogram != null) {
                    /*if( MyDebug.LOG )
						Log.d(TAG, "histogram length: " + histogram.length);*/
                    val histogram_width =
                        (histogram_width_dp * scale_dp + 0.5f).toInt() // convert dps to pixels
                    val histogram_height =
                        (histogram_height_dp * scale_dp + 0.5f).toInt() // convert dps to pixels
                    // n.b., if changing the histogram_height, remember to update focus_seekbar and
                    // focus_bracketing_target_seekbar margins in activity_main.xml
                    var location_x2 = location_x - flash_padding
                    if (device_ui_rotation == 180) {
                        location_x2 = location_x - histogram_width + flash_padding
                    }
                    icon_dest.set(
                        location_x2 - flash_padding,
                        location_y,
                        location_x2 - flash_padding + histogram_width,
                        location_y + histogram_height
                    )
                    if (device_ui_rotation == 90) {
                        icon_dest.top -= histogram_height
                        icon_dest.bottom -= histogram_height
                    }

                    p.setStyle(Paint.Style.FILL)
                    p.setColor(Color.argb(64, 0, 0, 0))
                    canvas.drawRect(icon_dest, p)

                    var max = 0
                    for (value in histogram) {
                        max = max(max, value)
                    }

                    if (histogram.size == 256 * 3) {
                        var c = 0

                        /* For overlapping rgb, we'll have:
							(1, (1-a2).(1-a1).a0.r, (1-a2).a1.g, a2.b)
						   If we wanted to have the alpha scaling the same (i.e., same r, g, b values
						   if r=g=b, then this gives:
						       a2 = 1/[2+1/a0]
                               a1 = 1 - a2/[a0.(1-a2)]
                           However this then means that for non-overlapping colours, red is too
                           strong whilst blue is too weak, so we instead adjust to:
                               a0' = (a0+a1)/2
                               a1' = a1
                               a2' = (a1+a2)/2
						 */
                        /*final int a0 = 255;
						final int a1 = 128;
						final int a2 = 85;*/
                        //final int a0 = 191;
                        val a0 = 151
                        val a1 = 110
                        //final int a2 = 77;
                        val a2 = 94
                        /*final int a0 = 128;
						final int a1 = 85;
						final int a2 = 64;*/
                        val r = 255
                        val g = 255
                        val b = 255

                        for (i in 0..255) temp_histogram_channel[i] = histogram[c++]
                        p.setColor(Color.argb(a0, r, 0, 0))
                        drawHistogramChannel(canvas, temp_histogram_channel, max)

                        for (i in 0..255) temp_histogram_channel[i] = histogram[c++]
                        p.setColor(Color.argb(a1, 0, g, 0))
                        drawHistogramChannel(canvas, temp_histogram_channel, max)

                        for (i in 0..255) temp_histogram_channel[i] = histogram[c++]
                        p.setColor(Color.argb(a2, 0, 0, b))
                        drawHistogramChannel(canvas, temp_histogram_channel, max)
                    } else {
                        p.setColor(Color.argb(192, 255, 255, 255))
                        drawHistogramChannel(canvas, histogram, max)
                    }
                }
            }
        }
    }

    /** Draws histogram for a single color channel.
     * @param canvas Canvas to draw onto.
     * @param histogram_channel The histogram for this color.
     * @param max The maximum value of histogram_channel, or if drawing multiple channels, this
     * should be the maximum value of all histogram channels.
     */
    private fun drawHistogramChannel(canvas: Canvas, histogram_channel: IntArray, max: Int) {
        /*long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }*/

        /*if( MyDebug.LOG )
			Log.d(TAG, "drawHistogramChannel, time before creating path: " + (System.currentTimeMillis() - debug_time));*/

        path.reset()
        path.moveTo(icon_dest.left.toFloat(), icon_dest.bottom.toFloat())
        for (c in histogram_channel.indices) {
            val c_alpha = c / histogram_channel.size.toDouble()
            val x = (c_alpha * icon_dest.width()).toInt()
            val h = (histogram_channel[c] * icon_dest.height()) / max
            path.lineTo((icon_dest.left + x).toFloat(), (icon_dest.bottom - h).toFloat())
        }
        path.lineTo(icon_dest.right.toFloat(), icon_dest.bottom.toFloat())
        path.close()
        /*if( MyDebug.LOG )
			Log.d(TAG, "drawHistogramChannel, time after creating path: " + (System.currentTimeMillis() - debug_time));*/
        canvas.drawPath(path, p)
        /*if( MyDebug.LOG )
			Log.d(TAG, "drawHistogramChannel, time before drawing path: " + (System.currentTimeMillis() - debug_time));*/
    }

    /** This includes drawing of the UI that requires the canvas to be rotated according to the preview's
     * current UI rotation.
     */
    private fun drawUI(canvas: Canvas, device_ui_rotation: Int, time_ms: Long) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        val ui_rotation = preview.getUIRotation()
        val ui_placement = mainActivity.mainUI!!.uIPlacement
        val has_level_angle = preview.hasLevelAngle()
        val level_angle = preview.getLevelAngle()
        val has_geo_direction = preview.hasGeoDirection()
        val geo_direction = preview.getGeoDirection()
        val system_orientation = mainActivity.systemOrientation
        val system_orientation_portrait = system_orientation == SystemOrientation.PORTRAIT
        var text_base_y = 0

        canvas.save()
        canvas.rotate(ui_rotation.toFloat(), canvas.width / 2.0f, canvas.height / 2.0f)

        if (camera_controller != null && !preview.isPreviewPaused()) {
            /*canvas.drawText("PREVIEW", canvas.width / 2,
					canvas.height / 2, p);*/

            val gap_y = (20 * scale_font + 0.5f).toInt() // convert dps to pixels
            val text_y = (16 * scale_font + 0.5f).toInt() // convert dps to pixels
            var avoid_ui = false
            // fine tuning to adjust placement of text with respect to the GUI, depending on orientation
            if (ui_placement == UIPlacement.UIPLACEMENT_TOP && (device_ui_rotation == 0 || device_ui_rotation == 180)) {
                text_base_y = canvas.height - (0.1 * gap_y).toInt()
                if (device_ui_rotation == 0) avoid_ui = true
            } else if (device_ui_rotation == (if (ui_placement == UIPlacement.UIPLACEMENT_RIGHT) 0 else 180)) {
                text_base_y = canvas.height - (0.1 * gap_y).toInt()
                avoid_ui = true
            } else if (device_ui_rotation == (if (ui_placement == UIPlacement.UIPLACEMENT_RIGHT) 180 else 0)) {
                text_base_y = canvas.height - (2.5 * gap_y).toInt() // leave room for GUI icons
            } else if (device_ui_rotation == 90 || device_ui_rotation == 270) {
                // 90 is upside down portrait
                // 270 is portrait

                if (last_take_photo_top_time == 0L || time_ms > last_take_photo_top_time + 1000) {
                    // don't call this too often, for UI performance (due to calling View.getLocationOnScreen())
                    preview.getView().getLocationOnScreen(gui_location)
                    val this_left = gui_location[if (system_orientation_portrait) 1 else 0]
                    take_photo_top = this_left

                    last_take_photo_top_time = time_ms
                }

                // diff_x is the difference from the centre of the canvas to the position we want
                var max_x =
                    if (system_orientation_portrait) canvas.height else canvas.width
                val mid_x = max_x / 2
                var diff_x = take_photo_top - mid_x

                // diff_x is the difference from the centre of the canvas to the position we want
                // assumes canvas is centered
                // avoids calling getLocationOnScreen for performance
                /*int offset_x = (int) (124 * scale + 0.5f); // convert dps to pixels
                // offset_x should be enough such that on-screen level angle (this is the lowest display on-screen text) does not
                // interfere with take photo icon when using at least a 16:9 preview aspect ratio
                // should correspond to the logged "compare offset_x" above
                int diff_x = preview.getView().getRootView().getRight()/2 - offset_x;
                */
                if (device_ui_rotation == 90) {
                    // so we don't interfere with the top bar info (datetime, free memory, ISO) when upside down
                    max_x -= (2.5 * gap_y).toInt()
                }
                /*if( MyDebug.LOG ) {
					Log.d(TAG, "root view right: " + preview.getView().getRootView().getRight());
					Log.d(TAG, "diff_x: " + diff_x);
					Log.d(TAG, "canvas.width/2 + diff_x: " + (canvas.width/2+diff_x));
					Log.d(TAG, "max_x: " + max_x);
				}*/
                if (mid_x + diff_x > max_x) {
                    // in case goes off the size of the canvas, for "black bar" cases (when preview aspect ratio < screen aspect ratio)
                    diff_x = max_x - mid_x
                }
                text_base_y = canvas.height / 2 + diff_x - (0.5 * gap_y).toInt()
            }

            if (device_ui_rotation == 0 || device_ui_rotation == 180) {
                // also avoid navigation bar in (reverse) landscape (for e.g. OnePlus Pad which has a landscape navigation bar when in landscape orientation)
                val navigation_gap =
                    if (device_ui_rotation == 0) mainActivity.navigationGapLandscape else mainActivity.navigationGapReverseLandscape
                text_base_y -= navigation_gap
            }

            if (avoid_ui) {
                // avoid parts of the UI
                var view = mainActivity.binding.focusSeekbar
                if (view.isVisible) {
                    text_base_y -= view.height
                }
                view = mainActivity.binding.focusBracketingTargetSeekbar
                if (view.isVisible) {
                    text_base_y -= view.height
                }
            }

            val draw_angle = has_level_angle && show_angle_pref
            val draw_geo_direction = has_geo_direction && show_geo_direction_pref
            if (draw_angle) {
                var color = Color.WHITE
                p.setTextSize(14 * scale_font + 0.5f) // convert dps to pixels
                val pixels_offset_x: Int
                if (draw_geo_direction) {
                    pixels_offset_x = -(35 * scale_font + 0.5f).toInt() // convert dps to pixels
                    p.textAlign = Paint.Align.LEFT
                } else {
                    //p.setTextAlign(Paint.Align.CENTER);
                    // slightly better for performance to use Align.LEFT, due to avoid measureText() call in drawTextWithBackground()
                    pixels_offset_x =
                        -((if (level_angle < 0) 16 else 14) * scale_font + 0.5f).toInt() // convert dps to pixels
                    p.textAlign = Paint.Align.LEFT
                }
                if (abs(level_angle) <= close_level_angle) {
                    color = angle_highlight_color_pref
                    p.isUnderlineText = true
                }
                if (angle_string == null || time_ms > this.last_angle_string_time + 500) {
                    // update cached string
                    /*if( MyDebug.LOG )
						Log.d(TAG, "update angle_string: " + angle_string);*/
                    last_angle_string_time = time_ms
                    val number_string: String = formatLevelAngle(level_angle)
                    //String number_string = "" + level_angle;
                    angle_string = number_string + 0x00B0.toChar()
                    cached_angle = level_angle
                    //String angle_string = "" + level_angle;
                }
                //applicationInterface.drawTextWithBackground(canvas, p, angle_string, color, Color.BLACK, canvas.width / 2 + pixels_offset_x, text_base_y, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, ybounds_text, true);
                if (text_bounds_angle_single == null) {
                    if (MyDebug.LOG) Log.d(TAG, "compute text_bounds_angle_single")
                    text_bounds_angle_single = Rect()
                    val bounds_angle_string = "-9.0" + 0x00B0.toChar()
                    p.getTextBounds(
                        bounds_angle_string,
                        0,
                        bounds_angle_string.length,
                        text_bounds_angle_single
                    )
                }
                if (text_bounds_angle_double == null) {
                    if (MyDebug.LOG) Log.d(TAG, "compute text_bounds_angle_double")
                    text_bounds_angle_double = Rect()
                    val bounds_angle_string = "-45.0" + 0x00B0.toChar()
                    p.getTextBounds(
                        bounds_angle_string,
                        0,
                        bounds_angle_string.length,
                        text_bounds_angle_double
                    )
                }
                applicationInterface.drawTextWithBackground(
                    canvas,
                    p,
                    angle_string?:"",
                    color,
                    Color.BLACK,
                    canvas.width / 2 + pixels_offset_x,
                    text_base_y,
                    MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM,
                    null,
                    MyApplicationInterface.Shadow.SHADOW_OUTLINE,
                    if (abs(cached_angle) < 10.0) text_bounds_angle_single else text_bounds_angle_double
                )
                p.isUnderlineText = false
            }
            if (draw_geo_direction) {
                val color = Color.WHITE
                p.setTextSize(14 * scale_font + 0.5f) // convert dps to pixels
                val pixels_offset_x: Int
                if (draw_angle) {
                    pixels_offset_x = (10 * scale_font + 0.5f).toInt() // convert dps to pixels
                    p.textAlign = Paint.Align.LEFT
                } else {
                    //p.setTextAlign(Paint.Align.CENTER);
                    // slightly better for performance to use Align.LEFT, due to avoid measureText() call in drawTextWithBackground()
                    pixels_offset_x = -(14 * scale_font + 0.5f).toInt() // convert dps to pixels
                    p.textAlign = Paint.Align.LEFT
                }
                var geo_angle = Math.toDegrees(geo_direction).toFloat()
                if (geo_angle < 0.0f) {
                    geo_angle += 360.0f
                }
                val string = Math.round(geo_angle).toString() + 0x00B0.toChar()
                applicationInterface.drawTextWithBackground(
                    canvas,
                    p,
                    string,
                    color,
                    Color.BLACK,
                    canvas.width / 2 + pixels_offset_x,
                    text_base_y,
                    MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM,
                    ybounds_text,
                    MyApplicationInterface.Shadow.SHADOW_OUTLINE
                )
            }
            if (preview.isOnTimer()) {
                val remaining_time = (preview.timerEndTime - time_ms + 999) / 1000
                if (MyDebug.LOG) Log.d(TAG, "remaining_time: " + remaining_time)
                if (remaining_time > 0) {
                    p.textSize = 42 * scale_font + 0.5f // convert dps to pixels
                    p.textAlign = Paint.Align.CENTER
                    val time_s: String?
                    if (remaining_time < 60) {
                        // simpler to just show seconds when less than a minute
                        time_s = remaining_time.toString()
                    } else {
                        time_s = getTimeStringFromSeconds(remaining_time)
                    }
                    applicationInterface.drawTextWithBackground(
                        canvas,
                        p,
                        time_s,
                        Color.rgb(244, 67, 54),
                        Color.BLACK,
                        canvas.width / 2,
                        canvas.height / 2
                    ) // Red 500
                }
            } else if (preview.isVideoRecording()) {
                val video_time = preview.getVideoTime(false)
                val time_s = getTimeStringFromSeconds(video_time / 1000)
                /*if( MyDebug.LOG )
					Log.d(TAG, "video_time: " + video_time + " " + time_s);*/
                p.textSize = 14 * scale_font + 0.5f // convert dps to pixels
                p.textAlign = Paint.Align.CENTER
                var pixels_offset_y = 2 * text_y // avoid overwriting the zoom
                val color = Color.rgb(244, 67, 54) // Red 500
                if (mainActivity.isScreenLocked) {
                    // writing in reverse order, bottom to top
                    applicationInterface.drawTextWithBackground(
                        canvas,
                        p,
                        context.resources.getString(R.string.screen_lock_message_2),
                        color,
                        Color.BLACK,
                        canvas.width / 2,
                        text_base_y - pixels_offset_y
                    )
                    pixels_offset_y += text_y
                    applicationInterface.drawTextWithBackground(
                        canvas,
                        p,
                        context.resources.getString(R.string.screen_lock_message_1),
                        color,
                        Color.BLACK,
                        canvas.width / 2,
                        text_base_y - pixels_offset_y
                    )
                    pixels_offset_y += text_y
                }
                if (!preview.isVideoRecordingPaused || ((time_ms / 500).toInt()) % 2 == 0) { // if video is paused, then flash the video time
                    applicationInterface.drawTextWithBackground(
                        canvas,
                        p,
                        time_s,
                        color,
                        Color.BLACK,
                        canvas.width / 2,
                        text_base_y - pixels_offset_y
                    )
                    pixels_offset_y += text_y
                }
                if (show_video_max_amp_pref && !preview.isVideoRecordingPaused) {
                    // audio amplitude
                    if (!this.has_video_max_amp || time_ms > this.last_video_max_amp_time + 50) {
                        has_video_max_amp = true
                        val video_max_amp_prev1 = video_max_amp_prev2
                        video_max_amp_prev2 = video_max_amp
                        video_max_amp = preview.maxAmplitude
                        last_video_max_amp_time = time_ms
                        if (MyDebug.LOG) {
                            if (video_max_amp > 30000) {
                                Log.d(TAG, "max_amp: " + video_max_amp)
                            }
                            if (video_max_amp > 32767) {
                                Log.e(TAG, "video_max_amp greater than max: " + video_max_amp)
                            }
                        }
                        if (video_max_amp_prev2 > video_max_amp_prev1 && video_max_amp_prev2 > video_max_amp) {
                            // new peak
                            video_max_amp_peak = video_max_amp_prev2
                        }
                        //video_max_amp_peak = Math.max(video_max_amp_peak, video_max_amp);
                    }
                    var amp_frac = video_max_amp / 32767.0f
                    amp_frac = max(amp_frac, 0.0f)
                    amp_frac = min(amp_frac, 1.0f)

                    //applicationInterface.drawTextWithBackground(canvas, p, "" + max_amp, color, Color.BLACK, canvas.width / 2, text_base_y - pixels_offset_y);
                    pixels_offset_y += text_y // allow extra space
                    val amp_width = (160 * scale_dp + 0.5f).toInt() // convert dps to pixels
                    val amp_height = (10 * scale_dp + 0.5f).toInt() // convert dps to pixels
                    val amp_x = (canvas.width - amp_width) / 2
                    p.color = Color.WHITE
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = stroke_width
                    canvas.drawRect(
                        amp_x.toFloat(),
                        (text_base_y - pixels_offset_y).toFloat(),
                        (amp_x + amp_width).toFloat(),
                        (text_base_y - pixels_offset_y + amp_height).toFloat(),
                        p
                    )
                    p.style = Paint.Style.FILL
                    canvas.drawRect(
                        amp_x.toFloat(),
                        (text_base_y - pixels_offset_y).toFloat(),
                        amp_x + amp_frac * amp_width,
                        (text_base_y - pixels_offset_y + amp_height).toFloat(),
                        p
                    )
                    if (amp_frac < 1.0f) {
                        p.color = Color.BLACK
                        p.alpha = 64
                        canvas.drawRect(
                            amp_x + amp_frac * amp_width + 1,
                            (text_base_y - pixels_offset_y).toFloat(),
                            (amp_x + amp_width).toFloat(),
                            (text_base_y - pixels_offset_y + amp_height).toFloat(),
                            p
                        )
                        p.alpha = 255
                    }
                    if (video_max_amp_peak > video_max_amp) {
                        var peak_frac = video_max_amp_peak / 32767.0f
                        peak_frac = max(peak_frac, 0.0f)
                        peak_frac = min(peak_frac, 1.0f)
                        p.color = Color.YELLOW
                        p.style = Paint.Style.STROKE
                        p.strokeWidth = stroke_width
                        canvas.drawLine(
                            amp_x + peak_frac * amp_width,
                            (text_base_y - pixels_offset_y).toFloat(),
                            amp_x + peak_frac * amp_width,
                            (text_base_y - pixels_offset_y + amp_height).toFloat(),
                            p
                        )
                        p.color = Color.WHITE
                    }
                }
            } else if (taking_picture && capture_started) {
                if (camera_controller.isCapturingBurst()) {
                    val n_burst_taken = camera_controller.getNBurstTaken() + 1
                    val n_burst_total = camera_controller.getBurstTotal()
                    p.textSize = 14 * scale_font + 0.5f // convert dps to pixels
                    p.textAlign = Paint.Align.CENTER
                    var pixels_offset_y = 2 * text_y // avoid overwriting the zoom
                    if (device_ui_rotation == 0 && applicationInterface.photoMode == PhotoMode.FocusBracketing) {
                        // avoid clashing with the target focus bracketing seekbar in landscape orientation
                        pixels_offset_y = 5 * gap_y
                    }
                    var text = context.resources
                        .getString(R.string.capturing) + " " + n_burst_taken
                    if (n_burst_total > 0) {
                        text += " / " + n_burst_total
                    }
                    applicationInterface.drawTextWithBackground(
                        canvas,
                        p,
                        text,
                        Color.WHITE,
                        Color.BLACK,
                        canvas.width / 2,
                        text_base_y - pixels_offset_y
                    )
                } else if (camera_controller.isManualISO()) {
                    // only show "capturing" text with time for manual exposure time >= 0.5s
                    val exposure_time = camera_controller.getExposureTime()
                    if (exposure_time >= 500000000L) {
                        if (((time_ms / 500).toInt()) % 2 == 0) {
                            p.textSize = 14 * scale_font + 0.5f // convert dps to pixels
                            p.textAlign = Paint.Align.CENTER
                            val pixels_offset_y = 2 * text_y // avoid overwriting the zoom
                            val color = Color.rgb(244, 67, 54) // Red 500
                            applicationInterface.drawTextWithBackground(
                                canvas,
                                p,
                                context.resources.getString(R.string.capturing),
                                color,
                                Color.BLACK,
                                canvas.width / 2,
                                text_base_y - pixels_offset_y
                            )
                        }
                    }
                }
            } else if (image_queue_full) {
                if (((time_ms / 500).toInt()) % 2 == 0) {
                    p.textSize = 14 * scale_font + 0.5f // convert dps to pixels
                    p.textAlign = Paint.Align.CENTER
                    val pixels_offset_y = 2 * text_y // avoid overwriting the zoom
                    val n_images_to_save = applicationInterface.imageSaver?.nRealImagesToSave
                    val string = context.resources
                        .getString(R.string.processing) + " (" + n_images_to_save + " " + context.resources
                        .getString(R.string.remaining) + ")"
                    applicationInterface.drawTextWithBackground(
                        canvas,
                        p,
                        string,
                        Color.LTGRAY,
                        Color.BLACK,
                        canvas.width / 2,
                        text_base_y - pixels_offset_y
                    )
                }
            }

            if (preview.supportsZoom() && show_zoom_pref) {
                val zoom_ratio = preview.getZoomRatio()
                // only show when actually zoomed in - or out!
                // but only show if zoomed in by at least 1.1x, to avoid showing when only very slightly
                // zoomed in - otherwise on devices that support zooming out to ultrawide, it's hard to
                // zoom back to exactly 1.0x
                //if( zoom_ratio < 1.0f - 1.0e-5f || zoom_ratio > 1.0f + 1.0e-5f ) {
                if (zoom_ratio < 1.0f - 1.0e-5f || zoom_ratio > 1.1f - 1.0e-5f) {
                    // Convert the dps to pixels, based on density scale
                    p.textSize = 14 * scale_font + 0.5f // convert dps to pixels
                    p.textAlign = Paint.Align.CENTER
                    applicationInterface.drawTextWithBackground(
                        canvas,
                        p,
                        context.resources
                            .getString(R.string.zoom) + ": " + zoom_ratio + "x",
                        Color.WHITE,
                        Color.BLACK,
                        canvas.width / 2,
                        text_base_y - text_y,
                        MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM,
                        ybounds_text,
                        MyApplicationInterface.Shadow.SHADOW_OUTLINE
                    )
                }
            }
        } else if (camera_controller == null) {
            /*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.width + " height " + canvas.getHeight());
			}*/
            p.color = Color.WHITE
            p.textSize = 14 * scale_font + 0.5f // convert dps to pixels
            p.textAlign = Paint.Align.CENTER
            val pixels_offset = (20 * scale_font + 0.5f).toInt() // convert dps to pixels
            if (preview.hasPermissions()) {
                if (preview.openCameraFailed()) {
                    canvas.drawText(
                        context.resources.getString(R.string.failed_to_open_camera_1),
                        canvas.width / 2.0f,
                        canvas.height / 2.0f,
                        p
                    )
                    canvas.drawText(
                        context.resources.getString(R.string.failed_to_open_camera_2),
                        canvas.width / 2.0f,
                        canvas.height / 2.0f + pixels_offset,
                        p
                    )
                    canvas.drawText(
                        context.resources.getString(R.string.failed_to_open_camera_3),
                        canvas.width / 2.0f,
                        canvas.height / 2.0f + 2 * pixels_offset,
                        p
                    )
                    // n.b., use applicationInterface.getCameraIdPref(), as preview.getCameraId() returns 0 if camera_controller==null
                    canvas.drawText(
                        context.resources
                            .getString(R.string.camera_id) + ":" + applicationInterface.getCameraIdPref(),
                        canvas.width / 2.0f,
                        canvas.height / 2.0f + 3 * pixels_offset,
                        p
                    )
                }
            } else {
                canvas.drawText(
                    context.resources.getString(R.string.no_permission),
                    canvas.width / 2.0f,
                    canvas.height / 2.0f,
                    p
                )
            }
            //canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
            //canvas.drawRGB(255, 0, 0);
            //canvas.drawRect(0.0f, 0.0f, canvas.width, canvas.height, p);
        }

        var top_x = (5 * scale_dp + 0.5f).toInt() // convert dps to pixels
        var top_y = (5 * scale_dp + 0.5f).toInt() // convert dps to pixels
        val top_icon = mainActivity.mainUI!!.topIcon
        if (top_icon != null) {
            if (last_top_icon_shift_time == 0L || time_ms > last_top_icon_shift_time + 1000) {
                // avoid computing every time, due to cost of calling View.getLocationOnScreen()
                /*if( MyDebug.LOG )
                    Log.d(TAG, "update cached top_icon_shift");*/
                var top_margin = getViewOnScreenX(top_icon)
                if (system_orientation == SystemOrientation.LANDSCAPE) top_margin += top_icon.width
                else if (system_orientation == SystemOrientation.PORTRAIT) top_margin += top_icon.height
                // n.b., don't adjust top_margin for icon width/height for an reverse orientation
                preview.view.getLocationOnScreen(gui_location)
                var preview_left = gui_location[if (system_orientation_portrait) 1 else 0]
                if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) preview_left += preview.getView()
                    .width // actually want preview-right for reverse landscape

                this.top_icon_shift = top_margin - preview_left
                if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) this.top_icon_shift =
                    -this.top_icon_shift

                /*if( MyDebug.LOG ) {
                    Log.d(TAG, "top_icon.getRotation(): " + top_icon.getRotation());
                    Log.d(TAG, "preview_left: " + preview_left);
                    Log.d(TAG, "top_margin: " + top_margin);
                    Log.d(TAG, "top_icon_shift: " + top_icon_shift);
                }*/
                last_top_icon_shift_time = time_ms
            }

            if (this.top_icon_shift > 0) {
                if (device_ui_rotation == 90 || device_ui_rotation == 270) {
                    // portrait
                    top_y += top_icon_shift
                } else {
                    // landscape
                    top_x += top_icon_shift
                }
            }
        }

//        run {
//            /*int focus_seekbars_margin_left_dp = 85;
//                       if( want_histogram )
//                           focus_seekbars_margin_left_dp += DrawPreview.histogram_height_dp;*/
//            // 135 needed to make room for on-screen info lines in DrawPreview.onDrawInfoLines(), including the histogram
//            // but we also need to take the top_icon_shift into account, for widescreen aspect ratios and "icons along top" UI placement
//            val focus_seekbars_margin_left_dp = 135
//            var new_focus_seekbars_margin_left =
//                (focus_seekbars_margin_left_dp * scale_dp + 0.5f).toInt() // convert dps to pixels
//            if (top_icon_shift > 0) {
//                new_focus_seekbars_margin_left += top_icon_shift
//            }
//            if (focus_seekbars_margin_left == -1 || new_focus_seekbars_margin_left != focus_seekbars_margin_left) {
//                // we check whether focus_seekbars_margin_left has changed, in case there is a performance cost for setting layoutparams
//                this.focus_seekbars_margin_left = new_focus_seekbars_margin_left
//                if (MyDebug.LOG) Log.d(
//                    TAG,
//                    "set focus_seekbars_margin_left to " + focus_seekbars_margin_left
//                )
//
//                // "left" and "right" here are written assuming we're in landscape system orientation
//                var view = mainActivity.findViewById<View>(R.id.focus_seekbar)
//                var layoutParams = view.layoutParams as LinearLayout.LayoutParams
//                preview.view.getLocationOnScreen(gui_location)
//                var preview_left = gui_location[if (system_orientation_portrait) 1 else 0]
//                if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) preview_left += preview.view
//                    .width // actually want preview-right for reverse landscape
//
//
//                view.getLocationOnScreen(gui_location)
//                var seekbar_right = gui_location[if (system_orientation_portrait) 1 else 0]
//                if (system_orientation == SystemOrientation.LANDSCAPE || system_orientation == SystemOrientation.PORTRAIT) {
//                    // n.b., we read view.width even if system_orientation is portrait, because the seekbar is rotated in portrait orientation
//                    seekbar_right += view.width
//                } else {
//                    // and for reversed landscape, the seekbar is rotated 180 degrees, and getLocationOnScreen() returns the location after the rotation
//                    seekbar_right -= view.width
//                }
//
//                val min_seekbar_width = (150 * scale_dp + 0.5f).toInt() // convert dps to pixels
//                var new_seekbar_width: Int
//                if (system_orientation == SystemOrientation.LANDSCAPE || system_orientation == SystemOrientation.PORTRAIT) {
//                    new_seekbar_width = seekbar_right - (preview_left + focus_seekbars_margin_left)
//                } else {
//                    // reversed landscape
//                    new_seekbar_width = preview_left - focus_seekbars_margin_left - seekbar_right
//                }
//                new_seekbar_width = max(new_seekbar_width, min_seekbar_width)
//                layoutParams.width = new_seekbar_width
//                view.layoutParams = layoutParams
//
//                view = mainActivity.findViewById<View>(R.id.focus_bracketing_target_seekbar)
//                layoutParams = view.layoutParams as LinearLayout.LayoutParams
//                layoutParams.width = new_seekbar_width
//                view.layoutParams = layoutParams
//
//                // need to update due to changing width of focus seekbars
//                mainActivity.mainUI!!.setFocusSeekbarsRotation()
//            }
//        }

        var battery_x = top_x
        var battery_y = top_y + (5 * scale_dp + 0.5f).toInt()
        val battery_width = (5 * scale_dp + 0.5f).toInt() // convert dps to pixels
        val battery_height = 4 * battery_width
        if (ui_rotation == 90 || ui_rotation == 270) {
            // n.b., this is only for when lock_to_landscape==true, so we don't look at device_ui_rotation
            val diff = canvas.width - canvas.height
            battery_x += diff / 2
            battery_y -= diff / 2
        }
        if (device_ui_rotation == 90) {
            battery_y = canvas.height - battery_y - battery_height
        }
        if (device_ui_rotation == 180) {
            battery_x = canvas.width - battery_x - battery_width
        }
        if (show_battery_pref) {
            if (!this.has_battery_frac || time_ms > this.last_battery_time + 60000) {
                // only check periodically - unclear if checking is costly in any way
                // note that it's fine to call registerReceiver repeatedly - we pass a null receiver, so this is fine as a "one shot" use
                val batteryStatus = mainActivity.registerReceiver(null, battery_ifilter)
                val battery_level = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                has_battery_frac = true
                battery_frac = battery_level / battery_scale.toFloat()
                last_battery_time = time_ms
                if (MyDebug.LOG) Log.d(
                    TAG,
                    "Battery status is $battery_level / $battery_scale : $battery_frac"
                )
            }
            //battery_frac = 0.2999f; // test
            var draw_battery = true
            if (battery_frac <= 0.05f) {
                // flash icon at this low level
                draw_battery = (((time_ms / 1000)) % 2) == 0L
            }
            if (draw_battery) {
                p.color = if (battery_frac > 0.15f) Color.rgb(37, 155, 36) else Color.rgb(
                    244,
                    67,
                    54
                ) // Green 500 or Red 500
                p.style = Paint.Style.FILL
                canvas.drawRect(
                    battery_x.toFloat(),
                    battery_y + (1.0f - battery_frac) * (battery_height - 2),
                    (battery_x + battery_width).toFloat(),
                    (battery_y + battery_height).toFloat(),
                    p
                )
                if (battery_frac < 1.0f) {
                    p.color = Color.BLACK
                    p.alpha = 64
                    canvas.drawRect(
                        battery_x.toFloat(),
                        battery_y.toFloat(),
                        (battery_x + battery_width).toFloat(),
                        battery_y + (1.0f - battery_frac) * (battery_height - 2),
                        p
                    )
                    p.alpha = 255
                }
            }
            top_x += (10 * scale_dp + 0.5f).toInt() // convert dps to pixels
        }

        onDrawInfoLines(canvas, top_x, top_y, text_base_y, device_ui_rotation, time_ms)

        canvas.restore()
    }

    private val angleStep: Int
        get() {
            val preview = mainActivity.preview
            var angle_step = 10
            val zoom_ratio = preview!!.getZoomRatio()
            if (zoom_ratio >= 10.0f) angle_step = 1
            else if (zoom_ratio >= 5.0f) angle_step = 2
            else if (zoom_ratio >= 2.0f) angle_step = 5
            return angle_step
        }

    private fun drawAngleLines(canvas: Canvas, device_ui_rotation: Int, time_ms: Long) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        val system_orientation = mainActivity.systemOrientation
        val system_orientation_portrait = system_orientation == SystemOrientation.PORTRAIT
        val has_level_angle = preview.hasLevelAngle()
        val actual_show_angle_line_pref: Boolean
        if (photoMode == PhotoMode.Panorama) {
            // in panorama mode, we should the level iff we aren't taking the panorama photos
            actual_show_angle_line_pref =
                !mainActivity.applicationInterface!!.gyroSensor.isRecording()
        } else actual_show_angle_line_pref = show_angle_line_pref

        val allow_angle_lines = camera_controller != null && !preview.isPreviewPaused()

        if (allow_angle_lines && has_level_angle && (actual_show_angle_line_pref || show_pitch_lines_pref || show_geo_direction_lines_pref)) {
            val level_angle = preview.getLevelAngle()
            val has_pitch_angle = preview.hasPitchAngle()
            val pitch_angle = preview.getPitchAngle()
            val has_geo_direction = preview.hasGeoDirection()
            val geo_direction = preview.getGeoDirection()
            // n.b., must draw this without the standard canvas rotation
            // lines should be shorter in portrait
            val radius_dps = if (device_ui_rotation == 90 || device_ui_rotation == 270) 60 else 80
            val radius = (radius_dps * scale_dp + 0.5f).toInt() // convert dps to pixels
            val o_radius = (10 * scale_dp + 0.5f).toInt() // convert dps to pixels
            var angle = -preview.getOrigLevelAngle()
            // see http://android-developers.blogspot.co.uk/2010/09/one-screen-turn-deserves-another.html
            val rotation = mainActivity.getDisplayRotation(false)
            when (rotation) {
                Surface.ROTATION_90 -> angle -= 90.0
                Surface.ROTATION_270 -> angle += 90.0
                Surface.ROTATION_180 -> angle += 180.0
                Surface.ROTATION_0 -> {}
                else -> {}
            }
            /*if( MyDebug.LOG ) {
                Log.d(TAG, "system_orientation: " + system_orientation);
                Log.d(TAG, "rotation: " + rotation);
            }*/
            /*if( MyDebug.LOG ) {
				Log.d(TAG, "orig_level_angle: " + preview.getOrigLevelAngle());
				Log.d(TAG, "angle: " + angle);
			}*/
            val cx = canvas.width / 2
            val cy = canvas.height / 2

            var is_level = false
            if (has_level_angle && abs(level_angle) <= close_level_angle) { // n.b., use level_angle, not angle or orig_level_angle
                is_level = true
            }

            val line_alpha = 160
            val hthickness = (0.5f * scale_dp + 0.5f) // convert dps to pixels
            var shadow_radius = hthickness
            shadow_radius = max(shadow_radius, 1.0f)
            p.setStyle(Paint.Style.FILL)

            if (actual_show_angle_line_pref && preview.hasLevelAngleStable()) {
                // draw the non-rotated part of the level
                // only show the angle line if level angle "stable" (i.e., not pointing near vertically up or down)

                p.setShadowLayer(shadow_radius, 0.0f, 0.0f, Color.BLACK)

                if (is_level) {
                    p.setColor(angle_highlight_color_pref)
                } else {
                    p.setColor(Color.WHITE)
                }
                p.setAlpha(line_alpha)
                draw_rect.set(
                    (cx - radius - o_radius).toFloat(),
                    cy - hthickness,
                    (cx - radius).toFloat(),
                    cy + hthickness
                )
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p)
                draw_rect.set(
                    (cx + radius).toFloat(),
                    cy - hthickness,
                    (cx + radius + o_radius).toFloat(),
                    cy + hthickness
                )
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p)

                p.clearShadowLayer()
            }

            canvas.save()
            canvas.rotate(angle.toFloat(), cx.toFloat(), cy.toFloat())

            if (actual_show_angle_line_pref && preview.hasLevelAngleStable()) {
                // only show the angle line if level angle "stable" (i.e., not pointing near vertically up or down)

                p.setShadowLayer(shadow_radius, 0.0f, 0.0f, Color.BLACK)

                if (is_level) {
                    p.setColor(angle_highlight_color_pref)
                } else {
                    p.setColor(Color.WHITE)
                }
                p.setAlpha(line_alpha)
                draw_rect.set(
                    (cx - radius).toFloat(),
                    cy - hthickness,
                    (cx + radius).toFloat(),
                    cy + hthickness
                )
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p)

                // draw the vertical crossbar
                draw_rect.set(
                    cx - hthickness,
                    cy - radius / 2.0f,
                    cx + hthickness,
                    cy + radius / 2.0f
                )
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p)

                if (is_level) {
                    // draw a second line

                    p.setColor(angle_highlight_color_pref)
                    p.setAlpha(line_alpha)
                    draw_rect.set(
                        (cx - radius).toFloat(),
                        cy - 6 * hthickness,
                        (cx + radius).toFloat(),
                        cy - 4 * hthickness
                    )
                    canvas.drawRoundRect(draw_rect, hthickness, hthickness, p)
                }

                p.clearShadowLayer()
            }
            updateCachedViewAngles(time_ms) // ensure view_angle_x_preview, view_angle_y_preview are computed and up to date
            val camera_angle_x: Float
            val camera_angle_y: Float
            if (system_orientation_portrait) {
                camera_angle_x = this.view_angle_y_preview
                camera_angle_y = this.view_angle_x_preview
            } else {
                camera_angle_x = this.view_angle_x_preview
                camera_angle_y = this.view_angle_y_preview
            }
            val angle_scale_x =
                (canvas.width / (2.0 * tan(Math.toRadians((camera_angle_x / 2.0))))).toFloat()
            val angle_scale_y =
                (canvas.height / (2.0 * tan(Math.toRadians((camera_angle_y / 2.0))))).toFloat()
            /*if( MyDebug.LOG ) {
				Log.d(TAG, "camera_angle_x: " + camera_angle_x);
				Log.d(TAG, "camera_angle_y: " + camera_angle_y);
				Log.d(TAG, "angle_scale_x: " + angle_scale_x);
				Log.d(TAG, "angle_scale_y: " + angle_scale_y);
				Log.d(TAG, "angle_scale_x/scale: " + angle_scale_x/scale);
				Log.d(TAG, "angle_scale_y/scale: " + angle_scale_y/scale);
			}*/
            /*if( MyDebug.LOG ) {
				Log.d(TAG, "has_pitch_angle?: " + has_pitch_angle);
				Log.d(TAG, "show_pitch_lines?: " + show_pitch_lines);
			}*/
            var angle_scale =
                sqrt((angle_scale_x * angle_scale_x + angle_scale_y * angle_scale_y).toDouble()).toFloat()
            angle_scale *= preview.getZoomRatio()
            if (has_pitch_angle && show_pitch_lines_pref) {
                // lines should be shorter in portrait
                val pitch_radius_dps =
                    if (device_ui_rotation == 90 || device_ui_rotation == 270) 80 else 100
                val pitch_radius =
                    (pitch_radius_dps * scale_dp + 0.5f).toInt() // convert dps to pixels
                val angle_step = this.angleStep
                var latitude_angle = -90
                while (latitude_angle <= 90) {
                    val this_angle = pitch_angle - latitude_angle
                    if (abs(this_angle) < 90.0) {
                        val pitch_distance =
                            angle_scale * tan(Math.toRadians(this_angle)).toFloat() // angle_scale is already in pixels rather than dps
                        /*if( MyDebug.LOG ) {
							Log.d(TAG, "pitch_angle: " + pitch_angle);
							Log.d(TAG, "pitch_distance_dp: " + pitch_distance_dp);
						}*/
                        p.setColor(Color.WHITE)
                        p.setTextAlign(Paint.Align.LEFT)
                        if (latitude_angle == 0 && abs(pitch_angle) < 1.0) {
                            p.setAlpha(255)
                        } else if (latitude_angle == 90 && abs(pitch_angle - 90) < 3.0) {
                            p.setAlpha(255)
                        } else if (latitude_angle == -90 && abs(pitch_angle + 90) < 3.0) {
                            p.setAlpha(255)
                        } else {
                            p.setAlpha(line_alpha)
                        }
                        p.setShadowLayer(shadow_radius, 0.0f, 0.0f, Color.BLACK)
                        // can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
                        draw_rect.set(
                            (cx - pitch_radius).toFloat(),
                            cy + pitch_distance - hthickness,
                            (cx + pitch_radius).toFloat(),
                            cy + pitch_distance + hthickness
                        )
                        canvas.drawRoundRect(draw_rect, hthickness, hthickness, p)
                        p.clearShadowLayer()
                        // draw pitch angle indicator
                        applicationInterface.drawTextWithBackground(
                            canvas,
                            p,
                            latitude_angle.toString() + "\u00B0",
                            p.getColor(),
                            Color.BLACK,
                            (cx + pitch_radius + 4 * hthickness).toInt(),
                            (cy + pitch_distance - 2 * hthickness).toInt(),
                            MyApplicationInterface.Alignment.ALIGNMENT_CENTRE
                        )
                    }
                    latitude_angle += angle_step
                }
            }
            if (has_geo_direction && has_pitch_angle && show_geo_direction_lines_pref) {
                // lines should be longer in portrait - n.b., this is opposite to behaviour of pitch lines, as
                // geo lines are drawn perpendicularly
                val geo_radius_dps =
                    if (device_ui_rotation == 90 || device_ui_rotation == 270) 100 else 80
                val geo_radius = (geo_radius_dps * scale_dp + 0.5f).toInt() // convert dps to pixels
                val geo_angle = Math.toDegrees(geo_direction).toFloat()
                val angle_step = this.angleStep
                var longitude_angle = 0
                while (longitude_angle < 360) {
                    var this_angle = (longitude_angle - geo_angle).toDouble()
                    /*if( MyDebug.LOG ) {
						Log.d(TAG, "longitude_angle: " + longitude_angle);
						Log.d(TAG, "geo_angle: " + geo_angle);
						Log.d(TAG, "this_angle: " + this_angle);
					}*/
                    // normalise to be in interval [0, 360)
                    while (this_angle >= 360.0) this_angle -= 360.0
                    while (this_angle < -360.0) this_angle += 360.0
                    // pick shortest angle
                    if (this_angle > 180.0) this_angle = -(360.0 - this_angle)
                    if (abs(this_angle) < 90.0) {
                        /*if( MyDebug.LOG ) {
							Log.d(TAG, "this_angle is now: " + this_angle);
						}*/
                        val geo_distance =
                            angle_scale * tan(Math.toRadians(this_angle)).toFloat() // angle_scale is already in pixels rather than dps
                        p.setColor(Color.WHITE)
                        p.setTextAlign(Paint.Align.CENTER)
                        p.setAlpha(line_alpha)
                        p.setShadowLayer(shadow_radius, 0.0f, 0.0f, Color.BLACK)
                        // can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
                        draw_rect.set(
                            cx + geo_distance - hthickness,
                            (cy - geo_radius).toFloat(),
                            cx + geo_distance + hthickness,
                            (cy + geo_radius).toFloat()
                        )
                        canvas.drawRoundRect(draw_rect, hthickness, hthickness, p)
                        p.clearShadowLayer()
                        // draw geo direction angle indicator
                        applicationInterface.drawTextWithBackground(
                            canvas,
                            p,
                            longitude_angle.toString() + "\u00B0",
                            p.getColor(),
                            Color.BLACK,
                            (cx + geo_distance).toInt(),
                            (cy - geo_radius - 4 * hthickness).toInt(),
                            MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM
                        )
                    }
                    longitude_angle += angle_step
                }
            }

            p.setAlpha(255)
            p.setStyle(Paint.Style.FILL) // reset

            canvas.restore()
        }

        if (allow_angle_lines && this.storedAutoStabilisePref && preview.hasLevelAngleStable() && !preview.isVideo()) {
            // although auto-level is supported for photos taken in video mode, there's the risk that it's misleading to display
            // the guide when in video mode!
            val level_angle = preview.getLevelAngle()
            var auto_stabilise_level_angle = level_angle
            //double auto_stabilise_level_angle = angle;
            while (auto_stabilise_level_angle < -90) auto_stabilise_level_angle += 180.0
            while (auto_stabilise_level_angle > 90) auto_stabilise_level_angle -= 180.0
            val level_angle_rad_abs = abs(Math.toRadians(auto_stabilise_level_angle))

            val w1 = canvas.width
            val h1 = canvas.height
            val w0 = (w1 * cos(level_angle_rad_abs) + h1 * sin(level_angle_rad_abs))
            val h0 = (w1 * sin(level_angle_rad_abs) + h1 * cos(level_angle_rad_abs))

            if (ImageSaver.autoStabiliseCrop(
                    auto_stabilise_crop,
                    level_angle_rad_abs,
                    w0,
                    h0,
                    w1,
                    h1,
                    canvas.width,
                    canvas.height
                )
            ) {
                val w2 = auto_stabilise_crop[0]
                val h2 = auto_stabilise_crop[1]
                val cx = canvas.width / 2
                val cy = canvas.height / 2

                val left = (canvas.width - w2) / 2.0f
                val top = (canvas.height - h2) / 2.0f
                val right = (canvas.width + w2) / 2.0f
                val bottom = (canvas.height + h2) / 2.0f

                canvas.save()
                canvas.rotate(-level_angle.toFloat(), cx.toFloat(), cy.toFloat())

                // draw shaded area
                val o_dist =
                    sqrt((canvas.width * canvas.width + canvas.height * canvas.getHeight()).toDouble()).toFloat()
                val o_left = (canvas.width - o_dist) / 2.0f
                val o_top = (canvas.height - o_dist) / 2.0f
                val o_right = (canvas.width + o_dist) / 2.0f
                val o_bottom = (canvas.height + o_dist) / 2.0f
                p.setStyle(Paint.Style.FILL)
                p.setColor(Color.rgb(0, 0, 0))
                p.setAlpha(crop_shading_alpha_c)
                canvas.drawRect(o_left, o_top, left, o_bottom, p)
                canvas.drawRect(right, o_top, o_right, o_bottom, p)
                canvas.drawRect(left, o_top, right, top, p) // top
                canvas.drawRect(left, bottom, right, o_bottom, p) // bottom

                if (has_level_angle && abs(level_angle) <= close_level_angle) { // n.b., use level_angle, not angle or orig_level_angle
                    p.setColor(angle_highlight_color_pref)
                } else {
                    p.setColor(Color.WHITE)
                }
                p.setStyle(Paint.Style.STROKE)
                p.setStrokeWidth(stroke_width)

                canvas.drawRect(left, top, right, bottom, p)

                canvas.restore()

                p.setStyle(Paint.Style.FILL) // reset
                p.setAlpha(255) // reset
            }
        }
    }

    private fun doThumbnailAnimation(canvas: Canvas, time_ms: Long) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        // note, no need to check preferences here, as we do that when setting thumbnail_anim
        if (camera_controller != null && this.thumbnail_anim && last_thumbnail != null) {
            val ui_rotation = preview.getUIRotation()
            val time = time_ms - this.thumbnail_anim_start_ms
            val duration: Long = 500
            if (time > duration) {
                if (MyDebug.LOG) Log.d(TAG, "thumbnail_anim finished")
                this.thumbnail_anim = false
            } else {
                thumbnail_anim_src_rect.left = 0f
                thumbnail_anim_src_rect.top = 0f
                thumbnail_anim_src_rect.right = last_thumbnail!!.width.toFloat()
                thumbnail_anim_src_rect.bottom = last_thumbnail!!.height.toFloat()
                //                View galleryButton = main_activity.findViewById(R.id.gallery);
                val alpha = (time.toFloat()) / duration.toFloat()

                val st_x = canvas.width / 2
                val st_y = canvas.height / 2

                //                int nd_x = galleryButton.getLeft() + galleryButton.width/2;
//                int nd_y = galleryButton.getTop() + galleryButton.height/2;
//                int thumbnail_x = (int)( (1.0f-alpha)*st_x + alpha*nd_x );
//                int thumbnail_y = (int)( (1.0f-alpha)*st_y + alpha*nd_y );
                val st_w = canvas.width.toFloat()
                val st_h = canvas.height.toFloat()
                //                float nd_w = galleryButton.width;
//                float nd_h = galleryButton.height;
                //int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
                //int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
//                float correction_w = st_w/nd_w - 1.0f;
//                float correction_h = st_h/nd_h - 1.0f;
//                int thumbnail_w = (int)(st_w/(1.0f+alpha*correction_w));
//                int thumbnail_h = (int)(st_h/(1.0f+alpha*correction_h));
//                thumbnail_anim_dst_rect.left = thumbnail_x - thumbnail_w/2.0f;
//                thumbnail_anim_dst_rect.top = thumbnail_y - thumbnail_h/2.0f;
//                thumbnail_anim_dst_rect.right = thumbnail_x + thumbnail_w/2.0f;
//                thumbnail_anim_dst_rect.bottom = thumbnail_y + thumbnail_h/2.0f;
                //canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect, thumbnail_anim_dst_rect, p);
                thumbnail_anim_matrix.setRectToRect(
                    thumbnail_anim_src_rect,
                    thumbnail_anim_dst_rect,
                    Matrix.ScaleToFit.FILL
                )
                //thumbnail_anim_matrix.reset();
                if (ui_rotation == 90 || ui_rotation == 270) {
                    val ratio =
                        (last_thumbnail!!.width.toFloat()) / last_thumbnail!!.height
                            .toFloat()
                    thumbnail_anim_matrix.preScale(
                        ratio,
                        1.0f / ratio,
                        last_thumbnail!!.width / 2.0f,
                        last_thumbnail!!.height / 2.0f
                    )
                }
                thumbnail_anim_matrix.preRotate(
                    ui_rotation.toFloat(),
                    last_thumbnail!!.width / 2.0f,
                    last_thumbnail!!.height / 2.0f
                )
                canvas.drawBitmap(last_thumbnail!!, thumbnail_anim_matrix, p)
            }
        }
    }

    private fun doFocusAnimation(canvas: Canvas, time_ms: Long) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        if (camera_controller != null && continuous_focus_moving && !taking_picture) {
            // we don't display the continuous focusing animation when taking a photo - and can also give the impression of having
            // frozen if we pause because the image saver queue is full
            val dt = time_ms - continuous_focus_moving_ms
            val length: Long = 1000
            /*if( MyDebug.LOG )
				Log.d(TAG, "continuous focus moving, dt: " + dt);*/
            if (dt <= length) {
                val frac = (dt.toFloat()) / length.toFloat()
                val pos_x = canvas.width / 2.0f
                val pos_y = canvas.height / 2.0f
                val min_radius = (40 * scale_dp + 0.5f) // convert dps to pixels
                val max_radius = (60 * scale_dp + 0.5f) // convert dps to pixels
                val radius: Float
                if (frac < 0.5f) {
                    val alpha = frac * 2.0f
                    radius = (1.0f - alpha) * min_radius + alpha * max_radius
                } else {
                    val alpha = (frac - 0.5f) * 2.0f
                    radius = (1.0f - alpha) * max_radius + alpha * min_radius
                }
                /*if( MyDebug.LOG ) {
					Log.d(TAG, "dt: " + dt);
					Log.d(TAG, "radius: " + radius);
				}*/
                p.setColor(Color.WHITE)
                p.setStyle(Paint.Style.STROKE)
                p.setStrokeWidth(stroke_width)
                canvas.drawCircle(pos_x, pos_y, radius, p)
                p.setStyle(Paint.Style.FILL) // reset
            } else {
                clearContinuousFocusMove()
            }
        }

        if (preview.isFocusWaiting() || preview.isFocusRecentSuccess() || preview.isFocusRecentFailure()) {
            val time_since_focus_started = preview.timeSinceStartedAutoFocus()
            val min_radius = (40 * scale_dp + 0.5f) // convert dps to pixels
            val max_radius = (45 * scale_dp + 0.5f) // convert dps to pixels
            var radius = min_radius
            if (time_since_focus_started > 0) {
                val length: Long = 500
                var frac = (time_since_focus_started.toFloat()) / length.toFloat()
                if (frac > 1.0f) frac = 1.0f
                if (frac < 0.5f) {
                    val alpha = frac * 2.0f
                    radius = (1.0f - alpha) * min_radius + alpha * max_radius
                } else {
                    val alpha = (frac - 0.5f) * 2.0f
                    radius = (1.0f - alpha) * max_radius + alpha * min_radius
                }
            }
            val size = radius.toInt()

            if (preview.isFocusRecentSuccess()) p.setColor(Color.rgb(20, 231, 21)) // Green A400
            else if (preview.isFocusRecentFailure()) p.setColor(Color.rgb(244, 67, 54)) // Red 500
            else p.setColor(Color.WHITE)
            p.setStyle(Paint.Style.STROKE)
            p.setStrokeWidth(stroke_width)
            val pos_x: Int
            val pos_y: Int
            if (preview.hasFocusArea()) {
                val focus_pos = preview.getFocusPos()
                pos_x = focus_pos.first!!
                pos_y = focus_pos.second!!
            } else {
                pos_x = canvas.width / 2
                pos_y = canvas.height / 2
            }
            val frac = 0.5f
            // horizontal strokes
            canvas.drawLine(
                (pos_x - size).toFloat(),
                (pos_y - size).toFloat(),
                pos_x - frac * size,
                (pos_y - size).toFloat(),
                p
            )
            canvas.drawLine(
                pos_x + frac * size,
                (pos_y - size).toFloat(),
                (pos_x + size).toFloat(),
                (pos_y - size).toFloat(),
                p
            )
            canvas.drawLine(
                (pos_x - size).toFloat(),
                (pos_y + size).toFloat(),
                pos_x - frac * size,
                (pos_y + size).toFloat(),
                p
            )
            canvas.drawLine(
                pos_x + frac * size,
                (pos_y + size).toFloat(),
                (pos_x + size).toFloat(),
                (pos_y + size).toFloat(),
                p
            )
            // vertical strokes
            canvas.drawLine(
                (pos_x - size).toFloat(),
                (pos_y - size).toFloat(),
                (pos_x - size).toFloat(),
                pos_y - frac * size,
                p
            )
            canvas.drawLine(
                (pos_x - size).toFloat(),
                pos_y + frac * size,
                (pos_x - size).toFloat(),
                (pos_y + size).toFloat(),
                p
            )
            canvas.drawLine(
                (pos_x + size).toFloat(),
                (pos_y - size).toFloat(),
                (pos_x + size).toFloat(),
                pos_y - frac * size,
                p
            )
            canvas.drawLine(
                (pos_x + size).toFloat(),
                pos_y + frac * size,
                (pos_x + size).toFloat(),
                (pos_y + size).toFloat(),
                p
            )
            p.setStyle(Paint.Style.FILL) // reset
        }
    }

    fun setCoverPreview(cover_preview: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "setCoverPreview: " + cover_preview)
        this.cover_preview = cover_preview
    }

    fun setDimPreview(on: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "setDimPreview: " + on)
        if (on) {
            this.dim_preview = DimPreview.DIM_PREVIEW_ON
        } else if (this.dim_preview == DimPreview.DIM_PREVIEW_ON) {
            this.dim_preview = DimPreview.DIM_PREVIEW_UNTIL
        }
    }

    fun clearDimPreview() {
        this.dim_preview = DimPreview.DIM_PREVIEW_OFF
    }

    fun onDrawPreview(canvas: Canvas) {
        /*if( MyDebug.LOG )
			Log.d(TAG, "onDrawPreview");*/
        /*if( MyDebug.LOG )
			Log.d(TAG, "onDrawPreview hardware accelerated: " + canvas.isHardwareAccelerated());*/

        val time_ms = System.currentTimeMillis()

        if (!has_settings) {
            if (MyDebug.LOG) Log.d(TAG, "onDrawPreview: need to update settings")
            updateSettings()
        }
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        val ui_rotation = preview.getUIRotation()

        // set up preview bitmaps (histogram etc)
        val want_preview_bitmap =
            want_histogram || want_zebra_stripes || want_focus_peaking || want_pre_shots
        val use_preview_bitmap_small = want_histogram || want_zebra_stripes || want_focus_peaking
        val use_preview_bitmap_full = want_pre_shots
        if (want_preview_bitmap != preview.isPreviewBitmapEnabled() || use_preview_bitmap_small != preview.usePreviewBitmapSmall() || use_preview_bitmap_full != preview.usePreviewBitmapFull()) {
            if (want_preview_bitmap) {
                preview.enablePreviewBitmap(use_preview_bitmap_small, use_preview_bitmap_full)
            } else preview.disablePreviewBitmap()
        }
        if (want_preview_bitmap) {
            if (want_histogram) preview.enableHistogram(histogram_type)
            else preview.disableHistogram()

            if (want_zebra_stripes) preview.enableZebraStripes(
                zebra_stripes_threshold,
                zebra_stripes_color_foreground,
                zebra_stripes_color_background
            )
            else preview.disableZebraStripes()

            if (want_focus_peaking) preview.enableFocusPeaking()
            else preview.disableFocusPeaking()

            if (want_pre_shots) preview.enablePreShots()
            else preview.disablePreShots()
        }

        // See documentation for CameraController.shouldCoverPreview().
        // Note, originally we checked camera_controller.shouldCoverPreview() every frame, but this
        // has the problem that we blank whenever the camera is being reopened, e.g., when switching
        // cameras or changing photo modes that require a reopen. The intent however is to only
        // cover up the camera when the application is pausing, and to keep it covered up until
        // after we've resumed, and the camera has been reopened and we've received frames.
        if (preview.usingCamera2API()) {
            val camera_is_active =
                camera_controller != null && !camera_controller.shouldCoverPreview()
            if (cover_preview) {
                // see if we have received a frame yet
                if (camera_is_active) {
                    if (MyDebug.LOG) Log.d(TAG, "no longer need to cover preview")
                    cover_preview = false
                }
            }
            if (cover_preview) {
                // camera has never been active since last resuming
                p.setColor(Color.BLACK)
                //p.setColor(Color.RED); // test
                canvas.drawRect(
                    0.0f,
                    0.0f,
                    canvas.width.toFloat(),
                    canvas.height.toFloat(),
                    p
                )
            } else if (dim_preview == DimPreview.DIM_PREVIEW_ON || (!camera_is_active && dim_preview == DimPreview.DIM_PREVIEW_UNTIL)) {
                val time_now = System.currentTimeMillis()
                if (camera_inactive_time_ms == -1L) {
                    camera_inactive_time_ms = time_now
                }
                var frac = ((time_now - camera_inactive_time_ms) / dim_effect_time_c.toFloat())
                frac = min(frac, 1.0f)
                val alpha = (frac * 127).toInt()
                /*if( MyDebug.LOG ) {
                    Log.d(TAG, "time diff: " + (time_now - camera_inactive_time_ms));
                    Log.d(TAG, "    frac: " + frac);
                    Log.d(TAG, "    alpha: " + alpha);
                }*/
                p.setColor(Color.BLACK)
                p.setAlpha(alpha)
                canvas.drawRect(
                    0.0f,
                    0.0f,
                    canvas.width.toFloat(),
                    canvas.height.toFloat(),
                    p
                )
                p.setAlpha(255)
            } else {
                camera_inactive_time_ms = -1
                if (dim_preview == DimPreview.DIM_PREVIEW_UNTIL && camera_is_active) {
                    dim_preview = DimPreview.DIM_PREVIEW_OFF
                }
            }
        }

        if (camera_controller != null && front_screen_flash) {
            p.setColor(Color.WHITE)
            canvas.drawRect(
                0.0f,
                0.0f,
                canvas.width.toFloat(),
                canvas.height.toFloat(),
                p
            )
        } else if ("flash_frontscreen_torch" == preview.getCurrentFlashValue()) { // getCurrentFlashValue() may return null
            p.setColor(Color.WHITE)
            p.setAlpha(200) // set alpha so user can still see some of the preview
            canvas.drawRect(
                0.0f,
                0.0f,
                canvas.width.toFloat(),
                canvas.height.toFloat(),
                p
            )
            p.setAlpha(255)
        }

        if (mainActivity.mainUI!!.inImmersiveMode()) {
            if (immersive_mode_everything_pref) {
                // exit, to ensure we don't display anything!
                // though note we still should do the front screen flash (since the user can take photos via volume keys when
                // in immersive_mode_everything mode)
                return
            }
        }

        // If MainActivity.lock_to_landscape==true, then the ui_rotation represents the orientation of the
        // device; if MainActivity.lock_to_landscape==false then ui_rotation is always 0 as we don't need to
        // apply any orientation ourselves. However, we're we do want to know the true rotation of the
        // device, as it affects how certain elements of the UI are layed out.
        val device_ui_rotation: Int
        if (MainActivity.lockToLandscape) {
            device_ui_rotation = ui_rotation
        } else {
            val system_orientation = mainActivity.systemOrientation
            device_ui_rotation = getRotationFromSystemOrientation(system_orientation)
        }

        if (camera_controller != null && taking_picture && !front_screen_flash && take_photo_border_pref) {
            p.setColor(Color.WHITE)
            p.setStyle(Paint.Style.STROKE)
            p.setStrokeWidth(stroke_width)
            val this_stroke_width = (5.0f * scale_dp + 0.5f) // convert dps to pixels
            p.setStrokeWidth(this_stroke_width)
            canvas.drawRect(
                0.0f,
                0.0f,
                canvas.width.toFloat(),
                canvas.height.toFloat(),
                p
            )
            p.setStyle(Paint.Style.FILL) // reset
            p.setStrokeWidth(stroke_width) // reset
        }
        drawGrids(canvas)

        drawCropGuides(canvas)

        // n.b., don't display ghost image if front_screen_flash==true (i.e., frontscreen flash is in operation), otherwise
        // the effectiveness of the "flash" is reduced
        if (last_thumbnail != null && !last_thumbnail_is_video && camera_controller != null && (show_last_image || (allow_ghost_last_image && !front_screen_flash && ghost_image_pref == "preference_ghost_image_last"))) {
            // If changing this code, ensure that pause preview still works when:
            // - Taking a photo in portrait or landscape - and check rotating the device while preview paused
            // - Taking a photo with lock to portrait/landscape options still shows the thumbnail with aspect ratio preserved
            // Also check ghost last image works okay!
            if (show_last_image) {
                p.setColor(
                    Color.rgb(
                        0,
                        0,
                        0
                    )
                ) // in case image doesn't cover the canvas (due to different aspect ratios)
                canvas.drawRect(
                    0.0f,
                    0.0f,
                    canvas.width.toFloat(),
                    canvas.height.toFloat(),
                    p
                ) // in case
            }
            setLastImageMatrix(canvas, last_thumbnail!!, ui_rotation, !show_last_image)
            if (!show_last_image) p.setAlpha(ghost_image_alpha)
            canvas.drawBitmap(last_thumbnail!!, last_image_matrix, p)
            if (!show_last_image) p.setAlpha(255)
        } else if (camera_controller != null && !front_screen_flash && ghost_selected_image_bitmap != null) {
            setLastImageMatrix(canvas, ghost_selected_image_bitmap!!, ui_rotation, true)
            p.setAlpha(ghost_image_alpha)
            canvas.drawBitmap(ghost_selected_image_bitmap!!, last_image_matrix, p)
            p.setAlpha(255)
        }

        if (preview.isPreviewBitmapEnabled() && !show_last_image) {
            // draw additional real-time effects

            // draw zebra stripes

            val zebra_stripes_bitmap = preview.getZebraStripesBitmap()
            if (zebra_stripes_bitmap != null) {
                setLastImageMatrix(canvas, zebra_stripes_bitmap, 0, false)
                p.setAlpha(255)
                canvas.drawBitmap(zebra_stripes_bitmap, last_image_matrix, p)
            }

            // draw focus peaking
            val focus_peaking_bitmap = preview.getFocusPeakingBitmap()
            if (focus_peaking_bitmap != null) {
                setLastImageMatrix(canvas, focus_peaking_bitmap, 0, false)
                p.setAlpha(127)
                if (focus_peaking_color_pref != Color.WHITE) {
                    p.setColorFilter(
                        PorterDuffColorFilter(
                            focus_peaking_color_pref,
                            PorterDuff.Mode.SRC_IN
                        )
                    )
                }
                canvas.drawBitmap(focus_peaking_bitmap, last_image_matrix, p)
                if (focus_peaking_color_pref != Color.WHITE) {
                    p.setColorFilter(null)
                }
                p.setAlpha(255)
            }
        }

        //        doThumbnailAnimation(canvas, time_ms);
        drawUI(canvas, device_ui_rotation, time_ms)

        drawAngleLines(canvas, device_ui_rotation, time_ms)

        doFocusAnimation(canvas, time_ms)

        val faces_detected = preview.getFacesDetected()
        if (faces_detected != null) {
            p.setColor(Color.rgb(255, 235, 59)) // Yellow 500
            p.setStyle(Paint.Style.STROKE)
            p.setStrokeWidth(stroke_width)
            for (face in faces_detected) {
                // Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
                if (face.score >= 50) {
                    canvas.drawRect(face.temp, p)
                }
            }
            p.setStyle(Paint.Style.FILL) // reset
        }

        if (enable_gyro_target_spot && camera_controller != null) {
            val gyroSensor = mainActivity.applicationInterface!!.gyroSensor
            if (gyroSensor.isRecording()) {
                val system_orientation = mainActivity.systemOrientation
                val system_orientation_portrait = system_orientation == SystemOrientation.PORTRAIT
                for (gyro_direction in gyro_directions) {
                    gyroSensor.getRelativeInverseVector(transformed_gyro_direction, gyro_direction)
                    gyroSensor.getRelativeInverseVector(
                        transformed_gyro_direction_up,
                        gyro_direction_up
                    )
                    // note that although X of gyro_direction represents left to right on the device, because we're in landscape mode,
                    // this is y coordinates on the screen
                    val angle_x: Float
                    val angle_y: Float
                    if (system_orientation_portrait) {
                        angle_x = asin(transformed_gyro_direction[0].toDouble()).toFloat()
                        angle_y = -asin(transformed_gyro_direction[1].toDouble()).toFloat()
                    } else {
                        angle_x = -asin(transformed_gyro_direction[1].toDouble()).toFloat()
                        angle_y = -asin(transformed_gyro_direction[0].toDouble()).toFloat()
                    }
                    if (abs(angle_x) < 0.5f * Math.PI && abs(angle_y) < 0.5f * Math.PI) {
                        updateCachedViewAngles(time_ms) // ensure view_angle_x_preview, view_angle_y_preview are computed and up to date
                        val camera_angle_x: Float
                        val camera_angle_y: Float
                        if (system_orientation_portrait) {
                            camera_angle_x = this.view_angle_y_preview
                            camera_angle_y = this.view_angle_x_preview
                        } else {
                            camera_angle_x = this.view_angle_x_preview
                            camera_angle_y = this.view_angle_y_preview
                        }
                        var angle_scale_x =
                            (canvas.width / (2.0 * tan(Math.toRadians((camera_angle_x / 2.0))))).toFloat()
                        var angle_scale_y =
                            (canvas.height / (2.0 * tan(Math.toRadians((camera_angle_y / 2.0))))).toFloat()
                        angle_scale_x *= preview.getZoomRatio()
                        angle_scale_y *= preview.getZoomRatio()
                        val distance_x =
                            angle_scale_x * tan(angle_x.toDouble()).toFloat() // angle_scale is already in pixels rather than dps
                        val distance_y =
                            angle_scale_y * tan(angle_y.toDouble()).toFloat() // angle_scale is already in pixels rather than dps
                        p.setColor(Color.WHITE)
                        drawGyroSpot(
                            canvas,
                            0.0f,
                            0.0f,
                            -1.0f,
                            0.0f,
                            48,
                            true
                        ) // draw spot for the centre of the screen, to help the user orient the device
                        p.setColor(Color.BLUE)
                        val dir_x = -transformed_gyro_direction_up[1]
                        val dir_y = -transformed_gyro_direction_up[0]
                        drawGyroSpot(canvas, distance_x, distance_y, dir_x, dir_y, 45, false)
                        /*{
						// for debug only, draw the gyro spot that isn't calibrated with the accelerometer
						gyroSensor.getRelativeInverseVectorGyroOnly(transformed_gyro_direction, gyro_direction);
						gyroSensor.getRelativeInverseVectorGyroOnly(transformed_gyro_direction_up, gyro_direction_up);
						p.setColor(Color.YELLOW);
						angle_x = - (float)Math.asin(transformed_gyro_direction[1]);
						angle_y = - (float)Math.asin(transformed_gyro_direction[0]);
						distance_x = angle_scale_x * (float) Math.tan(angle_x); // angle_scale is already in pixels rather than dps
						distance_y = angle_scale_y * (float) Math.tan(angle_y); // angle_scale is already in pixels rather than dps
						dir_x = -transformed_gyro_direction_up[1];
						dir_y = -transformed_gyro_direction_up[0];
						drawGyroSpot(canvas, distance_x, distance_y, dir_x, dir_y, 45);
					}*/
                    }

                    // show indicator for not being "upright", but only if tilt angle is within 20 degrees
                    if (gyroSensor.isUpright() != 0 && abs(angle_x) <= 20.0f * 0.0174532925199f) {
                        //applicationInterface.drawTextWithBackground(canvas, p, "not upright", Color.WHITE, Color.BLACK, canvas.width/2, canvas.height/2, MyApplicationInterface.Alignment.ALIGNMENT_CENTRE, null, true);
                        canvas.save()
                        canvas.rotate(
                            ui_rotation.toFloat(),
                            canvas.width / 2.0f,
                            canvas.height / 2.0f
                        )
                        val icon_size = (64 * scale_dp + 0.5f).toInt() // convert dps to pixels
                        val cy_offset = (80 * scale_dp + 0.5f).toInt() // convert dps to pixels
                        val cx = canvas.width / 2
                        val cy = canvas.height / 2 - cy_offset
                        icon_dest.set(
                            cx - icon_size / 2,
                            cy - icon_size / 2,
                            cx + icon_size / 2,
                            cy + icon_size / 2
                        )
                        /*p.setStyle(Paint.Style.FILL);
					p.setColor(Color.BLACK);
					p.setAlpha(64);
					canvas.drawRect(icon_dest, p);
					p.setAlpha(255);*/
                        canvas.drawBitmap(
                            (if (gyroSensor.isUpright() > 0) rotate_left_bitmap else rotate_right_bitmap)!!,
                            null,
                            icon_dest,
                            p
                        )
                        canvas.restore()
                    }
                }
            }
        }

        if (time_ms > last_update_focus_seekbar_auto_time + 100) {
            last_update_focus_seekbar_auto_time = time_ms

            if (camera_controller != null && photoMode == PhotoMode.FocusBracketing && applicationInterface.isFocusBracketingSourceAutoPref()) {
                // not strictly related to drawing on the preview, but a convenient place to do this
                // also need to wait some time after getSettingTargetFocusDistanceTime(), as when user stops changing target seekbar, it takes time to return to
                // continuous focus
                if (!mainActivity.preview!!.isSettingTargetFocusDistance && time_ms > mainActivity.preview!!.settingTargetFocusDistanceTime + 500 &&
                    camera_controller.captureResultHasFocusDistance()
                ) {
                    mainActivity.setManualFocusSeekbarProgress(
                        false,
                        camera_controller.captureResultFocusDistance()
                    )
                }
            }
        }

        /*if( MyDebug.LOG ) {
            long time_taken = System.currentTimeMillis() - time_ms;
            Log.d(TAG, "onDrawPreview time: " + time_taken);
        }*/
    }

    private fun setLastImageMatrix(
        canvas: Canvas,
        bitmap: Bitmap,
        this_ui_rotation: Int,
        flip_front: Boolean
    ) {
        val preview = mainActivity.preview
        val camera_controller = preview!!.cameraController
        last_image_src_rect.left = 0f
        last_image_src_rect.top = 0f
        last_image_src_rect.right = bitmap.width.toFloat()
        last_image_src_rect.bottom = bitmap.height.toFloat()
        if (this_ui_rotation == 90 || this_ui_rotation == 270) {
            last_image_src_rect.right = bitmap.height.toFloat()
            last_image_src_rect.bottom = bitmap.width.toFloat()
        }
        last_image_dst_rect.left = 0f
        last_image_dst_rect.top = 0f
        last_image_dst_rect.right = canvas.width.toFloat()
        last_image_dst_rect.bottom = canvas.height.toFloat()
        /*if( MyDebug.LOG ) {
			Log.d(TAG, "thumbnail: " + bitmap.width + " x " + bitmap.getHeight());
			Log.d(TAG, "canvas: " + canvas.width + " x " + canvas.getHeight());
		}*/
        last_image_matrix.setRectToRect(
            last_image_src_rect,
            last_image_dst_rect,
            Matrix.ScaleToFit.CENTER
        ) // use CENTER to preserve aspect ratio
        if (this_ui_rotation == 90 || this_ui_rotation == 270) {
            // the rotation maps (0, 0) to (tw/2 - th/2, th/2 - tw/2), so we translate to undo this
            val diff = (bitmap.height - bitmap.getWidth()).toFloat()
            last_image_matrix.preTranslate(diff / 2.0f, -diff / 2.0f)
        }
        last_image_matrix.preRotate(
            this_ui_rotation.toFloat(),
            bitmap.width / 2.0f,
            bitmap.height / 2.0f
        )
        if (flip_front) {
            val is_front_facing =
                camera_controller != null && (camera_controller.getFacing() == CameraController.Facing.FACING_FRONT)
            if (is_front_facing && sharedPreferences.getString(
                    PreferenceKeys.FrontCameraMirrorKey,
                    "preference_front_camera_mirror_no"
                ) != "preference_front_camera_mirror_photo"
            ) {
                last_image_matrix.preScale(-1.0f, 1.0f, bitmap.width / 2.0f, 0.0f)
            }
        }
    }

    private fun drawGyroSpot(
        canvas: Canvas,
        distance_x: Float,
        distance_y: Float,
        dir_x: Float,
        dir_y: Float,
        radius_dp: Int,
        outline: Boolean
    ) {
        if (outline) {
            p.setStyle(Paint.Style.STROKE)
            p.setStrokeWidth(stroke_width)
            p.setAlpha(255)
        } else {
            p.setAlpha(127)
        }
        val radius = (radius_dp * scale_dp + 0.5f) // convert dps to pixels
        var cx = canvas.width / 2.0f + distance_x
        var cy = canvas.height / 2.0f + distance_y

        // if gyro spots would be outside the field of view, it's still better to show them on the
        // border of the canvas, so the user knows which direction to move the device
        cx = max(cx, 0.0f)
        cx = min(cx, canvas.width.toFloat())
        cy = max(cy, 0.0f)
        cy = min(cy, canvas.height.toFloat())

        canvas.drawCircle(cx, cy, radius, p)
        p.setAlpha(255)
        p.setStyle(Paint.Style.FILL) // reset

        // draw crosshairs
        //p.setColor(Color.WHITE);
        /*p.setStrokeWidth(stroke_width);
        canvas.drawLine(cx - radius*dir_x, cy - radius*dir_y, cx + radius*dir_x, cy + radius*dir_y, p);
        canvas.drawLine(cx - radius*dir_y, cy + radius*dir_x, cx + radius*dir_y, cy - radius*dir_x, p);*/
    }

    /**
     * A generic method to display up to two lines on the preview.
     * Currently used by the Kraken underwater housing sensor to display
     * temperature and depth.
     *
     * The two lines are displayed in the lower left corner of the screen.
     *
     * @param line1 First line to display
     * @param line2 Second line to display
     */
    fun onExtraOSDValuesChanged(line1: String?, line2: String?) {
        OSDLine1 = line1
        OSDLine2 = line2
    }

    companion object {
        private const val TAG = "DrawPreview"

        // Time for the dimming effect. This should be quick, because we call Preview.setupCamera() on
        // the UI thread, which will block redraws:
        // - When reopening the camera, we want the dimming to have occurred whilst reopening the
        //   camera, before we call setupCamera() on the UI thread.
        // - When pausing the preview in MainActivity.updateForSettings(), we call setupCamera() after
        //   this delay - so we don't want to keep the user waiting too long.
        const val dim_effect_time_c: Long = 50

        private val decimalFormat = DecimalFormat("#0.0")
        private const val close_level_angle = 1.0
        private const val histogram_width_dp = 100
        private const val histogram_height_dp = 60

        private const val crop_shading_alpha_c =
            160 // alpha to use for shading areas not of interest

        /** Formats the level_angle double into a string.
         * Beware of calling this too often - shouldn't be every frame due to performance of DecimalFormat
         * (see http://stackoverflow.com/questions/8553672/a-faster-alternative-to-decimalformat-format ).
         */
        fun formatLevelAngle(level_angle: Double): String {
            var number_string = decimalFormat.format(level_angle)
            if (abs(level_angle) < 0.1) {
                // avoids displaying "-0.0", see http://stackoverflow.com/questions/11929096/negative-sign-in-case-of-zero-in-java
                // only do this when level_angle is small, to help performance
                number_string = number_string.replace("^-(?=0(.0*)?$)".toRegex(), "")
            }
            return number_string
        }
    }
}
