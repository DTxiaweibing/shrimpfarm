package com.shrimpfarm.app.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class StoragePermissionHelper {

    public static final int REQUEST_CODE_STORAGE = 200;

    public static boolean hasStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 29) return true;
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean requestIfNeeded(Activity activity) {
        if (hasStoragePermission(activity)) return true;
        ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_CODE_STORAGE);
        return false;
    }
}
