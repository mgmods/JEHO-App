package com.Dramizo.Series.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AvatarCosmetics;

/**
 * Mikoo ComingMsgView — join toast (level BG + avatar + name + انضمام).
 * Inflates {@link R.layout#layout_coming_msg}.
 */
public class ComingMsgView extends LinearLayout {
    private TextView tvUserName;
    private TextView tvJoin;
    private ImageView ivBg;
    private ImageView ivHead;
    private ImageView ivHeadVip;
    private ImageView ivVipKuang;
    private boolean showing;

    public ComingMsgView(Context context) {
        super(context);
        init(context);
    }

    public ComingMsgView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ComingMsgView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        LayoutInflater.from(context).inflate(R.layout.layout_coming_msg, this, true);
        tvUserName = findViewById(R.id.tvUserName);
        tvJoin = findViewById(R.id.tv_msg_car);
        ivBg = findViewById(R.id.iv_bg);
        ivHead = findViewById(R.id.iv_head);
        ivHeadVip = findViewById(R.id.iv_head_vip);
        ivVipKuang = findViewById(R.id.iv_vip_kuang);
        setVisibility(GONE);
    }

    /** Bind join toast like Mikoo setupView(IMRoomMemberComeInfo). */
    public void setupView(@Nullable String displayName,
                          @Nullable String avatarUrl,
                          int userLevel,
                          int vipLevel) {
        if (tvUserName != null) {
            String name = displayName == null || displayName.trim().isEmpty()
                    ? "ضيف" : displayName.trim();
            if (name.length() > 8) name = name.substring(0, 8);
            tvUserName.setText(name);
        }
        if (tvJoin != null) {
            tvJoin.setText(R.string.room_my_join);
        }
        int vipTier = Math.min(7, Math.max(0, vipLevel));
        boolean vip = vipTier >= 1;
        if (ivBg != null) {
            // VIP → Mikoo enter BG by VIP tier; guests → wealth/user level BG.
            if (vip) {
                ivBg.setImageResource(AssetCatalog.joinToastForVip(vipTier, displayName));
            } else {
                ivBg.setImageResource(enterBgForLevel(userLevel));
            }
        }
        if (ivHead != null) {
            ivHead.setVisibility(vip ? GONE : VISIBLE);
            if (!vip) AvatarCosmetics.bindAvatar(ivHead, avatarUrl);
        }
        if (ivHeadVip != null) {
            ivHeadVip.setVisibility(vip ? VISIBLE : GONE);
            if (vip) AvatarCosmetics.bindAvatar(ivHeadVip, avatarUrl);
        }
        if (ivVipKuang != null) {
            if (vip) {
                ivVipKuang.setImageResource(vipKuangForLevel(vipTier));
                ivVipKuang.setVisibility(VISIBLE);
            } else {
                ivVipKuang.setVisibility(INVISIBLE);
            }
        }
    }

    /** Slow slide-in from the left near the mic/music bar, hold, slow exit. */
    public void play(long displayMs) {
        animate().cancel();
        setVisibility(VISIBLE);
        setAlpha(0f);
        float from = -getResources().getDisplayMetrics().density * 280f;
        setTranslationX(from);
        showing = true;
        animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(780)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
        long hold = displayMs > 0 ? Math.max(3200L, displayMs) : 4200L;
        postDelayed(() -> {
            if (!showing) return;
            animate()
                    .translationX(from)
                    .alpha(0f)
                    .setDuration(620)
                    .setInterpolator(new DecelerateInterpolator(1.4f))
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
        }, hold);
    }

    public void hideNow() {
        showing = false;
        animate().cancel();
        setVisibility(GONE);
    }

    @DrawableRes
    public static int enterBgForLevel(int level) {
        int lv = Math.max(1, level);
        if (lv >= 91) return R.drawable.ic_enter_room_anima_bg100;
        if (lv >= 81) return R.drawable.ic_enter_room_anima_bg90;
        if (lv >= 71) return R.drawable.ic_enter_room_anima_bg80;
        if (lv >= 61) return R.drawable.ic_enter_room_anima_bg70;
        if (lv >= 51) return R.drawable.ic_enter_room_anima_bg60;
        if (lv >= 41) return R.drawable.ic_enter_room_anima_bg50;
        if (lv >= 31) return R.drawable.ic_enter_room_anima_bg40;
        if (lv >= 21) return R.drawable.ic_enter_room_anima_bg30;
        if (lv >= 11) return R.drawable.ic_enter_room_anima_bg20;
        return R.drawable.ic_enter_room_anima_bg10;
    }

    /** Mikoo noble head frame: VIP1→head_1 … VIP7→head_7 (sequential). */
    @DrawableRes
    public static int vipKuangForLevel(int vipLevel) {
        switch (Math.min(7, Math.max(1, vipLevel))) {
            case 1: return R.drawable.bg_enter_noble_head_1;
            case 2: return R.drawable.bg_enter_noble_head_2;
            case 3: return R.drawable.bg_enter_noble_head_3;
            case 4: return R.drawable.bg_enter_noble_head_4;
            case 5: return R.drawable.bg_enter_noble_head_5;
            case 6: return R.drawable.bg_enter_noble_head_6;
            default: return R.drawable.bg_enter_noble_head_7;
        }
    }
}
