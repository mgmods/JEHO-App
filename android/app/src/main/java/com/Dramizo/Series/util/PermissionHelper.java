package com.Dramizo.Series.util;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class PermissionHelper {
    public static final int REQ_MEDIA = 9101;
    public static final int REQ_DEVICE_MUSIC = 9102;

    private PermissionHelper() {}

    public static boolean ensureMediaPermissions(Activity activity, boolean needCamera) {
        List<String> needed = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO);
        }
        if (needCamera && ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA);
        }
        if (needed.isEmpty()) return true;
        ActivityCompat.requestPermissions(activity, needed.toArray(new String[0]), REQ_MEDIA);
        return false;
    }

    public static boolean hasAudioPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasCameraPermission(Activity activity) {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Read device songs (Android 13+ READ_MEDIA_AUDIO, older READ_EXTERNAL_STORAGE). */
    public static boolean hasDeviceMusicPermission(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** @return true if already granted; false if a request was started. */
    public static boolean ensureDeviceMusicPermission(Activity activity) {
        if (hasDeviceMusicPermission(activity)) return true;
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQ_DEVICE_MUSIC);
        } else {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_DEVICE_MUSIC);
        }
        return false;
    }

    public static String[] deviceMusicPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_AUDIO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }
}
