package com.ssolstice.camera.manual;

import static com.ssolstice.camera.manual.PreferenceKeys.PreferenceKey_Root;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.ssolstice.camera.manual.ui.MyEditTextPreference;
import com.ssolstice.camera.manual.utils.Logger;

import java.util.Locale;

public class PreferenceSubCameraControlsMore extends PreferenceSubScreen {
    private static final String TAG = "PfSubCameraControlsMore";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.INSTANCE.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_camera_controls_more);

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        {
            final Preference pref = findPreference("preference_using_saf");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if (pref.getKey().equals("preference_using_saf")) {
                        Logger.INSTANCE.d(TAG, "user clicked saf");
                        if (sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false)) {
                            Logger.INSTANCE.d(TAG, "saf is now enabled");
                            // seems better to alway re-show the dialog when the user selects, to make it clear where files will be saved (as the SAF location in general will be different to the non-SAF one)
                            //String uri = sharedPreferences.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "");
                            //if( uri.length() == 0 )
                            {
                                MainActivity main_activity = (MainActivity) PreferenceSubCameraControlsMore.this.getActivity();
                                Toast.makeText(main_activity, R.string.saf_select_save_location, Toast.LENGTH_SHORT).show();
                                main_activity.openFolderChooserDialogSAF(true);
                            }
                        } else {
                            Logger.INSTANCE.d(TAG, "saf is now disabled");
                        }
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_calibrate_level");
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if (pref.getKey().equals("preference_calibrate_level")) {
                        Logger.INSTANCE.d(TAG, "user clicked calibrate level option");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(PreferenceSubCameraControlsMore.this.getActivity());
                        alertDialog.setTitle(getActivity().getResources().getString(R.string.preference_calibrate_level));
                        alertDialog.setMessage(R.string.preference_calibrate_level_dialog);
                        alertDialog.setPositiveButton(R.string.preference_calibrate_level_calibrate, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Logger.INSTANCE.d(TAG, "user clicked calibrate level");
                                MainActivity main_activity = (MainActivity) PreferenceSubCameraControlsMore.this.getActivity();
                                if (main_activity.getPreview().hasLevelAngleStable()) {
                                    double current_level_angle = main_activity.getPreview().getLevelAngleUncalibrated();
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, (float) current_level_angle);
                                    editor.apply();
                                    main_activity.getPreview().updateLevelAngles();
                                    Toast.makeText(main_activity, R.string.preference_calibrate_level_calibrated, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        alertDialog.setNegativeButton(R.string.preference_calibrate_level_reset, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Logger.INSTANCE.d(TAG, "user clicked reset calibration level");
                                MainActivity main_activity = (MainActivity) PreferenceSubCameraControlsMore.this.getActivity();
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, 0.0f);
                                editor.apply();
                                main_activity.getPreview().updateLevelAngles();
                                Toast.makeText(main_activity, R.string.preference_calibrate_level_calibration_reset, Toast.LENGTH_SHORT).show();
                            }
                        });
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                Logger.INSTANCE.d(TAG, "calibration dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        return false;
                    }
                    return false;
                }
            });
        }

        MyPreferenceFragment.setSummary(findPreference("preference_save_photo_prefix"));
        MyPreferenceFragment.setSummary(findPreference("preference_save_video_prefix"));

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
        // set up dependency for preference_audio_noise_control_sensitivity on preference_audio_control
        ListPreference pref = (ListPreference) findPreference("preference_audio_control");
        if (pref != null) { // may be null if preference not supported
            pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    String value = newValue.toString();
                    setAudioNoiseControlSensitivityDependency(value);
                    return true;
                }
            });
            setAudioNoiseControlSensitivityDependency(pref.getValue()); // ensure dependency is enabled/disabled as required for initial value
        }
    }

    private void setAudioNoiseControlSensitivityDependency(String newValue) {
        Preference dependent = findPreference("preference_audio_noise_control_sensitivity");
        if (dependent != null) { // just in case
            boolean enable_dependent = "noise".equals(newValue);
            Logger.INSTANCE.d(TAG, "clicked audio control: " + newValue + " enable_dependent: " + enable_dependent);
            dependent.setEnabled(enable_dependent);
        }
    }

}
