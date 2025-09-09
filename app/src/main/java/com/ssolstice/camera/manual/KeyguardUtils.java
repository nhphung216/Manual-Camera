package com.ssolstice.camera.manual;

import static android.content.Context.KEYGUARD_SERVICE;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;

import com.ssolstice.camera.manual.utils.Logger;

public class KeyguardUtils {
    private static final String TAG = "KeyguardUtils";

    public static void requireKeyguard(Activity activity, Runnable callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            KeyguardManager keyguardManager = (KeyguardManager) activity.getSystemService(KEYGUARD_SERVICE);
            if (keyguardManager == null || !keyguardManager.isKeyguardLocked()) {
                callback.run();
                return;
            }
            keyguardManager.requestDismissKeyguard(activity, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    Logger.INSTANCE.d(TAG, "onDismissSucceeded");
                    callback.run();
                    Logger.INSTANCE.d(TAG, "onDismissSucceeded: after callback run");
                }
            });
        } else {
            callback.run();
        }
    }
}
