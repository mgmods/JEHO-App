package com.Dramizo.Series.presentation.cp;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.Dramizo.Series.presentation.common.ThemedActivity;

/** Compatibility entry point: all CP discovery/categories are rendered in the native Android CP center. */
public class CpDiscoveryActivity extends ThemedActivity {
    public static final String EXTRA_CP_TYPE = "cp_type";

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        String type = getIntent().getStringExtra(EXTRA_CP_TYPE);
        Intent intent = new Intent(this, CpCenterActivity.class);
        intent.putExtra(CpCenterActivity.EXTRA_VIEW, type == null || type.trim().isEmpty() ? "discover" : "discover");
        intent.putExtra(CpCenterActivity.EXTRA_TYPE, type == null ? "" : type);
        startActivity(intent);
        finish();
    }
}