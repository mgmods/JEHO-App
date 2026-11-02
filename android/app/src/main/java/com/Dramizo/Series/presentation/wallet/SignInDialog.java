package com.Dramizo.Series.presentation.wallet;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.caverock.androidsvg.SVG;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.DialogSignInBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Native seven-day login rewards; configuration and claims are server-backed. */
public class SignInDialog extends DialogFragment {
    private static final int GREEN = Color.rgb(33, 134, 83);
    private static final int DARK = Color.rgb(35, 45, 40);
    private static final int MUTED = Color.rgb(112, 122, 116);
    private static final OkHttpClient SVG_HTTP = new OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    public static void show(@Nullable FragmentManager fm) {
        if (fm == null || fm.isStateSaved()) return;
        if (fm.findFragmentByTag("sign_in") != null) return;
        new SignInDialog().show(fm, "sign_in");
    }

    private DialogSignInBinding binding;
    private boolean claiming;
    private final List<ImageView> rewardImages = new ArrayList<>();
    private final List<TextView> rewardLabels = new ArrayList<>();
    private final List<TextView> dayLabels = new ArrayList<>();
    private MiscDtos.DailyLoginStatusDto status;

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogSignInBinding.inflate(inflater, container, false);
        buildDayCells();
        binding.bltvCheckIn.setOnClickListener(v -> claimToday());
        loadRewards();
        return binding.getRoot();
    }

    @Override public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private void buildDayCells() {
        if (binding == null) return;
        GridLayout grid = binding.gridDays;
        grid.removeAllViews();
        rewardImages.clear(); rewardLabels.clear(); dayLabels.clear();
        for (int i = 0; i < 7; i++) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackgroundResource(R.drawable.bg_sign_day_cell);
            card.setPadding(dp(4), dp(7), dp(4), dp(7));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = dp(98);
            lp.columnSpec = GridLayout.spec(i < 4 ? i : i - 4, 1f);
            lp.rowSpec = GridLayout.spec(i < 4 ? 0 : 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(lp);

            TextView day = new TextView(requireContext());
            day.setText("اليوم " + (i + 1)); day.setTextColor(DARK); day.setTextSize(11f);
            day.setTypeface(Typeface.DEFAULT, Typeface.BOLD); day.setGravity(Gravity.CENTER);
            ImageView image = new ImageView(requireContext());
            image.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
            image.setImageResource(R.drawable.ic_gold_coin);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("صورة مكافأة اليوم " + (i + 1));
            TextView label = new TextView(requireContext());
            label.setText("عملات"); label.setTextColor(MUTED); label.setTextSize(10f);
            label.setGravity(Gravity.CENTER); label.setMaxLines(2);
            card.addView(day); card.addView(image); card.addView(label);
            grid.addView(card);
            dayLabels.add(day); rewardImages.add(image); rewardLabels.add(label);
        }
    }

    private void loadRewards() {
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.DailyLoginStatusDto> r = ApiCall.execute(c.getTasksApi().dailyLoginStatus());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (!r.success || r.data == null) {
                    binding.tvSignHint.setText("تعذر تحميل المكافآت. تحقق من اتصالك وحاول مجددًا.");
                    binding.bltvCheckIn.setEnabled(true);
                    return;
                }
                status = r.data;
                if (!status.enabled) {
                    binding.tvSignHint.setText("مكافآت تسجيل الدخول متوقفة حاليًا");
                    binding.bltvCheckIn.setEnabled(false);
                    binding.bltvCheckIn.setText("غير متاح");
                    return;
                }
                applyTheme(status.theme);
                List<MiscDtos.DailyLoginRewardDto> days = status.days;
                for (int i = 0; i < 7; i++) {
                    MiscDtos.DailyLoginRewardDto day = days != null && i < days.size() ? days.get(i) : null;
                    ImageView image = rewardImages.get(i);
                    TextView label = rewardLabels.get(i);
                    if (day != null && "cosmetic".equalsIgnoreCase(day.type)) {
                        // Try the catalogue thumbnail first, then its animation artwork if
                        // the thumbnail URL is missing or the server returns an error.
                        if (nonEmpty(day.previewUrl) || nonEmpty(day.animationUrl)) {
                            loadDashboardArtwork(image, day.previewUrl, day.animationUrl,
                                    R.drawable.ic_gold_coin, ImageView.ScaleType.FIT_CENTER);
                        } else {
                            image.setImageResource(R.drawable.ic_gold_coin);
                        }
                        label.setText(nonEmpty(day.name) ? day.name : (nonEmpty(day.label) ? day.label : "عنصر تجميلي"));
                        if (day.durationDays > 0) label.setText(label.getText() + "\n" + day.durationDays + " يوم");
                    } else {
                        image.setImageResource(R.drawable.ic_gold_coin);
                        long amount = day == null ? 0 : day.amount;
                        label.setText(nonEmpty(day == null ? null : day.label) ? day.label : String.format(Locale.getDefault(), "%,d كوينز", amount));
                    }
                    dayLabels.get(i).setText("اليوم " + (i + 1) + (day != null && day.claimed ? " ✓" : ""));
                    if (day != null && day.day == status.currentDay) {
                        dayLabels.get(i).setTextColor(GREEN);
                    } else dayLabels.get(i).setTextColor(DARK);
                }
                MiscDtos.DailyLoginRewardDto reward = status.reward;
                String summary = reward == null ? "سجّل حضورك اليومي واحصل على مكافأتك"
                        : ("cosmetic".equalsIgnoreCase(reward.type)
                        ? (nonEmpty(reward.name) ? reward.name : "مكافأة تجميلية") + (reward.durationDays > 0 ? " · " + reward.durationDays + " يوم" : "")
                        : String.format(Locale.getDefault(), "+%,d كوينز", reward.amount));
                binding.tvSignHint.setText(status.alreadyClaimed ? "تم استلام مكافأة اليوم بنجاح" : "مكافأة اليوم " + status.currentDay + ": " + summary);
                binding.bltvCheckIn.setEnabled(!status.alreadyClaimed && status.shouldShow);
                binding.bltvCheckIn.setText(status.alreadyClaimed ? "تم الاستلام" : "استلام");
                binding.bltvCheckIn.setTextColor(GREEN);
            });
        });
    }


    /** Apply dashboard-owned artwork and styling to the actual user dialog. */
    private void applyTheme(Map<String, Object> theme) {
        if (binding == null || theme == null) return;
        String backgroundUrl = themeString(theme, "backgroundImageUrl");
        String logoUrl = themeString(theme, "logoUrl");
        int backgroundColor = themeColor(theme, "backgroundColor", Color.rgb(245, 240, 232));
        int overlayColor = themeColor(theme, "overlayColor", Color.WHITE);
        float overlayOpacity = Math.max(0f, Math.min(1f, themeFloat(theme, "overlayOpacity", 0.12f)));
        int panelColor = blend(backgroundColor, overlayColor, overlayOpacity);
        if (nonEmpty(backgroundUrl)) {
            binding.imgDialogBackground.setVisibility(View.VISIBLE);
            loadDashboardArtwork(binding.imgDialogBackground, backgroundUrl, null,
                    R.drawable.bg_sign_in_panel, ImageView.ScaleType.CENTER_CROP);
            // Keep the uploaded wallpaper visible beneath a readable translucent panel.
            GradientDrawable panel = new GradientDrawable();
            panel.setColor(Color.argb(144, Color.red(panelColor), Color.green(panelColor), Color.blue(panelColor)));
            panel.setCornerRadius(dp(22));
            binding.clSignIn.setBackground(panel);
        } else {
            binding.imgDialogBackground.setVisibility(View.GONE);
            GradientDrawable panel = new GradientDrawable();
            panel.setColor(panelColor);
            panel.setCornerRadius(dp(22));
            binding.clSignIn.setBackground(panel);
        }
        if (nonEmpty(logoUrl)) {
            binding.imgSignLogo.setVisibility(View.VISIBLE);
            loadDashboardArtwork(binding.imgSignLogo, logoUrl, null,
                    R.drawable.ic_tab_me, ImageView.ScaleType.FIT_CENTER);
        } else {
            binding.imgSignLogo.setVisibility(View.GONE);
            binding.imgSignLogo.setImageDrawable(null);
        }
        String title = themeString(theme, "title");
        if (nonEmpty(title)) binding.tvTitle2.setText(title);
        String titleColor = themeString(theme, "titleColor");
        if (nonEmpty(titleColor)) binding.tvTitle2.setTextColor(parseColor(titleColor, Color.WHITE));
        String buttonText = themeString(theme, "buttonText");
        if (nonEmpty(buttonText)) binding.bltvCheckIn.setText(buttonText);
        int buttonColor = themeColor(theme, "buttonColor", GREEN);
        GradientDrawable button = new GradientDrawable();
        button.setColor(buttonColor);
        button.setCornerRadius(dp(64));
        binding.bltvCheckIn.setBackground(button);
        binding.bltvCheckIn.setTextColor(themeColor(theme, "buttonTextColor", Color.WHITE));
    }

    /** Resolve dashboard paths and load either raster artwork or SVG uploads. */
    private void loadDashboardArtwork(ImageView target, @Nullable String primary,
                                      @Nullable String fallback, int placeholder,
                                      ImageView.ScaleType scaleType) {
        if (target == null) return;
        target.setScaleType(scaleType);
        target.setImageResource(placeholder);
        List<String> candidates = new ArrayList<>();
        addArtworkCandidate(candidates, primary);
        addArtworkCandidate(candidates, fallback);
        if (candidates.isEmpty()) return;
        loadDashboardArtworkCandidate(target, candidates, 0, placeholder);
    }

    private void addArtworkCandidate(List<String> candidates, @Nullable String raw) {
        if (!nonEmpty(raw)) return;
        String absolute = AssetCatalog.absoluteUrl(raw.trim());
        String resolved = com.Dramizo.Series.util.MediaAssetSync.bust(absolute);
        if (nonEmpty(resolved) && !candidates.contains(resolved)) candidates.add(resolved);
    }

    private boolean isSvgArtwork(String url) {
        String clean = url == null ? "" : url;
        int query = clean.indexOf('?');
        if (query >= 0) clean = clean.substring(0, query);
        return clean.toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    private void loadDashboardArtworkCandidate(ImageView target, List<String> candidates,
                                               int index, int placeholder) {
        if (!isAdded() || binding == null || target == null) return;
        if (index >= candidates.size()) {
            target.setImageResource(placeholder);
            return;
        }
        String url = candidates.get(index);
        if (isSvgArtwork(url)) {
            loadSvgArtwork(target, candidates, index, placeholder);
            return;
        }
        Glide.with(this).load(url)
                .placeholder(placeholder)
                .error(placeholder)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException error, Object model,
                                                Target<Drawable> glideTarget, boolean first) {
                        if (index + 1 < candidates.size()) {
                            loadDashboardArtworkCandidate(target, candidates, index + 1, placeholder);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                   Target<Drawable> glideTarget,
                                                   com.bumptech.glide.load.DataSource source,
                                                   boolean first) {
                        return false;
                    }
                })
                .into(target);
    }

    private void loadSvgArtwork(ImageView target, List<String> candidates, int index, int placeholder) {
        final String url = candidates.get(index);
        AppContainer container;
        try {
            container = ContainerProvider.from(requireActivity());
        } catch (Exception error) {
            loadDashboardArtworkCandidate(target, candidates, index + 1, placeholder);
            return;
        }
        container.getIoExecutor().execute(() -> {
            Bitmap bitmap = null;
            try (Response response = SVG_HTTP.newCall(new Request.Builder().url(url).get().build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("SVG image request failed: " + response.code());
                }
                SVG svg = SVG.getFromInputStream(response.body().byteStream());
                float docWidth = svg.getDocumentWidth();
                float docHeight = svg.getDocumentHeight();
                if (!(docWidth > 0f) || !(docHeight > 0f)
                        || Float.isInfinite(docWidth) || Float.isInfinite(docHeight)
                        || Float.isNaN(docWidth) || Float.isNaN(docHeight)) {
                    docWidth = 512f;
                    docHeight = 512f;
                }
                float scale = Math.min(1f, 1200f / Math.max(docWidth, docHeight));
                int width = Math.max(1, Math.round(docWidth * scale));
                int height = Math.max(1, Math.round(docHeight * scale));
                Picture picture = svg.renderToPicture(width, height);
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                picture.draw(canvas);
            } catch (Exception error) {
                android.util.Log.w("SignInDialog", "Dashboard SVG artwork failed: " + url, error);
            }
            final Bitmap result = bitmap;
            if (!isAdded()) {
                if (result != null && !result.isRecycled()) result.recycle();
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (binding == null || !isAdded()) {
                    if (result != null && !result.isRecycled()) result.recycle();
                    return;
                }
                if (result != null) {
                    target.setImageBitmap(result);
                } else if (index + 1 < candidates.size()) {
                    loadDashboardArtworkCandidate(target, candidates, index + 1, placeholder);
                } else {
                    target.setImageResource(placeholder);
                }
            });
        });
    }

    private String themeString(Map<String, Object> theme, String key) {
        Object value = theme.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private float themeFloat(Map<String, Object> theme, String key, float fallback) {
        try { return Float.parseFloat(String.valueOf(theme.get(key))); }
        catch (Exception ignored) { return fallback; }
    }

    private int themeColor(Map<String, Object> theme, String key, int fallback) {
        return parseColor(themeString(theme, key), fallback);
    }

    private int parseColor(String value, int fallback) {
        try { return Color.parseColor(value); }
        catch (Exception ignored) { return fallback; }
    }

    private int blend(int base, int overlay, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(base) * inverse + Color.red(overlay) * amount),
                Math.round(Color.green(base) * inverse + Color.green(overlay) * amount),
                Math.round(Color.blue(base) * inverse + Color.blue(overlay) * amount));
    }

    private void claimToday() {
        if (claiming || binding == null || (status != null && (status.alreadyClaimed || !status.enabled))) return;
        claiming = true;
        binding.bltvCheckIn.setEnabled(false);
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> r = ApiCall.execute(c.getTasksApi().claimDailyLogin());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                claiming = false;
                if (r.success) {
                    if (binding != null) {
                        binding.bltvCheckIn.setText("تم الاستلام");
                        binding.bltvCheckIn.setEnabled(false);
                        binding.bltvCheckIn.setTextColor(GREEN);
                    }
                    Toast.makeText(requireContext(), "تم الاستلام بنجاح", Toast.LENGTH_SHORT).show();
                    loadRewards();
                } else {
                    Toast.makeText(requireContext(), r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                    if (binding != null) binding.bltvCheckIn.setEnabled(true);
                }
            });
        });
    }

    private boolean nonEmpty(String value) { return value != null && !value.trim().isEmpty(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        rewardImages.clear(); rewardLabels.clear(); dayLabels.clear();
    }
}
