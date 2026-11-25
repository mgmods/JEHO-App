package com.Dramizo.Series.presentation.voiceroom;

import android.app.Dialog;
import android.view.Window;
import android.view.WindowManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.widget.Toast;

import com.Dramizo.Series.data.remote.dto.AdvertisementDtos;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Room-wide advertisement composer. All quota and permission decisions remain server-authoritative. */
public final class RoomAdvertisementComposerView extends FrameLayout implements RealtimeClient.UserListener {
    private final AtomicBoolean loading = new AtomicBoolean(false);
    private TextView settingsButton;
    private int remaining = -1;
    private int maxLength = 180;
    private int maxWords = 30;
    private int publishDelaySeconds = 10;
    private boolean featureEnabled = false;
    private AdvertisementDtos.Ticker composerConfig;
    // Reuse this key whenever the request outcome is uncertain; the backend deduplicates it.
    private String pendingIdempotencyKey;
    private String pendingIdempotencyText;

    public RoomAdvertisementComposerView(Context context, AttributeSet attrs) { this(context); }
    public RoomAdvertisementComposerView(Context context, AttributeSet attrs, int defStyleAttr) { this(context); }

    public RoomAdvertisementComposerView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
// Hidden controller only; the settings-panel action is the sole entry point.
        setVisibility(GONE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        RealtimeClient.getInstance().addUserListener(this);
        refreshQuota();
    }

    @Override protected void onDetachedFromWindow() {
        RealtimeClient.getInstance().removeUserListener(this);
        super.onDetachedFromWindow();
    }

    @Override public void onConnected() { refreshQuota(); }
    @Override public void onDisconnected() {}

    @Override public void onUserEvent(String event, JsonObject payload) {
        if (!"room:advertisement:config".equals(event) || payload == null) return;
        try {
            AdvertisementDtos.Ticker next = new com.google.gson.Gson().fromJson(payload, AdvertisementDtos.Ticker.class);
            if (next == null) return;
            featureEnabled = next.enabled && next.sendButtonEnabled && !next.paused;
            maxLength = next.maxTextLength > 0 ? next.maxTextLength : 180;
            maxWords = next.maxWords > 0 ? next.maxWords : 30;
            publishDelaySeconds = Math.max(0, next.publishDelayMs / 1000);
            composerConfig = next;
            post(() -> {
                applyOpenButtonConfig(next);
                updateFeatureVisibility();
            });
            if (featureEnabled) refreshRemaining();
        } catch (Exception ignored) {}
    }

    /** Bind the existing wide action above the room/tool/challenge tabs to this composer. */
    public void bindSettingsButton(TextView button) {
        settingsButton = button;
        if (settingsButton == null) return;
        settingsButton.setOnClickListener(v -> showComposer());
        settingsButton.setVisibility(featureEnabled ? VISIBLE : GONE);
        if (composerConfig != null) applyOpenButtonConfig(composerConfig);
    }

    private void updateFeatureVisibility() {
        // Never render a second floating button in the room.
        setVisibility(GONE);
        if (settingsButton != null) settingsButton.setVisibility(featureEnabled ? VISIBLE : GONE);
    }

    private void applyOpenButtonConfig(AdvertisementDtos.Ticker cfg) {
        if (cfg == null || settingsButton == null) return;
        String label = cfg.composerButtonText == null || cfg.composerButtonText.trim().isEmpty()
                ? "✦  إعلان عام" : cfg.composerButtonText.trim();
        settingsButton.setText(label);
        settingsButton.setTextColor(colorOr(cfg.composerButtonTextColor, Color.WHITE));
        settingsButton.setTextSize(Math.max(9, cfg.composerButtonTextSizeSp > 0 ? cfg.composerButtonTextSizeSp : 13));
        int radius = dp(Math.max(0, cfg.composerButtonRadiusDp));
        int borderWidth = dp(Math.max(0, cfg.composerButtonBorderWidthDp));
        int borderColor = colorOr(cfg.composerButtonBorderColor, Color.rgb(239, 201, 129));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{colorOr(cfg.composerButtonBackgroundStartColor, Color.rgb(105,65,23)),
                        colorOr(cfg.composerButtonBackgroundEndColor, Color.rgb(229,184,91))});
        bg.setCornerRadius(radius);
        if (borderWidth > 0) bg.setStroke(borderWidth, borderColor);
        settingsButton.setBackground(bg);
        int height = dp(Math.max(32, cfg.composerButtonHeightDp > 0 ? cfg.composerButtonHeightDp : 46));
        ViewGroup.LayoutParams lp = settingsButton.getLayoutParams();
        if (lp != null && lp.height != height) { lp.height = height; settingsButton.setLayoutParams(lp); }
        settingsButton.setMinHeight(height);
        settingsButton.setContentDescription(label);
    }

    private void refreshQuota() {
        ContainerProvider.from(getContext()).getRoomApi().advertisementConfig()
                .enqueue(new Callback<ApiResponse<AdvertisementDtos.Config>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Config>> call,
                                             Response<ApiResponse<AdvertisementDtos.Config>> response) {
                AdvertisementDtos.Config cfg = response.body() == null ? null : response.body().data;
                if (cfg == null) return;
                featureEnabled = cfg.enabled && cfg.sendButtonEnabled && !cfg.paused;
                maxLength = cfg.maxTextLength > 0 ? cfg.maxTextLength : 180;
                maxWords = cfg.maxWords > 0 ? cfg.maxWords : 30;
                if (cfg.ticker != null) {
                    composerConfig = cfg.ticker;
                    applyOpenButtonConfig(cfg.ticker);
                    publishDelaySeconds = Math.max(0, cfg.ticker.publishDelayMs / 1000);
                }
                updateFeatureVisibility();
                refreshRemaining();
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Config>> call, Throwable t) {
                featureEnabled = false;
                post(RoomAdvertisementComposerView.this::updateFeatureVisibility);
            }
        });
    }

    private void refreshRemaining() {
        ContainerProvider.from(getContext()).getRoomApi().advertisementRemaining()
                .enqueue(new Callback<ApiResponse<AdvertisementDtos.Remaining>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Remaining>> call,
                                             Response<ApiResponse<AdvertisementDtos.Remaining>> response) {
                AdvertisementDtos.Remaining data = response.body() == null ? null : response.body().data;
                if (data == null) { remaining = -1; return; }
                remaining = Math.max(0, data.remaining);
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Remaining>> call, Throwable t) {
                remaining = -1;
                remaining = -1;
            }
        });
    }

    /** Opened from the room-settings header action; refreshes the server quota before showing the composer. */
    public void showComposer() {
        if (loading.get()) return;
        ContainerProvider.from(getContext()).getRoomApi().advertisementConfig()
                .enqueue(new Callback<ApiResponse<AdvertisementDtos.Config>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Config>> call,
                                             Response<ApiResponse<AdvertisementDtos.Config>> response) {
                AdvertisementDtos.Config cfg = response.body() == null ? null : response.body().data;
                if (cfg == null) {
                    Toast.makeText(getContext(), "تعذر التحقق من إعدادات الإعلان، حاول مجدداً", Toast.LENGTH_SHORT).show();
                    return;
                }
                featureEnabled = cfg.enabled && cfg.sendButtonEnabled && !cfg.paused;
                maxLength = cfg.maxTextLength > 0 ? cfg.maxTextLength : 180;
                maxWords = cfg.maxWords > 0 ? cfg.maxWords : 30;
                composerConfig = cfg.ticker;
                applyOpenButtonConfig(cfg.ticker);
                updateFeatureVisibility();
                if (!featureEnabled) {
                    Toast.makeText(getContext(), "الإعلانات العامة غير مفعّلة حالياً", Toast.LENGTH_SHORT).show();
                    return;
                }
                ContainerProvider.from(getContext()).getRoomApi().advertisementRemaining()
                        .enqueue(new Callback<ApiResponse<AdvertisementDtos.Remaining>>() {
                    @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Remaining>> call,
                                                     Response<ApiResponse<AdvertisementDtos.Remaining>> response) {
                        AdvertisementDtos.Remaining data = response.body() == null ? null : response.body().data;
                        if (data == null) {
                            // Quota display is advisory only. The create endpoint
                            // enforces the daily limit transactionally, so do not
                            // block the user from composing when this read fails.
                            remaining = -1;
                            post(() -> openComposer());
                            return;
                        }
                        remaining = Math.max(0, data.remaining);
                        if (remaining == 0) {
                            Toast.makeText(getContext(), "انتهى الحد اليومي للإعلانات", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        post(() -> openComposer());
                    }
                    @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Remaining>> call, Throwable t) {
                        remaining = -1;
                        post(() -> openComposer());
                    }
                });
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Config>> call, Throwable t) {
                Toast.makeText(getContext(), "تعذر الاتصال للتحقق من الإعلانات", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openComposer() {
        if (!featureEnabled || remaining == 0 || loading.get()) {
            refreshQuota();
            if (remaining == 0) Toast.makeText(getContext(), "انتهت إعلاناتك اليوم", Toast.LENGTH_SHORT).show();
            return;
        }

        final AdvertisementDtos.Ticker cfg = composerConfig;
        final int muted = colorOr(cfg == null ? null : cfg.composerSubtitleColor, Color.rgb(102, 116, 136));
        final int dark = colorOr(cfg == null ? null : cfg.composerTitleColor, Color.rgb(25, 34, 49));
        final int inputTextColor = colorOr(cfg == null ? null : cfg.composerInputTextColor, Color.rgb(17, 24, 39));
        final int inputHintColor = colorOr(cfg == null ? null : cfg.composerInputHintColor, Color.rgb(143, 154, 171));
        final int inputBgColor = colorOr(cfg == null ? null : cfg.composerInputBackgroundColor, Color.WHITE);
        final int inputBorderColor = colorOr(cfg == null ? null : cfg.composerInputBorderColor, Color.rgb(211, 221, 234));
        final int cancelTextColor = colorOr(cfg == null ? null : cfg.composerCancelTextColor, Color.rgb(62, 76, 96));
        final int cancelBgColor = colorOr(cfg == null ? null : cfg.composerCancelBackgroundColor, Color.rgb(246, 248, 252));
        final int cancelBorderColor = colorOr(cfg == null ? null : cfg.composerCancelBorderColor, Color.rgb(220, 228, 238));
        final int sendTextColor = colorOr(cfg == null ? null : cfg.composerSendTextColor, Color.rgb(35, 28, 21));
        final int sendStart = colorOr(cfg == null ? null : cfg.composerSendGradientStartColor, Color.rgb(238, 197, 119));
        final int sendEnd = colorOr(cfg == null ? null : cfg.composerSendGradientEndColor, Color.rgb(188, 126, 47));
        final int sendBorder = colorOr(cfg == null ? null : cfg.composerSendBorderColor, Color.rgb(239, 202, 128));
        final int radius = dp(cfg == null ? 16 : Math.max(0, Math.min(40, cfg.composerButtonRadiusDp > 0 ? cfg.composerButtonRadiusDp : 16)));
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Rebuilt as a true broadcast composer: structured header, clear delivery
        // summary, spacious editor, live quota and a dedicated fixed action area.
        LinearLayout sheet = new LinearLayout(getContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(16), dp(9), dp(16), dp(14));
        String composerBg = cfg == null ? "#F7F9FC" : cfg.composerBackgroundColor;
        int composerOpacity = cfg == null ? 100 : Math.max(0, Math.min(100, cfg.composerBackgroundOpacity));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(withAlpha(colorOr(composerBg, Color.rgb(247, 249, 252)), composerOpacity));
        sheetBg.setCornerRadii(new float[]{dp(30),dp(30),dp(30),dp(30),dp(0),dp(0),dp(0),dp(0)});
        sheetBg.setStroke(dp(1), inputBorderColor);
        sheet.setBackground(sheetBg);
        sheet.setElevation(dp(24));
        FrameLayout sheetRoot = new FrameLayout(getContext());
        ImageView composerBackground = new ImageView(getContext());
        composerBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        composerBackground.setAlpha(composerOpacity / 100f);
        composerBackground.setBackgroundColor(colorOr(composerBg, Color.rgb(247, 249, 252)));
        sheetRoot.addView(composerBackground, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        sheet.setBackgroundColor(Color.TRANSPARENT);
        sheetRoot.addView(sheet, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        loadRemoteImage(composerBackground, cfg == null ? "" : cfg.composerBackgroundImage);

        View grabber = new View(getContext());
        grabber.setBackground(roundRect(Color.rgb(198, 207, 220), dp(8), Color.rgb(198, 207, 220)));
        LinearLayout.LayoutParams grabLp = new LinearLayout.LayoutParams(dp(44), dp(5));
        grabLp.gravity = Gravity.CENTER_HORIZONTAL;
        grabLp.bottomMargin = dp(10);
        sheet.addView(grabber, grabLp);

        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        topLp.bottomMargin = dp(10);
        sheet.addView(top, topLp);

        LinearLayout brandMark = new LinearLayout(getContext());
        brandMark.setGravity(Gravity.CENTER);
        GradientDrawable markBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(242, 201, 112), Color.rgb(164, 102, 35)});
        markBg.setCornerRadius(dp(17));
        brandMark.setBackground(markBg);
        String iconUrl = cfg == null ? "" : String.valueOf(cfg.composerIconUrl == null ? "" : cfg.composerIconUrl).trim();
        if (!iconUrl.isEmpty()) {
            ImageView mark = new ImageView(getContext());
            mark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int iconSize = dp(cfg == null || cfg.composerIconSizeDp <= 0 ? 42 : Math.min(80, cfg.composerIconSizeDp));
            brandMark.addView(mark, new LinearLayout.LayoutParams(iconSize, iconSize));
            loadRemoteImage(mark, iconUrl);
        } else {
            TextView mark = new TextView(getContext());
            mark.setText("✦");
            mark.setTextColor(Color.rgb(37, 29, 24));
            mark.setTextSize(25);
            mark.setTypeface(Typeface.DEFAULT_BOLD);
            mark.setGravity(Gravity.CENTER);
            brandMark.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        top.addView(brandMark);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView eyebrow = new TextView(getContext());
        eyebrow.setText("JEHO  /  BROADCAST CENTER");
        eyebrow.setTextColor(Color.rgb(224, 184, 112));
        eyebrow.setTextSize(9);
        eyebrow.setLetterSpacing(.13f);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(eyebrow);
        TextView title = new TextView(getContext());
        title.setText("إرسال إعلان عام");
        title.setTextColor(dark);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(3);
        heading.addView(title, titleLp);
        top.addView(heading, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(getContext());
        close.setText("×");
        close.setTextColor(Color.rgb(75, 88, 108));
        close.setTextSize(25);
        close.setGravity(Gravity.CENTER);
        close.setBackground(roundRect(Color.rgb(244, 247, 251), dp(14), Color.rgb(220, 228, 238)));
        top.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout fieldHeader = new LinearLayout(getContext());
        fieldHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams fieldHeaderLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        fieldHeaderLp.topMargin = dp(7);
        sheet.addView(fieldHeader, fieldHeaderLp);
        TextView fieldLabel = new TextView(getContext());
        fieldLabel.setText("نص الإعلان");
        fieldLabel.setTextColor(dark);
        fieldLabel.setTextSize(14);
        fieldLabel.setTypeface(Typeface.DEFAULT_BOLD);
        fieldHeader.addView(fieldLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout editorFrame = new FrameLayout(getContext());
        GradientDrawable editorBg = new GradientDrawable();
        editorBg.setColor(inputBgColor);
        editorBg.setCornerRadius(dp(18));
        editorBg.setStroke(dp(1), inputBorderColor);
        editorFrame.setBackground(editorBg);
        LinearLayout.LayoutParams editorFrameLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(112));
        editorFrameLp.topMargin = dp(10);
        sheet.addView(editorFrame, editorFrameLp);

        EditText input = new EditText(getContext());
        input.setHint("اكتب إعلاناً واضحاً ومختصراً ليصل إلى جميع أعضاء JEHO...");
        input.setTextSize(15);
        input.setTextColor(inputTextColor);
        input.setHintTextColor(inputHintColor);
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(6);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setPadding(dp(16), dp(15), dp(16), dp(15));
        input.setLineSpacing(dp(4), 1f);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setSelectAllOnFocus(false);
        input.setVerticalScrollBarEnabled(true);
        FrameLayout.LayoutParams inputLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        editorFrame.addView(input, inputLp);

        LinearLayout meta = new LinearLayout(getContext());
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(dp(3), dp(6), dp(3), 0);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sheet.addView(meta, metaLp);
        TextView quota = new TextView(getContext());
        quota.setText(remaining < 0 ? "●  سيتم التحقق من الحد عند الإرسال" : "●  المتبقي اليوم  " + remaining + " إعلان");
        quota.setTextColor(sendStart);
        quota.setTextSize(11);
        quota.setTypeface(Typeface.DEFAULT_BOLD);
        meta.addView(quota, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView counter = new TextView(getContext());
        counter.setText("0 / " + maxLength + " حرف  •  0 / " + maxWords + " كلمة");
        counter.setTextColor(muted);
        counter.setTextSize(11);
        meta.addView(counter);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s.toString().replaceAll("\\r\\n|\\r|\\n", ", ").replaceAll("[ \\t]+", " ").replaceAll("\\s*,\\s*", ", ").trim();
                int words = current.isEmpty() ? 0 : current.split("\\s+").length;
                counter.setText(current.length() + " / " + maxLength + " حرف  •  " + words + " / " + maxWords + " كلمة");
                counter.setTextColor(current.length() > maxLength || words > maxWords ? Color.rgb(255, 126, 126) : muted);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        View separator = new View(getContext());
        separator.setBackgroundColor(inputBorderColor);
        LinearLayout.LayoutParams separatorLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(1));
        separatorLp.topMargin = dp(11);
        separatorLp.bottomMargin = dp(10);
        sheet.addView(separator, separatorLp);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        sheet.addView(actions, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(44)));

        TextView cancel = new TextView(getContext());
        cancel.setText("إلغاء");
        cancel.setTextColor(cancelTextColor);
        cancel.setTextSize(12);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(roundRect(Color.rgb(246, 248, 252), dp(16), Color.rgb(220, 228, 238)));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, .78f));

        TextView send = new TextView(getContext());
        send.setText("إرسال الإعلان  ✦");
        send.setTextColor(sendTextColor);
        send.setTextSize(13);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setGravity(Gravity.CENTER);
        send.setBackground(roundRectGradient(
                new int[]{Color.rgb(238, 197, 119), Color.rgb(188, 126, 47)}, dp(16)));
        send.setElevation(dp(5));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.22f);
        sendLp.leftMargin = dp(10);
        actions.addView(send, sendLp);

        close.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnClickListener(v -> dialog.dismiss());
        send.setOnClickListener(v -> {
            String message = input.getText() == null ? "" : input.getText().toString().trim();
            if (message.isEmpty()) {
                input.setError("اكتب نص الإعلان أولاً");
                input.requestFocus();
                return;
            }
            String normalizedMessage = message.replaceAll("\\r\\n|\\r|\\n", ", ").replaceAll("[ \\t]+", " ").replaceAll("\\s*,\\s*", ", ").trim();
            if (normalizedMessage.length() > maxLength) {
                input.setError("بعد جمع الأسطر، الحد الأقصى " + maxLength + " حرف");
                input.requestFocus();
                return;
            }
            int wordCount = normalizedMessage.isEmpty() ? 0 : normalizedMessage.split("\\s+").length;
            if (wordCount > maxWords) {
                input.setError("الإعلان يحتوي على " + wordCount + " كلمة، والحد المسموح " + maxWords + " كلمة");
                input.requestFocus();
                return;
            }
            send.setEnabled(false);
            cancel.setEnabled(false);
            send.setText("جارٍ إرسال الإعلان...");
            send.setAlpha(.72f);
            submit(normalizedMessage, dialog, send, cancel);
        });

        android.widget.ScrollView scroll = new android.widget.ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(sheetRoot, new android.widget.ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scroll);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.BOTTOM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams();
            p.copyFrom(window.getAttributes());
            p.width = (int) (getResources().getDisplayMetrics().widthPixels * .94f);
            p.height = (int) (getResources().getDisplayMetrics().heightPixels * .82f);
            p.dimAmount = .66f;
            p.y = dp(6);
            window.setAttributes(p);
        }

        dialog.show();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * .94f),
                    (int) (getResources().getDisplayMetrics().heightPixels * .82f));
            window.setGravity(Gravity.BOTTOM);
        }
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private GradientDrawable roundRectGradient(int[] colors, int radius) { return roundRectGradient(colors, radius, Color.rgb(239, 202, 128)); }

    private GradientDrawable roundRectGradient(int[] colors, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private int colorOr(String value, int fallback) {
        try { return value == null || value.trim().isEmpty() ? fallback : Color.parseColor(value.trim()); }
        catch (Exception ignored) { return fallback; }
    }

    private int withAlpha(int color, int percent) {
        int a = Math.max(0, Math.min(100, percent));
        return Color.argb(Math.round(255f * a / 100f), Color.red(color), Color.green(color), Color.blue(color));
    }

    private void loadRemoteImage(ImageView target, String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.isEmpty()) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000); conn.setReadTimeout(8000); conn.setInstanceFollowRedirects(true);
                conn.setUseCaches(true);
                try (InputStream in = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) target.post(() -> target.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {}
            finally { if (conn != null) conn.disconnect(); }
        }, "jeho-ad-composer-image").start();
    }

    private void submit(String text, Dialog dialog, TextView send, TextView cancel) {
        if (!loading.compareAndSet(false, true)) return;
        send.setEnabled(false);
        Map<String, String> body = new HashMap<>();
        body.put("text", text);
        if (pendingIdempotencyKey == null || !text.equals(pendingIdempotencyText)) {
            pendingIdempotencyKey = UUID.randomUUID().toString();
            pendingIdempotencyText = text;
        }
        body.put("idempotencyKey", pendingIdempotencyKey);
        ContainerProvider.from(getContext()).getRoomApi().createAdvertisement(body)
                .enqueue(new Callback<ApiResponse<AdvertisementDtos.CreateResponse>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.CreateResponse>> call,
                                             Response<ApiResponse<AdvertisementDtos.CreateResponse>> response) {
                loading.set(false);
                // A 2xx response confirms the controller completed the create operation.
                // The response envelope is optional: never report a saved ad as a failure
                // merely because response.body().data is absent.
                if (response.isSuccessful()) {
                    AdvertisementDtos.CreateResponse result = response.body() == null ? null : response.body().data;
                    if (result != null && result.remaining != null) {
                        remaining = Math.max(0, result.remaining.remaining);
                    } else {
                        remaining = Math.max(0, remaining - 1);
                    }
                    pendingIdempotencyKey = null;
                    pendingIdempotencyText = null;
                    dialog.dismiss();
                    refreshQuota();
                    showResultDialog(true, "تم إرسال إعلانك", "");
                } else {
                    send.setEnabled(true);
                    send.setAlpha(1f);
                    cancel.setEnabled(true);
                    send.setText("إعادة المحاولة");
                    String message = "تعذّر إرسال الإعلان. تحقق من البيانات وحاول مرة أخرى.";
                    String rawDetails = "";
                    try {
                        if (response.errorBody() != null) {
                            rawDetails = response.errorBody().string();
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(rawDetails);
                                Object apiMessage = json.opt("message");
                                if (apiMessage instanceof org.json.JSONArray) {
                                    org.json.JSONArray messages = (org.json.JSONArray) apiMessage;
                                    if (messages.length() > 0) message = messages.optString(0, message);
                                } else if (apiMessage instanceof String && !((String) apiMessage).trim().isEmpty()) {
                                    message = (String) apiMessage;
                                }
                                String apiError = json.optString("error", "");
                                if (!apiError.trim().isEmpty() && !message.contains(apiError)) message += "\n" + apiError;
                            } catch (Exception ignored) {
                                if (!rawDetails.trim().isEmpty()) message = rawDetails;
                            }
                        }
                    } catch (Exception ignored) {}
                    // Client errors are definitive. Keep the key on 5xx: commit outcome may be uncertain.
                    if (response.code() >= 400 && response.code() < 500) {
                        pendingIdempotencyKey = null;
                        pendingIdempotencyText = null;
                    }
                    String diagnostic = "رمز الاستجابة: HTTP " + response.code();
                    if (message == null || message.trim().isEmpty()) message = "رفض الخادم طلب الإعلان.";
                    if (!message.contains("HTTP ")) message += "\n" + diagnostic;
                    // If the server only returns the same generic phrase, show its actual payload too.
                    if (rawDetails != null && !rawDetails.trim().isEmpty()
                            && (message.trim().length() < 24 || "تعذّر إرسال الإعلان".equals(message.trim()))) {
                        String payload = rawDetails.trim();
                        if (payload.length() > 700) payload = payload.substring(0, 700) + "…";
                        message += "\nتفاصيل ردّ الخادم: " + payload;
                    }
                    showResultDialog(false, "تعذّر إرسال الإعلان", message);
                    refreshQuota();
                }
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.CreateResponse>> call, Throwable t) {
                loading.set(false);
                send.setEnabled(true);
                send.setAlpha(1f);
                cancel.setEnabled(true);
                send.setText("إعادة المحاولة");
                // The server may have committed before the connection dropped.
                // Keep the same key so a retry returns the original ad instead of creating another.
                String detail = t == null ? "سبب غير معروف" : String.valueOf(t.getLocalizedMessage());
                if (detail == null || detail.trim().isEmpty()) detail = t == null ? "تعذّر تحديد سبب الاتصال" : t.getClass().getSimpleName();
                showResultDialog(false, "تعذّر تأكيد الإرسال",
                        "لم يصل ردّ الخادم إلى التطبيق.\nالتفاصيل: " + detail
                                + "\nرقم الطلب محفوظ؛ أعد المحاولة بأمان دون إنشاء إعلان مكرر.");
                refreshQuota();
            }
        });
    }

    private void showResultDialog(boolean success, String titleText, String messageText) {
        final Dialog resultDialog = new Dialog(getContext());
        resultDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(22), dp(22), dp(22), dp(20));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.WHITE);
        panelBg.setCornerRadius(dp(22));
        panel.setBackground(panelBg);

        TextView mark = new TextView(getContext());
        mark.setText(success ? "✓" : "!");
        mark.setTextSize(28);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setTextColor(Color.WHITE);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable markBg = new GradientDrawable();
        markBg.setColor(success ? Color.rgb(38, 160, 105) : Color.rgb(210, 145, 45));
        markBg.setShape(GradientDrawable.OVAL);
        mark.setBackground(markBg);
        panel.addView(mark, new LinearLayout.LayoutParams(dp(58), dp(58)));

        TextView title = new TextView(getContext());
        title.setText(titleText);
        title.setTextColor(Color.rgb(38, 42, 50));
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(15);
        panel.addView(title, titleLp);

        TextView message = new TextView(getContext());
        message.setText(messageText == null ? "" : messageText);
        message.setVisibility(messageText == null || messageText.trim().isEmpty() ? GONE : VISIBLE);
        message.setTextColor(Color.rgb(111, 117, 128));
        message.setTextSize(13);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        messageLp.topMargin = dp(8);
        panel.addView(message, messageLp);

        TextView ok = new TextView(getContext());
        ok.setText("حسناً");
        ok.setGravity(Gravity.CENTER);
        ok.setTextColor(Color.WHITE);
        ok.setTextSize(14);
        ok.setTypeface(Typeface.DEFAULT_BOLD);
        ok.setClickable(true);
        GradientDrawable okBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(157, 99, 25), Color.rgb(218, 163, 66)});
        okBg.setCornerRadius(dp(13));
        ok.setBackground(okBg);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(46));
        okLp.topMargin = dp(18);
        panel.addView(ok, okLp);

        resultDialog.setContentView(panel);
        Window w = resultDialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams();
            p.copyFrom(w.getAttributes());
            p.width = (int) (getResources().getDisplayMetrics().widthPixels * .84f);
            p.height = WindowManager.LayoutParams.WRAP_CONTENT;
            p.dimAmount = .58f;
            w.setAttributes(p);
        }
        ok.setOnClickListener(v -> resultDialog.dismiss());
        resultDialog.show();
        if (w != null) w.setLayout((int) (getResources().getDisplayMetrics().widthPixels * .84f),
                WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}