package com.ssolstice.camera.manual;

import static com.ssolstice.camera.manual.PreferenceKeys.PreferenceKey_Root;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;

import com.ssolstice.camera.manual.ui.MyEditTextPreference;
import com.ssolstice.camera.manual.utils.Logger;

import java.util.Locale;

public class PreferenceSubVideo extends PreferenceSubScreen {

    private static final String TAG = "PreferenceSubVideo";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.INSTANCE.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_video);
        setupDependencies();

        //updatePreferenceSummaries(getPreferenceScreen(), PreferenceManager.getDefaultSharedPreferences(getActivity()));

        PreferenceGroup preferenceGroup = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
        for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
            Preference pref = preferenceGroup.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                pref.setTitle(pref.getTitle().toString().toUpperCase(Locale.ROOT));
            }
        }

        Logger.INSTANCE.d(TAG, "onCreate done");
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

    /** Programmatically set up dependencies for preference types (e.g., ListPreference) that don't
     *  support this in xml (such as SwitchPreference and CheckBoxPreference), or where this depends
     *  on the device (e.g., Android version).
     */
    private void setupDependencies() {
        // set up dependency for preference_video_profile_gamma on preference_video_log
        ListPreference pref = (ListPreference) findPreference("preference_video_log");
        if (pref != null) { // may be null if preference not supported
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    String value = newValue.toString();
                    setVideoProfileGammaDependency(value);
                    return true;
                }
            });
            setVideoProfileGammaDependency(pref.getValue()); // ensure dependency is enabled/disabled as required for initial value
        }

        if (!MyApplicationInterface.mediastoreSupportsVideoSubtitles()) {
            // video subtitles only supported with SAF on Android 11+
            // since these preferences are entirely in separate sub-screens (and one isn't the parent of the other), we don't need
            // a dependency (and indeed can't use one, as the preference_using_saf won't exist here as a Preference)
            pref = (ListPreference) findPreference("preference_video_subtitle");
            if (pref != null) {
                boolean using_saf = false;
                // n.b., not safe to call main_activity.getApplicationInterface().getStorageUtils().isUsingSAF() if fragment
                // is being recreated
                {
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
                    if (sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false)) {
                        using_saf = true;
                    }
                }
                Logger.INSTANCE.d(TAG, "using_saf: " + using_saf);

                //pref.setDependency("preference_using_saf");
                if (using_saf) {
                    pref.setEnabled(true);
                } else {
                    pref.setEnabled(false);
                }
            }
        }
    }

    private void setVideoProfileGammaDependency(String newValue) {
        Preference dependent = findPreference("preference_video_profile_gamma");
        if (dependent != null) { // just in case
            boolean enable_dependent = "gamma".equals(newValue);
            Logger.INSTANCE.d(TAG, "clicked video log: " + newValue + " enable_dependent: " + enable_dependent);
            dependent.setEnabled(enable_dependent);
        }
    }
}
