package com.Dramizo.Series.presentation.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityCustomerSupportBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Customer support channels (WhatsApp / Telegram / Instagram / email / phone). */
public class CustomerSupportActivity extends ThemedActivity {
    private ActivityCustomerSupportBinding binding;
    private AppContainer c;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerSupportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);
        binding.btnBack.setOnClickListener(v -> navigateUp());
        loadSupport();
    }

    private void loadSupport() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.SupportConfigDto> r = ApiCall.execute(c.getConfigApi().support());
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                if (!r.success || r.data == null) {
                    bindChannels(null);
                    Toast.makeText(this, R.string.customer_support_load_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                bindChannels(r.data);
            });
        });
    }

    private void bindChannels(@Nullable MiscDtos.SupportConfigDto cfg) {
        String wa = cfg != null ? trim(cfg.whatsapp) : "";
        String tg = cfg != null ? trim(cfg.telegram) : "";
        String ig = cfg != null ? trim(cfg.instagram) : "";
        String email = cfg != null ? trim(cfg.email) : "";
        String phone = cfg != null ? trim(cfg.phone) : "";

        boolean any = false;
        any |= showRow(binding.rowWhatsapp, binding.divWhatsapp, binding.tvWhatsapp, wa,
                v -> openWhatsApp(wa));
        any |= showRow(binding.rowTelegram, binding.divTelegram, binding.tvTelegram, tg,
                v -> openTelegram(tg));
        any |= showRow(binding.rowInstagram, binding.divInstagram, binding.tvInstagram, ig,
                v -> openInstagram(ig));
        any |= showRow(binding.rowEmail, binding.divEmail, binding.tvEmail, email,
                v -> openEmail(email));
        // Phone is last — no divider after it
        boolean phoneShown = showRow(binding.rowPhone, null, binding.tvPhone, phone,
                v -> openPhone(phone));
        any |= phoneShown;

        // Hide trailing dividers when later rows are gone
        refreshDividers(wa, tg, ig, email, phone);

        binding.tvEmpty.setVisibility(any ? View.GONE : View.VISIBLE);
        binding.cardChannels.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private void refreshDividers(String wa, String tg, String ig, String email, String phone) {
        boolean hasTg = !tg.isEmpty();
        boolean hasIg = !ig.isEmpty();
        boolean hasEmail = !email.isEmpty();
        boolean hasPhone = !phone.isEmpty();
        binding.divWhatsapp.setVisibility(!wa.isEmpty() && (hasTg || hasIg || hasEmail || hasPhone)
                ? View.VISIBLE : View.GONE);
        binding.divTelegram.setVisibility(hasTg && (hasIg || hasEmail || hasPhone)
                ? View.VISIBLE : View.GONE);
        binding.divInstagram.setVisibility(hasIg && (hasEmail || hasPhone)
                ? View.VISIBLE : View.GONE);
        binding.divEmail.setVisibility(hasEmail && hasPhone ? View.VISIBLE : View.GONE);
    }

    private boolean showRow(View row, @Nullable View divider, android.widget.TextView valueView,
                            String value, View.OnClickListener click) {
        if (value == null || value.isEmpty()) {
            row.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
            return false;
        }
        row.setVisibility(View.VISIBLE);
        valueView.setText(value);
        row.setOnClickListener(click);
        return true;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private void openWhatsApp(String raw) {
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        digits = digits.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            Toast.makeText(this, R.string.support_invalid_whatsapp, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg = URLEncoder.encode(getString(R.string.support_whatsapp_prefill),
                    StandardCharsets.UTF_8.name());
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + digits + "?text=" + msg)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.support_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openTelegram(String raw) {
        String value = raw.trim();
        String url;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            url = value;
        } else if (value.startsWith("@")) {
            url = "https://t.me/" + value.substring(1);
        } else if (value.matches("^[A-Za-z0-9_]{3,}$")) {
            url = "https://t.me/" + value;
        } else {
            String digits = value.replaceAll("[^0-9+]", "");
            url = "https://t.me/+" + digits.replace("+", "");
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.support_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openInstagram(String raw) {
        String value = raw.trim();
        String url;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            url = value;
        } else {
            if (value.startsWith("@")) value = value.substring(1);
            url = "https://instagram.com/" + value;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.support_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void openEmail(String email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + email));
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.support_email_subject));
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.customer_support)));
        } catch (Exception e) {
            Toast.makeText(this, email, Toast.LENGTH_LONG).show();
        }
    }

    private void openPhone(String phone) {
        String digits = phone.replaceAll("[^0-9+]", "");
        if (digits.isEmpty()) {
            Toast.makeText(this, R.string.support_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + digits)));
        } catch (Exception e) {
            Toast.makeText(this, phone, Toast.LENGTH_LONG).show();
        }
    }
}
