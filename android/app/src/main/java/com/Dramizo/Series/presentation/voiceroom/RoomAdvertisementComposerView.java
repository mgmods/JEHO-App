package com.Dramizo.Series.presentation.voiceroom;

import android.app.AlertDialog;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Room-wide advertisement composer. All quota and permission decisions remain server-authoritative. */
public final class RoomAdvertisementComposerView extends FrameLayout {
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
        openButton.setText("إعلان");
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
        refreshQuota();
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
        final LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView intro = new TextView(getContext());
        intro.setText("سيصل إعلانك إلى جميع الرومات بعد 10 ثوانٍ من الإرسال.");
        intro.setTextColor(Color.DKGRAY);
        intro.setTextSize(13);
        panel.addView(intro);
        EditText input = new EditText(getContext());
        input.setHint("اكتب إعلانك هنا");
        input.setTextSize(15);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setGravity(Gravity.TOP | Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        panel.addView(input, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(112)));
        TextView counter = new TextView(getContext());
        counter.setText("0 / " + maxLength);
        counter.setTextColor(Color.GRAY);
        counter.setGravity(Gravity.END);
        panel.addView(counter);
        TextView quota = new TextView(getContext());
        quota.setText("المتبقي اليوم: " + remaining + " إعلان");
        quota.setTextColor(Color.rgb(142, 93, 20));
        panel.addView(quota);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                counter.setText(s.length() + " / " + maxLength);
                counter.setTextColor(s.length() > maxLength ? Color.RED : Color.GRAY);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("إرسال إعلان عام")
                .setView(panel)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إرسال للجميع", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            send.setTextColor(Color.rgb(151, 94, 17));
            send.setOnClickListener(v -> {
                String text = input.getText() == null ? "" : input.getText().toString().trim();
                if (text.isEmpty()) { input.setError("اكتب نص الإعلان"); return; }
                if (text.length() > maxLength) { input.setError("الحد الأقصى " + maxLength + " حرف"); return; }
                send.setEnabled(false);
                send.setText("جارٍ الإرسال...");
                submit(text, dialog, send);
            });
        });
        dialog.show();
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
