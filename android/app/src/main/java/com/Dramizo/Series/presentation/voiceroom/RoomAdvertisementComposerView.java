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
        openButton.setText("✦ إعلان");
        openButton.setTextColor(Color.WHITE);
        openButton.setTextSize(12);
        openButton.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(164, 104, 26), Color.rgb(221, 166, 66)});
        bg.setCornerRadius(dp(22));
        openButton.setBackground(bg);
        openButton.setElevation(dp(5));
        openButton.setPadding(dp(16), 0, dp(16), 0);
        openButton.setLetterSpacing(0.02f);
        bg.setStroke(dp(1), Color.rgb(244, 206, 126));
        addView(openButton, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(38), Gravity.CENTER));
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
                    openButton.setText(remaining > 0 ? "إعلان · " + remaining : "انتهت إعلاناتك اليوم");
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

        final int ink = Color.rgb(27, 35, 49);
        final int muted = Color.rgb(111, 122, 139);
        final int gold = Color.rgb(171, 119, 35);
        final int surface = Color.rgb(255, 255, 255);
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(23), dp(24), dp(22));
        root.setMinimumWidth(dp(300));
        GradientDrawable card = new GradientDrawable();
        card.setColor(surface);
        card.setCornerRadius(dp(28));
        card.setStroke(dp(1), Color.rgb(226, 213, 188));
        root.setBackground(card);
        root.setElevation(dp(16));

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView emblem = new TextView(getContext());
        emblem.setText("📣");
        emblem.setTextSize(23);
        emblem.setIncludeFontPadding(false);
        emblem.setGravity(Gravity.CENTER);
        GradientDrawable emblemBg = new GradientDrawable();
        emblemBg.setColor(Color.rgb(255, 244, 220));
        emblemBg.setStroke(dp(1), Color.rgb(242, 220, 174));
        emblemBg.setCornerRadius(dp(15));
        emblem.setBackground(emblemBg);
        header.addView(emblem, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(getContext());
        title.setText("إعلان عام لكل الغرف");
        title.setTextColor(ink);
        title.setTextSize(20);
        title.setLetterSpacing(-0.01f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(title);
        TextView subtitle = new TextView(getContext());
        subtitle.setText("إعلان موحّد يصل إلى جميع الغرف");
        subtitle.setTextColor(muted);
        subtitle.setTextSize(12);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(3);
        heading.addView(subtitle, subParams);
        header.addView(heading, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(getContext());
        close.setText("×");
        close.setTextSize(25);
        close.setTextColor(muted);
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(34), dp(40)));
        root.addView(header);

        View headerDivider = new View(getContext());
        headerDivider.setBackgroundColor(Color.rgb(238, 229, 210));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(17);
        root.addView(headerDivider, dividerParams);

        TextView info = new TextView(getContext());
        info.setText("سيبدأ الإعلان بعد 10 ثوانٍ، ثم يظهر في جميع الغرف النشطة.");
        info.setTextColor(Color.rgb(111, 83, 35));
        info.setTextSize(13);
        info.setLineSpacing(dp(2), 1f);
        info.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(Color.rgb(255, 248, 232));
        infoBg.setStroke(dp(1), Color.rgb(244, 229, 198));
        infoBg.setCornerRadius(dp(13));
        info.setBackground(infoBg);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        infoParams.topMargin = dp(16);
        root.addView(info, infoParams);

        TextView fieldLabel = new TextView(getContext());
        fieldLabel.setText("نص الإعلان");
        fieldLabel.setTextColor(ink);
        fieldLabel.setTextSize(14);
        fieldLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(17);
        root.addView(fieldLabel, labelParams);

        EditText input = new EditText(getContext());
        input.setHint("اكتب رسالتك التي تريد إيصالها للجميع...");
        input.setTextSize(15);
        input.setTextColor(ink);
        input.setHintTextColor(Color.rgb(165, 169, 177));
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setLineSpacing(dp(3), 1f);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setPadding(dp(13), dp(12), dp(13), dp(12));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.WHITE);
        inputBg.setCornerRadius(dp(14));
        inputBg.setStroke(dp(1), Color.rgb(214, 219, 226));
        input.setBackground(inputBg);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(138));
        inputParams.topMargin = dp(8);
        root.addView(input, inputParams);

        LinearLayout meta = new LinearLayout(getContext());
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(dp(2), dp(9), dp(2), 0);
        TextView quota = new TextView(getContext());
        quota.setText("● المتبقي اليوم: " + remaining + " إعلان");
        quota.setTextColor(gold);
        quota.setTextSize(12);
        quota.setTypeface(Typeface.DEFAULT_BOLD);
        meta.addView(quota, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView counter = new TextView(getContext());
        counter.setText("0 / " + maxLength);
        counter.setTextColor(muted);
        counter.setTextSize(11);
        meta.addView(counter);
        root.addView(meta);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                counter.setText(s.length() + " / " + maxLength);
                counter.setTextColor(s.length() > maxLength ? Color.RED : muted);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(54));
        actionsParams.topMargin = dp(20);

        TextView cancel = new TextView(getContext());
        cancel.setText("إلغاء");
        cancel.setGravity(Gravity.CENTER);
        cancel.setIncludeFontPadding(false);
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setTextSize(13);
        cancel.setTextColor(Color.rgb(91, 96, 106));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.rgb(238, 239, 241));
        cancelBg.setCornerRadius(dp(16));
        cancelBg.setStroke(dp(1), Color.rgb(224, 226, 230));
        cancel.setBackground(cancelBg);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.8f));

        TextView send = new TextView(getContext());
        send.setText("إرسال للجميع  ➤");
        send.setGravity(Gravity.CENTER);
        send.setIncludeFontPadding(false);
        send.setClickable(true);
        send.setFocusable(true);
        send.setTextSize(13);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setTextColor(Color.WHITE);
        GradientDrawable sendBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(157, 99, 25), Color.rgb(218, 163, 66)});
        sendBg.setCornerRadius(dp(16));
        sendBg.setStroke(dp(1), Color.rgb(236, 197, 119));
        send.setBackground(sendBg);
        send.setElevation(dp(3));
        send.setLetterSpacing(0.015f);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.2f);
        sendParams.leftMargin = dp(9);
        actions.addView(send, sendParams);
        root.addView(actions, actionsParams);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(window.getAttributes());
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.93f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.dimAmount = 0.62f;
            window.setAttributes(lp);
        }
        close.setOnClickListener(v -> dialog.dismiss());
        cancel.setOnClickListener(v -> dialog.dismiss());
        send.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            if (text.isEmpty()) { input.setError("اكتب نص الإعلان أولاً"); return; }
            if (text.length() > maxLength) { input.setError("الحد الأقصى " + maxLength + " حرف"); return; }
            send.setEnabled(false);
            cancel.setEnabled(false);
            send.setText("جارٍ الإرسال...");
            submit(text, dialog, send);
        });
        dialog.show();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.93f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
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
                    try {
                        if (response.errorBody() != null) {
                            String raw = response.errorBody().string();
                            try {
                                org.json.JSONObject json = new org.json.JSONObject(raw);
                                Object apiMessage = json.opt("message");
                                if (apiMessage instanceof org.json.JSONArray) {
                                    org.json.JSONArray messages = (org.json.JSONArray) apiMessage;
                                    if (messages.length() > 0) message = messages.optString(0, message);
                                } else if (apiMessage instanceof String && !((String) apiMessage).trim().isEmpty()) {
                                    message = (String) apiMessage;
                                }
                            } catch (Exception ignored) {
                                if (!raw.trim().isEmpty() && !raw.trim().startsWith("{")) message = raw;
                            }
                        }
                    } catch (Exception ignored) {}
                    // Client errors are definitive. Keep the key on 5xx: commit outcome may be uncertain.
                    if (response.code() >= 400 && response.code() < 500) {
                        pendingIdempotencyKey = null;
                        pendingIdempotencyText = null;
                    }
                    String diagnostic = "رمز الاستجابة: HTTP " + response.code();
                    if (message == null || message.trim().isEmpty()) {
                        message = "رفض الخادم طلب الإعلان. " + diagnostic;
                    } else if (!message.contains("HTTP ")) {
                        message = message + "\\n" + diagnostic;
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
                showResultDialog(false, "تعذّر تأكيد الإرسال",
                        "انقطع الاتصال قبل وصول التأكيد. أعد المحاولة؛ سنستخدم رقم الطلب نفسه لمنع تكرار الإعلان.");
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
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
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
