package com.Dramizo.Series.util;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.google.android.material.button.MaterialButton;

import java.util.Random;

/**
 * Premium reward celebration using APK 3D PNG assets (no Lottie / DotLottie).
 * Scatters coins/diamonds/chests across the screen like major social apps.
 */
public final class RewardBurstOverlay {
    public enum Kind {
        COINS,
        DIAMONDS,
        SILVER,
        CHEST,
        GIFT,
        TASK
    }

    private RewardBurstOverlay() {}

    public static void show(
            @NonNull Context context,
            @NonNull Kind kind,
            @NonNull String title,
            @NonNull String subtitle
    ) {
        if (context instanceof Activity) {
            Activity a = (Activity) context;
            if (a.isFinishing() || a.isDestroyed()) return;
        }

        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_reward_burst, null, false);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0f);
        }

        ImageView hero = root.findViewById(R.id.imgHero);
        TextView tvTitle = root.findViewById(R.id.tvHeroTitle);
        TextView tvSub = root.findViewById(R.id.tvHeroSubtitle);
        MaterialButton btnOk = root.findViewById(R.id.btnHeroOk);
        FrameLayout particles = root.findViewById(R.id.particleLayer);
        View heroCard = root.findViewById(R.id.heroCard);

        int heroRes = heroDrawable(kind);
        hero.setImageResource(heroRes);
        tvTitle.setText(title);
        tvSub.setText(subtitle);

        heroCard.setScaleX(0.72f);
        heroCard.setScaleY(0.72f);
        heroCard.setAlpha(0f);
        heroCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(420)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();

        spawnParticles(context, particles, kind);

        Runnable dismiss = () -> {
            try {
                if (dialog.isShowing()) dialog.dismiss();
            } catch (Exception ignored) {
            }
        };
        btnOk.setOnClickListener(v -> dismiss.run());
        root.setOnClickListener(v -> dismiss.run());
        heroCard.setOnClickListener(v -> { /* absorb */ });

        dialog.setCancelable(true);
        dialog.show();

        new Handler(Looper.getMainLooper()).postDelayed(dismiss, 3200L);
    }

    public static void showTaskClaim(@NonNull Context context, @NonNull String rewardLine) {
        show(context, Kind.TASK, "مهمة مكتملة!", rewardLine);
    }

    /** Build a short Arabic reward line from a task DTO. */
    @NonNull
    public static String taskRewardLine(@Nullable MiscDtos.TaskDto task) {
        if (task == null) return "تم استلام المكافأة";
        StringBuilder sb = new StringBuilder();
        sb.append(Math.max(0, task.rewardPoints)).append(" نقطة");
        if (task.rewardSilver > 0) sb.append(" · ").append(task.rewardSilver).append(" كوينز");
        if (!task.isHostTask() && task.rewardDiamonds > 0) {
            sb.append(" · ").append(task.rewardDiamonds).append(" ماس");
        }
        return sb.toString();
    }

    public static void showForTask(@NonNull Context context, @Nullable MiscDtos.TaskDto task) {
        showTaskClaim(context, taskRewardLine(task));
    }

    public static void showCheckIn(@NonNull Context context) {
        show(context, Kind.CHEST, "تم تسجيل الحضور!", "استلم المكافأة من قائمة المهام");
    }

    public static void showCoins(@NonNull Context context, @NonNull String title, @NonNull String subtitle) {
        show(context, Kind.COINS, title, subtitle);
    }

    public static void showGift(@NonNull Context context, @NonNull String title, @NonNull String subtitle) {
        show(context, Kind.GIFT, title, subtitle);
    }

    public static void showLuckyWin(@NonNull Context context, @NonNull String opener, @NonNull String reward) {
        String title = (opener != null && !opener.isEmpty() ? opener : "مستخدم") + " · صندوق الحظ";
        show(context, Kind.CHEST, title, reward != null ? reward : "جائزة");
    }

    /**
     * Lucky gift win: دفعت X · ربحت Y (×N إن وُجد). بدون شرح المضيف/المنصة.
     */
    public static void showLuckyGiftWin(
            @NonNull Context context,
            @Nullable String giftName,
            int multiplier,
            long baseCoins,
            long coinsWon
    ) {
        showLuckyGiftWin(context, giftName, multiplier, baseCoins, coinsWon, false);
    }

    public static void showLuckyGiftWin(
            @NonNull Context context,
            @Nullable String giftName,
            int multiplier,
            long baseCoins,
            long coinsWon,
            boolean softReturn
    ) {
        // Disabled — room uses ComingMsgView-sized toast only.
    }

    /** Lucky gift lose: disabled (never show «ما جاك مردود»). */
    public static void showLuckyGiftLose(
            @NonNull Context context,
            @Nullable String giftName,
            long coinsSpent
    ) {
        // Disabled — empty rolls stay silent.
    }

    @Nullable
    private static Context resolveActivityContext(@NonNull Context context) {
        if (context instanceof Activity) {
            Activity a = (Activity) context;
            if (a.isFinishing() || a.isDestroyed()) return null;
            return a;
        }
        if (context instanceof android.content.ContextWrapper) {
            Context base = ((android.content.ContextWrapper) context).getBaseContext();
            if (base instanceof Activity) {
                Activity a = (Activity) base;
                if (a.isFinishing() || a.isDestroyed()) return null;
                return a;
            }
        }
        return context;
    }

    private static void animateLuckyCard(@Nullable View card) {
        if (card == null) return;
        card.setScaleX(0.86f);
        card.setScaleY(0.86f);
        card.setAlpha(0f);
        card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(new OvershootInterpolator(1.08f))
                .start();
    }

    private static void wireLuckyDismiss(
            @NonNull Dialog dialog,
            @NonNull View root,
            @Nullable View card,
            @Nullable TextView btnOk
    ) {
        Runnable dismiss = () -> {
            try {
                if (dialog.isShowing()) dialog.dismiss();
            } catch (Exception ignored) {
            }
        };
        if (btnOk != null) btnOk.setOnClickListener(v -> dismiss.run());
        root.setOnClickListener(v -> dismiss.run());
        if (card != null) card.setOnClickListener(v -> { /* absorb */ });
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
    }

    @DrawableRes
    private static int heroDrawable(Kind kind) {
        switch (kind) {
            case DIAMONDS:
                return R.drawable.ic_asset_diamond;
            case SILVER:
                return R.drawable.ic_asset_bag_silver;
            case GIFT:
                return R.drawable.ic_asset_gift;
            case CHEST:
                return R.drawable.ic_asset_chest_gold;
            case TASK:
                return R.drawable.ic_asset_tasks;
            case COINS:
            default:
                return R.drawable.ic_asset_coin_gold;
        }
    }

    @DrawableRes
    private static int particleDrawable(Kind kind, Random rnd) {
        switch (kind) {
            case DIAMONDS:
                return rnd.nextBoolean() ? R.drawable.ic_asset_diamond : R.drawable.ic_asset_diamond_alt;
            case SILVER:
                return R.drawable.ic_asset_bag_silver;
            case GIFT:
                return rnd.nextBoolean() ? R.drawable.ic_asset_gift : R.drawable.ic_asset_chest_pink;
            case CHEST:
                int[] chests = {
                        R.drawable.ic_asset_chest_gold,
                        R.drawable.ic_asset_chest_coins,
                        R.drawable.ic_asset_chest_blue,
                        R.drawable.ic_asset_coin_gold,
                        R.drawable.ic_asset_diamond
                };
                return chests[rnd.nextInt(chests.length)];
            case TASK:
                int[] mix = {
                        R.drawable.ic_asset_coin_gold,
                        R.drawable.ic_asset_diamond,
                        R.drawable.ic_asset_gift,
                        R.drawable.ic_asset_chest_gold
                };
                return mix[rnd.nextInt(mix.length)];
            case COINS:
            default:
                return R.drawable.ic_asset_coin_gold;
        }
    }

    private static void spawnParticles(Context context, FrameLayout layer, Kind kind) {
        if (layer == null) return;
        layer.post(() -> {
            int w = layer.getWidth();
            int h = layer.getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f;
            float cy = h / 2f;
            Random rnd = new Random();
            int count = 22;
            float density = context.getResources().getDisplayMetrics().density;

            for (int i = 0; i < count; i++) {
                ImageView img = new ImageView(context);
                int size = (int) ((28 + rnd.nextInt(28)) * density);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                lp.gravity = Gravity.TOP | Gravity.START;
                img.setLayoutParams(lp);
                img.setImageResource(particleDrawable(kind, rnd));
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                img.setX(cx - size / 2f);
                img.setY(cy - size / 2f);
                img.setAlpha(0f);
                layer.addView(img);

                double angle = (Math.PI * 2 * i) / count + (rnd.nextFloat() - 0.5f) * 0.45;
                float dist = (160 + rnd.nextInt(220)) * density;
                float tx = (float) (Math.cos(angle) * dist);
                float ty = (float) (Math.sin(angle) * dist) - 40f * density;
                float rot = (rnd.nextFloat() - 0.5f) * 540f;
                long delay = 40L + rnd.nextInt(180);
                long dur = 900L + rnd.nextInt(500);

                ObjectAnimator move = ObjectAnimator.ofPropertyValuesHolder(
                        img,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, tx),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, ty),
                        PropertyValuesHolder.ofFloat(View.ROTATION, 0f, rot),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 0.35f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.35f, 1f),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 1f, 0f)
                );
                move.setDuration(dur);
                move.setStartDelay(delay);
                move.setInterpolator(new DecelerateInterpolator(1.15f));
                move.start();
            }

            // Second soft rain of coins from the top.
            for (int i = 0; i < 10; i++) {
                ImageView img = new ImageView(context);
                int size = (int) ((20 + rnd.nextInt(18)) * density);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                img.setLayoutParams(lp);
                img.setImageResource(particleDrawable(Kind.COINS, rnd));
                float startX = rnd.nextFloat() * w;
                img.setX(startX);
                img.setY(-size - rnd.nextInt((int) (80 * density)));
                img.setAlpha(0f);
                layer.addView(img);

                ObjectAnimator fall = ObjectAnimator.ofPropertyValuesHolder(
                        img,
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, h * (0.55f + rnd.nextFloat() * 0.4f)),
                        PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, (rnd.nextFloat() - 0.5f) * 120f * density),
                        PropertyValuesHolder.ofFloat(View.ROTATION, 0f, (rnd.nextFloat() - 0.5f) * 360f),
                        PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.95f, 0f)
                );
                fall.setDuration(1100L + rnd.nextInt(600));
                fall.setStartDelay(120L + i * 55L);
                fall.setInterpolator(new AccelerateDecelerateInterpolator());
                fall.start();
            }
        });
    }

    private static float dp(Context ctx, float v) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics());
    }
}
