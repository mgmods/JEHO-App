package com.Dramizo.Series.presentation.createroom;

import android.content.Intent;
import android.os.Bundle;

import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.main.MainActivity;

/**
 * Compatibility entry that opens MainActivity's Create Room tab.
 */
public class CreateRoomActivity extends ThemedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_OPEN_CREATE_ROOM, true);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
