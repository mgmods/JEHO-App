package com.Dramizo.Series.presentation.profile;

import android.graphics.Bitmap;

/** In-memory result from {@link GenderLivenessCameraActivity}. */
public final class GenderLivenessResult {
    public Bitmap selfieBitmap;
    public boolean livenessPassed;
    public float livenessScore;
    public float yawCenter;
    public float yawLeft;
    public float yawRight;
    public boolean blinkPassed;
    public boolean faceTrackingStable;
    public float femaleConfidence;

    private static GenderLivenessResult pending;

    public static void stash(GenderLivenessResult result) {
        pending = result;
    }

    public static GenderLivenessResult take() {
        GenderLivenessResult r = pending;
        pending = null;
        return r;
    }
}
