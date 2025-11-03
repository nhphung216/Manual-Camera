package com.ssolstice.camera.manual.cameracontroller

import android.hardware.Camera
import android.hardware.Camera.AutoFocusMoveCallback
import android.hardware.Camera.ShutterCallback
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.view.SurfaceHolder
import android.view.TextureView
import com.ssolstice.camera.manual.MyDebug
import com.ssolstice.camera.manual.utils.Logger.d
import com.ssolstice.camera.manual.utils.Logger.e
import java.io.IOException
import java.util.Collections
import kotlin.math.max
import kotlin.math.min

/** Provides support using Android's original camera API
 * android.hardware.Camera.
 */
class CameraController1(cameraId: Int, camera_error_cb: ErrorCallback?) :
    CameraController(cameraId) {
    private var camera: Camera? = null
    private var display_orientation = 0
    private val camera_info = Camera.CameraInfo()
    private var iso_key: String? = null
    private var frontscreen_flash = false
    private val camera_error_cb: ErrorCallback?
    private var sounds_enabled = true

    private var n_burst = 0 // number of expected burst images in this capture
    private val pending_burst_images: MutableList<ByteArray?> =
        ArrayList<ByteArray?>() // burst images that have been captured so far, but not yet sent to the application
    private var burst_exposures: MutableList<Int?>? = null
    private var want_expo_bracketing = false
    private var expo_bracketing_n_images = 3
    private var expo_bracketing_stops = 2.0

    private var autofocus_timeout_handler: Handler? = null // handler for tracking autofocus timeout
    private var autofocus_timeout_runnable: Runnable? =
        null // runnable set for tracking autofocus timeout

    // we keep track of some camera settings rather than reading from Camera.getParameters() every time. Firstly this is important
    // for performance (affects UI rendering times, e.g., see profiling of GPU rendering). Secondly runtimeexceptions from
    // Camera.getParameters() seem to be common in Google Play, particularly for getZoom().
    private var current_zoom_value = 0
    private var current_exposure_compensation = 0
    private var picture_width = 0
    private var picture_height = 0

    /** Opens the camera device.
     * @param cameraId Which camera to open (must be between 0 and CameraControllerManager1.getNumberOfCameras()-1).
     * @param camera_error_cb onError() will be called if the camera closes due to serious error. No more calls to the CameraController1 object should be made (though a new one can be created, to try reopening the camera).
     * @throws CameraControllerException if the camera device fails to open.
     */
    init {
        d(TAG, "create new CameraController1: " + cameraId)
        this.camera_error_cb = camera_error_cb
        try {
            camera = Camera.open(cameraId)
        } catch (e: RuntimeException) {
            e(TAG, "failed to open camera")
            e.printStackTrace()
            throw CameraControllerException()
        }
        if (camera == null) {
            // Although the documentation says Camera.open() should throw a RuntimeException, it seems that it some cases it can return null
            // I've seen this in some crashes reported in Google Play; also see:
            // http://stackoverflow.com/questions/12054022/camera-open-returns-null
            e(TAG, "camera.open returned null")
            throw CameraControllerException()
        }
        try {
            Camera.getCameraInfo(cameraId, camera_info)
        } catch (e: RuntimeException) {
            // Had reported RuntimeExceptions from Google Play
            // also see http://stackoverflow.com/questions/22383708/java-lang-runtimeexception-fail-to-get-camera-info
            e(TAG, "failed to get camera info")
            e.printStackTrace()
            this.release()
            throw CameraControllerException()
        }

        val camera_error_callback = CameraErrorCallback()
        camera!!.setErrorCallback(camera_error_callback)

        /*{
			// test error handling
			final Handler handler = new Handler();
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if( MyDebug.LOG )
						Logger.INSTANCE.d(TAG, "test camera error");
					camera_error_callback.onError(Camera.CAMERA_ERROR_SERVER_DIED, camera);
				}
			}, 5000);
		}*/
    }

    override fun onError() {
        e(TAG, "onError")
        if (this.camera != null) { // I got Google Play crash reports due to camera being null in v1.36
            this.camera!!.release()
            this.camera = null
        }
        if (this.camera_error_cb != null) {
            // need to communicate the problem to the application
            this.camera_error_cb.onError()
        }
    }

    private inner class CameraErrorCallback : Camera.ErrorCallback {
        override fun onError(error: Int, cam: Camera?) {
            // n.b., as this is potentially serious error, we always log even if MyDebug.LOG is false
            e(TAG, "camera onError: " + error)
            if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                e(TAG, "    CAMERA_ERROR_SERVER_DIED")
                this@CameraController1.onError()
            } else if (error == Camera.CAMERA_ERROR_UNKNOWN) {
                e(TAG, "    CAMERA_ERROR_UNKNOWN ")
            }
        }
    }

    override fun release() {
        if (camera != null) {
            // have had crashes when this is called from Preview/CloseCameraTask.
            camera!!.release()
            camera = null
        }
    }

    private val parameters: Camera.Parameters
        get() {
            d(
                TAG, "getParameters"
            )
            return camera!!.getParameters()
        }

    private fun setCameraParameters(parameters: Camera.Parameters?) {
        d(TAG, "setCameraParameters")
        try {
            camera!!.setParameters(parameters)
            d(TAG, "done")
        } catch (e: RuntimeException) {
            // just in case something has gone wrong
            d(TAG, "failed to set parameters")
            e.printStackTrace()
            count_camera_parameters_exception++
        }
    }

    private fun convertFlashModesToValues(supported_flash_modes: MutableList<String?>?): MutableList<String?> {
        if (MyDebug.LOG) {
            d(TAG, "convertFlashModesToValues()")
            d(TAG, "supported_flash_modes: " + supported_flash_modes)
        }
        val output_modes: MutableList<String?> = ArrayList<String?>()
        if (supported_flash_modes != null) {
            // also resort as well as converting
            if (supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                output_modes.add("flash_off")
                d(TAG, " supports flash_off")
            }
            if (supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                output_modes.add("flash_auto")
                d(TAG, " supports flash_auto")
            }
            if (supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                output_modes.add("flash_on")
                d(TAG, " supports flash_on")
            }
            if (supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                output_modes.add("flash_torch")
                d(TAG, " supports flash_torch")
            }
            if (supported_flash_modes.contains(Camera.Parameters.FLASH_MODE_RED_EYE)) {
                output_modes.add("flash_red_eye")
                d(TAG, " supports flash_red_eye")
            }
        }

        // Samsung Galaxy S7 at least for front camera has supported_flash_modes: auto, beach, portrait?!
        // so rather than checking supported_flash_modes, we should check output_modes here
        // this is always why we check whether the size is greater than 1, rather than 0 (this also matches
        // the check we do in Preview.setupCameraParameters()).
        if (output_modes.size > 1) {
            d(TAG, "flash supported")
        } else {
            if (getFacing() == Facing.FACING_FRONT) {
                d(TAG, "front-screen with no flash")
                output_modes.clear() // clear any pre-existing mode (see note above about Samsung Galaxy S7)
                output_modes.add("flash_off")
                output_modes.add("flash_frontscreen_on")
                output_modes.add("flash_frontscreen_torch")
            } else {
                d(TAG, "no flash")
                // probably best to not return any modes, rather than one mode (see note about about Samsung Galaxy S7)
                output_modes.clear()
            }
        }

        return output_modes
    }

    private fun convertFocusModesToValues(supported_focus_modes: MutableList<String?>?): MutableList<String?> {
        d(TAG, "convertFocusModesToValues()")
        val output_modes: MutableList<String?> = ArrayList<String?>()
        if (supported_focus_modes != null) {
            // also resort as well as converting
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                output_modes.add("focus_mode_auto")
                if (MyDebug.LOG) {
                    d(TAG, " supports focus_mode_auto")
                }
            }
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                output_modes.add("focus_mode_infinity")
                d(TAG, " supports focus_mode_infinity")
            }
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
                output_modes.add("focus_mode_macro")
                d(TAG, " supports focus_mode_macro")
            }
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                output_modes.add("focus_mode_locked")
                if (MyDebug.LOG) {
                    d(TAG, " supports focus_mode_locked")
                }
            }
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                output_modes.add("focus_mode_fixed")
                d(TAG, " supports focus_mode_fixed")
            }
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_EDOF)) {
                output_modes.add("focus_mode_edof")
                d(TAG, " supports focus_mode_edof")
            }
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                output_modes.add("focus_mode_continuous_picture")
                d(TAG, " supports focus_mode_continuous_picture")
            }
            if (supported_focus_modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                output_modes.add("focus_mode_continuous_video")
                d(TAG, " supports focus_mode_continuous_video")
            }
        }
        return output_modes
    }

    override fun getAPI(): String {
        return "Camera"
    }

    @Throws(CameraControllerException::class)
    override fun getCameraFeatures(): CameraFeatures {
        d(TAG, "getCameraFeatures()")
        val parameters: Camera.Parameters
        try {
            parameters = this.parameters
        } catch (e: RuntimeException) {
            e(TAG, "failed to get camera parameters")
            e.printStackTrace()
            throw CameraControllerException()
        }
        val camera_features = CameraFeatures()
        camera_features.is_zoom_supported = parameters.isZoomSupported()
        if (camera_features.is_zoom_supported) {
            camera_features.max_zoom = parameters.getMaxZoom()
            try {
                camera_features.zoom_ratios = parameters.getZoomRatios()
            } catch (e: NumberFormatException) {
                // crash java.lang.NumberFormatException: Invalid int: " 500" reported in v1.4 on device "es209ra", Android 4.1, 3 Jan 2014
                // this is from java.lang.Integer.invalidInt(Integer.java:138) - unclear if this is a bug in ManualCamera, all we can do for now is catch it
                e(TAG, "NumberFormatException in getZoomRatios()")
                e.printStackTrace()
                camera_features.is_zoom_supported = false
                camera_features.max_zoom = 0
                camera_features.zoom_ratios = null
            }
        }

        camera_features.supports_face_detection = parameters.getMaxNumDetectedFaces() > 0

        // get available sizes
        val camera_picture_sizes = parameters.getSupportedPictureSizes()
        if (camera_picture_sizes == null) {
            // Google Play crashes suggest that getSupportedPictureSizes() can be null?! Better to fail gracefully
            // instead of crashing
            e(TAG, "getSupportedPictureSizes() returned null!")
            throw CameraControllerException()
        }
        camera_features.picture_sizes = ArrayList<Size?>()
        //camera_features.picture_sizes.add(new CameraController.Size(1920, 1080)); // test
        for (camera_size in camera_picture_sizes) {
            // we leave supports_burst as true - strictly speaking it should be false, but we'll never use a fast burst mode
            // with CameraController1 anyway
            camera_features.picture_sizes.add(Size(camera_size.width, camera_size.height))
        }
        // sizes are usually already sorted from high to low, but sort just in case
        // note some devices do have sizes in a not fully sorted order (e.g., Nokia 8)
        Collections.sort<Size?>(camera_features.picture_sizes, SizeSorter())

        //camera_features.supported_flash_modes = parameters.getSupportedFlashModes(); // Android format
        val supported_flash_modes = parameters.supportedFlashModes // Android format
        camera_features.supported_flash_values =
            convertFlashModesToValues(supported_flash_modes) // convert to our format (also resorts)

        val supported_focus_modes = parameters.supportedFocusModes // Android format
        camera_features.supported_focus_values =
            convertFocusModesToValues(supported_focus_modes) // convert to our format (also resorts)
        camera_features.max_num_focus_areas = parameters.maxNumFocusAreas

        camera_features.is_exposure_lock_supported = parameters.isAutoExposureLockSupported

        camera_features.is_white_balance_lock_supported = parameters.isAutoWhiteBalanceLockSupported

        camera_features.is_video_stabilization_supported = parameters.isVideoStabilizationSupported

        camera_features.is_photo_video_recording_supported = parameters.isVideoSnapshotSupported

        camera_features.min_exposure = parameters.minExposureCompensation
        camera_features.max_exposure = parameters.maxExposureCompensation
        camera_features.exposure_step = this.exposureCompensationStep
        camera_features.supports_expo_bracketing =
            (camera_features.min_exposure != 0 && camera_features.max_exposure != 0) // require both a darker and brighter exposure, in order to support expo bracketing
        camera_features.max_expo_bracketing_n_images = max_expo_bracketing_n_images

        var camera_video_sizes = parameters.supportedVideoSizes
        if (camera_video_sizes == null) {
            // if null, we should use the preview sizes - see http://stackoverflow.com/questions/14263521/android-getsupportedvideosizes-allways-returns-null
            d(TAG, "take video_sizes from preview sizes")
            camera_video_sizes = parameters.supportedPreviewSizes
        }
        camera_features.video_sizes = ArrayList<Size?>()
        //camera_features.video_sizes.add(new CameraController.Size(1920, 1080)); // test
        for (camera_size in camera_video_sizes) {
            camera_features.video_sizes.add(Size(camera_size.width, camera_size.height))
        }
        // sizes are usually already sorted from high to low, but sort just in case
        Collections.sort<Size?>(camera_features.video_sizes, SizeSorter())

        val camera_preview_sizes = parameters.supportedPreviewSizes
        camera_features.preview_sizes = ArrayList<Size?>()
        for (camera_size in camera_preview_sizes) {
            camera_features.preview_sizes.add(Size(camera_size.width, camera_size.height))
        }

        d(TAG, "camera parameters: " + parameters.flatten())

        camera_features.can_disable_shutter_sound = camera_info.canDisableShutterSound

        // Determine view angles. Note that these can vary based on the resolution - and since we read these before the caller has
        // set the desired resolution, this isn't strictly correct. However these are presumably view angles for the photo anyway,
        // when some callers (e.g., DrawPreview) want view angles for the preview anyway - so these will only be an approximation for
        // what we want anyway.
        val default_view_angle_x = 55.0f
        val default_view_angle_y = 43.0f
        try {
            camera_features.view_angle_x = parameters.horizontalViewAngle
            camera_features.view_angle_y = parameters.verticalViewAngle
        } catch (e: Exception) {
            // apparently some devices throw exceptions...
            e.printStackTrace()
            e(TAG, "exception reading horizontal or vertical view angles")
            camera_features.view_angle_x = default_view_angle_x
            camera_features.view_angle_y = default_view_angle_y
        }
        // need to handle some devices reporting rubbish
        if (camera_features.view_angle_x > 150.0f || camera_features.view_angle_y > 150.0f) {
            e(TAG, "camera API reporting stupid view angles, set to sensible defaults")
            camera_features.view_angle_x = default_view_angle_x
            camera_features.view_angle_y = default_view_angle_y
        }

        return camera_features
    }

    /** Important, from docs:
     * "Changing scene mode may override other parameters (such as flash mode, focus mode, white balance).
     * For example, suppose originally flash mode is on and supported flash modes are on/off. In night
     * scene mode, both flash mode and supported flash mode may be changed to off. After setting scene
     * mode, applications should call getParameters to know if some parameters are changed."
     */
    override fun setSceneMode(value: String?): SupportedValues? {
        val parameters: Camera.Parameters
        try {
            parameters = this.parameters
        } catch (e: RuntimeException) {
            e(TAG, "exception from getParameters")
            e.printStackTrace()
            count_camera_parameters_exception++
            return null
        }
        val values = parameters.supportedSceneModes/*{
			// test
			values = new ArrayList<>();
			values.add(ISO_DEFAULT);
		}*/
        val supported_values = checkModeIsSupported(values, value, SCENE_MODE_DEFAULT)
        if (supported_values != null) {
            val scene_mode = parameters.sceneMode
            // if scene mode is null, it should mean scene modes aren't supported anyway
            if (scene_mode != null && scene_mode != supported_values.selected_value) {
                parameters.sceneMode = supported_values.selected_value
                setCameraParameters(parameters)
            }
        }
        return supported_values
    }

    override fun getSceneMode(): String? {
        val parameters = this.parameters
        return parameters.getSceneMode()
    }

    override fun sceneModeAffectsFunctionality(): Boolean {
        // see https://developer.android.com/reference/android/hardware/Camera.Parameters.html#setSceneMode(java.lang.String)
        // "Changing scene mode may override other parameters ... After setting scene mode, applications should call
        // getParameters to know if some parameters are changed."
        return true
    }

    override fun setColorEffect(value: String?): SupportedValues? {
        val parameters = this.parameters
        val values = parameters.getSupportedColorEffects()
        val supported_values = checkModeIsSupported(values, value, COLOR_EFFECT_DEFAULT)
        if (supported_values != null) {
            val color_effect = parameters.getColorEffect()
            // have got nullpointerexception from Google Play, so now check for null
            if (color_effect == null || color_effect != supported_values.selected_value) {
                parameters.setColorEffect(supported_values.selected_value)
                setCameraParameters(parameters)
            }
        }
        return supported_values
    }

    override fun getColorEffect(): String? {
        val parameters = this.parameters
        return parameters.getColorEffect()
    }

    override fun setWhiteBalance(value: String?): SupportedValues? {
        d(TAG, "setWhiteBalance: " + value)
        val parameters = this.parameters
        val values = parameters.getSupportedWhiteBalance()
        if (values != null) {
            // Some devices (e.g., OnePlus 3T) claim to support a "manual" mode, even though this
            // isn't one of the possible white balances defined in Camera.Parameters.
            // Since the old API doesn't support white balance temperatures, and this mode seems to
            // have no useful effect, we remove it to avoid confusion.
            while (values.contains("manual")) {
                values.remove("manual")
            }
        }
        val supported_values = checkModeIsSupported(values, value, WHITE_BALANCE_DEFAULT)
        if (supported_values != null) {
            val white_balance = parameters.getWhiteBalance()
            // if white balance is null, it should mean white balances aren't supported anyway
            if (white_balance != null && white_balance != supported_values.selected_value) {
                parameters.setWhiteBalance(supported_values.selected_value)
                setCameraParameters(parameters)
            }
        }
        return supported_values
    }

    override fun getWhiteBalance(): String? {
        val parameters = this.parameters
        return parameters.getWhiteBalance()
    }

    override fun setWhiteBalanceTemperature(temperature: Int): Boolean {
        // not supported for CameraController1
        return false
    }

    override fun getWhiteBalanceTemperature(): Int {
        // not supported for CameraController1
        return 0
    }

    override fun setAntiBanding(value: String?): SupportedValues? {
        val parameters = this.parameters
        val values = parameters.getSupportedAntibanding()
        val supported_values = checkModeIsSupported(values, value, ANTIBANDING_DEFAULT)
        if (supported_values != null) {
            // for antibanding, if the requested value isn't available, we don't modify it at all
            // (so we stick with the device's default setting)
            if (supported_values.selected_value == value) {
                val antibanding = parameters.getAntibanding()
                if (antibanding == null || antibanding != supported_values.selected_value) {
                    parameters.setAntibanding(supported_values.selected_value)
                    setCameraParameters(parameters)
                }
            }
        }
        return supported_values
    }

    override fun getAntiBanding(): String? {
        val parameters = this.parameters
        return parameters.getAntibanding()
    }

    override fun setEdgeMode(value: String?): SupportedValues? {
        return null
    }

    override fun getEdgeMode(): String? {
        return null
    }

    override fun setNoiseReductionMode(value: String?): SupportedValues? {
        return null
    }

    override fun getNoiseReductionMode(): String? {
        return null
    }

    override fun setISO(value: String?): SupportedValues? {
        val parameters = this.parameters
        // get available isos - no standard value for this, see http://stackoverflow.com/questions/2978095/android-camera-api-iso-setting
        var iso_values = parameters.get("iso-values")
        if (iso_values == null) {
            iso_values = parameters.get("iso-mode-values") // Galaxy Nexus
            if (iso_values == null) {
                iso_values = parameters.get("iso-speed-values") // Micromax A101
                if (iso_values == null) iso_values =
                    parameters.get("nv-picture-iso-values") // LG dual P990
            }
        }
        var values: MutableList<String?>? = null
        if (iso_values != null && iso_values.isNotEmpty()) {
            d(TAG, "iso_values: " + iso_values)
            val isos_array: Array<String?> =
                iso_values.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            // split shouldn't return null
            if (isos_array.isNotEmpty()) {
                // remove duplicates (OnePlus 3T has several duplicate "auto" entries)
                val hashSet = HashSet<String?>()
                values = ArrayList<String?>()
                // use hashset for efficiency
                // make sure we alo preserve the order
                for (iso in isos_array) {
                    if (!hashSet.contains(iso)) {
                        values.add(iso)
                        hashSet.add(iso)
                    }
                }
            }
        }

        iso_key = "iso"
        if (parameters.get(iso_key) == null) {
            iso_key = "iso-speed" // Micromax A101
            if (parameters.get(iso_key) == null) {
                iso_key = "nv-picture-iso" // LG dual P990
                if (parameters.get(iso_key) == null) {
                    if (Build.MODEL.contains("Z00")) iso_key =
                        "iso" // Asus Zenfone 2 Z00A and Z008: see https://sourceforge.net/p/opencamera/tickets/183/
                    else iso_key = null // not supported
                }
            }
        }
        if (iso_key != null) {
            if (values == null) {
                // set a default for some devices which have an iso_key, but don't give a list of supported ISOs
                values = ArrayList()
                values.add(ISO_DEFAULT)
                values.add("50")
                values.add("100")
                values.add("200")
                values.add("400")
                values.add("800")
                values.add("1600")
            }
            val supported_values = checkModeIsSupported(values, value, ISO_DEFAULT)
            if (supported_values != null) {
                d(TAG, "set: " + iso_key + " to: " + supported_values.selected_value)
                parameters.set(iso_key, supported_values.selected_value)
                setCameraParameters(parameters)
            }
            return supported_values
        }
        return null
    }

    override fun getISOKey(): String? {
        d(TAG, "getISOKey")
        return this.iso_key
    }

    override fun setManualISO(manual_iso: Boolean, iso: Int) {
        // not supported for CameraController1
    }

    override fun isManualISO(): Boolean {
        // not supported for CameraController1
        return false
    }

    override fun setISO(iso: Int): Boolean {
        // not supported for CameraController1
        return false
    }

    override fun getISO(): Int {
        // not supported for CameraController1
        return 0
    }

    override fun getExposureTime(): Long {
        // not supported for CameraController1
        return 0L
    }

    override fun setExposureTime(exposure_time: Long): Boolean {
        // not supported for CameraController1
        return false
    }

    override fun setAperture(aperture: Float) {
        // not supported for CameraController1
    }

    override fun getPictureSize(): Size {/*Camera.Parameters parameters = this.getParameters();
    	Camera.Size camera_size = parameters.getPictureSize();
    	return new CameraController.Size(camera_size.width, camera_size.height);*/
        return Size(picture_width, picture_height)
    }

    override fun setPictureSize(width: Int, height: Int) {
        val parameters = this.parameters
        this.picture_width = width
        this.picture_height = height
        parameters.setPictureSize(width, height)
        setCameraParameters(parameters)
    }

    override fun getPreviewSize(): Size {
        val parameters = this.parameters
        val camera_size = parameters.previewSize
        return Size(camera_size.width, camera_size.height)
    }

    override fun setPreviewSize(width: Int, height: Int) {
        val parameters = this.parameters
        parameters.setPreviewSize(width, height)
        setCameraParameters(parameters)
    }

    override fun setCameraExtension(enabled: Boolean, extension: Int) {
        // not supported
    }

    override fun isCameraExtension(): Boolean {
        return false
    }

    override fun getCameraExtension(): Int {
        return -1
    }

    override fun setBurstType(burst_type: BurstType?) {
        d(TAG, "setBurstType: $burst_type")
        if (camera == null) {
            e(TAG, "no camera")
            return
        }
        if (burst_type != BurstType.BURSTTYPE_NONE && burst_type != BurstType.BURSTTYPE_EXPO) {
            e(TAG, "burst type not supported")
            return
        }
        this.want_expo_bracketing = burst_type == BurstType.BURSTTYPE_EXPO
    }

    override fun getBurstType(): BurstType {
        return if (want_expo_bracketing) BurstType.BURSTTYPE_EXPO else BurstType.BURSTTYPE_NONE
    }

    override fun setBurstNImages(burst_requested_n_images: Int) {
        // not supported
    }

    override fun setBurstForNoiseReduction(
        burst_for_noise_reduction: Boolean, noise_reduction_low_light: Boolean
    ) {
        // not supported
    }

    override fun isContinuousBurstInProgress(): Boolean {
        // not supported
        return false
    }

    override fun stopContinuousBurst() {
        // not supported
    }

    override fun stopFocusBracketingBurst() {
        // not supported
    }

    override fun setExpoBracketingNImages(n_images: Int) {
        var n_images = n_images
        d(TAG, "setExpoBracketingNImages: $n_images")
        if (n_images <= 1 || (n_images % 2) == 0) {
            e(TAG, "n_images should be an odd number greater than 1")
            throw RuntimeException() // throw as RuntimeException, as this is a programming error
        }
        if (n_images > max_expo_bracketing_n_images) {
            n_images = max_expo_bracketing_n_images
            e(TAG, "limiting n_images to max of $n_images")
        }
        this.expo_bracketing_n_images = n_images
    }

    override fun setExpoBracketingStops(stops: Double) {
        d(TAG, "setExpoBracketingStops: $stops")
        if (stops <= 0.0) {
            e(TAG, "stops should be positive")
            throw RuntimeException() // throw as RuntimeException, as this is a programming error
        }
        this.expo_bracketing_stops = stops
    }

    override fun setDummyCaptureHack(dummy_capture_hack: Boolean) {
        // not supported for CameraController1
    }

    override fun setUseExpoFastBurst(use_expo_fast_burst: Boolean) {
        // not supported for CameraController1
    }

    override fun isCaptureFastBurst(): Boolean {
        // not supported for CameraController1
        return false
    }

    override fun isCapturingBurst(): Boolean {
        return getBurstTotal() > 1 && getNBurstTaken() < getBurstTotal()
    }

    override fun getNBurstTaken(): Int {
        return pending_burst_images.size
    }

    override fun getBurstTotal(): Int {
        return n_burst
    }

    override fun setJpegR(want_jpeg_r: Boolean) {
        // not supported for CameraController1
    }

    override fun setRaw(want_raw: Boolean, max_raw_images: Int) {
        // not supported for CameraController1
    }

    override fun setVideoHighSpeed(setVideoHighSpeed: Boolean) {
        // not supported for CameraController1
    }

    override fun getOpticalStabilization(): Boolean {
        // not supported for CameraController1
        return false
    }

    override fun setVideoStabilization(enabled: Boolean) {
        val parameters = this.parameters
        parameters.setVideoStabilization(enabled)
        setCameraParameters(parameters)
    }

    override fun getVideoStabilization(): Boolean {
        try {
            val parameters = this.parameters
            return parameters.getVideoStabilization()
        } catch (e: RuntimeException) {
            // have had crashes from Google Play for getParameters - assume video stabilization not enabled
            e(TAG, "failed to get parameters for video stabilization")
            e.printStackTrace()
            count_camera_parameters_exception++
            return false
        }
    }

    override fun setTonemapProfile(
        tonemap_profile: TonemapProfile?, log_profile_strength: Float, gamma: Float
    ) {
        // not supported for CameraController1!
    }

    override fun getTonemapProfile(): TonemapProfile {
        // not supported for CameraController1!
        return TonemapProfile.TONEMAPPROFILE_OFF
    }

    override fun getJpegQuality(): Int {
        val parameters = this.parameters
        return parameters.getJpegQuality()
    }

    override fun setJpegQuality(quality: Int) {
        val parameters = this.parameters
        parameters.setJpegQuality(quality)
        setCameraParameters(parameters)
    }

    override fun getZoom(): Int {/*Camera.Parameters parameters = this.getParameters();
		return parameters.getZoom();*/
        return this.current_zoom_value
    }

    override fun setZoom(value: Int) {
        try {
            val parameters = this.parameters
            this.current_zoom_value = value
            parameters.zoom = value
            setCameraParameters(parameters)
        } catch (e: RuntimeException) {
            e(TAG, "failed to set parameters for zoom")
            e.printStackTrace()
            count_camera_parameters_exception++
        }
    }

    override fun setZoom(value: Int, smooth_zoom: Float) {
        zoom = value
    }

    override fun resetZoom() {
        zoom = 0
    }

    override fun getExposureCompensation(): Int {/*Camera.Parameters parameters = this.getParameters();
		return parameters.getExposureCompensation();*/
        return this.current_exposure_compensation
    }

    private val exposureCompensationStep: Float
        get() {
            var exposure_step: Float
            val parameters = this.parameters
            try {
                exposure_step = parameters.exposureCompensationStep
            } catch (e: Exception) {
                // received a NullPointerException from StringToReal.parseFloat() beneath getExposureCompensationStep() on Google Play!
                e(
                    TAG, "exception from getExposureCompensationStep()"
                )
                e.printStackTrace()
                exposure_step = 1.0f / 3.0f // make up a typical example
            }
            return exposure_step
        }

    // Returns whether exposure was modified
    override fun setExposureCompensation(new_exposure: Int): Boolean {
        if (new_exposure != current_exposure_compensation) {
            val parameters = this.parameters
            this.current_exposure_compensation = new_exposure
            parameters.exposureCompensation = new_exposure
            setCameraParameters(parameters)
            return true
        }
        return false
    }

    override fun setPreviewFpsRange(min: Int, max: Int) {
        try {
            val parameters = this.parameters
            parameters.setPreviewFpsRange(min, max)
            setCameraParameters(parameters)
        } catch (e: RuntimeException) {
            // can get RuntimeException from getParameters - we don't catch within that function because callers may not be able to recover,
            // but here it doesn't really matter if we fail to set the fps range
            e(TAG, "setPreviewFpsRange failed to get parameters")
            e.printStackTrace()
            count_camera_parameters_exception++
        }
    }

    override fun clearPreviewFpsRange() {
        d(TAG, "clearPreviewFpsRange")
        // not supported for old API
    }

    override fun getSupportedPreviewFpsRange(): MutableList<IntArray?>? {
        try {
            val parameters = this.parameters
            return parameters.supportedPreviewFpsRange
        } catch (e: RuntimeException) {/* N.B, have had reports of StringIndexOutOfBoundsException on Google Play on Sony Xperia M devices
				at android.hardware.Camera$Parameters.splitRange(Camera.java:4098)
				at android.hardware.Camera$Parameters.getSupportedPreviewFpsRange(Camera.java:2799)
			  But that's a subclass of RuntimeException which we now catch anyway.
			  */
            e.printStackTrace()
            count_camera_parameters_exception++
        }
        return null
    }

    override fun setFocusValue(focus_value: String) {
        val parameters = this.parameters
        when (focus_value) {
            "focus_mode_auto", "focus_mode_locked" -> parameters.focusMode =
                Camera.Parameters.FOCUS_MODE_AUTO

            "focus_mode_infinity" -> parameters.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
            "focus_mode_macro" -> parameters.focusMode = Camera.Parameters.FOCUS_MODE_MACRO
            "focus_mode_fixed" -> parameters.focusMode = Camera.Parameters.FOCUS_MODE_FIXED
            "focus_mode_edof" -> parameters.focusMode = Camera.Parameters.FOCUS_MODE_EDOF
            "focus_mode_continuous_picture" -> parameters.focusMode =
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE

            "focus_mode_continuous_video" -> parameters.focusMode =
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO

            else -> d(TAG, "setFocusValue() received unknown focus value $focus_value")
        }
        setCameraParameters(parameters)
    }

    private fun convertFocusModeToValue(focus_mode: String?): String {
        // focus_mode may be null on some devices; we return ""
        var focus_value = ""
        when (focus_mode) {
            null -> {
                // ignore, leave focus_value at ""
            }

            Camera.Parameters.FOCUS_MODE_AUTO -> {
                focus_value = "focus_mode_auto"
            }

            Camera.Parameters.FOCUS_MODE_INFINITY -> {
                focus_value = "focus_mode_infinity"
            }

            Camera.Parameters.FOCUS_MODE_MACRO -> {
                focus_value = "focus_mode_macro"
            }

            Camera.Parameters.FOCUS_MODE_FIXED -> {
                focus_value = "focus_mode_fixed"
            }

            Camera.Parameters.FOCUS_MODE_EDOF -> {
                focus_value = "focus_mode_edof"
            }

            Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE -> {
                focus_value = "focus_mode_continuous_picture"
            }

            Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO -> {
                focus_value = "focus_mode_continuous_video"
            }
        }
        return focus_value
    }

    override fun getFocusValue(): String {
        // returns "" if Parameters.getFocusMode() returns null
        val parameters = this.parameters
        val focus_mode = parameters.focusMode
        // getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play
        return convertFocusModeToValue(focus_mode)
    }

    override fun getFocusDistance(): Float {
        // not supported for CameraController1!
        return 0.0f
    }

    override fun setFocusDistance(focus_distance: Float): Boolean {
        // not supported for CameraController1!
        return false
    }

    override fun setFocusBracketingNImages(n_images: Int) {
        // not supported for CameraController1
    }

    override fun setFocusBracketingAddInfinity(focus_bracketing_add_infinity: Boolean) {
        // not supported for CameraController1
    }

    override fun setFocusBracketingSourceDistance(focus_bracketing_source_distance: Float) {
        // not supported for CameraController1!
    }

    override fun getFocusBracketingSourceDistance(): Float {
        // not supported for CameraController1!
        return 0.0f
    }

    override fun setFocusBracketingSourceDistanceFromCurrent() {
        // not supported for CameraController1!
    }

    override fun setFocusBracketingTargetDistance(focus_bracketing_target_distance: Float) {
        // not supported for CameraController1!
    }

    override fun getFocusBracketingTargetDistance(): Float {
        // not supported for CameraController1!
        return 0.0f
    }

    private fun convertFlashValueToMode(flash_value: String): String {
        var flash_mode = ""
        when (flash_value) {
            "flash_off", "flash_frontscreen_on", "flash_frontscreen_torch" -> flash_mode =
                Camera.Parameters.FLASH_MODE_OFF

            "flash_auto" -> flash_mode = Camera.Parameters.FLASH_MODE_AUTO
            "flash_on" -> flash_mode = Camera.Parameters.FLASH_MODE_ON
            "flash_torch" -> flash_mode = Camera.Parameters.FLASH_MODE_TORCH
            "flash_red_eye" -> flash_mode = Camera.Parameters.FLASH_MODE_RED_EYE
        }
        return flash_mode
    }

    override fun setFlashValue(flash_value: String) {
        val parameters = this.parameters
        d(TAG, "setFlashValue: $flash_value")

        this.frontscreen_flash = false
        if (flash_value == "flash_frontscreen_on") {
            // we do this check first due to weird behaviour on Samsung Galaxy S7 front camera where parameters.getFlashMode() returns values (auto, beach, portrait)
            this.frontscreen_flash = true
            return
        }

        if (parameters.flashMode == null) {
            d(TAG, "flash mode not supported")
            return
        }

        val flash_mode = convertFlashValueToMode(flash_value)
        if (flash_mode.isNotEmpty() && flash_mode != parameters.flashMode) {
            if (parameters.flashMode == Camera.Parameters.FLASH_MODE_TORCH && flash_mode != Camera.Parameters.FLASH_MODE_OFF) {
                // workaround for bug on Nexus 5 and Nexus 6 where torch doesn't switch off until we set FLASH_MODE_OFF
                d(TAG, "first turn torch off")
                parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                setCameraParameters(parameters)
                // need to set the correct flash mode after a delay
                val handler = Handler()
                handler.postDelayed({
                    d(TAG, "now set actual flash mode after turning torch off")
                    if (camera != null) { // make sure camera wasn't released in the meantime (has a Google Play crash as a result of this)
                        val parameters: Camera.Parameters = parameters
                        parameters.flashMode = flash_mode
                        setCameraParameters(parameters)
                    }
                }, 100)
            } else {
                parameters.flashMode = flash_mode
                setCameraParameters(parameters)
            }
        }
    }

    private fun convertFlashModeToValue(flash_mode: String?): String {
        // flash_mode may be null, meaning flash isn't supported; we return ""
        d(TAG, "convertFlashModeToValue: $flash_mode")
        var flash_value = ""
        if (flash_mode == null) {
            // ignore, leave focus_value at ""
        } else if (flash_mode == Camera.Parameters.FLASH_MODE_OFF) {
            flash_value = "flash_off"
        } else if (flash_mode == Camera.Parameters.FLASH_MODE_AUTO) {
            flash_value = "flash_auto"
        } else if (flash_mode == Camera.Parameters.FLASH_MODE_ON) {
            flash_value = "flash_on"
        } else if (flash_mode == Camera.Parameters.FLASH_MODE_TORCH) {
            flash_value = "flash_torch"
        } else if (flash_mode == Camera.Parameters.FLASH_MODE_RED_EYE) {
            flash_value = "flash_red_eye"
        }
        return flash_value
    }

    override fun getFlashValue(): String {
        // returns "" if flash isn't supported
        val parameters = this.parameters
        val flash_mode = parameters.flashMode // will be null if flash mode not supported
        return convertFlashModeToValue(flash_mode)
    }

    override fun setRecordingHint(hint: Boolean) {
        d(TAG, "setRecordingHint: " + hint)
        try {
            val parameters = this.parameters
            // Calling setParameters here with continuous video focus mode causes preview to not restart after taking a photo on Galaxy Nexus?! (fine on my Nexus 7).
            // The issue seems to specifically be with setParameters (i.e., the problem occurs even if we don't setRecordingHint).
            // In addition, I had a report of a bug on HTC Desire X, Android 4.0.4 where the saved video was corrupted.
            // This worked fine in 1.7, then not in 1.8 and 1.9, then was fixed again in 1.10
            // The only thing in common to 1.7->1.8 and 1.9-1.10, that seems relevant, was adding this code to setRecordingHint() and setParameters() (unclear which would have been the problem),
            // so we should be very careful about enabling this code again!
            // Update for v1.23: the bug with Galaxy Nexus has come back (see comments in Preview.setPreviewFps()) and is now unavoidable,
            // but I've still kept this check here - if nothing else, because it apparently caused video recording problems on other devices too.
            // Update for v1.29: this doesn't seem to happen on Galaxy Nexus with continuous picture focus mode, which is what we now use; but again, still keepin the check here due to possible problems on other devices
            val focus_mode = parameters.getFocusMode()
            // getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play
            if (focus_mode != null && focus_mode != Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO) {
                parameters.setRecordingHint(hint)
                setCameraParameters(parameters)
            }
        } catch (e: RuntimeException) {
            // can get RuntimeException from getParameters - we don't catch within that function because callers may not be able to recover,
            // but here it doesn't really matter if we fail to set the recording hint
            e(TAG, "setRecordingHint failed to get parameters")
            e.printStackTrace()
            count_camera_parameters_exception++
        }
    }

    override fun setAutoExposureLock(enabled: Boolean) {
        val parameters = this.parameters
        parameters.autoExposureLock = enabled
        setCameraParameters(parameters)
    }

    override fun getAutoExposureLock(): Boolean {
        val parameters = this.parameters
        if (!parameters.isAutoExposureLockSupported) return false
        return parameters.autoExposureLock
    }

    override fun setAutoWhiteBalanceLock(enabled: Boolean) {
        val parameters = this.parameters
        parameters.autoWhiteBalanceLock = enabled
        setCameraParameters(parameters)
    }

    override fun getAutoWhiteBalanceLock(): Boolean {
        val parameters = this.parameters
        if (!parameters.isAutoWhiteBalanceLockSupported) return false
        return parameters.autoWhiteBalanceLock
    }

    override fun setRotation(rotation: Int) {
        val parameters = this.parameters
        parameters.setRotation(rotation)
        setCameraParameters(parameters)
    }

    override fun setLocationInfo(location: Location) {
        // don't log location, in case of privacy!
        d(TAG, "setLocationInfo")
        val parameters = this.parameters
        parameters.removeGpsData()
        parameters.setGpsTimestamp(System.currentTimeMillis() / 1000) // initialise to a value (from Android camera source)
        parameters.setGpsLatitude(location.latitude)
        parameters.setGpsLongitude(location.longitude)
        parameters.setGpsProcessingMethod(location.provider) // from http://boundarydevices.com/how-to-write-an-android-camera-app/
        if (location.hasAltitude()) {
            parameters.setGpsAltitude(location.altitude)
        } else {
            // Android camera source claims we need to fake one if not present
            // and indeed, this is needed to fix crash on Nexus 7
            parameters.setGpsAltitude(0.0)
        }
        if (location.time != 0L) { // from Android camera source
            parameters.setGpsTimestamp(location.time / 1000)
        }
        setCameraParameters(parameters)
    }

    override fun removeLocationInfo() {
        val parameters = this.parameters
        parameters.removeGpsData()
        setCameraParameters(parameters)
    }

    override fun enableShutterSound(enabled: Boolean) {
        camera!!.enableShutterSound(enabled)
        sounds_enabled = enabled
    }

    override fun setFocusAndMeteringArea(areas: MutableList<Area>): Boolean {
        val camera_areas: MutableList<Camera.Area?> = ArrayList<Camera.Area?>()
        for (area in areas) {
            camera_areas.add(Camera.Area(area.rect, area.weight))
        }
        try {
            val parameters = this.parameters
            val focus_mode = parameters.focusMode
            // getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play
            if (parameters.maxNumFocusAreas != 0 && focus_mode != null && (focus_mode == Camera.Parameters.FOCUS_MODE_AUTO || focus_mode == Camera.Parameters.FOCUS_MODE_MACRO || focus_mode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE || focus_mode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.focusAreas = camera_areas

                // also set metering areas
                if (parameters.maxNumMeteringAreas == 0) {
                    d(TAG, "metering areas not supported")
                } else {
                    parameters.meteringAreas = camera_areas
                }

                setCameraParameters(parameters)

                return true
            } else if (parameters.maxNumMeteringAreas != 0) {
                parameters.meteringAreas = camera_areas

                setCameraParameters(parameters)
            } else {
                d(TAG, "metering areas not supported")
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            count_camera_parameters_exception++
        }
        return false
    }

    override fun clearFocusAndMetering() {
        try {
            val parameters = this.parameters
            var update_parameters = false
            if (parameters.maxNumFocusAreas > 0) {
                parameters.focusAreas = null
                update_parameters = true
            }
            if (parameters.maxNumMeteringAreas > 0) {
                parameters.meteringAreas = null
                update_parameters = true
            }
            if (update_parameters) {
                setCameraParameters(parameters)
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            count_camera_parameters_exception++
        }
    }

    override fun getFocusAreas(): MutableList<Area?>? {
        val parameters = this.parameters
        val camera_areas = parameters.focusAreas
        if (camera_areas == null) return null
        val areas: MutableList<Area?> = ArrayList()
        for (camera_area in camera_areas) {
            areas.add(Area(camera_area.rect, camera_area.weight))
        }
        return areas
    }

    override fun getMeteringAreas(): MutableList<Area?>? {
        val parameters = this.parameters
        val camera_areas = parameters.meteringAreas
        if (camera_areas == null) return null
        val areas: MutableList<Area?> = ArrayList()
        for (camera_area in camera_areas) {
            areas.add(Area(camera_area.rect, camera_area.weight))
        }
        return areas
    }

    override fun supportsAutoFocus(): Boolean {
        try {
            val parameters = this.parameters
            val focus_mode = parameters.focusMode
            // getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play from the below line (v1.7),
            // on Galaxy Tab 10.1 (GT-P7500), Android 4.0.3 - 4.0.4; HTC EVO 3D X515m (shooteru), Android 4.0.3 - 4.0.4
            if (focus_mode != null && (focus_mode == Camera.Parameters.FOCUS_MODE_AUTO || focus_mode == Camera.Parameters.FOCUS_MODE_MACRO)) {
                return true
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            count_camera_parameters_exception++
        }
        return false
    }

    override fun supportsMetering(): Boolean {
        try {
            val parameters = this.parameters
            return parameters.getMaxNumMeteringAreas() > 0
        } catch (e: RuntimeException) {
            e.printStackTrace()
            count_camera_parameters_exception++
        }
        return false
    }

    override fun focusIsContinuous(): Boolean {
        try {
            val parameters = this.parameters
            val focus_mode = parameters.getFocusMode()
            // getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play from the below line (v1.7),
            // on Galaxy Tab 10.1 (GT-P7500), Android 4.0.3 - 4.0.4; HTC EVO 3D X515m (shooteru), Android 4.0.3 - 4.0.4
            if (focus_mode != null && (focus_mode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE || focus_mode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                return true
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
            count_camera_parameters_exception++
        }
        return false
    }

    override fun focusIsVideo(): Boolean {
        val parameters = this.parameters
        val current_focus_mode = parameters.getFocusMode()
        // getFocusMode() is documented as never returning null, however I've had null pointer exceptions reported in Google Play
        val focus_is_video =
            current_focus_mode != null && current_focus_mode == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        if (MyDebug.LOG) {
            d(TAG, "current_focus_mode: " + current_focus_mode)
            d(TAG, "focus_is_video: " + focus_is_video)
        }
        return focus_is_video
    }

    @Throws(CameraControllerException::class)
    override fun reconnect() {
        d(TAG, "reconnect")
        try {
            camera!!.reconnect()
        } catch (e: IOException) {
            e(TAG, "reconnect threw IOException")
            e.printStackTrace()
            throw CameraControllerException()
        }
    }

    @Throws(CameraControllerException::class)
    override fun setPreviewDisplay(holder: SurfaceHolder?) {
        d(TAG, "setPreviewDisplay")
        try {
            camera!!.setPreviewDisplay(holder)
        } catch (e: IOException) {
            e.printStackTrace()
            throw CameraControllerException()
        }
    }

    @Throws(CameraControllerException::class)
    override fun setPreviewTexture(texture: TextureView) {
        d(TAG, "setPreviewTexture")
        try {
            camera!!.setPreviewTexture(texture.getSurfaceTexture())
        } catch (e: IOException) {
            e.printStackTrace()
            throw CameraControllerException()
        }
    }

    @Throws(CameraControllerException::class)
    override fun startPreview() {
        d(TAG, "startPreview")
        try {
            camera!!.startPreview()
        } catch (e: RuntimeException) {
            e(TAG, "failed to start preview")
            e.printStackTrace()
            throw CameraControllerException()
        }
    }

    override fun stopRepeating() {
        // not relevant for old camera API
    }

    override fun stopPreview() {
        if (camera != null) {
            // have had crashes when this is called from Preview/CloseCameraTask.
            camera!!.stopPreview()
        }
    }

    // returns false if RuntimeException thrown (may include if face-detection already started)
    override fun startFaceDetection(): Boolean {
        d(TAG, "startFaceDetection")
        try {
            camera!!.startFaceDetection()
        } catch (e: RuntimeException) {
            d(TAG, "face detection failed or already started")
            count_camera_parameters_exception++
            return false
        }
        return true
    }

    override fun setFaceDetectionListener(listener: FaceDetectionListener?) {
        if (listener != null) {
            class CameraFaceDetectionListener : Camera.FaceDetectionListener {
                override fun onFaceDetection(camera_faces: Array<Camera.Face?>, camera: Camera?) {
                    val faces = arrayOfNulls<Face>(camera_faces.size)
                    for (i in camera_faces.indices) {
                        faces[i] = Face(camera_faces[i]!!.score, camera_faces[i]!!.rect)
                    }
                    listener.onFaceDetection(faces)
                }
            }
            camera!!.setFaceDetectionListener(CameraFaceDetectionListener())
        } else {
            camera!!.setFaceDetectionListener(null)
        }
    }

    override fun autoFocus(cb: AutoFocusCallback, capture_follows_autofocus_hint: Boolean) {
        d(TAG, "autoFocus")
        class MyAutoFocusCallback : Camera.AutoFocusCallback {
            var done_autofocus: Boolean = false
            val handler = Handler()
            val runnable: Runnable = object : Runnable {
                override fun run() {
                    d(TAG, "autofocus timeout check")
                    autofocus_timeout_runnable = null
                    autofocus_timeout_handler = null
                    if (!done_autofocus) {
                        e(TAG, "autofocus timeout!")
                        done_autofocus = true
                        cb.onAutoFocus(false)
                    }
                }
            }

            fun setTimeout() {
                handler.postDelayed(runnable, 2000) // set autofocus timeout
            }

            override fun onAutoFocus(success: Boolean, camera: Camera?) {
                d(TAG, "autoFocus.onAutoFocus")
                handler.removeCallbacks(runnable)
                autofocus_timeout_runnable = null
                autofocus_timeout_handler = null
                // in theory we should only ever get one call to onAutoFocus(), but some Samsung phones at least can call the callback multiple times
                // see http://stackoverflow.com/questions/36316195/take-picture-fails-on-samsung-phones
                // needed to fix problem on Samsung S7 with flash auto/on and continuous picture focus where it would claim failed to take picture even though it'd succeeded,
                // because we repeatedly call takePicture(), and the subsequent ones cause a runtime exception
                // update: also the done_autofocus flag is needed in case we had an autofocus timeout, see above
                if (!done_autofocus) {
                    done_autofocus = true
                    cb.onAutoFocus(success)
                } else {
                    e(TAG, "ignore repeated autofocus")
                }
            }
        }

        val camera_cb = MyAutoFocusCallback()
        autofocus_timeout_handler = camera_cb.handler
        autofocus_timeout_runnable = camera_cb.runnable

        try {
            camera_cb.setTimeout()
            camera!!.autoFocus(camera_cb)
        } catch (e: RuntimeException) {
            // just in case? We got a RuntimeException report here from 1 user on Google Play:
            // 21 Dec 2013, Xperia Go, Android 4.1
            e(TAG, "runtime exception from autoFocus")
            e.printStackTrace()
            if (autofocus_timeout_handler != null) {
                if (autofocus_timeout_runnable != null) {
                    autofocus_timeout_handler!!.removeCallbacks(autofocus_timeout_runnable!!)
                    autofocus_timeout_runnable = null
                }
                autofocus_timeout_handler = null
            }
            // should call the callback, so the application isn't left waiting (e.g., when we autofocus before trying to take a photo)
            cb.onAutoFocus(false)
        }
    }

    override fun setCaptureFollowAutofocusHint(capture_follows_autofocus_hint: Boolean) {
        // unused by this API
    }

    override fun cancelAutoFocus() {
        try {
            camera!!.cancelAutoFocus()
            if (autofocus_timeout_handler != null) {
                if (autofocus_timeout_runnable != null) {
                    // so we don't trigger autofocus timeout
                    autofocus_timeout_handler!!.removeCallbacks(autofocus_timeout_runnable!!)
                    autofocus_timeout_runnable = null
                }
                autofocus_timeout_handler = null
            }
        } catch (e: RuntimeException) {
            // had a report of crash on some devices, see comment at https://sourceforge.net/p/opencamera/tickets/4/ made on 20140520
            d(TAG, "cancelAutoFocus() failed")
            e.printStackTrace()
        }
    }

    override fun setContinuousFocusMoveCallback(cb: ContinuousFocusMoveCallback?) {
        d(TAG, "setContinuousFocusMoveCallback")
        run {
            try {
                if (cb != null) {
                    camera!!.setAutoFocusMoveCallback(object : AutoFocusMoveCallback {
                        override fun onAutoFocusMoving(start: Boolean, camera: Camera?) {
                            d(TAG, "onAutoFocusMoving: " + start)
                            cb.onContinuousFocusMove(start)
                        }
                    })
                } else {
                    camera!!.setAutoFocusMoveCallback(null)
                }
            } catch (e: RuntimeException) {
                // received RuntimeException reports from some users on Google Play - seems to be older devices, but still important to catch!
                e(TAG, "runtime exception from setAutoFocusMoveCallback")
                e.printStackTrace()
            }
        }
    }

    private class TakePictureShutterCallback : ShutterCallback {
        // don't do anything here, but we need to implement the callback to get the shutter sound (at least on Galaxy Nexus and Nexus 7)
        override fun onShutter() {
            d(TAG, "shutterCallback.onShutter()")
        }
    }

    private fun clearPending() {
        d(TAG, "clearPending")
        pending_burst_images.clear()
        burst_exposures = null
        n_burst = 0
    }

    private fun takePictureNow(picture: PictureCallback?, error: ErrorCallback) {
        d(TAG, "takePictureNow")

        // only set the shutter callback if sounds enabled
        val shutter: ShutterCallback? = if (sounds_enabled) TakePictureShutterCallback() else null
        val camera_jpeg: Camera.PictureCallback? =
            if (picture == null) null else object : Camera.PictureCallback {
                override fun onPictureTaken(data: ByteArray?, cam: Camera?) {
                    d(TAG, "onPictureTaken")

                    // n.b., this is automatically run in a different thread
                    if (want_expo_bracketing && n_burst > 1) {
                        pending_burst_images.add(data)
                        if (pending_burst_images.size >= n_burst) { // shouldn't ever be greater, but just in case
                            d(TAG, "all burst images available")
                            if (pending_burst_images.size > n_burst) {
                                e(
                                    TAG,
                                    "pending_burst_images size " + pending_burst_images.size + " is greater than n_burst " + n_burst
                                )
                            }

                            // set exposure compensation back to original
                            setExposureCompensation(burst_exposures!!.get(0)!!)

                            // take a copy, so that we can clear pending_burst_images
                            // also allows us to reorder from dark to light
                            // since we took the images with the base exposure being first
                            val n_half_images = pending_burst_images.size / 2
                            val images: MutableList<ByteArray?> = ArrayList<ByteArray?>()
                            // darker images
                            for (i in 0..<n_half_images) {
                                images.add(pending_burst_images.get(i + 1))
                            }
                            // base image
                            images.add(pending_burst_images.get(0))
                            // lighter images
                            for (i in 0..<n_half_images) {
                                images.add(pending_burst_images.get(n_half_images + 1))
                            }

                            picture.onBurstPictureTaken(images)
                            pending_burst_images.clear()
                            picture.onCompleted()
                        } else {
                            d(TAG, "number of burst images is now: " + pending_burst_images.size)
                            // set exposure compensation for next image
                            setExposureCompensation(burst_exposures!!.get(pending_burst_images.size)!!)

                            // need to start preview again: otherwise fail to take subsequent photos on Nexus 6
                            // and Nexus 7; on Galaxy Nexus we succeed, but exposure compensation has no effect
                            try {
                                startPreview()
                            } catch (e: CameraControllerException) {
                                d(TAG, "CameraControllerException trying to startPreview")
                                e.printStackTrace()
                            }

                            val handler = Handler()
                            handler.postDelayed(object : Runnable {
                                override fun run() {
                                    d(TAG, "take picture after delay for next expo")
                                    if (camera != null) { // make sure camera wasn't released in the meantime
                                        takePictureNow(picture, error)
                                    }
                                }
                            }, 1000)
                        }
                    } else {
                        picture.onPictureTaken(data)
                        picture.onCompleted()
                    }
                }
            }

        if (picture != null) {
            d(TAG, "call onStarted() in callback")
            picture.onStarted()
        }
        try {
            camera!!.takePicture(shutter, null, camera_jpeg)
        } catch (e: RuntimeException) {
            // just in case? We got a RuntimeException report here from 1 user on Google Play; I also encountered it myself once of Galaxy Nexus when starting up
            e(TAG, "runtime exception from takePicture")
            e.printStackTrace()
            error.onError()
        }
    }

    override fun takePicture(picture: PictureCallback, error: ErrorCallback) {
        d(TAG, "takePicture")

        clearPending()
        if (want_expo_bracketing) {
            d(TAG, "set up expo bracketing")
            val parameters = this.parameters
            val n_half_images = expo_bracketing_n_images / 2
            val min_exposure = parameters.getMinExposureCompensation()
            val max_exposure = parameters.getMaxExposureCompensation()
            var exposure_step = this.exposureCompensationStep
            if (exposure_step == 0.0f)  // just in case?
                exposure_step = 1.0f / 3.0f // make up a typical example

            val exposure_current = getExposureCompensation()
            val stops_per_image = expo_bracketing_stops / n_half_images.toDouble()
            var steps =
                ((stops_per_image + 1.0e-5) / exposure_step).toInt() // need to add a small amount, otherwise we can round down
            steps = max(steps, 1)
            if (MyDebug.LOG) {
                d(TAG, "steps: " + steps)
                d(TAG, "exposure_current: " + exposure_current)
            }

            val requests: MutableList<Int?> = ArrayList<Int?>()

            // do the current exposure first, so we can take the first shot immediately
            // if we change the order, remember to update the code that re-orders for passing resultant images back to picture.onBurstPictureTaken()
            requests.add(exposure_current)

            // darker images
            for (i in 0..<n_half_images) {
                var exposure = exposure_current - (n_half_images - i) * steps
                exposure = max(exposure, min_exposure)
                requests.add(exposure)
                if (MyDebug.LOG) {
                    d(TAG, "add burst request for " + i + "th dark image:")
                    d(TAG, "exposure: " + exposure)
                }
            }

            // lighter images
            for (i in 0..<n_half_images) {
                var exposure = exposure_current + (i + 1) * steps
                exposure = min(exposure, max_exposure)
                requests.add(exposure)
                if (MyDebug.LOG) {
                    d(TAG, "add burst request for " + i + "th light image:")
                    d(TAG, "exposure: " + exposure)
                }
            }

            burst_exposures = requests
            n_burst = requests.size
        }

        if (frontscreen_flash) {
            d(TAG, "front screen flash")
            picture.onFrontScreenTurnOn()
            // take picture after a delay, to allow autoexposure and autofocus to update (unlike CameraController2, we can't tell when this happens, so we just wait for a fixed delay)
            val handler = Handler()
            handler.postDelayed(object : Runnable {
                override fun run() {
                    d(TAG, "take picture after delay for front screen flash")
                    if (camera != null) { // make sure camera wasn't released in the meantime
                        takePictureNow(picture, error)
                    }
                }
            }, 1000)
            return
        }
        takePictureNow(picture, error)
    }

    override fun setDisplayOrientation(degrees: Int) {
        // see http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
        var result: Int
        if (camera_info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (camera_info.orientation + degrees) % 360
            result = (360 - result) % 360
        } else {
            result = (camera_info.orientation - degrees + 360) % 360
        }
        if (MyDebug.LOG) {
            d(TAG, "    info orientation is " + camera_info.orientation)
            d(TAG, "    setDisplayOrientation to " + result)
        }

        try {
            camera!!.setDisplayOrientation(result)
        } catch (e: RuntimeException) {
            // unclear why this happens, but have had crashes from Google Play...
            e(TAG, "failed to set display orientation")
            e.printStackTrace()
        }
        this.display_orientation = result
    }

    override fun getDisplayOrientation(): Int {
        return this.display_orientation
    }

    override fun getCameraOrientation(): Int {
        return camera_info.orientation
    }

    override fun getFacing(): Facing {
        when (camera_info.facing) {
            Camera.CameraInfo.CAMERA_FACING_FRONT -> return Facing.FACING_FRONT
            Camera.CameraInfo.CAMERA_FACING_BACK -> return Facing.FACING_BACK
        }
        e(TAG, "unknown camera_facing: " + camera_info.facing)
        return Facing.FACING_UNKNOWN
    }

    override fun unlock() {
        this.stopPreview() // although not documented, we need to stop preview to prevent device freeze or video errors shortly after video recording starts on some devices (e.g., device freeze on Samsung Galaxy S2 - I could reproduce this on Samsung RTL; also video recording fails and preview becomes corrupted on Galaxy S3 variant "SGH-I747-US2"); also see http://stackoverflow.com/questions/4244999/problem-with-video-recording-after-auto-focus-in-android
        camera!!.unlock()
    }

    override fun initVideoRecorderPrePrepare(video_recorder: MediaRecorder) {
        video_recorder.setCamera(camera)
    }

    override fun initVideoRecorderPostPrepare(
        video_recorder: MediaRecorder?, want_photo_video_recording: Boolean
    ) {
        // no further actions necessary
    }

    override fun getParametersString(): String? {
        var string: String? = ""
        try {
            string = this.parameters.flatten()
        } catch (e: Exception) {
            // received a StringIndexOutOfBoundsException from beneath getParameters().flatten() on Google Play!
            e(TAG, "exception from getParameters().flatten()")
            e.printStackTrace()
        }
        return string
    }

    companion object {
        private const val TAG = "CameraController1"

        private const val max_expo_bracketing_n_images =
            3 // seem to have problems with 5 images in some cases, e.g., images coming out same brightness on OnePlus 3T
    }
}
