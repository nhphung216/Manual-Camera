package com.ssolstice.camera.manual

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Fragment
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Matrix
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.PreferenceFragment.OnPreferenceStartFragmentCallback
import android.preference.PreferenceManager
import android.provider.MediaStore
import android.renderscript.RenderScript
import android.speech.tts.TextToSpeech
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.util.Log
import android.util.SizeF
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import com.ssolstice.camera.manual.MyApplicationInterface.PhotoMode
import com.ssolstice.camera.manual.billing.BillingManager
import com.ssolstice.camera.manual.billing.BillingManager.ActiveSubscription
import com.ssolstice.camera.manual.billing.BillingManager.BillingProduct
import com.ssolstice.camera.manual.cameracontroller.CameraController
import com.ssolstice.camera.manual.cameracontroller.CameraController.EXPOSURE_TIME_DEFAULT
import com.ssolstice.camera.manual.cameracontroller.CameraController.Facing
import com.ssolstice.camera.manual.cameracontroller.CameraController.TonemapProfile
import com.ssolstice.camera.manual.cameracontroller.CameraControllerManager
import com.ssolstice.camera.manual.cameracontroller.CameraControllerManager2
import com.ssolstice.camera.manual.compose.CameraControls
import com.ssolstice.camera.manual.compose.CameraScreen
import com.ssolstice.camera.manual.compose.CameraSettings
import com.ssolstice.camera.manual.compose.CameraViewModel
import com.ssolstice.camera.manual.compose.ui.theme.OpenCameraTheme
import com.ssolstice.camera.manual.databinding.ActivityMainBinding
import com.ssolstice.camera.manual.preview.Preview
import com.ssolstice.camera.manual.remotecontrol.BluetoothRemoteControl
import com.ssolstice.camera.manual.ui.DrawPreview
import com.ssolstice.camera.manual.ui.FolderChooserDialog
import com.ssolstice.camera.manual.ui.MainUI
import com.ssolstice.camera.manual.ui.ManualSeekbars
import com.ssolstice.camera.manual.utils.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.DecimalFormat
import java.util.Arrays
import java.util.Collections
import java.util.Hashtable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), OnPreferenceStartFragmentCallback {

    private val TAG = "PreferencesListener"

    lateinit var binding: ActivityMainBinding

    private val viewModel: CameraViewModel by viewModels()

    var isAppPaused: Boolean = true
        private set

    private var mSensorManager: SensorManager? = null
    private var mSensorAccelerometer: Sensor? = null

    // components: always non-null (after onCreate())
    private var bluetoothRemoteControl: BluetoothRemoteControl? = null
    private var permissionHandler: PermissionHandler? = null
    private var settingsManager: SettingsManager? = null

    var mainUI: MainUI? = null
        private set

    private var manualSeekbars: ManualSeekbars? = null

    var applicationInterface: MyApplicationInterface? = null
        private set

    var textFormatter: TextFormatter? = null
        private set

    private var soundPoolManager: SoundPoolManager? = null
    private var magneticSensor: MagneticSensor? = null

    // if we change this, remember that any page linked to must abide by Google Play developer policies!
    //public static final String DonateLink = "https://play.google.com/store/apps/details?id=harman.mark.donation";
    //private SpeechControl speechControl;
    var preview: Preview? = null
        private set

    private var orientationEventListener: OrientationEventListener? = null
    private var layoutChangeListener: OnLayoutChangeListener? = null
    private var large_heap_memory = 0
    private var supports_auto_stabilise = false
    private var supports_force_video_4k = false
    private var supports_camera2 = false
    private var save_location_history: SaveLocationHistory? = null // save location for non-SAF
    var saveLocationHistorySAF: SaveLocationHistory? =
        null // save location for SAF (only initialised when SAF is used)
        private set

    private var saf_dialog_from_preferences =
        false // if a SAF dialog is opened, this records whether we opened it from the Preferences

    var isCameraInBackground: Boolean =
        false // whether the camera is covered by a fragment/dialog (such as settings or folder picker)
        private set

    private var gestureDetector: GestureDetector? = null

    /** Whether the screen is locked (see lockScreen()).
     */
    var isScreenLocked: Boolean =
        false // whether screen is "locked" - this is ManualCamera's own lock to guard against accidental presses, not the standard Android lock
        private set
    private val preloaded_bitmap_resources: MutableMap<Int?, Bitmap?> = Hashtable<Int?, Bitmap?>()
    private var gallery_save_anim: ValueAnimator? = null
    private var last_continuous_fast_burst =
        false // whether the last photo operation was a continuous_fast_burst
    private var update_gallery_future: Future<*>? = null

    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechSuccess = false

    private var audio_listener: AudioListener? = null // may be null - created when needed

    /** Returns whether we are always running in edge-to-edge mode. (If false, we may still sometimes
     * run edge-to-edge.)
     */
    //private boolean ui_placement_right = true;
    //private final boolean edge_to_edge_mode = false; // whether running always in edge-to-edge mode
    //private final boolean edge_to_edge_mode = true; // whether running always in edge-to-edge mode
    val edgeToEdgeMode: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM // whether running always in edge-to-edge mode
    private var want_no_limits = false // whether we want to run with FLAG_LAYOUT_NO_LIMITS
    private var set_window_insets_listener =
        false // whether we've enabled a setOnApplyWindowInsetsListener()
    private var navigation_gap =
        0 // gap for navigation bar along bottom (portrait) or right (landscape)
    private var navigation_gap_landscape =
        0 // gap for navigation bar along left (portrait) or bottom (landscape); only set for edge_to_edge_mode==true
    private var navigation_gap_reverse_landscape =
        0 // gap for navigation bar along right (portrait) or top (landscape); only set for edge_to_edge_mode==true

    @JvmField
    @Volatile
    var test_set_show_under_navigation: Boolean =
        false // test flag, the value of enable for the last call of showUnderNavigation() (or false if not yet called)

    /** Whether this is a multi camera device, whether or not the user preference is set to enable
     * the multi-camera button.
     */
    // whether this is a multi-camera device (note, this isn't simply having more than 1 camera, but also having more than one with the same facing)
    // note that in most cases, code should check the MultiCamButtonPreferenceKey preference as well as the is_multi_cam flag,
    // this can be done via isMultiCamEnabled().
    var isMultiCam: Boolean = false
        private set

    // These lists are lists of camera IDs with the same "facing" (front, back or external).
    // Only initialised if is_multi_cam==true.
    private var back_camera_ids: MutableList<Int?>? = null
    private var frontCameraIds: MutableList<Int?>? = null
    private var otherCameraIds: MutableList<Int?>? = null

    private val switch_video_toast = ToastBoxer()
    private val screen_locked_toast = ToastBoxer()
    private val stamp_toast = ToastBoxer()
    val changedAutoStabiliseToastBoxer: ToastBoxer = ToastBoxer()
    private val white_balance_lock_toast = ToastBoxer()
    private val exposure_lock_toast = ToastBoxer()
    val audioControlToast: ToastBoxer = ToastBoxer()
    private val store_location_toast = ToastBoxer()
    private var block_startup_toast =
        false // used when returning from Settings/Popup - if we're displaying a toast anyway, don't want to display the info toast too
    private var push_info_toast_text: String? =
        null // can be used to "push" extra text to the info text for showPhotoVideoToast()
//    private var pushSwitchedCamera =
//        false // whether to display animation for switching front/back cameras

    // for testing; must be volatile for test project reading the state
    // n.b., avoid using static, as static variables are shared between different instances of an application,
    // and won't be reset in subsequent tests in a suite!
    @JvmField
    var is_test: Boolean = false // whether called from ManualCamera.test testing

    @JvmField
    @Volatile
    var gallery_bitmap: Bitmap? = null

    @JvmField
    @Volatile
    var test_low_memory: Boolean = false

    @JvmField
    @Volatile
    var test_have_angle: Boolean = false

    @JvmField
    @Volatile
    var test_angle: Float = 0f

    @JvmField
    @Volatile
    var test_last_saved_imageuri: Uri? =
        null // uri of last image; set if using scoped storage OR using SAF

    @JvmField
    @Volatile
    var test_last_saved_image: String? =
        null // filename (including full path) of last image; set if not using scoped storage nor using SAF (i.e., writing using File API)

    @JvmField
    @Volatile
    var test_save_settings_file: String? = null

    var waterDensity: Float = 1.0f
        private set

    // handling for lock_to_landscape==false:
    enum class SystemOrientation {
        LANDSCAPE, PORTRAIT, REVERSE_LANDSCAPE
    }

    private var displayListener: MyDisplayListener? = null

    private var has_cached_system_orientation = false
    private var cached_system_orientation: SystemOrientation? = null

    private var hasOldSystemOrientation = false
    private var oldSystemOrientation: SystemOrientation? = null

    private var has_cached_display_rotation = false
    private var cached_display_rotation_time_ms: Long = 0
    private var cached_display_rotation = 0

    // mapping from exposure_seekbar progress value to preview exposure compensation
    var exposure_seekbar_values: MutableList<Int?>? = null

    // index in exposure_seekbar_values that maps to zero preview exposure compensation
    var exposureSeekbarProgressZero: Int = 0
        private set

    var billingManager: BillingManager? = null

    fun doUpgrade() {
        billingManager?.doUpgrade(this)
    }

    fun isPremiumUser(): Boolean {
        return getSharedPreferences(BillingManager.PREF_BILLING_NAME, MODE_PRIVATE).getBoolean(
            BillingManager.PREF_PREMIUM_KEY, false
        )
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase))
    }

    @SuppressLint("ClickableViewAccessibility", "UseKtx")
    override fun onCreate(savedInstanceState: Bundle?) {
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            Log.d(TAG, "onCreate: $this")
            debug_time = System.currentTimeMillis()
        }
        activity_count++
        if (MyDebug.LOG) Log.d(TAG, "activity_count: $activity_count")
        //EdgeToEdge.enable(this, SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT)); // test edge-to-edge on pre-Android 15
        super.onCreate(savedInstanceState)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // 2. Gán binding trong onCreate
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())

        billingManager = BillingManager(this, {

        }, {
            billingManager?.queryProducts { products: MutableList<BillingProduct?>? ->
                Log.e(MainActivity.TAG, "products " + products!!.size)
            }
        }, {})

        billingManager!!.queryAllPurchases { active: ActiveSubscription? ->
            if (active != null) {
                Log.e(MainActivity.TAG, "User is Premium")
                binding.upgrade.visibility = View.GONE
            } else {
                Log.e(MainActivity.TAG, "User is Freemium")
                binding.upgrade.visibility = View.VISIBLE
            }
        }
        binding.upgrade.visibility = if (isPremiumUser()) View.GONE else View.VISIBLE

        PreferenceManager.setDefaultValues(
            this, R.xml.preferences, false
        ) // initialise any unset preferences to their default values
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after setting default preference values: " + (System.currentTimeMillis() - debug_time)
        )

        if (intent != null && intent.extras != null) {
            // whether called from testing
            is_test = intent.extras!!.getBoolean("test_project")
            if (MyDebug.LOG) Log.d(TAG, "is_test: $is_test")
        }
        if (MyDebug.LOG) {
            // whether called from Take Photo widget
            Log.d(TAG, "take_photo?: " + TakePhoto.TAKE_PHOTO)
        }
        if (intent != null && intent.action != null) {
            // invoked via the manifest shortcut?
            if (MyDebug.LOG) Log.d(TAG, "shortcut: " + intent.action)
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // determine whether we should support "auto stabilise" feature
        // risk of running out of memory on lower end devices, due to manipulation of large bitmaps
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (MyDebug.LOG) {
            Log.d(TAG, "large max memory = " + activityManager.largeMemoryClass + "MB")
        }
        large_heap_memory = activityManager.largeMemoryClass
        if (large_heap_memory >= 128) {
            supports_auto_stabilise = true
        }
        if (MyDebug.LOG) Log.d(TAG, "supports_auto_stabilise? $supports_auto_stabilise")

        // hack to rule out phones unlikely to have 4K video, so no point even offering the option!
        // both S5 and Note 3 have 128MB standard and 512MB large heap (tested via Samsung RTL), as does Galaxy K Zoom
        if (activityManager.largeMemoryClass >= 512) {
            supports_force_video_4k = true
        }
        if (MyDebug.LOG) Log.d(TAG, "supports_force_video_4k? $supports_force_video_4k")

        // set up components
        bluetoothRemoteControl = BluetoothRemoteControl(this)
        permissionHandler = PermissionHandler(this)
        settingsManager = SettingsManager(this)
        mainUI = MainUI(this, viewModel)
        manualSeekbars = ManualSeekbars()
        applicationInterface = MyApplicationInterface(this, savedInstanceState)
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after creating application interface: " + (System.currentTimeMillis() - debug_time)
        )
        textFormatter = TextFormatter(this)
        soundPoolManager = SoundPoolManager(this)
        magneticSensor = MagneticSensor(this)

        //speechControl = new SpeechControl(this);

        // determine whether we support Camera2 API
        // must be done before setDeviceDefaults()
        initCamera2Support()

        // set some per-device defaults
        // must be done before creating the Preview (as setDeviceDefaults() may set Camera2 API)
        val has_done_first_time = sharedPreferences.contains(PreferenceKeys.FirstTimePreferenceKey)
        if (MyDebug.LOG) Log.d(TAG, "has_done_first_time: $has_done_first_time")
        if (!has_done_first_time) {
            // must be done after initCamera2Support()
            setDeviceDefaults()
        }

        val settings_is_open = settingsIsOpen()
        if (MyDebug.LOG) Log.d(TAG, "settings_is_open?: $settings_is_open")
        // settings_is_open==true can happen if application is recreated when settings is open
        // to reproduce: go to settings, then turn screen off and on (and unlock)
        if (!settings_is_open) {
            // set up window flags for normal operation
            setWindowFlagsForCamera()
        }
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after setting window flags: " + (System.currentTimeMillis() - debug_time)
        )

        save_location_history = SaveLocationHistory(
            this,
            PreferenceKeys.SaveLocationHistoryBasePreferenceKey,
            this.storageUtils!!.getSaveLocation()
        )
        checkSaveLocations()
        if (applicationInterface!!.storageUtils.isUsingSAF()) {
            if (MyDebug.LOG) Log.d(TAG, "create new SaveLocationHistory for SAF")
            this.saveLocationHistorySAF = SaveLocationHistory(
                this,
                PreferenceKeys.SaveLocationHistorySAFBasePreferenceKey,
                this.storageUtils!!.getSaveLocationSAF()
            )
        }
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after updating folder history: " + (System.currentTimeMillis() - debug_time)
        )

        // set up sensors
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // accelerometer sensor (for device orientation)
        if (mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            if (MyDebug.LOG) Log.d(TAG, "found accelerometer")
            mSensorAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            if (MyDebug.LOG) Log.d(TAG, "no support for accelerometer")
        }
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after creating accelerometer sensor: " + (System.currentTimeMillis() - debug_time)
        )

        // magnetic sensor (for compass direction)
        magneticSensor!!.initSensor(mSensorManager)
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after creating magnetic sensor: " + (System.currentTimeMillis() - debug_time)
        )

        // clear any seek bars (just in case??)
        mainUI?.closeExposureUI()

        // set up the camera and its preview
        preview = Preview(applicationInterface, binding.preview)
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after creating preview: " + (System.currentTimeMillis() - debug_time)
        )

        if (settings_is_open) {
            // must be done after creating preview
            setWindowFlagsForSettings()
        }

        run {
            // don't show orientation animations
            // must be done after creating Preview (so we know if Camera2 API or not)
            val layout = window.attributes
            // If locked to landscape, ROTATION_ANIMATION_SEAMLESS/JUMPCUT has the problem that when going to
            // Settings in portrait, we briefly see the UI change - this is because we set the flag
            // to no longer lock to landscape, and that change happens too quickly.
            // This isn't a problem when lock_to_landscape==false, and we want
            // ROTATION_ANIMATION_SEAMLESS so that there is no/minimal pause from the preview when
            // rotating the device. However if using old camera API, we get an ugly transition with
            // ROTATION_ANIMATION_SEAMLESS (probably related to not using TextureView?)
            if (preview?.usingCamera2API() == false) layout.rotationAnimation =
                WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) layout.rotationAnimation =
                WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS
            else layout.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT
            window.attributes = layout
        }

        // Setup multi-camera buttons (must be done after creating preview so we know which Camera API is being used,
        // and before initialising on-screen visibility).
        // We only allow the separate icon for switching cameras if:
        // - there are at least 2 types of "facing" camera, and
        // - there are at least 2 cameras with the same "facing".
        // If there are multiple cameras but all with different "facing", then the switch camera
        // icon is used to iterate over all cameras.
        // If there are more than two cameras, but all cameras have the same "facing, we still stick
        // with using the switch camera icon to iterate over all cameras.
        val nCameras = preview?.cameraControllerManager?.getNumberOfCameras() ?: 0
        if (nCameras > 2) {
            this.back_camera_ids = ArrayList()
            this.frontCameraIds = ArrayList()
            this.otherCameraIds = ArrayList()
            for (i in 0..<nCameras) {
                when (preview?.cameraControllerManager?.getFacing(i)) {
                    Facing.FACING_BACK -> back_camera_ids!!.add(i)
                    Facing.FACING_FRONT -> frontCameraIds!!.add(i)
                    else ->                         // we assume any unknown cameras are also external
                        otherCameraIds!!.add(i)
                }
            }
            val multi_same_facing =
                back_camera_ids!!.size >= 2 || frontCameraIds!!.size >= 2 || otherCameraIds!!.size >= 2
            var n_facing = 0
            if (back_camera_ids!!.size > 0) n_facing++
            if (frontCameraIds!!.size > 0) n_facing++
            if (otherCameraIds!!.size > 0) n_facing++
            this.isMultiCam = multi_same_facing && n_facing >= 2
            //this.is_multi_cam = false; // test
            if (MyDebug.LOG) {
                Log.d(TAG, "multi_same_facing: " + multi_same_facing)
                Log.d(TAG, "n_facing: " + n_facing)
                Log.d(TAG, "is_multi_cam: " + this.isMultiCam)
            }

            if (!this.isMultiCam) {
                this.back_camera_ids = null
                this.frontCameraIds = null
                this.otherCameraIds = null
            }
        }

        // initialise on-screen button visibility
//        val switchCameraButton = binding.switchCamera
//        switchCameraButton.visibility = if (n_cameras > 1) View.VISIBLE else View.GONE
        // switchMultiCameraButton visibility updated below in mainUI.updateOnScreenIcons(), as it also depends on user preference
        val speechRecognizerButton = binding.audioControl
        speechRecognizerButton.visibility =
            View.GONE // disabled by default, until the speech recognizer is created
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after setting button visibility: " + (System.currentTimeMillis() - debug_time)
        )
        val cancelPanoramaButton = binding.cancelPanorama
        cancelPanoramaButton.visibility = View.GONE

        // We initialise optional controls to invisible/gone, so they don't show while the camera is opening - the actual visibility is
        // set in cameraSetup().
        // Note that ideally we'd set this in the xml, but doing so for R.id.zoom causes a crash on Galaxy Nexus startup beneath
        // setContentView()!
        // To be safe, we also do so for take_photo and zoom_seekbar (we already know we've had no reported crashes for focus_seekbar,
        // however).

        binding.zoom.visibility = View.GONE
        binding.zoomSeekbar.visibility = View.INVISIBLE

        // initialise state of on-screen icons
        mainUI?.updateOnScreenIcons()

        if (lockToLandscape) {
            // listen for orientation event change (only required if lock_to_landscape==true
            // (MainUI.onOrientationChanged() does nothing if lock_to_landscape==false)
            orientationEventListener = object : OrientationEventListener(this) {
                override fun onOrientationChanged(orientation: Int) {
                    this@MainActivity.mainUI?.onOrientationChanged(orientation)
                }
            }
            if (MyDebug.LOG) Log.d(
                TAG,
                "onCreate: time after setting orientation event listener: " + (System.currentTimeMillis() - debug_time)
            )
        }

        layoutChangeListener =
            OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (MyDebug.LOG) Log.d(TAG, "onLayoutChange")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
                    val display_size = Point()
                    applicationInterface!!.getDisplaySize(display_size, true)

                    // We need to call layoutUI when the window is resized without an orientation change -
                    // this can happen in split-screen or multi-window mode, where onConfigurationChanged
                    // is not guaranteed to be called.
                    // We check against the size of when layoutUI was last called, to avoid repeated calls
                    // when the resize is due to the device rotating and onConfigurationChanged is called -
                    // in fact we'd have a problem of repeatedly calling layoutUI, since doing layoutUI
                    // causes onLayoutChange() to be called again.
                    if (display_size.x != mainUI?.layoutUI_display_w || display_size.y != mainUI?.layoutUI_display_h) {
                        if (MyDebug.LOG) Log.d(TAG, "call layoutUI due to resize")
                    }
                }
            }

        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after setting long click listeners: " + (System.currentTimeMillis() - debug_time)
        )

        // listen for gestures
        gestureDetector = GestureDetector(this, MyGestureDetector())
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after creating gesture detector: " + (System.currentTimeMillis() - debug_time)
        )

        setupSystemUiVisibilityListener()
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after setting system ui visibility listener: " + (System.currentTimeMillis() - debug_time)
        )

        if (!has_done_first_time) {
            setFirstTimeFlag()
        }

        run {
            // handle What's New dialog
            var versionCode = -1
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                versionCode = pInfo.versionCode
            } catch (e: PackageManager.NameNotFoundException) {
                if (MyDebug.LOG) Log.d(
                    TAG, "NameNotFoundException exception trying to get version number"
                )
                e.printStackTrace()
            }
            if (versionCode != -1) {
                val latestVersion =
                    sharedPreferences.getInt(PreferenceKeys.LatestVersionPreferenceKey, 0)
                if (MyDebug.LOG) {
                    Log.d(TAG, "version_code: $versionCode")
                    Log.d(TAG, "latest_version: $latestVersion")
                }
                // We set the latest_version whether or not the dialog is shown - if we showed the first time dialog, we don't
                // want to then show the What's New dialog next time we run! Similarly if the user had disabled showing the dialog,
                // but then enables it, we still shouldn't show the dialog until the new time ManualCamera upgrades.
                val editor = sharedPreferences.edit()
                editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, versionCode)
                editor.apply()
            }
        }

        setModeFromIntents(savedInstanceState)

        // load icons
        preloadIcons(R.array.flash_icons)
        preloadIcons(R.array.focus_mode_icons)
        if (MyDebug.LOG) Log.d(
            TAG,
            "onCreate: time after preloading icons: " + (System.currentTimeMillis() - debug_time)
        )

        // initialise text to speech engine
        textToSpeechSuccess = false
        // run in separate thread so as to not delay startup time
        Thread {
            textToSpeech = TextToSpeech(this@MainActivity) { status ->
                if (MyDebug.LOG) Log.d(TAG, "TextToSpeech initialised")
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeechSuccess = true
                    if (MyDebug.LOG) Log.d(TAG, "TextToSpeech succeeded")
                } else {
                    if (MyDebug.LOG) Log.d(TAG, "TextToSpeech failed")
                }
            }
        }.start()

        // handle on back behaviour
        popupOnBackPressedCallback = PopupOnBackPressedCallback(false)
        this.onBackPressedDispatcher.addCallback(
            this, popupOnBackPressedCallback!!
        )

        pausePreviewOnBackPressedCallback = PausePreviewOnBackPressedCallback(false)
        this.onBackPressedDispatcher.addCallback(
            this, pausePreviewOnBackPressedCallback!!
        )

        screenLockOnBackPressedCallback = ScreenLockOnBackPressedCallback(false)
        this.onBackPressedDispatcher.addCallback(
            this, screenLockOnBackPressedCallback!!
        )

        // so we get the icons rotation even when rotating for the first time - see onSystemOrientationChanged
        this.hasOldSystemOrientation = true
        this.oldSystemOrientation = this.systemOrientation

        binding.upgrade.setOnClickListener { doUpgrade() }
        binding.settings.setOnClickListener { clickedSettings() }
        binding.cancelPanorama.setOnClickListener { clickedCancelPanorama() }
        binding.cycleRaw.setOnClickListener { clickedCycleRaw() }
        binding.cycleFlash.setOnClickListener { clickedCycleFlash() }
        binding.storeLocation.setOnClickListener { clickedStoreLocation() }
        binding.textStamp.setOnClickListener { clickedTextStamp() }
        binding.stamp.setOnClickListener { clickedStamp() }
        binding.focusPeaking.setOnClickListener { clickedFocusPeaking() }
        binding.autoLevel.setOnClickListener { clickedAutoLevel() }
        binding.faceDetection.setOnClickListener { clickedFaceDetection() }
        binding.audioControl.setOnClickListener { clickedAudioControl() }
        binding.switchMultiCamera.setOnClickListener { clickedSwitchMultiCamera() }

        binding.composeView.setContent {
            OpenCameraTheme {

                val activity = this@MainActivity

                val showCameraSettings = rememberSaveable { mutableStateOf(false) }
                val showCameraControls = rememberSaveable { mutableStateOf(false) }

                val isRecording by viewModel.isRecording.collectAsState()
                val isPhotoMode by viewModel.isPhotoMode.collectAsState()

                val isVideoRecordingPaused by viewModel.isVideoRecordingPaused.collectAsState()
                val galleryBitmap by viewModel.galleryBitmap.collectAsState()

                val resolutions by viewModel.resolutionsOfPhoto.collectAsState()
                val timers by viewModel.timers.collectAsState()
                val repeats by viewModel.repeats.collectAsState()
                val speeds by viewModel.speeds.collectAsState()
                val flashList by viewModel.flashList.collectAsState()
                val rawList by viewModel.rawList.collectAsState()

                val resolutionsOfVideo by viewModel.resolutionsOfVideo.collectAsState()
                val resolutionOfVideoSelected by viewModel.resolutionsOfVideoSelected.observeAsState()
                val resolutionSelected by viewModel.resolutionSelected.observeAsState()
                val timerSelected by viewModel.timerSelected.observeAsState()
                val repeatSelected by viewModel.repeatSelected.observeAsState()
                val speedSelected by viewModel.speedSelected.observeAsState()
                val flashSelected by viewModel.flashSelected.observeAsState()
                val rawSelected by viewModel.rawSelected.observeAsState()

                val controlsMapData by viewModel.controlsMapData.observeAsState()
                val controlOptionModel = viewModel.controlOptionModel.observeAsState()

                var exposureValue by remember { mutableFloatStateOf(0f) }
                var isoValue by remember { mutableFloatStateOf(0f) }
                var shutterValue by remember { mutableFloatStateOf(0f) }
                var whiteBalanceManualValue by remember { mutableFloatStateOf(0f) }
                var focusManualValue by remember { mutableFloatStateOf(0f) }

                var whiteBalanceValue by remember { mutableStateOf("") }
                var focusValue by remember { mutableStateOf("") }
                var sceneModeValue by remember { mutableStateOf("") }
                var colorEffectValue by remember { mutableStateOf("") }

                var currentControlSelected by remember { mutableStateOf("white_balance") }
                var valueFormated by remember { mutableStateOf("") }

                val photoModes = viewModel.photoModes.collectAsState()
                val currentPhotoMode = viewModel.currentPhotoModeUiModel.collectAsState()
                val selectedPhotoOption = viewModel.selectedPhotoOption.collectAsState()

                val videoModes = viewModel.videoModes.collectAsState()
                val currentVideoMode = viewModel.currentVideoMode.collectAsState()
                val captureRate = viewModel.captureRate.collectAsState()

                LaunchedEffect(isPhotoMode) {
                    if (isPhotoMode) {
                        viewModel.loadPhotoModes(activity, applicationInterface!!, preview!!)
                    } else {
                        if (preview != null && applicationInterface != null) {
                            delay(1000)
                            viewModel.loadVideoModes(activity, applicationInterface!!, preview!!)
                        }
                    }
                }

//                BackHandler {
//                    if (showCameraSettings.value) {
//                        showCameraSettings.value = false
//                    } else if (showCameraControls.value) {
//                        showCameraControls.value = false
//                    } else {
//                        val settings_is_open = settingsIsOpen()
//                        activity.finish()
//                    }
//                }

                Box {
                    CameraScreen(
                        isRecording = isRecording,
                        isVideoRecordingPaused = isVideoRecordingPaused,
                        isPhotoMode = isPhotoMode,
                        galleryBitmap = galleryBitmap,
                        onOpenGallery = { clickedGallery() },
                        onTogglePhotoVideoMode = { clickedSwitchVideo() },
                        onTakePhoto = { clickedTakePhoto() },
                        onTakePhotoVideoSnapshot = { clickedTakePhotoVideoSnapshot() },
                        onPauseVideo = { clickedPauseVideo() },
                        onSwitchCamera = {
                            clickedSwitchCamera()
                        },

                        showCameraSettings = {
                            showCameraSettings.value = !showCameraSettings.value
                            if (showCameraSettings.value && preview != null && applicationInterface != null) {
                                viewModel.setupCameraData(
                                    activity, applicationInterface!!, preview!!
                                )
                            }
                        },
                        showConfigTableSettings = {
                            if (preview != null && applicationInterface != null) {
                                if (preview?.isVideo == true && preview?.isVideoRecording == true) {
                                    Log.e(MainActivity.TAG, "don't add any more options")
                                } else {
                                    if (preview?.cameraController != null && applicationInterface?.isCameraExtensionPref != true) {
                                        showCameraControls.value = !showCameraControls.value
                                        if (showCameraControls.value) {
                                            viewModel.setupCameraControlsData(
                                                activity, applicationInterface!!, preview!!
                                            )
                                        }
                                    }
                                }
                            }

                        },

                        photoModes = photoModes.value,
                        currentPhotoMode = currentPhotoMode.value,
                        onChangePhotoMode = {
                            Log.e(TAG, "changePhotoMode: $it")
                            if (currentPhotoMode.value != it) {
                                viewModel.setCurrentPhotoMode(it)
                                viewModel.changePhotoModeUiModel(it)
                                changePhotoMode(it.mode)
                            }
                        },
                        onSelectedPhotoOption = {
                            viewModel.setSelectedPhotoOption(this@MainActivity, preview!!, it)
                        },
                        selectedPhotoOption = selectedPhotoOption.value,

                        videoModes = videoModes.value,
                        onChangeVideoMode = { newMode ->
                            Log.e(TAG, "onChangeVideoMode: $newMode")

                            if (newMode.mode == MyApplicationInterface.VideoMode.Video) {
                                viewModel.setCaptureRate(1f)
                                if (applicationInterface != null && preview != null) {
                                    viewModel.applySpeedSelectedPreview(
                                        activity, applicationInterface!!, preview!!, 1f
                                    )
                                }
                            }
                            viewModel.setVideoModeSelected(newMode)
                        },
                        // video, slow motion, time lapse
                        currentVideoMode = currentVideoMode.value,

                        captureRate = captureRate.value,
                        onCaptureRateSelected = {
                            Log.e(TAG, "onCaptureRateSelected: $it")
                            viewModel.setCaptureRate(it)
                            if (applicationInterface != null && preview != null) {
                                viewModel.applySpeedSelectedPreview(
                                    activity, applicationInterface!!, preview!!, it
                                )
                            }
                        },
                    )

                    if (showCameraSettings.value) {
                        CameraSettings(
                            isPhotoMode = isPhotoMode,
                            currentPhotoMode = currentPhotoMode.value,
                            resolutionSelected = resolutionSelected,
                            timerSelected = timerSelected,
                            repeatSelected = repeatSelected,
                            speedSelected = speedSelected,
                            resolutionOfVideoSelected = resolutionOfVideoSelected,
                            resolutions = resolutions,
                            resolutionsVideo = resolutionsOfVideo,
                            timers = timers,
                            repeats = repeats,
                            speeds = speeds,
                            flashList = flashList,
                            rawList = rawList,
                            flashSelected = flashSelected,
                            rawSelected = rawSelected,
                            onResolutionChange = {
                                Log.e(TAG, "onResolutionChange: $it")
                                if (applicationInterface != null && preview != null) {
                                    viewModel.setResolutionSelected(
                                        activity, applicationInterface!!, preview!!, it
                                    )
                                }
                            },
                            onRawChange = {
                                Log.e(TAG, "onRawChange: $it")
                                if (applicationInterface != null && preview != null) {
                                    viewModel.setRawSelected(activity, preview!!, it)
                                }
                            },
                            onFlashChange = {
                                Log.e(TAG, "onFlashChange: $it")
                                if (applicationInterface != null && preview != null) {
                                    viewModel.setFlashSelected(preview!!, it)
                                }
                            },
                            onResolutionOfVideoChange = {
                                Log.e(TAG, "onResolutionOfVideoChange: $it")
                                if (applicationInterface != null && preview != null) {
                                    viewModel.setResolutionOfVideoSelected(
                                        activity, applicationInterface!!, preview!!, it
                                    )
                                }
                            },
                            onTimerChange = {
                                Log.e(TAG, "onTimerChange: $it")
                                if (applicationInterface != null && preview != null) {
                                    viewModel.setTimerSelected(activity, it)
                                }
                            },
                            onRepeatChange = {
                                Log.e(TAG, "onRepeatChange: $it")
                                if (applicationInterface != null && preview != null) {
                                    viewModel.setRepeatSelected(activity, it)
                                }
                            },
                            onSpeedChange = {
                                Log.e(TAG, "onSpeedChange: $it")
                                if (applicationInterface != null && preview != null) {
                                    viewModel.setSpeedSelected(
                                        activity, applicationInterface!!, preview!!, it
                                    )
                                }
                            },
                            onClose = { showCameraSettings.value = false },
                            onOpenSettings = {
                                clickedSettings()
                            },
                            onUpgrade = {
                                showUpgradeDialog()
                            })
                    }

                    if (showCameraControls.value) {
                        CameraControls(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            onClose = { showCameraControls.value = false },
                            cameraControls = controlsMapData ?: hashMapOf(),
                            exposureValue = exposureValue,
                            isoValue = isoValue,
                            shutterValue = shutterValue,
                            valueFormated = valueFormated,
                            whiteBalanceValue = whiteBalanceValue,
                            controlOptionModel = controlOptionModel.value,
                            onWhiteBalanceChanged = { item ->
                                Log.e(TAG, "onWhiteBalanceChanged: $item")
                                whiteBalanceValue = item.id

                                if (whiteBalanceValue == "manual") {
                                    applicationInterface?.let {
                                        whiteBalanceManualValue =
                                            applicationInterface!!.whiteBalanceTemperaturePref.toFloat()
                                        viewModel.setControlOptionModel(item)
                                    }
                                } else {
                                    viewModel.setControlOptionModel(null)
                                }

                                val editor = sharedPreferences.edit()
                                editor.putString(
                                    PreferenceKeys.WhiteBalancePreferenceKey, whiteBalanceValue
                                )
                                editor.apply()

                                if (whiteBalanceValue == "manual") {
                                    updateForSettings(
                                        true,
                                        "${getString(R.string.white_balance)}: ${whiteBalanceManualValue.toInt()}K"
                                    )
                                } else {
                                    updateForSettings(
                                        true, "${getString(R.string.white_balance)}: ${item.text}"
                                    )
                                }
                            },
                            whiteBalanceManualValue = whiteBalanceManualValue,
                            onWhiteBalanceManualChanged = {
                                whiteBalanceManualValue = it
                                preview?.setWhiteBalanceTemperature(it.toInt())
                            },
                            focusManualValue = focusManualValue,
                            onFocusManualChanged = { progress ->
                                focusManualValue = progress
                                val frac: Double = progress / 100.0
                                val scaling = ManualSeekbars.seekbarScaling(frac)
                                val focusDistance =
                                    (scaling * preview!!.minimumFocusDistance).toFloat()
                                preview?.setFocusDistance(focusDistance, true, true)
                            },
                            focusValue = focusValue,
                            onFocusChanged = { item ->
                                Log.e(TAG, "onFocusChanged: $item")
                                val selectedValue: String? = item.id
                                focusValue = item.id

                                if (focusValue == "focus_mode_manual2") {
                                    if (applicationInterface != null) {
                                        focusManualValue =
                                            applicationInterface!!.getFocusDistancePref(true)
                                    }
                                    viewModel.setControlOptionModel(item)
                                } else {
                                    viewModel.setControlOptionModel(null)
                                }

                                if (focusValue != "focus_mode_manual2") {
                                    preview?.updateFocus(item.id, false, true)
                                    if (preview?.cameraController != null) {
                                        if (preview?.cameraController?.sceneModeAffectsFunctionality() == true) {
                                            updateForSettings(
                                                true,
                                                getResources().getString(R.string.scene_mode) + ": " + mainUI?.getEntryForFocus(
                                                    selectedValue
                                                )
                                            )
                                        } else {
                                            preview?.cameraController?.setSceneMode(selectedValue)
                                        }
                                    }
                                }
                            },
                            sceneModeValue = sceneModeValue,
                            onSceneModeChanged = { item ->
                                Log.e(TAG, "onSceneModeChanged: $item")
                                applicationInterface?.setSceneModePref(item.id)
                                sceneModeValue = item.id
                                if (preview?.cameraController != null) {
                                    if (preview?.cameraController?.sceneModeAffectsFunctionality() == true) {
                                        updateForSettings(
                                            true,
                                            getResources().getString(R.string.scene_mode) + ": " + mainUI?.getEntryForSceneMode(
                                                item.id
                                            )
                                        )
                                    } else {
                                        preview?.cameraController?.setSceneMode(item.id)
                                        updateForSettings(
                                            true,
                                            getResources().getString(R.string.scene_mode) + ": " + mainUI?.getEntryForSceneMode(
                                                item.id
                                            )
                                        )
                                    }
                                }
                            },
                            colorEffectValue = colorEffectValue,
                            onColorEffectChanged = { item ->
                                Log.e(TAG, "onColorEffectChanged: $item")
                                if (preview!!.cameraController != null) {
                                    preview!!.cameraController.setColorEffect(item.id)
                                    applicationInterface?.setColorEffectPref(item.id)
                                    colorEffectValue = item.id
                                }
                            },
                            controlIdSelected = currentControlSelected,
                            onControlIdSelected = {
                                currentControlSelected = it
                                when (currentControlSelected) {
                                    "exposure" -> {
                                        valueFormated =
                                            preview?.getExposureString(exposureValue.toInt()) ?: ""
                                    }

                                    "iso" -> {
                                        valueFormated =
                                            if (getIsoMode() == CameraController.ISO_DEFAULT) {
                                                CameraController.ISO_DEFAULT
                                            } else {
                                                preview?.getISOString(isoValue.toInt()) ?: ""
                                            }
                                    }

                                    "shutter" -> {
                                        valueFormated =
                                            if (shutterValue.toLong() == EXPOSURE_TIME_DEFAULT) {
                                                getString(R.string.auto)
                                            } else {
                                                preview?.getExposureTimeString(shutterValue.toLong())
                                                    ?: ""
                                            }
                                    }
                                }
                            },

                            // ISO
                            onIsoChanged = { iso ->
                                isoValue = iso
                                if (getIsoMode() == CameraController.ISO_DEFAULT) {
                                    setIsoManual()
                                }
                                preview?.setISO(iso.toInt())
                                valueFormated = preview?.getISOString(iso.toInt()) ?: ""
                            },
                            onIsoReset = {
                                setIsoAuto()
                                valueFormated = getString(R.string.auto)
                            },

                            // Shutter
                            onShutterChanged = {
                                shutterValue = it
                                setIsoManual()
                                preview?.setExposureTime(it.toLong())
                                valueFormated = preview?.getExposureTimeString(it.toLong()) ?: ""
                            },
                            onShutterReset = {
                                shutterValue = EXPOSURE_TIME_DEFAULT.toFloat()
                                setIsoAuto()
                                preview?.setExposureTime(EXPOSURE_TIME_DEFAULT)
                                valueFormated =
                                    preview?.getExposureTimeString(EXPOSURE_TIME_DEFAULT) ?: ""
                            },

                            // Exposure
                            onExposureChanged = {
                                exposureValue = it
                                preview?.setExposure(it.toInt())
                                valueFormated = preview?.getExposureString(it.toInt()) ?: ""
                            },
                            onExposureReset = {
                                exposureValue = 0f
                                preview?.setExposure(0)
                                valueFormated = preview?.getExposureString(0) ?: ""
                            },
                            onResetAllSettings = {
                                // iso
                                setIsoAuto()

                                // exposure
                                exposureValue = 0f
                                preview?.setExposure(0)

                                // white balance
                                whiteBalanceValue = "auto"
                                viewModel.setControlOptionModel(null)
                                val editor = sharedPreferences.edit()
                                editor.putString(
                                    PreferenceKeys.WhiteBalancePreferenceKey, whiteBalanceValue
                                )
                                editor.apply()

                                // shutter
                                shutterValue = EXPOSURE_TIME_DEFAULT.toFloat()
                                preview?.setExposureTime(EXPOSURE_TIME_DEFAULT)

                                // focus: focus_mode_auto
                                focusValue = "focus_mode_auto"
                                preview?.updateFocus(focusValue, false, true)
                                if (preview?.cameraController?.sceneModeAffectsFunctionality() == false) {
                                    preview?.cameraController?.setSceneMode(focusValue)
                                }

                                // scene mode: Auto
                                applicationInterface?.setSceneModePref("auto")
                                sceneModeValue = "auto"
                                if (preview?.cameraController?.sceneModeAffectsFunctionality() == false) {
                                    preview?.cameraController?.setSceneMode("auto")
                                }

                                // color effect
                                if (preview?.cameraController != null) {
                                    preview?.cameraController?.setColorEffect("auto")
                                    applicationInterface?.setColorEffectPref("auto")
                                    colorEffectValue = "auto"
                                }

                                updateForSettings(true, "Reset All")
                            },
                        )
                    }
                }
            }
        }

        if (MyDebug.LOG) {
            Log.d(
                TAG,
                "onCreate: total time for Activity startup: " + (System.currentTimeMillis() - debug_time)
            )
        }
    }

    private fun changePhotoMode(mode: PhotoMode) {
        Log.e(TAG, "photo mode: $mode")
        var toastMessage = when (mode) {
            PhotoMode.Standard -> getResources().getString(R.string.photo_mode_standard_full)
            PhotoMode.ExpoBracketing -> getResources().getString(R.string.photo_mode_expo_bracketing_full)
            PhotoMode.FocusBracketing -> getResources().getString(R.string.photo_mode_focus_bracketing_full)
            PhotoMode.FastBurst -> getResources().getString(R.string.photo_mode_fast_burst_full)
            PhotoMode.NoiseReduction -> getResources().getString(R.string.photo_mode_noise_reduction_full)
            PhotoMode.Panorama -> getResources().getString(R.string.photo_mode_panorama_full)
            PhotoMode.X_Auto -> getResources().getString(R.string.photo_mode_x_auto_full)
            PhotoMode.X_HDR -> getResources().getString(R.string.photo_mode_x_hdr_full)
            PhotoMode.X_Night -> getResources().getString(R.string.photo_mode_x_night_full)
            PhotoMode.X_Bokeh -> getResources().getString(R.string.photo_mode_x_bokeh_full)
            PhotoMode.X_Beauty -> getResources().getString(R.string.photo_mode_x_beauty_full)
            else -> mode.name
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            when (mode) {
                PhotoMode.Standard -> {
                    putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_std")
                }

                PhotoMode.DRO -> {
                    putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_dro")
                }

                PhotoMode.HDR -> {
                    putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_hdr")
                }

                PhotoMode.ExpoBracketing -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey,
                        "preference_photo_mode_expo_bracketing"
                    )
                }

                PhotoMode.FocusBracketing -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey,
                        "preference_photo_mode_focus_bracketing"
                    )
                }

                PhotoMode.FastBurst -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_fast_burst"
                    )
                }

                PhotoMode.NoiseReduction -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey,
                        "preference_photo_mode_noise_reduction"
                    )
                }

                PhotoMode.Panorama -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_panorama"
                    )
                }

                PhotoMode.X_Auto -> {
                    putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_x_auto")
                }

                PhotoMode.X_HDR -> {
                    putString(PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_x_hdr")
                }

                PhotoMode.X_Night -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_x_night"
                    )
                }

                PhotoMode.X_Bokeh -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_x_bokeh"
                    )
                }

                PhotoMode.X_Beauty -> {
                    putString(
                        PreferenceKeys.PhotoModePreferenceKey, "preference_photo_mode_x_beauty"
                    )
                }
            }
        }

        var doneDialog = false
        if (mode == PhotoMode.HDR) {
            val doneHdrInfo = sharedPreferences.contains(PreferenceKeys.HDRInfoPreferenceKey)
            if (!doneHdrInfo) {
                mainUI?.showInfoDialog(
                    R.string.photo_mode_hdr, R.string.hdr_info, PreferenceKeys.HDRInfoPreferenceKey
                )
                doneDialog = true
            }
        } else if (mode == PhotoMode.Panorama) {
            val donePanoramaInfo =
                sharedPreferences.contains(PreferenceKeys.PanoramaInfoPreferenceKey)
            if (!donePanoramaInfo) {
                mainUI?.showInfoDialog(
                    R.string.photo_mode_panorama_full,
                    R.string.panorama_info,
                    PreferenceKeys.PanoramaInfoPreferenceKey
                )
                doneDialog = true
            }
        }
        if (doneDialog) toastMessage = ""

        applicationInterface?.drawPreview?.updateSettings() // because we cache the photomode

        updateForSettings(
            true, toastMessage, false, true
        ) // need to setup the camera again, as options may change (e.g., required burst mode, or whether RAW is allowed in this mode)
    }

    fun getIsoMode(): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        return sharedPreferences.getString(
            PreferenceKeys.ISOModePreferenceKey, CameraController.ISO_DEFAULT
        ) ?: ""
    }

    private fun setIsoManual() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit {
            putString(PreferenceKeys.ISOModePreferenceKey, "m")
            if (preview?.cameraController != null && preview?.cameraController?.captureResultHasIso() == true) {
                val iso = preview!!.cameraController.captureResultIso()
                putString(PreferenceKeys.ISOPreferenceKey, iso.toString())
            } else {
                val iso = 800
                putString(PreferenceKeys.ISOPreferenceKey, "" + iso)
            }
        }
        updateForSettings(true, "")
    }

    private fun setIsoAuto() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.edit {
            putString(PreferenceKeys.ISOModePreferenceKey, CameraController.ISO_DEFAULT)
            putString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT)
            putLong(
                PreferenceKeys.ExposureTimePreferenceKey, EXPOSURE_TIME_DEFAULT
            )
        }
        updateForSettings(true, "")
    }

    val isMultiCamEnabled: Boolean
        /** Whether this is a multi camera device, and the user preference is set to enable the multi-camera button.
         */
        get() {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            return this.isMultiCam && sharedPreferences.getBoolean(
                PreferenceKeys.MultiCamButtonPreferenceKey, true
            )
        }

    private val actualCameraId: Int
        /* Returns the camera Id in use by the preview - or the one we requested, if the camera failed
              * to open.
              * Needed as Preview.getCameraId() returns 0 if camera_controller==null, but if the camera
              * fails to open, we want the switch camera icons to still work as expected!
              */
        get() {
            if (preview?.cameraController == null) return applicationInterface!!.cameraIdPref
            else return preview?.getCameraId() ?: 0
        }

    /** Whether the icon switch_multi_camera should be displayed. This is if the following are all
     * true:
     * - The device is a multi camera device (MainActivity.is_multi_cam==true).
     * - The user preference for using the separate icons is enabled
     * (PreferenceKeys.MultiCamButtonPreferenceKey).
     * - For the current camera ID, there are at least two cameras with the same front/back/external
     * "facing" (e.g., imagine a device with two back cameras, but only one front camera - no point
     * showing the multi-cam icon for just a single logical front camera).
     * OR there are physical cameras for the current camera, and again the user preference
     * PreferenceKeys.MultiCamButtonPreferenceKey is enabled.
     */
    fun showSwitchMultiCamIcon(): Boolean {
        if (preview?.hasPhysicalCameras() == true) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            if (sharedPreferences.getBoolean(
                    PreferenceKeys.MultiCamButtonPreferenceKey, true
                )
            ) return true
        }
        if (this.isMultiCamEnabled) {
            val cameraId = this.actualCameraId
            when (preview?.cameraControllerManager?.getFacing(cameraId)) {
                Facing.FACING_BACK -> if (back_camera_ids!!.size > 1) return true
                Facing.FACING_FRONT -> if (frontCameraIds!!.size > 1) return true
                else -> if (otherCameraIds!!.size > 1) return true
            }
        }
        return false
    }

    /** Whether user preference is set to allow long press actions.
     */
    private fun allowLongPress(): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        return sharedPreferences.getBoolean(PreferenceKeys.AllowLongPressPreferenceKey, true)
    }

    /* This method sets the preference defaults which are set specific for a particular device.
     * This method should be called when ManualCamera is run for the very first time after installation,
     * or when the user has requested to "Reset settings".
     */
    fun setDeviceDefaults() {
        if (MyDebug.LOG) Log.d(TAG, "setDeviceDefaults")
        val isSamsung = Build.MANUFACTURER.lowercase().contains("samsung")
        if (isSamsung && !is_test) {
            // Samsung Galaxy devices (including S10e, S24) have problems with HDR/expo - base images come out with wrong exposures.
            // This can be fixed by not using fast bast, allowing us to adjust the preview exposure to match.
            if (MyDebug.LOG) Log.d(TAG, "disable fast burst for camera2")
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            sharedPreferences.edit {
                putBoolean(PreferenceKeys.Camera2FastBurstPreferenceKey, false)
            }
        }
        if (supports_camera2 && !is_test) {
            // n.b., when testing, we explicitly decide whether to run with Camera2 API or not
            val manager2 = CameraControllerManager2(this)
            val n_cameras = manager2.getNumberOfCameras()
            var all_supports_camera2 =
                true // whether all cameras have at least LIMITED support for Camera2 (risky to default to Camera2 if any cameras are LEGACY, as not easy to test such devices)
            var i = 0
            while (i < n_cameras && all_supports_camera2) {
                if (!manager2.allowCamera2Support(i)) {
                    if (MyDebug.LOG) Log.d(
                        TAG, "camera $i doesn't have at least LIMITED support for Camera2 API"
                    )
                    all_supports_camera2 = false
                }
                i++
            }

            if (all_supports_camera2) {
                var default_to_camera2 = false
                val is_google = Build.MANUFACTURER.lowercase().contains("google")
                val is_nokia = Build.MANUFACTURER.lowercase().contains("hmd global")
                val is_oneplus = Build.MANUFACTURER.lowercase().contains("oneplus")
                if (is_google && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) default_to_camera2 =
                    true
                else if (is_nokia && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) default_to_camera2 =
                    true
                else if (isSamsung && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) default_to_camera2 =
                    true
                else if (is_oneplus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) default_to_camera2 =
                    true

                if (default_to_camera2) {
                    if (MyDebug.LOG) Log.d(TAG, "default to camera2 API")
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                    sharedPreferences.edit {
                        putString(
                            PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2"
                        )
                    }
                }
            }
        }
    }

    /** Switches modes if required, if called from a relevant intent/tile.
     */
    private fun setModeFromIntents(savedInstanceState: Bundle?) {
        if (MyDebug.LOG) Log.d(TAG, "setModeFromIntents")
        if (savedInstanceState != null) {
            // If we're restoring from a saved state, we shouldn't be resetting any modes
            if (MyDebug.LOG) Log.d(TAG, "restoring from saved state")
            return
        }
        var done_facing = false
        val action = this.intent.action
        if (MediaStore.INTENT_ACTION_VIDEO_CAMERA == action || MediaStore.ACTION_VIDEO_CAPTURE == action) {
            if (MyDebug.LOG) Log.d(TAG, "launching from video intent")
            applicationInterface!!.setVideoPref(true)
        } else if (MediaStore.ACTION_IMAGE_CAPTURE == action || MediaStore.ACTION_IMAGE_CAPTURE_SECURE == action || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA == action || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE == action) {
            if (MyDebug.LOG) Log.d(TAG, "launching from photo intent")
            applicationInterface!!.setVideoPref(false)
        } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileService.TILE_ID == action) || ACTION_SHORTCUT_CAMERA == action) {
            if (MyDebug.LOG) Log.d(
                TAG,
                "launching from quick settings tile or application shortcut for ManualCamera: photo mode"
            )
            applicationInterface!!.setVideoPref(false)
        } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileServiceVideo.TILE_ID == action) || ACTION_SHORTCUT_VIDEO == action) {
            if (MyDebug.LOG) Log.d(
                TAG,
                "launching from quick settings tile or application shortcut for ManualCamera: video mode"
            )
            applicationInterface!!.setVideoPref(true)
        } else if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && MyTileServiceFrontCamera.TILE_ID == action) || ACTION_SHORTCUT_SELFIE == action) {
            if (MyDebug.LOG) Log.d(
                TAG,
                "launching from quick settings tile or application shortcut for ManualCamera: selfie mode"
            )
            done_facing = true
            applicationInterface!!.switchToCamera(true)
        } else if (ACTION_SHORTCUT_GALLERY == action) {
            if (MyDebug.LOG) Log.d(
                TAG, "launching from application shortcut for ManualCamera: gallery"
            )
            openGallery()
        } else if (ACTION_SHORTCUT_SETTINGS == action) {
            if (MyDebug.LOG) Log.d(
                TAG, "launching from application shortcut for ManualCamera: settings"
            )
            openSettings()
        }

        val extras = this.getIntent().getExtras()
        if (extras != null) {
            if (MyDebug.LOG) Log.d(TAG, "handle intent extra information")
            if (!done_facing) {
                val camera_facing = extras.getInt("android.intent.extras.CAMERA_FACING", -1)
                if (camera_facing == 0 || camera_facing == 1) {
                    if (MyDebug.LOG) Log.d(
                        TAG, "found android.intent.extras.CAMERA_FACING: " + camera_facing
                    )
                    applicationInterface!!.switchToCamera(camera_facing == 1)
                    done_facing = true
                }
            }
            if (!done_facing) {
                if (extras.getInt("android.intent.extras.LENS_FACING_FRONT", -1) == 1) {
                    if (MyDebug.LOG) Log.d(TAG, "found android.intent.extras.LENS_FACING_FRONT")
                    applicationInterface!!.switchToCamera(true)
                    done_facing = true
                }
            }
            if (!done_facing) {
                if (extras.getInt("android.intent.extras.LENS_FACING_BACK", -1) == 1) {
                    if (MyDebug.LOG) Log.d(TAG, "found android.intent.extras.LENS_FACING_BACK")
                    applicationInterface!!.switchToCamera(false)
                    done_facing = true
                }
            }
            if (!done_facing) {
                if (extras.getBoolean("android.intent.extra.USE_FRONT_CAMERA", false)) {
                    if (MyDebug.LOG) Log.d(TAG, "found android.intent.extra.USE_FRONT_CAMERA")
                    applicationInterface!!.switchToCamera(true)
                    done_facing = true
                }
            }
        }

        // N.B., in practice the hasSetCameraId() check is pointless as we don't save the camera ID in shared preferences, so it will always
        // be false when application is started from onCreate(), unless resuming from saved instance (in which case we shouldn't be here anyway)
        if (!done_facing && !applicationInterface!!.hasSetCameraId()) {
            if (MyDebug.LOG) Log.d(TAG, "initialise to back camera")
            // most devices have first camera as back camera anyway so this wouldn't be needed, but some (e.g., LG G6) have first camera
            // as front camera, so we should explicitly switch to back camera
            applicationInterface!!.switchToCamera(false)
        }
    }

    /** Determine whether we support Camera2 API.
     */
    private fun initCamera2Support() {
        if (MyDebug.LOG) Log.d(TAG, "initCamera2Support")
        supports_camera2 = false
        run {
            // originally we allowed Camera2 if all cameras support at least LIMITED
            // as of 1.45, we allow Camera2 if at least one camera supports at least LIMITED - this
            // is to support devices that might have a camera with LIMITED or better support, but
            // also a LEGACY camera
            val manager2 = CameraControllerManager2(this)
            supports_camera2 = false
            val n_cameras = manager2.getNumberOfCameras()
            if (n_cameras == 0) {
                if (MyDebug.LOG) Log.d(TAG, "Camera2 reports 0 cameras")
                supports_camera2 = false
            }
            var i = 0
            while (i < n_cameras && !supports_camera2) {
                if (manager2.allowCamera2Support(i)) {
                    if (MyDebug.LOG) Log.d(
                        TAG, "camera " + i + " has at least limited support for Camera2 API"
                    )
                    supports_camera2 = true
                }
                i++
            }
        }

        //test_force_supports_camera2 = true; // test
        if (test_force_supports_camera2) {
            if (MyDebug.LOG) Log.d(TAG, "forcing supports_camera2")
            supports_camera2 = true
        }

        if (MyDebug.LOG) Log.d(TAG, "supports_camera2? " + supports_camera2)

        // handle the switch from a boolean preference_use_camera2 to String preference_camera_api
        // that occurred in v1.48
        if (supports_camera2) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            if (!sharedPreferences.contains(PreferenceKeys.CameraAPIPreferenceKey) // doesn't have the new key set yet
                && sharedPreferences.contains("preference_use_camera2") // has the old key set
                && sharedPreferences.getBoolean(
                    "preference_use_camera2", false
                ) // and camera2 was enabled
            ) {
                if (MyDebug.LOG) Log.d(
                    TAG, "transfer legacy camera2 boolean preference to new api option"
                )
                val editor = sharedPreferences.edit()
                editor.putString(
                    PreferenceKeys.CameraAPIPreferenceKey, "preference_camera_api_camera2"
                )
                editor.remove("preference_use_camera2") // remove the old key, just in case
                editor.apply()
            }
        }
    }

    /** Handles users updating to a version with scoped storage (this could be Android 10 users upgrading
     * to the version of ManualCamera with scoped storage; or users who later upgrade to Android 10).
     * With scoped storage, we no longer support saving outside of DCIM/ when not using SAF.
     * This updates if necessary both the current save location, and the save folder history.
     */
    private fun checkSaveLocations() {
        if (MyDebug.LOG) Log.d(TAG, "checkSaveLocations")
        if (useScopedStorage()) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            var any_changes = false
            val save_location = this.storageUtils!!.getSaveLocation()
            var res = checkSaveLocation(save_location)
            if (!res.res) {
                if (MyDebug.LOG) Log.d(
                    TAG, "save_location not valid with scoped storage: " + save_location
                )
                val new_folder: String?
                if (res.alt == null) {
                    // no alternative, fall back to default
                    new_folder = "ManualCamera"
                } else {
                    // replace with the alternative
                    if (MyDebug.LOG) Log.d(TAG, "alternative: " + res.alt)
                    new_folder = res.alt
                }
                val editor = sharedPreferences.edit()
                editor.putString(PreferenceKeys.SaveLocationPreferenceKey, new_folder)
                editor.apply()
                any_changes = true
            }

            // now check history
            // go backwards so we can remove easily
            for (i in save_location_history!!.size() - 1 downTo 0) {
                val this_location = save_location_history!!.get(i)
                res = checkSaveLocation(this_location)
                if (!res.res) {
                    if (MyDebug.LOG) Log.d(
                        TAG,
                        "save_location in history " + i + " not valid with scoped storage: " + this_location
                    )
                    if (res.alt == null) {
                        // no alternative, remove
                        save_location_history!!.remove(i)
                    } else {
                        // replace with the alternative
                        if (MyDebug.LOG) Log.d(TAG, "alternative: " + res.alt)
                        save_location_history!!.set(i, res.alt)
                    }
                    any_changes = true
                }
            }

            if (any_changes) {
                this.save_location_history!!.updateFolderHistory(
                    this.storageUtils!!.getSaveLocation(), false
                )
            }
        }
    }

    /** Result from checkSaveLocation. Ideally we'd just use android.util.Pair, but that's not mocked
     * for use in unit tests.
     * See checkSaveLocation() for documentation.
     */
    class CheckSaveLocationResult(val res: Boolean, val alt: String?) {
        override fun equals(o: Any?): Boolean {
            if (o !is CheckSaveLocationResult) {
                return false
            }
            val that = o
            // stop dumb inspection that suggests replacing warning with an error(!) (Objects class is not available on all API versions)
            // and the other inspection suggests replacing with code that would cause a nullpointerexception
            return that.res == this.res && ((that.alt === this.alt) || (that.alt != null && that.alt == this.alt))
            //return that.res == this.res && ( (that.alt == this.alt) || (that.alt != null && that.alt.equals(this.alt) ) );
        }

        override fun hashCode(): Int {
            return (if (res) 1249 else 1259) xor (if (alt == null) 0 else alt.hashCode())
        }

        override fun toString(): String {
            return "CheckSaveLocationResult{$res , $alt}"
        }
    }

    private fun preloadIcons(iconsId: Int) {
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            Log.d(TAG, "preloadIcons: " + iconsId)
            debug_time = System.currentTimeMillis()
        }
        val icons = getResources().getStringArray(iconsId)
        for (icon in icons) {
            val resource = getResources().getIdentifier(
                icon, null, this.getApplicationContext().getPackageName()
            )
            if (MyDebug.LOG) Log.d(TAG, "load resource: " + resource)
            val bm = BitmapFactory.decodeResource(getResources(), resource)
            this.preloaded_bitmap_resources.put(resource, bm)
        }
        if (MyDebug.LOG) {
            Log.d(
                TAG,
                "preloadIcons: total time for preloadIcons: " + (System.currentTimeMillis() - debug_time)
            )
            Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size)
        }
    }

    override fun onStop() {
        if (MyDebug.LOG) Log.d(TAG, "onStop")
        super.onStop()

        // we stop location listening in onPause, but done here again just to be certain!
        applicationInterface!!.locationSupplier.freeLocationListeners()
    }

    override fun onDestroy() {
        if (MyDebug.LOG) {
            Log.d(TAG, "onDestroy")
            Log.d(TAG, "size of preloaded_bitmap_resources: " + preloaded_bitmap_resources.size)
        }
        activity_count--
        if (MyDebug.LOG) Log.d(TAG, "activity_count: $activity_count")

        billingManager?.endConnection()
        // should do asap before waiting for images to be saved - as risk the application will be killed whilst waiting for that to happen,
        // and we want to avoid notifications hanging around
        cancelImageSavingNotification()

        if (want_no_limits && navigation_gap != 0) {
            if (MyDebug.LOG) Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS")
            // it's unclear why this matters - but there is a bug when exiting split-screen mode, if the split-screen mode had set want_no_limits:
            // even though the application is created when leaving split-screen mode, we still end up with the window flags for showing
            // under the navigation bar!
            // update: this issue is also fixed by not allowing want_no_limits mode in multi-window mode, but still good to reset things here
            // just in case
            showUnderNavigation(false)
        }

        // reduce risk of losing any images
        // we don't do this in onPause or onStop, due to risk of ANRs
        // note that even if we did call this earlier in onPause or onStop, we'd still want to wait again here: as it can happen
        // that a new image appears after onPause/onStop is called, in which case we want to wait until images are saved,
        // otherwise we can have crash if we need Renderscript after calling releaseAllContexts(), or because rs has been set to
        // null from beneath applicationInterface.onDestroy()
        waitUntilImageQueueEmpty()

        preview?.onDestroy()
        if (applicationInterface != null) {
            applicationInterface!!.onDestroy()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity_count == 0) {
            // See note in HDRProcessor.onDestroy() - but from Android M, renderscript contexts are released with releaseAllContexts()
            // doc for releaseAllContexts() says "If no contexts have been created this function does nothing"
            // Important to only do so if no other activities are running (see activity_count). Otherwise risk
            // of crashes if one activity is destroyed when another instance is still using Renderscript. I've
            // been unable to reproduce this, though such RSInvalidStateException crashes from Google Play.
            if (MyDebug.LOG) Log.d(TAG, "release renderscript contexts")
            RenderScript.releaseAllContexts()
        }
        // Need to recycle to avoid out of memory when running tests - probably good practice to do anyway
        for (entry in preloaded_bitmap_resources.entries) {
            if (MyDebug.LOG) Log.d(TAG, "recycle: " + entry.key)
            entry.value!!.recycle()
        }
        preloaded_bitmap_resources.clear()
        if (textToSpeech != null) {
            // http://stackoverflow.com/questions/4242401/tts-error-leaked-serviceconnection-android-speech-tts-texttospeech-solved
            if (MyDebug.LOG) Log.d(TAG, "free textToSpeech")
            textToSpeech!!.stop()
            textToSpeech!!.shutdown()
            textToSpeech = null
        }

        // we stop location listening in onPause, but done here again just to be certain!
        applicationInterface!!.locationSupplier.freeLocationListeners()

        super.onDestroy()
        if (MyDebug.LOG) Log.d(TAG, "onDestroy done")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    private fun setFirstTimeFlag() {
        if (MyDebug.LOG) Log.d(TAG, "setFirstTimeFlag")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            putBoolean(PreferenceKeys.FirstTimePreferenceKey, true)
        }
    }

    fun launchOnlineHelp() {
        if (MyDebug.LOG) Log.d(TAG, "launchOnlineHelp")
        // if we change this, remember that any page linked to must abide by Google Play developer policies!
        val browserIntent = Intent(Intent.ACTION_VIEW, getOnlineHelpUrl("").toUri())
        startActivity(browserIntent)
    }

    fun launchOnlinePrivacyPolicy() {
        if (MyDebug.LOG) Log.d(TAG, "launchOnlinePrivacyPolicy")
        // if we change this, remember that any page linked to must abide by Google Play developer policies!
        //Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getOnlineHelpUrl("index.html#privacy")));
        val browserIntent = Intent(Intent.ACTION_VIEW, getOnlineHelpUrl("privacy_oc.html").toUri())
        startActivity(browserIntent)
    }

    fun launchOnlineLicences() {
        if (MyDebug.LOG) Log.d(TAG, "launchOnlineLicences")
        // if we change this, remember that any page linked to must abide by Google Play developer policies!
        val browserIntent = Intent(Intent.ACTION_VIEW, getOnlineHelpUrl("#licence").toUri())
        startActivity(browserIntent)
    }

    /* Audio trigger - either loud sound, or speech recognition.
     * This performs some additional checks before taking a photo.
     */
    fun audioTrigger() {
        if (MyDebug.LOG) Log.d(TAG, "ignore audio trigger due to popup open")
        if (popupIsOpen()) {
            if (MyDebug.LOG) Log.d(TAG, "ignore audio trigger due to popup open")
        } else if (this.isCameraInBackground) {
            if (MyDebug.LOG) Log.d(TAG, "ignore audio trigger due to camera in background")
        } else if (preview?.isTakingPhotoOrOnTimer == true) {
            if (MyDebug.LOG) Log.d(
                TAG, "ignore audio trigger due to already taking photo or on timer"
            )
        } else if (preview?.isVideoRecording == true) {
            if (MyDebug.LOG) Log.d(TAG, "ignore audio trigger due to already recording video")
        } else {
            if (MyDebug.LOG) Log.d(TAG, "schedule take picture due to loud noise")
            this.runOnUiThread {
                if (MyDebug.LOG) Log.d(TAG, "taking picture due to audio trigger")
                takePicture(false)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (MyDebug.LOG) Log.d(TAG, "onKeyDown: $keyCode")
        if (this.isCameraInBackground) {
            // don't allow keys such as volume keys for taking photo when camera in background!
            if (MyDebug.LOG) Log.d(TAG, "camera is in background")
        } else {
            if (event != null) {
                val handled = mainUI?.onKeyDown(keyCode, event) ?: false
                if (handled) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (MyDebug.LOG) Log.d(TAG, "onKeyUp: $keyCode")
        if (this.isCameraInBackground) {
            // don't allow keys such as volume keys for taking photo when camera in background!
            if (MyDebug.LOG) Log.d(TAG, "camera is in background")
        } else {
            mainUI?.onKeyUp(keyCode, event)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun zoomByStep(change: Int) {
        var change = change
        if (MyDebug.LOG) Log.d(TAG, "zoomByStep: $change")
        if (preview?.supportsZoom() == true && change != 0) {
            if (preview?.cameraController != null) {
                // If the minimum zoom is < 1.0, the seekbar will have repeated entries for 1x zoom
                // (so it's easier for the user to zoom to exactly 1.0x). But if using the -/+ buttons,
                // volume keys etc to zoom, we want to skip over these repeated values.
                val zoom_factor = preview?.cameraController?.getZoom() ?: 1
                var new_zoom_factor = zoom_factor + change
                if (MyDebug.LOG) Log.d(TAG, "new_zoom_factor: $new_zoom_factor")
                while (new_zoom_factor > 0 && new_zoom_factor < preview?.getMaxZoom() ?: 1 && preview?.getZoomRatio(
                        new_zoom_factor
                    ) == preview?.getZoomRatio()
                ) {
                    if (change > 0) change++
                    else change--
                    new_zoom_factor = zoom_factor + change
                    if (MyDebug.LOG) Log.d(TAG, "skip over constant region: $new_zoom_factor")
                }
            }

            mainUI?.changeSeekbar(
                R.id.zoom_seekbar, -change
            ) // seekbar is opposite direction to zoom array
        }
    }

    fun zoomIn() {
        zoomByStep(1)
    }

    fun zoomOut() {
        zoomByStep(-1)
    }

    fun changeExposure(change: Int) {
        var change = change
        if (preview?.supportsExposures() == true) {
            if (exposure_seekbar_values != null) {
                val seekBar = binding.exposureSeekbar
                val progress = seekBar.getProgress()
                var new_progress = progress + change
                val current_exposure = getExposureSeekbarValue(progress)
                if (new_progress < 0 || new_progress > exposure_seekbar_values!!.size - 1) {
                    // skip
                } else if (getExposureSeekbarValue(new_progress) == 0 && current_exposure != 0) {
                    // snap to the central repeated zero
                    new_progress = this.exposureSeekbarProgressZero
                    change = new_progress - progress
                } else {
                    // skip over the repeated zeroes
                    while (new_progress > 0 && new_progress < exposure_seekbar_values!!.size - 1 && getExposureSeekbarValue(
                            new_progress
                        ) == current_exposure
                    ) {
                        if (change > 0) change++
                        else change--
                        new_progress = progress + change
                        if (MyDebug.LOG) Log.d(TAG, "skip over constant region: $new_progress")
                    }
                }
            }
            mainUI?.changeSeekbar(R.id.exposure_seekbar, change)
        }
    }

    /** Returns the exposure compensation corresponding to a progress on the seekbar.
     * Caller is responsible for checking that progress is within valid range.
     */
    fun getExposureSeekbarValue(progress: Int): Int {
        return exposure_seekbar_values!!.get(progress)!!
    }

    fun changeISO(change: Int) {
        if (preview?.supportsISORange() == true) {
            mainUI?.changeSeekbar(R.id.iso_seekbar, change)
        }
    }

    fun changeFocusDistance(change: Int, isTargetDistance: Boolean) {
        mainUI?.changeSeekbar(
            if (isTargetDistance) R.id.focus_bracketing_target_seekbar else R.id.focus_seekbar,
            change
        )
    }

    private val accelerometerListener: SensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

        override fun onSensorChanged(event: SensorEvent?) {
            preview?.onAccelerometerSensorChanged(event)
        }
    }

    override fun onResume() {
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            Log.d(TAG, "onResume")
            debug_time = System.currentTimeMillis()
        }
        super.onResume()
        this.isAppPaused = false // must be set before initLocation() at least

        // this is intentionally true, not false, as the uncovering happens in DrawPreview when we receive frames from the camera after it's opened
        // (this should already have been set from the call in onPause(), but we set it here again just in case)
        applicationInterface!!.drawPreview?.setCoverPreview(true)

        applicationInterface!!.drawPreview?.clearDimPreview() // shouldn't be needed, but just in case the dim preview flag got set somewhere

        cancelImageSavingNotification()

        // Set black window background; also needed if we hide the virtual buttons in immersive mode
        // Note that we do it here rather than customising the theme's android:windowBackground, so this doesn't affect other views - in particular, the MyPreferenceFragment settings
        window.decorView.rootView.setBackgroundColor(Color.BLACK)

        if (edgeToEdgeMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // needed on Android 15, otherwise the navigation bar is not transparent
            getWindow().setNavigationBarContrastEnforced(false)
        }

        registerDisplayListener()

        mSensorManager!!.registerListener(
            accelerometerListener, mSensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL
        )
        magneticSensor!!.registerMagneticListener(mSensorManager)
        if (orientationEventListener != null) {
            orientationEventListener!!.enable()
        }
        window.decorView.addOnLayoutChangeListener(layoutChangeListener)

        // if BLE remote control is enabled, then start the background BLE service
        bluetoothRemoteControl!!.startRemoteControl()

        //speechControl.initSpeechRecognizer();
        initLocation()
        initGyroSensors()
        applicationInterface!!.imageSaver?.onResume()
        soundPoolManager!!.initSound()
        soundPoolManager!!.loadSound(R.raw.mybeep)
        soundPoolManager!!.loadSound(R.raw.mybeep_hi)

        resetCachedSystemOrientation() // just in case?

        // If the cached last media has exif datetime info, it's fine to just call updateGalleryIcon(),
        // which will find the most recent media (and takes care of if the cached last image may have
        // been deleted).
        // If it doesn't have exif datetime tags, updateGalleryIcon() may not be able to find the most
        // recent media, so we stick with the cached uri if we can test that it's still accessible.
        if (!this.storageUtils!!.lastMediaScannedHasNoExifDateTime) {
            updateGalleryIcon()
        } else {
            if (MyDebug.LOG) Log.d(TAG, "last media has no exif datetime, so check it still exists")
            var uri_exists = false
            var inputStream: InputStream? = null
            val check_uri = this.storageUtils!!.lastMediaScannedCheckUri
            if (MyDebug.LOG) Log.d(TAG, "check_uri: $check_uri")
            try {
                inputStream = this.contentResolver.openInputStream(check_uri)
                if (inputStream != null) uri_exists = true
            } catch (ignored: Exception) {
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            if (uri_exists) {
                if (MyDebug.LOG) Log.d(TAG, "    most recent uri exists")
                // also re-allow ghost image again in case that option is set (since we won't be
                // doing this via updateGalleryIcon())
                applicationInterface!!.drawPreview?.allowGhostImage()
            } else {
                if (MyDebug.LOG) Log.d(TAG, "    most recent uri no longer valid")
                updateGalleryIcon()
            }
        }

        applicationInterface!!.reset(false) // should be called before opening the camera in preview.onResume()

        if (!this.isCameraInBackground) {
            // don't restart camera if we're showing a dialog or settings
            preview?.onResume()
        }

        run {
            // show a toast for the camera if it's not the first for front of back facing (otherwise on multi-front/back camera
            // devices, it's easy to forget if set to a different camera)
            // but we only show this when resuming, not every time the camera opens
            // OR show the toast for the camera if it's a physical camera
            val cameraId = applicationInterface!!.cameraIdPref
            val cameraIdSPhysical = applicationInterface!!.cameraIdSPhysicalPref
            if (cameraId > 0 || cameraIdSPhysical != null) {
                val camera_controller_manager = preview?.cameraControllerManager
                val front_facing = camera_controller_manager?.getFacing(cameraId)
                if (MyDebug.LOG) Log.d(TAG, "front_facing: $front_facing")
                if ((camera_controller_manager?.getNumberOfCameras()
                        ?: 0) > 2 || cameraIdSPhysical != null
                ) {
                    var camera_is_default = true
                    if (cameraIdSPhysical != null) camera_is_default = false
                    var i = 0
                    while (i < cameraId && camera_is_default) {
                        val that_front_facing = camera_controller_manager?.getFacing(i)
                        if (MyDebug.LOG) Log.d(
                            TAG, "camera $i that_front_facing: $that_front_facing"
                        )
                        if (that_front_facing == front_facing) {
                            // found an earlier camera with same front/back facing
                            camera_is_default = false
                        }
                        i++
                    }
                    if (MyDebug.LOG) Log.d(TAG, "camera_is_default: $camera_is_default")
                    if (!camera_is_default) {
                        this.pushCameraIdToast(cameraId, cameraIdSPhysical)
                    }
                }
            }
        }

//        pushSwitchedCamera = false // just in case

        if (MyDebug.LOG) {
            Log.d(
                TAG, "onResume: total time to resume: " + (System.currentTimeMillis() - debug_time)
            )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "onWindowFocusChanged: $hasFocus")
        super.onWindowFocusChanged(hasFocus)
        if (!this.isCameraInBackground && hasFocus) {
            // low profile mode is cleared when app goes into background
            // and for Kit Kat immersive mode, we want to set up the timer
            // we do in onWindowFocusChanged rather than onResume(), to also catch when window lost focus due to notification bar being dragged down (which prevents resetting of immersive mode)
            initImmersiveMode()
        }
    }

    override fun onPause() {
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            Log.d(TAG, "onPause")
            debug_time = System.currentTimeMillis()
        }
        super.onPause() // docs say to call this before freeing other things
        this.isAppPaused = true

        unregisterDisplayListener()
        mSensorManager!!.unregisterListener(accelerometerListener)
        magneticSensor!!.unregisterMagneticListener(mSensorManager)
        if (orientationEventListener != null) {
            orientationEventListener!!.disable()
        }
        window.decorView.removeOnLayoutChangeListener(layoutChangeListener)
        bluetoothRemoteControl!!.stopRemoteControl()
        freeAudioListener(false)
        //speechControl.stopSpeechRecognizer();
        applicationInterface!!.locationSupplier.freeLocationListeners()
        applicationInterface!!.stopPanorama(true) // in practice not needed as we should stop panorama when camera is closed, but good to do it explicitly here, before disabling the gyro sensors
        applicationInterface!!.gyroSensor.disableSensors()
        applicationInterface!!.imageSaver?.onPause()
        soundPoolManager!!.releaseSound()
        applicationInterface!!.clearLastImages() // this should happen when pausing the preview, but call explicitly just to be safe
        applicationInterface!!.drawPreview?.clearGhostImage()
        preview?.onPause()
        applicationInterface!!.drawPreview?.setCoverPreview(true) // must be after we've closed the preview (otherwise risk that further frames from preview will unset the cover_preview flag in DrawPreview)

        if ((applicationInterface!!.imageSaver?.nImagesToSave ?: 0) > 0) {
            createImageSavingNotification()
        }

        if (update_gallery_future != null) {
            update_gallery_future!!.cancel(true)
        }

        // intentionally do this again, just in case something turned location on since - keep this right at the end:
        applicationInterface!!.locationSupplier.freeLocationListeners()

        // don't want to enter immersive mode when in background
        // needs to be last in case anything above indirectly called initImmersiveMode()
        cancelImmersiveTimer()

        if (MyDebug.LOG) {
            Log.d(TAG, "onPause: total time to pause: " + (System.currentTimeMillis() - debug_time))
        }
    }

    private inner class MyDisplayListener : DisplayListener {
        private var old_rotation: Int

        init {
            val rotation = this@MainActivity.windowManager.defaultDisplay.rotation
            if (MyDebug.LOG) {
                Log.d(TAG, "MyDisplayListener")
                Log.d(TAG, "rotation: $rotation")
            }
            old_rotation = rotation
        }

        override fun onDisplayAdded(displayId: Int) {
        }

        override fun onDisplayRemoved(displayId: Int) {
        }

        override fun onDisplayChanged(displayId: Int) {
            val rotation = this@MainActivity.windowManager.defaultDisplay.rotation
            if (MyDebug.LOG) {
                Log.d(TAG, "onDisplayChanged: $displayId")
                Log.d(TAG, "rotation: $rotation")
                Log.d(TAG, "old_rotation: $old_rotation")
            }
            if ((rotation == Surface.ROTATION_0 && old_rotation == Surface.ROTATION_180) || (rotation == Surface.ROTATION_180 && old_rotation == Surface.ROTATION_0) || (rotation == Surface.ROTATION_90 && old_rotation == Surface.ROTATION_270) || (rotation == Surface.ROTATION_270 && old_rotation == Surface.ROTATION_90)) {
                if (MyDebug.LOG) Log.d(
                    TAG, "onDisplayChanged: switched between landscape and reverse orientation"
                )
                onSystemOrientationChanged()
            }

            old_rotation = rotation
        }
    }

    /** Creates and registers a display listener, needed to handle switches between landscape and
     * reverse landscape (without going via portrait) when lock_to_landscape==false.
     */
    private fun registerDisplayListener() {
        if (MyDebug.LOG) Log.d(TAG, "registerDisplayListener")
        if (!lockToLandscape) {
            displayListener = MyDisplayListener()
            val displayManager = this.getSystemService(DISPLAY_SERVICE) as DisplayManager
            displayManager.registerDisplayListener(displayListener, null)
        }
    }

    private fun unregisterDisplayListener() {
        if (MyDebug.LOG) Log.d(TAG, "unregisterDisplayListener")
        if (displayListener != null) {
            val displayManager = this.getSystemService(DISPLAY_SERVICE) as DisplayManager
            displayManager.unregisterDisplayListener(displayListener)
            displayListener = null
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (MyDebug.LOG) Log.d(TAG, "onConfigurationChanged(): " + newConfig.orientation)
        // configuration change can include screen orientation (landscape/portrait) when not locked (when settings is open)
        // needed if app is paused/resumed when settings is open and device is in portrait mode
        // update: need this all the time when lock_to_landscape==false
        onSystemOrientationChanged()
        super.onConfigurationChanged(newConfig)
    }

    private fun onSystemOrientationChanged() {
        if (MyDebug.LOG) Log.d(TAG, "onSystemOrientationChanged")

        // n.b., need to call this first, before preview.setCameraDisplayOrientation(), since
        // preview.setCameraDisplayOrientation() will call getDisplayRotation() and we don't want
        // to be using the outdated cached value now that the rotation has changed!
        // update: no longer relevant, as preview.setCameraDisplayOrientation() now sets
        // prefer_later to true to avoid using cached value. But might as well call it first anyway.
        resetCachedSystemOrientation()

        preview?.setCameraDisplayOrientation()
        if (!lockToLandscape) {
            val newSystemOrientation = this.systemOrientation
            if (hasOldSystemOrientation && oldSystemOrientation == newSystemOrientation) {
                if (MyDebug.LOG) Log.d(
                    TAG, "onSystemOrientationChanged: orientation hasn't changed"
                )
            } else {
                if (hasOldSystemOrientation) {
                    // handle rotation animation
                    var start_rotation: Int =
                        getRotationFromSystemOrientation(oldSystemOrientation) - getRotationFromSystemOrientation(
                            newSystemOrientation
                        )
                    if (MyDebug.LOG) Log.d(TAG, "start_rotation: " + start_rotation)
                    if (start_rotation < -180) start_rotation += 360
                    else if (start_rotation > 180) start_rotation -= 360
                    mainUI?.layoutUIWithRotation(start_rotation.toFloat())
                }
                applicationInterface!!.drawPreview?.updateSettings()

                hasOldSystemOrientation = true
                oldSystemOrientation = newSystemOrientation
            }
        }
    }

    val systemOrientation: SystemOrientation
        /** Returns the current system orientation.
         * Note if lock_to_landscape is true, this always returns LANDSCAPE even if called when we're
         * allowing configuration changes (e.g., in Settings or a dialog is showing). (This method,
         * and hence calls to it, were added to support lock_to_landscape==false behaviour, and we
         * want to avoid changing behaviour for lock_to_landscape==true behaviour.)
         * Note that this also caches the orientation: firstly for performance (as this is called from
         * DrawPreview), secondly to support REVERSE_LANDSCAPE, we don't want a sudden change if
         * getDefaultDisplay().getRotation() changes after the configuration changes.
         */
        get() {
            if (test_force_system_orientation) {
                return test_system_orientation
            }
            if (lockToLandscape) {
                return SystemOrientation.LANDSCAPE
            }
            if (has_cached_system_orientation) {
                return cached_system_orientation!!
            }
            var result: SystemOrientation
            val system_orientation = getResources().configuration.orientation
            if (MyDebug.LOG) Log.d(
                TAG, "system orientation: $system_orientation"
            )
            when (system_orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> {
                    result = SystemOrientation.LANDSCAPE
                    run {
                        val rotation = windowManager.getDefaultDisplay().rotation
                        if (MyDebug.LOG) Log.d(
                            TAG, "rotation: $rotation"
                        )
                        when (rotation) {
                            Surface.ROTATION_0, Surface.ROTATION_90 ->                             // landscape
                                if (MyDebug.LOG) Log.d(
                                    TAG, "landscape"
                                )

                            Surface.ROTATION_180, Surface.ROTATION_270 -> {
                                // reverse landscape
                                if (MyDebug.LOG) Log.d(
                                    TAG, "reverse landscape"
                                )
                                result = SystemOrientation.REVERSE_LANDSCAPE
                            }

                            else -> if (MyDebug.LOG) Log.e(
                                TAG, "unknown rotation: $rotation"
                            )
                        }
                    }
                }

                Configuration.ORIENTATION_PORTRAIT -> result = SystemOrientation.PORTRAIT

                Configuration.ORIENTATION_UNDEFINED -> {
                    if (MyDebug.LOG) Log.e(
                        TAG, "unknown system orientation: $system_orientation"
                    )
                    result = SystemOrientation.LANDSCAPE
                }

                else -> {
                    if (MyDebug.LOG) Log.e(
                        TAG, "unknown system orientation: $system_orientation"
                    )
                    result = SystemOrientation.LANDSCAPE
                }
            }
            if (MyDebug.LOG) Log.d(
                TAG, "system orientation is now: $result"
            )
            this.has_cached_system_orientation = true
            this.cached_system_orientation = result
            return result
        }

    private fun resetCachedSystemOrientation() {
        this.has_cached_system_orientation = false
        this.has_cached_display_rotation = false
    }

    /** A wrapper for getWindowManager().getDefaultDisplay().getRotation(), except if
     * lock_to_landscape==false && prefer_later==false, this uses a cached value.
     */
    fun getDisplayRotation(preferLater: Boolean): Int {/*if( MyDebug.LOG ) {
            Log.d(TAG, "getDisplayRotationDegrees");
            Log.d(TAG, "prefer_later: " + prefer_later);
        }*/
        if (lockToLandscape || preferLater) {
            return windowManager.defaultDisplay.rotation
        }
        // we cache to reduce effect of annoying problem where rotation changes shortly before the
        // configuration actually changes (several frames), so on-screen elements would briefly show
        // in wrong location when device rotates from/to portrait and landscape; also not a bad idea
        // to cache for performance anyway, to avoid calling
        // getWindowManager().getDefaultDisplay().getRotation() every frame
        val time_ms = System.currentTimeMillis()
        if (has_cached_display_rotation && time_ms < cached_display_rotation_time_ms + 1000) {
            return cached_display_rotation
        }
        has_cached_display_rotation = true
        val rotation = windowManager.defaultDisplay.rotation
        cached_display_rotation = rotation
        cached_display_rotation_time_ms = time_ms
        return rotation
    }

    fun waitUntilImageQueueEmpty() {
        if (MyDebug.LOG) Log.d(TAG, "waitUntilImageQueueEmpty")
        applicationInterface!!.imageSaver?.waitUntilDone()
    }

    /**
     * @return True if the long-click is handled, otherwise return false to indicate that regular
     * click should still be triggered when the user releases the touch.
     */
    private fun longClickedTakePhoto(): Boolean {
        if (MyDebug.LOG) Log.d(TAG, "longClickedTakePhoto")
        if (preview?.isVideo == true) {
            // no long-click action for video mode
        } else if (supportsFastBurst()) {
            // need to check whether fast burst is supported (including for the current resolution),
            // in case we're in Standard photo mode
            val current_size = preview?.getCurrentPictureSize()
            if (current_size != null && current_size.supports_burst) {
                val photo_mode = applicationInterface!!.photoMode
                if (photo_mode == PhotoMode.Standard && applicationInterface!!.isRawOnly(photo_mode)) {
                    if (MyDebug.LOG) Log.d(TAG, "fast burst not supported in RAW-only mode")
                    // in JPEG+RAW mode, a continuous fast burst will only produce JPEGs which is fine; but in RAW only mode,
                    // no images at all would be saved! (Or we could switch to produce JPEGs anyway, but this seems misleading
                    // in RAW only mode.)
                } else if (photo_mode == PhotoMode.Standard || photo_mode == PhotoMode.FastBurst) {
                    this.takePicturePressed(false, true)
                    return true
                }
            } else {
                if (MyDebug.LOG) Log.d(TAG, "fast burst not supported for this resolution")
            }
        } else {
            if (MyDebug.LOG) Log.d(TAG, "fast burst not supported")
        }
        // return false, so a regular click will still be triggered when the user releases the touch
        return false
    }

    fun clickedTakePhoto() {
        if (MyDebug.LOG) Log.d(TAG, "clickedTakePhoto")
        this.takePicture(false)
    }

    /** User has clicked button to take a photo snapshot whilst video recording.
     */
    fun clickedTakePhotoVideoSnapshot() {
        if (MyDebug.LOG) Log.d(TAG, "clickedTakePhotoVideoSnapshot")
        this.takePicture(true)
    }

    fun clickedPauseVideo() {
        if (MyDebug.LOG) Log.d(TAG, "clickedPauseVideo")
        pauseVideo()
    }

    fun pauseVideo() {
        if (MyDebug.LOG) Log.d(TAG, "pauseVideo")
        if (preview?.isVideoRecording == true) { // just in case
            preview?.pauseVideo()
            mainUI?.setPauseVideoContentDescription()
        }
    }

    fun clickedCancelPanorama() {
        if (MyDebug.LOG) Log.d(TAG, "clickedCancelPanorama")
        applicationInterface!!.stopPanorama(true)
    }

    fun clickedCycleRaw() {
        if (MyDebug.LOG) Log.d(TAG, "clickedCycleRaw")

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        var newValue: String? = null
        when (sharedPreferences.getString(PreferenceKeys.RawPreferenceKey, "preference_raw_no")) {
            "preference_raw_no" -> newValue = "preference_raw_yes"
            "preference_raw_yes" -> newValue = "preference_raw_only"
            "preference_raw_only" -> newValue = "preference_raw_no"
            else -> Log.e(TAG, "unrecognised raw preference")
        }
        if (newValue != null) {
            sharedPreferences.edit {
                putString(PreferenceKeys.RawPreferenceKey, newValue)
            }

            mainUI?.updateCycleRawIcon()
            applicationInterface!!.drawPreview?.updateSettings()
            preview?.reopenCamera() // needed for RAW options to take effect
        }
    }

    fun clickedStoreLocation() {
        if (MyDebug.LOG) Log.d(TAG, "clickedStoreLocation")
        var value = applicationInterface!!.geotaggingPref
        value = !value

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            putBoolean(PreferenceKeys.LocationPreferenceKey, value)
        }

        mainUI?.updateStoreLocationIcon()
        applicationInterface!!.drawPreview?.updateSettings() // because we cache the geotagging setting
        initLocation() // required to enable or disable GPS, also requests permission if necessary
        this.closePopup()

        val message =
            getResources().getString(R.string.preference_location) + ": " + getResources().getString(
                if (value) R.string.on else R.string.off
            )
        preview?.showToast(store_location_toast, message, true)
    }

    fun clickedTextStamp() {
        if (MyDebug.LOG) Log.d(TAG, "clickedTextStamp")
        this.closePopup()

        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(R.string.preference_textstamp)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.alertdialog_edittext, null)
        val editText = dialogView.findViewById<EditText>(R.id.edit_text)
        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
        editText.hint = getResources().getString(R.string.preference_textstamp)
        editText.setText(applicationInterface!!.textStampPref)
        alertDialog.setView(dialogView)
        alertDialog.setPositiveButton(
            android.R.string.ok
        ) { dialogInterface, i ->
            if (MyDebug.LOG) Log.d(TAG, "custom text stamp clicked okay")

            val customText = editText.text.toString()
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            sharedPreferences.edit {
                putString(PreferenceKeys.TextStampPreferenceKey, customText)
            }

            mainUI?.updateTextStampIcon()
        }
        alertDialog.setNegativeButton(android.R.string.cancel, null)

        val alert = alertDialog.create()
        alert.setOnDismissListener {
            if (MyDebug.LOG) Log.d(TAG, "custom stamp text dialog dismissed")
            setWindowFlagsForCamera()
            showPreview(true)
        }

        showPreview(false)
        setWindowFlagsForSettings()
        showAlert(alert)
    }

    fun clickedStamp() {
        if (MyDebug.LOG) Log.d(TAG, "clickedStamp")

        this.closePopup()

        var value = applicationInterface!!.stampPref == "preference_stamp_yes"
        value = !value
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            putString(
                PreferenceKeys.StampPreferenceKey,
                if (value) "preference_stamp_yes" else "preference_stamp_no"
            )
        }

        mainUI?.updateStampIcon()
        applicationInterface!!.drawPreview?.updateSettings()
        preview?.showToast(
            stamp_toast, if (value) R.string.stamp_enabled else R.string.stamp_disabled, true
        )
    }

    fun clickedFocusPeaking() {
        if (MyDebug.LOG) Log.d(TAG, "clickedFocusPeaking")
        var value = applicationInterface!!.focusPeakingPref
        value = !value

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            putString(
                PreferenceKeys.FocusPeakingPreferenceKey,
                if (value) "preference_focus_peaking_on" else "preference_focus_peaking_off"
            )
        }

        mainUI?.updateFocusPeakingIcon()
        applicationInterface!!.drawPreview?.updateSettings() // needed to update focus peaking
    }

    fun clickedAutoLevel() {
        if (MyDebug.LOG) Log.d(TAG, "clickedAutoLevel")
        var value = applicationInterface!!.autoStabilisePref
        value = !value

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            putBoolean(PreferenceKeys.AutoStabilisePreferenceKey, value)
        }

        var doneDialog = false
        if (value) {
            val doneAutoStabiliseInfo =
                sharedPreferences.contains(PreferenceKeys.AutoStabiliseInfoPreferenceKey)
            if (!doneAutoStabiliseInfo) {
                mainUI?.showInfoDialog(
                    R.string.preference_auto_stabilise,
                    R.string.auto_stabilise_info,
                    PreferenceKeys.AutoStabiliseInfoPreferenceKey
                )
                doneDialog = true
            }
        }

        if (!doneDialog) {
            val message =
                getResources().getString(R.string.preference_auto_stabilise) + ": " + getResources().getString(
                    if (value) R.string.on else R.string.off
                )
            preview?.showToast(this.changedAutoStabiliseToastBoxer, message, true)
        }

        mainUI?.updateAutoLevelIcon()
        applicationInterface?.drawPreview?.updateSettings() // because we cache the auto-stabilise setting
        this.closePopup()
    }

    fun clickedCycleFlash() {
        if (MyDebug.LOG) Log.d(TAG, "clickedCycleFlash")

        preview?.cycleFlash(true, true)
        mainUI?.updateCycleFlashIcon()
    }

    fun clickedFaceDetection() {
        if (MyDebug.LOG) Log.d(TAG, "clickedFaceDetection")

        this.closePopup()

        var value = applicationInterface!!.getFaceDetectionPref()
        value = !value
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            putBoolean(PreferenceKeys.FaceDetectionPreferenceKey, value)
        }

        mainUI?.updateFaceDetectionIcon()
        preview?.showToast(
            stamp_toast,
            if (value) R.string.face_detection_enabled else R.string.face_detection_disabled,
            true
        )
        block_startup_toast =
            true // so the toast from reopening camera is suppressed, otherwise it conflicts with the face detection toast
        preview?.reopenCamera()
    }

    fun clickedAudioControl() {
        if (MyDebug.LOG) Log.d(TAG, "clickedAudioControl")
        // check hasAudioControl just in case!
        if (!hasAudioControl()) {
            if (MyDebug.LOG) Log.e(TAG, "clickedAudioControl, but hasAudioControl returns false!")
            return
        }
        this.closePopup()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val audio_control: String =
            sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none")!!/*if( audio_control.equals("voice") && speechControl.hasSpeechRecognition() ) {
            if( speechControl.isStarted() ) {
                speechControl.stopListening();
            }
            else {
                boolean has_audio_permission = true;
                if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
                    if( MyDebug.LOG )
                        Log.d(TAG, "check for record audio permission");
                    if( ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "record audio permission not available");
                        applicationInterface.requestRecordAudioPermission();
                        has_audio_permission = false;
                    }
                }
                if( has_audio_permission ) {
                    speechControl.showToast(true);
                    speechControl.startSpeechRecognizerIntent();
                    speechControl.speechRecognizerStarted();
                }
            }
        }
        else*/
        if (audio_control == "noise") {
            if (audio_listener != null) {
                freeAudioListener(false)
            } else {
                startAudioListener()
            }
        }
    }

    val nextCameraId: Int
        /* Returns the cameraId that the "Switch camera" button will switch to.
              * Note that this may not necessarily be the next camera ID, on multi camera devices (if
              * isMultiCamEnabled() returns true).
              */
        get() {
            if (MyDebug.LOG) Log.d(TAG, "getNextCameraId")
            var cameraId = this.actualCameraId
            if (MyDebug.LOG) Log.d(TAG, "current cameraId: $cameraId")
            if (this.preview?.canSwitchCamera() == true) {
                if (this.isMultiCamEnabled) {
                    // don't use preview.cameraController, as it may be null if user quickly switches between cameras
                    when (preview?.cameraControllerManager?.getFacing(cameraId)) {
                        Facing.FACING_BACK -> if (frontCameraIds!!.isNotEmpty()) cameraId =
                            frontCameraIds!![0]!!
                        else if (otherCameraIds!!.isNotEmpty()) cameraId = otherCameraIds!![0]!!

                        Facing.FACING_FRONT -> if (otherCameraIds!!.isNotEmpty()) cameraId =
                            otherCameraIds!![0]!!
                        else if (back_camera_ids!!.isNotEmpty()) cameraId = back_camera_ids!![0]!!

                        else -> if (back_camera_ids!!.isNotEmpty()) cameraId =
                            back_camera_ids!![0]!!
                        else if (frontCameraIds!!.isNotEmpty()) cameraId = frontCameraIds!![0]!!
                    }
                } else {
                    val nCameras = preview?.cameraControllerManager?.getNumberOfCameras() ?: 1
                    cameraId = (cameraId + 1) % nCameras
                }
            }
            if (MyDebug.LOG) Log.d(TAG, "next cameraId: $cameraId")
            return cameraId
        }

    private fun pushCameraIdToast(cameraId: Int, cameraIdSPhysical: String?) {
        if (MyDebug.LOG) Log.d(TAG, "pushCameraIdToast: $cameraId")
        if ((preview?.cameraControllerManager?.getNumberOfCameras()
                ?: 1) > 2 || cameraIdSPhysical != null
        ) {
            // telling the user which camera is pointless for only two cameras, but on devices that now
            // expose many cameras it can be confusing, so show a toast to at least display the id
            // similarly we want to show a toast if using a physical camera, so user doesn't forget
            val description = if (cameraIdSPhysical != null) {
                preview?.cameraControllerManager?.getDescription(
                    null, this, cameraIdSPhysical, true, true
                )
            } else {
                preview?.cameraControllerManager?.getDescription(this, cameraId)
            }
            if (description != null) {
                var toast_string = description
                if (cameraIdSPhysical == null)  // only add the ID if not a physical camera
                    toast_string += ": " + getResources().getString(R.string.camera_id) + " " + cameraId
                //preview.showToast(null, toast_string);
                this.push_info_toast_text = toast_string
            }
        }
    }

    fun userSwitchToCamera(cameraId: Int, cameraIdSPhysical: String?) {
        if (MyDebug.LOG) Log.d(TAG, "userSwitchToCamera: $cameraId / $cameraIdSPhysical")
//        val switchCameraButton = binding.switchCamera
//        val switchMultiCameraButton = binding.switchMultiCamera
        // prevent slowdown if user repeatedly clicks:
//        switchCameraButton.isEnabled = false
//        switchMultiCameraButton.isEnabled = false
        applicationInterface!!.reset(true)
        this.applicationInterface!!.drawPreview?.setDimPreview(true)
        this.preview?.setCamera(cameraId, cameraIdSPhysical)
//        switchCameraButton.isEnabled = true
//        switchMultiCameraButton.isEnabled = true
        // no need to call mainUI.setSwitchCameraContentDescription - this will be called from Preview.cameraSetup when the
        // new camera is opened
    }

    /**
     * Selects the next camera on the phone - in practice, switches between
     * front and back cameras
     */
    fun clickedSwitchCamera() {
        if (MyDebug.LOG) Log.d(TAG, "clickedSwitchCamera")
        if (preview?.isOpeningCamera == true) {
            if (MyDebug.LOG) Log.d(TAG, "already opening camera in background thread")
            return
        }
        this.closePopup()
        if (this.preview?.canSwitchCamera() == true) {
            val cameraId = this.nextCameraId
            if (!this.isMultiCamEnabled) {
                pushCameraIdToast(cameraId, null)
            } else {
                // In multi-cam mode, no need to show the toast when just switching between front and back cameras.
                // But it is useful to clear an active fake toast, otherwise have issue if the user uses
                // clickedSwitchMultiCamera() (which displays a fake toast for the camera via the info toast), then
                // immediately uses clickedSwitchCamera() - the toast for the wrong camera will still be lingering
                // until it expires, which looks a bit strange.
                // (If using non-fake toasts, this isn't an issue, at least on Android 10+, as now toasts seem to
                // disappear when the user touches the screen anyway.)
                preview?.clearActiveFakeToast()
            }
            userSwitchToCamera(cameraId, null)

            if (applicationInterface != null && preview != null) {
                viewModel.checkSwitchCamera(this, applicationInterface!!, preview!!)
            }
        }
    }

    /** Returns list of logical cameras with same facing as the supplied camera_id.
     */
    fun getSameFacingLogicalCameras(camera_id: Int): MutableList<Int?> {
        val logical_camera_ids: MutableList<Int?> = ArrayList<Int?>()
        val this_facing = preview?.cameraControllerManager?.getFacing(camera_id) ?: 0
        val numberOfCameras = preview?.cameraControllerManager?.getNumberOfCameras() ?: 1
        for (i in 0..<numberOfCameras) {
            if (preview?.cameraControllerManager?.getFacing(i) != this_facing) {
                // only show cameras with same facing
                continue
            }
            logical_camera_ids.add(i)
        }
        return logical_camera_ids
    }

    /** User can long-click on switch multi cam icon to bring up a menu to switch to any camera.
     * Update: from v1.53 onwards with support for exposing physical lens, we always call this with
     * a regular click on the switch multi cam icon.
     */
    fun clickedSwitchMultiCamera() {
        if (MyDebug.LOG) Log.d(TAG, "clickedSwitchMultiCamera")

        var debug_time: Long = 0
        if (MyDebug.LOG) {
            debug_time = System.currentTimeMillis()
        }
        //showPreview(false);
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(R.string.choose_camera)

        val curr_camera_id = this.actualCameraId
        val logical_camera_ids = getSameFacingLogicalCameras(curr_camera_id)
        if (MyDebug.LOG) Log.d(
            TAG,
            "clickedSwitchMultiCamera: time after logical_camera_ids: " + (System.currentTimeMillis() - debug_time)
        )

        val n_logical_cameras = logical_camera_ids.size
        var n_cameras = n_logical_cameras
        if (preview?.hasPhysicalCameras() == true) {
            n_cameras += preview?.physicalCameras?.size ?: 0
            //n_cameras++; // for the info message
        }
        val items = arrayOfNulls<CharSequence>(n_cameras)
        val items_logical_camera_id = IntArray(n_cameras)
        val items_physical_camera_id = arrayOfNulls<String>(n_cameras)
        var index = 0
        var selected = -1
        val curr_physical_camera_id = applicationInterface!!.cameraIdSPhysicalPref
        for (i in 0..<n_logical_cameras) {
            val logical_camera_id: Int = logical_camera_ids[i]!!
            if (MyDebug.LOG) Log.d(
                TAG,
                "clickedSwitchMultiCamera: time before getDescription: " + (System.currentTimeMillis() - debug_time)
            )
            var camera_name =
                "$logical_camera_id: " + preview?.cameraControllerManager?.getDescription(
                    this, logical_camera_id
                )
            if (MyDebug.LOG) Log.d(
                TAG,
                "clickedSwitchMultiCamera: time after getDescription: " + (System.currentTimeMillis() - debug_time)
            )
            if (logical_camera_id == curr_camera_id) {
                // this is the current logical camera
                if (preview?.hasPhysicalCameras() == true) {
                    camera_name += " (" + getResources().getString(R.string.auto_lens) + ")"
                }
                if (curr_physical_camera_id == null) {
                    // the logical camera is being used directly
                    selected = index
                    val html_camera_name = "<b>$camera_name</b>"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        items[index] = Html.fromHtml(html_camera_name, Html.FROM_HTML_MODE_LEGACY)
                    } else {
                        items[index] = Html.fromHtml(html_camera_name)
                    }
                } else {
                    // a physical camera is in use, so don't bold this entry
                    items[index] = camera_name
                }
                items_logical_camera_id[index] = logical_camera_id
                items_physical_camera_id[index] = null
                index++

                if (preview?.hasPhysicalCameras() == true) {
                    // also add the physical cameras that underlie the current logical camera
                    val physical_camera_ids = preview?.physicalCameras ?: arrayListOf()

                    // sort by view angle
                    class PhysicalCamera public constructor(val id: String?) {
                        val description: String?
                        val view_angle: SizeF

                        init {
                            val info = CameraControllerManager.CameraInfo()
                            this.description = preview?.cameraControllerManager?.getDescription(
                                info, this@MainActivity, id, false, true
                            )
                            this.view_angle = info.view_angle
                        }
                    }

                    val physical_cameras = ArrayList<PhysicalCamera>()
                    for (physical_id in physical_camera_ids) {
                        if (MyDebug.LOG) Log.d(
                            TAG,
                            "clickedSwitchMultiCamera: time before getDescription: " + (System.currentTimeMillis() - debug_time)
                        )
                        physical_cameras.add(PhysicalCamera(physical_id))
                        if (MyDebug.LOG) Log.d(
                            TAG,
                            "clickedSwitchMultiCamera: time after getDescription: " + (System.currentTimeMillis() - debug_time)
                        )
                    }
                    run {
                        Collections.sort<PhysicalCamera?>(
                            physical_cameras, object : Comparator<PhysicalCamera?> {
                                override fun compare(
                                    o1: PhysicalCamera?, o2: PhysicalCamera?
                                ): Int {
                                    if (o1 != null && o2 != null) {
                                        val diff = o2.view_angle.width - o1.view_angle.width
                                        if (abs(diff) < 1.0e-5f) return 0
                                        else if (diff > 0.0f) return 1
                                        else return -1
                                    }
                                    return -1
                                }
                            })
                    }

                    var j = 0
                    val indent = "&nbsp;&nbsp;&nbsp;&nbsp;"
                    for (physical_camera in physical_cameras) {
                        val physical_id = physical_camera.id
                        camera_name =
                            getResources().getString(R.string.lens) + " " + j + ": " + physical_camera.description
                        val html_camera_name: String?
                        if (curr_physical_camera_id != null && curr_physical_camera_id == physical_id) {
                            // this is the current physical camera
                            selected = index
                            html_camera_name = "$indent<b>$camera_name</b>"
                        } else {
                            html_camera_name = indent + camera_name
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            items[index] =
                                Html.fromHtml(html_camera_name, Html.FROM_HTML_MODE_LEGACY)
                        } else {
                            items[index] = Html.fromHtml(html_camera_name)
                        }
                        items_logical_camera_id[index] = logical_camera_id
                        items_physical_camera_id[index] = physical_id
                        index++

                        j++
                    }
                }
            } else {
                items[index] = camera_name
                items_logical_camera_id[index] = logical_camera_id
                items_physical_camera_id[index] = null
                index++
            }
        }
        if (MyDebug.LOG) Log.d(
            TAG,
            "clickedSwitchMultiCamera: time after building menu: " + (System.currentTimeMillis() - debug_time)
        )

        alertDialog.setSingleChoiceItems(items, selected, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface, which: Int) {
                if (MyDebug.LOG) Log.d(TAG, "selected: $which")
                val logical_camera = items_logical_camera_id[which]
                val physical_camera = items_physical_camera_id[which]
                if (MyDebug.LOG) {
                    Log.d(TAG, "logical_camera: $logical_camera")
                    Log.d(TAG, "physical_camera: $physical_camera")
                }
                val n_cameras = preview?.cameraControllerManager?.getNumberOfCameras() ?: 1
                if (logical_camera >= 0 && logical_camera < n_cameras) {
                    if (preview?.isOpeningCamera == true) {
                        if (MyDebug.LOG) Log.d(TAG, "already opening camera in background thread")
                        return
                    }
                    this@MainActivity.closePopup()
                    if (this@MainActivity.preview?.canSwitchCamera() == true) {
                        pushCameraIdToast(logical_camera, physical_camera)
                        userSwitchToCamera(logical_camera, physical_camera)
                    }
                }
                dialog.dismiss() // need to explicitly dismiss for setSingleChoiceItems
            }
        })
        val dialog = alertDialog.create()
        dialog.window?.setWindowAnimations(R.style.DialogAnimation)
        dialog.show()
    }

    /**
     * Toggles Photo/Video mode
     */
    fun clickedSwitchVideo() {
        if (MyDebug.LOG) Log.d(TAG, "clickedSwitchVideo")
        this.closePopup()

        // In practice stopping the gyro sensor shouldn't be needed as (a) we don't show the switch
        // photo/video icon when recording, (b) at the time of writing switching to video mode
        // reopens the camera, which will stop panorama recording anyway, but we do this just to be
        // safe.
        applicationInterface?.stopPanorama(true)

        applicationInterface?.reset(false)
        this.applicationInterface?.drawPreview?.setDimPreview(true)
        this.preview?.switchVideo(false, true)

        mainUI?.setTakePhotoIcon()

        // ensure icons invisible if they're affected by being in video mode or not (e.g., on-screen RAW icon)
        // (if enabling them, we'll make the icon visible later on)
        checkDisableGUIIcons()

        if (!block_startup_toast) {
            this.showPhotoVideoToast(true)
        }
    }

    fun clickedWhiteBalanceLock() {
        if (MyDebug.LOG) Log.d(TAG, "clickedWhiteBalanceLock")
        this.preview?.toggleWhiteBalanceLock()
        mainUI?.updateWhiteBalanceLockIcon()
        preview?.showToast(
            white_balance_lock_toast,
            if (preview?.isWhiteBalanceLocked == true) R.string.white_balance_locked else R.string.white_balance_unlocked,
            true
        )
    }

    fun clickedExposureLock() {
        if (MyDebug.LOG) Log.d(TAG, "clickedExposureLock")
        this.preview?.toggleExposureLock()
        mainUI?.updateExposureLockIcon()
        preview?.showToast(
            exposure_lock_toast,
            if (preview?.isExposureLocked == true) R.string.exposure_locked else R.string.exposure_unlocked,
            true
        )
    }

    fun clickedExposure() {
        if (MyDebug.LOG) Log.d(TAG, "clickedExposure")
        mainUI?.toggleExposureUI()
    }

    fun clickedSettings() {
        if (MyDebug.LOG) Log.d(TAG, "clickedSettings")
        KeyguardUtils.requireKeyguard(this, Runnable { this.openSettings() })
    }

    fun popupIsOpen(): Boolean {
        return mainUI?.popupIsOpen() ?: false
    }

    fun closePopup() {
        mainUI?.closePopup()
    }

    fun getPreloadedBitmap(resource: Int): Bitmap? {
        return this.preloaded_bitmap_resources.get(resource)
    }

    fun clickedPopupSettings() {
        if (MyDebug.LOG) Log.d(TAG, "clickedPopupSettings")
//        mainUI?.togglePopupSettings()
    }

    private val preferencesListener = PreferencesListener()

    /** Keeps track of changes to SharedPreferences.
     */
    internal inner class PreferencesListener : OnSharedPreferenceChangeListener {
        private var any_significant_change =
            false // whether any changes that require updateForSettings have been made since startListening()
        private var any_change = false // whether any changes have been made since startListening()

        fun startListening() {
            if (MyDebug.LOG) Log.d(TAG, "startListening")
            any_significant_change = false
            any_change = false

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            // n.b., registerOnSharedPreferenceChangeListener warns that we must keep a reference to the listener (which
            // is this class) as long as we want to listen for changes, otherwise the listener may be garbage collected!
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        fun stopListening() {
            if (MyDebug.LOG) Log.d(TAG, "stopListening")
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (MyDebug.LOG) Log.d(TAG, "onSharedPreferenceChanged: " + key)

            if (key == null) {
                // on Android 11+, when targetting Android 11+, this method is called with key==null
                // if preferences are cleared (see testSettings(), or when doing "Reset settings")
                return
            }

            any_change = true

            when (key) {
                "preference_timer", "preference_burst_mode", "preference_burst_interval", "preference_touch_capture", "preference_pause_preview", "preference_shutter_sound", "preference_timer_beep", "preference_timer_speak", "preference_volume_keys", "preference_audio_noise_control_sensitivity", "preference_lock_orientation", "preference_using_saf", "preference_save_photo_prefix", "preference_save_video_prefix", "preference_save_zulu_time", "preference_show_when_locked", "preference_startup_focus", "ghost_image_alpha", "preference_focus_assist", "preference_show_zoom", "preference_show_angle", "preference_show_angle_line", "preference_show_pitch_lines", "preference_angle_highlight_color", "preference_show_battery", "preference_show_time", "preference_free_memory", "preference_show_iso", "preference_histogram", "preference_zebra_stripes", "preference_zebra_stripes_foreground_color", "preference_zebra_stripes_background_color", "preference_focus_peaking", "preference_focus_peaking_color", "preference_show_video_max_amp", "preference_grid", "preference_crop_guide", "preference_thumbnail_animation", "preference_take_photo_border", "preference_show_toasts", "preference_show_whats_new", "preference_keep_display_on", "preference_max_brightness", "preference_hdr_tonemapping", "preference_hdr_contrast_enhancement", "preference_panorama_crop", "preference_front_camera_mirror", "preference_exif_artist", "preference_exif_copyright", "preference_stamp", "preference_stamp_dateformat", "preference_stamp_timeformat", "preference_stamp_gpsformat", "preference_stamp_geo_address", "preference_units_distance", "preference_textstamp", "preference_stamp_fontsize", "preference_stamp_font_color", "preference_stamp_style", "preference_background_photo_saving", "preference_record_audio", "preference_record_audio_src", "preference_record_audio_channels", "preference_lock_video", "preference_video_subtitle", "preference_video_low_power_check", "preference_video_flash", "preference_require_location" ->                     //case "preference_antibanding": // need to set up camera controller
                    //case "preference_edge_mode": // need to set up camera controller
                    //case "preference_noise_reduction_mode": // need to set up camera controller
                    //case "preference_camera_api": // no point whitelisting as we restart anyway
                    if (MyDebug.LOG) Log.d(TAG, "this change doesn't require update")

                PreferenceKeys.EnableRemote -> bluetoothRemoteControl!!.startRemoteControl()
                PreferenceKeys.RemoteName -> {
                    // The remote address changed, restart the service
                    if (bluetoothRemoteControl!!.remoteEnabled()) bluetoothRemoteControl!!.stopRemoteControl()
                    bluetoothRemoteControl!!.startRemoteControl()
                }

                PreferenceKeys.WaterType -> {
                    val wt = sharedPreferences.getBoolean(PreferenceKeys.WaterType, true)
                    waterDensity = if (wt) WATER_DENSITY_SALTWATER else WATER_DENSITY_FRESHWATER
                }

                else -> {
                    if (MyDebug.LOG) Log.d(TAG, "this change does require update")
                    any_significant_change = true
                }
            }
        }

        fun anyChange(): Boolean {
            return any_change
        }

        fun anySignificantChange(): Boolean {
            return any_significant_change
        }
    }

    fun openSettings() {
        if (MyDebug.LOG) Log.d(TAG, "openSettings")
        closePopup() // important to close the popup to avoid confusing with back button callbacks
        preview?.cancelTimer() // best to cancel any timer, in case we take a photo while settings window is open, or when changing settings
        preview?.cancelRepeat() // similarly cancel the auto-repeat mode!
        preview?.stopVideo(false) // important to stop video, as we'll be changing camera parameters when the settings window closes
        applicationInterface!!.stopPanorama(true) // important to stop panorama recording, as we might end up as we'll be changing camera parameters when the settings window closes
        stopAudioListeners()
        // close back handler callbacks (so back button is enabled again when going to settings) - in theory shouldn't be needed as all of these should
        // be disabled now, but just in case:
        this.enablePopupOnBackPressedCallback(false)
        this.enablePausePreviewOnBackPressedCallback(false)
        this.enableScreenLockOnBackPressedCallback(false)

        val bundle = Bundle()
        bundle.putBoolean("edge_to_edge_mode", edgeToEdgeMode)
        bundle.putInt("cameraId", this.preview?.getCameraId() ?: 0)
        bundle.putString(
            "cameraIdSPhysical", this.applicationInterface!!.cameraIdSPhysicalPref
        )
        bundle.putInt("nCameras", preview?.cameraControllerManager?.getNumberOfCameras() ?: 1)
        bundle.putBoolean("camera_open", this.preview?.cameraController != null)
        bundle.putString("camera_api", this.preview?.getCameraAPI())
        bundle.putBoolean("using_android_l", this.preview?.usingCamera2API() ?: false)
        if (this.preview?.cameraController != null) {
            bundle.putInt(
                "camera_orientation", this.preview?.cameraController?.getCameraOrientation() ?: 0
            )
        }
        bundle.putString(
            "photo_mode_string", getPhotoModeString(applicationInterface!!.photoMode, true)
        )
        bundle.putBoolean("supports_auto_stabilise", this.supports_auto_stabilise)
        bundle.putBoolean("supports_flash", this.preview?.supportsFlash() == true)
        bundle.putBoolean("supports_force_video_4k", this.supports_force_video_4k)
        bundle.putBoolean("supports_camera2", this.supports_camera2)
        bundle.putBoolean("supports_face_detection", this.preview?.supportsFaceDetection() == true)
        bundle.putBoolean("supports_jpeg_r", this.preview?.supportsJpegR() == true)
        bundle.putBoolean("supports_raw", this.preview?.supportsRaw() == true)
        bundle.putBoolean("supports_burst_raw", this.supportsBurstRaw())
        bundle.putBoolean("supports_optimise_focus_latency", this.supportsOptimiseFocusLatency())
        bundle.putBoolean("supports_preshots", this.supportsPreShots())
        bundle.putBoolean("supports_hdr", this.supportsHDR())
        bundle.putBoolean("supports_nr", this.supportsNoiseReduction())
        bundle.putBoolean("supports_panorama", this.supportsPanorama())
        bundle.putBoolean("has_gyro_sensors", applicationInterface!!.gyroSensor.hasSensors())
        bundle.putBoolean("supports_expo_bracketing", this.supportsExpoBracketing())
        bundle.putBoolean("supports_preview_bitmaps", this.supportsPreviewBitmaps())
        bundle.putInt("max_expo_bracketing_n_images", this.maxExpoBracketingNImages())
        bundle.putBoolean(
            "supports_exposure_compensation", this.preview?.supportsExposures() == true
        )
        bundle.putInt("exposure_compensation_min", this.preview?.getMinimumExposure() ?: 0)
        bundle.putInt("exposure_compensation_max", this.preview?.getMaximumExposure() ?: 0)
        bundle.putBoolean("supports_iso_range", this.preview?.supportsISORange() == true)
        bundle.putInt("iso_range_min", this.preview?.getMinimumISO() ?: 0)
        bundle.putInt("iso_range_max", this.preview?.getMaximumISO() ?: 0)
        bundle.putBoolean("supports_exposure_time", this.preview?.supportsExposureTime() == true)
        bundle.putBoolean("supports_exposure_lock", this.preview?.supportsExposureLock() == true)
        bundle.putBoolean(
            "supports_white_balance_lock", this.preview?.supportsWhiteBalanceLock() == true
        )
        bundle.putLong("exposure_time_min", this.preview?.getMinimumExposureTime() ?: 0)
        bundle.putLong("exposure_time_max", this.preview?.getMaximumExposureTime() ?: 0)
        bundle.putBoolean(
            "supports_white_balance_temperature",
            this.preview?.supportsWhiteBalanceTemperature() == true
        )
        bundle.putInt(
            "white_balance_temperature_min", this.preview?.getMinimumWhiteBalanceTemperature() ?: 0
        )
        bundle.putInt(
            "white_balance_temperature_max", this.preview?.getMaximumWhiteBalanceTemperature() ?: 0
        )
        bundle.putBoolean("is_multi_cam", this.isMultiCam)
        bundle.putBoolean("has_physical_cameras", this.preview?.hasPhysicalCameras() == true)
        bundle.putBoolean(
            "supports_optical_stabilization", this.preview?.supportsOpticalStabilization() == true
        )
        bundle.putBoolean(
            "optical_stabilization_enabled", this.preview?.getOpticalStabilization() == true
        )
        bundle.putBoolean(
            "supports_video_stabilization", this.preview?.supportsVideoStabilization() == true
        )
        bundle.putBoolean(
            "video_stabilization_enabled", this.preview?.getVideoStabilization() == true
        )
        bundle.putBoolean(
            "can_disable_shutter_sound", this.preview?.canDisableShutterSound() == true
        )
        bundle.putInt("tonemap_max_curve_points", this.preview?.getTonemapMaxCurvePoints() ?: 0)
        bundle.putBoolean("supports_tonemap_curve", this.preview?.supportsTonemapCurve() == true)
        bundle.putBoolean(
            "supports_photo_video_recording", this.preview?.supportsPhotoVideoRecording() == true
        )
        bundle.putFloat("camera_view_angle_x", preview?.getViewAngleX(false) ?: 0f)
        bundle.putFloat("camera_view_angle_y", preview?.getViewAngleY(false) ?: 0f)
        bundle.putFloat("min_zoom_factor", preview?.getMinZoomRatio() ?: 0f)
        bundle.putFloat("max_zoom_factor", preview?.getMaxZoomRatio() ?: 0f)

        putBundleExtra(bundle, "color_effects", this.preview?.getSupportedColorEffects())
        putBundleExtra(bundle, "scene_modes", this.preview?.getSupportedSceneModes())
        putBundleExtra(bundle, "white_balances", this.preview?.getSupportedWhiteBalances())
        putBundleExtra(bundle, "isos", this.preview?.getSupportedISOs())
        bundle.putInt("magnetic_accuracy", magneticSensor!!.magneticAccuracy)
        bundle.putString("iso_key", this.preview?.getISOKey())
        if (this.preview?.cameraController != null) {
            bundle.putString(
                "parameters_string", preview?.cameraController?.getParametersString() ?: ""
            )
        }
        val antibanding = this.preview?.getSupportedAntiBanding()
        putBundleExtra(bundle, "antibanding", antibanding)
        if (antibanding != null) {
            val entries_arr = arrayOfNulls<String>(antibanding.size)
            var i = 0
            for (value in antibanding) {
                entries_arr[i] = this.mainUI?.getEntryForAntiBanding(value)
                i++
            }
            bundle.putStringArray("antibanding_entries", entries_arr)
        }
        val edge_modes = this.preview?.getSupportedEdgeModes()
        putBundleExtra(bundle, "edge_modes", edge_modes)
        if (edge_modes != null) {
            val entries_arr = arrayOfNulls<String>(edge_modes.size)
            var i = 0
            for (value in edge_modes) {
                entries_arr[i] = this.mainUI?.getEntryForNoiseReductionMode(value)
                i++
            }
            bundle.putStringArray("edge_modes_entries", entries_arr)
        }
        val noise_reduction_modes = this.preview?.getSupportedNoiseReductionModes()
        putBundleExtra(bundle, "noise_reduction_modes", noise_reduction_modes)
        if (noise_reduction_modes != null) {
            val entries_arr = arrayOfNulls<String>(noise_reduction_modes.size)
            var i = 0
            for (value in noise_reduction_modes) {
                entries_arr[i] = this.mainUI?.getEntryForNoiseReductionMode(value)
                i++
            }
            bundle.putStringArray("noise_reduction_modes_entries", entries_arr)
        }

        val preview_sizes = this.preview?.getSupportedPreviewSizes()
        if (preview_sizes != null) {
            val widths = IntArray(preview_sizes.size)
            val heights = IntArray(preview_sizes.size)
            var i = 0
            for (size in preview_sizes) {
                widths[i] = size.width
                heights[i] = size.height
                i++
            }
            bundle.putIntArray("preview_widths", widths)
            bundle.putIntArray("preview_heights", heights)
        }
        bundle.putInt("preview_width", preview?.currentPreviewSize?.width ?: 512)
        bundle.putInt("preview_height", preview?.currentPreviewSize?.height ?: 512)

        // Note that we set check_burst to false, as the Settings always displays all supported resolutions (along with the "saved"
        // resolution preference, even if that doesn't support burst and we're in a burst mode).
        // This is to be consistent with other preferences, e.g., we still show RAW settings even though that might not be supported
        // for the current photo mode.
        val sizes = this.preview?.getSupportedPictureSizes(false)
        if (sizes != null) {
            val widths = IntArray(sizes.size)
            val heights = IntArray(sizes.size)
            val supports_burst = BooleanArray(sizes.size)
            var i = 0
            for (size in sizes) {
                widths[i] = size.width
                heights[i] = size.height
                supports_burst[i] = size.supports_burst
                i++
            }
            bundle.putIntArray("resolution_widths", widths)
            bundle.putIntArray("resolution_heights", heights)
            bundle.putBooleanArray("resolution_supports_burst", supports_burst)
        }
        if (preview?.getCurrentPictureSize() != null) {
            bundle.putInt("resolution_width", preview?.getCurrentPictureSize()?.width ?: 512)
            bundle.putInt("resolution_height", preview?.getCurrentPictureSize()?.height ?: 512)
        }

        //List<String> video_quality = this.preview.getVideoQualityHander().getSupportedVideoQuality();
        val fps_value =
            applicationInterface!!.getVideoFPSPref() // n.b., this takes into account slow motion mode putting us into a high frame rate
        if (MyDebug.LOG) Log.d(TAG, "fps_value: $fps_value")
        var video_quality = this.preview?.getSupportedVideoQuality(fps_value)
        if (video_quality == null || video_quality.isEmpty()) {
            Log.e(TAG, "can't find any supported video sizes for current fps!")
            // fall back to unfiltered list
            video_quality = this.preview?.videoQualityHander?.getSupportedVideoQuality()
        }
        if (video_quality != null && this.preview?.cameraController != null) {
            val video_quality_arr = arrayOfNulls<String>(video_quality.size)
            val video_quality_string_arr = arrayOfNulls<String>(video_quality.size)
            var i = 0
            for (value in video_quality) {
                video_quality_arr[i] = value
                video_quality_string_arr[i] = this.preview?.getCamcorderProfileDescription(value)
                i++
            }
            bundle.putStringArray("video_quality", video_quality_arr)
            bundle.putStringArray("video_quality_string", video_quality_string_arr)

            val is_high_speed = this.preview?.fpsIsHighSpeed(fps_value)
            bundle.putBoolean("video_is_high_speed", is_high_speed == true)
            val video_quality_preference_key = PreferenceKeys.getVideoQualityPreferenceKey(
                this.preview?.getCameraId() ?: 0,
                applicationInterface!!.cameraIdSPhysicalPref,
                is_high_speed == true
            )
            if (MyDebug.LOG) Log.d(
                TAG, "video_quality_preference_key: $video_quality_preference_key"
            )
            bundle.putString("video_quality_preference_key", video_quality_preference_key)
        }

        if (preview?.videoQualityHander?.getCurrentVideoQuality() != null) {
            bundle.putString(
                "current_video_quality", preview?.videoQualityHander?.getCurrentVideoQuality()
            )
        }
        val camcorder_profile = preview?.getVideoProfile()
        camcorder_profile?.let {
            bundle.putInt("video_frame_width", camcorder_profile.videoFrameWidth)
            bundle.putInt("video_frame_height", camcorder_profile.videoFrameHeight)
            bundle.putInt("video_bit_rate", camcorder_profile.videoBitRate)
            bundle.putInt("video_frame_rate", camcorder_profile.videoFrameRate)
            bundle.putDouble("video_capture_rate", camcorder_profile.videoCaptureRate)
        }

        bundle.putBoolean("video_high_speed", preview?.isVideoHighSpeed() == true)
        bundle.putFloat(
            "video_capture_rate_factor", applicationInterface!!.getVideoCaptureRateFactor()
        )

        val video_sizes = this.preview?.videoQualityHander?.getSupportedVideoSizes() ?: null
        if (video_sizes != null) {
            val widths = IntArray(video_sizes.size)
            val heights = IntArray(video_sizes.size)
            var i = 0
            for (size in video_sizes) {
                widths[i] = size.width
                heights[i] = size.height
                i++
            }
            bundle.putIntArray("video_widths", widths)
            bundle.putIntArray("video_heights", heights)
        }

        // set up supported fps values
        if (preview?.usingCamera2API() == true) {
            // with Camera2, we know what frame rates are supported
            val candidate_fps = intArrayOf(15, 24, 25, 30, 60, 96, 100, 120, 240)
            val video_fps: MutableList<Int?> = ArrayList<Int?>()
            val video_fps_high_speed: MutableList<Boolean?> = ArrayList<Boolean?>()
            for (fps in candidate_fps) {
                if (preview?.fpsIsHighSpeed(fps.toString()) == true) {
                    video_fps.add(fps)
                    video_fps_high_speed.add(true)
                } else if (this.preview?.getVideoQualityHander()
                        ?.videoSupportsFrameRate(fps) == true
                ) {
                    video_fps.add(fps)
                    video_fps_high_speed.add(false)
                }
            }
            val video_fps_array = IntArray(video_fps.size)
            for (i in video_fps.indices) {
                video_fps_array[i] = video_fps[i]!!
            }
            bundle.putIntArray("video_fps", video_fps_array)
            val video_fps_high_speed_array = BooleanArray(video_fps_high_speed.size)
            for (i in video_fps_high_speed.indices) {
                video_fps_high_speed_array[i] = video_fps_high_speed[i]!!
            }
            bundle.putBooleanArray("video_fps_high_speed", video_fps_high_speed_array)
        } else {
            // with old API, we don't know what frame rates are supported, so we make it up and let the user try
            // probably shouldn't allow 120fps, but we did in the past, and there may be some devices where this did work?
            val video_fps = intArrayOf(15, 24, 25, 30, 60, 96, 100, 120)
            bundle.putIntArray("video_fps", video_fps)
            val video_fps_high_speed_array = BooleanArray(video_fps.size)
            for (i in video_fps.indices) {
                video_fps_high_speed_array[i] =
                    false // no concept of high speed frame rates in old API
            }
            bundle.putBooleanArray("video_fps_high_speed", video_fps_high_speed_array)
        }

        putBundleExtra(bundle, "flash_values", this.preview?.supportedFlashValues)
        putBundleExtra(bundle, "focus_values", this.preview?.supportedFocusValues)

        preferencesListener.startListening()

        showPreview(false)
        setWindowFlagsForSettings() // important to do after passing camera info into bundle, since this will close the camera
        val fragment = MyPreferenceFragment()
        fragment.setArguments(bundle)
        // use commitAllowingStateLoss() instead of commit(), does to "java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState" crash seen on Google Play
        // see http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-wit
        getFragmentManager().beginTransaction()
            .add(android.R.id.content, fragment, "PREFERENCE_FRAGMENT").addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragment, pref: Preference): Boolean {
        if (MyDebug.LOG) {
            Log.d(TAG, "onPreferenceStartFragment")
            Log.d(TAG, "pref: " + pref.getFragment())
        }

        // instantiate the new fragment
        //final Bundle args = pref.getExtras();
        // we want to pass the caller preference fragment's bundle to the new sub-screen (this will be a
        // copy of the bundle originally created in openSettings()
        val args = Bundle(caller.getArguments())

        val fragment = Fragment.instantiate(this, pref.getFragment(), args)
        fragment.setTargetFragment(caller, 0)
        if (MyDebug.LOG) Log.d(TAG, "replace fragment")/*getFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();*/
        getFragmentManager().beginTransaction()
            .add(android.R.id.content, fragment, "PREFERENCE_FRAGMENT_" + pref.getFragment())
            .addToBackStack(null).commitAllowingStateLoss()

        /*
        // AndroidX version:
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // replace the existing fragment with the new fragment:
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, fragment)
                .addToBackStack(null)
                .commit();
         */
        return true
    }

    /** Must be called when an settings (as stored in SharedPreferences) are made, so we can update the
     * camera, and make any other necessary changes.
     * @param update_camera Whether the camera needs to be updated. Can be set to false if we know changes
     * haven't been made to the camera settings, or we already reopened it.
     * @param toast_message If non-null, display this toast instead of the usual camera "startup" toast
     * that's shown in showPhotoVideoToast(). If non-null but an empty string, then
     * this means no toast is shown at all.
     * @param keep_popup    If false, the popup will be closed and destroyed. Set to true if you're sure
     * that the changed setting isn't one that requires the PopupView to be recreated
     * @param allow_dim     If true, for Camera2 API a dimming effect will be applied if updating the
     * camera.
     */
    @JvmOverloads
    fun updateForSettings(
        update_camera: Boolean,
        toast_message: String? = null,
        keep_popup: Boolean = false,
        allow_dim: Boolean = false
    ) {
        if (MyDebug.LOG) {
            Log.d(TAG, "updateForSettings()")
            if (toast_message != null) {
                Log.d(TAG, "toast_message: " + toast_message)
            }
        }
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            debug_time = System.currentTimeMillis()
        }

        // make sure we're into continuous video mode
        // workaround for bug on Samsung Galaxy S5 with UHD, where if the user switches to another (non-continuous-video) focus mode, then goes to Settings, then returns and records video, the preview freezes and the video is corrupted
        // so to be safe, we always reset to continuous video mode, and then reset it afterwards
        /*String saved_focus_value = preview.updateFocusForVideo(); // n.b., may be null if focus mode not changed
		if( MyDebug.LOG )
			Log.d(TAG, "saved_focus_value: " + saved_focus_value);*/
        if (MyDebug.LOG) Log.d(TAG, "update folder history")
        save_location_history!!.updateFolderHistory(
            this.storageUtils!!.getSaveLocation(), true
        ) // this also updates the last icon for ghost image, if that pref has changed
        // no need to update save_location_history_saf, as we always do this in onActivityResult()
        if (MyDebug.LOG) {
            Log.d(
                TAG,
                "updateForSettings: time after update folder history: " + (System.currentTimeMillis() - debug_time)
            )
        }

        imageQueueChanged() // needed at least for changing photo mode, but might as well call it always

        var need_reopen = false
        if (update_camera && preview?.cameraController != null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            var scene_mode = preview?.cameraController?.getSceneMode()
            if (MyDebug.LOG) Log.d(TAG, "scene mode was: " + scene_mode)
            val key = PreferenceKeys.SceneModePreferenceKey
            val value: String =
                sharedPreferences.getString(key, CameraController.SCENE_MODE_DEFAULT)!!
            if (scene_mode == null) scene_mode = CameraController.SCENE_MODE_DEFAULT
            if (value != scene_mode) {
                if (MyDebug.LOG) Log.d(TAG, "scene mode changed to: $value")
                need_reopen = true
            } else {
                if (applicationInterface!!.useCamera2()) {
                    val camera2_fake_flash = preview?.cameraController?.useCamera2FakeFlash
                    if (MyDebug.LOG) Log.d(TAG, "camera2_fake_flash was: $camera2_fake_flash")
                    if (applicationInterface!!.useCamera2FakeFlash() != camera2_fake_flash) {
                        if (MyDebug.LOG) Log.d(TAG, "camera2_fake_flash changed")
                        need_reopen = true
                    }
                }
            }

            if (!need_reopen) {
                val old_tonemap_profile = preview?.cameraController?.getTonemapProfile()
                if (old_tonemap_profile != TonemapProfile.TONEMAPPROFILE_OFF) {
                    val new_tonemap_profile = applicationInterface!!.getVideoTonemapProfile()
                    if (new_tonemap_profile != TonemapProfile.TONEMAPPROFILE_OFF && new_tonemap_profile != old_tonemap_profile) {
                        // needed for Galaxy S10e when changing from TONEMAP_MODE_CONTRAST_CURVE to TONEMAP_MODE_PRESET_CURVE,
                        // otherwise the contrast curve remains active!
                        if (MyDebug.LOG) Log.d(TAG, "switching between tonemap profiles")
                        need_reopen = true
                    }
                }
            }

            if (!need_reopen) {
                val old_is_extension = preview?.cameraController?.isCameraExtension()
                val new_is_extension = applicationInterface!!.isCameraExtensionPref()
                if (old_is_extension == true || new_is_extension) {
                    // At least on Galaxy S10e, we have problems stopping and starting a camera extension session,
                    // e.g., when changing resolutions whilst in an extension mode (XHDR or bokeh) or switching
                    // from XHDR to other modes (including non-extension modes like STD). Problems such as preview
                    // no longer receiving frames, or the call to createExtensionSession() (or createCaptureSession)
                    // hanging. So therefore we should reopen the camera if at least
                    // old_is_extension==true.
                    // This isn't required if old_is_extension==false but new_is_extension==true,
                    // but we still do so since reopening the camera occurs on a background thread
                    // (opening an extension session seems to take longer, so better not to block
                    // the UI thread).
                    if (MyDebug.LOG) Log.d(
                        TAG, "need to reopen camera for changes to extension session"
                    )
                    need_reopen = true
                }
            }
        }

        // ensure icons invisible if disabling them from showing from the Settings
        // (if enabling them, we'll make the icon visible later on)
        checkDisableGUIIcons()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val audio_control: String =
            sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none")!!
        // better to only display the audio control icon if it matches specific known supported types
        // (important now that "voice" is no longer supported)
        //if( !audio_control.equals("voice") && !audio_control.equals("noise") ) {
        if (audio_control != "noise") {
            val speechRecognizerButton = binding.audioControl
            speechRecognizerButton.visibility = View.GONE
        }

        //speechControl.initSpeechRecognizer(); // in case we've enabled or disabled speech recognizer

        // we no longer call initLocation() here (for having enabled or disabled geotagging), as that's
        // done in setWindowFlagsForCamera() - important not to call it here as well, otherwise if
        // permission wasn't granted, we'll ask for permission twice in a row (on Android 9 or earlier
        // at least)
        //initLocation(); // in case we've enabled or disabled GPS
        initGyroSensors() // in case we've entered or left panorama
        if (MyDebug.LOG) {
            Log.d(
                TAG,
                "updateForSettings: time after init speech and location: " + (System.currentTimeMillis() - debug_time)
            )
        }
        if (toast_message != null) block_startup_toast = true
        if (!update_camera) {
            // don't try to update camera
        } else if (need_reopen || preview?.cameraController == null) { // if camera couldn't be opened before, might as well try again
            if (allow_dim) applicationInterface!!.drawPreview?.setDimPreview(true)
            preview?.reopenCamera()
            if (MyDebug.LOG) {
                Log.d(
                    TAG,
                    "updateForSettings: time after reopen: " + (System.currentTimeMillis() - debug_time)
                )
            }
        } else {
            preview?.setCameraDisplayOrientation() // need to call in case the preview rotation option was changed
            if (MyDebug.LOG) {
                Log.d(
                    TAG,
                    "updateForSettings: time after set display orientation: " + (System.currentTimeMillis() - debug_time)
                )
            }
            if (allow_dim) applicationInterface!!.drawPreview?.setDimPreview(true)
            preview?.pausePreview(true)
            if (MyDebug.LOG) {
                Log.d(
                    TAG,
                    "updateForSettings: time after pause: " + (System.currentTimeMillis() - debug_time)
                )
            }

            val handler = Handler()
            // We run setupCamera on the UI thread, but we do it on a post-delayed so that the dimming effect (for Camera2 API) has a chance to run.
            // Even if allow_dim==false, still run as a postDelayed (a) for consistency, (b) to allow UI to run for a bit (to avoid risk of slow frames).
            handler.postDelayed(object : Runnable {
                override fun run() {
                    preview?.setupCamera(false)
                }
            }, DrawPreview.dim_effect_time_c + 16) // +16 to allow time for a frame update to run
        }
        // don't set block_startup_toast to false yet, as camera might be closing/opening on background thread
        if (toast_message != null && toast_message.length > 0) preview?.showToast(
            null, toast_message, true
        )

        // don't need to reset to saved_focus_value, as we'll have done this when setting up the camera (or will do so when the camera is reopened, if need_reopen)
        /*if( saved_focus_value != null ) {
			if( MyDebug.LOG )
				Log.d(TAG, "switch focus back to: " + saved_focus_value);
    		preview.updateFocus(saved_focus_value, true, false);
    	}*/
        magneticSensor!!.registerMagneticListener(mSensorManager) // check whether we need to register or unregister the magnetic listener
        magneticSensor!!.checkMagneticAccuracy()

        if (MyDebug.LOG) {
            Log.d(TAG, "updateForSettings: done: " + (System.currentTimeMillis() - debug_time))
        }
    }

    /** Disables the optional on-screen icons if either user doesn't want to enable them, or not
     * supported). Note that displaying icons is done via MainUI.showGUI.
     * @return Whether an icon's visibility was changed.
     */
    private fun checkDisableGUIIcons(): Boolean {
        if (MyDebug.LOG) Log.d(TAG, "checkDisableGUIIcons")
        var changed = false
        mainUI?.let {
            if (mainUI?.showCycleRawIcon() == false) {
                val button = binding.cycleRaw
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
            if (mainUI?.showStoreLocationIcon() == false) {
                val button = binding.storeLocation
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
            if (mainUI?.showTextStampIcon() == false) {
                val button = binding.textStamp
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
            if (mainUI?.showStampIcon() == false) {
                val button = binding.stamp
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
            if (mainUI?.showFocusPeakingIcon() == false) {
                val button = binding.focusPeaking
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
            if (mainUI?.showAutoLevelIcon() == false) {
                val button = binding.autoLevel
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
            if (mainUI?.showCycleFlashIcon() == false) {
                val button = binding.cycleFlash
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
            if (mainUI?.showFaceDetectionIcon() == false) {
                val button = binding.faceDetection
                changed = changed || (button.visibility != View.GONE)
                button.visibility = View.GONE
            }
        }
//        if (!showSwitchMultiCamIcon()) {
//            // also handle the multi-cam icon here, as this can change when switching between front/back cameras
//            // (e.g., if say a device only has multiple back cameras)
//            val button = binding.switchMultiCamera
//            changed = changed || (button.visibility != View.GONE)
//            button.visibility = View.GONE
//        }
        if (MyDebug.LOG) Log.d(TAG, "checkDisableGUIIcons: $changed")
        return changed
    }

    val preferenceFragment: MyPreferenceFragment?
        get() = getFragmentManager().findFragmentByTag("PREFERENCE_FRAGMENT") as MyPreferenceFragment?

    private fun settingsIsOpen(): Boolean {
        return this.preferenceFragment != null
    }

    /** Call when the settings is going to be closed.
     */
    fun settingsClosing() {
        if (MyDebug.LOG) Log.d(TAG, "close settings")
        setWindowFlagsForCamera()
        showPreview(true)

        preferencesListener.stopListening()

        // Update the cached settings in DrawPreview
        // Note that some GUI related settings won't trigger preferencesListener.anyChange(), so
        // we always call this. Perhaps we could add more classifications to PreferencesListener
        // to mark settings that need us to update DrawPreview but not call updateForSettings().
        // However, DrawPreview.updateSettings() should be a quick function (the main point is
        // to avoid reading the preferences in every single frame).
        applicationInterface!!.drawPreview?.updateSettings()

        // Set the flag to cover the preview until the camera is open and receiving frames again
        // (for Camera2 API) - avoids showing a flash of the preview from before the user went to
        // the settings.
        applicationInterface!!.drawPreview?.setCoverPreview(true)

        if (preferencesListener.anyChange()) {
            mainUI?.updateOnScreenIcons()
        }

        if (preferencesListener.anySignificantChange()) {
            // don't need to update camera, as we now pause/resume camera when going to settings
            updateForSettings(false)
        } else {
            if (MyDebug.LOG) Log.d(
                TAG, "no need to call updateForSettings() for changes made to preferences"
            )
            if (preferencesListener.anyChange()) {
                // however we should still destroy cached popup, in case UI settings need to be kept in
                // sync (e.g., changing the Repeat Mode)
            }
        }
    }

    private var popupOnBackPressedCallback: PopupOnBackPressedCallback? = null

    private inner class PopupOnBackPressedCallback(enabled: Boolean) :
        OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            if (MyDebug.LOG) Log.d(TAG, "PopupOnBackPressedCallback.handleOnBackPressed")
            if (popupIsOpen()) {
                // close popup will disable the PopupOnBackPressedCallback, so no need to do it here
                closePopup()
            } else {
                // shouldn't be here (if popup isn't open, this callback shouldn't be enabled), but just in case
                if (MyDebug.LOG) Log.e(
                    TAG, "PopupOnBackPressedCallback was enabled but popup menu not open?!"
                )
                this.isEnabled = false
                this@MainActivity.onBackPressed()
            }
        }
    }

    fun enablePopupOnBackPressedCallback(enabled: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "enablePopupOnBackPressedCallback: " + enabled)
        if (popupOnBackPressedCallback != null) {
            popupOnBackPressedCallback!!.isEnabled = enabled
        }
    }

    private var pausePreviewOnBackPressedCallback: PausePreviewOnBackPressedCallback? = null

    private inner class PausePreviewOnBackPressedCallback(enabled: Boolean) :
        OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            if (MyDebug.LOG) Log.d(TAG, "PausePreviewOnBackPressedCallback.handleOnBackPressed")

            if (preview != null && preview?.isPreviewPaused() == true) {
                // starting the preview will disable the PausePreviewOnBackPressedCallback, so no need to do it here
                if (MyDebug.LOG) Log.d(TAG, "preview was paused, so unpause it")
                preview?.startCameraPreview()
            } else {
                // shouldn't be here (if preview isn't paused, this callback shouldn't be enabled), but just in case
                if (MyDebug.LOG) Log.e(
                    TAG, "PausePreviewOnBackPressedCallback was enabled but preview not paused?!"
                )
                this.isEnabled = false
                this@MainActivity.onBackPressed()
            }
        }
    }

    fun enablePausePreviewOnBackPressedCallback(enabled: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "enablePausePreviewOnBackPressedCallback: " + enabled)
        if (pausePreviewOnBackPressedCallback != null) {
            pausePreviewOnBackPressedCallback!!.isEnabled = enabled
        }
    }

    private var screenLockOnBackPressedCallback: ScreenLockOnBackPressedCallback? = null

    private inner class ScreenLockOnBackPressedCallback(enabled: Boolean) :
        OnBackPressedCallback(enabled) {
        override fun handleOnBackPressed() {
            if (MyDebug.LOG) Log.d(TAG, "ScreenLockOnBackPressedCallback.handleOnBackPressed")

            if (isScreenLocked) {
                preview?.showToast(screen_locked_toast, R.string.screen_is_locked)
            } else {
                // shouldn't be here (if screen isn't locked, this callback shouldn't be enabled), but just in case
                if (MyDebug.LOG) Log.e(
                    TAG, "ScreenLockOnBackPressedCallback was enabled but screen isn't locked?!"
                )
                this.isEnabled = false
                this@MainActivity.onBackPressed()
            }
        }
    }

    private fun enableScreenLockOnBackPressedCallback(enabled: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "enableScreenLockOnBackPressedCallback: " + enabled)
        if (screenLockOnBackPressedCallback != null) {
            screenLockOnBackPressedCallback!!.isEnabled = enabled
        }
    }

    // should no longer use onBackPressed() - instead use OnBackPressedCallback, for upcoming changes in Android 14+ (predictive back gestures)
    /*@Override
    public void onBackPressed() {
        if( MyDebug.LOG )
            Log.d(TAG, "onBackPressed");
        if( screen_is_locked ) {
            preview.showToast(screen_locked_toast, R.string.screen_is_locked);
            return;
        }

        if( settingsIsOpen() ) {
            settingsClosing();
        }
        else if( preview != null && preview.isPreviewPaused() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "preview was paused, so unpause it");
            preview.startCameraPreview();
            return;
        }
        else {
            if( popupIsOpen() ) {
                closePopup();
                return;
            }
        }
        super.onBackPressed();
    }*/

    /** Whether to allow the application to show under the navigation bar, or not.
     * Arguably we could enable this all the time, but in practice we only enable for cases when
     * want_no_limits==true and navigation_gap!=0 (if want_no_limits==false, there's no need to
     * show under the navigation bar; if navigation_gap==0, there is no navigation bar).
     */
    private fun showUnderNavigation(enable: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "showUnderNavigation: " + enable)

        if (edgeToEdgeMode) {
            // we are already always in edge-to-edge mode
            return
        }

        run {
            // We used to use window flag FLAG_LAYOUT_NO_LIMITS, but this didn't work properly on
            // Android 11 (didn't take effect until orientation changed or application paused/resumed).
            // Although system ui visibility flags are deprecated on Android 11, this still works better
            // than the FLAG_LAYOUT_NO_LIMITS flag (which was not well documented anyway).
            // Update, now using WindowCompat.setDecorFitsSystemWindows. This is non-deprecated, and
            // documented at https://developer.android.com/develop/ui/views/layout/edge-to-edge-manually .
            /*int flags = getWindow().getDecorView().getSystemUiVisibility();
            if( enable ) {
                getWindow().getDecorView().setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            else {
                getWindow().getDecorView().setSystemUiVisibility(flags & ~View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }*/
            test_set_show_under_navigation = enable
            // in theory the VANILLA_ICE_CREAM is redundant as we shouldn't be here on Android 15+ anyway (since edge_to_edge_mode==true), but
            // wrapping in case this helps Google Play recommendation to avoid deprecated APIs for edge-to-edge
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                WindowCompat.setDecorFitsSystemWindows(getWindow(), !enable)
            }
        }

        // in theory the VANILLA_ICE_CREAM is redundant as we shouldn't be here on Android 15+ anyway (since edge_to_edge_mode==true), but
        // wrapping in case this helps Google Play recommendation to avoid deprecated APIs for edge-to-edge
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setNavigationBarColor(if (enable) Color.TRANSPARENT else Color.BLACK)
        }
    }

    val navigationGap: Int
        get() = if (want_no_limits || edgeToEdgeMode) navigation_gap else 0

    val navigationGapLandscape: Int
        get() = if (edgeToEdgeMode) navigation_gap_landscape else 0

    val navigationGapReverseLandscape: Int
        get() = if (edgeToEdgeMode) navigation_gap_reverse_landscape else 0

    /** The system is now such that we have entered or exited immersive mode. If visible is true,
     * system UI is now visible such that we should exit immersive mode. If visible is false, the
     * system has entered immersive mode.
     */
    private fun immersiveModeChanged(visible: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "immersiveModeChanged: " + visible)
        if (!usingKitKatImmersiveMode()) return

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
        val immersive_mode: String = sharedPreferences.getString(
            PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_off"
        )!!
        val hide_ui =
            immersive_mode == "immersive_mode_gui" || immersive_mode == "immersive_mode_everything"

        if (visible) {
            if (MyDebug.LOG) Log.d(TAG, "system bars now visible")
            // change UI due to having exited immersive mode
            if (hide_ui) mainUI?.setImmersiveMode(false)
            setImmersiveTimer()
        } else {
            if (MyDebug.LOG) Log.d(TAG, "system bars now NOT visible")
            // change UI due to having entered immersive mode
            if (hide_ui) mainUI?.setImmersiveMode(true)
        }
    }

    /** Set up listener to handle listening for system ui changes (for immersive mode), and setting
     * a WindowsInsetsListener to find the navigation_gap.
     */
    private fun setupSystemUiVisibilityListener() {
        val decorView = getWindow().getDecorView()

        run {
            // set a window insets listener to find the navigation_gap
            if (MyDebug.LOG) Log.d(TAG, "set a window insets listener")
            this.set_window_insets_listener = true
            decorView.getRootView()
                .setOnApplyWindowInsetsListener(object : View.OnApplyWindowInsetsListener {
                    private var has_last_system_orientation = false
                    private var last_system_orientation: SystemOrientation? = null
                    override fun onApplyWindowInsets(
                        v: View, windowInsets: WindowInsets
                    ): WindowInsets {
                        if (MyDebug.LOG) Log.d(TAG, "onApplyWindowInsets")
                        val inset_left: Int
                        val inset_top: Int
                        val inset_right: Int
                        val inset_bottom: Int
                        if (edgeToEdgeMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // take opportunity to use non-deprecated versions; also for edge_to_edge_mode==true, we need to use getInsetsIgnoringVisibility for
                            // immersive mode (since for edge_to_edge_mode==true, we are not using setSystemUiVisibility() / SYSTEM_UI_FLAG_LAYOUT_STABLE in setImmersiveMode())
                            // also compare with MyApplicationInterface.getDisplaySize() - in particular we don't care about caption/system bar that is returned on e.g.
                            // OnePlus Pad for insets.top when in landscape orientation (since the system bar isn't shown); however we also need to subtract any from the cutout -
                            // since this code is for finding what margins we need to set to avoid navigation bars; avoiding the cutout is done below for the entire
                            // ManualCamera view
                            var insets =
                                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                            var cutout_insets =
                                windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
                            if (test_force_window_insets) {
                                insets = test_insets!!
                                cutout_insets = test_cutout_insets!!
                            }
                            inset_left = insets.left - cutout_insets.left
                            inset_top = insets.top - cutout_insets.top
                            inset_right = insets.right - cutout_insets.right
                            inset_bottom = insets.bottom - cutout_insets.bottom
                        } else {
                            inset_left = windowInsets.getSystemWindowInsetLeft()
                            inset_top = windowInsets.getSystemWindowInsetTop()
                            inset_right = windowInsets.getSystemWindowInsetRight()
                            inset_bottom = windowInsets.getSystemWindowInsetBottom()
                        }
                        if (MyDebug.LOG) {
                            Log.d(TAG, "inset left: " + inset_left)
                            Log.d(TAG, "inset top: " + inset_top)
                            Log.d(TAG, "inset right: " + inset_right)
                            Log.d(TAG, "inset bottom: " + inset_bottom)
                        }

                        if (edgeToEdgeMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // easier to ensure the entire activity avoids display cutouts - for the preview, we still support
                            // it showing under the navigation bar
                            var insets = windowInsets.getInsets(WindowInsets.Type.displayCutout())
                            if (test_force_window_insets) {
                                insets = test_cutout_insets!!
                            }
                            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)

                            // also handle change of immersive mode (instead of using deprecated setOnSystemUiVisibilityChangeListener below
                            immersiveModeChanged(windowInsets.isVisible(WindowInsets.Type.navigationBars()))
                        }

                        resetCachedSystemOrientation() // don't want to get cached result - this can sometimes happen e.g. on Pixel 6 Pro when switching between landscape and reverse landscape
                        val system_orientation: SystemOrientation = systemOrientation
                        val new_navigation_gap: Int
                        var new_navigation_gap_landscape: Int
                        var new_navigation_gap_reverse_landscape: Int
                        when (system_orientation) {
                            SystemOrientation.PORTRAIT -> {
                                if (MyDebug.LOG) Log.d(TAG, "portrait")
                                new_navigation_gap = inset_bottom
                                new_navigation_gap_landscape = inset_left
                                new_navigation_gap_reverse_landscape = inset_right
                            }

                            SystemOrientation.LANDSCAPE -> {
                                if (MyDebug.LOG) Log.d(TAG, "landscape")
                                new_navigation_gap = inset_right
                                new_navigation_gap_landscape = inset_bottom
                                new_navigation_gap_reverse_landscape = inset_top
                            }

                            SystemOrientation.REVERSE_LANDSCAPE -> {
                                if (MyDebug.LOG) Log.d(TAG, "reverse landscape")
                                new_navigation_gap = inset_left
                                new_navigation_gap_landscape = inset_top
                                new_navigation_gap_reverse_landscape = inset_bottom
                            }

                            else -> {
                                Log.e(TAG, "unknown system_orientation?!: " + system_orientation)
                                new_navigation_gap = 0
                                new_navigation_gap_landscape = 0
                                new_navigation_gap_reverse_landscape = 0
                            }
                        }
                        if (!edgeToEdgeMode) {
                            // we only care about avoiding a landscape navigation bar (e.g., large tablets in landscape orientation) for edge_to_edge_mode==true
                            // in theory this could be useful when edge_to_edge_mode==false, but in practice we will never enter edge-to-edge-mode if the
                            // navigation bar is along the landscape-edge, so restrict behaviour change to edge_to_edge_mode==true
                            new_navigation_gap_landscape = 0
                            new_navigation_gap_reverse_landscape = 0
                        }

                        // for edge_to_edge_mode==false, we only enter this case if system orientation changes, due to issues where this callback may be called first with 0 navigation gap
                        // (see notes below)
                        // for edge_to_edge_mode==true, simpler to always react to updated insets - in particular, in split-window mode, the navigation gaps can
                        // change when device rotates, even though the application remains in the same orientation
                        if ((edgeToEdgeMode || (has_last_system_orientation && system_orientation != last_system_orientation)) && (new_navigation_gap != navigation_gap || new_navigation_gap_landscape != navigation_gap_landscape || new_navigation_gap_reverse_landscape != navigation_gap_reverse_landscape)) {
                            if (MyDebug.LOG) Log.d(
                                TAG,
                                "navigation_gap changed from " + navigation_gap + " to " + new_navigation_gap
                            )

                            navigation_gap = new_navigation_gap
                            navigation_gap_landscape = new_navigation_gap_landscape
                            navigation_gap_reverse_landscape = new_navigation_gap_reverse_landscape

                            if (MyDebug.LOG) Log.d(TAG, "want_no_limits: " + want_no_limits)
                            if (want_no_limits || edgeToEdgeMode) {
                                // If we want no_limits mode, then need to take care in case of device orientation
                                // in cases where that changes the navigation_gap:
                                // - Need to set showUnderNavigation() (in case navigation_gap when from zero to non-zero or vice versa).
                                // - Need to call layoutUI() (for different value of navigation_gap)

                                // Need to call showUnderNavigation() from handler for it to take effect.
                                // Similarly we have problems if we call layoutUI without post-ing it -
                                // sometimes when rotating a device, we get a call to OnApplyWindowInsetsListener
                                // with 0 navigation_gap followed by the call with the correct non-zero values -
                                // posting the call to layoutUI means it runs after the second call, so we have the
                                // correct navigation_gap.

                                val handler = Handler()
                                handler.post(object : Runnable {
                                    override fun run() {
                                        if (MyDebug.LOG) Log.d(
                                            TAG,
                                            "runnable for change in navigation_gap due to orientation change"
                                        )
                                        if (navigation_gap != 0) {
                                            if (MyDebug.LOG) Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS")
                                            showUnderNavigation(true)
                                        } else {
                                            if (MyDebug.LOG) Log.d(
                                                TAG, "clear FLAG_LAYOUT_NO_LIMITS"
                                            )
                                            showUnderNavigation(false)
                                        }
                                        // needed for OnePlus Pad when rotating, to avoid delay in updating last_take_photo_top_time (affects placement of on-screen text e.g. zoom)
                                        // need to do this from handler for this to take effect (otherwise last_take_photo_top_time won't update to new value)
                                        applicationInterface!!.drawPreview?.onNavigationGapChanged()

                                        if (MyDebug.LOG) Log.d(
                                            TAG, "layout UI due to changing navigation_gap"
                                        )
                                    }
                                })
                            }
                        } else if (!edgeToEdgeMode && navigation_gap == 0) {
                            if (MyDebug.LOG) Log.d(
                                TAG, "navigation_gap changed from zero to " + new_navigation_gap
                            )
                            navigation_gap = new_navigation_gap
                            // Sometimes when this callback is called, the navigation_gap may still be 0 even if
                            // the device doesn't have physical navigation buttons - we need to wait
                            // until we have found a non-zero value before switching to no limits.
                            // On devices with physical navigation bar, navigation_gap should remain 0
                            // (and there's no point setting FLAG_LAYOUT_NO_LIMITS)
                            if (want_no_limits && navigation_gap != 0) {
                                if (MyDebug.LOG) Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS")
                                showUnderNavigation(true)
                            }
                        }

                        if (has_last_system_orientation && ((system_orientation == SystemOrientation.LANDSCAPE && last_system_orientation == SystemOrientation.REVERSE_LANDSCAPE) || (system_orientation == SystemOrientation.REVERSE_LANDSCAPE && last_system_orientation == SystemOrientation.LANDSCAPE))) {
                            // hack - this should be done via MyDisplayListener.onDisplayChanged(), but that doesn't work on Galaxy S24+ (either MyDisplayListener.onDisplayChanged()
                            // isn't called, or getDefaultDisplay().getRotation() is still returning the old rotation)
                            if (MyDebug.LOG) Log.d(
                                TAG,
                                "onApplyWindowInsets: switched between landscape and reverse orientation"
                            )
                            onSystemOrientationChanged()
                        }

                        has_last_system_orientation = true
                        last_system_orientation = system_orientation

                        // see comments in MainUI.layoutUI() for why we don't use this
                        /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && getSystemOrientation() == SystemOrientation.LANDSCAPE ) {
                        Rect privacy_indicator_rect = windowInsets.getPrivacyIndicatorBounds();
                        if( privacy_indicator_rect != null ) {
                            Rect window_bounds = getWindowManager().getCurrentWindowMetrics().getBounds();
                            if( MyDebug.LOG ) {
                                Log.d(TAG, "privacy_indicator_rect: " + privacy_indicator_rect);
                                Log.d(TAG, "window_bounds: " + window_bounds);
                            }
                            privacy_indicator_gap = window_bounds.right - privacy_indicator_rect.left;
                            if( privacy_indicator_gap < 0 )
                                privacy_indicator_gap = 0; // just in case??
                            if( MyDebug.LOG )
                                Log.d(TAG, "privacy_indicator_gap: " + privacy_indicator_gap);
                        }
                    }
                    else {
                        privacy_indicator_gap = 0;
                    }*/
                        return getWindow().getDecorView().getRootView()
                            .onApplyWindowInsets(windowInsets)
                    }
                })
        }

        if (edgeToEdgeMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // already handled by the setOnApplyWindowInsetsListener above
        } else {
            decorView.setOnSystemUiVisibilityChangeListener(object :
                OnSystemUiVisibilityChangeListener {
                override fun onSystemUiVisibilityChange(visibility: Int) {
                    // Note that system bars will only be "visible" if none of the
                    // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.

                    if (MyDebug.LOG) Log.d(TAG, "onSystemUiVisibilityChange: " + visibility)

                    // Note that Android example code says to test against SYSTEM_UI_FLAG_FULLSCREEN,
                    // but this stopped working on Android 11, as when calling setSystemUiVisibility(0)
                    // to exit immersive mode, when we arrive here the flag SYSTEM_UI_FLAG_FULLSCREEN
                    // is still set. Fixed by checking for SYSTEM_UI_FLAG_HIDE_NAVIGATION instead -
                    // which makes some sense since we run in fullscreen mode all the time anyway.
                    //if( (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 ) {
                    if ((visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                        immersiveModeChanged(true)
                    } else {
                        immersiveModeChanged(false)
                    }
                }
            })
        }
    }

    fun usingKitKatImmersiveMode(): Boolean {
        // whether we are using a Kit Kat style immersive mode (either hiding navigation bar, GUI, or everything)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val immersive_mode: String = sharedPreferences.getString(
            PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_off"
        )!!
        if (immersive_mode == "immersive_mode_navigation" || immersive_mode == "immersive_mode_gui" || immersive_mode == "immersive_mode_everything") return true
        return false
    }

    fun usingKitKatImmersiveModeEverything(): Boolean {
        // whether we are using a Kit Kat style immersive mode for everything
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val immersive_mode: String = sharedPreferences.getString(
            PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_off"
        )!!
        if (immersive_mode == "immersive_mode_everything") return true
        return false
    }


    private var immersive_timer_handler: Handler? = null
    private var immersive_timer_runnable: Runnable? = null

    private fun cancelImmersiveTimer() {
        if (immersive_timer_handler != null && immersive_timer_runnable != null) {
            immersive_timer_handler!!.removeCallbacks(immersive_timer_runnable!!)
            immersive_timer_handler = null
            immersive_timer_runnable = null
        }
    }

    private fun setImmersiveTimer() {
        cancelImmersiveTimer()
        if (this.isAppPaused) {
            // don't want to enter immersive mode from background
            // problem that even after onPause, we can end up here via various callbacks
            return
        }
        immersive_timer_handler = Handler()
        immersive_timer_handler!!.postDelayed(object : Runnable {
            override fun run() {
                if (MyDebug.LOG) Log.d(TAG, "setImmersiveTimer: run")
                // even though timer should have been cancelled when in background, check app_is_paused just in case
                if (!isAppPaused && !isCameraInBackground && !popupIsOpen() && usingKitKatImmersiveMode()) setImmersiveMode(
                    true
                )
            }
        }.also { immersive_timer_runnable = it }, 5000)
    }

    fun initImmersiveMode() {
        if (!usingKitKatImmersiveMode()) {
            setImmersiveMode(true)
        } else {
            // don't start in immersive mode, only after a timer
            setImmersiveTimer()
        }
    }

    fun setImmersiveMode(on: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "setImmersiveMode: " + on)

        // n.b., preview.setImmersiveMode() is called from onSystemUiVisibilityChange()

        // don't allow the kitkat-style immersive mode for panorama mode (problem that in "full" immersive mode, the gyro spot can't be seen - we could fix this, but simplest to just disallow)
        val enable_immersive =
            on && usingKitKatImmersiveMode() && applicationInterface!!.photoMode != PhotoMode.Panorama
        if (MyDebug.LOG) Log.d(TAG, "enable_immersive?: " + enable_immersive)

        if (edgeToEdgeMode) {
            // take opportunity to avoid deprecated setSystemUiVisibility
            val windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
            val type =
                WindowInsetsCompat.Type.navigationBars() // only show/hide navigation bars, as we run with system bars always hidden
            if (enable_immersive) {
                windowInsetsController.hide(type)
            } else {
                windowInsetsController.show(type)
            }
        } else {
            // save whether we set SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION - since this flag might be enabled for showUnderNavigation(true), at least indirectly by setDecorFitsSystemWindows() on old versions of Android
            val saved_flags = getWindow().getDecorView()
                .getSystemUiVisibility() and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (MyDebug.LOG) Log.d(TAG, "saved_flags?: " + saved_flags)
            if (enable_immersive) {
                getWindow().getDecorView()
                    .setSystemUiVisibility(saved_flags or View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
            } else {
                getWindow().getDecorView().setSystemUiVisibility(saved_flags)
            }
        }
    }

    /** Sets the brightness level for normal operation (when camera preview is visible).
     * If force_max is true, this always forces maximum brightness; otherwise this depends on user preference.
     */
    fun setBrightnessForCamera(force_max: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "setBrightnessForCamera")
        // set screen to max brightness - see http://stackoverflow.com/questions/11978042/android-screen-brightness-max-value
        // done here rather than onCreate, so that changing it in preferences takes effect without restarting app
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val layout = getWindow().getAttributes()
        if (force_max || sharedPreferences.getBoolean(
                PreferenceKeys.MaxBrightnessPreferenceKey, false
            )
        ) {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        } else {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        // this must be called from the ui thread
        // sometimes this method may be called not on UI thread, e.g., Preview.takePhotoWhenFocused->CameraController2.takePicture
        // ->CameraController2.runFakePrecapture->Preview/onFrontScreenTurnOn->MyApplicationInterface.turnFrontScreenFlashOn
        // -> this.setBrightnessForCamera
        this.runOnUiThread(object : Runnable {
            override fun run() {
                getWindow().setAttributes(layout)
            }
        })
    }

    /**
     * Set the brightness to minimal in case the preference key is set to do it
     */
    fun setBrightnessToMinimumIfWanted() {
        if (MyDebug.LOG) Log.d(TAG, "setBrightnessToMinimum")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val layout = getWindow().getAttributes()
        if (sharedPreferences.getBoolean(PreferenceKeys.DimWhenDisconnectedPreferenceKey, false)) {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF
        } else {
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }

        this.runOnUiThread(object : Runnable {
            override fun run() {
                getWindow().setAttributes(layout)
            }
        })
    }

    /** Sets the window flags for normal operation (when camera preview is visible).
     */
    fun setWindowFlagsForCamera() {
        if (MyDebug.LOG) Log.d(TAG, "setWindowFlagsForCamera")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // we set this to prevent what's on the preview being used to show under the "recent apps" view - potentially useful
            // for privacy reasons
            setRecentsScreenshotEnabled(false)
        }

        if (lockToLandscape) {
            // force to landscape mode
//            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE); // testing for devices with unusual sensor orientation (e.g., Nexus 5X)
        } else {
            // allow orientation to change for camera, even if user has locked orientation
//            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        }
        if (preview != null) {
            // also need to call preview.setCameraDisplayOrientation, as this handles if the user switched from portrait to reverse landscape whilst in settings/etc
            // as switching from reverse landscape back to landscape isn't detected in onConfigurationChanged
            // update: now probably irrelevant now that we close/reopen the camera, but keep it here anyway
            preview?.setCameraDisplayOrientation()
        }

        // keep screen active - see http://stackoverflow.com/questions/2131948/force-screen-on
        if (sharedPreferences.getBoolean(PreferenceKeys.KeepDisplayOnPreferenceKey, true)) {
            if (MyDebug.LOG) Log.d(TAG, "do keep screen on")
            this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            if (MyDebug.LOG) Log.d(TAG, "don't keep screen on")
            this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (sharedPreferences.getBoolean(PreferenceKeys.ShowWhenLockedPreferenceKey, false)) {
            if (MyDebug.LOG) Log.d(TAG, "do show when locked")
            // keep ManualCamera on top of screen-lock (will still need to unlock when going to gallery or settings)
            showWhenLocked(true)
        } else {
            if (MyDebug.LOG) Log.d(TAG, "don't show when locked")
            showWhenLocked(false)
        }

        if (want_no_limits && navigation_gap != 0) {
            if (MyDebug.LOG) Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS")
            showUnderNavigation(true)
        }

        setBrightnessForCamera(false)

        initImmersiveMode()
        this.isCameraInBackground = false

        magneticSensor!!.clearDialog() // if the magnetic accuracy was opened, it must have been closed now
        if (!this.isAppPaused) {
            // Needs to be called after camera_in_background is set to false.
            // Note that the app_is_paused guard is in some sense unnecessary, as initLocation tests for that too,
            // but useful for error tracking - ideally we want to make sure that initLocation is never called when
            // app is paused. It can happen here because setWindowFlagsForCamera() is called from
            // onCreate()
            initLocation()

            // Similarly only want to reopen the camera if no longer paused
            if (preview != null) {
                preview?.onResume()
            }
        }
    }

    private fun setWindowFlagsForSettings() {
        setWindowFlagsForSettings(true)
    }

    /** Sets the window flags for when the settings window is open.
     * @param set_lock_protect If true, then window flags will be set to protect by screen lock, no
     * matter what the preference setting
     * PreferenceKeys.getShowWhenLockedPreferenceKey() is set to. This
     * should be true for the Settings window, and anything else that might
     * need protecting. But some callers use this method for opening other
     * things (such as info dialogs).
     */
    fun setWindowFlagsForSettings(set_lock_protect: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "setWindowFlagsForSettings: " + set_lock_protect)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // in settings mode, okay to revert to default behaviour for using a screenshot for "recent apps" view
            setRecentsScreenshotEnabled(true)
        }

        // allow screen rotation
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)

        // revert to standard screen blank behaviour
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (want_no_limits && navigation_gap != 0) {
            if (MyDebug.LOG) Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS")
            showUnderNavigation(false)
        }
        if (set_lock_protect) {
            // settings should still be protected by screen lock
            showWhenLocked(false)
        }

        run {
            val layout = getWindow().getAttributes()
            layout.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            getWindow().setAttributes(layout)
        }

        setImmersiveMode(false)
        this.isCameraInBackground = true

        // we disable location listening when showing settings or a dialog etc - saves battery life, also better for privacy
        applicationInterface!!.locationSupplier.freeLocationListeners()

        // similarly we close the camera
        preview?.onPause(false)

//        pushSwitchedCamera = false // just in case
    }

    private fun showWhenLocked(show: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "showWhenLocked: " + show)
        // although FLAG_SHOW_WHEN_LOCKED is deprecated, setShowWhenLocked(false) does not work
        // correctly: if we turn screen off and on when camera is open (so we're now running above
        // the lock screen), going to settings does not show the lock screen, i.e.,
        // setShowWhenLocked(false) does not take effect!
        /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			if( MyDebug.LOG )
				Log.d(TAG, "use setShowWhenLocked");
			setShowWhenLocked(show);
		}
		else*/
        run {
            if (show) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        }
    }

    /** Use this is place of simply alert.show(), if the orientation has just been set to allow
     * rotation via setWindowFlagsForSettings(). On some devices (e.g., OnePlus 3T with Android 8),
     * the dialog doesn't show properly if the phone is held in portrait. A workaround seems to be
     * to use postDelayed. Note that postOnAnimation() doesn't work.
     */
    fun showAlert(alert: AlertDialog) {
        if (MyDebug.LOG) Log.d(TAG, "showAlert")
        val handler = Handler()
        handler.postDelayed(object : Runnable {
            override fun run() {
                alert.show()
            }
        }, 20)
        // note that 1ms usually fixes the problem, but not always; 10ms seems fine, have set 20ms
        // just in case
    }

    fun showPreview(show: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "showPreview: " + show)
        val container = binding.hideContainer
        container.visibility = if (show) View.GONE else View.VISIBLE
    }

    /** Rotates the supplied bitmap according to the orientation tag stored in the exif data. If no
     * rotation is required, the input bitmap is returned. If rotation is required, the input
     * bitmap is recycled.
     * @param uri Uri containing the JPEG with Exif information to use.
     */
    @Throws(IOException::class)
    fun rotateForExif(bitmap: Bitmap, uri: Uri): Bitmap {
        var bitmap = bitmap
        var exif: ExifInterface?
        var inputStream: InputStream? = null
        try {
            inputStream = this.getContentResolver().openInputStream(uri)
            exif = ExifInterface(inputStream!!)
        } finally {
            if (inputStream != null) inputStream.close()
        }

        if (exif != null) {
            val exif_orientation_s = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED
            )
            var needs_tf = false
            var exif_orientation = 0
            // see http://jpegclub.org/exif_orientation.html
            // and http://stackoverflow.com/questions/20478765/how-to-get-the-correct-orientation-of-the-image-selected-from-the-default-image
            if (exif_orientation_s == ExifInterface.ORIENTATION_UNDEFINED || exif_orientation_s == ExifInterface.ORIENTATION_NORMAL) {
                // leave unchanged
            } else if (exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_180) {
                needs_tf = true
                exif_orientation = 180
            } else if (exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_90) {
                needs_tf = true
                exif_orientation = 90
            } else if (exif_orientation_s == ExifInterface.ORIENTATION_ROTATE_270) {
                needs_tf = true
                exif_orientation = 270
            } else {
                // just leave unchanged for now
                if (MyDebug.LOG) Log.e(
                    TAG, "    unsupported exif orientation: " + exif_orientation_s
                )
            }
            if (MyDebug.LOG) Log.d(TAG, "    exif orientation: " + exif_orientation)

            if (needs_tf) {
                if (MyDebug.LOG) Log.d(TAG, "    need to rotate bitmap due to exif orientation tag")
                val m = Matrix()
                m.setRotate(
                    exif_orientation.toFloat(), bitmap.getWidth() * 0.5f, bitmap.getHeight() * 0.5f
                )
                val rotated_bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true
                )
                if (rotated_bitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated_bitmap
                }
            }
        }
        return bitmap
    }

    /** Loads a thumbnail from the supplied image uri (not videos). Note this loads from the bitmap
     * rather than reading from MediaStore. Therefore this works with SAF uris as well as
     * MediaStore uris, as well as allowing control over the resolution of the thumbnail.
     * If sample_factor is 1, this returns a bitmap scaled to match the display resolution. If
     * sample_factor is greater than 1, it will be scaled down to a lower resolution.
     * We now use this for photos in preference to APIs like
     * MediaStore.Images.Thumbnails.getThumbnail(). Advantages are simplifying the code, reducing
     * number of different codepaths, but also seems to help against device specific bugs
     * in getThumbnail() e.g. Pixel 6 Pro with x-night in portrait.
     */
    private fun loadThumbnailFromUri(uri: Uri, sample_factor: Int): Bitmap? {
        var thumbnail: Bitmap? = null
        try {
            //thumbnail = MediaStore.Images.Media.getBitmap(getContentResolver(), media.uri);
            // only need to load a bitmap as large as the screen size
            val options = BitmapFactory.Options()
            var `is` = getContentResolver().openInputStream(uri)
            // get dimensions
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(`is`, null, options)
            var bitmap_width = options.outWidth
            var bitmap_height = options.outHeight
            val display_size = Point()
            applicationInterface!!.getDisplaySize(display_size, true)
            if (MyDebug.LOG) {
                Log.d(TAG, "bitmap_width: " + bitmap_width)
                Log.d(TAG, "bitmap_height: " + bitmap_height)
                Log.d(TAG, "display width: " + display_size.x)
                Log.d(TAG, "display height: " + display_size.y)
            }
            // align dimensions
            if (display_size.x < display_size.y) {
                display_size.set(display_size.y, display_size.x)
            }
            if (bitmap_width < bitmap_height) {
                val dummy = bitmap_width
                bitmap_width = bitmap_height
                bitmap_height = dummy
            }
            if (MyDebug.LOG) {
                Log.d(TAG, "bitmap_width: " + bitmap_width)
                Log.d(TAG, "bitmap_height: " + bitmap_height)
                Log.d(TAG, "display width: " + display_size.x)
                Log.d(TAG, "display height: " + display_size.y)
            }
            // only care about height, to save worrying about different aspect ratios
            options.inSampleSize = 1
            while (bitmap_height / (2 * options.inSampleSize) >= display_size.y) {
                options.inSampleSize *= 2
            }
            options.inSampleSize *= sample_factor
            if (MyDebug.LOG) {
                Log.d(TAG, "inSampleSize: " + options.inSampleSize)
            }
            options.inJustDecodeBounds = false
            // need a new inputstream, see https://stackoverflow.com/questions/2503628/bitmapfactory-decodestream-returning-null-when-options-are-set
            `is`!!.close()
            `is` = getContentResolver().openInputStream(uri)
            thumbnail = BitmapFactory.decodeStream(`is`, null, options)
            if (thumbnail == null) {
                Log.e(TAG, "decodeStream returned null bitmap for ghost image last")
            }
            `is`!!.close()

            thumbnail = rotateForExif(thumbnail!!, uri)
        } catch (e: IOException) {
            Log.e(TAG, "failed to load bitmap for ghost image last")
            e.printStackTrace()
        }
        return thumbnail
    }

    /** Shows the default "blank" gallery icon, when we don't have a thumbnail available.
     */
    private fun updateGalleryIconToBlank() {
        if (MyDebug.LOG) Log.d(TAG, "updateGalleryIconToBlank")
//        val galleryButton = binding.gallery
//        val bottom = galleryButton.paddingBottom
//        val top = galleryButton.paddingTop
//        val right = galleryButton.paddingRight
//        val left = galleryButton.paddingLeft
//        galleryButton.setImageBitmap(null)
//        galleryButton.setImageResource(R.drawable.baseline_photo_library_white_48)
////         workaround for setImageResource also resetting padding, Android bug
//        galleryButton.setPadding(left, top, right, bottom)
        gallery_bitmap = null
        viewModel.setGalleryBitmap(gallery_bitmap)
    }

    /** Shows a thumbnail for the gallery icon.
     */
    fun updateGalleryIcon(thumbnail: Bitmap?) {
        if (MyDebug.LOG) Log.d(TAG, "updateGalleryIcon: $thumbnail")
        // If we're currently running the background task to update the gallery (see updateGalleryIcon()), we should cancel that!
        // Otherwise if user takes a photo whilst the background task is still running, the thumbnail from the latest photo will
        // be overridden when the background task completes. This is more likely when using SAF on Android 10+ with scoped storage,
        // due to SAF's poor performance for folders with large number of files.
        if (update_gallery_future != null) {
            if (MyDebug.LOG) Log.d(TAG, "cancel update_gallery_future")
            update_gallery_future?.cancel(true)
        }
//        val galleryButton = binding.gallery
//        galleryButton.setImageBitmap(thumbnail)
        gallery_bitmap = thumbnail
        viewModel.setGalleryBitmap(gallery_bitmap)
    }

    /** Updates the gallery icon by searching for the most recent photo.
     * Launches the task in a separate thread.
     */
    fun updateGalleryIcon() {
        var debug_time: Long = 0
        if (MyDebug.LOG) {
            Log.d(TAG, "updateGalleryIcon")
            debug_time = System.currentTimeMillis()
        }
        if (update_gallery_future != null) {
            Log.d(TAG, "previous updateGalleryIcon task already running")
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val ghost_image_pref: String = sharedPreferences.getString(
            PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off"
        )!!
        val ghost_image_last = ghost_image_pref == "preference_ghost_image_last"

        val handler = Handler(Looper.getMainLooper())

        val runnable: Runnable = object : Runnable {
            private val TAG = "updateGalleryIcon"
            private var uri: Uri? = null
            private var is_raw = false
            private var is_video = false

            //protected Bitmap doInBackground(Void... params) {
            override fun run() {
                if (MyDebug.LOG) Log.d(TAG, "doInBackground")
                val media = applicationInterface!!.storageUtils.getLatestMedia()
                var thumbnail: Bitmap? = null
                val keyguard_manager =
                    this@MainActivity.getSystemService(KEYGUARD_SERVICE) as KeyguardManager?
                val is_locked =
                    keyguard_manager != null && keyguard_manager.inKeyguardRestrictedInputMode()
                if (MyDebug.LOG) Log.d(TAG, "is_locked?: $is_locked")
                if (media != null && contentResolver != null && !is_locked) {
                    // check for getContentResolver() != null, as have had reported Google Play crashes

                    uri = media.getMediaStoreUri(this@MainActivity)
                    is_raw = media.filename != null && StorageUtils.filenameIsRaw(media.filename)
                    is_video = media.video

                    if (ghost_image_last && !media.video) {
                        if (MyDebug.LOG) Log.d(
                            TAG, "load full size bitmap for ghost image last photo"
                        )
                        // use sample factor of 1 so that it's full size for ghost image
                        thumbnail = loadThumbnailFromUri(media.uri, 1)
                    }
                    if (thumbnail == null) {
                        try {
                            if (!media.video) {
                                if (MyDebug.LOG) Log.d(TAG, "load thumbnail for photo")
                                // use sample factor as this image is only used for thumbnail; and
                                // unlike code in MyApplicationInterface.saveImage() we don't need to
                                // worry about the thumbnail animation when taking/saving a photo
                                thumbnail = loadThumbnailFromUri(media.uri, 8)
                            } else if (!media.mediastore) {
                                if (MyDebug.LOG) Log.d(TAG, "load thumbnail for video from SAF uri")
                                var pfd_saf: ParcelFileDescriptor? =
                                    null // keep a reference to this as long as retriever, to avoid risk of pfd_saf being garbage collected
                                val retriever = MediaMetadataRetriever()
                                try {
                                    pfd_saf = contentResolver.openFileDescriptor(media.uri, "r")
                                    retriever.setDataSource(pfd_saf!!.fileDescriptor)
                                    thumbnail = retriever.getFrameAtTime(-1)
                                } catch (e: Exception) {
                                    Log.d(TAG, "failed to load video thumbnail")
                                    e.printStackTrace()
                                } finally {
                                    try {
                                        retriever.release()
                                    } catch (ex: RuntimeException) {
                                        // ignore
                                    }
                                    try {
                                        pfd_saf?.close()
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    }
                                }
                            } else {
                                if (MyDebug.LOG) Log.d(TAG, "load thumbnail for video")
                                thumbnail = MediaStore.Video.Thumbnails.getThumbnail(
                                    contentResolver,
                                    media.id,
                                    MediaStore.Video.Thumbnails.MINI_KIND,
                                    null
                                )
                            }
                        } catch (exception: Throwable) {
                            // have had Google Play NoClassDefFoundError crashes from getThumbnail() for Galaxy Ace4 (vivalto3g), Galaxy S Duos3 (vivalto3gvn)
                            // also NegativeArraySizeException - best to catch everything
                            if (MyDebug.LOG) Log.e(TAG, "thumbnail exception")
                            exception.printStackTrace()
                        }
                    }
                }

                //return thumbnail;
                val thumbnailF = thumbnail
                handler.post { onPostExecute(thumbnailF) }
            }

            /** Runs on UI thread, after background work is complete.
             */
            fun onPostExecute(thumbnail: Bitmap?) {
                if (MyDebug.LOG) Log.d(TAG, "onPostExecute")
                if (update_gallery_future != null && update_gallery_future!!.isCancelled) {
                    if (MyDebug.LOG) Log.d(TAG, "was cancelled")
                    update_gallery_future = null
                    return
                }
                // since we're now setting the thumbnail to the latest media on disk, we need to make sure clicking the Gallery goes to this
                applicationInterface!!.storageUtils.clearLastMediaScanned()
                if (uri != null) {
                    if (MyDebug.LOG) {
                        Log.d(TAG, "found media uri: $uri")
                        Log.d(TAG, "    is_raw?: $is_raw")
                    }
                    applicationInterface!!.storageUtils.setLastMediaScanned(
                        uri, is_raw, false, null
                    )
                }
                if (thumbnail != null) {
                    if (MyDebug.LOG) Log.d(TAG, "set gallery button to thumbnail")
                    updateGalleryIcon(thumbnail)
                    applicationInterface!!.drawPreview?.updateThumbnail(
                        thumbnail, is_video, false
                    ) // needed in case last ghost image is enabled
                } else {
                    if (MyDebug.LOG) Log.d(TAG, "set gallery button to blank")
                    updateGalleryIconToBlank()
                }

                update_gallery_future = null
            } //}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        val executor = Executors.newSingleThreadExecutor()
        update_gallery_future = executor.submit(runnable)

        if (MyDebug.LOG) Log.d(
            TAG,
            "updateGalleryIcon: total time to update gallery icon: " + (System.currentTimeMillis() - debug_time)
        )
    }

    fun savingImage(started: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "savingImage: " + started)
//        this.runOnUiThread(object : Runnable {
//            override fun run() {
//                val galleryButton = binding.gallery
//                if (started) {
//                    //galleryButton.setColorFilter(0x80ffffff, PorterDuff.Mode.MULTIPLY);
//                    if (gallery_save_anim == null) {
//                        gallery_save_anim = ValueAnimator.ofInt(
//                            Color.argb(200, 255, 255, 255), Color.argb(63, 255, 255, 255)
//                        )
//                        gallery_save_anim!!.setEvaluator(ArgbEvaluator())
//                        gallery_save_anim!!.setRepeatCount(ValueAnimator.INFINITE)
//                        gallery_save_anim!!.setRepeatMode(ValueAnimator.REVERSE)
//                        gallery_save_anim!!.setDuration(500)
//                    }
//                    gallery_save_anim!!.addUpdateListener(object : AnimatorUpdateListener {
//                        override fun onAnimationUpdate(animation: ValueAnimator) {
//                            galleryButton.setColorFilter(
//                                (animation.getAnimatedValue() as Int?)!!, PorterDuff.Mode.MULTIPLY
//                            )
//                        }
//                    })
//                    gallery_save_anim!!.start()
//                } else if (gallery_save_anim != null) {
//                    gallery_save_anim!!.cancel()
//                }
//                galleryButton.setColorFilter(null)
//            }
//        })
    }

    /** Called when the number of images being saved in ImageSaver changes (or otherwise something
     * that changes our calculation of whether we can take a new photo, e.g., changing photo mode).
     */
    fun imageQueueChanged() {
        if (MyDebug.LOG) Log.d(TAG, "imageQueueChanged")
        applicationInterface!!.drawPreview?.setImageQueueFull(!applicationInterface!!.canTakeNewPhoto())

        /*if( applicationInterface.getImageSaver().getNImagesToSave() == 0) {
            cancelImageSavingNotification();
        }
        else if( has_notification ) {
            // call again to update the text of remaining images
            createImageSavingNotification();
        }*/
    }

    /** Creates a notification to indicate still saving images (or updates an existing one).
     * Update: notifications now removed due to needing permissions on Android 13+.
     */
    private fun createImageSavingNotification() {
        if (MyDebug.LOG) Log.d(TAG, "createImageSavingNotification")/*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            int n_images_to_save = applicationInterface.getImageSaver().getNRealImagesToSave();
            Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_notify_take_photo)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.image_saving_notification) + " " + n_images_to_save + " " + getString(R.string.remaining))
                    //.setStyle(new Notification.BigTextStyle()
                    //        .bigText("Much longer text that cannot fit one line..."))
                    //.setPriority(Notification.PRIORITY_DEFAULT)
                    ;
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.notify(image_saving_notification_id, builder.build());
            has_notification = true;
        }*/
    }

    /** Cancels the notification for saving images.
     * Update: notifications now removed due to needing permissions on Android 13+.
     */
    private fun cancelImageSavingNotification() {
        if (MyDebug.LOG) Log.d(TAG, "cancelImageSavingNotification")/*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.cancel(image_saving_notification_id);
            has_notification = false;
        }*/
    }

    fun clickedGallery() {
        if (MyDebug.LOG) Log.d(TAG, "clickedGallery")
        openGallery()
    }

    private fun openGallery() {
        if (MyDebug.LOG) Log.d(TAG, "openGallery")
        //Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        var uri = applicationInterface!!.storageUtils.getLastMediaScanned()
        var is_raw = uri != null && applicationInterface!!.storageUtils.getLastMediaScannedIsRaw()
        if (MyDebug.LOG && uri != null) {
            Log.d(TAG, "found cached most recent uri: " + uri)
            Log.d(TAG, "    is_raw: " + is_raw)
        }
        if (uri == null) {
            if (MyDebug.LOG) Log.d(TAG, "go to latest media")
            val media = applicationInterface!!.storageUtils.getLatestMedia()
            if (media != null) {
                if (MyDebug.LOG) {
                    Log.d(TAG, "latest uri:" + media.uri)
                    Log.d(TAG, "filename: " + media.filename)
                }
                uri = media.getMediaStoreUri(this)
                if (MyDebug.LOG) Log.d(TAG, "media uri:" + uri)
                is_raw = media.filename != null && StorageUtils.filenameIsRaw(media.filename)
                if (MyDebug.LOG) Log.d(TAG, "is_raw:" + is_raw)
            }
        }

        if (uri != null && !useScopedStorage()) {
            // check uri exists
            // note, with scoped storage this isn't reliable when using SAF - since we don't actually have permission to access mediastore URIs that
            // were created via Storage Access Framework, even though ManualCamera was the application that saved them(!)
            try {
                val cr = getContentResolver()
                val pfd = cr.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    if (MyDebug.LOG) Log.d(TAG, "uri no longer exists (1): " + uri)
                    uri = null
                    is_raw = false
                } else {
                    pfd.close()
                }
            } catch (e: IOException) {
                if (MyDebug.LOG) Log.d(TAG, "uri no longer exists (2): " + uri)
                uri = null
                is_raw = false
            }
        }
        if (uri == null) {
            uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            is_raw = false
        }
        if (!is_test) {
            // don't do if testing, as unclear how to exit activity to finish test (for testGallery())
            if (MyDebug.LOG) Log.d(TAG, "launch uri:" + uri)
            val REVIEW_ACTION = "com.android.camera.action.REVIEW"
            var done = false
            if (!is_raw) {
                // REVIEW_ACTION means we can view video files without autoplaying.
                // However, Google Photos at least has problems with going to a RAW photo (in RAW only mode),
                // unless we first pause and resume ManualCamera.
                // Update: on Galaxy S10e with Android 11 at least, no longer seem to have problems, but leave
                // the check for is_raw just in case for older devices.
                if (MyDebug.LOG) Log.d(TAG, "try REVIEW_ACTION")
                try {
                    val intent = Intent(REVIEW_ACTION, uri)
                    this.startActivity(intent)
                    done = true
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            if (!done) {
                if (MyDebug.LOG) Log.d(TAG, "try ACTION_VIEW")
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    this.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    preview?.showToast(null, R.string.no_gallery_app)
                } catch (e: SecurityException) {
                    // have received this crash from Google Play - don't display a toast, simply do nothing
                    Log.e(TAG, "SecurityException from ACTION_VIEW startActivity")
                    e.printStackTrace()
                }
            }
        }
    }

    /** Opens the Storage Access Framework dialog to select a folder for save location.
     * @param from_preferences Whether called from the Preferences
     */
    fun openFolderChooserDialogSAF(from_preferences: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "openFolderChooserDialogSAF: " + from_preferences)
        this.saf_dialog_from_preferences = from_preferences
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        //Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        //intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, CHOOSE_SAVE_FOLDER_SAF_CODE)
    }

    /** Opens the Storage Access Framework dialog to select a file for ghost image.
     * @param from_preferences Whether called from the Preferences
     */
    fun openGhostImageChooserDialogSAF(from_preferences: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "openGhostImageChooserDialogSAF: " + from_preferences)
        this.saf_dialog_from_preferences = from_preferences
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("image/*")
        try {
            startActivityForResult(intent, CHOOSE_GHOST_IMAGE_SAF_CODE)
        } catch (e: ActivityNotFoundException) {
            // see https://stackoverflow.com/questions/34021039/action-open-document-not-working-on-miui/34045627
            preview?.showToast(null, R.string.open_files_saf_exception_ghost)
            Log.e(TAG, "ActivityNotFoundException from startActivityForResult")
            e.printStackTrace()
        }
    }

    /** Opens the Storage Access Framework dialog to select a file for loading settings.
     * @param from_preferences Whether called from the Preferences
     */
    fun openLoadSettingsChooserDialogSAF(from_preferences: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "openLoadSettingsChooserDialogSAF: " + from_preferences)
        this.saf_dialog_from_preferences = from_preferences
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("text/xml") // note that application/xml doesn't work (can't select the xml files)!
        try {
            startActivityForResult(intent, CHOOSE_LOAD_SETTINGS_SAF_CODE)
        } catch (e: ActivityNotFoundException) {
            // see https://stackoverflow.com/questions/34021039/action-open-document-not-working-on-miui/34045627
            preview?.showToast(null, R.string.open_files_saf_exception_generic)
            Log.e(TAG, "ActivityNotFoundException from startActivityForResult")
            e.printStackTrace()
        }
    }

    /** Call when the SAF save history has been updated.
     * This is only public so we can call from testing.
     * @param save_folder The new SAF save folder Uri.
     */
    fun updateFolderHistorySAF(save_folder: String?) {
        if (MyDebug.LOG) Log.d(TAG, "updateSaveHistorySAF")
        if (this.saveLocationHistorySAF == null) {
            this.saveLocationHistorySAF =
                SaveLocationHistory(this, "save_location_history_saf", save_folder)
        }
        saveLocationHistorySAF!!.updateFolderHistory(save_folder, true)
    }

    /** Listens for the response from the Storage Access Framework dialog to select a folder
     * (as opened with openFolderChooserDialogSAF()).
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (MyDebug.LOG) Log.d(TAG, "onActivityResult: " + requestCode)

        super.onActivityResult(requestCode, resultCode, resultData)

        when (requestCode) {
            CHOOSE_SAVE_FOLDER_SAF_CODE -> {
                if (resultCode == RESULT_OK && resultData != null) {
                    val treeUri = resultData.getData()
                    if (MyDebug.LOG) Log.d(TAG, "returned treeUri: " + treeUri)
                    // see https://developer.android.com/training/data-storage/shared/documents-files#persist-permissions :
                    val takeFlags =
                        resultData.getFlags() and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    try {/*if( true )
						throw new SecurityException(); // test*/
                        getContentResolver().takePersistableUriPermission(treeUri!!, takeFlags)

                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                        val editor = sharedPreferences.edit()
                        editor.putString(
                            PreferenceKeys.SaveLocationSAFPreferenceKey, treeUri.toString()
                        )
                        editor.apply()

                        if (MyDebug.LOG) Log.d(TAG, "update folder history for saf")
                        updateFolderHistorySAF(treeUri.toString())

                        val file = applicationInterface!!.storageUtils.getImageFolderPath()
                        if (file != null) {
                            preview?.showToast(
                                null,
                                getResources().getString(R.string.changed_save_location) + "\n" + file
                            )
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException failed to take permission")
                        e.printStackTrace()
                        preview?.showToast(null, R.string.saf_permission_failed)
                        // failed - if the user had yet to set a save location, make sure we switch SAF back off
                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                        val uri: String = sharedPreferences.getString(
                            PreferenceKeys.SaveLocationSAFPreferenceKey, ""
                        )!!
                        if (uri.length == 0) {
                            if (MyDebug.LOG) Log.d(TAG, "no SAF save location was set")
                            val editor = sharedPreferences.edit()
                            editor.putBoolean(PreferenceKeys.UsingSAFPreferenceKey, false)
                            editor.apply()
                        }
                    }
                } else {
                    if (MyDebug.LOG) Log.d(TAG, "SAF dialog cancelled")
                    // cancelled - if the user had yet to set a save location, make sure we switch SAF back off
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                    val uri: String = sharedPreferences.getString(
                        PreferenceKeys.SaveLocationSAFPreferenceKey, ""
                    )!!
                    if (uri.length == 0) {
                        if (MyDebug.LOG) Log.d(TAG, "no SAF save location was set")
                        val editor = sharedPreferences.edit()
                        editor.putBoolean(PreferenceKeys.UsingSAFPreferenceKey, false)
                        editor.apply()
                        preview?.showToast(null, R.string.saf_cancelled)
                    }
                }

                if (!saf_dialog_from_preferences) {
                    setWindowFlagsForCamera()
                    showPreview(true)
                }
            }

            CHOOSE_GHOST_IMAGE_SAF_CODE -> {
                if (resultCode == RESULT_OK && resultData != null) {
                    val fileUri = resultData.getData()
                    if (MyDebug.LOG) Log.d(TAG, "returned single fileUri: " + fileUri)
                    // persist permission just in case?
                    val takeFlags =
                        (resultData.getFlags() and (Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    try {/*if( true )
						throw new SecurityException(); // test*/
                        // Check for the freshest data.
                        getContentResolver().takePersistableUriPermission(fileUri!!, takeFlags)

                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                        val editor = sharedPreferences.edit()
                        editor.putString(
                            PreferenceKeys.GhostSelectedImageSAFPreferenceKey, fileUri.toString()
                        )
                        editor.apply()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException failed to take permission")
                        e.printStackTrace()
                        preview?.showToast(null, R.string.saf_permission_failed_open_image)
                        // failed - if the user had yet to set a ghost image
                        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                        val uri: String = sharedPreferences.getString(
                            PreferenceKeys.GhostSelectedImageSAFPreferenceKey, ""
                        )!!
                        if (uri.length == 0) {
                            if (MyDebug.LOG) Log.d(TAG, "no SAF ghost image was set")
                            val editor = sharedPreferences.edit()
                            editor.putString(
                                PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off"
                            )
                            editor.apply()
                        }
                    }
                } else {
                    if (MyDebug.LOG) Log.d(TAG, "SAF dialog cancelled")
                    // cancelled - if the user had yet to set a ghost image, make sure we switch the option back off
                    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                    val uri: String = sharedPreferences.getString(
                        PreferenceKeys.GhostSelectedImageSAFPreferenceKey, ""
                    )!!
                    if (uri.length == 0) {
                        if (MyDebug.LOG) Log.d(TAG, "no SAF ghost image was set")
                        val editor = sharedPreferences.edit()
                        editor.putString(
                            PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off"
                        )
                        editor.apply()
                    }
                }

                if (!saf_dialog_from_preferences) {
                    setWindowFlagsForCamera()
                    showPreview(true)
                }
            }

            CHOOSE_LOAD_SETTINGS_SAF_CODE -> {
                if (resultCode == RESULT_OK && resultData != null) {
                    val fileUri = resultData.getData()
                    if (MyDebug.LOG) Log.d(TAG, "returned single fileUri: " + fileUri)
                    // persist permission just in case?
                    val takeFlags =
                        (resultData.getFlags() and (Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    try {/*if( true )
						throw new SecurityException(); // test*/
                        // Check for the freshest data.
                        getContentResolver().takePersistableUriPermission(fileUri!!, takeFlags)

                        settingsManager!!.loadSettings(fileUri)
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException failed to take permission")
                        e.printStackTrace()
                        preview?.showToast(null, R.string.restore_settings_failed)
                    }
                } else {
                    if (MyDebug.LOG) Log.d(TAG, "SAF dialog cancelled")
                }

                if (!saf_dialog_from_preferences) {
                    setWindowFlagsForCamera()
                    showPreview(true)
                }
            }
        }
    }

    /** Update the save folder (for non-SAF methods).
     */
    fun updateSaveFolder(new_save_location: String?) {
        if (MyDebug.LOG) Log.d(TAG, "updateSaveFolder: " + new_save_location)
        if (new_save_location != null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val orig_save_location = this.applicationInterface!!.storageUtils.getSaveLocation()

            if (orig_save_location != new_save_location) {
                if (MyDebug.LOG) Log.d(
                    TAG,
                    "changed save_folder to: " + this.applicationInterface!!.storageUtils.getSaveLocation()
                )
                val editor = sharedPreferences.edit()
                editor.putString(PreferenceKeys.SaveLocationPreferenceKey, new_save_location)
                editor.apply()

                this.save_location_history!!.updateFolderHistory(
                    this.storageUtils!!.getSaveLocation(), true
                )
                val save_folder_name = getHumanReadableSaveFolder(
                    this.applicationInterface!!.storageUtils.getSaveLocation()
                )
                this.preview?.showToast(
                    null,
                    getResources().getString(R.string.changed_save_location) + "\n" + save_folder_name
                )
            }
        }
    }

    class MyFolderChooserDialog : FolderChooserDialog() {
        override fun onDismiss(dialog: DialogInterface?) {
            if (MyDebug.LOG) Log.d(TAG, "FolderChooserDialog dismissed")
            // n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
            // so we access the MainActivity via the fragment's getActivity().
            val main_activity = this.getActivity() as MainActivity?
            // activity may be null, see https://stackoverflow.com/questions/13116104/best-practice-to-reference-the-parent-activity-of-a-fragment
            // have had Google Play crashes from this
            if (main_activity != null) {
                main_activity.setWindowFlagsForCamera()
                main_activity.showPreview(true)
                val new_save_location = this.getChosenFolder()
                main_activity.updateSaveFolder(new_save_location)
            } else {
                if (MyDebug.LOG) Log.e(TAG, "activity no longer exists!")
            }
            super.onDismiss(dialog)
        }
    }

    /** Creates a dialog builder for specifying a save folder dialog (used when not using SAF,
     * and on scoped storage, as an alternative to using FolderChooserDialog).
     */
    fun createSaveFolderDialog(): AlertDialog.Builder {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(R.string.preference_save_location)

        val dialog_view = LayoutInflater.from(this).inflate(R.layout.alertdialog_edittext, null)
        val editText = dialog_view.findViewById<EditText>(R.id.edit_text)

        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
        editText.setHint(getResources().getString(R.string.preference_save_location))
        editText.setInputType(InputType.TYPE_CLASS_TEXT)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        editText.setText(
            sharedPreferences.getString(
                PreferenceKeys.SaveLocationPreferenceKey, "ManualCamera"
            )
        )
        val filter: InputFilter = object : InputFilter {
            // whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
            val disallowed: String = "|\\?*<\":>"
            override fun filter(
                source: CharSequence, start: Int, end: Int, dest: Spanned?, dstart: Int, dend: Int
            ): CharSequence? {
                for (i in start..<end) {
                    if (disallowed.indexOf(source.get(i)) != -1) {
                        return ""
                    }
                }
                // also check for '/', not allowed at start
                if (dstart == 0 && start < source.length && source.get(start) == '/') {
                    return ""
                }
                return null
            }
        }
        editText.setFilters(arrayOf<InputFilter>(filter))

        alertDialog.setView(dialog_view)

        alertDialog.setPositiveButton(
            android.R.string.ok, object : DialogInterface.OnClickListener {
                override fun onClick(dialogInterface: DialogInterface?, i: Int) {
                    if (MyDebug.LOG) Log.d(TAG, "save location clicked okay")

                    var folder = editText.getText().toString()
                    folder = processUserSaveLocation(folder)

                    updateSaveFolder(folder)
                }
            })
        alertDialog.setNegativeButton(android.R.string.cancel, null)

        return alertDialog
    }

    /** Opens ManualCamera's own (non-Storage Access Framework) dialog to select a folder.
     */
    private fun openFolderChooserDialog() {
        if (MyDebug.LOG) Log.d(TAG, "openFolderChooserDialog")
        showPreview(false)
        setWindowFlagsForSettings()

        if (useScopedStorage()) {
            val alertDialog = createSaveFolderDialog()
            val alert = alertDialog.create()
            // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
            alert.setOnDismissListener(object : DialogInterface.OnDismissListener {
                override fun onDismiss(arg0: DialogInterface?) {
                    if (MyDebug.LOG) Log.d(TAG, "save folder dialog dismissed")
                    setWindowFlagsForCamera()
                    showPreview(true)
                }
            })
            alert.show()
        } else {
            val start_folder = this.storageUtils!!.getImageFolder()

            val fragment: FolderChooserDialog = MyFolderChooserDialog()
            fragment.setStartFolder(start_folder)
            // use commitAllowingStateLoss() instead of fragment.show(), does to "java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState" crash seen on Google Play
            // see https://stackoverflow.com/questions/14262312/java-lang-illegalstateexception-can-not-perform-this-action-after-onsaveinstanc
            //fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
            getFragmentManager().beginTransaction().add(fragment, "FOLDER_FRAGMENT")
                .commitAllowingStateLoss()
        }
    }

    /** Returns a human readable string for the save_folder (as stored in the preferences).
     */
    private fun getHumanReadableSaveFolder(save_folder: String): String {
        var save_folder = save_folder
        if (applicationInterface!!.storageUtils.isUsingSAF()) {
            // try to get human readable form if possible
            val file_name = applicationInterface!!.storageUtils.getFilePathFromDocumentUriSAF(
                Uri.parse(save_folder), true
            )
            if (file_name != null) {
                save_folder = file_name
            }
        } else {
            // The strings can either be a sub-folder of DCIM, or (pre-scoped-storage) a full path, so normally either can be displayed.
            // But with scoped storage, an empty string is used to mean DCIM, so seems clearer to say that instead of displaying a blank line!
            if (useScopedStorage() && save_folder.length == 0) {
                save_folder = "DCIM"
            }
        }
        return save_folder
    }

    /** User can long-click on gallery to select a recent save location from the history, of if not available,
     * go straight to the file dialog to pick a folder.
     */
    private fun longClickedGallery() {
        if (MyDebug.LOG) Log.d(TAG, "longClickedGallery")
        if (applicationInterface!!.storageUtils.isUsingSAF()) {
            if (this.saveLocationHistorySAF == null || saveLocationHistorySAF!!.size() <= 1) {
                if (MyDebug.LOG) Log.d(TAG, "go straight to choose folder dialog for SAF")
                openFolderChooserDialogSAF(false)
                return
            }
        } else {
            if (save_location_history!!.size() <= 1) {
                if (MyDebug.LOG) Log.d(TAG, "go straight to choose folder dialog")
                openFolderChooserDialog()
                return
            }
        }

        val history =
            (if (applicationInterface?.storageUtils?.isUsingSAF() == true) this.saveLocationHistorySAF else save_location_history)!!
        showPreview(false)
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(R.string.choose_save_location)
        val items = arrayOfNulls<CharSequence>(history.size() + 2)
        var index = 0
        // history is stored in order most-recent-last
        for (i in 0..<history.size()) {
            var folder_name = history.get(history.size() - 1 - i)
            folder_name = getHumanReadableSaveFolder(folder_name)
            items[index++] = folder_name
        }
        val clear_index = index
        items[index++] = getResources().getString(R.string.clear_folder_history)
        val new_index = index
        items[index++] = getResources().getString(R.string.choose_another_folder)
        //alertDialog.setItems(items, new DialogInterface.OnClickListener() {
        alertDialog.setSingleChoiceItems(items, 0, object : DialogInterface.OnClickListener {
            override fun onClick(dialog: DialogInterface, which: Int) {
                if (which == clear_index) {
                    if (MyDebug.LOG) Log.d(TAG, "selected clear save history")
                    AlertDialog.Builder(this@MainActivity)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle(R.string.clear_folder_history)
                        .setMessage(R.string.clear_folder_history_question).setPositiveButton(
                            android.R.string.yes, object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface?, which: Int) {
                                    if (MyDebug.LOG) Log.d(TAG, "confirmed clear save history")
                                    if (applicationInterface!!.storageUtils.isUsingSAF()) clearFolderHistorySAF()
                                    else clearFolderHistory()
                                    setWindowFlagsForCamera()
                                    showPreview(true)
                                }
                            }).setNegativeButton(
                            android.R.string.no, object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface?, which: Int) {
                                    if (MyDebug.LOG) Log.d(TAG, "don't clear save history")
                                    setWindowFlagsForCamera()
                                    showPreview(true)
                                }
                            }).setOnCancelListener(object : DialogInterface.OnCancelListener {
                            override fun onCancel(arg0: DialogInterface?) {
                                if (MyDebug.LOG) Log.d(TAG, "cancelled clear save history")
                                setWindowFlagsForCamera()
                                showPreview(true)
                            }
                        }).show()
                } else if (which == new_index) {
                    if (MyDebug.LOG) Log.d(TAG, "selected choose new folder")
                    if (applicationInterface!!.storageUtils.isUsingSAF()) {
                        openFolderChooserDialogSAF(false)
                    } else {
                        openFolderChooserDialog()
                    }
                } else {
                    if (MyDebug.LOG) Log.d(TAG, "selected: " + which)
                    if (which >= 0 && which < history.size()) {
                        val save_folder = history.get(history.size() - 1 - which)
                        if (MyDebug.LOG) Log.d(
                            TAG, "changed save_folder from history to: " + save_folder
                        )
                        val save_folder_name = getHumanReadableSaveFolder(save_folder)
                        preview?.showToast(
                            null,
                            getResources().getString(R.string.changed_save_location) + "\n" + save_folder_name
                        )
                        val sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
                        val editor = sharedPreferences.edit()
                        if (applicationInterface!!.storageUtils.isUsingSAF()) editor.putString(
                            PreferenceKeys.SaveLocationSAFPreferenceKey, save_folder
                        )
                        else editor.putString(PreferenceKeys.SaveLocationPreferenceKey, save_folder)
                        editor.apply()
                        history.updateFolderHistory(
                            save_folder, true
                        ) // to move new selection to most recent
                    }
                    setWindowFlagsForCamera()
                    showPreview(true)
                }

                dialog.dismiss() // need to explicitly dismiss for setSingleChoiceItems
            }
        })
        alertDialog.setOnCancelListener(object : DialogInterface.OnCancelListener {
            override fun onCancel(arg0: DialogInterface?) {
                setWindowFlagsForCamera()
                showPreview(true)
            }
        })
        //getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        setWindowFlagsForSettings()
        showAlert(alertDialog.create())
    }

    /** Clears the non-SAF folder history.
     */
    fun clearFolderHistory() {
        if (MyDebug.LOG) Log.d(TAG, "clearFolderHistory")
        save_location_history!!.clearFolderHistory(this.storageUtils!!.getSaveLocation())
    }

    /** Clears the SAF folder history.
     */
    fun clearFolderHistorySAF() {
        if (MyDebug.LOG) Log.d(TAG, "clearFolderHistorySAF")
        saveLocationHistorySAF!!.clearFolderHistory(this.storageUtils!!.getSaveLocationSAF())
    }

    fun clickedShare() {
        if (MyDebug.LOG) Log.d(TAG, "clickedShare")
        applicationInterface!!.shareLastImage()
    }

    fun clickedTrash() {
        if (MyDebug.LOG) Log.d(TAG, "clickedTrash")
        applicationInterface!!.trashLastImage()
    }

    /** User has pressed the take picture button, or done an equivalent action to request this (e.g.,
     * volume buttons, audio trigger).
     * @param photoSnapshot If true, then the user has requested taking a photo whilst video
     * recording. If false, either take a photo or start/stop video depending
     * on the current mode.
     */
    fun takePicture(photoSnapshot: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "takePicture")
        if (applicationInterface?.photoMode == PhotoMode.Panorama) {
            if (preview?.isTakingPhoto ?: false) {
                if (MyDebug.LOG) Log.d(TAG, "ignore whilst taking panorama photo")
            } else if (applicationInterface?.gyroSensor?.isRecording == true) {
                if (MyDebug.LOG) Log.d(TAG, "panorama complete")
                applicationInterface?.finishPanorama()
                return
            } else if (applicationInterface?.canTakeNewPhoto() == false) {
                if (MyDebug.LOG) Log.d(TAG, "can't start new panorama, still saving in background")
                // we need to test here, otherwise the Preview won't take a new photo - but we'll think we've
                // started the panorama!
            } else {
                if (MyDebug.LOG) Log.d(TAG, "start panorama")
                applicationInterface?.startPanorama()
            }
        }

        this.takePicturePressed(photoSnapshot, false)
    }

    /** Returns whether the last photo operation was a continuous fast burst.
     */
    fun lastContinuousFastBurst(): Boolean {
        return this.last_continuous_fast_burst
    }

    /**
     * @param photo_snapshot If true, then the user has requested taking a photo whilst video
     * recording. If false, either take a photo or start/stop video depending
     * on the current mode.
     * @param continuous_fast_burst If true, then start a continuous fast burst.
     */
    fun takePicturePressed(photo_snapshot: Boolean, continuous_fast_burst: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "takePicturePressed")

        closePopup()

        this.last_continuous_fast_burst = continuous_fast_burst
        this.preview?.takePicturePressed(photo_snapshot, continuous_fast_burst)

        if (preview?.isVideo == true) {
            viewModel.setPhotoMode(false)
            if (preview?.isVideoRecording == true) {
                viewModel.setVideoRecording(true)
            } else {
                viewModel.setVideoRecording(false)
            }
        } else {
            viewModel.setPhotoMode(true)
        }
    }

    /** Lock the screen - this is ManualCamera's own lock to guard against accidental presses,
     * not the standard Android lock.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun lockScreen() {
        binding.locker.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(arg0: View?, event: MotionEvent): Boolean {
                return gestureDetector!!.onTouchEvent(event)
            }
        })
        this.isScreenLocked = true
        this.enableScreenLockOnBackPressedCallback(true) // also disable back button
    }

    /** Unlock the screen (see lockScreen()).
     */
    fun unlockScreen() {
        binding.locker.setOnTouchListener(null)
        this.isScreenLocked = false
        this.enableScreenLockOnBackPressedCallback(false) // reenable back button
    }

    /** Listen for gestures.
     * Doing a swipe will unlock the screen (see lockScreen()).
     */
    private inner class MyGestureDetector : SimpleOnGestureListener() {

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
        ): Boolean {
            try {
                e1?.let {
                    if (MyDebug.LOG) Log.d(
                        TAG,
                        "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY()
                    )
                    val vc = ViewConfiguration.get(this@MainActivity)
                    //final int swipeMinDistance = 4*vc.getScaledPagingTouchSlop();
                    val scale = getResources().getDisplayMetrics().density
                    val swipeMinDistance = (160 * scale + 0.5f).toInt() // convert dps to pixels
                    val swipeThresholdVelocity = vc.getScaledMinimumFlingVelocity()
                    if (MyDebug.LOG) {
                        Log.d(
                            TAG,
                            "from " + e1.getX() + " , " + e1.getY() + " to " + e2.getX() + " , " + e2.getY()
                        )
                        Log.d(TAG, "swipeMinDistance: " + swipeMinDistance)
                    }
                    val xdist = e1.getX() - e2.getX()
                    val ydist = e1.getY() - e2.getY()
                    val dist2 = xdist * xdist + ydist * ydist
                    val vel2 = velocityX * velocityX + velocityY * velocityY
                    if (dist2 > swipeMinDistance * swipeMinDistance && vel2 > swipeThresholdVelocity * swipeThresholdVelocity) {
                        preview?.showToast(screen_locked_toast, R.string.unlocked)
                        unlockScreen()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        override fun onDown(e: MotionEvent): Boolean {
            preview?.showToast(screen_locked_toast, R.string.screen_is_locked)
            return true
        }
    }

    override fun onSaveInstanceState(state: Bundle) {
        if (MyDebug.LOG) Log.d(TAG, "onSaveInstanceState")
        super.onSaveInstanceState(state)
        if (this.preview != null) {
            preview?.onSaveInstanceState(state)
        }
        if (this.applicationInterface != null) {
            applicationInterface!!.onSaveInstanceState(state)
        }
    }

    fun supportsExposureButton(): Boolean {
        if (preview?.isVideoHighSpeed() == true) {
            // manual ISO/exposure not supported for high speed video mode
            // it's safer not to allow opening the panel at all (otherwise the user could open it, and switch to manual)
            return false
        }
        if (applicationInterface!!.isCameraExtensionPref()) {
            // nothing in this UI (exposure compensation, manual ISO/exposure, manual white balance) is supported for camera extensions
            return false
        }
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val isoValue: String = sharedPreferences.getString(
            PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT
        )!!
        val manualIso = isoValue != CameraController.ISO_DEFAULT
        return preview?.supportsExposures() == true || (manualIso && preview?.supportsISORange() == true)
    }

    fun cameraSetup() {
        var debugTime: Long = 0
        if (MyDebug.LOG) {
            Log.d(TAG, "cameraSetup")
            debugTime = System.currentTimeMillis()
        }
        if (preview?.cameraController == null) {
            if (MyDebug.LOG) Log.d(TAG, "camera controller is null")
            return
        }

        val oldWantNoLimits = want_no_limits
        this.want_no_limits = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode) {
            if (MyDebug.LOG) Log.d(TAG, "multi-window mode")
            // don't support want_no_limits mode in multi-window mode - extra complexity that the
            // preview size could change from simply resizing the window; also problem that the
            // navigation_gap, and whether we'd want want_no_limits, can both change depending on
            // device orientation (because application can e.g. be in landscape mode even if device
            // has switched to portrait)
        } else if (set_window_insets_listener && !edgeToEdgeMode) {
            val display_size = Point()
            applicationInterface!!.getDisplaySize(display_size, true)
            val display_width = max(display_size.x, display_size.y)
            val display_height = min(display_size.x, display_size.y)
            val display_aspect_ratio = ((display_width.toDouble()) / display_height.toDouble())
            val preview_aspect_ratio = preview?.currentPreviewAspectRatio ?: 0.0
            var preview_is_wide = preview_aspect_ratio > display_aspect_ratio + 1.0e-5f
            if (test_preview_want_no_limits) {
                preview_is_wide = test_preview_want_no_limits_value
            }
            if (preview_is_wide) {
                if (MyDebug.LOG) Log.d(TAG, "preview is wide, set want_no_limits")
                this.want_no_limits = true

                if (!oldWantNoLimits) {
                    if (MyDebug.LOG) Log.d(TAG, "need to change to FLAG_LAYOUT_NO_LIMITS")
                    // Ideally we'd just go straight to FLAG_LAYOUT_NO_LIMITS mode, but then all calls to onApplyWindowInsets()
                    // end up returning a value of 0 for the navigation_gap! So we need to wait until we know the navigation_gap.
                    if (navigation_gap != 0) {
                        // already have navigation gap, can go straight into no limits mode
                        if (MyDebug.LOG) Log.d(TAG, "set FLAG_LAYOUT_NO_LIMITS")
                        showUnderNavigation(true)
                        // need to layout the UI again due to now taking the navigation gap into account
                        if (MyDebug.LOG) Log.d(
                            TAG, "layout UI due to changing want_no_limits behaviour"
                        )
                    } else {
                        if (MyDebug.LOG) Log.d(TAG, "but navigation_gap is 0")
                    }
                }
            } else if (oldWantNoLimits && navigation_gap != 0) {
                if (MyDebug.LOG) Log.d(TAG, "clear FLAG_LAYOUT_NO_LIMITS")
                showUnderNavigation(false)
                // need to layout the UI again due to no longer taking the navigation gap into account
                if (MyDebug.LOG) Log.d(TAG, "layout UI due to changing want_no_limits behaviour")
            }
        }

        if (this.supportsForceVideo4K() && preview?.usingCamera2API() == true) {
            if (MyDebug.LOG) Log.d(TAG, "using Camera2 API, so can disable the force 4K option")
            this.disableForceVideo4K()
        }

        if (this.supportsForceVideo4K() && preview?.videoQualityHander?.getSupportedVideoSizes() != null) {
            preview?.videoQualityHander?.getSupportedVideoSizes()?.let {
                for (size in it) {
                    if (size.width >= 3840 && size.height >= 2160) {
                        if (MyDebug.LOG) Log.d(
                            TAG, "camera natively supports 4K, so can disable the force option"
                        )
                        this.disableForceVideo4K()
                    }
                }
            }

        }

        if (MyDebug.LOG) Log.d(
            TAG,
            "cameraSetup: time after handling Force 4K option: " + (System.currentTimeMillis() - debugTime)
        )

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        run {
            if (MyDebug.LOG) Log.d(TAG, "set up zoom")
            if (MyDebug.LOG) Log.d(TAG, "has_zoom? " + preview?.supportsZoom())
            val zoomControls = binding.zoom
            val zoomSeekBar = binding.zoomSeekbar

            if (preview?.supportsZoom() == true) {
                if (sharedPreferences.getBoolean(
                        PreferenceKeys.ShowZoomControlsPreferenceKey, false
                    )
                ) {
                    zoomControls.setIsZoomInEnabled(true)
                    zoomControls.setIsZoomOutEnabled(true)
                    zoomControls.setZoomSpeed(20)

                    zoomControls.setOnZoomInClickListener { zoomIn() }
                    zoomControls.setOnZoomOutClickListener { zoomOut() }
                    if (mainUI?.inImmersiveMode() == false) {
                        zoomControls.visibility = View.VISIBLE
                    }
                } else {
                    zoomControls.visibility = View.GONE
                }

                zoomSeekBar.setOnSeekBarChangeListener(null) // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                zoomSeekBar.max = preview?.maxZoom ?: 1
                zoomSeekBar.progress =
                    (preview?.maxZoom ?: 1) - (preview?.cameraController?.getZoom() ?: 1)
                zoomSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    private var last_haptic_time: Long = 0

                    override fun onProgressChanged(
                        seekBar: SeekBar, progress: Int, fromUser: Boolean
                    ) {
                        if (MyDebug.LOG) Log.d(TAG, "zoom onProgressChanged: $progress")
                        // note we zoom even if !fromUser, as various other UI controls (multitouch, volume key zoom, -/+ zoomcontrol)
                        // indirectly set zoom via this method, from setting the zoom slider
                        // if hasSmoothZoom()==true, then the preview already handled zooming to the current value
                        if (preview?.hasSmoothZoom() == false) {
                            val new_zoom_factor = (preview?.maxZoom ?: 1) - progress
                            if (fromUser && preview?.cameraController != null) {
                                val old_zoom_ratio = preview?.getZoomRatio()
                                val new_zoom_ratio = preview?.getZoomRatio(new_zoom_factor)
                                if (new_zoom_ratio != old_zoom_ratio) {
                                    last_haptic_time =
                                        performHapticFeedback(seekBar, last_haptic_time)
                                }
                            }
                            preview?.zoomTo(new_zoom_factor, false)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    }
                })

                if (sharedPreferences.getBoolean(
                        PreferenceKeys.ShowZoomSliderControlsPreferenceKey, true
                    )
                ) {
//                    if (mainUI?.inImmersiveMode() == false) {
//                        zoomSeekBar.visibility = View.VISIBLE
//                    }
                } else {
                    zoomSeekBar.visibility = View.INVISIBLE // should be INVISIBLE not GONE, as the focus_seekbar is aligned to be left to this; in future we might want this similarly for exposure panel
                }
            } else {
                zoomControls.visibility = View.GONE
                zoomSeekBar.visibility = View.INVISIBLE // should be INVISIBLE not GONE, as the focus_seekbar is aligned to be left to this; in future we might want this similarly for the exposure panel
            }
            if (MyDebug.LOG) Log.d(
                TAG,
                "cameraSetup: time after setting up zoom: " + (System.currentTimeMillis() - debugTime)
            )
        }
        run {
            if (MyDebug.LOG) Log.d(TAG, "set up manual focus")
            setManualFocusSeekbar(false)
            setManualFocusSeekbar(true)
        }

        if (MyDebug.LOG) Log.d(
            TAG,
            "cameraSetup: time after setting up manual focus: " + (System.currentTimeMillis() - debugTime)
        )

        run {
            if (preview?.supportsISORange() == true) {
                if (MyDebug.LOG) Log.d(TAG, "set up iso")
                val iso_seek_bar = binding.isoSeekbar
                iso_seek_bar.setOnSeekBarChangeListener(null) // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                //setProgressSeekbarExponential(iso_seek_bar, preview.getMinimumISO(), preview.getMaximumISO(), preview.cameraController.getISO());
                manualSeekbars!!.setProgressSeekbarISO(
                    iso_seek_bar,
                    preview?.getMinimumISO()?.toLong() ?: 0L,
                    preview?.getMaximumISO()?.toLong() ?: 0L,
                    preview?.cameraController?.getISO()?.toLong() ?: 0L
                )
                iso_seek_bar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    private var last_haptic_time: Long = 0

                    override fun onProgressChanged(
                        seekBar: SeekBar, progress: Int, fromUser: Boolean
                    ) {
                        if (MyDebug.LOG) Log.d(TAG, "iso seekbar onProgressChanged: $progress")/*double frac = progress/(double)iso_seek_bar.getMax();
						if( MyDebug.LOG )
							Log.d(TAG, "exposure_time frac: " + frac);
						double scaling = MainActivity.seekbarScaling(frac);
						if( MyDebug.LOG )
							Log.d(TAG, "exposure_time scaling: " + scaling);
						int min_iso = preview.getMinimumISO();
						int max_iso = preview.getMaximumISO();
						int iso = min_iso + (int)(scaling * (max_iso - min_iso));*//*int min_iso = preview.getMinimumISO();
						int max_iso = preview.getMaximumISO();
						int iso = (int)exponentialScaling(frac, min_iso, max_iso);*/
                        // n.b., important to update even if fromUser==false (e.g., so this works when user changes ISO via clicking
                        // the ISO buttons rather than moving the slider directly, see MainUI.setupExposureUI())
                        preview?.setISO(manualSeekbars!!.getISO(progress))
                        mainUI?.updateSelectedISOButton()
                        if (fromUser) {
                            last_haptic_time = performHapticFeedback(seekBar, last_haptic_time)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    }
                })
                if (preview?.supportsExposureTime() == true) {
                    if (MyDebug.LOG) Log.d(TAG, "set up exposure time")
                    val exposureTimeSeekBar = binding.exposureTimeSeekbar
                    exposureTimeSeekBar.setOnSeekBarChangeListener(null) // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
                    //setProgressSeekbarExponential(exposure_time_seek_bar, preview.getMinimumExposureTime(), preview.getMaximumExposureTime(), preview.cameraController.getExposureTime());
                    manualSeekbars!!.setProgressSeekbarShutterSpeed(
                        exposureTimeSeekBar,
                        preview?.getMinimumExposureTime() ?: 0,
                        preview?.getMaximumExposureTime() ?: 0,
                        preview?.cameraController?.getExposureTime() ?: 0
                    )
                    exposureTimeSeekBar.setOnSeekBarChangeListener(object :
                        OnSeekBarChangeListener {
                        private var last_haptic_time: Long = 0

                        override fun onProgressChanged(
                            seekBar: SeekBar, progress: Int, fromUser: Boolean
                        ) {
                            if (MyDebug.LOG) Log.d(
                                TAG, "exposure_time seekbar onProgressChanged: $progress"
                            )/*double frac = progress/(double)exposure_time_seek_bar.getMax();
							if( MyDebug.LOG )
								Log.d(TAG, "exposure_time frac: " + frac);
							long min_exposure_time = preview.getMinimumExposureTime();
							long max_exposure_time = preview.getMaximumExposureTime();
							long exposure_time = exponentialScaling(frac, min_exposure_time, max_exposure_time);*/
                            preview?.setExposureTime(manualSeekbars!!.getExposureTime(progress))
                            if (fromUser) {
                                last_haptic_time = performHapticFeedback(seekBar, last_haptic_time)
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        }

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        }
                    })
                }
            }
        }
        setManualWBSeekbar()

        if (MyDebug.LOG) Log.d(
            TAG,
            "cameraSetup: time after setting up iso: " + (System.currentTimeMillis() - debugTime)
        )

        run {
            exposure_seekbar_values = null
            if (preview?.supportsExposures() == true) {
                if (MyDebug.LOG) Log.d(TAG, "set up exposure compensation")
                val min_exposure = preview?.getMinimumExposure() ?: 0
                val exposure_seek_bar = binding.exposureSeekbar
                exposure_seek_bar.setOnSeekBarChangeListener(null) // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state

                val exposure_seekbar_n_repeated_zero =
                    3 // how many times to repeat 0 for R.id.exposure_seekbar, so that it "sticks" to zero when changing seekbar

                //exposure_seek_bar.setMax( preview.getMaximumExposure() - min_exposure + exposure_seekbar_n_repeated_zero-1 );
                //exposure_seek_bar.setProgress( preview.getCurrentExposure() - min_exposure );
                exposure_seekbar_values = ArrayList<Int?>()
                val current_exposure = preview?.getCurrentExposure()
                var current_progress = 0
                for (i in min_exposure..(preview?.getMaximumExposure() ?: 0)) {
                    exposure_seekbar_values!!.add(i)
                    if (i == 0) {
                        this.exposureSeekbarProgressZero = exposure_seekbar_values!!.size - 1
                        this.exposureSeekbarProgressZero += (exposure_seekbar_n_repeated_zero - 1) / 2 // centre within the region of zeroes
                        for (j in 0..<exposure_seekbar_n_repeated_zero - 1) {
                            exposure_seekbar_values!!.add(i)
                        }
                    }
                    if (i == current_exposure) {
                        if (i == 0) {
                            current_progress += this.exposureSeekbarProgressZero
                        } else {
                            current_progress = exposure_seekbar_values!!.size - 1
                        }
                    }
                }
                exposure_seek_bar.max = exposure_seekbar_values!!.size - 1
                exposure_seek_bar.progress = current_progress
                exposure_seek_bar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    private var last_haptic_time: Long = 0

                    override fun onProgressChanged(
                        seekBar: SeekBar, progress: Int, fromUser: Boolean
                    ) {
                        if (MyDebug.LOG) Log.d(
                            TAG, "exposure seekbar onProgressChanged: $progress"
                        )
                        if (exposure_seekbar_values == null) {
                            Log.e(TAG, "exposure_seekbar_values is null")
                            return
                        }
                        val new_exposure = getExposureSeekbarValue(progress)
                        if (fromUser) {
                            // check if not scrolling past the repeated zeroes
                            if (preview?.getCurrentExposure() != new_exposure) {
                                last_haptic_time = performHapticFeedback(seekBar, last_haptic_time)
                            }
                        }
                        preview?.setExposure(new_exposure)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    }
                })

                val seek_bar_zoom = binding.exposureSeekbarZoom
                seek_bar_zoom.setOnZoomInClickListener { changeExposure(1) }
                seek_bar_zoom.setOnZoomOutClickListener { changeExposure(-1) }
            }
        }

        if (MyDebug.LOG) Log.d(
            TAG,
            "cameraSetup: time after setting up exposure: " + (System.currentTimeMillis() - debugTime)
        )

        if (checkDisableGUIIcons()) {
            if (MyDebug.LOG) Log.d(TAG, "cameraSetup: need to layoutUI as we hid some icons")
        }

        // need to update some icons, e.g., white balance and exposure lock due to them being turned off when pause/resuming
        mainUI?.updateOnScreenIcons()

        mainUI?.setTakePhotoIcon()
        mainUI?.setSwitchCameraContentDescription()

        if (!block_startup_toast) {
            this.showPhotoVideoToast(false)
        }
        block_startup_toast = false

        this.applicationInterface!!.drawPreview?.setDimPreview(false)
    }

    fun setManualFocusSeekbarProgress(isTargetDistance: Boolean, focusDistance: Float) {
        val focusSeekBar =
            if (isTargetDistance) binding.focusBracketingTargetSeekbar else binding.focusSeekbar
        ManualSeekbars.setProgressSeekbarScaled(
            focusSeekBar,
            0.0,
            preview?.minimumFocusDistance?.toDouble() ?: 0.0,
            focusDistance.toDouble()
        )
    }

    private fun setManualFocusSeekbar(isTargetDistance: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "setManualFocusSeekbar")
        val focusSeekBar =
            if (isTargetDistance) binding.focusBracketingTargetSeekbar else binding.focusSeekbar
        focusSeekBar.setOnSeekBarChangeListener(null) // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state

        preview?.cameraController?.let {
            setManualFocusSeekbarProgress(
                isTargetDistance, if (isTargetDistance) {
                    it.getFocusBracketingTargetDistance()
                } else {
                    it.getFocusDistance()
                }
            )
        }

        focusSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            private var has_saved_zoom = false
            private var saved_zoom_factor = 0

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!isTargetDistance && applicationInterface!!.isFocusBracketingSourceAutoPref()) {
                    // source is set from continuous focus, not by changing the seekbar
                    if (fromUser) {
                        // but if user has manually changed, then exit auto mode
                        applicationInterface!!.setFocusBracketingSourceAutoPref(false)
                    } else {
                        return
                    }
                }
                val frac = progress / focusSeekBar.max.toDouble()
                val scaling = ManualSeekbars.seekbarScaling(frac)
                val focusDistance = (scaling * (preview?.minimumFocusDistance ?: 0f)).toFloat()
                preview?.setFocusDistance(focusDistance, isTargetDistance, true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (MyDebug.LOG) Log.d(TAG, "manual focus seekbar: onStartTrackingTouch")
                has_saved_zoom = false
                if (preview?.supportsZoom() == true) {
                    val focusAssist = applicationInterface!!.focusAssistPref
                    if (focusAssist > 0 && preview?.cameraController != null) {
                        has_saved_zoom = true
                        saved_zoom_factor = preview?.cameraController?.getZoom() ?: 0
                        if (MyDebug.LOG) Log.d(
                            TAG,
                            "zoom by " + focusAssist + " for focus assist, zoom factor was: " + saved_zoom_factor
                        )
                        val newZoomFactor = preview?.getScaledZoomFactor(focusAssist.toFloat()) ?: 1
                        preview?.cameraController?.setZoom(newZoomFactor)
                    }
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (MyDebug.LOG) Log.d(TAG, "manual focus seekbar: onStopTrackingTouch")
                if (has_saved_zoom && preview?.cameraController != null) {
                    if (MyDebug.LOG) Log.d(
                        TAG, "unzoom for focus assist, zoom factor was: $saved_zoom_factor"
                    )
                    preview?.cameraController?.setZoom(saved_zoom_factor)
                }
                preview?.stoppedSettingFocusDistance(isTargetDistance)
            }
        })
        setManualFocusSeekBarVisibility(isTargetDistance)
    }

    fun showManualFocusSeekbar(isTargetDistance: Boolean): Boolean {
        if ((applicationInterface!!.photoMode == PhotoMode.FocusBracketing) && preview?.isVideo == false) {
            return true // both seekbars shown in focus bracketing mode
        }
        if (isTargetDistance) {
            return false // target seekbar only shown in focus bracketing mode
        }
        val isVisible =
            preview?.getCurrentFocusValue() != null && this.preview?.getCurrentFocusValue() == "focus_mode_manual2"
        return isVisible
    }

    fun setManualFocusSeekBarVisibility(isTargetDistance: Boolean) {
        val isVisible = showManualFocusSeekbar(isTargetDistance)
        val focusSeekBar =
            if (isTargetDistance) binding.layoutFocusBracketingTargetSeekbar else binding.layoutFocusSeekbar
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        focusSeekBar.visibility = visibility

        if (isVisible) {
            applicationInterface!!.drawPreview?.updateSettings() // needed so that we reset focus_seekbars_margin_left, as the focus seekbars can only be updated when visible
        }
    }

    fun setManualWBSeekbar() {
        if (MyDebug.LOG) Log.d(TAG, "setManualWBSeekbar")
        if (preview?.getSupportedWhiteBalances() != null && preview?.supportsWhiteBalanceTemperature() == true) {
            if (MyDebug.LOG) Log.d(TAG, "set up manual white balance")
            val white_balance_seek_bar = binding.whiteBalanceSeekbar
            white_balance_seek_bar.setOnSeekBarChangeListener(null) // clear an existing listener - don't want to call the listener when setting up the progress bar to match the existing state
            val minimum_temperature = preview?.getMinimumWhiteBalanceTemperature()
            val maximum_temperature = preview?.getMaximumWhiteBalanceTemperature()

            manualSeekbars!!.setProgressSeekbarWhiteBalance(
                white_balance_seek_bar,
                minimum_temperature?.toLong() ?: 0L,
                maximum_temperature?.toLong() ?: 0L,
                preview?.cameraController?.getWhiteBalanceTemperature()?.toLong() ?: 0L
            )
            white_balance_seek_bar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                private var last_haptic_time: Long = 0

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (MyDebug.LOG) Log.d(
                        TAG, "white balance seekbar onProgressChanged: $progress"
                    )
                    preview?.setWhiteBalanceTemperature(
                        manualSeekbars!!.getWhiteBalanceTemperature(
                            progress
                        )
                    )
                    if (fromUser) {
                        last_haptic_time = performHapticFeedback(seekBar, last_haptic_time)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }
            })
        }
    }

    fun supportsAutoStabilise(): Boolean {
        if (applicationInterface?.isRawOnly == true) return false // if not saving JPEGs, no point having auto-stabilise mode, as it won't affect the RAW images

        if (applicationInterface?.photoMode == PhotoMode.Panorama) return false // not supported in panorama mode

        return this.supports_auto_stabilise
    }

    /** Returns whether the device supports auto-level at all. Most callers probably want to use
     * supportsAutoStabilise() which also checks whether auto-level is allowed with current options.
     */
    fun deviceSupportsAutoStabilise(): Boolean {
        return this.supports_auto_stabilise
    }

    fun supportsDRO(): Boolean {
        if (applicationInterface!!.isRawOnly(PhotoMode.DRO)) return false // if not saving JPEGs, no point having DRO mode, as it won't affect the RAW images

        // require at least Android 5, for the Renderscript support in HDRProcessor
        return true
    }

    fun supportsHDR(): Boolean {
        // we also require the device have sufficient memory to do the processing
        // also require at least Android 5, for the Renderscript support in HDRProcessor
        return large_heap_memory >= 128 && preview?.supportsExpoBracketing() == true
    }

    fun supportsExpoBracketing(): Boolean {
        if (applicationInterface!!.isImageCaptureIntent) return false // don't support expo bracketing mode if called from image capture intent

        return preview?.supportsExpoBracketing() == true
    }

    fun supportsFocusBracketing(): Boolean {
        if (applicationInterface!!.isImageCaptureIntent) return false // don't support focus bracketing mode if called from image capture intent

        return preview?.supportsFocusBracketing() == true
    }

    /** Whether we support the auto mode for setting source focus distance for focus bracketing mode.
     * Note the caller should still separately call supportsFocusBracketing() to see if focus
     * bracketing is supported in the first place.
     */
    fun supportsFocusBracketingSourceAuto(): Boolean {
        return preview?.supportsFocus() == true && preview?.supportedFocusValues?.contains("focus_mode_continuous_picture") == true
    }

    fun supportsPanorama(): Boolean {
        // don't support panorama mode if called from image capture intent
        // in theory this works, but problem that currently we'd end up doing the processing on the UI thread, so risk ANR
        if (applicationInterface!!.isImageCaptureIntent) return false
        // require 256MB just to be safe, due to the large number of images that may be created
        // also require at least Android 5, for Renderscript
        // remember to update the FAQ "Why isn't Panorama supported on my device?" if this changes
        return large_heap_memory >= 256 && applicationInterface!!.gyroSensor.hasSensors()
        //return false; // currently blocked for release
    }

    fun supportsFastBurst(): Boolean {
        if (applicationInterface!!.isImageCaptureIntent) return false // don't support burst mode if called from image capture intent

        // require 512MB just to be safe, due to the large number of images that may be created
        return (preview?.usingCamera2API() == true && large_heap_memory >= 512 && preview?.supportsBurst() == true)
    }

    fun supportsNoiseReduction(): Boolean {
        // require at least Android 5, for the Renderscript support in HDRProcessor, but we require
        // Android 7 to limit to more modern devices (for performance reasons)
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && preview?.usingCamera2API() == true && large_heap_memory >= 512 && preview?.supportsBurst() == true && preview?.supportsExposureTime() == true)
        //return false; // currently blocked for release
    }

    /** Whether the Camera vendor extension is supported (see
     * https://developer.android.com/reference/android/hardware/camera2/CameraExtensionCharacteristics ).
     */
    fun supportsCameraExtension(extension: Int): Boolean {
        return preview?.supportsCameraExtension(extension) == true
    }

    /** Whether RAW mode would be supported for various burst modes (expo bracketing etc).
     * Note that caller should still separately check preview.supportsRaw() if required.
     */
    fun supportsBurstRaw(): Boolean {
        return (large_heap_memory >= 512)
    }

    fun supportsOptimiseFocusLatency(): Boolean {
        // whether to support optimising focus for latency
        // in theory this works on any device, as well as old or Camera2 API, but restricting this for now to avoid risk of poor default behaviour
        // on older devices
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && preview?.usingCamera2API() == true)
    }

    fun supportsPreviewBitmaps(): Boolean {
        // In practice we only use TextureView on Android 5+ (with Camera2 API enabled) anyway, but have put an explicit check here -
        // even if in future we allow TextureView pre-Android 5, we still need Android 5+ for Renderscript.
        return preview?.getView() is TextureView && large_heap_memory >= 128
    }

    fun supportsPreShots(): Boolean {
        // Need at least Android 5+ for TextureView
        // Need at least Android 8+ for video encoding classes
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && preview?.view is TextureView && large_heap_memory >= 512
    }

    private fun maxExpoBracketingNImages(): Int {
        return preview?.maxExpoBracketingNImages() ?: 0
    }

    fun supportsForceVideo4K(): Boolean {
        return this.supports_force_video_4k
    }

    fun supportsCamera2(): Boolean {
        return this.supports_camera2
    }

    private fun disableForceVideo4K() {
        this.supports_force_video_4k = false
    }

    fun getBluetoothRemoteControl(): BluetoothRemoteControl {
        return bluetoothRemoteControl!!
    }

    fun getPermissionHandler(): PermissionHandler {
        return permissionHandler!!
    }

    fun getSettingsManager(): SettingsManager {
        return settingsManager!!
    }

    fun getManualSeekbars(): ManualSeekbars {
        return this.manualSeekbars!!
    }

    fun getSoundPoolManager(): SoundPoolManager {
        return this.soundPoolManager!!
    }

    val locationSupplier: LocationSupplier?
        get() = this.applicationInterface!!.locationSupplier

    val storageUtils: StorageUtils?
        get() = this.applicationInterface!!.storageUtils

    val imageFolder: File?
        get() = this.applicationInterface!!.storageUtils.getImageFolder()

    private fun getPhotoModeString(photo_mode: PhotoMode, string_for_std: Boolean): String? {
        var photo_mode_string: String? = null
        when (photo_mode) {
            PhotoMode.Standard -> if (string_for_std) photo_mode_string =
                getResources().getString(R.string.photo_mode_standard_full)

            PhotoMode.DRO -> photo_mode_string = getResources().getString(R.string.photo_mode_dro)
            PhotoMode.HDR -> photo_mode_string = getResources().getString(R.string.photo_mode_hdr)
            PhotoMode.ExpoBracketing -> photo_mode_string =
                getResources().getString(R.string.photo_mode_expo_bracketing_full)

            PhotoMode.FocusBracketing -> {
                photo_mode_string =
                    getResources().getString(R.string.photo_mode_focus_bracketing_full)
                val n_images = applicationInterface!!.getFocusBracketingNImagesPref()
                photo_mode_string += " (" + n_images + ")"
            }

            PhotoMode.FastBurst -> {
                photo_mode_string = getResources().getString(R.string.photo_mode_fast_burst_full)
                val n_images = applicationInterface!!.getBurstNImages()
                photo_mode_string += " (" + n_images + ")"
            }

            PhotoMode.NoiseReduction -> photo_mode_string =
                getResources().getString(R.string.photo_mode_noise_reduction_full)

            PhotoMode.Panorama -> photo_mode_string =
                getResources().getString(R.string.photo_mode_panorama_full)

            PhotoMode.X_Auto -> photo_mode_string =
                getResources().getString(R.string.photo_mode_x_auto_full)

            PhotoMode.X_HDR -> photo_mode_string =
                getResources().getString(R.string.photo_mode_x_hdr_full)

            PhotoMode.X_Night -> photo_mode_string =
                getResources().getString(R.string.photo_mode_x_night_full)

            PhotoMode.X_Bokeh -> photo_mode_string =
                getResources().getString(R.string.photo_mode_x_bokeh_full)

            PhotoMode.X_Beauty -> photo_mode_string =
                getResources().getString(R.string.photo_mode_x_beauty_full)
        }
        return photo_mode_string
    }

    /** Displays a toast with information about the current preferences.
     * If always_show is true, the toast is always displayed; otherwise, we only display
     * a toast if it's important to notify the user (i.e., unusual non-default settings are
     * set). We want a balance between not pestering the user too much, whilst also reminding
     * them if certain settings are on.
     */
    private fun showPhotoVideoToast(always_show: Boolean) {
        if (MyDebug.LOG) {
            Log.d(TAG, "showPhotoVideoToast")
            Log.d(TAG, "always_show? " + always_show)
        }
        val camera_controller = preview?.cameraController
        if (camera_controller == null || this.isCameraInBackground) {
            if (MyDebug.LOG) Log.d(TAG, "camera not open or in background")
            return
        }
        var toast_string: String = ""
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        var simple = true
        val video_high_speed = preview?.isVideoHighSpeed()
        val photo_mode = applicationInterface!!.photoMode

        if (preview?.isVideo() == true) {
            val profile = preview?.getVideoProfile()
            profile?.let {
                val extension_string = profile.fileExtension
                if (profile.fileExtension != "mp4") {
                    simple = false
                }

                val bitrate_string: String?
                if (profile.videoBitRate >= 10000000) bitrate_string =
                    (profile.videoBitRate / 1000000).toString() + "Mbps"
                else if (profile.videoBitRate >= 10000) bitrate_string =
                    (profile.videoBitRate / 1000).toString() + "Kbps"
                else bitrate_string = profile.videoBitRate.toString() + "bps"
                val bitrate_value = applicationInterface!!.videoBitratePref
                if (bitrate_value != "default") {
                    simple = false
                }

                val capture_rate = profile.videoCaptureRate
                val capture_rate_string =
                    if (capture_rate < 9.5f) DecimalFormat("#0.###").format(capture_rate) else (profile.videoCaptureRate + 0.5).toInt()
                        .toString()
                toast_string =
                    getResources().getString(R.string.video) + ": " + profile.videoFrameWidth + "x" + profile.videoFrameHeight + "\n" + capture_rate_string + getResources().getString(
                        R.string.fps
                    ) + (if (video_high_speed == true) " [" + getResources().getString(
                        R.string.high_speed
                    ) + "]" else "") + ", " + bitrate_string + " (" + extension_string + ")"

                val fps_value = applicationInterface!!.getVideoFPSPref()
                if (fps_value != "default" || video_high_speed == true) {
                    simple = false
                }

                val capture_rate_factor = applicationInterface!!.getVideoCaptureRateFactor()
                if (abs(capture_rate_factor - 1.0f) > 1.0e-5) {
                    toast_string += "\n" + getResources().getString(R.string.preference_video_capture_rate) + ": " + capture_rate_factor + "x"
                    simple = false
                }
            }

            run {
                val tonemap_profile = applicationInterface!!.getVideoTonemapProfile()
                if (tonemap_profile != TonemapProfile.TONEMAPPROFILE_OFF && preview?.supportsTonemapCurve() == true) {
                    if (applicationInterface!!.getVideoTonemapProfile() != TonemapProfile.TONEMAPPROFILE_OFF && preview?.supportsTonemapCurve() == true) {
                        var string_id = 0
                        when (tonemap_profile) {
                            TonemapProfile.TONEMAPPROFILE_REC709 -> string_id =
                                R.string.preference_video_rec709

                            TonemapProfile.TONEMAPPROFILE_SRGB -> string_id =
                                R.string.preference_video_srgb

                            TonemapProfile.TONEMAPPROFILE_LOG -> string_id = R.string.video_log
                            TonemapProfile.TONEMAPPROFILE_GAMMA -> string_id =
                                R.string.preference_video_gamma

                            TonemapProfile.TONEMAPPROFILE_JTVIDEO -> string_id =
                                R.string.preference_video_jtvideo

                            TonemapProfile.TONEMAPPROFILE_JTLOG -> string_id =
                                R.string.preference_video_jtlog

                            TonemapProfile.TONEMAPPROFILE_JTLOG2 -> string_id =
                                R.string.preference_video_jtlog2

                            TonemapProfile.TONEMAPPROFILE_OFF -> {

                            }
                        }
                        if (string_id != 0) {
                            simple = false
                            toast_string += "\n" + getResources().getString(string_id)
                            if (tonemap_profile == TonemapProfile.TONEMAPPROFILE_GAMMA) {
                                toast_string += " " + applicationInterface!!.getVideoProfileGamma()
                            }
                        } else {
                            Log.e(TAG, "unknown tonemap_profile: " + tonemap_profile)
                        }
                    }
                }
            }

            val record_audio = applicationInterface!!.getRecordAudioPref()
            if (!record_audio) {
                toast_string += "\n" + getResources().getString(R.string.audio_disabled)
                simple = false
            }
            val max_duration_value: String =
                sharedPreferences.getString(PreferenceKeys.VideoMaxDurationPreferenceKey, "0")!!
            if (max_duration_value.length > 0 && max_duration_value != "0") {
                val entries_array =
                    getResources().getStringArray(R.array.preference_video_max_duration_entries)
                val values_array =
                    getResources().getStringArray(R.array.preference_video_max_duration_values)
                val index = Arrays.asList<String?>(*values_array).indexOf(max_duration_value)
                if (index != -1) { // just in case!
                    val entry = entries_array[index]
                    toast_string += "\n" + getResources().getString(R.string.max_duration) + ": " + entry
                    simple = false
                }
            }
            val max_filesize = applicationInterface!!.videoMaxFileSizeUserPref
            if (max_filesize != 0L) {
                toast_string += "\n" + getResources().getString(R.string.max_filesize) + ": "
                if (max_filesize >= 1024 * 1024 * 1024) {
                    val max_filesize_gb = max_filesize / (1024 * 1024 * 1024)
                    toast_string += max_filesize_gb.toString() + getResources().getString(R.string.gb_abbreviation)
                } else {
                    val max_filesize_mb = max_filesize / (1024 * 1024)
                    toast_string += max_filesize_mb.toString() + getResources().getString(R.string.mb_abbreviation)
                }
                simple = false
            }
            if (applicationInterface!!.videoFlashPref && preview?.supportsFlash() == true) {
                toast_string += "\n" + getResources().getString(R.string.preference_video_flash)
                simple = false
            }
        } else {
            if (photo_mode == PhotoMode.Panorama) {
                // don't show resolution in panorama mode
                toast_string = ""
            } else {
                toast_string = getResources().getString(R.string.photo)
                val current_size = preview?.getCurrentPictureSize()
                current_size?.let {
                    toast_string += " " + current_size.width + "x" + current_size.height
                }
            }

            val photo_mode_string = getPhotoModeString(photo_mode, false)
            if (photo_mode_string != null) {
                toast_string += (if (toast_string.length == 0) "" else "\n") + getResources().getString(
                    R.string.photo_mode
                ) + ": " + photo_mode_string
                if (photo_mode != PhotoMode.DRO && photo_mode != PhotoMode.HDR && photo_mode != PhotoMode.NoiseReduction) simple =
                    false
            }

            if (preview?.supportsFocus() == true && (preview?.supportedFocusValues?.size ?: 0 > 1) && photo_mode != PhotoMode.FocusBracketing) {
                val focus_value = preview?.getCurrentFocusValue()
                if (focus_value != null && (focus_value != "focus_mode_auto") && (focus_value != "focus_mode_continuous_picture")) {
                    val focus_entry = preview?.findFocusEntryForValue(focus_value)
                    if (focus_entry != null) {
                        toast_string += "\n" + focus_entry
                    }
                }
            }

            if (applicationInterface!!.autoStabilisePref) {
                // important as users are sometimes confused at the behaviour if they don't realise the option is on
                toast_string += (if (toast_string.length == 0) "" else "\n") + getResources().getString(
                    R.string.preference_auto_stabilise
                )
                simple = false
            }
        }
        if (applicationInterface!!.getFaceDetectionPref()) {
            // important so that the user realises why touching for focus/metering areas won't work - easy to forget that face detection has been turned on!
            toast_string += "\n" + getResources().getString(R.string.preference_face_detection)
            simple = false
        }
        if (video_high_speed == false) {
            //manual ISO only supported for high speed video
            val iso_value = applicationInterface!!.getISOPref()
            if (iso_value != CameraController.ISO_DEFAULT) {
                toast_string += "\nISO: " + iso_value
                if (preview?.supportsExposureTime() == true) {
                    val exposure_time_value = applicationInterface!!.getExposureTimePref()
                    toast_string += " " + preview?.getExposureTimeString(exposure_time_value)
                }
                simple = false
            }
            val current_exposure = camera_controller.getExposureCompensation()
            if (current_exposure != 0) {
                toast_string += "\n" + preview?.getExposureCompensationString(current_exposure)
                simple = false
            }
        }
        try {
            val scene_mode = camera_controller.getSceneMode()
            val white_balance = camera_controller.getWhiteBalance()
            val color_effect = camera_controller.getColorEffect()
            if (scene_mode != null && scene_mode != CameraController.SCENE_MODE_DEFAULT) {
                toast_string += "\n" + getResources().getString(R.string.scene_mode) + ": " + mainUI?.getEntryForSceneMode(
                    scene_mode
                )
                simple = false
            }
            if (white_balance != null && white_balance != CameraController.WHITE_BALANCE_DEFAULT) {
                toast_string += "\n" + getResources().getString(R.string.white_balance) + ": " + mainUI?.getEntryForWhiteBalance(
                    white_balance
                )
                if (white_balance == "manual" && preview?.supportsWhiteBalanceTemperature() == true) {
                    toast_string += " " + camera_controller.getWhiteBalanceTemperature()
                }
                simple = false
            }
            if (color_effect != null && color_effect != CameraController.COLOR_EFFECT_DEFAULT) {
                toast_string += "\n" + getResources().getString(R.string.color_effect) + ": " + mainUI?.getEntryForColorEffect(
                    color_effect
                )
                simple = false
            }
        } catch (e: RuntimeException) {
            // catch runtime error from camera_controller old API from camera.getParameters()
            e.printStackTrace()
        }
        val lock_orientation = applicationInterface!!.getLockOrientationPref()
        if (lock_orientation != "none" && photo_mode != PhotoMode.Panorama) {
            // panorama locks to portrait, but don't want to display that in the toast
            val entries_array =
                getResources().getStringArray(R.array.preference_lock_orientation_entries)
            val values_array =
                getResources().getStringArray(R.array.preference_lock_orientation_values)
            val index = Arrays.asList<String?>(*values_array).indexOf(lock_orientation)
            if (index != -1) { // just in case!
                val entry = entries_array[index]
                toast_string += "\n" + entry
                simple = false
            }
        }
        val timer: String = sharedPreferences.getString(PreferenceKeys.TimerPreferenceKey, "0")!!
        if (timer != "0" && photo_mode != PhotoMode.Panorama) {
            val entries_array = getResources().getStringArray(R.array.preference_timer_entries)
            val values_array = getResources().getStringArray(R.array.preference_timer_values)
            val index = Arrays.asList<String?>(*values_array).indexOf(timer)
            if (index != -1) { // just in case!
                val entry = entries_array[index]
                toast_string += "\n" + getResources().getString(R.string.preference_timer) + ": " + entry
                simple = false
            }
        }
        val repeat = applicationInterface!!.getRepeatPref()
        if (repeat != "1") {
            val entries_array = getResources().getStringArray(R.array.preference_burst_mode_entries)
            val values_array = getResources().getStringArray(R.array.preference_burst_mode_values)
            val index = Arrays.asList<String?>(*values_array).indexOf(repeat)
            if (index != -1) { // just in case!
                val entry = entries_array[index]
                toast_string += "\n" + getResources().getString(R.string.preference_burst_mode) + ": " + entry
                simple = false
            }
        }

        /*if( audio_listener != null ) {
			toast_string += "\n" + getResources().getString(R.string.preference_audio_noise_control);
		}*/
        if (MyDebug.LOG) {
            Log.d(TAG, "toast_string: " + toast_string)
            Log.d(TAG, "simple?: " + simple)
            Log.d(TAG, "push_info_toast_text: " + push_info_toast_text)
        }
        val use_fake_toast = true
        if (!simple || always_show) {
            if (push_info_toast_text != null) {
                toast_string = push_info_toast_text + "\n" + toast_string
            }
            preview?.showToast(switch_video_toast, toast_string, use_fake_toast)
        } else if (push_info_toast_text != null) {
            preview?.showToast(switch_video_toast, push_info_toast_text, use_fake_toast)
        }
        push_info_toast_text = null // reset
    }

    private fun freeAudioListener(wait_until_done: Boolean) {
        if (MyDebug.LOG) Log.d(TAG, "freeAudioListener")
        if (audio_listener != null) {
            audio_listener!!.release(wait_until_done)
            audio_listener = null
        }
        mainUI?.audioControlStopped()
    }

    private fun startAudioListener() {
        if (MyDebug.LOG) Log.d(TAG, "startAudioListener")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // we restrict the checks to Android 6 or later just in case, see note in LocationSupplier.setupLocationListener()
            if (MyDebug.LOG) Log.d(TAG, "check for record audio permission")
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (MyDebug.LOG) Log.d(TAG, "record audio permission not available")
                applicationInterface!!.requestRecordAudioPermission()
                return
            }
        }

        val callback = MyAudioTriggerListenerCallback(this)
        audio_listener = AudioListener(callback)
        if (audio_listener!!.status()) {
            preview?.showToast(this.audioControlToast, R.string.audio_listener_started, true)

            audio_listener!!.start()
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val sensitivity_pref: String = sharedPreferences.getString(
                PreferenceKeys.AudioNoiseControlSensitivityPreferenceKey, "0"
            )!!
            val audio_noise_sensitivity: Int
            when (sensitivity_pref) {
                "3" -> audio_noise_sensitivity = 50
                "2" -> audio_noise_sensitivity = 75
                "1" -> audio_noise_sensitivity = 125
                "-1" -> audio_noise_sensitivity = 150
                "-2" -> audio_noise_sensitivity = 200
                "-3" -> audio_noise_sensitivity = 400
                else ->                     // default
                    audio_noise_sensitivity = 100
            }
            callback.setAudioNoiseSensitivity(audio_noise_sensitivity)
            mainUI?.audioControlStarted()
        } else {
            audio_listener!!.release(true) // shouldn't be needed, but just to be safe
            audio_listener = null
            preview?.showToast(null, R.string.audio_listener_failed)
        }
    }

    fun hasAudioControl(): Boolean {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val audio_control: String =
            sharedPreferences.getString(PreferenceKeys.AudioControlPreferenceKey, "none")!!/*if( audio_control.equals("voice") ) {
            return speechControl.hasSpeechRecognition();
        }
        else*/
        if (audio_control == "noise") {
            return true
        }
        return false
    }

    /*void startAudioListeners() {
		initAudioListener();
		// no need to restart speech recognizer, as we didn't free it in stopAudioListeners(), and it's controlled by a user button
	}*/
    fun stopAudioListeners() {
        freeAudioListener(true)/*if( speechControl.hasSpeechRecognition() ) {
            // no need to free the speech recognizer, just stop it
            speechControl.stopListening();
        }*/
    }

    fun initLocation() {
        if (MyDebug.LOG) Log.d(TAG, "initLocation")
        if (this.isAppPaused) {
            if (MyDebug.LOG) Log.d(TAG, "initLocation: app is paused!")
            // we shouldn't need this (as we only call initLocation() when active), but just in case we end up here after onPause...
            // in fact this happens when we need to grant permission for location - the call to initLocation() from
            // MainActivity.onRequestPermissionsResult()->PermissionsHandler.onRequestPermissionsResult() will be when the application
            // is still paused - so we won't do anything here, but instead initLocation() will be called after when resuming.
        } else if (this.isCameraInBackground) {
            if (MyDebug.LOG) Log.d(TAG, "initLocation: camera in background!")
            // we will end up here if app is pause/resumed when camera in background (settings, dialog, etc)
        } else if (!applicationInterface!!.locationSupplier.setupLocationListener()) {
            if (MyDebug.LOG) Log.d(TAG, "location permission not available, so request permission")
            permissionHandler!!.requestLocationPermission()
        }
    }

    private fun initGyroSensors() {
        if (MyDebug.LOG) Log.d(TAG, "initGyroSensors")
        if (applicationInterface!!.photoMode == PhotoMode.Panorama) {
            applicationInterface!!.gyroSensor.enableSensors()
        } else {
            applicationInterface!!.gyroSensor.disableSensors()
        }
    }

    fun speak(text: String?) {
        if (textToSpeech != null && textToSpeechSuccess) {
            textToSpeech!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray, deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        if (MyDebug.LOG) Log.d(TAG, "onRequestPermissionsResult: requestCode " + requestCode)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHandler!!.onRequestPermissionsResult(requestCode, grantResults)
    }

    fun restartOpenCamera() {
        if (MyDebug.LOG) Log.d(TAG, "restartOpenCamera")
        this.waitUntilImageQueueEmpty()
        // see http://stackoverflow.com/questions/2470870/force-application-to-restart-on-first-activity
        val intent =
            this.baseContext.packageManager.getLaunchIntentForPackage(this.baseContext.packageName)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        this.startActivity(intent)
    }

    fun takePhotoButtonLongClickCancelled() {
        if (MyDebug.LOG) Log.d(TAG, "takePhotoButtonLongClickCancelled")
        if (preview?.cameraController != null && preview?.cameraController?.isContinuousBurstInProgress() == true) {
            preview?.cameraController?.stopContinuousBurst()
        }
    }

    val saveLocationHistory: SaveLocationHistory
        // for testing:
        get() = this.save_location_history!!

    fun usedFolderPicker() {
        if (applicationInterface?.storageUtils?.isUsingSAF() == true) {
            saveLocationHistorySAF!!.updateFolderHistory(
                this.storageUtils!!.getSaveLocationSAF(), true
            )
        } else {
            save_location_history!!.updateFolderHistory(this.storageUtils!!.getSaveLocation(), true)
        }
    }

    fun hasThumbnailAnimation(): Boolean {
        return this.applicationInterface!!.hasThumbnailAnimation()
    }

    companion object {
        private const val TAG = "MainActivity"

        private var activity_count = 0

        @JvmField
        @Volatile
        var test_preview_want_no_limits: Boolean =
            false // test flag, if set to true then instead use test_preview_want_no_limits_value; needs to be static, as it needs to be set before activity is created to take effect

        @JvmField
        @Volatile
        var test_preview_want_no_limits_value: Boolean = false

        @JvmField
        @Volatile
        var test_force_system_orientation: Boolean =
            false // test flag, if set to true, that getSystemOrientation() returns test_system_orientation

        @JvmField
        @Volatile
        var test_system_orientation: SystemOrientation = SystemOrientation.PORTRAIT

        @JvmField
        @Volatile
        var test_force_window_insets: Boolean =
            false // test flag, if set to true, then the OnApplyWindowInsetsListener will read from the following flags

        @JvmField
        @Volatile
        var test_insets: Insets? =
            null // test insets for WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout()

        @JvmField
        @Volatile
        var test_cutout_insets: Insets? = null // test insets for WindowInsets.Type.displayCutout()

        // application shortcuts:
        private const val ACTION_SHORTCUT_CAMERA = "com.ssolstice.camera.manual.SHORTCUT_CAMERA"
        private const val ACTION_SHORTCUT_SELFIE = "com.ssolstice.camera.manual.SHORTCUT_SELFIE"
        private const val ACTION_SHORTCUT_VIDEO = "com.ssolstice.camera.manual.SHORTCUT_VIDEO"
        private const val ACTION_SHORTCUT_GALLERY = "com.ssolstice.camera.manual.SHORTCUT_GALLERY"
        private const val ACTION_SHORTCUT_SETTINGS = "com.ssolstice.camera.manual.SHORTCUT_SETTINGS"

        private const val CHOOSE_SAVE_FOLDER_SAF_CODE = 42
        private const val CHOOSE_GHOST_IMAGE_SAF_CODE = 43
        private const val CHOOSE_LOAD_SETTINGS_SAF_CODE = 44

        @JvmField
        var test_force_supports_camera2: Boolean =
            false // okay to be static, as this is set for an entire test suite

        // update: notifications now removed due to needing permissions on Android 13+
        //private boolean has_notification;
        //private final String CHANNEL_ID = "open_camera_channel";
        //private final int image_saving_notification_id = 1;
        private const val WATER_DENSITY_FRESHWATER = 1.0f
        private const val WATER_DENSITY_SALTWATER = 1.03f

        // whether to lock to landscape orientation, or allow switching between portrait and landscape orientations
        //public static final boolean lock_to_landscape = true;
        const val lockToLandscape: Boolean = false

        /** Whether to use codepaths that are compatible with scoped storage.
         */
        @JvmStatic
        fun useScopedStorage(): Boolean {
            //return false;
            //return true;
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }

        /** Checks to see if the supplied folder (in the format as used by our preferences) is supported
         * with scoped storage.
         * @return The Boolean is always non-null, and returns whether the save location is valid.
         * If the return is false, then if the String is non-null, this stores an alternative
         * form that is valid. If null, there is no valid alternative.
         * @param base_folder This should normally be null, but can be used to specify manually the
         * folder instead of using StorageUtils.getBaseFolder() - needed for unit
         * tests as Environment class (for Environment.getExternalStoragePublicDirectory())
         * is not mocked.
         */
        @JvmStatic
        @JvmOverloads
        fun checkSaveLocation(
            folder: String, base_folder: String? = null
        ): CheckSaveLocationResult {/*if( MyDebug.LOG )
            Log.d(TAG, "DCIM path: " + Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath());*/
            var base_folder = base_folder
            if (StorageUtils.saveFolderIsFull(folder)) {
                if (MyDebug.LOG) Log.d(TAG, "checkSaveLocation for full path: " + folder)

                // But still check to see if the full path is part of DCIM. Since when using the
                // file dialog method with non-scoped storage, if the user specifies multiple subfolders
                // e.g. DCIM/blah_a/blah_b, we don't spot that in FolderChooserDialog.useFolder(), and
                // instead still store that as the full path.
                if (base_folder == null) base_folder =
                    StorageUtils.getBaseFolder().getAbsolutePath()
                // strip '/' as last character - makes it easier to also spot cases where the folder is the
                // DCIM folder, but doesn't have a '/' last character
                if (base_folder.length >= 1 && base_folder.get(base_folder.length - 1) == '/') base_folder =
                    base_folder.substring(0, base_folder.length - 1)
                if (MyDebug.LOG) Log.d(TAG, "    compare to base_folder: " + base_folder)
                var alt_folder: String? = null
                if (folder.startsWith(base_folder)) {
                    alt_folder = folder.substring(base_folder.length)
                    // also need to strip the first '/' if it exists
                    if (alt_folder.length >= 1 && alt_folder.get(0) == '/') alt_folder =
                        alt_folder.substring(1)
                }

                return CheckSaveLocationResult(false, alt_folder)
            } else {
                // already in expected format (indicates a sub-folder of DCIM)
                return CheckSaveLocationResult(true, null)
            }
        }

        private fun getOnlineHelpUrl(append: String): String {
            if (MyDebug.LOG) Log.d(TAG, "getOnlineHelpUrl: " + append)
            // if we change this, remember that any page linked to must abide by Google Play developer policies!
            // also if we change this method name or where it's located, remember to update the mention in
            // opencamera_source.txt
            //return "https://opencamera.sourceforge.io/" + append;
            return "https://opencamera.org.uk/" + append
        }

        /** Returns rotation in degrees (as a multiple of 90 degrees) corresponding to the supplied
         * system orientation.
         */
        @JvmStatic
        fun getRotationFromSystemOrientation(system_orientation: SystemOrientation?): Int {
            val rotation: Int
            if (system_orientation == SystemOrientation.PORTRAIT) rotation = 270
            else if (system_orientation == SystemOrientation.REVERSE_LANDSCAPE) rotation = 180
            else rotation = 0
            return rotation
        }

        /** Processes a user specified save folder. This should be used with the non-SAF scoped storage
         * method, where the user types a folder directly.
         */
        @JvmStatic
        fun processUserSaveLocation(folder: String): String {
            // filter repeated '/', e.g., replace // with /:
            var folder = folder
            val strip = "//"
            while (folder.length >= 1 && folder.contains(strip)) {
                folder = folder.replace(strip.toRegex(), "/")
            }

            if (folder.length >= 1 && folder.get(0) == '/') {
                // strip '/' as first character - as absolute paths not allowed with scoped storage
                // whilst we do block entering a '/' as first character in the InputFilter, users could
                // get around this (e.g., put a '/' as second character, then delete the first character)
                folder = folder.substring(1)
            }

            if (folder.length >= 1 && folder.get(folder.length - 1) == '/') {
                // strip '/' as last character - MediaStore will ignore it, but seems cleaner to strip it out anyway
                // (we still need to allow '/' as last character in the InputFilter, otherwise users won't be able to type it whilst writing a subfolder)
                folder = folder.substring(0, folder.length - 1)
            }

            return folder
        }

        private fun putBundleExtra(bundle: Bundle, key: String?, values: MutableList<String>?) {
            if (values != null) {
                val values_arr = arrayOfNulls<String>(values.size)
                var i = 0
                for (value in values) {
                    values_arr[i] = value
                    i++
                }
                bundle.putStringArray(key, values_arr)
            }
        }

        @JvmStatic
        fun performHapticFeedback(seekBar: SeekBar, last_haptic_time: Long): Long {
            var last_haptic_time = last_haptic_time
            val sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(seekBar.getContext())
            if (sharedPreferences.getBoolean(
                    PreferenceKeys.AllowHapticFeedbackPreferenceKey, true
                )
            ) {
                val time_ms = System.currentTimeMillis()
                if (time_ms > last_haptic_time + 16) {
                    last_haptic_time = time_ms
                    // SEGMENT_TICK or SEGMENT_TICK doesn't work on Galaxy S24+ at least, even though on Android 14!
                    /*if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ) {
                    seekBar.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK);
                }
                else*/
                    run {
                        seekBar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            return last_haptic_time
        }
    }

    fun showResetSettingsDialog() {
        mainUI?.showConfirmDialog(
            this,
            getString(R.string.preference_reset),
            getString(R.string.preference_reset_question),
            {
                setDeviceDefaults()
                restartOpenCamera()
            },
            {})
    }

    fun showUpgradeDialog() {
        mainUI?.showConfirmDialog(
            this,
            getString(R.string.dialog_pro_version_title),
            getString(R.string.dialog_pro_version_msg),
            getString(R.string.unlock_pro),
            getString(R.string.later),
            { this.doUpgrade() },
            {})
    }

    fun resetOnScreenGUI() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.edit {
            remove(PreferenceKeys.ShowCycleRawPreferenceKey)
            remove(PreferenceKeys.ShowStoreLocationPreferenceKey)
            remove(PreferenceKeys.ShowTextStampPreferenceKey)
            remove(PreferenceKeys.ShowStampPreferenceKey)
            remove(PreferenceKeys.ShowFocusPeakingPreferenceKey)
            remove(PreferenceKeys.ShowAutoLevelPreferenceKey)
            remove(PreferenceKeys.ShowCycleFlashPreferenceKey)
            remove(PreferenceKeys.ShowFaceDetectionPreferenceKey)
            remove(PreferenceKeys.MultiCamButtonPreferenceKey)
            remove(PreferenceKeys.ShowGridPreferenceKey)
        }
    }
}