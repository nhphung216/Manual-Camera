package com.ssolstice.camera.manual

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.hardware.camera2.CameraExtensionCharacteristics
import android.location.Location
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import com.ssolstice.camera.manual.GyroSensor.TargetCallback
import com.ssolstice.camera.manual.HDRProcessor.TonemappingAlgorithm
import com.ssolstice.camera.manual.ImageSaver.Request.ProcessType
import com.ssolstice.camera.manual.ImageSaver.Request.RemoveDeviceExif
import com.ssolstice.camera.manual.ImageSaver.Request.SaveBase
import com.ssolstice.camera.manual.MainActivity.Companion.useScopedStorage
import com.ssolstice.camera.manual.cameracontroller.CameraController
import com.ssolstice.camera.manual.cameracontroller.CameraController.Facing
import com.ssolstice.camera.manual.cameracontroller.CameraController.TonemapProfile
import com.ssolstice.camera.manual.cameracontroller.RawImage
import com.ssolstice.camera.manual.preview.ApplicationInterface.CameraResolutionConstraints
import com.ssolstice.camera.manual.preview.ApplicationInterface.NRModePref
import com.ssolstice.camera.manual.preview.ApplicationInterface.NoFreeStorageException
import com.ssolstice.camera.manual.preview.ApplicationInterface.RawPref
import com.ssolstice.camera.manual.preview.ApplicationInterface.VideoMaxFileSize
import com.ssolstice.camera.manual.preview.ApplicationInterface.VideoMethod
import com.ssolstice.camera.manual.preview.BasicApplicationInterface
import com.ssolstice.camera.manual.preview.VideoProfile
import com.ssolstice.camera.manual.ui.DrawPreview
import java.io.File
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.Calendar
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import com.ssolstice.camera.manual.PreferenceKeys.FocusModeKey
import com.ssolstice.camera.manual.utils.Logger

//import android.location.Address; // don't use until we have info for data privacy!
//import android.location.Geocoder; // don't use until we have info for data privacy!
/** Our implementation of ApplicationInterface, see there for details.
 */
class MyApplicationInterface internal constructor(
    mainActivity: MainActivity, savedInstanceState: Bundle?
) : BasicApplicationInterface() {
    // note, okay to change the order of enums in future versions, as getPhotoMode() does not rely on the order for the saved photo mode
    enum class PhotoMode {
        Standard, DRO,  // single image "fake" HDR
        HDR,  // HDR created from multiple (expo bracketing) images
        ExpoBracketing,  // take multiple expo bracketed images, without combining to a single image
        FocusBracketing,  // take multiple focus bracketed images, without combining to a single image
        FastBurst, NoiseReduction, Panorama,

        // camera vendor extensions:
        X_Auto, X_HDR, X_Night, X_Bokeh, X_Beauty
    }

    // note, okay to change the order of enums in future versions, as getPhotoMode() does not rely on the order for the saved photo mode
    enum class VideoMode {
        Video, Slow_Motion, Time_Lapse,
    }

    private val main_activity: MainActivity
    val locationSupplier: LocationSupplier
    val gyroSensor: GyroSensor

    @JvmField
    val storageUtils: StorageUtils

    @JvmField
    val drawPreview: DrawPreview?
    val imageSaver: ImageSaver?

    private var n_capture_images =
        0 // how many calls to onPictureTaken() since the last call to onCaptureStarted()
    private var n_capture_images_raw =
        0 // how many calls to onRawPictureTaken() since the last call to onCaptureStarted()
    private var n_panorama_pics = 0
    private var panorama_pic_accepted =
        false // whether the last panorama picture was accepted, or else needs to be retaken
    private var panorama_dir_left_to_right =
        true // direction of panorama (set after we've captured two images)

    private var last_video_file: File? = null
    private var last_video_file_uri: Uri? = null

    private val subtitleVideoTimer = Timer()
    private var subtitleVideoTimerTask: TimerTask? = null

    private val text_bounds = Rect()
    private var used_front_screen_flash = false

    // store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
    private val sharedPreferences: SharedPreferences

    private enum class LastImagesType {
        FILE, SAF, MEDIASTORE
    }

    private var last_images_type =
        LastImagesType.FILE // whether the last images array are using File API, SAF or MediaStore

    /** This class keeps track of the images saved in this batch, for use with Pause Preview option, so we can share or trash images.
     */
    private class LastImage {
        val share: Boolean // one of the images in the list should have share set to true, to indicate which image to share
        val name: String?
        var uri: Uri? = null

        internal constructor(uri: Uri?, share: Boolean) {
            this.name = null
            this.uri = uri
            this.share = share
        }

        internal constructor(filename: String?, share: Boolean) {
            this.name = filename
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // previous to Android 7, we could just use a "file://" uri, but this is no longer supported on Android 7, and
                // results in a android.os.FileUriExposedException when trying to share!
                // see https://stackoverflow.com/questions/38200282/android-os-fileuriexposedexception-file-storage-emulated-0-test-txt-exposed
                // so instead we leave null for now, and set it from MyApplicationInterface.scannedFile().
                this.uri = null
            } else {
                this.uri = Uri.parse("file://" + this.name)
            }
            this.share = share
        }
    }

    private val last_images: MutableList<LastImage> = ArrayList<LastImage>()

    private val photo_delete_toast = ToastBoxer()

    private var has_set_cameraId = false
    private var cameraId: Int = cameraId_default
    private var cameraIdSPhysical: String? =
        null // if non-null, this is the ID string for a physical camera, undlerying the logical cameraId

    /*if( MyDebug.LOG )
             Logger.d(TAG, "nr_mode: " + nr_mode);*/
    var nRMode: String = nr_mode_default
    private var aperture: Float = aperture_default

    // camera properties that aren't saved even in the bundle; these should be initialised/reset in reset()
    private var zoom_factor =
        -1 // don't save zoom, as doing so tends to confuse users; other camera applications don't seem to save zoom when pause/resuming

    // for testing:
    @Volatile
    var test_n_videos_scanned: Int = 0

    @Volatile
    var test_max_mp: Int = 0

    /** Here we save states which aren't saved in preferences (we don't want them to be saved if the
     * application is restarted from scratch), but we do want to preserve if Android has to recreate
     * the application (e.g., configuration change, or it's destroyed while in background).
     */
    fun onSaveInstanceState(state: Bundle) {
        Logger.d(TAG, "onSaveInstanceState")
        Logger.d(TAG, "save cameraId: " + cameraId)
        state.putInt("cameraId", cameraId)
        Logger.d(TAG, "save cameraIdSPhysical: " + cameraIdSPhysical)
        state.putString("cameraIdSPhysical", cameraIdSPhysical)
        Logger.d(TAG, "save nr_mode: " + this.nRMode)
        state.putString("nr_mode", this.nRMode)
        Logger.d(TAG, "save aperture: " + aperture)
        state.putFloat("aperture", aperture)
    }

    fun onDestroy() {
        Logger.d(TAG, "onDestroy")
        if (drawPreview != null) {
            drawPreview.onDestroy()
        }
        if (imageSaver != null) {
            imageSaver.onDestroy()
        }
    }

    override fun getContext(): Context {
        return main_activity
    }

    override fun useCamera2(): Boolean {
        if (main_activity.supportsCamera2()) {
            val camera_api: String = sharedPreferences.getString(
                PreferenceKeys.CameraAPIPreferenceKey, PreferenceKeys.CameraAPIPreferenceDefault
            )!!
            if ("preference_camera_api_camera2" == camera_api) {
                return true
            }
        }
        return false
    }

    /** If adding extra calls to this, consider whether explicit user permission is required, and whether
     * privacy policy or data privacy section needs updating.
     * Returns null if location not available.
     */
    override fun getLocation(): Location? {
        return locationSupplier.getLocation()
    }

    /** If adding extra calls to this, consider whether explicit user permission is required, and whether
     * privacy policy or data privacy section  needs updating.
     * Returns null if location not available.
     */
    fun getLocation(locationInfo: LocationSupplier.LocationInfo?): Location? {
        return locationSupplier.getLocation(locationInfo)
    }

    override fun createOutputVideoMethod(): VideoMethod {
        if (this.isVideoCaptureIntent) {
            Logger.d(TAG, "from video capture intent")
            val myExtras = main_activity.getIntent().getExtras()
            if (myExtras != null) {
                val intent_uri = myExtras.getParcelable<Uri?>(MediaStore.EXTRA_OUTPUT)
                if (intent_uri != null) {
                    Logger.d(TAG, "save to: " + intent_uri)
                    return VideoMethod.URI
                }
            }
            // if no EXTRA_OUTPUT, we should save to standard location, and will pass back the Uri of that location
            Logger.d(TAG, "intent uri not specified")
            if (useScopedStorage()) {
                // can't use file method with scoped storage
                return VideoMethod.MEDIASTORE
            } else {
                // note that SAF URIs don't seem to work for calling applications (tested with Grabilla and "Photo Grabber Image From Video" (FreezeFrame)), so we use standard folder with non-SAF method
                return VideoMethod.FILE
            }
        } else if (storageUtils.isUsingSAF()) {
            return VideoMethod.SAF
        } else if (useScopedStorage()) {
            return VideoMethod.MEDIASTORE
        } else {
            return VideoMethod.FILE
        }
    }

    @Throws(IOException::class)
    override fun createOutputVideoFile(extension: String?): File? {
        return createOutputVideoFile(false, extension, Date())
    }

    @Throws(IOException::class)
    override fun createOutputVideoSAF(extension: String?): Uri? {
        return createOutputVideoSAF(false, extension, Date())
    }

    @Throws(IOException::class)
    override fun createOutputVideoMediaStore(extension: String): Uri {
        return createOutputVideoMediaStore(false, extension, Date())
    }

    @Throws(IOException::class)
    fun createOutputVideoFile(is_preshot: Boolean, extension: String?, date: Date?): File? {
        last_video_file = storageUtils.createOutputMediaFile(
            if (is_preshot) StorageUtils.MEDIA_TYPE_PRESHOT else StorageUtils.MEDIA_TYPE_VIDEO,
            "",
            extension,
            date
        )
        return last_video_file
    }

    @Throws(IOException::class)
    fun createOutputVideoSAF(is_preshot: Boolean, extension: String?, date: Date?): Uri? {
        last_video_file_uri = storageUtils.createOutputMediaFileSAF(
            if (is_preshot) StorageUtils.MEDIA_TYPE_PRESHOT else StorageUtils.MEDIA_TYPE_VIDEO,
            "",
            extension,
            date
        )
        return last_video_file_uri
    }

    @Throws(IOException::class)
    fun createOutputVideoMediaStore(is_preshot: Boolean, extension: String, date: Date?): Uri {
        val folder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            ) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val contentValues = ContentValues()
        val filename = storageUtils.createMediaFilename(
            if (is_preshot) StorageUtils.MEDIA_TYPE_PRESHOT else StorageUtils.MEDIA_TYPE_VIDEO,
            "",
            0,
            "." + extension,
            date
        )
        Logger.d(TAG, "filename: " + filename)
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename)
        val mime_type = storageUtils.getVideoMimeType(extension)
        Logger.d(TAG, "mime_type: " + mime_type)
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, mime_type)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relative_path = storageUtils.getSaveRelativeFolder()
            Logger.d(TAG, "relative_path: " + relative_path)
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, relative_path)
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        try {
            last_video_file_uri = main_activity.getContentResolver().insert(folder, contentValues)
            Logger.d(TAG, "uri: " + last_video_file_uri)
        } catch (e: IllegalArgumentException) {
            // can happen for mediastore method if invalid ContentResolver.insert() call
            if (MyDebug.LOG) Log.e(TAG, "IllegalArgumentException writing video file: " + e.message)
            e.printStackTrace()
            throw IOException()
        } catch (e: IllegalStateException) {
            // have received Google Play crashes from ContentResolver.insert() call for mediastore method
            if (MyDebug.LOG) Log.e(TAG, "IllegalStateException writing video file: " + e.message)
            e.printStackTrace()
            throw IOException()
        }
        if (last_video_file_uri == null) {
            throw IOException()
        }

        return last_video_file_uri!!
    }

    override fun createOutputVideoUri(): Uri {
        if (this.isVideoCaptureIntent) {
            Logger.d(TAG, "from video capture intent")
            val myExtras = main_activity.getIntent().getExtras()
            if (myExtras != null) {
                val intent_uri = myExtras.getParcelable<Uri?>(MediaStore.EXTRA_OUTPUT)
                if (intent_uri != null) {
                    Logger.d(TAG, "save to: " + intent_uri)
                    return intent_uri
                }
            }
        }
        throw RuntimeException() // programming error if we arrived here
    }

    override fun getCameraIdPref(): Int {
        return cameraId
    }

    override fun getCameraIdSPhysicalPref(): String? {
        return cameraIdSPhysical
    }

    override fun getFlashPref(): String {
        return sharedPreferences.getString(PreferenceKeys.getFlashPreferenceKey(cameraId), "")!!
    }

    override fun getFocusPref(is_video: Boolean): String {
        if (this.photoMode == PhotoMode.FocusBracketing && main_activity.preview?.isVideo == false) {
            if (isFocusBracketingSourceAutoPref()) {
                return "focus_mode_continuous_picture"
            } else {
                return "focus_mode_manual2"
            }
        }
        return sharedPreferences.getString(
            PreferenceKeys.getFocusPreferenceKey(cameraId, is_video), ""
        )!!
    }

    val focusAssistPref: Int
        get() {
            val focus_assist_value: String =
                sharedPreferences.getString(PreferenceKeys.FocusAssistPreferenceKey, "0")!!
            var focus_assist: Int
            try {
                focus_assist = focus_assist_value.toInt()
            } catch (e: NumberFormatException) {
                if (MyDebug.LOG) Log.e(
                    TAG, "failed to parse focus_assist_value: " + focus_assist_value
                )
                e.printStackTrace()
                focus_assist = 0
            }
            if (focus_assist > 0 && main_activity.preview!!.isVideoRecording()) {
                // focus assist not currently supported while recording video - don't want to zoom the resultant video!
                focus_assist = 0
            }
            return focus_assist
        }

    override fun isVideoPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.IsVideoPreferenceKey, false)
    }

    override fun getSceneModePref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.SceneModePreferenceKey, CameraController.SCENE_MODE_DEFAULT
        )!!
    }

    override fun getColorEffectPref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.ColorEffectPreferenceKey, CameraController.COLOR_EFFECT_DEFAULT
        )!!
    }

    override fun getWhiteBalancePref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.WhiteBalancePreferenceKey, CameraController.WHITE_BALANCE_DEFAULT
        )!!
    }

    override fun getWhiteBalanceTemperaturePref(): Int {
        return sharedPreferences.getInt(PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, 5000)
    }

    override fun getAntiBandingPref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.AntiBandingPreferenceKey, CameraController.ANTIBANDING_DEFAULT
        )!!
    }

    override fun getEdgeModePref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.EdgeModePreferenceKey, CameraController.EDGE_MODE_DEFAULT
        )!!
    }

    override fun getCameraNoiseReductionModePref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.CameraNoiseReductionModePreferenceKey,
            CameraController.NOISE_REDUCTION_MODE_DEFAULT
        )!!
    }

    override fun getISOPref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT
        )!!
    }

    override fun getExposureCompensationPref(): Int {
        val value: String = sharedPreferences.getString(PreferenceKeys.ExposurePreferenceKey, "0")!!
        Logger.d(TAG, "saved exposure value: " + value)
        var exposure = 0
        try {
            exposure = value.toInt()
            Logger.d(TAG, "exposure: " + exposure)
        } catch (exception: NumberFormatException) {
            Logger.d(TAG, "exposure invalid format, can't parse to int")
        }
        return exposure
    }

    override fun getCameraResolutionPref(constraints: CameraResolutionConstraints?): Pair<Int?, Int?>? {
        val photo_mode = this.photoMode
        if (photo_mode == PhotoMode.Panorama) {
            val best_size: CameraController.Size? =
                choosePanoramaResolution(main_activity.preview!!.getSupportedPictureSizes(false))
            return Pair<Int?, Int?>(best_size!!.width, best_size.height)
        }

        val resolution_value: String = sharedPreferences.getString(
            PreferenceKeys.getResolutionPreferenceKey(
                cameraId, cameraIdSPhysical
            ), ""
        )!!
        Logger.d(TAG, "resolution_value: " + resolution_value)
        var result: Pair<Int?, Int?>? = null
        if (resolution_value.length > 0) {
            // parse the saved size, and make sure it is still valid
            val index = resolution_value.indexOf(' ')
            if (index == -1) {
                Logger.d(TAG, "resolution_value invalid format, can't find space")
            } else {
                val resolution_w_s = resolution_value.substring(0, index)
                val resolution_h_s = resolution_value.substring(index + 1)
                if (MyDebug.LOG) {
                    Logger.d(TAG, "resolution_w_s: " + resolution_w_s)
                    Logger.d(TAG, "resolution_h_s: " + resolution_h_s)
                }
                try {
                    val resolution_w = resolution_w_s.toInt()
                    Logger.d(TAG, "resolution_w: " + resolution_w)
                    val resolution_h = resolution_h_s.toInt()
                    Logger.d(TAG, "resolution_h: " + resolution_h)
                    result = Pair<Int?, Int?>(resolution_w, resolution_h)
                } catch (exception: NumberFormatException) {
                    Logger.d(
                        TAG, "resolution_value invalid format, can't parse w or h to int"
                    )
                }
            }
        }

        if (photo_mode == PhotoMode.NoiseReduction || photo_mode == PhotoMode.HDR) {
            // set a maximum resolution for modes that require decompressing multiple images for processing,
            // due to risk of running out of memory!
            constraints?.has_max_mp = true
            constraints?.max_mp = 18000000 // max of 18MP
            //constraints.max_mp = 7800000; // test!
            if (main_activity.is_test && test_max_mp != 0) {
                constraints?.max_mp = test_max_mp
            }
        }

        return result
    }

    private val saveImageQualityPref: Int
        /** getImageQualityPref() returns the image quality used for the Camera Controller for taking a
         * photo - in some cases, we may set that to a higher value, then perform processing on the
         * resultant JPEG before resaving. This method returns the image quality setting to be used for
         * saving the final image (as specified by the user).
         */
        get() {
            Logger.d(
                TAG, "getSaveImageQualityPref"
            )
            val image_quality_s: String =
                sharedPreferences.getString(PreferenceKeys.QualityPreferenceKey, "90")!!
            var image_quality: Int
            try {
                image_quality = image_quality_s.toInt()
            } catch (exception: NumberFormatException) {
                if (MyDebug.LOG) Log.e(
                    TAG, "image_quality_s invalid format: " + image_quality_s
                )
                image_quality = 90
            }
            if (this.isRawOnly) {
                // if raw only mode, we can set a lower quality for the JPEG, as it isn't going to be saved - only used for
                // the thumbnail and pause preview option
                Logger.d(
                    TAG, "set lower quality for raw_only mode"
                )
                image_quality = min(image_quality, 70)
            }
            return image_quality
        }

    override fun getImageQualityPref(): Int {
        Logger.d(TAG, "getImageQualityPref")
        // see documentation for getSaveImageQualityPref(): in DRO mode we want to take the photo
        // at 100% quality for post-processing, the final image will then be saved at the user requested
        // setting
        val photo_mode = this.photoMode
        if (main_activity.preview?.isVideo == true) ; else if (photo_mode == PhotoMode.DRO) return 100
        else if (photo_mode == PhotoMode.HDR) return 100
        else if (photo_mode == PhotoMode.NoiseReduction) return 100

        if (this.imageFormatPref != ImageSaver.Request.ImageFormat.STD) return 100

        return this.saveImageQualityPref
    }

    override fun getFaceDetectionPref(): Boolean {
        if (isCameraExtensionPref()) {
            // not supported for camera extensions
            return false
        }
        return sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false)
    }

    /** Returns whether the current fps preference is one that requires a "high speed" video size/
     * frame rate.
     */
    fun fpsIsHighSpeed(): Boolean {
        return main_activity.preview!!.fpsIsHighSpeed(getVideoFPSPref())
    }

    override fun getVideoQualityPref(): String? {
        if (this.isVideoCaptureIntent) {
            Logger.d(TAG, "from video capture intent")
            if (main_activity.getIntent().hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
                val intent_quality =
                    main_activity.getIntent().getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0)
                Logger.d(TAG, "intent_quality: " + intent_quality)
                if (intent_quality == 0 || intent_quality == 1) {
                    val video_quality =
                        main_activity.preview!!.getVideoQualityHander().getSupportedVideoQuality()
                    if (intent_quality == 0) {
                        Logger.d(TAG, "return lowest quality")
                        // return lowest quality, video_quality is sorted high to low
                        return video_quality.get(video_quality.size - 1)
                    } else {
                        Logger.d(TAG, "return highest quality")
                        // return highest quality, video_quality is sorted high to low
                        return video_quality.get(0)
                    }
                }
            }
        }

        // Conceivably, we might get in a state where the fps isn't supported at all (e.g., an upgrade changes the available
        // supported video resolutions/frame-rates).
        return sharedPreferences.getString(
            PreferenceKeys.getVideoQualityPreferenceKey(
                cameraId, cameraIdSPhysical, fpsIsHighSpeed()
            ), ""
        )
    }

    override fun getVideoStabilizationPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.VideoStabilizationPreferenceKey, false)
    }

    override fun getForce4KPref(): Boolean {
        return cameraId == 0 && sharedPreferences.getBoolean(
            PreferenceKeys.ForceVideo4KPreferenceKey, false
        ) && main_activity.supportsForceVideo4K()
    }

    override fun getRecordVideoOutputFormatPref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.VideoFormatPreferenceKey, "preference_video_output_format_default"
        )!!
    }

    override fun getVideoBitratePref(): String {
        return sharedPreferences.getString(PreferenceKeys.VideoBitratePreferenceKey, "default")!!
    }

    override fun getVideoFPSPref(): String {
        // if check for EXTRA_VIDEO_QUALITY, if set, best to fall back to default FPS - see corresponding code in getVideoQualityPref
        if (this.isVideoCaptureIntent) {
            Logger.d(TAG, "from video capture intent")
            if (main_activity.getIntent().hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
                val intent_quality =
                    main_activity.getIntent().getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0)
                Logger.d(TAG, "intent_quality: " + intent_quality)
                if (intent_quality == 0 || intent_quality == 1) {
                    return "default"
                }
            }
        }

        val capture_rate_factor = getVideoCaptureRateFactor()
        if (capture_rate_factor < 1.0f - 1.0e-5f) {
            Logger.d(
                TAG, "set fps for slow motion, capture rate: " + capture_rate_factor
            )
            var preferred_fps = (30.0 / capture_rate_factor + 0.5).toInt()
            Logger.d(TAG, "preferred_fps: " + preferred_fps)
            if (main_activity.preview!!.getVideoQualityHander()
                    .videoSupportsFrameRateHighSpeed(preferred_fps) || main_activity.preview!!.getVideoQualityHander()
                    .videoSupportsFrameRate(preferred_fps)
            ) return preferred_fps.toString()
            // just in case say we support 120fps but NOT 60fps, getSupportedSlowMotionRates() will have returned that 2x slow
            // motion is supported, but we need to set 120fps instead of 60fps
            while (preferred_fps < 240) {
                preferred_fps *= 2
                Logger.d(TAG, "preferred_fps not supported, try: " + preferred_fps)
                if (main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRateHighSpeed(preferred_fps) || main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRate(preferred_fps)
                ) return preferred_fps.toString()
            }
            // shouln't happen based on getSupportedSlowMotionRates()
            Log.e(TAG, "can't find valid fps for slow motion")
            return "default"
        }
        return sharedPreferences.getString(
            PreferenceKeys.getVideoFPSPreferenceKey(
                cameraId, cameraIdSPhysical
            ), "default"
        )!!
    }

    override fun getVideoCaptureRateFactor(): Float {
        var capture_rate_factor = sharedPreferences.getFloat(
            PreferenceKeys.getVideoCaptureRatePreferenceKey(
                main_activity.preview!!.getCameraId(), cameraIdSPhysical
            ), 1.0f
        )
        Logger.d(TAG, "capture_rate_factor: " + capture_rate_factor)
        if (abs(capture_rate_factor - 1.0f) > 1.0e-5) {
            // check stored capture rate is valid
            Logger.d(TAG, "check stored capture rate is valid")
            val supported_capture_rates = this.supportedVideoCaptureRates
            Logger.d(TAG, "supported_capture_rates: " + supported_capture_rates)
            var found = false
            for (this_capture_rate in supported_capture_rates) {
                if (abs(capture_rate_factor - this_capture_rate!!) < 1.0e-5) {
                    found = true
                    break
                }
            }
            if (!found) {
                Log.e(TAG, "stored capture_rate_factor: " + capture_rate_factor + " not supported")
                capture_rate_factor = 1.0f
            }
        }
        return capture_rate_factor
    }

    override fun isVideoCaptureRateFactor(): Boolean {
        Log.e(TAG, "isVideoCaptureRateFactor")

        val cameraId = main_activity.preview!!.getCameraId()
        Log.e(TAG, "getCameraId " + cameraId)

        val cameraKey = PreferenceKeys.getVideoCaptureRatePreferenceKey(cameraId, cameraIdSPhysical)
        Log.e(TAG, "cameraKey " + cameraKey)

        val captureRateFactor = sharedPreferences.getFloat(cameraKey, 1.0f)
        Log.e(TAG, "captureRateFactor " + captureRateFactor)

        Log.e(TAG, "capture_rate_factor: " + captureRateFactor)

        if (abs(captureRateFactor - 1.0f) > 1.0e-5) {
            // check stored capture rate is valid
            Log.e(TAG, "check stored capture rate is valid")

            val supportedVideoCaptureRates = this.supportedVideoCaptureRates
            Log.e(TAG, "supported_capture_rates: " + supportedVideoCaptureRates)

            var found = false

            for (captureRate in supportedVideoCaptureRates) {
                if (abs(captureRateFactor - captureRate!!) < 1.0e-5) {
                    found = true
                    break
                }
            }

            if (!found) {
                Log.e(TAG, "stored capture_rate_factor: " + captureRateFactor + " not supported")
            }

            return found
        }

        return true
    }

    val supportedVideoCaptureRates: MutableList<Float?>
        /** This will always return 1, even if slow motion isn't supported (i.e.,
         * slow motion should only be considered as supported if at least 2 entries
         * are returned. Entries are returned in increasing order.
         */
        get() {
            val rates: MutableList<Float?> = ArrayList<Float?>()
            if (main_activity.preview!!.supportsVideoHighSpeed()) {
                // We consider a slow motion rate supported if we can get at least 30fps in slow motion.
                // If this code is updated, see if we also need to update how slow motion fps is chosen
                // in getVideoFPSPref().
                if (main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRateHighSpeed(240) || main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRate(240)
                ) {
                    rates.add(1.0f / 8.0f)
                    rates.add(1.0f / 4.0f)
                    rates.add(1.0f / 2.0f)
                } else if (main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRateHighSpeed(120) || main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRate(120)
                ) {
                    rates.add(1.0f / 4.0f)
                    rates.add(1.0f / 2.0f)
                } else if (main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRateHighSpeed(60) || main_activity.preview!!.getVideoQualityHander()
                        .videoSupportsFrameRate(60)
                ) {
                    rates.add(1.0f / 2.0f)
                }
            }
            rates.add(1.0f)
            run {
                // add timelapse options
                // in theory this should work on any Android version, though video fails to record in timelapse mode on Galaxy Nexus...
                rates.add(2.0f)
                rates.add(3.0f)
                rates.add(4.0f)
                rates.add(5.0f)
                rates.add(10.0f)
                rates.add(20.0f)
                rates.add(30.0f)
                rates.add(60.0f)
                rates.add(120.0f)
                rates.add(240.0f)
            }
            return rates
        }

    override fun getVideoTonemapProfile(): TonemapProfile {
        val video_log: String =
            sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off")!!
        // only return TONEMAPPROFILE_LOG for values recognised by getVideoLogProfileStrength()
        when (video_log) {
            "off" -> return TonemapProfile.TONEMAPPROFILE_OFF
            "rec709" -> return TonemapProfile.TONEMAPPROFILE_REC709
            "srgb" -> return TonemapProfile.TONEMAPPROFILE_SRGB
            "fine", "low", "medium", "strong", "extra_strong" -> return TonemapProfile.TONEMAPPROFILE_LOG
            "gamma" -> return TonemapProfile.TONEMAPPROFILE_GAMMA
            "jtvideo" -> return TonemapProfile.TONEMAPPROFILE_JTVIDEO
            "jtlog" -> return TonemapProfile.TONEMAPPROFILE_JTLOG
            "jtlog2" -> return TonemapProfile.TONEMAPPROFILE_JTLOG2
        }
        return TonemapProfile.TONEMAPPROFILE_OFF
    }

    override fun getVideoLogProfileStrength(): Float {
        val video_log: String =
            sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off")!!
        // remember to update getVideoTonemapProfile() if adding/changing modes
        when (video_log) {
            "off", "rec709", "srgb", "gamma", "jtvideo", "jtlog", "jtlog2" -> return 0.0f
            "fine" -> return 10.0f
            "low" -> return 32.0f
            "medium" -> return 100.0f
            "strong" -> return 224.0f
            "extra_strong" -> return 500.0f
        }
        return 0.0f
    }

    override fun getVideoProfileGamma(): Float {
        val gamma_value: String =
            sharedPreferences.getString(PreferenceKeys.VideoProfileGammaPreferenceKey, "2.2")!!
        var gamma = 0.0f
        try {
            gamma = gamma_value.toFloat()
            Logger.d(TAG, "gamma: " + gamma)
        } catch (e: NumberFormatException) {
            if (MyDebug.LOG) Log.e(TAG, "failed to parse gamma value: " + gamma_value)
            e.printStackTrace()
        }
        return gamma
    }

    override fun getVideoMaxDurationPref(): Long {
        if (this.isVideoCaptureIntent) {
            Logger.d(TAG, "from video capture intent")
            if (main_activity.getIntent().hasExtra(MediaStore.EXTRA_DURATION_LIMIT)) {
                val intent_duration_limit =
                    main_activity.getIntent().getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0)
                Logger.d(TAG, "intent_duration_limit: " + intent_duration_limit)
                return intent_duration_limit * 1000L
            }
        }

        val video_max_duration_value: String =
            sharedPreferences.getString(PreferenceKeys.VideoMaxDurationPreferenceKey, "0")!!
        var video_max_duration: Long
        try {
            video_max_duration = video_max_duration_value.toInt().toLong() * 1000
        } catch (e: NumberFormatException) {
            if (MyDebug.LOG) Log.e(
                TAG,
                "failed to parse preference_video_max_duration value: " + video_max_duration_value
            )
            e.printStackTrace()
            video_max_duration = 0
        }
        return video_max_duration
    }

    override fun getVideoRestartTimesPref(): Int {
        val restart_value: String =
            sharedPreferences.getString(PreferenceKeys.VideoRestartPreferenceKey, "0")!!
        var remaining_restart_video: Int
        try {
            remaining_restart_video = restart_value.toInt()
        } catch (e: NumberFormatException) {
            if (MyDebug.LOG) Log.e(
                TAG, "failed to parse preference_video_restart value: " + restart_value
            )
            e.printStackTrace()
            remaining_restart_video = 0
        }
        return remaining_restart_video
    }

    val videoMaxFileSizeUserPref: Long
        get() {
            Logger.d(
                TAG, "getVideoMaxFileSizeUserPref"
            )

            if (this.isVideoCaptureIntent) {
                Logger.d(
                    TAG, "from video capture intent"
                )
                if (main_activity.getIntent().hasExtra(MediaStore.EXTRA_SIZE_LIMIT)) {
                    val intent_size_limit =
                        main_activity.getIntent().getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, 0)
                    Logger.d(
                        TAG, "intent_size_limit: " + intent_size_limit
                    )
                    return intent_size_limit
                }
            }

            val video_max_filesize_value: String =
                sharedPreferences.getString(PreferenceKeys.VideoMaxFileSizePreferenceKey, "0")!!
            var video_max_filesize: Long
            try {
                video_max_filesize = video_max_filesize_value.toLong()
            } catch (e: NumberFormatException) {
                if (MyDebug.LOG) Log.e(
                    TAG,
                    "failed to parse preference_video_max_filesize value: " + video_max_filesize_value
                )
                e.printStackTrace()
                video_max_filesize = 0
            }
            //video_max_filesize = 1024*1024; // test
            Logger.d(
                TAG, "video_max_filesize: " + video_max_filesize
            )
            return video_max_filesize
        }

    private val videoRestartMaxFileSizeUserPref: Boolean
        get() {
            if (this.isVideoCaptureIntent) {
                Logger.d(
                    TAG, "from video capture intent"
                )
                if (main_activity.getIntent().hasExtra(MediaStore.EXTRA_SIZE_LIMIT)) {
                    // if called from a video capture intent that set a max file size, this will be expecting a single file with that maximum size
                    return false
                }
            }

            return sharedPreferences.getBoolean(
                PreferenceKeys.VideoRestartMaxFileSizePreferenceKey, true
            )
        }

    @Throws(NoFreeStorageException::class)
    override fun getVideoMaxFileSizePref(): VideoMaxFileSize {
        Logger.d(TAG, "getVideoMaxFileSizePref")
        val video_max_filesize = VideoMaxFileSize()
        video_max_filesize.max_filesize = this.videoMaxFileSizeUserPref
        video_max_filesize.auto_restart = this.videoRestartMaxFileSizeUserPref


        /* Try to set the max filesize so we don't run out of space.
		   If using SD card without storage access framework, it's not reliable to get the free storage
		   (see https://sourceforge.net/p/opencamera/tickets/153/ ).
		   If using Storage Access Framework, getting the available space seems to be reliable for
		   internal storage or external SD card.
		   */
        val set_max_filesize: Boolean
        if (storageUtils.isUsingSAF()) {
            set_max_filesize = true
        } else {
            val folder_name = storageUtils.getSaveLocation()
            Logger.d(TAG, "saving to: " + folder_name)
            var is_internal = false
            if (!StorageUtils.saveFolderIsFull(folder_name)) {
                is_internal = true
            } else {
                // If save folder path is a full path, see if it matches the "external" storage (which actually means "primary", which typically isn't an SD card these days).
                val storage = Environment.getExternalStorageDirectory()
                Logger.d(TAG, "compare to: " + storage.getAbsolutePath())
                if (folder_name.startsWith(storage.getAbsolutePath())) is_internal = true
            }
            Logger.d(TAG, "using internal storage?" + is_internal)
            set_max_filesize = is_internal
        }
        if (set_max_filesize) {
            Logger.d(TAG, "try setting max filesize")
            var free_memory = storageUtils.freeMemory()
            if (free_memory >= 0) {
                free_memory = free_memory * 1024 * 1024

                val min_free_memory: Long = 50000000 // how much free space to leave after video
                // min_free_filesize is the minimum value to set for max file size:
                //   - no point trying to create a really short video
                //   - too short videos can end up being corrupted
                //   - also with auto-restart, if this is too small we'll end up repeatedly restarting and creating shorter and shorter videos
                val min_free_filesize: Long = 20000000
                var available_memory = free_memory - min_free_memory
                if (test_set_available_memory) {
                    available_memory = test_available_memory
                }
                if (MyDebug.LOG) {
                    Logger.d(TAG, "free_memory: " + free_memory)
                    Logger.d(TAG, "available_memory: " + available_memory)
                }
                if (available_memory > min_free_filesize) {
                    if (video_max_filesize.max_filesize == 0L || video_max_filesize.max_filesize > available_memory) {
                        video_max_filesize.max_filesize = available_memory
                        // still leave auto_restart set to true - because even if we set a max filesize for running out of storage, the video may still hit a maximum limit beforehand, if there's a device max limit set (typically ~2GB)
                        Logger.d(
                            TAG,
                            "set video_max_filesize to avoid running out of space: " + video_max_filesize
                        )
                    }
                } else {
                    if (MyDebug.LOG) Log.e(TAG, "not enough free storage to record video")
                    throw NoFreeStorageException()
                }
            } else {
                Logger.d(TAG, "can't determine remaining free space")
            }
        }

        return video_max_filesize
    }

    override fun getVideoFlashPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.VideoFlashPreferenceKey, false)
    }

    override fun getVideoLowPowerCheckPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.VideoLowPowerCheckPreferenceKey, true)
    }

    override fun getPreviewSizePref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.PreviewSizePreferenceKey, "preference_preview_size_wysiwyg"
        )!!
    }

    override fun getLockOrientationPref(): String {
        if (this.photoMode == PhotoMode.Panorama) return "portrait" // for now panorama only supports portrait

        return sharedPreferences.getString(PreferenceKeys.LockOrientationPreferenceKey, "none")!!
    }

    override fun getTouchCapturePref(): Boolean {
        val value: String =
            sharedPreferences.getString(PreferenceKeys.TouchCapturePreferenceKey, "none")!!
        return value == "single"
    }

    override fun getDoubleTapCapturePref(): Boolean {
        val value: String =
            sharedPreferences.getString(PreferenceKeys.TouchCapturePreferenceKey, "none")!!
        return value == "double"
    }

    override fun getPausePreviewPref(): Boolean {
        if (main_activity.preview!!.isVideoRecording()) {
            // don't pause preview when taking photos while recording video!
            return false
        } else if (main_activity.lastContinuousFastBurst()) {
            // Don't use pause preview mode when doing a continuous fast burst
            // Firstly due to not using background thread for pause preview mode, this will be
            // sluggish anyway, but even when this is fixed, I'm not sure it makes sense to use
            // pause preview in this mode.
            return false
        } else if (this.photoMode == PhotoMode.Panorama) {
            // don't pause preview when taking photos for panorama mode
            return false
        }
        return sharedPreferences.getBoolean(PreferenceKeys.PausePreviewPreferenceKey, false)
    }

    override fun getShowToastsPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.ShowToastsPreferenceKey, true)
    }

    val thumbnailAnimationPref: Boolean
        get() = sharedPreferences.getBoolean(PreferenceKeys.ThumbnailAnimationPreferenceKey, true)

    override fun getShutterSoundPref(): Boolean {
        if (this.photoMode == PhotoMode.Panorama) return false
        return sharedPreferences.getBoolean(PreferenceKeys.ShutterSoundPreferenceKey, true)
    }

    override fun getStartupFocusPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.StartupFocusPreferenceKey, true)
    }

    override fun getTimerPref(): Long {
        if (this.photoMode == PhotoMode.Panorama) return 0 // don't support timer with panorama

        val timer_value: String =
            sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0")!!
        var timer_delay: Long
        try {
            timer_delay = timer_value.toInt().toLong() * 1000
        } catch (e: NumberFormatException) {
            if (MyDebug.LOG) Log.e(TAG, "failed to parse preference_timer value: " + timer_value)
            e.printStackTrace()
            timer_delay = 0
        }
        return timer_delay
    }

    override fun getRepeatPref(): String {
        if (this.photoMode == PhotoMode.Panorama) return "1" // don't support repeat with panorama

        return sharedPreferences.getString(PreferenceKeys.RepeatModePreferenceKey, "1")!!
    }

    override fun getRepeatIntervalPref(): Long {
        val timer_value: String =
            sharedPreferences.getString(PreferenceKeys.RepeatIntervalPreferenceKey, "0")!!
        var timer_delay: Long
        try {
            val timer_delay_s = timer_value.toFloat()
            Logger.d(TAG, "timer_delay_s: " + timer_delay_s)
            timer_delay = (timer_delay_s * 1000).toLong()
        } catch (e: NumberFormatException) {
            if (MyDebug.LOG) Log.e(TAG, "failed to parse repeat interval value: " + timer_value)
            e.printStackTrace()
            timer_delay = 0
        }
        return timer_delay
    }

    private val removeDeviceExifPref: RemoveDeviceExif
        get() {
            when (sharedPreferences.getString(
                PreferenceKeys.RemoveDeviceExifPreferenceKey, "preference_remove_device_exif_off"
            )) {
                "preference_remove_device_exif_on" -> return RemoveDeviceExif.ON
                "preference_remove_device_exif_keep_datetime" -> return RemoveDeviceExif.KEEP_DATETIME
                else -> return RemoveDeviceExif.OFF
            }
        }

    override fun getGeotaggingPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.LocationPreferenceKey, false)
    }

    override fun getRequireLocationPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.RequireLocationPreferenceKey, false)
    }

    val geodirectionPref: Boolean
        get() = sharedPreferences.getBoolean(PreferenceKeys.GPSDirectionPreferenceKey, false)

    override fun getRecordAudioPref(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.RecordAudioPreferenceKey, true)
    }

    override fun getRecordAudioChannelsPref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.RecordAudioChannelsPreferenceKey, "audio_default"
        )!!
    }

    override fun getRecordAudioSourcePref(): String {
        return sharedPreferences.getString(
            PreferenceKeys.RecordAudioSourcePreferenceKey, "audio_src_camcorder"
        )!!
    }

    val focusPeakingPref: Boolean
        get() {
            val focus_peaking_pref: String = sharedPreferences.getString(
                PreferenceKeys.FocusPeakingPreferenceKey, "preference_focus_peaking_off"
            )!!
            return focus_peaking_pref != "preference_focus_peaking_off" && main_activity.supportsPreviewBitmaps()
        }

    fun getPreShotsPref(photo_mode: PhotoMode?): Boolean {
        if (main_activity.preview!!.isVideo || photo_mode == PhotoMode.ExpoBracketing || photo_mode == PhotoMode.FocusBracketing || photo_mode == PhotoMode.Panorama) {
            // pre-shots not supported for these modes
            return false
        }
        val pre_shots_pref: String = sharedPreferences.getString(
            PreferenceKeys.PreShotsPreferenceKey, "preference_save_preshots_off"
        )!!
        return pre_shots_pref != "preference_save_preshots_off" && main_activity.supportsPreShots()
    }

    val autoStabilisePref: Boolean
        get() {
            val auto_stabilise =
                sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false)
            return auto_stabilise && main_activity.supportsAutoStabilise()
        }

    val ghostImageAlpha: Int
        /** Returns the alpha value to use for ghost image, as a number from 0 to 255.
         * Note that we store the preference as a percentage from 0 to 100, but scale this to 0 to 255.
         */
        get() {
            val ghost_image_alpha_value: String =
                sharedPreferences.getString(PreferenceKeys.GhostImageAlphaPreferenceKey, "50")!!
            var ghost_image_alpha: Int
            try {
                ghost_image_alpha = ghost_image_alpha_value.toInt()
            } catch (e: NumberFormatException) {
                if (MyDebug.LOG) Log.e(
                    TAG, "failed to parse ghost_image_alpha_value: " + ghost_image_alpha_value
                )
                e.printStackTrace()
                ghost_image_alpha = 50
            }
            ghost_image_alpha = (ghost_image_alpha * 2.55f + 0.1f).toInt()
            return ghost_image_alpha
        }

    val stampPref: String
        get() = sharedPreferences.getString(
            PreferenceKeys.StampPreferenceKey, "preference_stamp_no"
        )!!

    private val stampDateFormatPref: String
        get() = sharedPreferences.getString(
            PreferenceKeys.StampDateFormatPreferenceKey, "preference_stamp_dateformat_default"
        )!!

    private val stampTimeFormatPref: String
        get() = sharedPreferences.getString(
            PreferenceKeys.StampTimeFormatPreferenceKey, "preference_stamp_timeformat_default"
        )!!

    private val stampGPSFormatPref: String
        get() = sharedPreferences.getString(
            PreferenceKeys.StampGPSFormatPreferenceKey, "preference_stamp_gpsformat_default"
        )!!

    private val unitsDistancePref: String
        /*private String getStampGeoAddressPref() {
                 return sharedPreferences.getString(PreferenceKeys.StampGeoAddressPreferenceKey, "preference_stamp_geo_address_no");
             }*/
        get() = sharedPreferences.getString(
            PreferenceKeys.UnitsDistancePreferenceKey, "preference_units_distance_m"
        )!!

    val textStampPref: String
        get() = sharedPreferences.getString(PreferenceKeys.TextStampPreferenceKey, "")!!

    private val textStampFontSizePref: Int
        get() {
            var font_size = 12
            val value: String =
                sharedPreferences.getString(PreferenceKeys.StampFontSizePreferenceKey, "12")!!
            Logger.d(
                TAG, "saved font size: $value"
            )
            try {
                font_size = value.toInt()
                Logger.d(
                    TAG, "font_size: $font_size"
                )
            } catch (exception: NumberFormatException) {
                Logger.d(
                    TAG, "font size invalid format, can't parse to int"
                )
            }
            return font_size
        }

    private fun getVideoSubtitlePref(video_method: VideoMethod?): String {
        if (video_method == VideoMethod.MEDIASTORE && !mediastoreSupportsVideoSubtitles()) {
            return "preference_video_subtitle_no"
        }
        return sharedPreferences.getString(
            PreferenceKeys.VideoSubtitlePref, "preference_video_subtitle_no"
        )!!
    }

    override fun getZoomPref(): Int {
        Logger.d(TAG, "getZoomPref: " + zoom_factor)
        return zoom_factor
    }

    override fun getCalibratedLevelAngle(): Double {
        return sharedPreferences.getFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, 0.0f)
            .toDouble()
    }

    override fun canTakeNewPhoto(): Boolean {
        Logger.d(TAG, "canTakeNewPhoto")

        val n_raw: Int
        var n_jpegs: Int
        if (main_activity.preview?.isVideo == true) {
            // video snapshot mode
            n_raw = 0
            n_jpegs = 1
        } else {
            n_jpegs = 1 // default

            if (main_activity.preview!!.supportsExpoBracketing() && this.isExpoBracketingPref()) {
                n_jpegs = this.getExpoBracketingNImagesPref()
            } else if (main_activity.preview!!.supportsFocusBracketing() && this.isFocusBracketingPref()) {
                // focus bracketing mode always avoids blocking the image queue, no matter how many images are being taken
                // so all that matters is that we can take at least 1 photo (for the first shot)
                //n_jpegs = this.getFocusBracketingNImagesPref();
                n_jpegs = 1
            } else if (main_activity.preview!!.supportsBurst() && this.isCameraBurstPref()) {
                if (this.getBurstForNoiseReduction()) {
                    if (this.getNRModePref() == NRModePref.NRMODE_LOW_LIGHT) {
                        n_jpegs = CameraController.N_IMAGES_NR_DARK_LOW_LIGHT
                    } else {
                        n_jpegs = CameraController.N_IMAGES_NR_DARK
                    }
                } else {
                    n_jpegs = this.getBurstNImages()
                }
            }

            if (main_activity.preview!!.supportsRaw() && this.getRawPref() == RawPref.RAWPREF_JPEG_DNG) {
                // note, even in RAW only mode, the CameraController will still take JPEG+RAW (we still need to JPEG to
                // generate a bitmap from for thumbnail and pause preview option), so this still generates a request in
                // the ImageSaver
                n_raw = n_jpegs
            } else {
                n_raw = 0
            }
        }

        val photo_cost = imageSaver!!.computePhotoCost(n_raw, n_jpegs)
        if (imageSaver.queueWouldBlock(photo_cost)) {
            Logger.d(TAG, "canTakeNewPhoto: no, as queue would block")
            return false
        }

        // even if the queue isn't full, we may apply additional limits
        val n_images_to_save = imageSaver.getNImagesToSave()
        val photo_mode = this.photoMode
        if (photo_mode == PhotoMode.FastBurst || photo_mode == PhotoMode.Panorama) {
            // only allow one fast burst at a time, so require queue to be empty
            if (n_images_to_save > 0) {
                Logger.d(TAG, "canTakeNewPhoto: no, as too many for fast burst")
                return false
            }
        }
        if (photo_mode == PhotoMode.NoiseReduction) {
            // allow a max of 2 photos in memory when at max of 8 images
            if (n_images_to_save >= 2 * photo_cost) {
                Logger.d(TAG, "canTakeNewPhoto: no, as too many for nr")
                return false
            }
        }
        if (n_jpegs > 1) {
            // if in any other kind of burst mode (e.g., expo burst, HDR), allow a max of 3 photos in memory
            if (n_images_to_save >= 3 * photo_cost) {
                Logger.d(TAG, "canTakeNewPhoto: no, as too many for burst")
                return false
            }
        }
        if (n_raw > 0) {
            // if RAW mode, allow a max of 3 photos
            if (n_images_to_save >= 3 * photo_cost) {
                Logger.d(TAG, "canTakeNewPhoto: no, as too many for raw")
                return false
            }
        }
        // otherwise, still have a max limit of 5 photos
        if (n_images_to_save >= 5 * photo_cost) {
            if (main_activity.supportsNoiseReduction() && n_images_to_save <= 8) {
                // if we take a photo in NR mode, then switch to std mode, it doesn't make sense to suddenly block!
                // so need to at least allow a new photo, if the number of photos is less than 1 NR photo
            } else {
                Logger.d(TAG, "canTakeNewPhoto: no, as too many for regular")
                return false
            }
        }

        return true
    }

    override fun imageQueueWouldBlock(n_raw: Int, n_jpegs: Int): Boolean {
        Logger.d(TAG, "imageQueueWouldBlock")
        return imageSaver!!.queueWouldBlock(n_raw, n_jpegs)
    }

    /** Returns the ROTATION_* enum of the display relative to the natural device orientation, but
     * also checks for the preview being rotated due to user preference
     * RotatePreviewPreferenceKey.
     * See ApplicationInterface.getDisplayRotation() for more details, including for prefer_later.
     */
    override fun getDisplayRotation(prefer_later: Boolean): Int {
        // important to use cached rotation to reduce issues of incorrect focus square location when
        // rotating device, due to strange Android behaviour where rotation changes shortly before
        // the configuration actually changes
        var rotation = main_activity.getDisplayRotation(prefer_later)

        val rotate_preview: String =
            sharedPreferences.getString(PreferenceKeys.RotatePreviewPreferenceKey, "0")!!
        if (MyDebug.LOG) Logger.d(TAG, "    rotate_preview = " + rotate_preview)
        if (rotate_preview == "180") {
            when (rotation) {
                Surface.ROTATION_0 -> rotation = Surface.ROTATION_180
                Surface.ROTATION_90 -> rotation = Surface.ROTATION_270
                Surface.ROTATION_180 -> rotation = Surface.ROTATION_0
                Surface.ROTATION_270 -> rotation = Surface.ROTATION_90
                else -> {}
            }
        }

        return rotation
    }

    override fun getExposureTimePref(): Long {
        return sharedPreferences.getLong(
            PreferenceKeys.ExposureTimePreferenceKey, CameraController.EXPOSURE_TIME_DEFAULT
        )
    }

    override fun getFocusDistancePref(isTargetDistance: Boolean): Float {
        return sharedPreferences.getFloat(
            if (isTargetDistance) PreferenceKeys.FocusBracketingTargetDistancePreferenceKey else PreferenceKeys.FocusDistancePreferenceKey,
            0.0f
        )
    }

    override fun isFocusBracketingSourceAutoPref(): Boolean {
        if (!main_activity.supportsFocusBracketingSourceAuto()) return false // not supported

        return sharedPreferences.getBoolean(
            PreferenceKeys.FocusBracketingAutoSourceDistancePreferenceKey, false
        )
    }

    /** Sets whether in focus bracketing auto focusing mode for source focus distance.
     * If enabled==false (i.e. returning to manual mode), the caller should call Preview.setFocusDistance()
     * to set the new manual focus distance.
     */
    fun setFocusBracketingSourceAutoPref(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(PreferenceKeys.FocusBracketingAutoSourceDistancePreferenceKey, enabled)
        }
        if (main_activity.preview!!.cameraController != null) {
            main_activity.preview!!.setFocusPref(true)
        }
    }

    override fun isExpoBracketingPref(): Boolean {
        val photoMode = this.photoMode
        return photoMode == PhotoMode.HDR || photoMode == PhotoMode.ExpoBracketing
    }

    override fun isFocusBracketingPref(): Boolean {
        val photoMode = this.photoMode
        return photoMode == PhotoMode.FocusBracketing
    }

    override fun isCameraBurstPref(): Boolean {
        val photoMode = this.photoMode
        return photoMode == PhotoMode.FastBurst || photoMode == PhotoMode.NoiseReduction
    }

    override fun getBurstNImages(): Int {
        val photoMode = this.photoMode
        if (photoMode == PhotoMode.FastBurst) {
            val nImagesValue: String =
                sharedPreferences.getString(PreferenceKeys.FastBurstNImagesPreferenceKey, "5")!!
            var nImages: Int
            try {
                nImages = nImagesValue.toInt()
            } catch (e: NumberFormatException) {
                Logger.e(TAG, "failed to parse FastBurstNImagesPreferenceKey value: $nImagesValue")
                e.printStackTrace()
                nImages = 5
            }
            return nImages
        }
        return 1
    }

    override fun getBurstForNoiseReduction(): Boolean {
        val photoMode = this.photoMode
        return photoMode == PhotoMode.NoiseReduction
    }

    override fun getNRModePref(): NRModePref {/*if( MyDebug.LOG )
			Logger.d(TAG, "nr_mode: " + nr_mode);*/
        when (this.nRMode) {
            "preference_nr_mode_low_light" -> return NRModePref.NRMODE_LOW_LIGHT
        }
        return NRModePref.NRMODE_NORMAL
    }

    override fun isCameraExtensionPref(): Boolean {
        val mode = this.photoMode
        return mode == PhotoMode.X_Auto || mode == PhotoMode.X_HDR || mode == PhotoMode.X_Night || mode == PhotoMode.X_Bokeh || mode == PhotoMode.X_Beauty
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    override fun getCameraExtensionPref(): Int {
        val mode = this.photoMode
        when (mode) {
            PhotoMode.X_Auto -> {
                return CameraExtensionCharacteristics.EXTENSION_AUTOMATIC
            }
            PhotoMode.X_HDR -> {
                return CameraExtensionCharacteristics.EXTENSION_HDR
            }
            PhotoMode.X_Night -> {
                return CameraExtensionCharacteristics.EXTENSION_NIGHT
            }
            PhotoMode.X_Bokeh -> {
                return CameraExtensionCharacteristics.EXTENSION_BOKEH
            }
            PhotoMode.X_Beauty -> {
                return CameraExtensionCharacteristics.EXTENSION_BEAUTY
            }
            else -> return 0
        }
    }

    fun setAperture(aperture: Float) {
        this.aperture = aperture
    }

    override fun getAperturePref(): Float {
        return aperture
    }

    override fun getExpoBracketingNImagesPref(): Int {
        if (MyDebug.LOG) Logger.d(TAG, "getExpoBracketingNImagesPref")
        var n_images: Int
        val photo_mode = this.photoMode
        if (photo_mode == PhotoMode.HDR) {
            // always set 3 images for HDR
            n_images = 3
        } else {
            val n_images_s: String = sharedPreferences.getString(
                PreferenceKeys.ExpoBracketingNImagesPreferenceKey, "3"
            )!!
            try {
                n_images = n_images_s.toInt()
            } catch (exception: NumberFormatException) {
                if (MyDebug.LOG) Log.e(TAG, "n_images_s invalid format: " + n_images_s)
                n_images = 3
            }
        }
        if (MyDebug.LOG) Logger.d(TAG, "n_images = " + n_images)
        return n_images
    }

    override fun getExpoBracketingStopsPref(): Double {
        if (MyDebug.LOG) Logger.d(TAG, "getExpoBracketingStopsPref")
        var n_stops: Double
        val photo_mode = this.photoMode
        if (photo_mode == PhotoMode.HDR) {
            // always set 2 stops for HDR
            n_stops = 2.0
        } else {
            val n_stops_s: String =
                sharedPreferences.getString(PreferenceKeys.ExpoBracketingStopsPreferenceKey, "2")!!
            try {
                n_stops = n_stops_s.toDouble()
            } catch (exception: NumberFormatException) {
                if (MyDebug.LOG) Log.e(TAG, "n_stops_s invalid format: " + n_stops_s)
                n_stops = 2.0
            }
        }
        if (MyDebug.LOG) Logger.d(TAG, "n_stops = " + n_stops)
        return n_stops
    }

    override fun getFocusBracketingNImagesPref(): Int {
        if (MyDebug.LOG) Logger.d(TAG, "getFocusBracketingNImagesPref")
        var n_images: Int
        val n_images_s: String =
            sharedPreferences.getString(PreferenceKeys.FocusBracketingNImagesPreferenceKey, "3")!!
        try {
            n_images = n_images_s.toInt()
        } catch (exception: NumberFormatException) {
            if (MyDebug.LOG) Log.e(TAG, "n_images_s invalid format: " + n_images_s)
            n_images = 3
        }
        if (MyDebug.LOG) Logger.d(TAG, "n_images = " + n_images)
        return n_images
    }

    override fun getFocusBracketingAddInfinityPref(): Boolean {
        return sharedPreferences.getBoolean(
            PreferenceKeys.FocusBracketingAddInfinityPreferenceKey, false
        )
    }

    val photoMode: PhotoMode
        /** Returns the current photo mode.
         * Note, this always should return the true photo mode - if we're in video mode and taking a photo snapshot while
         * video recording, the caller should override. We don't override here, as this preference may be used to affect how
         * the CameraController is set up, and we don't always re-setup the camera when switching between photo and video modes.
         */
        get() {
            val photo_mode_pref: String = sharedPreferences.getString(
                PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std"
            )!!/*if( MyDebug.LOG )
         Logger.d(TAG, "photo_mode_pref: " + photo_mode_pref);*/
            val dro = photo_mode_pref == "preference_photo_mode_dro"
            if (dro && main_activity.supportsDRO()) return PhotoMode.DRO
            val hdr = photo_mode_pref == "preference_photo_mode_hdr"
            if (hdr && main_activity.supportsHDR()) return PhotoMode.HDR
            val expo_bracketing = photo_mode_pref == "preference_photo_mode_expo_bracketing"
            if (expo_bracketing && main_activity.supportsExpoBracketing()) return PhotoMode.ExpoBracketing
            val focus_bracketing = photo_mode_pref == "preference_photo_mode_focus_bracketing"
            if (focus_bracketing && main_activity.supportsFocusBracketing()) return PhotoMode.FocusBracketing
            val fast_burst = photo_mode_pref == "preference_photo_mode_fast_burst"
            if (fast_burst && main_activity.supportsFastBurst()) return PhotoMode.FastBurst
            val noise_reduction = photo_mode_pref == "preference_photo_mode_noise_reduction"
            if (noise_reduction && main_activity.supportsNoiseReduction()) return PhotoMode.NoiseReduction
            val panorama = photo_mode_pref == "preference_photo_mode_panorama"
            if (panorama && !main_activity.preview!!.isVideo && main_activity.supportsPanorama()) return PhotoMode.Panorama
            val x_auto = photo_mode_pref == "preference_photo_mode_x_auto"
            if (x_auto && !main_activity.preview!!.isVideo && main_activity.supportsCameraExtension(
                    CameraExtensionCharacteristics.EXTENSION_AUTOMATIC
                )
            ) return PhotoMode.X_Auto
            val x_hdr = photo_mode_pref == "preference_photo_mode_x_hdr"
            if (x_hdr && !main_activity.preview!!.isVideo && main_activity.supportsCameraExtension(
                    CameraExtensionCharacteristics.EXTENSION_HDR
                )
            ) return PhotoMode.X_HDR
            val x_night = photo_mode_pref == "preference_photo_mode_x_night"
            if (x_night && !main_activity.preview!!.isVideo && main_activity.supportsCameraExtension(
                    CameraExtensionCharacteristics.EXTENSION_NIGHT
                )
            ) return PhotoMode.X_Night
            val x_bokeh = photo_mode_pref == "preference_photo_mode_x_bokeh"
            if (x_bokeh && !main_activity.preview!!.isVideo && main_activity.supportsCameraExtension(
                    CameraExtensionCharacteristics.EXTENSION_BOKEH
                )
            ) return PhotoMode.X_Bokeh
            val x_beauty = photo_mode_pref == "preference_photo_mode_x_beauty"
            if (x_beauty && !main_activity.preview!!.isVideo && main_activity.supportsCameraExtension(
                    CameraExtensionCharacteristics.EXTENSION_BEAUTY
                )
            ) return PhotoMode.X_Beauty
            return PhotoMode.Standard
        }

    override fun getJpegRPref(): Boolean {
        if (sharedPreferences.getString(
                PreferenceKeys.ImageFormatPreferenceKey, "preference_image_format_jpeg"
            ) == "preference_image_format_jpeg_r"
        ) {
            if (main_activity.preview!!.isVideo) {
                // don't support JPEG R, either for video recording or video snapshot - problem that video recording fails
                // if CameraController2 sets "config.setDynamicRangeProfile(DynamicRangeProfiles.HLG10);" for the preview
                return false
            } else {
                val photo_mode = this.photoMode
                if (photo_mode == PhotoMode.NoiseReduction || photo_mode == PhotoMode.HDR || photo_mode == PhotoMode.Panorama) return false // not supported for these photo modes

                // n.b., JPEG R won't be supported by x- extension modes either, although this is automatically handled by Preview
                return true
            }
        }
        return false
    }

    private val imageFormatPref: ImageSaver.Request.ImageFormat
        get() {
            return when (sharedPreferences.getString(
                PreferenceKeys.ImageFormatPreferenceKey, "preference_image_format_jpeg"
            )) {
                "preference_image_format_webp" -> ImageSaver.Request.ImageFormat.WEBP
                "preference_image_format_png" -> ImageSaver.Request.ImageFormat.PNG
                else -> ImageSaver.Request.ImageFormat.STD
            }
        }

    /** Returns whether RAW is currently allowed, even if RAW is enabled in the preference (RAW
     * isn't allowed for some photo modes, or in video mode, or when called from an intent).
     * Note that this doesn't check whether RAW is supported by the camera.
     */
    fun isRawAllowed(photo_mode: PhotoMode?): Boolean {
        if (this.isImageCaptureIntent) return false
        if (main_activity.preview?.isVideo == true) return false // video snapshot mode

        //return photo_mode == PhotoMode.Standard || photo_mode == PhotoMode.DRO;
        if (photo_mode == PhotoMode.Standard || photo_mode == PhotoMode.DRO) {
            return true
        } else if (photo_mode == PhotoMode.ExpoBracketing) {
            return sharedPreferences.getBoolean(
                PreferenceKeys.AllowRawForExpoBracketingPreferenceKey, true
            ) && main_activity.supportsBurstRaw()
        } else if (photo_mode == PhotoMode.HDR) {
            // for HDR, RAW is only relevant if we're going to be saving the base expo images (otherwise there's nothing to save)
            return sharedPreferences.getBoolean(
                PreferenceKeys.HDRSaveExpoPreferenceKey, false
            ) && sharedPreferences.getBoolean(
                PreferenceKeys.AllowRawForExpoBracketingPreferenceKey, true
            ) && main_activity.supportsBurstRaw()
        } else if (photo_mode == PhotoMode.FocusBracketing) {
            return sharedPreferences.getBoolean(
                PreferenceKeys.AllowRawForFocusBracketingPreferenceKey, true
            ) && main_activity.supportsBurstRaw()
        }
        // not supported for panorama mode
        // not supported for camera vendor extensions
        return false
    }

    /** Return whether to capture JPEG, or RAW+JPEG.
     * Note even if in RAW only mode, we still capture RAW+JPEG - the JPEG is needed for things like
     * getting the bitmap for the thumbnail and pause preview option; we simply don't do any post-
     * processing or saving on the JPEG.
     */
    override fun getRawPref(): RawPref {
        val photo_mode = this.photoMode
        if (isRawAllowed(photo_mode)) {
            when (sharedPreferences.getString(
                PreferenceKeys.RawPreferenceKey, "preference_raw_no"
            )) {
                "preference_raw_yes", "preference_raw_only" -> return RawPref.RAWPREF_JPEG_DNG
            }
        }
        return RawPref.RAWPREF_JPEG_ONLY
    }

    val isRawOnly: Boolean
        /** Whether RAW only mode is enabled.
         */
        get() {
            val photo_mode = this.photoMode
            return isRawOnly(photo_mode)
        }

    /** Use this instead of isRawOnly() if the photo mode is already known - useful to call e.g. from MainActivity.supportsDRO()
     * without causing an infinite loop!
     */
    fun isRawOnly(photo_mode: PhotoMode?): Boolean {
        if (isRawAllowed(photo_mode)) {
            when (sharedPreferences.getString(
                PreferenceKeys.RawPreferenceKey, "preference_raw_no"
            )) {
                "preference_raw_only" -> return true
            }
        }
        return false
    }

    override fun getMaxRawImages(): Int {
        return imageSaver!!.getMaxDNG()
    }

    override fun useCamera2FakeFlash(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2FakeFlashPreferenceKey, false)
    }

    override fun useCamera2DummyCaptureHack(): Boolean {
        return sharedPreferences.getBoolean(
            PreferenceKeys.Camera2DummyCaptureHackPreferenceKey, false
        )
    }

    override fun useCamera2FastBurst(): Boolean {
        return sharedPreferences.getBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, true)
    }

    override fun usePhotoVideoRecording(): Boolean {
        // we only show the preference for Camera2 API (since there's no point disabling the feature for old API)
        if (!useCamera2()) return true
        return sharedPreferences.getBoolean(
            PreferenceKeys.Camera2PhotoVideoRecordingPreferenceKey, true
        )
    }

    override fun isPreviewInBackground(): Boolean {
        return main_activity.isCameraInBackground
    }

    override fun allowZoom(): Boolean {
        if (this.photoMode == PhotoMode.Panorama) {
            // don't allow zooming in panorama mode, the algorithm isn't set up to support this!
            return false
        } else if (isCameraExtensionPref() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !main_activity.preview!!.supportsZoomForCameraExtension(
                getCameraExtensionPref()
            )
        ) {
            // zoom not supported for camera extension
            return false
        }
        return true
    }

    override fun optimiseFocusForLatency(): Boolean {
        val pref: String = sharedPreferences.getString(
            PreferenceKeys.OptimiseFocusPreferenceKey, "preference_photo_optimise_focus_latency"
        )!!
        return pref == "preference_photo_optimise_focus_latency" && main_activity.supportsOptimiseFocusLatency()
    }

    override fun getDisplaySize(display_size: Point, exclude_insets: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val window_metrics = main_activity.windowManager.currentWindowMetrics
            val bounds = window_metrics.bounds
            if (!main_activity.edgeToEdgeMode || exclude_insets) {
                // use non-deprecated equivalent of Display.getSize()
                val windowInsets = window_metrics.windowInsets
                val insets =
                    windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                val insetsWidth = insets.right + insets.left
                val insetsHeight = insets.top + insets.bottom
                display_size.x = bounds.width() - insetsWidth
                display_size.y = bounds.height() - insetsHeight
            } else {
                display_size.x = bounds.width()
                display_size.y = bounds.height()
            }
        } else {
            val display = main_activity.windowManager.defaultDisplay
            display.getSize(display_size)
        }
    }

    override fun isTestAlwaysFocus(): Boolean {
        if (MyDebug.LOG) {
            Logger.d(TAG, "isTestAlwaysFocus: " + main_activity.is_test)
        }
        return main_activity.is_test
    }

    override fun cameraSetup() {
        main_activity.cameraSetup()
        drawPreview!!.clearContinuousFocusMove()
        // Need to cause drawPreview.updateSettings(), otherwise icons like HDR won't show after force-restart, because we only
        // know that HDR is supported after the camera is opened
        // Also needed for settings which update when switching between photo and video mode.
        drawPreview.updateSettings()
    }

    override fun onContinuousFocusMove(start: Boolean) {
        if (MyDebug.LOG) Logger.d(TAG, "onContinuousFocusMove: $start")
        drawPreview!!.onContinuousFocusMove(start)
    }

    fun startPanorama() {
        if (MyDebug.LOG) Logger.d(TAG, "startPanorama")
        gyroSensor.startRecording()
        n_panorama_pics = 0
        panorama_pic_accepted = false
        panorama_dir_left_to_right = true

        main_activity.mainUI!!.setTakePhotoIcon()
        val cancelPanoramaButton = main_activity.findViewById<View>(R.id.cancel_panorama)
        cancelPanoramaButton.visibility = View.VISIBLE
    }

    /** Ends panorama and submits the panoramic images to be processed.
     */
    fun finishPanorama() {
        if (MyDebug.LOG) Logger.d(TAG, "finishPanorama")

        imageSaver!!.imageBatchRequest.panorama_dir_left_to_right = this.panorama_dir_left_to_right

        stopPanorama(false)

        val image_capture_intent = this.isImageCaptureIntent
        val do_in_background = saveInBackground(image_capture_intent)
        imageSaver.finishImageBatch(do_in_background)
    }

    /** Stop the panorama recording. Does nothing if panorama isn't currently recording.
     * @param is_cancelled Whether the panorama has been cancelled.
     */
    fun stopPanorama(is_cancelled: Boolean) {
        if (MyDebug.LOG) Logger.d(TAG, "stopPanorama")
        if (!gyroSensor.isRecording()) {
            if (MyDebug.LOG) Logger.d(TAG, "...nothing to stop")
            return
        }
        gyroSensor.stopRecording()
        clearPanoramaPoint()
        if (is_cancelled) {
            imageSaver!!.flushImageBatch()
        }
        main_activity.mainUI!!.setTakePhotoIcon()
        val cancelPanoramaButton = main_activity.findViewById<View>(R.id.cancel_panorama)
        cancelPanoramaButton.setVisibility(View.GONE)
        main_activity.mainUI!!.showGUI() // refresh UI icons now that we've stopped panorama
    }

    private fun setNextPanoramaPoint(repeat: Boolean) {
        if (MyDebug.LOG) Logger.d(TAG, "setNextPanoramaPoint")
        val camera_angle_y = main_activity.preview!!.getViewAngleY(false)
        if (!repeat) n_panorama_pics++
        if (MyDebug.LOG) Logger.d(TAG, "n_panorama_pics is now: " + n_panorama_pics)
        if (n_panorama_pics == max_panorama_pics_c) {
            if (MyDebug.LOG) Logger.d(TAG, "reached max panorama limit")
            finishPanorama()
            return
        }
        var angle = Math.toRadians(camera_angle_y.toDouble()).toFloat() * n_panorama_pics
        if (n_panorama_pics > 1 && !panorama_dir_left_to_right) {
            angle = -angle // for right-to-left
        }
        var x = sin((angle / panoramaPicsPerScreen).toDouble()).toFloat()
        var z = -cos((angle / panoramaPicsPerScreen).toDouble()).toFloat()
        setNextPanoramaPoint(x, 0.0f, z)

        if (n_panorama_pics == 1) {
            // also set target for right-to-left
            angle = -angle
            x = sin((angle / panoramaPicsPerScreen).toDouble()).toFloat()
            z = -cos((angle / panoramaPicsPerScreen).toDouble()).toFloat()
            gyroSensor.addTarget(x, 0.0f, z)
            drawPreview!!.addGyroDirectionMarker(x, 0.0f, z)
        }
    }

    private fun setNextPanoramaPoint(x: Float, y: Float, z: Float) {
        if (MyDebug.LOG) Logger.d(
            TAG, "setNextPanoramaPoint : " + x + " , " + y + " , " + z
        )

        val target_angle = 1.0f * 0.01745329252f
        //final float target_angle = 0.5f * 0.01745329252f;
        // good to not allow too small an angle for upright_angle_tol - as sometimes the device may
        // get in a state where what we think is upright isn't quite right, and frustrating for users
        // to be told they have to tilt to not be upright
        val upright_angle_tol = 3.0f * 0.017452406437f
        //final float upright_angle_tol = 2.0f * 0.017452406437f;
        val too_far_angle = 45.0f * 0.01745329252f
        gyroSensor.setTarget(
            x, y, z, target_angle, upright_angle_tol, too_far_angle, object : TargetCallback {
                override fun onAchieved(indx: Int) {
                    if (MyDebug.LOG) {
                        Logger.d(TAG, "TargetCallback.onAchieved: " + indx)
                        Logger.d(TAG, "    n_panorama_pics: " + n_panorama_pics)
                    }
                    // Disable the target callback so we avoid risk of multiple callbacks - but note we don't call
                    // clearPanoramaPoint(), as we don't want to call drawPreview.clearGyroDirectionMarker()
                    // at this stage (looks better to keep showing the target market on-screen whilst photo
                    // is being taken, user more likely to keep the device still).
                    // Also we still keep the target active (and don't call clearTarget() so we can monitor if
                    // the target is still achieved or not (for panorama_pic_accepted).
                    //gyroSensor.clearTarget();
                    gyroSensor.disableTargetCallback()
                    if (n_panorama_pics == 1) {
                        panorama_dir_left_to_right = indx == 0
                        if (MyDebug.LOG) Logger.d(
                            TAG, "set panorama_dir_left_to_right to " + panorama_dir_left_to_right
                        )
                    }
                    main_activity.takePicturePressed(false, false)
                }

                override fun onTooFar() {
                    if (MyDebug.LOG) Logger.d(TAG, "TargetCallback.onTooFar")

                    // it's better not to cancel the panorama if the user moves the device too far in wrong direction
                    /*if( !main_activity.is_test ) {
                    main_activity.getPreview().showToast(null, R.string.panorama_cancelled, true);
                    MyApplicationInterface.this.stopPanorama(true);
                }*/
                }
            })
        drawPreview!!.setGyroDirectionMarker(x, y, z)
    }

    private fun clearPanoramaPoint() {
        if (MyDebug.LOG) Logger.d(TAG, "clearPanoramaPoint")
        gyroSensor.clearTarget()
        drawPreview!!.clearGyroDirectionMarker()
    }

    override fun touchEvent(event: MotionEvent?) {
        if (main_activity.usingKitKatImmersiveMode()) {
            main_activity.setImmersiveMode(false)
        }
    }

    override fun startingVideo() {
        if (sharedPreferences.getBoolean(PreferenceKeys.LockVideoPreferenceKey, false)) {
            main_activity.lockScreen()
        }
        main_activity.stopAudioListeners() // important otherwise MediaRecorder will fail to start() if we have an audiolistener! Also don't want to have the speech recognizer going off
    }

    private fun startVideoSubtitlesTask(video_method: VideoMethod?) {
        val preference_stamp_dateformat = this.stampDateFormatPref
        val preference_stamp_timeformat = this.stampTimeFormatPref
        val preference_stamp_gpsformat = this.stampGPSFormatPref
        val preference_units_distance = this.unitsDistancePref
        //final String preference_stamp_geo_address = this.getStampGeoAddressPref();
        val store_location = getGeotaggingPref()
        val store_geo_direction = this.geodirectionPref

        class SubtitleVideoTimerTask : TimerTask() {
            // need to keep a reference to pfd_saf for as long as writer, to avoid getting garbage collected - see https://sourceforge.net/p/opencamera/tickets/417/
            private var pfd_saf: ParcelFileDescriptor? = null
            private var writer: OutputStreamWriter? = null
            private var uri: Uri? = null
            private var count = 1
            private var min_video_time_from: Long = 0

            fun getSubtitleFilename(video_filename: String): String {
                var video_filename = video_filename
                if (MyDebug.LOG) Logger.d(TAG, "getSubtitleFilename")
                val indx = video_filename.indexOf('.')
                if (indx != -1) {
                    video_filename = video_filename.substring(0, indx)
                }
                video_filename = video_filename + ".srt"
                if (MyDebug.LOG) Logger.d(TAG, "return filename: " + video_filename)
                return video_filename
            }

            override fun run() {
                if (MyDebug.LOG) Logger.d(TAG, "SubtitleVideoTimerTask run")
                val video_time =
                    main_activity.preview!!.getVideoTime(true) // n.b., in case of restarts due to max filesize, we only want the time for this video file!
                if (!main_activity.preview!!.isVideoRecording()) {
                    if (MyDebug.LOG) Logger.d(TAG, "no longer video recording")
                    return
                }
                if (main_activity.preview!!.isVideoRecordingPaused()) {
                    if (MyDebug.LOG) Logger.d(TAG, "video recording is paused")
                    return
                }
                val current_date = Date()
                val current_calendar = Calendar.getInstance()
                val offset_ms = current_calendar.get(Calendar.MILLISECOND)
                // We subtract an offset, because if the current time is say 00:00:03.425 and the video has been recording for
                // 1s, we instead need to record the video time when it became 00:00:03.000. This does mean that the GPS
                // location is going to be off by up to 1s, but that should be less noticeable than the clock being off.
                if (MyDebug.LOG) {
                    Logger.d(TAG, "count: $count")
                    Logger.d(TAG, "offset_ms: $offset_ms")
                    Logger.d(TAG, "video_time: $video_time")
                }
                val date_stamp =
                    TextFormatter.getDateString(preference_stamp_dateformat, current_date)
                val time_stamp =
                    TextFormatter.getTimeString(preference_stamp_timeformat, current_date)
                val location = if (store_location) location else null
                val geo_direction =
                    if (store_geo_direction && main_activity.preview!!.hasGeoDirection()) main_activity.preview!!.geoDirection else 0.0
                val gps_stamp = main_activity.textFormatter!!.getGPSString(
                    preference_stamp_gpsformat,
                    preference_units_distance,
                    store_location && location != null,
                    location,
                    store_geo_direction && main_activity.preview!!.hasGeoDirection(),
                    geo_direction
                )
                if (MyDebug.LOG) {
                    Logger.d(TAG, "date_stamp: $date_stamp")
                    Logger.d(TAG, "time_stamp: $time_stamp")
                    // don't log gps_stamp, in case of privacy!
                }

                var datetime_stamp = ""
                if (date_stamp.isNotEmpty()) datetime_stamp += date_stamp
                if (time_stamp.isNotEmpty()) {
                    if (datetime_stamp.isNotEmpty()) datetime_stamp += " "
                    datetime_stamp += time_stamp
                }

                // build subtitles
                val subtitles = StringBuilder()
                if (datetime_stamp.isNotEmpty()) subtitles.append(datetime_stamp).append("\n")

                if (gps_stamp.isNotEmpty()) {
                    run {
                        if (MyDebug.LOG) Logger.d(TAG, "display gps coords")
                        subtitles.append(gps_stamp).append("\n")
                    }
                }

                if (subtitles.isEmpty()) {
                    return
                }
                var video_time_from = video_time - offset_ms
                val video_time_to = video_time_from + 999
                // don't want to start from before 0; also need to keep track of min_video_time_from to avoid bug reported at
                // https://forum.xda-developers.com/showpost.php?p=74827802&postcount=345 for pause video where we ended up
                // with overlapping times when resuming
                if (video_time_from < min_video_time_from) video_time_from = min_video_time_from
                min_video_time_from = video_time_to + 1
                val subtitle_time_from = TextFormatter.formatTimeMS(video_time_from)
                val subtitle_time_to = TextFormatter.formatTimeMS(video_time_to)
                try {
                    synchronized(this) {
                        if (writer == null) {
                            if (video_method == VideoMethod.FILE) {
                                var subtitle_filename = last_video_file!!.getAbsolutePath()
                                subtitle_filename = getSubtitleFilename(subtitle_filename)
                                writer = FileWriter(subtitle_filename)
                            } else if (video_method == VideoMethod.SAF || video_method == VideoMethod.MEDIASTORE) {
                                if (MyDebug.LOG) Logger.d(
                                    TAG, "last_video_file_uri: " + last_video_file_uri
                                )
                                var subtitle_filename =
                                    storageUtils.getFileName(last_video_file_uri)
                                subtitle_filename = getSubtitleFilename(subtitle_filename)
                                if (video_method == VideoMethod.SAF) {
                                    uri = storageUtils.createOutputFileSAF(
                                        subtitle_filename, ""
                                    ) // don't set a mimetype, as we don't want it to append a new extension
                                } else {
                                    val folder =
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(
                                            MediaStore.VOLUME_EXTERNAL_PRIMARY
                                        ) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                                    val contentValues = ContentValues()
                                    contentValues.put(
                                        MediaStore.Video.Media.DISPLAY_NAME, subtitle_filename
                                    )
                                    // set mime type - it's unclear if .SRT files have an official mime type, but (a) we must set a mime type otherwise
                                    // resultant files are named "*.srt.mp4", and (b) the mime type must be video/*, otherwise we get exception:
                                    // "java.lang.IllegalArgumentException: MIME type text/plain cannot be inserted into content://media/external_primary/video/media; expected MIME type under video/*"
                                    // and we need the file to be saved in the same folder (in DCIM/ ) as the video
                                    contentValues.put(
                                        MediaStore.Images.Media.MIME_TYPE, "video/x-srt"
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val relative_path = storageUtils.getSaveRelativeFolder()
                                        if (MyDebug.LOG) Logger.d(
                                            TAG, "relative_path: " + relative_path
                                        )
                                        contentValues.put(
                                            MediaStore.Video.Media.RELATIVE_PATH, relative_path
                                        )
                                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 1)
                                    }

                                    // Note, we catch exceptions specific to insert() here and rethrow as IOException,
                                    // rather than catching below, to avoid catching things too broadly.
                                    // Catching too broadly could mean we miss genuine problems that should be fixed.
                                    try {
                                        uri = main_activity.contentResolver.insert(
                                            folder, contentValues
                                        )
                                    } catch (e: IllegalArgumentException) {
                                        // can happen for mediastore method if invalid ContentResolver.insert() call
                                        if (MyDebug.LOG) Log.e(
                                            TAG,
                                            "IllegalArgumentException from SubtitleVideoTimerTask inserting to mediastore: " + e.message
                                        )
                                        e.printStackTrace()
                                        throw IOException()
                                    } catch (e: IllegalStateException) {
                                        if (MyDebug.LOG) Log.e(
                                            TAG,
                                            "IllegalStateException from SubtitleVideoTimerTask inserting to mediastore: " + e.message
                                        )
                                        e.printStackTrace()
                                        throw IOException()
                                    }
                                    if (uri == null) {
                                        throw IOException()
                                    }
                                }
                                if (MyDebug.LOG) Logger.d(TAG, "uri: $uri")
                                pfd_saf = context.contentResolver.openFileDescriptor(uri!!, "w")
                                writer = FileWriter(pfd_saf!!.fileDescriptor)
                            }
                        }
                        if (writer != null) {
                            writer!!.append(count.toString())
                            writer!!.append('\n')
                            writer!!.append(subtitle_time_from)
                            writer!!.append(" --> ")
                            writer!!.append(subtitle_time_to)
                            writer!!.append('\n')
                            writer!!.append(subtitles.toString()) // subtitles should include the '\n' at the end
                            writer!!.append('\n') // additional newline to indicate end of this subtitle
                            writer!!.flush()
                            // n.b., we flush rather than closing/reopening the writer each time, as appending doesn't seem to work with storage access framework
                        }
                    }
                    count++
                } catch (e: IOException) {
                    if (MyDebug.LOG) Log.e(TAG, "SubtitleVideoTimerTask failed to create or write")
                    e.printStackTrace()
                }
                if (MyDebug.LOG) Logger.d(TAG, "SubtitleVideoTimerTask exit")
            }

            override fun cancel(): Boolean {
                if (MyDebug.LOG) Logger.d(TAG, "SubtitleVideoTimerTask cancel")
                synchronized(this) {
                    if (writer != null) {
                        if (MyDebug.LOG) Logger.d(TAG, "close writer")
                        try {
                            writer!!.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        writer = null
                    }
                    if (pfd_saf != null) {
                        try {
                            pfd_saf!!.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        pfd_saf = null
                    }
                    if (video_method == VideoMethod.MEDIASTORE) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val contentValues = ContentValues()
                            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                            main_activity.contentResolver.update(uri!!, contentValues, null, null)
                        }
                    }
                }
                return super.cancel()
            }
        }
        subtitleVideoTimer.schedule(
            SubtitleVideoTimerTask().also { subtitleVideoTimerTask = it }, 0, 1000
        )
    }

    override fun startedVideo() {
        if (MyDebug.LOG) Logger.d(TAG, "startedVideo()")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            main_activity.mainUI!!.setPauseVideoContentDescription()
        }
        if (main_activity.preview!!.supportsPhotoVideoRecording() && this.usePhotoVideoRecording()) {
            if (!(main_activity.mainUI!!.inImmersiveMode() && main_activity.usingKitKatImmersiveModeEverything())) {
            }
        }
        if (main_activity.mainUI!!.isExposureUIOpen) {
            if (MyDebug.LOG) Logger.d(
                TAG, "need to update exposure UI for start video recording"
            )
            // need to update the exposure UI when starting/stopping video recording, to remove/add
            // ability to switch between auto and manual
            main_activity.mainUI!!.setupExposureUI()
        }
        val video_method = this.createOutputVideoMethod()
        val dategeo_subtitles =
            getVideoSubtitlePref(video_method) == "preference_video_subtitle_yes"
        if (dategeo_subtitles && video_method != VideoMethod.URI) {
            startVideoSubtitlesTask(video_method)
        }
    }

    override fun stoppingVideo() {
        if (MyDebug.LOG) Logger.d(TAG, "stoppingVideo()")
        main_activity.unlockScreen()
    }

    override fun stoppedVideo(video_method: VideoMethod?, uri: Uri?, filename: String?) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "stoppedVideo")
            Logger.d(TAG, "video_method $video_method")
            Logger.d(TAG, "uri $uri")
            Logger.d(TAG, "filename $filename")
        }
        main_activity.mainUI!!.setPauseVideoContentDescription() // just to be safe
        if (main_activity.mainUI!!.isExposureUIOpen) {
            if (MyDebug.LOG) Logger.d(
                TAG, "need to update exposure UI for stop video recording"
            )
            // need to update the exposure UI when starting/stopping video recording, to remove/add
            // ability to switch between auto and manual
            main_activity.mainUI!!.setupExposureUI()
        }
        if (subtitleVideoTimerTask != null) {
            subtitleVideoTimerTask!!.cancel()
            subtitleVideoTimerTask = null
        }

        completeVideo(video_method, uri)
        val done = broadcastVideo(video_method, uri, filename)

        if (this.isVideoCaptureIntent) {
            if (done && video_method == VideoMethod.FILE) {
                // do nothing here - we end the activity from storageUtils.broadcastFile after the file has been scanned, as it seems caller apps seem to prefer the content:// Uri rather than one based on a File
            } else {
                var output: Intent? = null
                if (done) {
                    // may need to pass back the Uri we saved to, if the calling application didn't specify a Uri
                    // set note above for VideoMethod.FILE
                    // n.b., currently this code is not used, as we always switch to VideoMethod.FILE if the calling application didn't specify a Uri, but I've left this here for possible future behaviour
                    if (video_method == VideoMethod.SAF || video_method == VideoMethod.MEDIASTORE) {
                        output = Intent()
                        output.data = uri
                    }
                }
                main_activity.setResult(
                    if (done) Activity.RESULT_OK else Activity.RESULT_CANCELED, output
                )
                main_activity.finish()
            }
        } else if (done) {
            // create thumbnail
            val debug_time = System.currentTimeMillis()
            var thumbnail: Bitmap? = null
            var pfd_saf: ParcelFileDescriptor? =
                null // keep a reference to this as long as retriever, to avoid risk of pfd_saf being garbage collected
            val retriever = MediaMetadataRetriever()
            try {
                if (video_method == VideoMethod.FILE) {
                    val file = File(filename)
                    retriever.setDataSource(file.getPath())
                } else {
                    uri?.let {
                        pfd_saf = context.contentResolver.openFileDescriptor(uri, "r")
                        retriever.setDataSource(pfd_saf!!.fileDescriptor)
                    }
                }
                thumbnail = retriever.getFrameAtTime(-1)
            } catch (e: FileNotFoundException) {
                // video file wasn't saved or corrupt video file?
                Logger.d(TAG, "failed to find thumbnail")
                e.printStackTrace()
            } catch (e: RuntimeException) {
                Logger.d(TAG, "failed to find thumbnail")
                e.printStackTrace()
            } finally {
                try {
                    retriever.release()
                } catch (ex: RuntimeException) {
                    // ignore
                } catch (ex: IOException) {
                }
                try {
                    pfd_saf?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if (thumbnail != null) {
                val thumbnail_f: Bitmap? = thumbnail
                main_activity.runOnUiThread(Runnable { updateThumbnail(thumbnail_f, true) })
            }
        }
    }

    override fun restartedVideo(video_method: VideoMethod?, uri: Uri?, filename: String?) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "restartedVideo")
            Logger.d(TAG, "video_method $video_method")
            Logger.d(TAG, "uri $uri")
            Logger.d(TAG, "filename $filename")
        }
        completeVideo(video_method, uri)
        broadcastVideo(video_method, uri, filename)

        // also need to restart subtitles file
        if (subtitleVideoTimerTask != null) {
            subtitleVideoTimerTask!!.cancel()
            subtitleVideoTimerTask = null

            // No need to check if option for subtitles is set, if we were already saving subtitles.
            // Assume that video_method is unchanged between old and new video file when restarting.
            startVideoSubtitlesTask(video_method)
        }
    }

    /** Called when we've finished recording to a video file, to do any necessary cleanup for the
     * file.
     */
    fun completeVideo(video_method: VideoMethod?, uri: Uri?) {
        if (MyDebug.LOG) Logger.d(TAG, "completeVideo")
        if (video_method == VideoMethod.MEDIASTORE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                uri?.let { main_activity.contentResolver.update(uri, contentValues, null, null) }
            }
        }
    }

    fun broadcastVideo(video_method: VideoMethod?, uri: Uri?, filename: String?): Boolean {
        if (MyDebug.LOG) {
            Logger.d(TAG, "broadcastVideo")
            Logger.d(TAG, "video_method $video_method")
            Logger.d(TAG, "uri $uri")
            Logger.d(TAG, "filename $filename")
        }
        var done = false
        // clear just in case we're unable to update this - don't want an out of date cached uri
        storageUtils.clearLastMediaScanned()
        if (video_method == VideoMethod.MEDIASTORE) {
            // no need to broadcast when using mediastore

            if (uri != null) {
                // in theory this is pointless, as announceUri no longer does anything on Android 7+,
                // and mediastore method is only used on Android 10+, but keep this just in case
                // announceUri does something in future
                storageUtils.announceUri(uri, false, true)

                // we also want to save the uri - we can use the media uri directly, rather than having to scan it
                storageUtils.setLastMediaScanned(uri, false, false, null)

                done = true
            }
        } else if (video_method == VideoMethod.FILE) {
            if (filename != null) {
                val file = File(filename)
                storageUtils.broadcastFile(file, false, true, true, false, null)
                done = true
            }
        } else {
            if (uri != null) {
                // see note in onPictureTaken() for where we call broadcastFile for SAF photos
                storageUtils.broadcastUri(uri, false, true, true, false, false)
                done = true
            }
        }
        if (done) {
            test_n_videos_scanned++
            if (MyDebug.LOG) Logger.d(
                TAG, "test_n_videos_scanned is now: $test_n_videos_scanned"
            )
        }

        if (video_method == VideoMethod.MEDIASTORE && this.isVideoCaptureIntent) {
            finishVideoIntent(uri)
        }
        return done
    }

    /** For use when called from a video capture intent. This returns the supplied uri to the
     * caller, and finishes the activity.
     */
    fun finishVideoIntent(uri: Uri?) {
        if (MyDebug.LOG) Logger.d(TAG, "finishVideoIntent:" + uri)
        val output = Intent()
        output.setData(uri)
        main_activity.setResult(Activity.RESULT_OK, output)
        main_activity.finish()
    }

    override fun deleteUnusedVideo(video_method: VideoMethod?, uri: Uri?, filename: String?) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "deleteUnusedVideo")
            Logger.d(TAG, "video_method $video_method")
            Logger.d(TAG, "uri $uri")
            Logger.d(TAG, "filename $filename")
        }
        if (video_method == VideoMethod.FILE) {
            trashImage(LastImagesType.FILE, uri, filename, false)
        } else if (video_method == VideoMethod.SAF) {
            trashImage(LastImagesType.SAF, uri, filename, false)
        } else if (video_method == VideoMethod.MEDIASTORE) {
            trashImage(LastImagesType.MEDIASTORE, uri, filename, false)
        }
        // else can't delete Uri
    }

    override fun onVideoInfo(what: Int, extra: Int) {
        // we don't show a toast for MEDIA_RECORDER_INFO_MAX_DURATION_REACHED - conflicts with "n repeats to go" toast from Preview
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && what == MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED) {
            if (MyDebug.LOG) Logger.d(TAG, "next output file started")
            val message_id = R.string.video_max_filesize
            main_activity.preview!!.showToast(null, message_id, true)
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
            if (MyDebug.LOG) Logger.d(TAG, "max filesize reached")
            val message_id = R.string.video_max_filesize
            main_activity.preview!!.showToast(null, message_id, true)
        }
        // in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
        // fixed in 1.25; also was correct for 1.23 and earlier
        val debug_value = "info_" + what + "_" + extra
        val editor = sharedPreferences.edit()
        editor.putString("last_video_error", debug_value)
        editor.apply()
    }

    override fun onFailedStartPreview() {
        main_activity.preview!!.showToast(null, R.string.failed_to_start_camera_preview)
        main_activity.enablePausePreviewOnBackPressedCallback(false) // reenable standard back button behaviour (in case preview was paused due to option to pause preview after taking a photo)
    }

    override fun onCameraError() {
        main_activity.preview!!.showToast(null, R.string.camera_error)
    }

    override fun onPhotoError() {
        main_activity.preview!!.showToast(null, R.string.failed_to_take_picture)
    }

    override fun onVideoError(what: Int, extra: Int) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "onVideoError: $what extra: $extra")
        }
        var message_id = R.string.video_error_unknown
        if (what == MediaRecorder.MEDIA_ERROR_SERVER_DIED) {
            if (MyDebug.LOG) Logger.d(TAG, "error: server died")
            message_id = R.string.video_error_server_died
        }
        main_activity.preview!!.showToast(null, message_id)
        // in versions 1.24 and 1.24, there was a bug where we had "info_" for onVideoError and "error_" for onVideoInfo!
        // fixed in 1.25; also was correct for 1.23 and earlier
        val debug_value = "error_" + what + "_" + extra
        sharedPreferences.edit {
            putString("last_video_error", debug_value)
        }
    }

    override fun onVideoRecordStartError(profile: VideoProfile?) {
        if (MyDebug.LOG) Logger.d(TAG, "onVideoRecordStartError")
        val error_message: String?
        val features = main_activity.preview!!.getErrorFeatures(profile)
        if (features.isNotEmpty()) {
            error_message = getContext().getResources()
                .getString(R.string.sorry) + ", " + features + " " + context.resources.getString(R.string.not_supported)
        } else {
            error_message = getContext().getResources().getString(R.string.failed_to_record_video)
        }
        main_activity.preview!!.showToast(null, error_message)
    }

    override fun onVideoRecordStopError(profile: VideoProfile?) {
        if (MyDebug.LOG) Logger.d(TAG, "onVideoRecordStopError")
        //main_activity.getPreview().showToast(null, R.string.failed_to_record_video);
        val features = main_activity.preview!!.getErrorFeatures(profile)
        var error_message = getContext().getResources().getString(R.string.video_may_be_corrupted)
        if (features.isNotEmpty()) {
            error_message += ", $features " + getContext().getResources()
                .getString(R.string.not_supported)
        }
        main_activity.preview!!.showToast(null, error_message)
    }

    override fun onFailedReconnectError() {
        main_activity.preview!!.showToast(null, R.string.failed_to_reconnect_camera)
    }

    override fun onFailedCreateVideoFileError() {
        if (MyDebug.LOG) Logger.d(TAG, "onFailedCreateVideoFileError")
        main_activity.preview!!.showToast(null, R.string.failed_to_save_video)
    }

    override fun hasPausedPreview(paused: Boolean) {
        if (MyDebug.LOG) Logger.d(TAG, "hasPausedPreview: $paused")
        if (paused) {
            main_activity.enablePausePreviewOnBackPressedCallback(true) // so that pressing back button instead unpauses the preview
        } else {
            this.clearLastImages()
            main_activity.enablePausePreviewOnBackPressedCallback(false) // reenable standard back button behaviour
        }
    }

    override fun cameraInOperation(in_operation: Boolean, is_video: Boolean) {
        if (MyDebug.LOG) Logger.d(TAG, "cameraInOperation: $in_operation")
        if (!in_operation && used_front_screen_flash) {
            main_activity.setBrightnessForCamera(false) // ensure screen brightness matches user preference, after using front screen flash
            used_front_screen_flash = false
        }
        drawPreview!!.cameraInOperation(in_operation)
        main_activity.mainUI!!.showGUI(!in_operation, is_video)
    }

    override fun turnFrontScreenFlashOn() {
        if (MyDebug.LOG) Logger.d(TAG, "turnFrontScreenFlashOn")
        used_front_screen_flash = true
        main_activity.setBrightnessForCamera(true) // ensure we have max screen brightness, even if user preference not set for max brightness
        drawPreview!!.turnFrontScreenFlashOn()
    }

    override fun onCaptureStarted() {
        if (MyDebug.LOG) Logger.d(TAG, "onCaptureStarted")
        n_capture_images = 0
        n_capture_images_raw = 0
        drawPreview!!.onCaptureStarted()

        if (this.photoMode == PhotoMode.X_Night) {
            main_activity.preview!!.showToast(
                null, R.string.preference_nr_mode_low_light_message, true
            )
        }
    }

    override fun onPictureCompleted() {
        if (MyDebug.LOG) Logger.d(TAG, "onPictureCompleted")

        // clear any toasts displayed during progress (e.g., preference_nr_mode_low_light_message, or onExtensionProgress())
        main_activity.preview!!.clearActiveFakeToast()

        var photo_mode = this.photoMode
        if (main_activity.preview!!.isVideo) {
            if (MyDebug.LOG) Logger.d(TAG, "snapshot mode")
            // must be in photo snapshot while recording video mode, only support standard photo mode
            photo_mode = PhotoMode.Standard
        }
        if (photo_mode == PhotoMode.NoiseReduction) {
            val image_capture_intent = this.isImageCaptureIntent
            val do_in_background = saveInBackground(image_capture_intent)
            imageSaver!!.finishImageBatch(do_in_background)
        } else if (photo_mode == PhotoMode.Panorama && gyroSensor.isRecording) {
            if (panorama_pic_accepted) {
                if (MyDebug.LOG) Logger.d(TAG, "set next panorama point")
                this.setNextPanoramaPoint(false)
            } else {
                if (MyDebug.LOG) Logger.d(TAG, "panorama pic wasn't accepted")
                this.setNextPanoramaPoint(true)
            }
        } else if (photo_mode == PhotoMode.FocusBracketing) {
            if (MyDebug.LOG) Logger.d(TAG, "focus bracketing completed")
            if (getShutterSoundPref()) {
                if (MyDebug.LOG) Logger.d(TAG, "play completion sound")
                val player = MediaPlayer.create(context, Settings.System.DEFAULT_NOTIFICATION_URI)
                player?.start()
            }
        }

        // call this, so that if pause-preview-after-taking-photo option is set, we remove the "taking photo" border indicator straight away
        // also even for normal (not pausing) behaviour, good to remove the border asap
        drawPreview!!.cameraInOperation(false)
    }

    override fun onExtensionProgress(progress: Int) {
        var message = ""
        if (this.photoMode == PhotoMode.X_Night) {
            message = getContext().getResources()
                .getString(R.string.preference_nr_mode_low_light_message) + "\n"
        }
        main_activity.preview!!.showToast(null, "$message$progress%", true)
    }

    override fun cameraClosed() {
        if (MyDebug.LOG) Logger.d(TAG, "cameraClosed")
        this.stopPanorama(true)
        drawPreview!!.clearContinuousFocusMove()
    }

    fun updateThumbnail(thumbnail: Bitmap?, is_video: Boolean) {
        if (MyDebug.LOG) Logger.d(TAG, "updateThumbnail")
        main_activity.updateGalleryIcon(thumbnail)
        drawPreview!!.updateThumbnail(thumbnail, is_video, true)
        if (!is_video && this.getPausePreviewPref()) {
            drawPreview.showLastImage()
        }
    }

    override fun timerBeep(remaining_time: Long) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "timerBeep()")
            Logger.d(TAG, "remaining_time: $remaining_time")
        }
        if (sharedPreferences.getBoolean(PreferenceKeys.TimerBeepPreferenceKey, true)) {
            if (MyDebug.LOG) Logger.d(TAG, "play beep!")
            val is_last = remaining_time <= 1000
            main_activity.getSoundPoolManager()
                .playSound(if (is_last) R.raw.mybeep_hi else R.raw.mybeep)
        }
        if (sharedPreferences.getBoolean(PreferenceKeys.TimerSpeakPreferenceKey, false)) {
            if (MyDebug.LOG) Logger.d(TAG, "speak countdown!")
            val remaining_time_s = (remaining_time / 1000).toInt()
            if (remaining_time_s <= 60) main_activity.speak(remaining_time_s.toString())
        }
    }

    override fun multitouchZoom(new_zoom: Int) {
        main_activity.mainUI!!.setSeekbarZoom(new_zoom)
    }

    override fun requestTakePhoto() {
        if (MyDebug.LOG) Logger.d(TAG, "requestTakePhoto")
        main_activity.takePicture(false)
    }

    /** Switch to the first available camera that is front or back facing as desired.
     * @param front_facing Whether to switch to a front or back facing camera.
     */
    fun switchToCamera(front_facing: Boolean) {
        if (MyDebug.LOG) Logger.d(TAG, "switchToCamera: $front_facing")
        val n_cameras = main_activity.preview!!.cameraControllerManager.getNumberOfCameras()
        val want_facing = if (front_facing) Facing.FACING_FRONT else Facing.FACING_BACK
        for (i in 0..<n_cameras) {
            if (main_activity.preview!!.cameraControllerManager.getFacing(i) == want_facing) {
                if (MyDebug.LOG) Logger.d(TAG, "found desired camera: $i")
                this.setCameraIdPref(i, null)
                break
            }
        }
    }

    /* Note that the cameraId is still valid if this returns false, it just means that a cameraId hasn't be explicitly set yet.
     */
    fun hasSetCameraId(): Boolean {
        return has_set_cameraId
    }

    override fun setCameraIdPref(cameraId: Int, cameraIdSPhysical: String?) {
        this.has_set_cameraId = true
        this.cameraId = cameraId
        this.cameraIdSPhysical = cameraIdSPhysical
    }

    override fun setFlashPref(flash_value: String?) {
        sharedPreferences.edit {
            putString(PreferenceKeys.getFlashPreferenceKey(cameraId), flash_value)
        }
    }

    override fun setFocusPref(focus_value: String?, is_video: Boolean) {
        sharedPreferences.edit {
            putString(PreferenceKeys.getFocusPreferenceKey(cameraId, is_video), focus_value)
        }
        // focus may be updated by preview (e.g., when switching to/from video mode)
        main_activity.setManualFocusSeekBarVisibility(false)
    }

    override fun setFocusModePref(focus_value: String?) {
        sharedPreferences.edit {
            putString(FocusModeKey, focus_value)
        }
    }

    override fun getFocusModePref(): String? {
        return sharedPreferences.getString(FocusModeKey, "")
    }

    override fun setVideoPref(is_video: Boolean) {
        sharedPreferences.edit {
            putBoolean(PreferenceKeys.IsVideoPreferenceKey, is_video)
        }
    }

    override fun setSceneModePref(scene_mode: String?) {
        sharedPreferences.edit {
            putString(PreferenceKeys.SceneModePreferenceKey, scene_mode)
        }
    }

    override fun clearSceneModePref() {
        sharedPreferences.edit {
            remove(PreferenceKeys.SceneModePreferenceKey)
        }
    }

    override fun setColorEffectPref(color_effect: String?) {
        sharedPreferences.edit {
            putString(PreferenceKeys.ColorEffectPreferenceKey, color_effect)
        }
    }

    override fun clearColorEffectPref() {
        sharedPreferences.edit {
            remove(PreferenceKeys.ColorEffectPreferenceKey)
        }
    }

    override fun setWhiteBalancePref(white_balance: String?) {
        sharedPreferences.edit {
            putString(PreferenceKeys.WhiteBalancePreferenceKey, white_balance)
        }
    }

    override fun clearWhiteBalancePref() {
        sharedPreferences.edit {
            remove(PreferenceKeys.WhiteBalancePreferenceKey)
        }
    }

    override fun setWhiteBalanceTemperaturePref(white_balance_temperature: Int) {
        sharedPreferences.edit {
            putInt(
                PreferenceKeys.WhiteBalanceTemperaturePreferenceKey, white_balance_temperature
            )
        }
    }

    override fun setISOPref(iso: String?) {
        sharedPreferences.edit {
            putString(PreferenceKeys.ISOPreferenceKey, iso)
        }
    }

    override fun clearISOPref() {
        sharedPreferences.edit {
            remove(PreferenceKeys.ISOPreferenceKey)
        }
    }

    override fun setExposureCompensationPref(exposure: Int) {
        sharedPreferences.edit {
            putString(PreferenceKeys.ExposurePreferenceKey, exposure.toString())
        }
    }

    override fun clearExposureCompensationPref() {
        sharedPreferences.edit {
            remove(PreferenceKeys.ExposurePreferenceKey)
        }
    }

    override fun setCameraResolutionPref(width: Int, height: Int) {
        if (this.photoMode == PhotoMode.Panorama) {
            // in Panorama mode we'll have set a different resolution to the user setting, so don't want that to then be saved!
            return
        }
        val resolution_value = "$width $height"
        if (MyDebug.LOG) {
            Logger.d(TAG, "save new resolution_value: $resolution_value")
        }
        sharedPreferences.edit {
            putString(
                PreferenceKeys.getResolutionPreferenceKey(cameraId, cameraIdSPhysical),
                resolution_value
            )
        }
    }

    override fun setVideoQualityPref(video_quality: String?) {
        sharedPreferences.edit {
            putString(
                PreferenceKeys.getVideoQualityPreferenceKey(
                    cameraId, cameraIdSPhysical, fpsIsHighSpeed()
                ), video_quality
            )
        }
    }

    override fun setZoomPref(zoom: Int) {
        if (MyDebug.LOG) Logger.d(TAG, "setZoomPref: " + zoom)
        this.zoom_factor = zoom
    }

    override fun requestCameraPermission() {
        if (MyDebug.LOG) Logger.d(TAG, "requestCameraPermission")
        main_activity.getPermissionHandler().requestCameraPermission()
    }

    override fun needsStoragePermission(): Boolean {
        if (MyDebug.LOG) Logger.d(TAG, "needsStoragePermission")
        if (useScopedStorage()) return false // no longer need storage permission with scoped storage - and shouldn't request it either

        return true
    }

    override fun requestStoragePermission() {
        if (MyDebug.LOG) Logger.d(TAG, "requestStoragePermission")
        main_activity.getPermissionHandler().requestStoragePermission()
    }

    override fun requestRecordAudioPermission() {
        if (MyDebug.LOG) Logger.d(TAG, "requestRecordAudioPermission")
        main_activity.getPermissionHandler().requestRecordAudioPermission()
    }

    override fun setExposureTimePref(exposure_time: Long) {
        val editor = sharedPreferences.edit()
        editor.putLong(PreferenceKeys.ExposureTimePreferenceKey, exposure_time)
        editor.apply()
    }

    override fun clearExposureTimePref() {
        val editor = sharedPreferences.edit()
        editor.remove(PreferenceKeys.ExposureTimePreferenceKey)
        editor.apply()
    }

    override fun setFocusDistancePref(focus_distance: Float, is_target_distance: Boolean) {
        sharedPreferences.edit {
            putFloat(
                if (is_target_distance) PreferenceKeys.FocusBracketingTargetDistancePreferenceKey else PreferenceKeys.FocusDistancePreferenceKey,
                focus_distance
            )
        }
    }

    private val stampFontColor: Int
        get() {
            val color: String =
                sharedPreferences.getString(PreferenceKeys.StampFontColorPreferenceKey, "#ffffff")!!
            return Color.parseColor(color)
        }

    /** Should be called to reset parameters which aren't expected to be saved (e.g., resetting zoom when application is paused,
     * when switching between photo/video modes, or switching cameras).
     */
    fun reset(switchedCamera: Boolean) {
        if (switchedCamera) {
            // aperture is reset when switching camera, but not when application is paused or switching between photo/video etc
            this.aperture = aperture_default
        }
        this.zoom_factor = -1
    }

    override fun onDrawPreview(canvas: Canvas?) {
        if (!main_activity.isCameraInBackground) {
            // no point drawing when in background (e.g., settings open)
            canvas?.let { drawPreview?.onDrawPreview(canvas) }
        }
    }

    enum class Alignment {
        ALIGNMENT_TOP, ALIGNMENT_CENTRE, ALIGNMENT_BOTTOM
    }

    enum class Shadow {
        SHADOW_NONE, SHADOW_OUTLINE, SHADOW_BACKGROUND
    }

    @JvmOverloads
    fun drawTextWithBackground(
        canvas: Canvas,
        paint: Paint,
        text: String,
        foreground: Int,
        background: Int,
        location_x: Int,
        location_y: Int,
        alignment_y: Alignment? = Alignment.ALIGNMENT_BOTTOM,
        shadow: Shadow? = Shadow.SHADOW_OUTLINE
    ): Int {
        return drawTextWithBackground(
            canvas,
            paint,
            text,
            foreground,
            background,
            location_x,
            location_y,
            alignment_y,
            null,
            shadow,
            null
        )
    }

    fun drawTextWithBackground(
        canvas: Canvas,
        paint: Paint,
        text: String,
        foreground: Int,
        background: Int,
        location_x: Int,
        location_y: Int,
        alignment_y: Alignment?,
        ybounds_text: String?,
        shadow: Shadow?,
        bounds: Rect?
    ): Int {
        var locationY = location_y
        val scale =
            context.resources.displayMetrics.scaledDensity // important to use scaledDensity for scaling font sizes
        paint.style = Paint.Style.FILL
        paint.color = background
        paint.alpha = 64

        val typeface = ResourcesCompat.getFont(context, R.font.vcr_osd_mono)
        paint.typeface = typeface

        if (bounds != null) {
            text_bounds.set(bounds)
        } else {
            var altHeight = 0
            if (ybounds_text != null) {
                paint.getTextBounds(ybounds_text, 0, ybounds_text.length, text_bounds)
                altHeight = text_bounds.bottom - text_bounds.top
            }
            paint.getTextBounds(text, 0, text.length, text_bounds)
            if (ybounds_text != null) {
                text_bounds.bottom = text_bounds.top + altHeight
            }
        }

        val padding = (2 * scale + 0.5f).toInt() // convert dps to pixels
        if (paint.textAlign == Paint.Align.RIGHT || paint.textAlign == Paint.Align.CENTER) {
            var width =
                paint.measureText(text) // n.b., need to use measureText rather than getTextBounds here/
            Logger.d(TAG, "width: $width");
            if (paint.textAlign == Paint.Align.CENTER) width /= 2.0f
            text_bounds.left = (text_bounds.left - width).toInt()
            text_bounds.right = (text_bounds.right - width).toInt()
        }
        text_bounds.left += location_x - padding
        text_bounds.right += location_x + padding

        // unclear why we need the offset of -1, but need this to align properly on Galaxy Nexus at least
        val topYDiff = -text_bounds.top + padding - 1
        when (alignment_y) {
            Alignment.ALIGNMENT_TOP -> {
                val height = text_bounds.bottom - text_bounds.top + 2 * padding
                text_bounds.top = locationY - 1
                text_bounds.bottom = text_bounds.top + height
                locationY += topYDiff
            }

            Alignment.ALIGNMENT_CENTRE -> {
                val height = text_bounds.bottom - text_bounds.top + 2 * padding
                text_bounds.top =
                    (0.5 * ((locationY - 1) + (text_bounds.top + locationY - padding))).toInt() // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
                text_bounds.bottom = text_bounds.top + height
                locationY += (0.5 * topYDiff).toInt() // average of ALIGNMENT_TOP and ALIGNMENT_BOTTOM
            }

            else -> {
                text_bounds.top += locationY - padding
                text_bounds.bottom += locationY + padding
            }
        }

        if (shadow == Shadow.SHADOW_BACKGROUND) {
            paint.color = background
            paint.alpha = 64
            canvas.drawRect(text_bounds, paint)
            paint.alpha = 255
        }
        paint.color = foreground
        if (shadow == Shadow.SHADOW_OUTLINE) {
            var shadowRadius = (1.0f * scale + 0.5f) // convert pt to pixels
            shadowRadius = max(shadowRadius, 1.0f)
            paint.setShadowLayer(shadowRadius, 0.0f, 0.0f, background)
        }
        canvas.drawText(text, location_x.toFloat(), locationY.toFloat(), paint)
        if (shadow == Shadow.SHADOW_OUTLINE) {
            paint.clearShadowLayer() // set back to default
        }

        return text_bounds.bottom - text_bounds.top
    }

    private fun saveInBackground(image_capture_intent: Boolean): Boolean {
        var do_in_background = true/*if( !sharedPreferences.getBoolean(PreferenceKeys.BackgroundPhotoSavingPreferenceKey, true) )
			do_in_background = false;
		else*/
        if (image_capture_intent) do_in_background = false
        else if (getPausePreviewPref()) do_in_background = false
        return do_in_background
    }

    val isImageCaptureIntent: Boolean
        get() {
            var image_capture_intent = false
            val action = main_activity.intent.action
            if (MediaStore.ACTION_IMAGE_CAPTURE == action || MediaStore.ACTION_IMAGE_CAPTURE_SECURE == action) {
                image_capture_intent = true
            }
            return image_capture_intent
        }

    val isVideoCaptureIntent: Boolean
        get() {
            var video_capture_intent = false
            val action = main_activity.intent.action
            if (MediaStore.ACTION_VIDEO_CAPTURE == action) {
                video_capture_intent = true
            }
            return video_capture_intent
        }

    /** Whether the photos will be part of a burst, even if we're receiving via the non-burst callbacks.
     */
    private fun forceSuffix(photo_mode: PhotoMode?): Boolean {
        // focus bracketing and fast burst shots come is as separate requests, so we need to make sure we get the filename suffixes right
        return photo_mode == PhotoMode.FocusBracketing || photo_mode == PhotoMode.FastBurst || (main_activity.preview!!.getCameraController() != null && main_activity.preview!!.getCameraController()
            .isCapturingBurst())
    }

    /** Saves the supplied image(s)
     * @param save_expo If the photo mode is one where multiple images are saved to a single
     * resultant image, this indicates if all the base images should also be saved
     * as separate images.
     * @param images The set of images.
     * @param current_date The current date/time stamp for the images.
     * @return Whether saving was successful.
     */
    private fun saveImage(
        save_expo: Boolean, images: MutableList<ByteArray>?, current_date: Date?
    ): Boolean {
        if (MyDebug.LOG) Logger.d(TAG, "saveImage")

        System.gc()

        val image_capture_intent = this.isImageCaptureIntent
        var image_capture_intent_uri: Uri? = null
        if (image_capture_intent) {
            if (MyDebug.LOG) Logger.d(TAG, "from image capture intent")
            val myExtras = main_activity.intent.extras
            if (myExtras != null) {
                image_capture_intent_uri = myExtras.getParcelable<Uri?>(MediaStore.EXTRA_OUTPUT)
                if (MyDebug.LOG) Logger.d(TAG, "save to: " + image_capture_intent_uri)
            }
        }

        val using_camera2 = main_activity.preview!!.usingCamera2API()
        val using_camera_extensions = isCameraExtensionPref()
        val image_format = this.imageFormatPref
        val store_ypr = sharedPreferences.getBoolean(
            PreferenceKeys.AddYPRToComments, false
        ) && main_activity.preview!!.hasLevelAngle() && main_activity.preview!!.hasPitchAngle() && main_activity.preview!!.hasGeoDirection()
        if (MyDebug.LOG) {
            Logger.d(TAG, "store_ypr: $store_ypr")
            Logger.d(TAG, "has level angle: " + main_activity.preview!!.hasLevelAngle())
            Logger.d(TAG, "has pitch angle: " + main_activity.preview!!.hasPitchAngle())
            Logger.d(
                TAG, "has geo direction: " + main_activity.preview!!.hasGeoDirection()
            )
        }
        val image_quality = this.saveImageQualityPref
        if (MyDebug.LOG) Logger.d(TAG, "image_quality: $image_quality")
        val do_auto_stabilise =
            this.autoStabilisePref && main_activity.preview!!.hasLevelAngleStable()
        var level_angle =
            if (main_activity.preview!!.hasLevelAngle()) main_activity.preview!!.getLevelAngle() else 0.0
        val pitch_angle =
            if (main_activity.preview!!.hasPitchAngle()) main_activity.preview!!.getPitchAngle() else 0.0
        if (do_auto_stabilise && main_activity.test_have_angle) level_angle =
            main_activity.test_angle.toDouble()
        if (do_auto_stabilise && main_activity.test_low_memory) level_angle = 45.0
        // I have received crashes where camera_controller was null - could perhaps happen if this thread was running just as the camera is closing?
        val is_front_facing =
            main_activity.preview?.cameraController != null && (main_activity.preview!!.cameraController.getFacing() == Facing.FACING_FRONT)
        val mirror = is_front_facing && sharedPreferences.getString(
            PreferenceKeys.FrontCameraMirrorKey, "preference_front_camera_mirror_no"
        ) == "preference_front_camera_mirror_photo"
        val preference_stamp = this.stampPref
        val preference_textstamp = this.textStampPref
        val font_size = this.textStampFontSizePref
        val color = this.stampFontColor
        val pref_style: String = sharedPreferences.getString(
            PreferenceKeys.StampStyleKey, "preference_stamp_style_shadowed"
        )!!
        val preference_stamp_dateformat = this.stampDateFormatPref
        val preference_stamp_timeformat = this.stampTimeFormatPref
        val preference_stamp_gpsformat = this.stampGPSFormatPref
        //String preference_stamp_geo_address = this.getStampGeoAddressPref();
        val preference_units_distance = this.unitsDistancePref
        val panorama_crop = sharedPreferences.getString(
            PreferenceKeys.PanoramaCropPreferenceKey, "preference_panorama_crop_on"
        ) == "preference_panorama_crop_on"
        val remove_device_exif = this.removeDeviceExifPref
        val store_location = geotaggingPref && location != null
        val location = if (store_location) location else null
        val store_geo_direction = main_activity.preview!!.hasGeoDirection() && this.geodirectionPref
        val geo_direction =
            if (main_activity.preview!!.hasGeoDirection()) main_activity.preview!!.geoDirection else 0.0
        val custom_tag_artist: String =
            sharedPreferences.getString(PreferenceKeys.ExifArtistPreferenceKey, "")!!
        val custom_tag_copyright: String =
            sharedPreferences.getString(PreferenceKeys.ExifCopyrightPreferenceKey, "")!!

        var iso = 800 // default value if we can't get ISO
        var exposure_time = 1000000000L / 30 // default value if we can't get shutter speed
        var zoom_factor = 1.0f
        if (main_activity.preview!!.cameraController != null) {
            if (main_activity.preview!!.cameraController.captureResultHasIso()) {
                iso = main_activity.preview!!.cameraController.captureResultIso()
                if (MyDebug.LOG) Logger.d(TAG, "iso: $iso")
            }
            if (main_activity.preview!!.cameraController.captureResultHasExposureTime()) {
                exposure_time = main_activity.preview!!.cameraController.captureResultExposureTime()
                if (MyDebug.LOG) Logger.d(TAG, "exposure_time: $exposure_time")
            }

            zoom_factor = main_activity.preview!!.getZoomRatio()
        }

        val has_thumbnail_animation = this.thumbnailAnimationPref

        val do_in_background = saveInBackground(image_capture_intent)

        val ghost_image_pref: String = sharedPreferences.getString(
            PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off"
        )!!

        var sample_factor = 1
        if (!this.getPausePreviewPref() && ghost_image_pref != "preference_ghost_image_last") {
            // if pausing the preview, we use the thumbnail also for the preview, so don't downsample
            // similarly for ghosting last image
            // otherwise, we can downsample by 4 to increase performance, without noticeable loss in visual quality (even for the thumbnail animation)
            sample_factor *= 4
            if (!has_thumbnail_animation) {
                // can use even lower resolution if we don't have the thumbnail animation
                sample_factor *= 4
            }
        }
        if (MyDebug.LOG) Logger.d(TAG, "sample_factor: $sample_factor")

        val success: Boolean
        var photo_mode = this.photoMode
        if (main_activity.preview!!.isVideo) {
            if (MyDebug.LOG) Logger.d(TAG, "snapshot mode")
            // must be in photo snapshot while recording video mode, only support standard photo mode
            photo_mode = PhotoMode.Standard
        }

        var preshot_bitmaps: MutableList<Bitmap?>? = null
        if (!image_capture_intent && n_capture_images <= 1 && getPreShotsPref(photo_mode)) {
            // n.b., n_capture_images == 0 if using onBurstPictureTaken(), e.g., for photo mode HDR
            val ring_buffer = main_activity.preview!!.preShotsRingBuffer

            if (ring_buffer.nBitmaps >= 3) {
                if (MyDebug.LOG) Logger.d(TAG, "save pre-shots")

                preshot_bitmaps = ArrayList<Bitmap?>()
                while (ring_buffer.hasBitmaps()) {
                    val bitmap = ring_buffer.get()
                    preshot_bitmaps.add(bitmap)
                }
            }
        }

        if (!main_activity.is_test && photo_mode == PhotoMode.Panorama && gyroSensor.isRecording() && gyroSensor.hasTarget() && !gyroSensor.isTargetAchieved()) {
            if (MyDebug.LOG) Logger.d(
                TAG, "ignore panorama image as target no longer achieved!"
            )
            // n.b., gyroSensor.hasTarget() will be false if this is the first picture in the panorama series
            panorama_pic_accepted = false
            success = true // still treat as success
        } else if (photo_mode == PhotoMode.NoiseReduction || photo_mode == PhotoMode.Panorama) {
            val first_image: Boolean
            if (photo_mode == PhotoMode.Panorama) {
                panorama_pic_accepted = true
                first_image = n_panorama_pics == 0
            } else first_image = n_capture_images == 1
            if (first_image) {
                var save_base = SaveBase.SAVEBASE_NONE
                if (photo_mode == PhotoMode.NoiseReduction) {
                    val save_base_preference: String = sharedPreferences.getString(
                        PreferenceKeys.NRSaveExpoPreferenceKey, "preference_nr_save_no"
                    )!!
                    when (save_base_preference) {
                        "preference_nr_save_single" -> save_base = SaveBase.SAVEBASE_FIRST
                        "preference_nr_save_all" -> save_base = SaveBase.SAVEBASE_ALL
                    }
                } else if (photo_mode == PhotoMode.Panorama) {
                    val save_base_preference: String = sharedPreferences.getString(
                        PreferenceKeys.PanoramaSaveExpoPreferenceKey, "preference_panorama_save_no"
                    )!!
                    when (save_base_preference) {
                        "preference_panorama_save_all" -> save_base = SaveBase.SAVEBASE_ALL
                        "preference_panorama_save_all_plus_debug" -> save_base =
                            SaveBase.SAVEBASE_ALL_PLUS_DEBUG
                    }
                }

                imageSaver!!.startImageBatch(
                    true,
                    if (photo_mode == PhotoMode.NoiseReduction) ProcessType.AVERAGE else ProcessType.PANORAMA,
                    preshot_bitmaps,
                    save_base,
                    image_capture_intent,
                    image_capture_intent_uri,
                    using_camera2,
                    using_camera_extensions,
                    image_format,
                    image_quality,
                    do_auto_stabilise,
                    level_angle,
                    photo_mode == PhotoMode.Panorama,
                    is_front_facing,
                    mirror,
                    current_date,
                    iso,
                    exposure_time,
                    zoom_factor,
                    preference_stamp,
                    preference_textstamp,
                    font_size,
                    color,
                    pref_style,
                    preference_stamp_dateformat,
                    preference_stamp_timeformat,
                    preference_stamp_gpsformat,  //preference_stamp_geo_address,
                    preference_units_distance,
                    panorama_crop,
                    remove_device_exif,
                    store_location,
                    location,
                    store_geo_direction,
                    geo_direction,
                    pitch_angle,
                    store_ypr,
                    custom_tag_artist,
                    custom_tag_copyright,
                    sample_factor
                )

                if (photo_mode == PhotoMode.Panorama) {
                    imageSaver.imageBatchRequest.camera_view_angle_x =
                        main_activity.preview!!.getViewAngleX(false)
                    imageSaver.imageBatchRequest.camera_view_angle_y =
                        main_activity.preview!!.getViewAngleY(false)
                }
            }

            var gyro_rotation_matrix: FloatArray? = null
            if (photo_mode == PhotoMode.Panorama) {
                gyro_rotation_matrix = FloatArray(9)
                this.gyroSensor.getRotationMatrix(gyro_rotation_matrix)
            }

            images?.let { imageSaver!!.addImageBatch(images[0], gyro_rotation_matrix) }
            success = true
        } else {
            val processType: ProcessType?
            if (photo_mode == PhotoMode.DRO || photo_mode == PhotoMode.HDR) processType =
                ProcessType.HDR
            else if (photo_mode == PhotoMode.X_Night) processType = ProcessType.X_NIGHT
            else processType = ProcessType.NORMAL
            val force_suffix = forceSuffix(photo_mode)

            var preference_hdr_tonemapping_algorithm = HDRProcessor.default_tonemapping_algorithm_c
            run {
                val tonemapping_algorithm_pref: String = sharedPreferences.getString(
                    PreferenceKeys.HDRTonemappingPreferenceKey, "preference_hdr_tonemapping_default"
                )!!
                when (tonemapping_algorithm_pref) {
                    "preference_hdr_tonemapping_clamp" -> preference_hdr_tonemapping_algorithm =
                        TonemappingAlgorithm.TONEMAPALGORITHM_CLAMP

                    "preference_hdr_tonemapping_exponential" -> preference_hdr_tonemapping_algorithm =
                        TonemappingAlgorithm.TONEMAPALGORITHM_EXPONENTIAL

                    "preference_hdr_tonemapping_default" -> preference_hdr_tonemapping_algorithm =
                        HDRProcessor.default_tonemapping_algorithm_c

                    "preference_hdr_tonemapping_aces" -> preference_hdr_tonemapping_algorithm =
                        TonemappingAlgorithm.TONEMAPALGORITHM_ACES

                    else -> Log.e(
                        TAG, "unhandled case for tone mapping: " + tonemapping_algorithm_pref
                    )
                }
            }
            val preference_hdr_contrast_enhancement: String = sharedPreferences.getString(
                PreferenceKeys.HDRContrastEnhancementPreferenceKey,
                "preference_hdr_contrast_enhancement_smart"
            )!!

            success = imageSaver!!.saveImageJpeg(
                do_in_background,
                processType,
                force_suffix,  // N.B., n_capture_images will be 1 for first image, not 0, so subtract 1 so we start off from _0.
                // (It wouldn't be a huge problem if we did start from _1, but it would be inconsistent with the naming
                // of images where images.size() > 1 (e.g., expo bracketing mode) where we also start from _0.)
                if (force_suffix) (n_capture_images - 1) else 0,
                save_expo,
                images,
                preshot_bitmaps,
                image_capture_intent,
                image_capture_intent_uri,
                using_camera2,
                using_camera_extensions,
                image_format,
                image_quality,
                do_auto_stabilise,
                level_angle,
                is_front_facing,
                mirror,
                current_date,
                preference_hdr_tonemapping_algorithm,
                preference_hdr_contrast_enhancement,
                iso,
                exposure_time,
                zoom_factor,
                preference_stamp,
                preference_textstamp,
                font_size,
                color,
                pref_style,
                preference_stamp_dateformat,
                preference_stamp_timeformat,
                preference_stamp_gpsformat,  //preference_stamp_geo_address,
                preference_units_distance,
                false,  // panorama doesn't use this codepath
                remove_device_exif,
                store_location,
                location,
                store_geo_direction,
                geo_direction,
                pitch_angle,
                store_ypr,
                custom_tag_artist,
                custom_tag_copyright,
                sample_factor
            )
        }

        if (MyDebug.LOG) Logger.d(TAG, "saveImage complete, success: $success")

        return success
    }

    override fun onPictureTaken(data: ByteArray?, current_date: Date?): Boolean {
        if (MyDebug.LOG) Logger.d(TAG, "onPictureTaken")

        n_capture_images++
        if (MyDebug.LOG) Logger.d(TAG, "n_capture_images is now $n_capture_images")

        val images: MutableList<ByteArray> = ArrayList()
        data?.let { images.add(data) }

        val success = saveImage(false, images, current_date)

        if (MyDebug.LOG) Logger.d(TAG, "onPictureTaken complete, success: $success")

        return success
    }

    override fun onBurstPictureTaken(
        images: MutableList<ByteArray>?, current_date: Date?
    ): Boolean {
        if (MyDebug.LOG) Logger.d(
            TAG, "onBurstPictureTaken: received " + images?.size + " images"
        )

        var success = false
        var photo_mode = this.photoMode
        if (main_activity.preview!!.isVideo) {
            if (MyDebug.LOG) Logger.d(TAG, "snapshot mode")
            // must be in photo snapshot while recording video mode, only support standard photo mode
            photo_mode = PhotoMode.Standard
        }
        if (photo_mode == PhotoMode.HDR) {
            if (MyDebug.LOG) Logger.d(TAG, "HDR mode")
            val save_expo =
                sharedPreferences.getBoolean(PreferenceKeys.HDRSaveExpoPreferenceKey, false)
            if (MyDebug.LOG) Logger.d(TAG, "save_expo: $save_expo")

            images?.let { success = saveImage(save_expo, images, current_date) }
        } else {
            if (MyDebug.LOG) {
                Logger.d(TAG, "exposure/focus bracketing mode mode")
                if (photo_mode != PhotoMode.ExpoBracketing && photo_mode != PhotoMode.FocusBracketing) Log.e(
                    TAG, "onBurstPictureTaken called with unexpected photo mode?!: $photo_mode"
                )
            }

            success = saveImage(true, images, current_date)
        }
        return success
    }

    override fun onRawPictureTaken(raw_image: RawImage?, current_date: Date?): Boolean {
        if (MyDebug.LOG) Logger.d(TAG, "onRawPictureTaken")
        System.gc()

        n_capture_images_raw++
        if (MyDebug.LOG) Logger.d(
            TAG, "n_capture_images_raw is now " + n_capture_images_raw
        )

        val do_in_background = saveInBackground(false)

        var photo_mode = this.photoMode
        if (main_activity.preview?.isVideo == true) {
            if (MyDebug.LOG) Logger.d(TAG, "snapshot mode")
            // must be in photo snapshot while recording video mode, only support standard photo mode
            // (RAW not supported anyway for video snapshot mode, but have this code just to be safe)
            photo_mode = PhotoMode.Standard
        }
        val force_suffix = forceSuffix(photo_mode)
        // N.B., n_capture_images_raw will be 1 for first image, not 0, so subtract 1 so we start off from _0.
        // (It wouldn't be a huge problem if we did start from _1, but it would be inconsistent with the naming
        // of images where images.size() > 1 (e.g., expo bracketing mode) where we also start from _0.)
        val suffix_offset = if (force_suffix) (n_capture_images_raw - 1) else 0
        val success = imageSaver!!.saveImageRaw(
            do_in_background, force_suffix, suffix_offset, raw_image, current_date
        )

        if (MyDebug.LOG) Logger.d(TAG, "onRawPictureTaken complete")
        return success
    }

    override fun onRawBurstPictureTaken(
        raw_images: MutableList<RawImage>?, current_date: Date?
    ): Boolean {
        if (MyDebug.LOG) Logger.d(TAG, "onRawBurstPictureTaken")
        System.gc()

        val do_in_background = saveInBackground(false)

        // currently we don't ever do post processing with RAW burst images, so just save them all
        var success = true
        var i = 0
        raw_images?.let {
            while (i < raw_images.size && success) {
                success = imageSaver!!.saveImageRaw(
                    do_in_background, true, i, raw_images.get(i), current_date
                )
                i++
            }
        }

        if (MyDebug.LOG) Logger.d(TAG, "onRawBurstPictureTaken complete")
        return success
    }

    fun addLastImage(file: File, share: Boolean) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "addLastImage: " + file)
            Logger.d(TAG, "share?: " + share)
        }
        last_images_type = LastImagesType.FILE
        val last_image = LastImage(file.getAbsolutePath(), share)
        last_images.add(last_image)
    }

    fun addLastImageSAF(uri: Uri?, share: Boolean) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "addLastImageSAF: " + uri)
            Logger.d(TAG, "share?: " + share)
        }
        last_images_type = LastImagesType.SAF
        val last_image = LastImage(uri, share)
        last_images.add(last_image)
    }

    fun addLastImageMediaStore(uri: Uri?, share: Boolean) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "addLastImageMediaStore: " + uri)
            Logger.d(TAG, "share?: " + share)
        }
        last_images_type = LastImagesType.MEDIASTORE
        val last_image = LastImage(uri, share)
        last_images.add(last_image)
    }

    fun clearLastImages() {
        if (MyDebug.LOG) Logger.d(TAG, "clearLastImages")
        last_images_type = LastImagesType.FILE
        last_images.clear()
        drawPreview!!.clearLastImage()
    }

    fun shareLastImage() {
        if (MyDebug.LOG) Logger.d(TAG, "shareLastImage")
        val preview = main_activity.preview
        if (preview!!.isPreviewPaused()) {
            var share_image: LastImage? = null
            var i = 0
            while (i < last_images.size && share_image == null) {
                val last_image = last_images.get(i)
                if (last_image.share) {
                    share_image = last_image
                }
                i++
            }
            var done = true
            if (share_image != null) {
                val last_image_uri = share_image.uri
                if (MyDebug.LOG) Logger.d(TAG, "Share: " + last_image_uri)
                if (last_image_uri == null) {
                    // could happen with Android 7+ with non-SAF if the image hasn't been scanned yet,
                    // so we don't know the uri yet
                    Log.e(TAG, "can't share last image as don't yet have uri")
                    done = false
                } else {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.setType("image/jpeg")
                    intent.putExtra(Intent.EXTRA_STREAM, last_image_uri)
                    main_activity.startActivity(Intent.createChooser(intent, "Photo"))
                }
            }
            if (done) {
                clearLastImages()
                preview.startCameraPreview()
            }
        }
    }

    private fun trashImage(
        image_type: LastImagesType?, image_uri: Uri?, image_name: String?, from_user: Boolean
    ) {
        if (MyDebug.LOG) Logger.d(TAG, "trashImage")
        val preview = main_activity.preview
        if (image_type == LastImagesType.SAF && image_uri != null) {
            if (MyDebug.LOG) Logger.d(TAG, "Delete SAF: " + image_uri)
            val file = storageUtils.getFileFromDocumentUriSAF(
                image_uri, false
            ) // need to get file before deleting it, as fileFromDocumentUriSAF may depend on the file still existing
            try {
                if (!DocumentsContract.deleteDocument(
                        main_activity.getContentResolver(), image_uri
                    )
                ) {
                    if (MyDebug.LOG) Log.e(TAG, "failed to delete " + image_uri)
                } else {
                    if (MyDebug.LOG) Logger.d(TAG, "successfully deleted " + image_uri)
                    if (from_user) preview!!.showToast(null, R.string.photo_deleted, true)
                    if (file != null) {
                        // SAF doesn't broadcast when deleting them
                        storageUtils.broadcastFile(file, false, false, false, false, null)
                    }
                }
            } catch (e: FileNotFoundException) {
                // note, Android Studio reports a warning that FileNotFoundException isn't thrown, but it can be
                // thrown by DocumentsContract.deleteDocument - and we get an error if we try to remove the catch!
                if (MyDebug.LOG) Log.e(TAG, "exception when deleting " + image_uri)
                e.printStackTrace()
            }
        } else if (image_type == LastImagesType.MEDIASTORE && image_uri != null) {
            if (MyDebug.LOG) Logger.d(TAG, "Delete MediaStore: " + image_uri)
            if (main_activity.getContentResolver().delete(image_uri, null, null) > 0) {
                if (from_user) preview!!.showToast(photo_delete_toast, R.string.photo_deleted, true)
            }
        } else if (image_name != null) {
            if (MyDebug.LOG) Logger.d(TAG, "Delete: " + image_name)
            val file = File(image_name)
            if (!file.delete()) {
                if (MyDebug.LOG) Log.e(TAG, "failed to delete " + image_name)
            } else {
                if (MyDebug.LOG) Logger.d(TAG, "successfully deleted " + image_name)
                if (from_user) preview!!.showToast(photo_delete_toast, R.string.photo_deleted, true)
                storageUtils.broadcastFile(file, false, false, false, false, null)
            }
        }
    }

    fun trashLastImage() {
        if (MyDebug.LOG) Logger.d(TAG, "trashLastImage")
        val preview = main_activity.preview
        if (preview!!.isPreviewPaused()) {
            for (i in last_images.indices) {
                val last_image = last_images.get(i)
                trashImage(last_images_type, last_image.uri, last_image.name, true)
            }
            clearLastImages()
            drawPreview!!.clearGhostImage() // doesn't make sense to show the last image as a ghost, if the user has trashed it!
            preview.startCameraPreview()
        }
        // Calling updateGalleryIcon() immediately has problem that it still returns the latest image that we've just deleted!
        // But works okay if we call after a delay. 100ms works fine on Nexus 7 and Galaxy Nexus, but set to 500 just to be safe.
        // Also note that if using option to strip all exif tags, we won't be able to find the previous most recent image - but not
        // much we can do here when the user is using that option.
        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                main_activity.updateGalleryIcon()
            }
        }, 500)
    }

    /** Called when StorageUtils scans a saved photo with MediaScannerConnection.scanFile.
     * @param file The file that was scanned.
     * @param uri  The file's corresponding uri.
     */
    fun scannedFile(file: File, uri: Uri?) {
        if (MyDebug.LOG) {
            Logger.d(TAG, "scannedFile")
            Logger.d(TAG, "file: $file")
            Logger.d(TAG, "uri: $uri")
        }
        // see note under LastImage constructor for why we need to update the Uris
        for (i in last_images.indices) {
            val last_image = last_images.get(i)
            if (MyDebug.LOG) Logger.d(TAG, "compare to last_image: " + last_image.name)
            if (last_image.uri == null && last_image.name != null && last_image.name == file.absolutePath) {
                if (MyDebug.LOG) Logger.d(TAG, "updated last_image : $i")
                last_image.uri = uri
            }
        }
    }

    // for testing
    fun hasThumbnailAnimation(): Boolean {
        return this.drawPreview!!.hasThumbnailAnimation()
    }

    val hDRProcessor: HDRProcessor?
        get() = imageSaver!!.getHDRProcessor()

    val panoramaProcessor: PanoramaProcessor?
        get() = imageSaver!!.getPanoramaProcessor()

    var test_set_available_memory: Boolean = false
    var test_available_memory: Long = 0

    init {
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            Logger.d(TAG, "MyApplicationInterface")
            debug_time = System.currentTimeMillis()
        }
        this.main_activity = mainActivity
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        this.locationSupplier = LocationSupplier(mainActivity)
        if (MyDebug.LOG) Logger.d(
            TAG,
            "MyApplicationInterface: time after creating location supplier: " + (System.currentTimeMillis() - debug_time)
        )
        this.gyroSensor = GyroSensor(mainActivity)
        this.storageUtils = StorageUtils(mainActivity, this)
        if (MyDebug.LOG) Logger.d(
            TAG,
            "MyApplicationInterface: time after creating storage utils: " + (System.currentTimeMillis() - debug_time)
        )
        this.drawPreview = DrawPreview(mainActivity, this)

        this.imageSaver = ImageSaver(mainActivity)
        this.imageSaver.start()

        this.reset(false)
        if (savedInstanceState != null) {
            // load the things we saved in onSaveInstanceState().
            if (MyDebug.LOG) Logger.d(TAG, "read from savedInstanceState")
            has_set_cameraId = true
            cameraId = savedInstanceState.getInt("cameraId", cameraId_default)
            if (MyDebug.LOG) Logger.d(TAG, "found cameraId: $cameraId")
            cameraIdSPhysical = savedInstanceState.getString("cameraIdSPhysical", null)
            if (MyDebug.LOG) Logger.d(TAG, "found cameraIdSPhysical: $cameraIdSPhysical")
            this.nRMode = savedInstanceState.getString("nr_mode", nr_mode_default)
            if (MyDebug.LOG) Logger.d(TAG, "found nr_mode: " + this.nRMode)
            aperture = savedInstanceState.getFloat("aperture", aperture_default)
            if (MyDebug.LOG) Logger.d(TAG, "found aperture: $aperture")
        }

        if (MyDebug.LOG) Logger.d(
            TAG,
            "MyApplicationInterface: total time to create MyApplicationInterface: " + (System.currentTimeMillis() - debug_time)
        )
    }

    companion object {
        private const val TAG = "MyApplicationInterface"

        const val panoramaPicsPerScreen: Float = 3.33333f
        const val max_panorama_pics_c: Int =
            10 // if we increase this, review against memory requirements under MainActivity.supportsPanorama()

        // camera properties which are saved in bundle, but not stored in preferences (so will be remembered if the app goes into background, but not after restart)
        private const val cameraId_default = 0
        private const val nr_mode_default = "preference_nr_mode_normal"
        private val aperture_default = -1.0f
        fun choosePanoramaResolution(sizes: MutableList<CameraController.Size>): CameraController.Size? {
            // if we allow panorama with higher resolutions, review against memory requirements under MainActivity.supportsPanorama()
            // also may need to update the downscaling in the testing code
            val max_width_c = 2080
            var found = false
            var best_size: CameraController.Size? = null
            // find largest width <= max_width_c with aspect ratio 4:3
            for (size in sizes) {
                if (size.width <= max_width_c) {
                    val aspect_ratio = (size.width.toDouble()) / size.height.toDouble()
                    if (abs(aspect_ratio - 4.0 / 3.0) < 1.0e-5) {
                        if (!found || size.width > best_size!!.width) {
                            found = true
                            best_size = size
                        }
                    }
                }
            }
            if (found) {
                return best_size
            }
            // else find largest width <= max_width_c
            for (size in sizes) {
                if (size.width <= max_width_c) {
                    if (!found || size.width > best_size!!.width) {
                        found = true
                        best_size = size
                    }
                }
            }
            if (found) {
                return best_size
            }
            // else find smallest width
            for (size in sizes) {
                if (!found || size.width < best_size!!.width) {
                    found = true
                    best_size = size
                }
            }
            return best_size
        }

        /** Whether the Mediastore API supports saving subtitle files.
         */
        @JvmStatic
        fun mediastoreSupportsVideoSubtitles(): Boolean {
            // Android 11+ no longer allows mediastore API to save types that Android doesn't support!
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.R
        }
    }
}