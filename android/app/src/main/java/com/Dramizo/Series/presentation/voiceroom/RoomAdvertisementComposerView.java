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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
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
    private TextView openButton;
    private int remaining = 0;
    private int maxLength = 180;
    private boolean featureEnabled = false;
    // Reuse this key whenever the request outcome is uncertain; the backend deduplicates it.
    private String pendingIdempotencyKey;
    private String pendingIdempotencyText;

    public RoomAdvertisementComposerView(Context context, AttributeSet attrs) { this(context); }
    public RoomAdvertisementComposerView(Context context, AttributeSet attrs, int defStyleAttr) { this(context); }

    public RoomAdvertisementComposerView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        openButton = new TextView(context);
        openButton.setGravity(Gravity.CENTER);
        openButton.setIncludeFontPadding(false);
        openButton.setClickable(true);
        openButton.setFocusable(true);
        openButton.setText("✦  إعلان عام");
        openButton.setTextColor(Color.WHITE);
        openButton.setTextSize(13);
        openButton.setTypeface(Typeface.DEFAULT_BOLD);
        openButton.setGravity(Gravity.CENTER);
        openButton.setMinWidth(dp(142));
        openButton.setMinHeight(dp(46));
        openButton.setBackground(roundRectGradient(
                new int[]{Color.rgb(105, 65, 23), Color.rgb(184, 126, 38), Color.rgb(229, 184, 91)},
                dp(24)));
        openButton.setElevation(dp(9));
        openButton.setPadding(dp(19), 0, dp(19), 0);
        openButton.setLetterSpacing(0.015f);
        openButton.setContentDescription("فتح نافذة إرسال إعلان عام إلى جميع الغرف");
        openButton.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(
                context, android.R.animator.button_state_list_animator));
        FrameLayout.LayoutParams buttonLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, dp(46), Gravity.CENTER);
        addView(openButton, buttonLp);
        openButton.setOnClickListener(v -> openComposer());
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
            featureEnabled = next.enabled && next.sendButtonEnabled;
            maxLength = next.maxTextLength > 0 ? next.maxTextLength : 180;
            if (!featureEnabled) {
                post(() -> setVisibility(GONE));
            } else {
                refreshRemaining();
            }
        } catch (Exception ignored) {}
    }

    private void refreshQuota() {
        ContainerProvider.from(getContext()).getRoomApi().advertisementConfig()
                .enqueue(new Callback<ApiResponse<AdvertisementDtos.Config>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Config>> call,
                                             Response<ApiResponse<AdvertisementDtos.Config>> response) {
                AdvertisementDtos.Config cfg = response.body() == null ? null : response.body().data;
                if (cfg == null) { post(() -> setVisibility(GONE)); return; }
                featureEnabled = cfg.enabled && cfg.sendButtonEnabled;
                maxLength = cfg.maxTextLength > 0 ? cfg.maxTextLength : 180;
                refreshRemaining();
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Config>> call, Throwable t) {
                post(() -> setVisibility(GONE));
            }
        });
    }

    private void refreshRemaining() {
        ContainerProvider.from(getContext()).getRoomApi().advertisementRemaining()
                .enqueue(new Callback<ApiResponse<AdvertisementDtos.Remaining>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.Remaining>> call,
                                             Response<ApiResponse<AdvertisementDtos.Remaining>> response) {
                AdvertisementDtos.Remaining data = response.body() == null ? null : response.body().data;
                if (data == null) { post(() -> setVisibility(GONE)); return; }
                remaining = Math.max(0, data.remaining);
                post(() -> {
                    setVisibility(featureEnabled ? VISIBLE : GONE);
                    openButton.setEnabled(remaining > 0 && !loading.get());
                    openButton.setAlpha(remaining > 0 ? 1f : .55f);
                    openButton.setText(remaining > 0 ? "✦  إعلان عام  ·  " + remaining : "اكتملت حصة اليوم");
                });
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.Remaining>> call, Throwable t) {
                post(() -> setVisibility(GONE));
            }
        });
    }

    private void openComposer() {
        if (!featureEnabled || remaining <= 0 || loading.get()) {
            refreshQuota();
            if (remaining <= 0) Toast.makeText(getContext(), "انتهت إعلاناتك اليوم", Toast.LENGTH_SHORT).show();
            return;
        }

        final int ink = Color.rgb(25, 32, 45);
        final int muted = Color.rgb(112, 121, 136);
        final int gold = Color.rgb(173, 119, 35);
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Premium composer sheet: a deliberately different layout from the old basic alert.
        LinearLayout sheet = new LinearLayout(getContext());
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(12), dp(20), dp(22));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(Color.WHITE);
        sheetBg.setCornerRadius(dp(28));
        sheetBg.setStroke(dp(1), Color.rgb(232, 224, 209));
        sheet.setBackground(sheetBg);
        sheet.setElevation(dp(22));

        View grabber = new View(getContext());
        GradientDrawable grabBg = new GradientDrawable();
        grabBg.setColor(Color.rgb(210, 214, 220));
        grabBg.setCornerRadius(dp(8));
        grabber.setBackground(grabBg);
        LinearLayout.LayoutParams grabLp = new LinearLayout.LayoutParams(dp(42), dp(5));
        grabLp.gravity = Gravity.CENTER_HORIZONTAL;
        grabLp.bottomMargin = dp(18);
        sheet.addView(grabber, grabLp);

        LinearLayout hero = new LinearLayout(getContext());
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(14), dp(16));
        GradientDrawable heroBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(35, 43, 60), Color.rgb(67, 75, 91)});
        heroBg.setCornerRadius(dp(20));
        hero.setBackground(heroBg);

        LinearLayout heroText = new LinearLayout(getContext());
        heroText.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = new TextView(getContext());
        eyebrow.setText("JEHO  •  ROOM BROADCAST");
        eyebrow.setTextColor(Color.rgb(236, 202, 137));
        eyebrow.setTextSize(9);
        eyebrow.setLetterSpacing(0.12f);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        heroText.addView(eyebrow);

        TextView title = new TextView(getContext());
        title.setText("إعلان يصل للجميع");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(5);
        heroText.addView(title, titleLp);

        TextView subtitle = new TextView(getContext());
        subtitle.setText("رسالة واحدة • جميع الغرف • في نفس التوقيت");
        subtitle.setTextColor(Color.rgb(218, 222, 230));
        subtitle.setTextSize(11);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        heroText.addView(subtitle, subLp);
        hero.addView(heroText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(getContext());
        close.setText("×");
        close.setTextColor(Color.WHITE);
        close.setTextSize(26);
        close.setGravity(Gravity.CENTER);
        close.setBackground(roundRect(Color.rgb(86, 94, 109), dp(14), Color.rgb(112, 119, 132)));
        hero.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));
        sheet.addView(hero);

        LinearLayout delivery = new LinearLayout(getContext());
        delivery.setGravity(Gravity.CENTER_VERTICAL);
        delivery.setPadding(dp(13), dp(12), dp(13), dp(12));
        delivery.setBackground(roundRect(Color.rgb(255, 249, 237), dp(15), Color.rgb(244, 229, 199)));
        LinearLayout.LayoutParams deliveryLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        deliveryLp.topMargin = dp(14);
        sheet.addView(delivery, deliveryLp);

        TextView deliveryIcon = new TextView(getContext());
        deliveryIcon.setText("◷");
        deliveryIcon.setTextColor(gold);
        deliveryIcon.setTextSize(22);
        deliveryIcon.setGravity(Gravity.CENTER);
        delivery.addView(deliveryIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout deliveryText = new LinearLayout(getContext());
        deliveryText.setOrientation(LinearLayout.VERTICAL);
        deliveryText.setPadding(dp(9), 0, 0, 0);
        TextView deliveryTitle = new TextView(getContext());
        deliveryTitle.setText("موعد الظهور");
        deliveryTitle.setTextColor(ink);
        deliveryTitle.setTextSize(12);
        deliveryTitle.setTypeface(Typeface.DEFAULT_BOLD);
        deliveryText.addView(deliveryTitle);
        TextView deliveryDesc = new TextView(getContext());
        deliveryDesc.setText("يبدأ بعد 10 ثوانٍ من قبول الطلب، ثم يُبث في كل الغرف.");
        deliveryDesc.setTextColor(Color.rgb(117, 99, 66));
        deliveryDesc.setTextSize(11);
        LinearLayout.LayoutParams deliveryDescLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        deliveryDescLp.topMargin = dp(3);
        deliveryText.addView(deliveryDesc, deliveryDescLp);
        delivery.addView(deliveryText, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout fieldHeader = new LinearLayout(getContext());
        fieldHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams fieldHeaderLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        fieldHeaderLp.topMargin = dp(21);
        sheet.addView(fieldHeader, fieldHeaderLp);

        TextView fieldLabel = new TextView(getContext());
        fieldLabel.setText("محتوى الإعلان");
        fieldLabel.setTextColor(ink);
        fieldLabel.setTextSize(14);
        fieldLabel.setTypeface(Typeface.DEFAULT_BOLD);
        fieldHeader.addView(fieldLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView fieldHint = new TextView(getContext());
        fieldHint.setText("رسالة عامة");
        fieldHint.setTextColor(muted);
        fieldHint.setTextSize(10);
        fieldHeader.addView(fieldHint);

        EditText input = new EditText(getContext());
        input.setHint("اكتب إعلانك بوضوح ليصل إلى جميع الموجودين...");
        input.setTextSize(15);
        input.setTextColor(ink);
        input.setHintTextColor(Color.rgb(157, 164, 174));
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setPadding(dp(15), dp(14), dp(15), dp(14));
        input.setLineSpacing(dp(3), 1f);
        input.setBackground(roundRect(Color.rgb(249, 250, 252), dp(16), Color.rgb(218, 223, 231)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(132));
        inputLp.topMargin = dp(9);
        sheet.addView(input, inputLp);

        LinearLayout footerMeta = new LinearLayout(getContext());
        footerMeta.setGravity(Gravity.CENTER_VERTICAL);
        footerMeta.setPadding(dp(2), dp(10), dp(2), 0);
        TextView quota = new TextView(getContext());
        quota.setText("●  المتبقي اليوم: " + remaining + " إعلان");
        quota.setTextColor(gold);
        quota.setTextSize(11);
        quota.setTypeface(Typeface.DEFAULT_BOLD);
        footerMeta.addView(quota, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView counter = new TextView(getContext());
        counter.setText("0 / " + maxLength);
        counter.setTextColor(muted);
        counter.setTextSize(11);
        footerMeta.addView(counter);
        sheet.addView(footerMeta);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                counter.setText(s.length() + " / " + maxLength);
                counter.setTextColor(s.length() > maxLength ? Color.rgb(196, 55, 55) : muted);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(54));
        actionsLp.topMargin = dp(20);

        TextView cancel = new TextView(getContext());
        cancel.setText("إلغاء");
        cancel.setTextColor(Color.rgb(88, 96, 109));
        cancel.setTextSize(13);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(roundRect(Color.rgb(241, 243, 246), dp(15), Color.rgb(228, 231, 236)));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.78f));

        TextView send = new TextView(getContext());
        send.setText("إرسال الإعلان  ➊");
        send.setTextColor(Color.WHITE);
        send.setTextSize(14);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setGravity(Gravity.CENTER);
        send.setBackground(roundRectGradient(new int[]{Color.rgb(151, 96, 26), Color.rgb(218, 164, 67)}, dp(15)));
        send.setElevation(dp(4));
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.22f);
        sendLp.leftMargin = dp(10);
        actions.addView(send, sendLp);
        sheet.addView(actions, actionsLp);

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setGravity(Gravity.BOTTOM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams();
            p.copyFrom(window.getAttributes());
            p.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.96f);
            p.height = WindowManager.LayoutParams.WRAP_CONTENT;
            p.dimAmount = 0.58f;
            p.y = dp(8);
            window.setAttributes(p);
        }

        close.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnClickListener(v -> dialog.dismiss());
        send.setOnClickListener(v -> {
            String message = input.getText() == null ? "" : input.getText().toString().trim();
            if (message.isEmpty()) { input.setError("اكتب نص الإعلان أولاً"); input.requestFocus(); return; }
            if (message.length() > maxLength) { input.setError("الحد الأقصى " + maxLength + " حرف"); input.requestFocus(); return; }
            send.setEnabled(false);
            cancel.setEnabled(false);
            send.setText("جارٍ إرسال إعلانك...");
            submit(message, dialog, send);
        });

        dialog.show();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.96f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    private GradientDrawable roundRect(int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), strokeColor);
        return d;
    }

    private GradientDrawable roundRectGradient(int[] colors, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(radius);
        d.setStroke(dp(1), Color.rgb(239, 202, 128));
        return d;
    }

    private void submit(String text, Dialog dialog, TextView send) {
        if (!loading.compareAndSet(false, true)) return;
        openButton.setEnabled(false);
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
                    showResultDialog(true, "تم إرسال الإعلان بنجاح",
                            "تم قبول إعلانك وإضافته إلى قائمة الإعلانات العامة. سيبدأ عرضه حسب توقيت النظام.");
                } else {
                    send.setEnabled(true);
                    send.setText("إرسال للجميع");
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
        message.setText(messageText);
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