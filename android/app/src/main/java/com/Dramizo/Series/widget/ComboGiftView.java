package com.Dramizo.Series.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.bumptech.glide.Glide;

/**
 * Gift send toast: enters from one side above the chat lane, crawls slowly,
 * then exits to the opposite side. Gift icon must always be visible.
 */
public class ComboGiftView extends FrameLayout {
    private ImageView bg;
    private ImageView avatar;
    private ImageView giftIcon;
    private TextView sender;
    private TextView recvDesc;
    private TextView comboNum;
    private boolean showing;
    @Nullable private Runnable hideRunnable;
    @Nullable private Runnable crawlRunnable;

    public ComboGiftView(Context context) {
        super(context);
        init(context);
    }

    public ComboGiftView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ComboGiftView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.item_combo_gift_layout, this, true);
        bg = findViewById(R.id.vComboAnimBg);
        avatar = findViewById(R.id.iv_avatar);
        giftIcon = findViewById(R.id.iv_gift);
        sender = findViewById(R.id.tvSenderNick);
        recvDesc = findViewById(R.id.tvRecvDesc);
        comboNum = findViewById(R.id.tvComboNum);
        if (comboNum != null) comboNum.setText("1");
        if (giftIcon != null) {
            giftIcon.setElevation(dp(6));
            giftIcon.bringToFront();
        }
        setVisibility(GONE);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void show(@Nullable String senderName,
                     @Nullable String receiverLabel,
                     @Nullable String senderAvatarUrl,
                     @Nullable String giftIconUrl,
                     int comboCount,
                     int totalCoins) {
        if (sender != null) {
            sender.setText(senderName != null && !senderName.isEmpty() ? senderName : "مستخدم");
        }
        if (recvDesc != null) {
            String label = receiverLabel != null && !receiverLabel.isEmpty()
                    ? receiverLabel
                    : getContext().getString(R.string.send);
            recvDesc.setText(label);
            recvDesc.setSelected(true);
        }
        if (comboNum != null) {
            comboNum.setText(String.valueOf(Math.max(1, comboCount)));
        }
        if (bg != null) {
            bg.setImageResource(comboBgForCombo(comboCount, totalCoins));
        }
        if (avatar != null) {
            AvatarCosmetics.bindAvatar(avatar, senderAvatarUrl);
        }
        bindGiftIcon(giftIconUrl);
        playCrawl();
    }

    private void bindGiftIcon(@Nullable String giftIconUrl) {
        if (giftIcon == null) return;
        giftIcon.setVisibility(VISIBLE);
        giftIcon.setAlpha(1f);
        giftIcon.setImageResource(R.drawable.ic_asset_gift);
        String url = AssetCatalog.absoluteUrl(giftIconUrl);
        if (url == null || url.isEmpty()) {
            url = giftIconUrl != null ? giftIconUrl.trim() : null;
        }
        if (url == null || url.isEmpty()) return;
        try {
            Glide.with(giftIcon.getContext().getApplicationContext())
                    .load(url)
                    .placeholder(R.drawable.ic_asset_gift)
                    .error(R.drawable.ic_asset_gift)
                    .fitCenter()
                    .into(giftIcon);
        } catch (Exception e) {
            giftIcon.setImageResource(R.drawable.ic_asset_gift);
        }
    }

    /**
     * Enter from the start side → park above chat → slow crawl → exit opposite side.
     */
    private void playCrawl() {
        animate().cancel();
        if (hideRunnable != null) {
            removeCallbacks(hideRunnable);
            hideRunnable = null;
        }
        if (crawlRunnable != null) {
            removeCallbacks(crawlRunnable);
            crawlRunnable = null;
        }
        setVisibility(VISIBLE);
        bringToFront();
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        setTranslationY(0f);
        showing = true;

        // Measure then animate so width is known.
        crawlRunnable = () -> {
            if (!showing) return;
            int w = Math.max(getWidth(), dp(240));
            ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
            int parentW = parent != null && parent.getWidth() > 0
                    ? parent.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;

            // Arabic/LTR rooms: enter from left, exit to the right (crawl across chat).
            final float enterFrom = -w - dp(28);
            final float park = dp(10);
            final float exitTo = parentW + dp(24);

            setTranslationX(enterFrom);
            // Phase 1 — slide in
            animate()
                    .translationX(park)
                    .setDuration(900)
                    .setInterpolator(new LinearInterpolator())
                    .setListener(null)
                    .withEndAction(() -> {
                        if (!showing) return;
                        // Phase 2 — hold so user can read + see gift icon
                        hideRunnable = () -> {
                            if (!showing) return;
                            // Phase 3 — slow crawl out to the other side
                            float distance = Math.abs(exitTo - park);
                            long duration = Math.max(1600L, Math.min(3200L,
                                    (long) (distance / Math.max(1f, getResources().getDisplayMetrics().density) * 9f)));
                            animate()
                                    .translationX(exitTo)
                                    .setDuration(duration)
                                    .setInterpolator(new LinearInterpolator())
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            showing = false;
                                            setVisibility(GONE);
                                            setTranslationX(0f);
                                            animate().setListener(null);
                                        }
                                    })
                                    .start();
                        };
                        postDelayed(hideRunnable, 2600L);
                    })
                    .start();
        };
        post(crawlRunnable);
    }

    public void hideNow() {
        showing = false;
        animate().cancel();
        if (hideRunnable != null) {
            removeCallbacks(hideRunnable);
            hideRunnable = null;
        }
        if (crawlRunnable != null) {
            removeCallbacks(crawlRunnable);
            crawlRunnable = null;
        }
        setVisibility(GONE);
        setTranslationX(0f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @DrawableRes
    private static int comboBgForCombo(int combo, int coins) {
        int score = Math.max(combo, coins / 500);
        if (score >= 50) return R.drawable.icon_combo_bg_level5;
        if (score >= 20) return R.drawable.icon_combo_bg_level4;
        if (score >= 10) return R.drawable.icon_combo_bg_level3;
        if (score >= 3) return R.drawable.icon_combo_bg_level2;
        return R.drawable.icon_combo_bg_level1;
    }
}
