package com.Dramizo.Series.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
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
 * Mikoo ComboGiftItemView — gift send toast.
 * Center pop → hold → fly away quickly; gift icon sits in its lane.
 */
public class ComboGiftView extends FrameLayout {
    private View root;
    private ImageView bg;
    private ImageView avatar;
    private ImageView giftIcon;
    private TextView sender;
    private TextView recvDesc;
    private TextView comboNum;
    private boolean showing;
    @Nullable private Runnable hideRunnable;

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
        root = LayoutInflater.from(context).inflate(R.layout.item_combo_gift_layout, this, true);
        bg = findViewById(R.id.vComboAnimBg);
        avatar = findViewById(R.id.iv_avatar);
        giftIcon = findViewById(R.id.iv_gift);
        sender = findViewById(R.id.tvSenderNick);
        recvDesc = findViewById(R.id.tvRecvDesc);
        comboNum = findViewById(R.id.tvComboNum);
        if (comboNum != null) comboNum.setText("1");
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
        if (giftIcon != null) {
            String url = AssetCatalog.absoluteUrl(giftIconUrl);
            if (url != null) {
                Glide.with(giftIcon.getContext().getApplicationContext())
                        .load(url)
                        .fitCenter()
                        .into(giftIcon);
            } else {
                giftIcon.setImageResource(R.drawable.ic_asset_gift);
            }
        }
        play();
    }

    private void play() {
        animate().cancel();
        if (hideRunnable != null) {
            removeCallbacks(hideRunnable);
            hideRunnable = null;
        }
        setVisibility(VISIBLE);
        bringToFront();
        setAlpha(0f);
        setScaleX(0.72f);
        setScaleY(0.72f);
        setTranslationX(0f);
        setTranslationY(getResources().getDisplayMetrics().density * 28f);
        showing = true;
        animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();
        hideRunnable = () -> {
            if (!showing) return;
            float fly = -getResources().getDisplayMetrics().density * 160f;
            animate()
                    .translationY(fly)
                    .alpha(0f)
                    .scaleX(0.86f)
                    .scaleY(0.86f)
                    .setDuration(220)
                    .withEndAction(() -> {
                        showing = false;
                        setVisibility(GONE);
                        setTranslationY(0f);
                        setScaleX(1f);
                        setScaleY(1f);
                        setAlpha(1f);
                    })
                    .start();
        };
        postDelayed(hideRunnable, 1600L);
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
