package com.Dramizo.Series.presentation.friends;

import android.content.Intent;
import android.os.Bundle;

import com.Dramizo.Series.presentation.common.ThemedActivity;

/**
 * Legacy entry — redirects into {@link FriendsActivity} hub tabs for requests.
 * Prefer opening {@link FriendsActivity} with {@code EXTRA_TAB} directly.
 */
public class RequestsActivity extends ThemedActivity {
    public static final String EXTRA_TAB = "tab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int requestTab = Math.min(Math.max(getIntent().getIntExtra(EXTRA_TAB, 0), 0), 2);
        Intent i = new Intent(this, FriendsActivity.class);
        i.putExtra(FriendsActivity.EXTRA_TAB, FriendsActivity.TAB_REQ_FRIEND + requestTab);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
