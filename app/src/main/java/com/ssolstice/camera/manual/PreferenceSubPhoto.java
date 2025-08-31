package com.ssolstice.camera.manual;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;

public class PreferenceSubPhoto extends PreferenceSubScreen {

    private static final String TAG = "PreferenceSubPhoto";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (MyDebug.LOG)
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_photo);

        final Bundle bundle = getArguments();

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final int cameraId = bundle.getInt("cameraId");
        if (MyDebug.LOG) Log.d(TAG, "cameraId: " + cameraId);

        final String cameraIdSPhysical = bundle.getString("cameraIdSPhysical");
        if (MyDebug.LOG) Log.d(TAG, "cameraIdSPhysical: " + cameraIdSPhysical);

        final boolean using_android_l = bundle.getBoolean("using_android_l");
        if (MyDebug.LOG) Log.d(TAG, "using_android_l: " + using_android_l);

        final boolean supports_jpeg_r = bundle.getBoolean("supports_jpeg_r");
        if (MyDebug.LOG) Log.d(TAG, "supports_jpeg_r: " + supports_jpeg_r);

        final boolean supports_raw = bundle.getBoolean("supports_raw");
        if (MyDebug.LOG) Log.d(TAG, "supports_raw: " + supports_raw);

        final boolean supports_burst_raw = bundle.getBoolean("supports_burst_raw");
        if (MyDebug.LOG) Log.d(TAG, "supports_burst_raw: " + supports_burst_raw);

        final boolean supports_optimise_focus_latency = bundle.getBoolean("supports_optimise_focus_latency");
        if (MyDebug.LOG)
            Log.d(TAG, "supports_optimise_focus_latency: " + supports_optimise_focus_latency);

        final boolean supports_preshots = bundle.getBoolean("supports_preshots");
        if (MyDebug.LOG) Log.d(TAG, "supports_preshots: " + supports_preshots);

        final boolean supports_nr = bundle.getBoolean("supports_nr");
        if (MyDebug.LOG) Log.d(TAG, "supports_nr: " + supports_nr);

        final boolean supports_hdr = bundle.getBoolean("supports_hdr");
        if (MyDebug.LOG) Log.d(TAG, "supports_hdr: " + supports_hdr);

        final boolean supports_expo_bracketing = bundle.getBoolean("supports_expo_bracketing");
        if (MyDebug.LOG) Log.d(TAG, "supports_expo_bracketing: " + supports_expo_bracketing);

        final int max_expo_bracketing_n_images = bundle.getInt("max_expo_bracketing_n_images");
        if (MyDebug.LOG)
            Log.d(TAG, "max_expo_bracketing_n_images: " + max_expo_bracketing_n_images);

        final boolean supports_panorama = bundle.getBoolean("supports_panorama");
        if (MyDebug.LOG) Log.d(TAG, "supports_panorama: " + supports_panorama);

        final boolean supports_photo_video_recording = bundle.getBoolean("supports_photo_video_recording");
        if (MyDebug.LOG)
            Log.d(TAG, "supports_photo_video_recording: " + supports_photo_video_recording);

        if (!(supports_raw && supports_burst_raw)) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");
            Preference pref = findPreference("preference_raw_expo_bracketing");
            pg.removePreference(pref);
            pref = findPreference("preference_raw_focus_bracketing");
            pg.removePreference(pref);
        }

        if (!supports_optimise_focus_latency) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");
            Preference pref = findPreference("preference_photo_optimise_focus");
            pg.removePreference(pref);
        }

        if (!supports_preshots) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");
            Preference pref = findPreference("preference_save_preshots");
            pg.removePreference(pref);
        }

        if (!supports_nr) {
            Preference pref = findPreference("preference_nr_save");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if (!supports_hdr) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");

            Preference pref = findPreference("preference_hdr_save_expo");
            pg.removePreference(pref);

            pref = findPreference("preference_hdr_tonemapping");
            pg.removePreference(pref);

            pref = findPreference("preference_hdr_contrast_enhancement");
            pg.removePreference(pref);
        }

        if (!supports_expo_bracketing || max_expo_bracketing_n_images <= 3) {
            Preference pref = findPreference("preference_expo_bracketing_n_images");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");
            pg.removePreference(pref);
        }
        if (!supports_expo_bracketing) {
            Preference pref = findPreference("preference_expo_bracketing_stops");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");
            pg.removePreference(pref);
        }

        if (!supports_panorama) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preferences_root");

            Preference pref = findPreference("preference_panorama_crop");
            pg.removePreference(pref);

            pref = findPreference("preference_panorama_save");
            pg.removePreference(pref);
        }

        if (!using_android_l) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_photo_debugging");

            Preference pref = findPreference("preference_camera2_fake_flash");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_dummy_capture_hack");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_fast_burst");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_photo_video_recording");
            pg.removePreference(pref);
        } else {
            if (!supports_photo_video_recording) {
                Preference pref = findPreference("preference_camera2_photo_video_recording");
                PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_photo_debugging");
                pg.removePreference(pref);
            }
        }

        {
            // remove preference_category_photo_debugging category if empty (which will be the case for old api)
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_category_photo_debugging");
            if (MyDebug.LOG)
                Log.d(TAG, "preference_category_photo_debugging children: " + pg.getPreferenceCount());
            if (pg.getPreferenceCount() == 0) {
                PreferenceGroup parent = (PreferenceGroup) this.findPreference("preferences_root");
                parent.removePreference(pg);
            }
        }

        MyPreferenceFragment.setSummary(findPreference("preference_exif_artist"));
        MyPreferenceFragment.setSummary(findPreference("preference_exif_copyright"));
        MyPreferenceFragment.setSummary(findPreference("preference_textstamp"));

        if (MyDebug.LOG)
            Log.d(TAG, "onCreate done");
    }
}
