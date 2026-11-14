package com.Dramizo.Series.presentation.profile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.home.MomentsActivity;

public final class ProfileDetailsActivity extends Activity {
    public static final String EXTRA_USER_ID = "extra_user_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_details);

        String userId = getIntent().getStringExtra(EXTRA_USER_ID);

        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());

        View moments = findViewById(R.id.cardMoments);
        if (moments != null) {
            moments.setOnClickListener(v -> {
                if (userId == null || userId.trim().isEmpty()) return;
                Intent intent = new Intent(this, MomentsActivity.class);
                intent.putExtra(MomentsActivity.EXTRA_USER_ID, userId);
                startActivity(intent);
            });
        }

        View gifts = findViewById(R.id.cardGiftHistory);
        if (gifts != null) {
            gifts.setOnClickListener(v -> {
                Intent intent = new Intent(this, GiftHistoryActivity.class);
                intent.putExtra(GiftHistoryActivity.EXTRA_USER_ID, userId);
                startActivity(intent);
            });
        }
    }
}