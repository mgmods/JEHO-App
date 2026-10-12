package com.Dramizo.Series.presentation.cp;

import android.graphics.Color;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.provider.Settings;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarImageLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CpDiscoveryActivity extends ThemedActivity {
    private AppContainer container;
    private LinearLayout list;
    private String selectedType;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        container = ContainerProvider.from(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(255, 250, 242));
        EdgeToEdgeHelper.padSystemBars(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView back = text("‹", 30, Color.rgb(70, 48, 39), true);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));
        back.setOnClickListener(v -> finish());
        TextView title = text("اكتشف علاقات CP", 20, Color.rgb(74, 46, 35), true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(header);

        TextView intro = text("أجمل الروابط تُبنى بالتفاعل الحقيقي", 14, Color.rgb(125, 96, 78), false);
        intro.setGravity(Gravity.CENTER);
        intro.setPadding(dp(16), dp(4), dp(16), dp(16));
        root.addView(intro);

        ScrollView filtersScroll = new ScrollView(this);
        filtersScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters = new LinearLayout(this);
        filters.setPadding(dp(12), dp(2), dp(12), dp(10));
        String[][] tabs = {
                {"الكل", ""}, {"الأخوّة", "brotherhood"}, {"الصداقة", "friendship"},
                {"الحب", "love"}, {"الخطوبة", "engagement"}, {"الزواج", "marriage"}
        };
        for (String[] tab : tabs) {
            TextView chip = text(tab[0], 13, Color.rgb(93, 61, 44), true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackground(round(Color.rgb(255, 238, 210), dp(22)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(40));
            cp.setMargins(dp(4), 0, dp(4), 0);
            filters.addView(chip, cp);
            chip.setOnClickListener(v -> {
                selectedType = tab[1].isEmpty() ? null : tab[1];
                loadRelationships();
            });
        }
        filtersScroll.addView(filters);
        root.addView(filtersScroll, new LinearLayout.LayoutParams(-1, dp(54)));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(4), dp(14), dp(24));
        ScrollView body = new ScrollView(this);
        body.addView(list);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        loadRelationships();
    }

    private void loadRelationships() {
        list.removeAllViews();
        TextView loading = text("جارٍ تحميل العلاقات الفعلية…", 14, Color.GRAY, false);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, dp(30), 0, dp(30));
        list.addView(loading);
        container.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(container.getUserApi().discoverCp(selectedType));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                list.removeAllViews();
                if (!result.success || result.data == null) {
                    emptyState(result.error != null ? result.error : "تعذر تحميل العلاقات حالياً");
                    return;
                }
                if (result.data.isEmpty()) {
                    emptyState("لا توجد علاقات نشطة في هذا القسم بعد. ستظهر هنا العلاقات الحقيقية عند قبولها.");
                    return;
                }
                int rank = 1;
                for (Map<String, Object> row : result.data) addRelationshipCard(row, rank++);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void addRelationshipCard(Map<String, Object> row, int rank) {
        Map<String, Object> a = row.get("userA") instanceof Map ? (Map<String, Object>) row.get("userA") : java.util.Collections.emptyMap();
        Map<String, Object> b = row.get("userB") instanceof Map ? (Map<String, Object>) row.get("userB") : java.util.Collections.emptyMap();
        String type = String.valueOf(row.get("type"));
        String typeLabel = labelFor(type);
        String points = String.valueOf(row.getOrDefault("points", "0"));
        String level = String.valueOf(row.getOrDefault("level", 1));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(Color.WHITE, dp(18)));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(7), 0, dp(7));
        list.addView(card, cardLp);

        LinearLayout rowTop = new LinearLayout(this);
        rowTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView rankView = text("#" + rank, 13, Color.rgb(171, 121, 42), true);
        rowTop.addView(rankView);
        TextView typeView = text(typeLabel, 13, Color.rgb(125, 78, 57), true);
        typeView.setGravity(Gravity.END);
        rowTop.addView(typeView, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(rowTop);

        LinearLayout people = new LinearLayout(this);
        people.setGravity(Gravity.CENTER);
        people.setPadding(0, dp(14), 0, dp(10));
        ImageView avatarA = avatar(String.valueOf(a.getOrDefault("avatarUrl", "")));
        ImageView avatarB = avatar(String.valueOf(b.getOrDefault("avatarUrl", "")));
        people.addView(avatarA, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView symbol = text(symbolFor(type), 28, Color.rgb(208, 151, 54), true);
        symbol.setGravity(Gravity.CENTER);
        if (rank <= 10) {
            try {
                float animatorScale = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
                if (animatorScale > 0f) {
                    ObjectAnimator pulseX = ObjectAnimator.ofFloat(symbol, "scaleX", 0.96f, 1.06f);
                    ObjectAnimator pulseY = ObjectAnimator.ofFloat(symbol, "scaleY", 0.96f, 1.06f);
                    pulseX.setDuration(900); pulseY.setDuration(900);
                    pulseX.setRepeatMode(ValueAnimator.REVERSE); pulseY.setRepeatMode(ValueAnimator.REVERSE);
                    pulseX.setRepeatCount(ValueAnimator.INFINITE); pulseY.setRepeatCount(ValueAnimator.INFINITE);
                    symbol.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override public void onViewAttachedToWindow(View v) {}
                        @Override public void onViewDetachedFromWindow(View v) {
                            pulseX.cancel();
                            pulseY.cancel();
                        }
                    });
                    pulseX.start(); pulseY.start();
                }
            } catch (Exception ignored) { /* reduced motion or device setting unavailable */ }
        }
        people.addView(symbol, new LinearLayout.LayoutParams(dp(58), dp(58)));
        people.addView(avatarB, new LinearLayout.LayoutParams(dp(58), dp(58)));
        card.addView(people);

        String nameA = nameOf(a), nameB = nameOf(b);
        TextView names = text(nameA + "  ♥  " + nameB, 15, Color.rgb(65, 47, 40), true);
        names.setGravity(Gravity.CENTER);
        card.addView(names);
        int days = numberOf(row.get("durationDays"), 0);
        int nextLevel = numberOf(row.get("nextLevel"), 0);
        double progress = Math.max(0, Math.min(100, doubleOf(row.get("progressPercent"), 0)));
        TextView stats = text("المستوى " + level + "  ·  النقاط " + points + "  ·  " + days + " يوم", 13, Color.rgb(126, 106, 91), false);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(0, dp(7), 0, dp(2));
        card.addView(stats);
        TextView next = text(nextLevel > 0 ? "التقدم نحو المستوى " + nextLevel + " · " + Math.round(progress) + "%" : "وصلت العلاقة إلى آخر مستوى مضبوط", 12, Color.rgb(143, 105, 60), false);
        next.setGravity(Gravity.CENTER);
        next.setPadding(0, dp(6), 0, dp(5));
        card.addView(next);
        LinearLayout track = new LinearLayout(this);
        track.setBackground(round(Color.rgb(244, 226, 202), dp(4)));
        View fill = new View(this);
        fill.setBackground(round(Color.rgb(214, 160, 69), dp(4)));
        int available = Math.max(dp(40), getResources().getDisplayMetrics().widthPixels - dp(76));
        track.addView(fill, new LinearLayout.LayoutParams(Math.max(dp(3), (int) (available * progress / 100.0)), dp(5)));
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(-1, dp(5));
        trackLp.setMargins(0, dp(2), 0, dp(2));
        card.addView(track, trackLp);
        String rewardText = "لا توجد مكافآت مفتوحة حالياً";
        Object rewards = row.get("rewardsUnlocked");
        if (rewards instanceof List && !((List<?>) rewards).isEmpty()) rewardText = "المكافآت المفتوحة: " + ((List<?>) rewards).size();
        String startedAt = String.valueOf(row.getOrDefault("startedAt", "—"));
        String detailText = nameA + " و " + nameB + "\nالنوع: " + typeLabel + "\nتاريخ البدء: " + startedAt + "\nمدة العلاقة: " + days + " يوم"
                + "\nالمستوى الحالي: " + level + "\nمجموع النقاط: " + points
                + "\n" + (nextLevel > 0 ? "المستوى التالي: " + nextLevel + " (" + Math.round(progress) + "%)" : "لا يوجد مستوى تالٍ مضبوط")
                + "\n" + rewardText;
        card.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(typeLabel + " · مستوى " + level)
                .setMessage(detailText)
                .setPositiveButton("حسناً", null).show());
    }

    private ImageView avatar(String url) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(round(Color.rgb(255, 231, 195), dp(32)));
        image.setClipToOutline(true);
        AvatarImageLoader.load(image, url);
        return image;
    }

    private void emptyState(String message) {
        TextView state = text(message, 14, Color.rgb(116, 96, 82), false);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(18), dp(36), dp(18), dp(36));
        list.addView(state);
    }

    private String nameOf(Map<String, Object> user) {
        String name = String.valueOf(user.getOrDefault("displayName", ""));
        if (name.trim().isEmpty() || "null".equals(name)) name = String.valueOf(user.getOrDefault("username", "عضو"));
        return name;
    }

    private String labelFor(String type) {
        switch (type) {
            case "friendship": return "الصداقة";
            case "brotherhood": return "الأخوّة";
            case "love": return "الحب";
            case "engagement": return "الخطوبة";
            case "marriage": return "الزواج";
            default: return "علاقة CP";
        }
    }

    private String symbolFor(String type) {
        switch (type) {
            case "friendship": return "✦";
            case "brotherhood": return "♛";
            case "engagement": return "💍";
            case "marriage": return "💞";
            default: return "♥";
        }
    }

    private int numberOf(Object value, int fallback) { try { return value == null ? fallback : (int) Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return fallback; } }

    private double doubleOf(Object value, double fallback) { try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return fallback; } }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
