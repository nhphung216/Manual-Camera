package com.ssolstice.camera.manual;

import android.os.Bundle;

import com.ssolstice.camera.manual.utils.Logger;

public class PreferenceSubRemoteCtrl extends PreferenceSubScreen {
    private static final String TAG = "PreferenceSubRemoteCtrl";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.INSTANCE.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_sub_remote_ctrl);
        Logger.INSTANCE.d(TAG, "onCreate done");
    }
}
