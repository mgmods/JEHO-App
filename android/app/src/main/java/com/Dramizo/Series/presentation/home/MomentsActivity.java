package com.Dramizo.Series.presentation.home;

import android.os.Bundle;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.Dramizo.Series.presentation.common.ThemedActivity;

/** Hosts the profile-specific moments section without replacing the profile screen. */
public class MomentsActivity extends ThemedActivity {
    public static final String EXTRA_USER_ID = "moments_user_id";
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout frame = new FrameLayout(this);
        frame.setId(android.view.View.generateViewId());
        setContentView(frame);
        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        Fragment fragment = userId == null || userId.trim().isEmpty()
                ? HomeMomentsFragment.newInstance()
                : HomeMomentsFragment.forProfile(userId);
        getSupportFragmentManager().beginTransaction().replace(frame.getId(), fragment).commit();
    }
}
