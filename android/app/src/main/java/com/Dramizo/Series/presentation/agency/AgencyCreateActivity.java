package com.Dramizo.Series.presentation.agency;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.ActivityCreateAgencyBinding;
import com.Dramizo.Series.databinding.DialogCreateAgencyBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.wallet.BagActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.CountryCatalog;

import java.util.Locale;

/** Full-screen paid agency application (not a dialog). */
public class AgencyCreateActivity extends ThemedActivity {
    private AgencyViewModel vm;
    private ActivityCreateAgencyBinding binding;
    private DialogCreateAgencyBinding form;
    private int priceCoins;
    private long walletCoins = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateAgencyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        form = DialogCreateAgencyBinding.inflate(LayoutInflater.from(this));
        FrameLayout host = binding.formHost;
        host.addView(form.getRoot(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        vm = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(AgencyViewModel.class);

        applyAccountCountry();
        form.btnCancelAgency.setText(R.string.back);
        form.btnCancelAgency.setOnClickListener(v -> finish());
        form.btnSubmitAgency.setEnabled(false);
        form.btnSubmitAgency.setText(R.string.agency_application_loading_price);
        form.btnSubmitAgency.setOnClickListener(v -> submit());

        vm.getPricing().observe(this, this::bindPricing);
        vm.getSubmitting().observe(this, busy -> {
            boolean loading = Boolean.TRUE.equals(busy);
            form.btnSubmitAgency.setEnabled(!loading && priceCoins > 0);
            if (loading) {
                form.btnSubmitAgency.setText(R.string.loading);
            } else {
                refreshSubmitLabel();
            }
        });
        vm.getMessage().observe(this, m -> {
            if ("application_submitted".equals(m)) {
                Toast.makeText(this, R.string.agency_application_submitted, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            } else if (m != null) {
                Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
            }
        });
        vm.getError().observe(this, e -> {
            if (e == null) return;
            Toast.makeText(this, e, Toast.LENGTH_LONG).show();
            String lower = e.toLowerCase(Locale.US);
            if (lower.contains("insufficient") || lower.contains("رصيد")
                    || lower.contains("coins") || lower.contains("عملات")
                    || lower.contains("balance")) {
                startActivity(new Intent(this, BagActivity.class));
            }
        });

        vm.loadPricing();
        loadWalletBalance();
    }

    private void bindPricing(MiscDtos.AgencyPricingDto p) {
        if (p == null) return;
        priceCoins = Math.max(0, p.createPriceCoins);
        if (priceCoins > 0) {
            binding.tvPriceCoins.setText(getString(R.string.agency_create_price_format, priceCoins));
        } else {
            binding.tvPriceCoins.setText(R.string.agency_application_loading_price);
        }
        refreshSubmitLabel();
        form.btnSubmitAgency.setEnabled(priceCoins > 0);
    }

    private void refreshSubmitLabel() {
        if (priceCoins > 0) {
            form.btnSubmitAgency.setText(getString(R.string.agency_pay_and_submit_format, priceCoins));
        } else {
            form.btnSubmitAgency.setText(R.string.agency_application_loading_price);
        }
    }

    private void loadWalletBalance() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r = ApiCall.execute(c.getWalletApi().getWallet());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (r.success && r.data != null) {
                    walletCoins = Math.max(0, r.data.coins);
                    binding.tvWalletBalance.setText(
                            getString(R.string.agency_wallet_balance_format, walletCoins));
                } else {
                    binding.tvWalletBalance.setText("");
                }
            });
        });
    }

    private void submit() {
        if (priceCoins <= 0) {
            Toast.makeText(this, R.string.agency_application_loading_price, Toast.LENGTH_SHORT).show();
            vm.loadPricing();
            return;
        }
        if (walletCoins >= 0 && walletCoins < priceCoins) {
            Toast.makeText(this,
                    getString(R.string.agency_insufficient_coins_format, priceCoins),
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, BagActivity.class));
            return;
        }
        MiscDtos.AgencyApplicationRequest request = applicationFrom();
        String err = validate(request);
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }
        if (!request.termsAccepted) {
            Toast.makeText(this, R.string.agency_accept_terms, Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.open_agency)
                .setMessage(getString(R.string.agency_confirm_pay_message, priceCoins))
                .setPositiveButton(getString(R.string.agency_pay_and_submit_format, priceCoins),
                        (d, w) -> vm.submitApplication(applicationFrom()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyAccountCountry() {
        AuthDtos.UserDto user = ContainerProvider.from(this).getSessionManager().getUser();
        String country = user != null && user.country != null ? user.country.trim() : "";
        if (country.isEmpty()) {
            country = Locale.getDefault().getCountry();
        }
        if (country == null || country.trim().isEmpty()) country = "JO";
        CountryCatalog.Entry entry = CountryCatalog.resolve(country);
        String canonicalCountry = entry != null ? entry.code : country;
        form.etCountry.setTag(canonicalCountry);
        form.etCountry.setText(CountryCatalog.labelWithFlag(country));
        form.etCountry.setFocusable(false);
        form.etCountry.setClickable(false);
        form.etCountry.setLongClickable(false);
        form.etCountry.setAlpha(0.92f);
    }

    private MiscDtos.AgencyApplicationRequest applicationFrom() {
        MiscDtos.AgencyApplicationRequest r = new MiscDtos.AgencyApplicationRequest();
        r.proposedName = text(form.etAgencyName);
        r.description = text(form.etAgencyDesc);
        r.businessPlan = r.description;
        Object accountCountry = form.etCountry.getTag();
        r.country = accountCountry instanceof String
                ? ((String) accountCountry).trim()
                : text(form.etCountry);
        r.contactEmail = text(form.etContactEmail);
        r.contactPhone = text(form.etContactPhone);
        r.socialLink = null;
        r.experience = text(form.etExperience);
        try {
            r.expectedHostCount = Integer.parseInt(text(form.etExpectedHostCount));
        } catch (NumberFormatException ignored) {
            r.expectedHostCount = 0;
        }
        r.documentUrls = java.util.Collections.emptyList();
        r.termsAccepted = form.checkTerms.isChecked();
        return r;
    }

    private static String text(android.widget.EditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    private static String validate(MiscDtos.AgencyApplicationRequest request) {
        if (request.proposedName.length() < 3) return "اسم الوكالة يجب أن يكون 3 أحرف على الأقل";
        if (request.businessPlan.length() < 50) return "خطة العمل يجب أن تكون واضحة ولا تقل عن 50 حرفاً";
        if (request.country.length() < 2) return "أدخل اسم الدولة بشكل صحيح";
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(request.contactEmail).matches()) {
            return "أدخل بريداً إلكترونياً صحيحاً";
        }
        if (request.contactPhone.length() < 6) return "رقم التواصل يجب أن يتكون من 6 خانات على الأقل";
        if (request.experience.length() < 10) return "اكتب خبرتك في 10 أحرف على الأقل";
        if (request.expectedHostCount < 1) return "أدخل عدد المضيفين المتوقع";
        return null;
    }
}
