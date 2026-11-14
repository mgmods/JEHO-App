package com.Dramizo.Series.presentation.profile;

import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.BadgeDto;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;

/** Dashboard-awarded medals shown on the user's profile. */
public class BadgeActivity extends ThemedActivity {
    public static final String EXTRA_USER_ID = "user_id";
    private final List<BadgeDto> badges = new ArrayList<>();
    private RecyclerView grid;
    private LinearLayout emptyState;
    private ProgressBar progress;
    private Adapter adapter;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (userId == null || userId.trim().isEmpty()) {
            AppContainer c = ContainerProvider.from(this);
            userId = c.getSessionManager().getUserId();
        }
        buildContent();
        loadBadges();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 12, 12));
        root.setPadding(dp(14), dp(8), dp(14), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(6), 0, dp(12));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(34);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(46)));
        back.setOnClickListener(v -> finish());

        TextView title = new TextView(this);
        title.setText("الميداليات");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("الميداليات التي حصل عليها هذا الحساب");
        subtitle.setTextColor(0xFFB5C0C0);
        subtitle.setTextSize(13);
        subtitle.setPadding(dp(4), 0, 0, dp(12));
        root.addView(subtitle);

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(2), 0, dp(10));
        grid.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        body.addView(progress, pp);

        emptyState = new LinearLayout(this);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setOrientation(LinearLayout.VERTICAL);
        TextView emptyText = new TextView(this);
        emptyText.setText("لم يحصل هذا الحساب على ميداليات بعد");
        emptyText.setTextColor(0xFFB5C0C0);
        emptyText.setTextSize(14);
        emptyState.addView(emptyText);
        body.addView(emptyState, new FrameLayout.LayoutParams(-1, -1));
        emptyState.setVisibility(View.GONE);
        setContentView(root);
    }

    private void loadBadges() {
        progress.setVisibility(View.VISIBLE);
        grid.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            List<BadgeDto> result = new ArrayList<>();
            String failure = null;
            try {
                Response<ApiResponse<List<BadgeDto>>> response =
                        c.getUserApi().earnedBadges(userId).execute();
                if (response.isSuccessful() && response.body() != null
                        && response.body().data != null) {
                    for (BadgeDto badge : response.body().data) {
                        if (badge != null && badge.earned) result.add(badge);
                    }
                } else {
                    failure = "تعذر تحميل الميداليات";
                }
                // Compatibility fallback for older backend deployments: query the regular
                // catalogue and use its persisted ownership flag if /earned returns empty.
                if (result.isEmpty()) {
                    try {
                        Response<ApiResponse<List<BadgeDto>>> catalogResponse =
                                c.getUserApi().badges(userId).execute();
                        if (catalogResponse.isSuccessful() && catalogResponse.body() != null
                                && catalogResponse.body().data != null) {
                            for (BadgeDto badge : catalogResponse.body().data) {
                                if (badge != null && badge.earned) result.add(badge);
                            }
                            if (!result.isEmpty()) failure = null;
                        }
                    } catch (Exception ignored) {
                        // Keep the primary endpoint's error if both routes fail.
                    }
                }
            } catch (Exception e) {
                failure = "تعذر الاتصال بالخادم لتحميل الميداليات";
            }
            final String error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                badges.clear();
                badges.addAll(result);
                adapter.notifyDataSetChanged();
                grid.setVisibility(badges.isEmpty() ? View.GONE : View.VISIBLE);
                emptyState.setVisibility(badges.isEmpty() ? View.VISIBLE : View.GONE);
                if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            });
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(parent.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(10), dp(14), dp(10), dp(12));
            card.setBackgroundColor(0xFF171C1C);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(-1, dp(164));
            lp.setMargins(dp(4), dp(4), dp(4), dp(6));
            card.setLayoutParams(lp);

            ImageView image = new ImageView(parent.getContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            card.addView(image, new LinearLayout.LayoutParams(dp(76), dp(76)));
            TextView name = new TextView(parent.getContext());
            name.setGravity(Gravity.CENTER);
            name.setTextColor(Color.WHITE);
            name.setTextSize(13);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setMaxLines(2);
            name.setPadding(0, dp(8), 0, 0);
            card.addView(name, new LinearLayout.LayoutParams(-1, -2));
            TextView state = new TextView(parent.getContext());
            state.setGravity(Gravity.CENTER);
            state.setTextSize(11);
            state.setPadding(0, dp(3), 0, 0);
            card.addView(state, new LinearLayout.LayoutParams(-1, -2));
            return new Holder(card, image, name, state);
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            BadgeDto badge = badges.get(position);
            h.name.setText(badge.name == null ? "ميدالية" : badge.name);
            h.state.setText(badge.earned ? "✓ مكتسبة" : "غير مكتسبة");
            h.state.setTextColor(badge.earned ? 0xFF65D9A0 : 0xFF8A9494);
            String url = AssetCatalog.absoluteUrl(badge.imageUrl);
            Glide.with(BadgeActivity.this).load(url).placeholder(com.Dramizo.Series.R.drawable.ic_medal_default)
                    .error(com.Dramizo.Series.R.drawable.ic_medal_default).into(h.image);
            if (h.pulse != null) {
                h.pulse.cancel();
                h.pulse = null;
            }
            if (badge.earned) {
                h.image.clearColorFilter();
                h.itemView.setAlpha(1f);
                if (badge.isAnimated) {
                    h.pulse = ObjectAnimator.ofFloat(h.image, View.ALPHA, 1f, 0.68f, 1f);
                    h.pulse.setDuration(1200L);
                    h.pulse.setRepeatCount(ValueAnimator.INFINITE);
                    h.pulse.setRepeatMode(ValueAnimator.RESTART);
                    h.pulse.start();
                }
            } else {
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0f);
                h.image.setColorFilter(new ColorMatrixColorFilter(matrix));
                h.itemView.setAlpha(0.72f);
            }
            h.itemView.setOnClickListener(v -> showDetails(badge));
        }

        @Override public int getItemCount() { return badges.size(); }

        @Override public void onViewRecycled(@NonNull Holder h) {
            if (h.pulse != null) {
                h.pulse.cancel();
                h.pulse = null;
            }
            h.image.setAlpha(1f);
            super.onViewRecycled(h);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView name;
            final TextView state;
            ObjectAnimator pulse;
            Holder(View item, ImageView image, TextView name, TextView state) {
                super(item); this.image = image; this.name = name; this.state = state;
            }
        }
    }

    private void showDetails(BadgeDto badge) {
        String description = badge.description == null || badge.description.trim().isEmpty()
                ? "لا يوجد وصف إضافي لهذه الميدالية." : badge.description;
        String status = badge.earned ? "\n\nالحالة: مستلمة" : "\n\nالحالة: غير مستلمة";
        new AlertDialog.Builder(this)
                .setTitle(badge.name == null ? "تفاصيل الميدالية" : badge.name)
                .setMessage(description + status)
                .setPositiveButton("حسنًا", null)
                .show();
    }
}
