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

import com.ssolstice.camera.manual.ui.ArraySeekBarPreference;
import com.ssolstice.camera.manual.ui.MyEditTextPreference;

import java.util.Locale;

public class PreferenceSubPreview extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubPreview";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (MyDebug.LOG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_preview);

        final Bundle bundle = getArguments();

        final boolean using_android_l = bundle.getBoolean("using_android_l");
        if (MyDebug.LOG) Log.d(TAG, "using_android_l: " + using_android_l);

        {
            ListPreference pref = (ListPreference) findPreference("preference_ghost_image");
            pref.setOnPreferenceChangeListener((arg0, newValue) -> {
                if (MyDebug.LOG) Log.d(TAG, "clicked ghost image: " + newValue);
                if (newValue.equals("preference_ghost_image_selected")) {
                    MainActivity main_activity = (MainActivity) PreferenceSubPreview.this.getActivity();
                    main_activity.openGhostImageChooserDialogSAF(true);
                }
                return true;
            });
        }

        {
            final int max_ghost_image_alpha = 80; // limit max to 80% for privacy reasons, so it isn't possible to put in a state where camera is on, but no preview is shown
            final int ghost_image_alpha_step = 5; // should be exact divisor of max_ghost_image_alpha
            final int n_ghost_image_alpha = max_ghost_image_alpha / ghost_image_alpha_step;
            CharSequence[] entries = new CharSequence[n_ghost_image_alpha];
            CharSequence[] values = new CharSequence[n_ghost_image_alpha];
            for (int i = 0; i < n_ghost_image_alpha; i++) {
                int alpha = ghost_image_alpha_step * (i + 1);
                entries[i] = alpha + "%";
                values[i] = String.valueOf(alpha);
            }
            ArraySeekBarPreference sp = (ArraySeekBarPreference) findPreference("ghost_image_alpha");
            sp.setEntries(entries);
            sp.setEntryValues(values);
        }

        if (!using_android_l) {
            Preference pref = findPreference("preference_focus_assist");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
            pg.removePreference(pref);
        }

        updatePreferenceSummaries(getPreferenceScreen(), PreferenceManager.getDefaultSharedPreferences(getActivity()));

        PreferenceGroup preferenceGroup = (PreferenceGroup) this.findPreference(PreferenceKey_Root);
        for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
            Preference pref = preferenceGroup.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                pref.setTitle(pref.getTitle().toString().toUpperCase(Locale.ROOT));
            }
        }

        if (MyDebug.LOG) Log.d(TAG, "onCreate done");
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
