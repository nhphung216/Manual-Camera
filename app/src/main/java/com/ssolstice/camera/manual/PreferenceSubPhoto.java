package com.ssolstice.camera.manual;

import static com.ssolstice.camera.manual.PreferenceKeys.PreferenceKey_Root;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.util.Log;

import com.ssolstice.camera.manual.ui.MyEditTextPreference;

import java.util.Locale;

public class PreferenceSubPhoto extends PreferenceSubScreen {

    private static final String TAG = "PreferenceSubPhoto";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_photo);

        final Bundle bundle = getArguments();
        final boolean using_android_l = bundle.getBoolean("using_android_l");
        final boolean supports_raw = bundle.getBoolean("supports_raw");
        final boolean supports_burst_raw = bundle.getBoolean("supports_burst_raw");
        final boolean supports_optimise_focus_latency = bundle.getBoolean("supports_optimise_focus_latency");
        final boolean supports_preshots = bundle.getBoolean("supports_preshots");
        final boolean supports_nr = bundle.getBoolean("supports_nr");
        final boolean supports_hdr = bundle.getBoolean("supports_hdr");
        final boolean supports_panorama = bundle.getBoolean("supports_panorama");
        final boolean supports_photo_video_recording = bundle.getBoolean("supports_photo_video_recording");

        if (!(supports_raw && supports_burst_raw)) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            Preference pref = findPreference("preference_raw_expo_bracketing");
            pg.removePreference(pref);
            pref = findPreference("preference_raw_focus_bracketing");
            pg.removePreference(pref);
        }

        if (!supports_optimise_focus_latency) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            Preference pref = findPreference("preference_photo_optimise_focus");
            pg.removePreference(pref);
        }

        if (!supports_preshots) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            Preference pref = findPreference("preference_save_preshots");
            pg.removePreference(pref);
        }

        if (!supports_nr) {
            Preference pref = findPreference("preference_nr_save");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        if (!supports_hdr) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);

            Preference pref = findPreference("preference_hdr_save_expo");
            pg.removePreference(pref);

            pref = findPreference("preference_hdr_tonemapping");
            pg.removePreference(pref);

            pref = findPreference("preference_hdr_contrast_enhancement");
            pg.removePreference(pref);
        }

        if (!supports_panorama) {
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);

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
                PreferenceGroup parent = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
                parent.removePreference(pg);
            }
        }

        MyPreferenceFragment.setSummary(findPreference("preference_exif_artist"));
        MyPreferenceFragment.setSummary(findPreference("preference_exif_copyright"));
        MyPreferenceFragment.setSummary(findPreference("preference_textstamp"));

        updatePreferenceSummaries(getPreferenceScreen(), PreferenceManager.getDefaultSharedPreferences(getActivity()));

        PreferenceGroup preferenceGroup = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
        for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
            Preference pref = preferenceGroup.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                pref.setTitle(pref.getTitle().toString().toUpperCase(Locale.ROOT));
            }
        }

        Log.d(TAG, "onCreate done");
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
}
