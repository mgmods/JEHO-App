package com.Dramizo.Series.presentation.invite;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.InviteDtos;
import com.Dramizo.Series.databinding.ActivityInvitationBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.InviteReferralHelper;

import java.util.Collections;
import java.util.Locale;

/** Mikoo-style invite / promo code page with server bind. */
public class InvitationActivity extends ThemedActivity {
    public static final String EXTRA_CODE = "invite_code";
    public static final String EXTRA_AUTO_BIND = "auto_bind_invite";

    private ActivityInvitationBinding binding;
    private AppContainer c;
    private String shareCode = "----";
    @Nullable private String pendingBindCode;
    private boolean autoBindRequested;
    private boolean autoBindStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityInvitationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);

        pendingBindCode = getIntent() != null
                ? getIntent().getStringExtra(EXTRA_CODE) : null;
        if (pendingBindCode == null || pendingBindCode.trim().isEmpty()) {
            pendingBindCode = InviteReferralHelper.peekPendingCode(this);
        }
        autoBindRequested = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_AUTO_BIND, false);
        if (!autoBindRequested && InviteReferralHelper.peekPendingCode(this) != null) {
            autoBindRequested = true;
        }

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnInviteHelp.setOnClickListener(v ->
                Toast.makeText(this,
                        "ادعُ أصدقاءك برمزك (public ID). عند إدخال الرمز مرة واحدة: أنت +100 والصديق +50 كوينز (قابل للتغيير من الإعدادات).",
                        Toast.LENGTH_LONG).show());
        binding.btnShareInvite.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,
                    InviteReferralHelper.shareMessage(this, shareCode));
            startActivity(Intent.createChooser(send, "دعوة الأصدقاء"));
        });
        binding.btnEnterInvite.setOnClickListener(v -> showEnterCodeDialog());
        binding.tvInviteCode.setOnLongClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("invite", shareCode));
                Toast.makeText(this, "تم نسخ الرمز", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        loadMe();
    }

    private void loadMe() {
        c.getIoExecutor().execute(() -> {
            Result<InviteDtos.InviteMeDto> r = ApiCall.execute(c.getInviteApi().me());
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                if (!r.success || r.data == null) {
                    Toast.makeText(this,
                            r.error != null ? r.error : "تعذر تحميل رمز الدعوة",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                bindMe(r.data);
                maybeAutoBind(r.data);
            });
        });
    }

    private void maybeAutoBind(InviteDtos.InviteMeDto data) {
        if (autoBindStarted || !autoBindRequested) return;
        if (pendingBindCode == null || pendingBindCode.trim().isEmpty()) return;
        if (!data.canBind) {
            // Already bound — drop stale referrer so we don't reopen forever.
            InviteReferralHelper.clearPendingCode(this);
            return;
        }
        autoBindStarted = true;
        String code = pendingBindCode.trim();
        Toast.makeText(this, "جارٍ تطبيق رمز الدعوة…", Toast.LENGTH_SHORT).show();
        bindCode(code);
    }

    private void bindMe(InviteDtos.InviteMeDto data) {
        shareCode = data.code != null && !data.code.isEmpty() ? data.code : "----";
        binding.tvInviteCode.setText(shareCode);
            binding.tvInvitePeople.setText(String.valueOf(Math.max(0, data.invitedCount)));
        binding.tvInviteCoins.setText(String.format(Locale.US, "%,d كوينز",
                Math.max(0L, data.totalCoins)));
        binding.btnEnterInvite.setEnabled(data.canBind);
        binding.btnEnterInvite.setAlpha(data.canBind ? 1f : 0.45f);
        if (!data.canBind && data.invitedBy != null) {
            String name = data.invitedBy.displayName != null
                    ? data.invitedBy.displayName : "صديق";
            binding.btnEnterInvite.setText("مرتبط بدعوة " + name);
        } else {
            binding.btnEnterInvite.setText("أدخل رمز الدعوة");
        }
    }

    private void showEnterCodeDialog() {
        EditText input = new EditText(this);
        input.setHint("أدخل رمز الدعوة");
        input.setPadding(48, 32, 48, 32);
        if (pendingBindCode != null && !pendingBindCode.isEmpty()) {
            input.setText(pendingBindCode);
            input.setSelection(pendingBindCode.length());
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("رمز الدعوة")
                .setView(input)
                .setPositiveButton("تأكيد", (d, w) -> {
                    String entered = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    if (entered.isEmpty()) {
                        Toast.makeText(this, "أدخل رمزاً صالحاً", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    bindCode(entered);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void bindCode(String code) {
        Toast.makeText(this, "جارٍ الربط...", Toast.LENGTH_SHORT).show();
        c.getIoExecutor().execute(() -> {
            Result<InviteDtos.InviteMeDto> r = ApiCall.execute(
                    c.getInviteApi().bind(Collections.singletonMap("code", code)));
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                if (!r.success || r.data == null) {
                    Toast.makeText(this,
                            r.error != null ? r.error : "فشل ربط الرمز",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                InviteReferralHelper.clearPendingCode(this);
                pendingBindCode = null;
                bindMe(r.data);
                int bonus = r.data.rewards != null ? r.data.rewards.inviteeCoins : 0;
                Toast.makeText(this,
                        bonus > 0
                                ? "تم الربط بنجاح · +" + bonus + " كوينز"
                                : "تم ربط رمز الدعوة بنجاح",
                        Toast.LENGTH_LONG).show();
            });
        });
    }
}
