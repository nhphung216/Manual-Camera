package com.ssolstice.camera.manual;

import static com.ssolstice.camera.manual.PreferenceKeys.PreferenceKey_Root;
import static com.ssolstice.camera.manual.PreferenceKeys.ShowAutoLevelPreferenceKey;
import static com.ssolstice.camera.manual.PreferenceKeys.ShowCameraIDPreferenceKey;
import static com.ssolstice.camera.manual.PreferenceKeys.ShowCycleRawPreferenceKey;
import static com.ssolstice.camera.manual.PreferenceKeys.ShowISOPreferenceKey;
import static com.ssolstice.camera.manual.PreferenceKeys.ShowWhiteBalanceLockPreferenceKey;

import com.ssolstice.camera.manual.cameracontroller.CameraController;
import com.ssolstice.camera.manual.preview.Preview;
import com.ssolstice.camera.manual.ui.ArraySeekBarPreference;
import com.ssolstice.camera.manual.ui.FolderChooserDialog;
import com.ssolstice.camera.manual.ui.MyEditTextPreference;
import com.ssolstice.camera.manual.utils.LocaleHelper;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class MyPreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    private static final String TAG = "MyPreferenceFragment";

    private boolean edge_to_edge_mode = false;

    private int cameraId;

    private final HashSet<AlertDialog> dialogs = new HashSet<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (MyDebug.LOG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        final Bundle bundle = getArguments();
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        preferenceMain(bundle, sharedPreferences);
        preferenceSubPhoto(bundle, sharedPreferences);
        preferenceSubVideo(bundle, sharedPreferences);
        preferenceSubPreview(bundle, sharedPreferences);
        preferenceSubGUI(bundle, sharedPreferences);
        preferenceSubProcessing(bundle, sharedPreferences);
        preferenceSubCameraControlsMore(bundle, sharedPreferences);

        setupDependencies();

        updatePreferenceSummaries(getPreferenceScreen(), PreferenceManager.getDefaultSharedPreferences(getActivity()));
    }

    private void updatePreferenceSummaries(PreferenceGroup group, SharedPreferences sharedPreferences) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                updatePreferenceSummaries((PreferenceGroup) pref, sharedPreferences); // đệ quy
            } else {
                updatePreferenceSummary(pref, sharedPreferences);
            }
        }
    }

    private void updatePreferenceSummary(Preference preference, SharedPreferences sharedPreferences) {
        if (preference == null) return;

        if (preference instanceof ListPreference) {
            ListPreference listPref = (ListPreference) preference;
            String value = sharedPreferences.getString(listPref.getKey(), "");
            int index = listPref.findIndexOfValue(value);
            if (index >= 0) listPref.setSummary(listPref.getEntries()[index]);
        } else if (preference instanceof EditTextPreference) {
            EditTextPreference editPref = (EditTextPreference) preference;
            editPref.setSummary(editPref.getText());
        } else if (preference instanceof MyEditTextPreference) {
            MyEditTextPreference editPref = (MyEditTextPreference) preference;
            editPref.setSummary(editPref.getText());
        } else {
            Object value = sharedPreferences.getAll().get(preference.getKey());
            if (value instanceof String) {
                preference.setSummary((String) value);
            } else if (value instanceof Integer) {
                preference.setSummary(String.valueOf(value));
            } else if (value instanceof Boolean) {
                // preference.setSummary(String.valueOf(value));
            }
        }
    }


    private void preferenceSubCameraControlsMore(Bundle bundle, SharedPreferences sharedPreferences) {
        final boolean can_disable_shutter_sound = bundle.getBoolean("can_disable_shutter_sound");
        if (MyDebug.LOG) Log.d(TAG, "can_disable_shutter_sound: " + can_disable_shutter_sound);
        if (!can_disable_shutter_sound) {
            Preference pref = findPreference("preference_shutter_sound");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        {
            Preference pref = findPreference("preference_save_location");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if (MyDebug.LOG) Log.d(TAG, "clicked save location");
                    MainActivity main_activity = (MainActivity) getActivity();
                    if (main_activity.getStorageUtils().isUsingSAF()) {
                        main_activity.openFolderChooserDialogSAF(true);
                        return true;
                    } else if (MainActivity.useScopedStorage()) {
                        // we can't use an EditTextPreference (or MyEditTextPreference) due to having to support non-scoped-storage, or when SAF is enabled...
                        // anyhow, this means we can share code when called from gallery long-press anyway
                        AlertDialog.Builder alertDialog = main_activity.createSaveFolderDialog();
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if (MyDebug.LOG) Log.d(TAG, "save folder dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        return true;
                    } else {
                        File start_folder = main_activity.getStorageUtils().getImageFolder();

                        FolderChooserDialog fragment = new MyPreferenceFragment.SaveFolderChooserDialog();
                        fragment.setStartFolder(start_folder);
                        fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
                        return true;
                    }
                }
            });
        }
    }

    private void preferenceSubPhoto(Bundle bundle, SharedPreferences sharedPreferences) {
        final boolean supports_expo_bracketing = bundle.getBoolean("supports_expo_bracketing");
        Log.d(TAG, "supports_expo_bracketing: " + supports_expo_bracketing);

        final int max_expo_bracketing_n_images = bundle.getInt("max_expo_bracketing_n_images");
        if (MyDebug.LOG)
            Log.d(TAG, "max_expo_bracketing_n_images: " + max_expo_bracketing_n_images);


        final int[] widths = bundle.getIntArray("resolution_widths");
        final int[] heights = bundle.getIntArray("resolution_heights");
        final String cameraIdSPhysical = bundle.getString("cameraIdSPhysical");
        final boolean[] supports_burst = bundle.getBooleanArray("resolution_supports_burst");
        if (widths != null && heights != null && supports_burst != null) {
            CharSequence[] entries = new CharSequence[widths.length];
            CharSequence[] values = new CharSequence[widths.length];
            for (int i = 0; i < widths.length; i++) {
                entries[i] = widths[i] + " x " + heights[i] + " " + Preview.getAspectRatioMPString(getResources(), widths[i], heights[i], supports_burst[i]);
                values[i] = widths[i] + " " + heights[i];
            }
            ListPreference lp = (ListPreference) findPreference("preference_resolution");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            String resolution_preference_key = PreferenceKeys.getResolutionPreferenceKey(cameraId, cameraIdSPhysical);
            String resolution_value = sharedPreferences.getString(resolution_preference_key, "");
            if (MyDebug.LOG) Log.d(TAG, "resolution_value: " + resolution_value);
            lp.setValue(resolution_value);
            // now set the key, so we save for the correct cameraId
            lp.setKey(resolution_preference_key);
        } else {
            Preference pref = findPreference("preference_resolution");
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        {
            final int n_quality = 100;
            CharSequence[] entries = new CharSequence[n_quality];
            CharSequence[] values = new CharSequence[n_quality];
            for (int i = 0; i < n_quality; i++) {
                entries[i] = (i + 1) + "%";
                values[i] = String.valueOf(i + 1);
            }
            ArraySeekBarPreference sp = (ArraySeekBarPreference) findPreference("preference_quality");
            sp.setEntries(entries);
            sp.setEntryValues(values);
        }

        if (!supports_expo_bracketing || max_expo_bracketing_n_images <= 3) {
            Preference pref = findPreference("preference_expo_bracketing_n_images");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }
        if (!supports_expo_bracketing) {
            Preference pref = findPreference("preference_expo_bracketing_stops");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }
    }

    private void preferenceMain(Bundle bundle, SharedPreferences sharedPreferences) {
        this.edge_to_edge_mode = bundle.getBoolean("edge_to_edge_mode");

        this.cameraId = bundle.getInt("cameraId");

        final boolean camera_open = bundle.getBoolean("camera_open");

        final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");

        if (!supports_face_detection && (camera_open || !sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false))) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.FaceDetectionPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_camera_controls");
            pg.removePreference(pref);
        }

        {
            List<String> camera_api_values = new ArrayList<>();
            List<String> camera_api_entries = new ArrayList<>();

            // all devices support old api
            camera_api_values.add("preference_camera_api_old");
            camera_api_entries.add(getActivity().getResources().getString(R.string.preference_camera_api_old));

            final boolean supports_camera2 = bundle.getBoolean("supports_camera2");
            Log.d(TAG, "supports_camera2: " + supports_camera2);
            if (supports_camera2) {
                camera_api_values.add("preference_camera_api_camera2");
                camera_api_entries.add(getActivity().getResources().getString(R.string.preference_camera_api_camera2));
            }

            if (camera_api_values.size() == 1) {
                // if only supports 1 API, no point showing the preference
                camera_api_values.clear();
                camera_api_entries.clear();
            }

            readFromBundle(camera_api_values.toArray(new String[0]), camera_api_entries.toArray(new String[0]), "preference_camera_api", PreferenceKeys.CameraAPIPreferenceDefault, "preference_category_online");

            if (camera_api_values.size() >= 2) {
                final Preference pref = findPreference("preference_camera_api");
                pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference arg0, Object newValue) {
                        if (pref.getKey().equals("preference_camera_api")) {
                            ListPreference list_pref = (ListPreference) pref;
                            if (list_pref.getValue().equals(newValue)) {
                                Log.d(TAG, "user selected same camera API");
                            } else {
                                Log.d(TAG, "user changed camera API - need to restart");
                                MainActivity main_activity = (MainActivity) MyPreferenceFragment.this.getActivity();
                                main_activity.restartOpenCamera();
                            }
                        }
                        return true;
                    }
                });
            }
        }

        preferenceAbout(bundle, sharedPreferences);
    }

    private void preferenceAbout(Bundle bundle, SharedPreferences sharedPreferences) {
        final String camera_api = bundle.getString("camera_api");
        final int nCameras = bundle.getInt("nCameras");
        final float camera_view_angle_x = bundle.getFloat("camera_view_angle_x");
        final float camera_view_angle_y = bundle.getFloat("camera_view_angle_y");
        final String photo_mode_string = bundle.getString("photo_mode_string");
        final boolean using_android_l = bundle.getBoolean("using_android_l");
        final int camera_orientation = bundle.getInt("camera_orientation");

        final float min_zoom_factor = bundle.getFloat("min_zoom_factor");
        final float max_zoom_factor = bundle.getFloat("max_zoom_factor");
        final boolean supports_auto_stabilise = bundle.getBoolean("supports_auto_stabilise");
        final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");

        final boolean camera_open = bundle.getBoolean("camera_open");
        if (!supports_face_detection && (camera_open || !sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false))) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.FaceDetectionPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_camera_controls");
            pg.removePreference(pref);
        }

        final int preview_width = bundle.getInt("preview_width");
        final int preview_height = bundle.getInt("preview_height");
        final int[] preview_widths = bundle.getIntArray("preview_widths");
        final int[] preview_heights = bundle.getIntArray("preview_heights");
        final int[] video_widths = bundle.getIntArray("video_widths");
        final int[] video_heights = bundle.getIntArray("video_heights");

        final int resolution_width = bundle.getInt("resolution_width");
        final int resolution_height = bundle.getInt("resolution_height");
        final int[] widths = bundle.getIntArray("resolution_widths");
        final int[] heights = bundle.getIntArray("resolution_heights");
        final boolean[] supports_burst = bundle.getBooleanArray("resolution_supports_burst");

        final boolean supports_raw = bundle.getBoolean("supports_raw");
        final boolean supports_hdr = bundle.getBoolean("supports_hdr");
        final boolean supports_panorama = bundle.getBoolean("supports_panorama");
        final boolean has_gyro_sensors = bundle.getBoolean("has_gyro_sensors");
        final boolean supports_expo_bracketing = bundle.getBoolean("supports_expo_bracketing");
        final boolean supports_exposure_compensation = bundle.getBoolean("supports_exposure_compensation");
        final int exposure_compensation_min = bundle.getInt("exposure_compensation_min");
        final int exposure_compensation_max = bundle.getInt("exposure_compensation_max");

        final boolean supports_iso_range = bundle.getBoolean("supports_iso_range");
        final int iso_range_min = bundle.getInt("iso_range_min");
        final int iso_range_max = bundle.getInt("iso_range_max");
        final boolean supports_exposure_time = bundle.getBoolean("supports_exposure_time");
        final long exposure_time_min = bundle.getLong("exposure_time_min");
        final long exposure_time_max = bundle.getLong("exposure_time_max");
        final boolean supports_white_balance_temperature = bundle.getBoolean("supports_white_balance_temperature");
        final int white_balance_temperature_min = bundle.getInt("white_balance_temperature_min");
        final int white_balance_temperature_max = bundle.getInt("white_balance_temperature_max");
        final boolean is_multi_cam = bundle.getBoolean("is_multi_cam");
        final String[] video_quality = bundle.getStringArray("video_quality");
        final String current_video_quality = bundle.getString("current_video_quality");
        final int video_frame_width = bundle.getInt("video_frame_width");
        final int video_frame_height = bundle.getInt("video_frame_height");
        final int video_bit_rate = bundle.getInt("video_bit_rate");
        final int video_frame_rate = bundle.getInt("video_frame_rate");
        final double video_capture_rate = bundle.getDouble("video_capture_rate");
        final boolean video_high_speed = bundle.getBoolean("video_high_speed");
        final float video_capture_rate_factor = bundle.getFloat("video_capture_rate_factor");

        final boolean supports_optical_stabilization = bundle.getBoolean("supports_optical_stabilization");
        final boolean optical_stabilization_enabled = bundle.getBoolean("optical_stabilization_enabled");

        final boolean supports_video_stabilization = bundle.getBoolean("supports_video_stabilization");
        final boolean video_stabilization_enabled = bundle.getBoolean("video_stabilization_enabled");

        final boolean can_disable_shutter_sound = bundle.getBoolean("can_disable_shutter_sound");

        final int tonemap_max_curve_points = bundle.getInt("tonemap_max_curve_points");
        final boolean supports_tonemap_curve = bundle.getBoolean("supports_tonemap_curve");

        {
            final Preference pref = findPreference("preference_about");
            pref.setOnPreferenceClickListener(arg0 -> {
                if (pref.getKey().equals("preference_about")) {
                    Log.d(TAG, "user clicked about");
                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                    alertDialog.setTitle(R.string.preference_about);
                    final StringBuilder about_string = new StringBuilder();
                    String version = "UNKNOWN_VERSION";
                    int version_code = -1;
                    try {
                        PackageInfo pInfo = MyPreferenceFragment.this.getActivity().getPackageManager().getPackageInfo(MyPreferenceFragment.this.getActivity().getPackageName(), 0);
                        version = pInfo.versionName;
                        version_code = pInfo.versionCode;
                    } catch (NameNotFoundException e) {
                        Log.d(TAG, "NameNotFoundException exception trying to get version number");
                        e.printStackTrace();
                    }
                    about_string.append("ManualCamera v");
                    about_string.append(version);
                    about_string.append("\nCode: ");
                    about_string.append(version_code);
                    about_string.append("\nPackage: ");
                    about_string.append(MyPreferenceFragment.this.getActivity().getPackageName());
                    about_string.append("\nAndroid API version: ");
                    about_string.append(Build.VERSION.SDK_INT);
                    about_string.append("\nDevice manufacturer: ");
                    about_string.append(Build.MANUFACTURER);
                    about_string.append("\nDevice model: ");
                    about_string.append(Build.MODEL);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // use non-deprecated equivalent of Display.getSize()
                        WindowMetrics window_metrics = MyPreferenceFragment.this.getActivity().getWindowManager().getCurrentWindowMetrics();
                        final WindowInsets windowInsets = window_metrics.getWindowInsets();
                        Insets insets = windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                        int insetsWidth = insets.right + insets.left;
                        int insetsHeight = insets.top + insets.bottom;
                        final Rect bounds = window_metrics.getBounds();
                        int display_x = bounds.width() - insetsWidth;
                        int display_y = bounds.height() - insetsHeight;
                        about_string.append("\nDisplay size: ");
                        about_string.append(display_x);
                        about_string.append("x");
                        about_string.append(display_y);
                    } else {
                        Point display_size = new Point();
                        Display display = MyPreferenceFragment.this.getActivity().getWindowManager().getDefaultDisplay();
                        display.getSize(display_size);
                        about_string.append("\nDisplay size: ");
                        about_string.append(display_size.x);
                        about_string.append("x");
                        about_string.append(display_size.y);
                    }
                    about_string.append("\nCurrent camera ID: ");
                    about_string.append(cameraId);
                    about_string.append("\nNo. of cameras: ");
                    about_string.append(nCameras);
                    about_string.append("\nMulti-camera?: ");
                    about_string.append(is_multi_cam);
                    about_string.append("\nCamera API: ");
                    about_string.append(camera_api);
                    about_string.append("\nCamera orientation: ");
                    about_string.append(camera_orientation);
                    about_string.append("\nPhoto mode: ");
                    about_string.append(photo_mode_string == null ? "UNKNOWN" : photo_mode_string);
                    {
                        String last_video_error = sharedPreferences.getString("last_video_error", "");
                        if (!last_video_error.isEmpty()) {
                            about_string.append("\nLast video error: ");
                            about_string.append(last_video_error);
                        }
                    }
                    about_string.append("\nMin zoom factor: ");
                    about_string.append(min_zoom_factor);
                    about_string.append("\nMax zoom factor: ");
                    about_string.append(max_zoom_factor);
                    if (preview_widths != null && preview_heights != null) {
                        about_string.append("\nPreview resolutions: ");
                        for (int i = 0; i < preview_widths.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(preview_widths[i]);
                            about_string.append("x");
                            about_string.append(preview_heights[i]);
                        }
                    }
                    about_string.append("\nPreview resolution: ");
                    about_string.append(preview_width);
                    about_string.append("x");
                    about_string.append(preview_height);
                    if (widths != null && heights != null) {
                        about_string.append("\nPhoto resolutions: ");
                        for (int i = 0; i < widths.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(widths[i]);
                            about_string.append("x");
                            about_string.append(heights[i]);
                            if (supports_burst != null && !supports_burst[i]) {
                                about_string.append("[no burst]");
                            }
                        }
                    }
                    about_string.append("\nPhoto resolution: ");
                    about_string.append(resolution_width);
                    about_string.append("x");
                    about_string.append(resolution_height);
                    if (video_quality != null) {
                        about_string.append("\nVideo qualities: ");
                        for (int i = 0; i < video_quality.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(video_quality[i]);
                        }
                    }
                    if (video_widths != null && video_heights != null) {
                        about_string.append("\nVideo resolutions: ");
                        for (int i = 0; i < video_widths.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(video_widths[i]);
                            about_string.append("x");
                            about_string.append(video_heights[i]);
                        }
                    }
                    about_string.append("\nVideo quality: ");
                    about_string.append(current_video_quality);
                    about_string.append("\nVideo frame width: ");
                    about_string.append(video_frame_width);
                    about_string.append("\nVideo frame height: ");
                    about_string.append(video_frame_height);
                    about_string.append("\nVideo bit rate: ");
                    about_string.append(video_bit_rate);
                    about_string.append("\nVideo frame rate: ");
                    about_string.append(video_frame_rate);
                    about_string.append("\nVideo capture rate: ");
                    about_string.append(video_capture_rate);
                    about_string.append("\nVideo high speed: ");
                    about_string.append(video_high_speed);
                    about_string.append("\nVideo capture rate factor: ");
                    about_string.append(video_capture_rate_factor);
                    about_string.append("\nAuto-level?: ");
                    about_string.append(getString(supports_auto_stabilise ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nAuto-level enabled?: ");
                    about_string.append(sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false));
                    about_string.append("\nFace detection?: ");
                    about_string.append(getString(supports_face_detection ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nRAW?: ");
                    about_string.append(getString(supports_raw ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nHDR?: ");
                    about_string.append(getString(supports_hdr ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nPanorama?: ");
                    about_string.append(getString(supports_panorama ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nGyro sensors?: ");
                    about_string.append(getString(has_gyro_sensors ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nExpo?: ");
                    about_string.append(getString(supports_expo_bracketing ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nExpo compensation?: ");
                    about_string.append(getString(supports_exposure_compensation ? R.string.about_available : R.string.about_not_available));
                    if (supports_exposure_compensation) {
                        about_string.append("\nExposure compensation range: ");
                        about_string.append(exposure_compensation_min);
                        about_string.append(" to ");
                        about_string.append(exposure_compensation_max);
                    }
                    about_string.append("\nManual ISO?: ");
                    about_string.append(getString(supports_iso_range ? R.string.about_available : R.string.about_not_available));
                    if (supports_iso_range) {
                        about_string.append("\nISO range: ");
                        about_string.append(iso_range_min);
                        about_string.append(" to ");
                        about_string.append(iso_range_max);
                    }
                    about_string.append("\nManual exposure?: ");
                    about_string.append(getString(supports_exposure_time ? R.string.about_available : R.string.about_not_available));
                    if (supports_exposure_time) {
                        about_string.append("\nExposure range: ");
                        about_string.append(exposure_time_min);
                        about_string.append(" to ");
                        about_string.append(exposure_time_max);
                    }
                    about_string.append("\nManual WB?: ");
                    about_string.append(getString(supports_white_balance_temperature ? R.string.about_available : R.string.about_not_available));
                    if (supports_white_balance_temperature) {
                        about_string.append("\nWB temperature: ");
                        about_string.append(white_balance_temperature_min);
                        about_string.append(" to ");
                        about_string.append(white_balance_temperature_max);
                    }
                    about_string.append("\nOptical stabilization?: ");
                    about_string.append(getString(supports_optical_stabilization ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nOptical stabilization enabled?: ");
                    about_string.append(optical_stabilization_enabled);
                    about_string.append("\nVideo stabilization?: ");
                    about_string.append(getString(supports_video_stabilization ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nVideo stabilization enabled?: ");
                    about_string.append(video_stabilization_enabled);
                    about_string.append("\nTonemap curve?: ");
                    about_string.append(getString(supports_tonemap_curve ? R.string.about_available : R.string.about_not_available));
                    about_string.append("\nTonemap max curve points: ");
                    about_string.append(tonemap_max_curve_points);
                    about_string.append("\nCan disable shutter sound?: ");
                    about_string.append(getString(can_disable_shutter_sound ? R.string.about_available : R.string.about_not_available));

                    about_string.append("\nCamera view angle: ").append(camera_view_angle_x).append(" , ").append(camera_view_angle_y);

                    about_string.append("\nFlash modes: ");
                    String[] flash_values = bundle.getStringArray("flash_values");
                    if (flash_values != null && flash_values.length > 0) {
                        for (int i = 0; i < flash_values.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(flash_values[i]);
                        }
                    } else {
                        about_string.append("None");
                    }
                    about_string.append("\nFocus modes: ");
                    String[] focus_values = bundle.getStringArray("focus_values");
                    if (focus_values != null && focus_values.length > 0) {
                        for (int i = 0; i < focus_values.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(focus_values[i]);
                        }
                    } else {
                        about_string.append("None");
                    }
                    about_string.append("\nColor effects: ");
                    String[] color_effects_values = bundle.getStringArray("color_effects");
                    if (color_effects_values != null && color_effects_values.length > 0) {
                        for (int i = 0; i < color_effects_values.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(color_effects_values[i]);
                        }
                    } else {
                        about_string.append("None");
                    }
                    about_string.append("\nScene modes: ");
                    String[] scene_modes_values = bundle.getStringArray("scene_modes");
                    if (scene_modes_values != null && scene_modes_values.length > 0) {
                        for (int i = 0; i < scene_modes_values.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(scene_modes_values[i]);
                        }
                    } else {
                        about_string.append("None");
                    }
                    about_string.append("\nWhite balances: ");
                    String[] white_balances_values = bundle.getStringArray("white_balances");
                    if (white_balances_values != null && white_balances_values.length > 0) {
                        for (int i = 0; i < white_balances_values.length; i++) {
                            if (i > 0) {
                                about_string.append(", ");
                            }
                            about_string.append(white_balances_values[i]);
                        }
                    } else {
                        about_string.append("None");
                    }
                    if (!using_android_l) {
                        about_string.append("\nISOs: ");
                        String[] isos = bundle.getStringArray("isos");
                        if (isos != null && isos.length > 0) {
                            for (int i = 0; i < isos.length; i++) {
                                if (i > 0) {
                                    about_string.append(", ");
                                }
                                about_string.append(isos[i]);
                            }
                        } else {
                            about_string.append("None");
                        }
                        String iso_key = bundle.getString("iso_key");
                        if (iso_key != null) {
                            about_string.append("\nISO key: ");
                            about_string.append(iso_key);
                        }
                    }

                    int magnetic_accuracy = bundle.getInt("magnetic_accuracy");
                    about_string.append("\nMagnetic accuracy?: ");
                    about_string.append(magnetic_accuracy);

                    about_string.append("\nUsing SAF?: ");
                    about_string.append(sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false));
                    String save_location = sharedPreferences.getString(PreferenceKeys.SaveLocationPreferenceKey, "ManualCamera");
                    about_string.append("\nSave Location: ");
                    about_string.append(save_location);
                    String save_location_saf = sharedPreferences.getString(PreferenceKeys.SaveLocationSAFPreferenceKey, "");
                    about_string.append("\nSave Location SAF: ");
                    about_string.append(save_location_saf);

                    about_string.append("\nParameters: ");
                    String parameters_string = bundle.getString("parameters_string");
                    if (parameters_string != null) {
                        about_string.append(parameters_string);
                    } else {
                        about_string.append("None");
                    }

                    SpannableString span = new SpannableString(about_string);

                    // clickable text is only supported if we call setMovementMethod on the TextView - which means we need to create
                    // our own for the AlertDialog!
                    @SuppressLint("InflateParams")
                    // we add the view to the alert dialog in addTextViewForAlertDialog()
                    final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_textview, null);
                    final TextView textView = dialog_view.findViewById(R.id.text_view);

                    textView.setText(span);
                    textView.setMovementMethod(LinkMovementMethod.getInstance());
                    textView.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
                    addTextViewForAlertDialog(alertDialog, textView);

                    alertDialog.setPositiveButton(android.R.string.ok, null);
                    alertDialog.setNegativeButton(R.string.about_copy_to_clipboard, (dialog, id) -> {
                        if (MyDebug.LOG) Log.d(TAG, "user clicked copy to clipboard");
                        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("ManualCamera About", about_string);
                        clipboard.setPrimaryClip(clip);
                    });
                    final AlertDialog alert = alertDialog.create();
                    // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                    alert.setOnDismissListener(arg1 -> {
                        if (MyDebug.LOG) Log.d(TAG, "about dialog dismissed");
                        dialogs.remove(alert);
                    });
                    alert.show();
                    dialogs.add(alert);
                    return false;
                }
                return false;
            });
        }
    }

    private void preferenceSubProcessing(Bundle bundle, SharedPreferences sharedPreferences) {
        final boolean camera_open = bundle.getBoolean("camera_open");
        if (MyDebug.LOG) Log.d(TAG, "camera_open: " + camera_open);

        boolean has_antibanding = false;
        String[] antibanding_values = bundle.getStringArray("antibanding");
        if (antibanding_values != null && antibanding_values.length > 0) {
            String[] antibanding_entries = bundle.getStringArray("antibanding_entries");
            if (antibanding_entries != null && antibanding_entries.length == antibanding_values.length) { // should always be true here, but just in case
                MyPreferenceFragment.readFromBundle(this, antibanding_values, antibanding_entries, PreferenceKeys.AntiBandingPreferenceKey, CameraController.ANTIBANDING_DEFAULT, PreferenceKey_Root);
                has_antibanding = true;
            }
        }
        if (MyDebug.LOG) Log.d(TAG, "has_antibanding?: " + has_antibanding);
        if (!has_antibanding && (camera_open || sharedPreferences.getString(PreferenceKeys.AntiBandingPreferenceKey, CameraController.ANTIBANDING_DEFAULT).equals(CameraController.ANTIBANDING_DEFAULT))) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.AntiBandingPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_processing_settings");
            pg.removePreference(pref);
        }

        boolean has_edge_mode = false;
        String[] edge_mode_values = bundle.getStringArray("edge_modes");
        if (edge_mode_values != null && edge_mode_values.length > 0) {
            String[] edge_mode_entries = bundle.getStringArray("edge_modes_entries");
            if (edge_mode_entries != null && edge_mode_entries.length == edge_mode_values.length) { // should always be true here, but just in case
                MyPreferenceFragment.readFromBundle(this, edge_mode_values, edge_mode_entries, PreferenceKeys.EdgeModePreferenceKey, CameraController.EDGE_MODE_DEFAULT, PreferenceKey_Root);
                has_edge_mode = true;
            }
        }
        if (MyDebug.LOG) Log.d(TAG, "has_edge_mode?: " + has_edge_mode);
        if (!has_edge_mode && (camera_open || sharedPreferences.getString(PreferenceKeys.EdgeModePreferenceKey, CameraController.EDGE_MODE_DEFAULT).equals(CameraController.EDGE_MODE_DEFAULT))) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.EdgeModePreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_processing_settings");
            pg.removePreference(pref);
        }

        boolean has_noise_reduction_mode = false;
        String[] noise_reduction_mode_values = bundle.getStringArray("noise_reduction_modes");
        if (noise_reduction_mode_values != null && noise_reduction_mode_values.length > 0) {
            String[] noise_reduction_mode_entries = bundle.getStringArray("noise_reduction_modes_entries");
            if (noise_reduction_mode_entries != null && noise_reduction_mode_entries.length == noise_reduction_mode_values.length) { // should always be true here, but just in case
                MyPreferenceFragment.readFromBundle(this, noise_reduction_mode_values, noise_reduction_mode_entries, PreferenceKeys.CameraNoiseReductionModePreferenceKey, CameraController.NOISE_REDUCTION_MODE_DEFAULT, PreferenceKey_Root);
                has_noise_reduction_mode = true;
            }
        }
        if (MyDebug.LOG) Log.d(TAG, "has_noise_reduction_mode?: " + has_noise_reduction_mode);
        if (!has_noise_reduction_mode && (camera_open || sharedPreferences.getString(PreferenceKeys.CameraNoiseReductionModePreferenceKey, CameraController.NOISE_REDUCTION_MODE_DEFAULT).equals(CameraController.NOISE_REDUCTION_MODE_DEFAULT))) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference(PreferenceKeys.CameraNoiseReductionModePreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_processing_settings");
            pg.removePreference(pref);
        }
    }

    private void preferenceSubGUI(Bundle bundle, SharedPreferences sharedPreferences) {
        final boolean camera_open = bundle.getBoolean("camera_open");
        Log.d(TAG, "camera_open: " + camera_open);

        final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");
        Log.d(TAG, "supports_face_detection: " + supports_face_detection);

        final boolean supports_flash = bundle.getBoolean("supports_flash");
        Log.d(TAG, "supports_flash: " + supports_flash);

        final boolean supports_preview_bitmaps = bundle.getBoolean("supports_preview_bitmaps");
        Log.d(TAG, "supports_preview_bitmaps: " + supports_preview_bitmaps);

        final boolean supports_auto_stabilise = bundle.getBoolean("supports_auto_stabilise");
        Log.d(TAG, "supports_auto_stabilise: " + supports_auto_stabilise);

        final boolean supports_raw = bundle.getBoolean("supports_raw");
        Log.d(TAG, "supports_raw: " + supports_raw);

        final boolean supports_white_balance_lock = bundle.getBoolean("supports_white_balance_lock");
        Log.d(TAG, "supports_white_balance_lock: " + supports_white_balance_lock);

        final boolean supports_exposure_lock = bundle.getBoolean("supports_exposure_lock");
        Log.d(TAG, "supports_exposure_lock: " + supports_exposure_lock);

        final boolean is_multi_cam = bundle.getBoolean("is_multi_cam");
        Log.d(TAG, "is_multi_cam: " + is_multi_cam);

        final boolean has_physical_cameras = bundle.getBoolean("has_physical_cameras");
        Log.d(TAG, "has_physical_cameras: " + has_physical_cameras);

        if (!supports_face_detection && (camera_open || !sharedPreferences.getBoolean(PreferenceKeys.FaceDetectionPreferenceKey, false))) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            Preference pref = findPreference("preference_show_face_detection");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_flash) {
            Preference pref = findPreference("preference_show_cycle_flash");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_preview_bitmaps) {
            Preference pref = findPreference("preference_show_focus_peaking");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_auto_stabilise) {
            Preference pref = findPreference(ShowAutoLevelPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_raw) {
            Preference pref = findPreference(ShowCycleRawPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_white_balance_lock) {
            Preference pref = findPreference(ShowWhiteBalanceLockPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_exposure_lock) {
            Preference pref = findPreference("preference_show_exposure_lock");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!is_multi_cam && !has_physical_cameras) {
            Preference pref = findPreference("preference_multi_cam_button");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }
    }

    private void preferenceSubPreview(Bundle bundle, SharedPreferences sharedPreferences) {
        final boolean using_android_l = bundle.getBoolean("using_android_l");
        if (MyDebug.LOG) Log.d(TAG, "using_android_l: " + using_android_l);

        final boolean is_multi_cam = bundle.getBoolean("is_multi_cam");
        if (MyDebug.LOG) Log.d(TAG, "is_multi_cam: " + is_multi_cam);

        if (!is_multi_cam) {
            Preference pref = findPreference(ShowCameraIDPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!using_android_l) {
            Preference pref = findPreference(ShowISOPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }
    }

    private void preferenceSubVideo(Bundle bundle, SharedPreferences sharedPreferences) {
        final int cameraId = bundle.getInt("cameraId");
        if (MyDebug.LOG) Log.d(TAG, "cameraId: " + cameraId);
        final String cameraIdSPhysical = bundle.getString("cameraIdSPhysical");
        if (MyDebug.LOG) Log.d(TAG, "cameraIdSPhysical: " + cameraIdSPhysical);

        final boolean camera_open = bundle.getBoolean("camera_open");
        if (MyDebug.LOG) Log.d(TAG, "camera_open: " + camera_open);

        final String[] video_quality = bundle.getStringArray("video_quality");
        final String[] video_quality_string = bundle.getStringArray("video_quality_string");

        final int[] video_fps = bundle.getIntArray("video_fps");
        final boolean[] video_fps_high_speed = bundle.getBooleanArray("video_fps_high_speed");

        String fps_preference_key = PreferenceKeys.getVideoFPSPreferenceKey(cameraId, cameraIdSPhysical);
        if (MyDebug.LOG) Log.d(TAG, "fps_preference_key: " + fps_preference_key);
        String fps_value = sharedPreferences.getString(fps_preference_key, "default");
        if (MyDebug.LOG) Log.d(TAG, "fps_value: " + fps_value);

        final boolean supports_tonemap_curve = bundle.getBoolean("supports_tonemap_curve");
        if (MyDebug.LOG) Log.d(TAG, "supports_tonemap_curve: " + supports_tonemap_curve);

        final boolean supports_video_stabilization = bundle.getBoolean("supports_video_stabilization");
        if (MyDebug.LOG)
            Log.d(TAG, "supports_video_stabilization: " + supports_video_stabilization);

        final boolean supports_force_video_4k = bundle.getBoolean("supports_force_video_4k");
        if (MyDebug.LOG) Log.d(TAG, "supports_force_video_4k: " + supports_force_video_4k);

		/* Set up video resolutions.
		   Note that this will be the resolutions for either standard or high speed frame rate (where
		   the latter may also include being in slow motion mode), depending on the current setting when
		   this settings fragment is launched. A limitation is that if the user changes the fps value
		   within the settings, this list won't update until the user exits and re-enters the settings.
		   This could be fixed by setting a setOnPreferenceChangeListener for the preference_video_fps
		   ListPreference and updating, but we must not assume that the preview will be non-null (since
		   if the application is being recreated, MyPreferenceFragment.onCreate() is called via
		   MainActivity.onCreate()->super.onCreate() before the preview is created! So we still need to
		   read the info via a bundle, and only update when fps changes if the preview is non-null.
		 */
        if (video_quality != null && video_quality_string != null) {
            CharSequence[] entries = new CharSequence[video_quality.length];
            CharSequence[] values = new CharSequence[video_quality.length];
            for (int i = 0; i < video_quality.length; i++) {
                entries[i] = video_quality_string[i];
                values[i] = video_quality[i];
            }
            ListPreference lp = (ListPreference) findPreference("preference_video_quality");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            String video_quality_preference_key = bundle.getString("video_quality_preference_key");
            if (MyDebug.LOG)
                Log.d(TAG, "video_quality_preference_key: " + video_quality_preference_key);
            String video_quality_value = sharedPreferences.getString(video_quality_preference_key, "");
            if (MyDebug.LOG) Log.d(TAG, "video_quality_value: " + video_quality_value);
            // set the key, so we save for the correct cameraId and high-speed setting
            // this must be done before setting the value (otherwise the video resolutions preference won't be
            // updated correctly when this is called from the callback when the user switches between
            // normal and high speed frame rates
            lp.setKey(video_quality_preference_key);
            lp.setValue(video_quality_value);

            boolean is_high_speed = bundle.getBoolean("video_is_high_speed");
            String title = is_high_speed ? getResources().getString(R.string.video_quality) + " [" + getResources().getString(R.string.high_speed) + "]" : getResources().getString(R.string.video_quality);
            lp.setTitle(title);
            lp.setDialogTitle(title);
        } else {
            Preference pref = findPreference("preference_video_quality");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (video_fps != null) {
            // build video fps settings
            CharSequence[] entries = new CharSequence[video_fps.length + 1];
            CharSequence[] values = new CharSequence[video_fps.length + 1];
            int i = 0;
            // default:
            entries[i] = getResources().getString(R.string.preference_video_fps_default);
            values[i] = "default";
            i++;
            final String high_speed_append = " [" + getResources().getString(R.string.high_speed) + "]";
            for (int k = 0; k < video_fps.length; k++) {
                int fps = video_fps[k];
                if (video_fps_high_speed != null && video_fps_high_speed[k]) {
                    entries[i] = fps + high_speed_append;
                } else {
                    entries[i] = String.valueOf(fps);
                }
                values[i] = String.valueOf(fps);
                i++;
            }

            ListPreference lp = (ListPreference) findPreference("preference_video_fps");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            lp.setValue(fps_value);
            // now set the key, so we save for the correct cameraId
            lp.setKey(fps_preference_key);
        }

        if (!supports_tonemap_curve && (camera_open || sharedPreferences.getString(PreferenceKeys.VideoLogPreferenceKey, "off").equals("off"))) {
            // if camera not open, we'll think this setting isn't supported - but should only remove
            // this preference if it's set to the default (otherwise if user sets to a non-default
            // value that causes camera to not open, user won't be able to put it back to the
            // default!)
            // (needed for Pixel 6 Pro where setting to sRGB causes camera to fail to open when in video mode)
            Preference pref = findPreference(PreferenceKeys.VideoLogPreferenceKey);
            //PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);

            pref = findPreference(PreferenceKeys.VideoProfileGammaPreferenceKey);
            //pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_video_stabilization) {
            Preference pref = findPreference("preference_video_stabilization");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

//        if( !supports_force_video_4k || video_quality == null ) {
//            Preference pref = findPreference("preference_force_video_4k");
//            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_video_debugging");
//            pg.removePreference(pref);
//        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            MyPreferenceFragment.filterArrayEntry((ListPreference) findPreference("preference_video_output_format"), "preference_video_output_format_mpeg4_hevc");
        }

        {
            ListPreference pref = (ListPreference) findPreference("preference_record_audio_src");

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                // some values require at least Android 7
                pref.setEntries(R.array.preference_record_audio_src_entries_preandroid7);
                pref.setEntryValues(R.array.preference_record_audio_src_values_preandroid7);
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (edge_to_edge_mode) {
            handleEdgeToEdge(view);
        }
    }

    static void handleEdgeToEdge(View view) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            //androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // don't need to avoid WindowInsetsCompat.Type.displayCutout(), as we already do this for the entire activity (see MainActivity's setOnApplyWindowInsetsListener)
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        view.requestApplyInsets();
    }

    /** Adds a TextView to an AlertDialog builder, placing it inside a scrollview and adding appropriate padding.
     */
    private void addTextViewForAlertDialog(AlertDialog.Builder alertDialog, TextView textView) {
        final float scale = getActivity().getResources().getDisplayMetrics().density;
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.addView(textView);
        // padding values from /sdk/platforms/android-18/data/res/layout/alert_dialog.xml
        textView.setPadding((int) (5 * scale + 0.5f), (int) (5 * scale + 0.5f), (int) (5 * scale + 0.5f), (int) (5 * scale + 0.5f));
        scrollView.setPadding((int) (14 * scale + 0.5f), (int) (2 * scale + 0.5f), (int) (10 * scale + 0.5f), (int) (12 * scale + 0.5f));
        alertDialog.setView(scrollView);
    }

    /** Programmatically set up dependencies for preference types (e.g., ListPreference) that don't
     *  support this in xml (such as SwitchPreference and CheckBoxPreference), or where this depends
     *  on the device (e.g., Android version).
     */
    private void setupDependencies() {
        final Preference prefReset = findPreference("preference_reset");
        prefReset.setOnPreferenceClickListener(arg0 -> {
            if (prefReset.getKey().equals("preference_reset")) {
                Log.d(TAG, "user clicked reset settings");
                MainActivity activity = (MainActivity) this.getActivity();
                activity.showResetSettingsDialog();
            }
            return false;
        });

        {
            ListPreference languagePref = (ListPreference) findPreference("pref_app_language");
            if (languagePref != null) {
                languagePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String lang = newValue.toString();
                    if (lang.equals("auto")) {
                        LocaleHelper.clearOverride(getActivity());
                    } else {
                        LocaleHelper.setLocale(getActivity(), lang);
                    }
                    getActivity().recreate();
                    return true;
                });
            }
        }
    }

    /* The user clicked the privacy policy preference.
     */
    public void clickedPrivacyPolicy() {
        if (MyDebug.LOG) Log.d(TAG, "clickedPrivacyPolicy()");
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
        alertDialog.setTitle(R.string.preference_privacy_policy);

        String privacy_policy_text = getActivity().getResources().getString(R.string.preference_privacy_policy_text);
        Spanned span = Html.fromHtml(privacy_policy_text);
        // clickable text is only supported if we call setMovementMethod on the TextView - which means we need to create
        // our own for the AlertDialog!
        @SuppressLint("InflateParams")
        // we add the view to the alert dialog in addTextViewForAlertDialog()
        final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_textview, null);
        final TextView textView = dialog_view.findViewById(R.id.text_view);
        textView.setText(span);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
        addTextViewForAlertDialog(alertDialog, textView);

        alertDialog.setPositiveButton(android.R.string.ok, null);
        alertDialog.setNegativeButton(R.string.preference_privacy_policy_online, (dialog, which) -> {
            if (MyDebug.LOG) Log.d(TAG, "online privacy policy");
            MainActivity main_activity = (MainActivity) MyPreferenceFragment.this.getActivity();
            main_activity.launchOnlinePrivacyPolicy();
        });
        final AlertDialog alert = alertDialog.create();
        alert.setOnDismissListener(arg0 -> {
            if (MyDebug.LOG) Log.d(TAG, "reset dialog dismissed");
            dialogs.remove(alert);
        });
        alert.show();
        dialogs.add(alert);
    }

    /** Removes an entry and value pair from a ListPreference, if it exists.
     * @param pref The ListPreference to remove the supplied entry/value.
     * @param filter_value The value to remove from the list.
     */
    static void filterArrayEntry(ListPreference pref, String filter_value) {
        {
            CharSequence[] orig_entries = pref.getEntries();
            CharSequence[] orig_values = pref.getEntryValues();
            List<CharSequence> new_entries = new ArrayList<>();
            List<CharSequence> new_values = new ArrayList<>();
            for (int i = 0; i < orig_entries.length; i++) {
                CharSequence value = orig_values[i];
                if (!value.equals(filter_value)) {
                    new_entries.add(orig_entries[i]);
                    new_values.add(value);
                }
            }
            CharSequence[] new_entries_arr = new CharSequence[new_entries.size()];
            new_entries.toArray(new_entries_arr);
            CharSequence[] new_values_arr = new CharSequence[new_values.size()];
            new_values.toArray(new_values_arr);
            pref.setEntries(new_entries_arr);
            pref.setEntryValues(new_values_arr);
        }
    }

    public static class SaveFolderChooserDialog extends FolderChooserDialog {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if (MyDebug.LOG) Log.d(TAG, "FolderChooserDialog dismissed");
            // n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
            // so we access the MainActivity via the fragment's getActivity().
            MainActivity main_activity = (MainActivity) this.getActivity();
            if (main_activity != null) { // main_activity may be null if this is being closed via MainActivity.onNewIntent()
                String new_save_location = this.getChosenFolder();
                main_activity.updateSaveFolder(new_save_location);
            }
            super.onDismiss(dialog);
        }
    }

    private void readFromBundle(String[] values, String[] entries, String preference_key, String default_value, String preference_category_key) {
        readFromBundle(this, values, entries, preference_key, default_value, preference_category_key);
    }

    static void readFromBundle(PreferenceFragment preference_fragment, String[] values, String[] entries, String preference_key, String default_value, String preference_category_key) {
        if (MyDebug.LOG) {
            Log.d(TAG, "readFromBundle");
        }
        if (values != null && values.length > 0) {
            if (MyDebug.LOG) {
                Log.d(TAG, "values:");
                for (String value : values) {
                    Log.d(TAG, value);
                }
            }
            Log.e(TAG, "preference_key: " + preference_key);
            ListPreference lp = (ListPreference) preference_fragment.findPreference(preference_key);
            lp.setEntries(entries);
            lp.setEntryValues(values);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(preference_fragment.getActivity());
            String value = sharedPreferences.getString(preference_key, default_value);
            Log.d(TAG, "    value: " + Arrays.toString(values));
            lp.setValue(value);
        } else {
            Log.d(TAG, "remove preference " + preference_key + " from category " + preference_category_key);
            Preference pref = preference_fragment.findPreference(preference_key);
            PreferenceGroup pg = (PreferenceGroup) preference_fragment.findPreference(preference_category_key);
            pg.removePreference(pref);
        }
    }

    static void setBackground(Fragment fragment) {
        // prevent fragment being transparent
        // note, setting color here only seems to affect the "main" preference fragment screen, and not sub-screens
        // note, on Galaxy Nexus Android 4.3 this sets to black rather than the dark grey that the background theme should be (and what the sub-screens use); works okay on Nexus 7 Android 5
        // we used to use a light theme for the PreferenceFragment, but mixing themes in same activity seems to cause problems (e.g., for EditTextPreference colors)
        TypedArray array = fragment.getActivity().getTheme().obtainStyledAttributes(new int[]{android.R.attr.colorBackground});
        int backgroundColor = array.getColor(0, Color.BLACK);
        fragment.getView().setBackgroundColor(backgroundColor);
        array.recycle();
    }

    public void onResume() {
        super.onResume();

        setBackground(this);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

        Log.d(TAG, "isRemoving?: " + isRemoving());

        if (isRemoving()) {
            // if isRemoving()==true, then it means the fragment is being removed and we are returning to the activity
            // if isRemoving()==false, then it may be that the activity is being destroyed
            ((MainActivity) getActivity()).settingsClosing();
        }

        dismissDialogs(getFragmentManager(), dialogs);
    }

    static void dismissDialogs(FragmentManager fragment_manager, HashSet<AlertDialog> dialogs) {
        // dismiss open dialogs - see comment for dialogs for why we do this
        for (AlertDialog dialog : dialogs) {
            Log.d(TAG, "dismiss dialog: " + dialog);
            dialog.dismiss();
        }
        // similarly dimiss any dialog fragments still opened
        Fragment folder_fragment = fragment_manager.findFragmentByTag("FOLDER_FRAGMENT");
        if (folder_fragment != null) {
            DialogFragment dialogFragment = (DialogFragment) folder_fragment;
            Log.d(TAG, "dismiss dialogFragment: " + dialogFragment);
            dialogFragment.dismissAllowingStateLoss();
        }
    }

    /* So that manual changes to the checkbox/switch preferences, while the preferences are showing, show up;
     * in particular, needed for preference_using_saf, when the user cancels the SAF dialog (see
     * MainActivity.onActivityResult).
     * Also programmatically sets summary (see setSummary).
     */
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (MyDebug.LOG) Log.d(TAG, "onSharedPreferenceChanged: " + key);

        if (key == null) {
            // On Android 11+, when targetting Android 11+, this method is called with key==null
            // if preferences are cleared. Unclear if this happens here in practice, but return
            // just in case.
            return;
        }

        Preference pref = findPreference(key);
        handleOnSharedPreferenceChanged(prefs, key, pref);
    }

    static void handleOnSharedPreferenceChanged(SharedPreferences prefs, String key, Preference pref) {
        Log.d(TAG, "handleOnSharedPreferenceChanged: " + key);

        if (pref == null) {
            // this can happen if the shared preference that changed is for a sub-screen i.e. a different fragment
            Log.d(TAG, "handleOnSharedPreferenceChanged: preference doesn't belong to this fragment");
            return;
        }

        if (pref instanceof TwoStatePreference) {
            TwoStatePreference twoStatePref = (TwoStatePreference) pref;
            twoStatePref.setChecked(prefs.getBoolean(key, true));
        } else if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            listPref.setValue(prefs.getString(key, ""));
        }
        setSummary(pref);
    }

    /** Programmatically sets summaries as required.
     *  Remember to call setSummary() from the constructor for any keys we set, to initialise the
     *  summary.
     */
    static void setSummary(Preference pref) {
        //noinspection DuplicateCondition
        if (pref instanceof EditTextPreference) {
            /* We have a runtime check for using EditTextPreference - we don't want these due to importance of
             * supporting the Google Play emoji policy (see comment in MyEditTextPreference.java) - and this
             * helps guard against the risk of accidentally adding more EditTextPreferences in future.
             * Once we've switched to using Android X Preference library, and hence safe to use EditTextPreference
             * again, this code can be removed.
             */
            throw new RuntimeException("detected an EditTextPreference: " + pref.getKey() + " pref: " + pref);
        }

        //noinspection DuplicateCondition
        if (pref instanceof EditTextPreference || pref instanceof MyEditTextPreference) {
            // %s only supported for ListPreference
            // we also display the usual summary if no preference value is set
            if (pref.getKey().equals("preference_exif_artist") || pref.getKey().equals("preference_exif_copyright") || pref.getKey().equals("preference_save_photo_prefix") || pref.getKey().equals("preference_save_video_prefix") || pref.getKey().equals("preference_textstamp")) {
                String default_value = "";
                if (pref.getKey().equals("preference_save_photo_prefix")) default_value = "IMG_";
                else if (pref.getKey().equals("preference_save_video_prefix"))
                    default_value = "VID_";

                String current_value;
                if (pref instanceof EditTextPreference) {
                    EditTextPreference editTextPref = (EditTextPreference) pref;
                    current_value = editTextPref.getText();
                } else {
                    MyEditTextPreference editTextPref = (MyEditTextPreference) pref;
                    current_value = editTextPref.getText();
                }

                if (current_value.equals(default_value)) {
                    switch (pref.getKey()) {
                        case "preference_exif_artist":
                            pref.setSummary(R.string.preference_exif_artist_summary);
                            break;
                        case "preference_exif_copyright":
                            pref.setSummary(R.string.preference_exif_copyright_summary);
                            break;
                        case "preference_save_photo_prefix":
                            pref.setSummary(R.string.preference_save_photo_prefix_summary);
                            break;
                        case "preference_save_video_prefix":
                            pref.setSummary(R.string.preference_save_video_prefix_summary);
                            break;
                        case "preference_textstamp":
                            pref.setSummary(R.string.preference_textstamp_summary);
                            break;
                    }
                } else {
                    // non-default value, so display the current value
                    pref.setSummary(current_value);
                }
            }
        }
    }
}
