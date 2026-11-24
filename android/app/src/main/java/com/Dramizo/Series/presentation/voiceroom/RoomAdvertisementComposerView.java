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
import android.widget.Button;
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
    private Button openButton;
    private int remaining = 0;
    private int maxLength = 180;
    private boolean featureEnabled = false;

    public RoomAdvertisementComposerView(Context context, AttributeSet attrs) { this(context); }
    public RoomAdvertisementComposerView(Context context, AttributeSet attrs, int defStyleAttr) { this(context); }

    public RoomAdvertisementComposerView(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        openButton = new Button(context);
        openButton.setAllCaps(false);
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
        openButton.setPadding(dp(14), 0, dp(14), 0);
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

        final int ink = Color.rgb(36, 39, 47);
        final int muted = Color.rgb(133, 139, 151);
        final int gold = Color.rgb(196, 139, 45);
        final int surface = Color.rgb(250, 249, 246);
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable card = new GradientDrawable();
        card.setColor(surface);
        card.setCornerRadius(dp(24));
        root.setBackground(card);
        root.setElevation(dp(16));

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView emblem = new TextView(getContext());
        emblem.setText("📣");
        emblem.setTextSize(22);
        emblem.setGravity(Gravity.CENTER);
        GradientDrawable emblemBg = new GradientDrawable();
        emblemBg.setColor(Color.rgb(255, 241, 211));
        emblemBg.setCornerRadius(dp(15));
        emblem.setBackground(emblemBg);
        header.addView(emblem, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(getContext());
        title.setText("إعلان عام لكل الغرف");
        title.setTextColor(ink);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(title);
        TextView subtitle = new TextView(getContext());
        subtitle.setText("رسالتك ستصل إلى مجتمع JEHO بالكامل");
        subtitle.setTextColor(muted);
        subtitle.setTextSize(11);
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

        TextView info = new TextView(getContext());
        info.setText("سيبدأ ظهور الإعلان بعد 10 ثوانٍ من الإرسال، ويُعرض في جميع الرومات النشطة.");
        info.setTextColor(Color.rgb(111, 83, 35));
        info.setTextSize(12);
        info.setLineSpacing(dp(2), 1f);
        info.setPadding(dp(12), dp(11), dp(12), dp(11));
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(Color.rgb(255, 246, 226));
        infoBg.setCornerRadius(dp(13));
        info.setBackground(infoBg);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        infoParams.topMargin = dp(16);
        root.addView(info, infoParams);

        TextView fieldLabel = new TextView(getContext());
        fieldLabel.setText("نص الإعلان");
        fieldLabel.setTextColor(ink);
        fieldLabel.setTextSize(13);
        fieldLabel.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(17);
        root.addView(fieldLabel, labelParams);

        EditText input = new EditText(getContext());
        input.setHint("اكتب رسالتك التي تريد إيصالها للجميع...");
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(Color.rgb(165, 169, 177));
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(4);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setPadding(dp(13), dp(12), dp(13), dp(12));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.WHITE);
        inputBg.setCornerRadius(dp(14));
        inputBg.setStroke(dp(1), Color.rgb(229, 226, 219));
        input.setBackground(inputBg);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(112));
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
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48));
        actionsParams.topMargin = dp(18);

        Button cancel = new Button(getContext());
        cancel.setText("إلغاء");
        cancel.setAllCaps(false);
        cancel.setTextSize(13);
        cancel.setTextColor(Color.rgb(91, 96, 106));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.rgb(238, 239, 241));
        cancelBg.setCornerRadius(dp(13));
        cancel.setBackground(cancelBg);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 0.8f));

        Button send = new Button(getContext());
        send.setText("إرسال للجميع  ➤");
        send.setAllCaps(false);
        send.setTextSize(13);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setTextColor(Color.WHITE);
        GradientDrawable sendBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(157, 99, 25), Color.rgb(218, 163, 66)});
        sendBg.setCornerRadius(dp(13));
        send.setBackground(sendBg);
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
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
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
            window.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.90f),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void submit(String text, AlertDialog dialog, Button send) {
        if (!loading.compareAndSet(false, true)) return;
        openButton.setEnabled(false);
        Map<String, String> body = new HashMap<>();
        body.put("text", text);
        body.put("idempotencyKey", UUID.randomUUID().toString());
        ContainerProvider.from(getContext()).getRoomApi().createAdvertisement(body)
                .enqueue(new Callback<ApiResponse<AdvertisementDtos.CreateResponse>>() {
            @Override public void onResponse(Call<ApiResponse<AdvertisementDtos.CreateResponse>> call,
                                             Response<ApiResponse<AdvertisementDtos.CreateResponse>> response) {
                loading.set(false);
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    AdvertisementDtos.CreateResponse result = response.body().data;
                    remaining = result.remaining == null ? Math.max(0, remaining - (result.duplicate ? 0 : 1)) : result.remaining.remaining;
                    Toast.makeText(getContext(), "تم إرسال الإعلان إلى الطابور العام", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    refreshQuota();
                } else {
                    send.setEnabled(true);
                    send.setText("إرسال للجميع");
                    String message = "تعذر إرسال الإعلان، حاول مرة أخرى";
                    try { if (response.errorBody() != null) message = response.errorBody().string(); } catch (Exception ignored) {}
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                    refreshQuota();
                }
            }
            @Override public void onFailure(Call<ApiResponse<AdvertisementDtos.CreateResponse>> call, Throwable t) {
                loading.set(false);
                send.setEnabled(true);
                send.setText("إرسال للجميع");
                Toast.makeText(getContext(), "تعذر الاتصال بالخادم، لم يتم تأكيد الإرسال", Toast.LENGTH_LONG).show();
                refreshQuota();
            }
        });
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
