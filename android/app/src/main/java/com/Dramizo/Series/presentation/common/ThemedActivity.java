package com.Dramizo.Series.presentation.common;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.util.LocaleHelper;
import com.Dramizo.Series.util.RemoteTheme;

/**
 * Base activity: clean white theme with light system bars.
 * Back from a cold-start (notification / deep link) returns to Main instead of leaving the app.
 */
public abstract class ThemedActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = "ar";
        try {
            lang = newBase.getSharedPreferences("auralive_lang", MODE_PRIVATE)
                    .getString("language", "ar");
        } catch (Exception ignored) {
        }
        super.attachBaseContext(LocaleHelper.wrap(newBase, lang));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Never follow device auto-rotate — keep the whole app portrait.
        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } catch (Exception ignored) {
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (wantsEdgeToEdgeWallpaper()) {
            EdgeToEdgeHelper.apply(this);
        }

        if (wantsRemoteThemeChrome()) {
            RemoteTheme.applyActivityBackground(this, inferScreenKey());
            View content = findViewById(android.R.id.content);
            RemoteTheme.applyMappedAssets(content != null ? content : getWindow().getDecorView());
            // applyChrome must never override wallpaper-derived system bars.
            RemoteTheme.applyChrome(this);
        }

        if (wantsEdgeToEdgeWallpaper() && wantsContentSystemPadding()) {
            View padTarget = findContentColumnForInsets();
            if (padTarget != null) {
                // Status + nav — prevents content under Android 15/16 gesture/nav bar.
                EdgeToEdgeHelper.padSystemBarsWithIme(padTarget);
            }
        }

        if (wantsHomeOnRootBack()) {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    navigateUp();
                }
            });
        }
    }

    /**
     * Prefer this for toolbar back buttons. If this screen is the only one in the task
     * (opened from a notification), go to {@link MainActivity} instead of exiting the app.
     */
    public void navigateUp() {
        if (isFinishing()) return;
        if (!isTaskRoot() || !wantsHomeOnRootBack()) {
            finish();
            return;
        }
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Open the relevant main tab when possible.
        applyHomeTabExtra(home);
        startActivity(home);
        finish();
    }

    /** Root shells (login / splash / main) should leave the app on back. */
    protected boolean wantsHomeOnRootBack() {
        String name = getClass().getSimpleName();
        return !containsAny(name,
                "Main", "Splash", "Login", "Guest", "Register", "Otp",
                "ProfileSetup", "FemaleIdentity", "GenderLiveness", "VoiceRoom", "GamePlay");
    }

    protected void applyHomeTabExtra(Intent home) {
        String name = getClass().getSimpleName();
        if (containsAny(name, "Chat", "Messages", "Friends", "Requests",
                "Notifications", "OfficialNews")) {
            home.putExtra(MainActivity.EXTRA_OPEN_MESSAGES, true);
        } else if (containsAny(name, "Drama")) {
            home.putExtra(MainActivity.EXTRA_OPEN_DRAMA, true);
        } else {
            home.putExtra(MainActivity.EXTRA_OPEN_HOME, true);
        }
    }

    /** Transparent status bar over white content. */
    protected boolean wantsEdgeToEdgeWallpaper() {
        String name = getClass().getSimpleName();
        return !containsAny(name, "VoiceRoom", "DramaPlayer", "GenderLiveness", "Splash", "GamePlay");
    }

    /** Game hosts paint their own black immersive chrome. */
    protected boolean wantsRemoteThemeChrome() {
        String name = getClass().getSimpleName();
        return !containsAny(name, "GamePlay", "VoiceRoom", "DramaPlayer", "Splash");
    }

    /**
     * Pad the content column (not the wallpaper ImageView).
     * Main/chat manage their own insets.
     */
    protected boolean wantsContentSystemPadding() {
        String name = getClass().getSimpleName();
        return !containsAny(name, "Main", "VoiceRoom", "ChatConversation",
                "DramaPlayer", "GenderLiveness", "Splash", "Login", "GamePlay");
    }

    @Nullable
    private View findContentColumnForInsets() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return null;
        ViewGroup host = (ViewGroup) content;
        if (host.getChildCount() == 0) return null;
        View root = host.getChildAt(0);
        if (!(root instanceof ViewGroup)) return root;

        ViewGroup group = (ViewGroup) root;
        // Prefer explicit content root (wallpaper layouts keep bg full-bleed).
        View named = group.findViewById(R.id.contentRoot);
        if (named != null) return named;

        // Vertical/horizontal LinearLayout shells: pad the whole root — not the first
        // child (often a fixed-height toolbar whose padding would stay under the status bar).
        if (group instanceof android.widget.LinearLayout) {
            return root;
        }

        // FrameLayout pattern: full-bleed ImageView bg + content column.
        if (group instanceof android.widget.FrameLayout || group instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            boolean hasFullBleedBg = false;
            View nonBg = null;
            int nonBgCount = 0;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                int id = child.getId();
                if (id == R.id.imgThemeBg || id == R.id.imgHomeBg || id == R.id.imgRoomBg) {
                    hasFullBleedBg = true;
                    continue;
                }
                if (child instanceof ImageView
                        && child.getLayoutParams() != null
                        && child.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT
                        && child.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    hasFullBleedBg = true;
                    continue;
                }
                nonBg = child;
                nonBgCount++;
            }
            // Wallpaper + single content column → pad the column only.
            if (hasFullBleedBg && nonBgCount == 1 && nonBg != null) {
                return nonBg;
            }
            // ConstraintLayout / single-root screens (medals, lists, …): pad whole root
            // so toolbar and list clear status + nav bars together.
            return root;
        }

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            int id = child.getId();
            if (id == R.id.imgThemeBg || id == R.id.imgHomeBg || id == R.id.imgRoomBg) {
                continue;
            }
            if (child instanceof ImageView
                    && child.getLayoutParams() != null
                    && child.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT
                    && child.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {
                continue;
            }
            return child;
        }
        return root;
    }

    protected String inferScreenKey() {
        String name = getClass().getSimpleName();

        if (name.contains("Splash")) return "splash";
        if (containsAny(name, "Login", "Guest", "Register", "Otp", "ProfileSetup")) return "auth";
        if (containsAny(name, "Profile", "EditProfile", "UserLevel", "Visitors", "Task", "GiftHistory")) {
            return "profile";
        }
        if (containsAny(name, "Chat", "Messages", "Friends", "Requests", "Notifications", "Search")) {
            return "chat";
        }
        if (containsAny(name, "Game", "Contest", "Ranking")) return "games";
        if (name.contains("CreateRoom")) return "createRoom";
        if (name.contains("VoiceRoom")) return "voiceRoom";
        if (containsAny(name, "Wallet", "Bag", "Earnings", "Recharge")) return "app";
        return "app";
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }
}
