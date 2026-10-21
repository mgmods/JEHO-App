package com.Dramizo.Series.presentation.agency;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.CountryCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AgencyCreateActivity extends ThemedActivity {
    private AgencyViewModel vm;
    private ActivityCreateAgencyBinding binding;
    private DialogCreateAgencyBinding form;
    private int priceCoins;
    private boolean createFree;
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
        form.etCountry.setOnClickListener(v -> showCountryPicker());
        form.etCountry.setOnFocusChangeListener((v, focused) -> {
            if (focused) showCountryPicker();
        });
        form.btnCancelAgency.setText(R.string.back);
        form.btnCancelAgency.setOnClickListener(v -> finish());
        form.btnSubmitAgency.setEnabled(false);
        form.btnSubmitAgency.setText(R.string.agency_application_loading_price);
        form.btnSubmitAgency.setOnClickListener(v -> submit());

        vm.getPricing().observe(this, this::bindPricing);
        vm.getSubmitting().observe(this, busy -> {
            boolean loading = Boolean.TRUE.equals(busy);
            form.btnSubmitAgency.setEnabled(!loading && pricingReady());
            if (loading) form.btnSubmitAgency.setText(R.string.loading);
            else refreshSubmitLabel();
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
            if (e != null) com.Dramizo.Series.util.BalanceRedirect.handle(this, e);
        });

        vm.loadPricing();
        loadWalletBalance();
    }

    private boolean pricingReady() {
        return createFree || priceCoins > 0;
    }

    private void bindPricing(MiscDtos.AgencyPricingDto p) {
        if (p == null) return;
        createFree = p.createFree || (!p.isPaid && p.createPriceCoins <= 0);
        priceCoins = createFree ? 0 : Math.max(0, p.createPriceCoins);
        if (createFree) binding.tvPriceCoins.setText(R.string.agency_application_free_hint);
        else if (priceCoins > 0) binding.tvPriceCoins.setText(getString(R.string.agency_create_price_format, priceCoins));
        else binding.tvPriceCoins.setText(R.string.agency_application_loading_price);
        refreshSubmitLabel();
        form.btnSubmitAgency.setEnabled(pricingReady());
    }

    private void refreshSubmitLabel() {
        if (createFree) form.btnSubmitAgency.setText(R.string.agency_submit_free);
        else if (priceCoins > 0) form.btnSubmitAgency.setText(getString(R.string.agency_pay_and_submit_format, priceCoins));
        else form.btnSubmitAgency.setText(R.string.agency_application_loading_price);
    }

    private void loadWalletBalance() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r = ApiCall.execute(c.getWalletApi().getWallet());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (r.success && r.data != null) {
                    walletCoins = Math.max(0, r.data.coins);
                    binding.tvWalletBalance.setText(getString(R.string.agency_wallet_balance_format, walletCoins));
                } else binding.tvWalletBalance.setText("");
            });
        });
    }

    private void submit() {
        if (!pricingReady()) {
            Toast.makeText(this, R.string.agency_application_loading_price, Toast.LENGTH_SHORT).show();
            vm.loadPricing();
            return;
        }
        if (!createFree && walletCoins >= 0 && walletCoins < priceCoins) {
            com.Dramizo.Series.util.BalanceRedirect.handleForced(this,
                    getString(R.string.agency_insufficient_coins_format, priceCoins));
            return;
        }
        MiscDtos.AgencyApplicationRequest request = applicationFrom();
        String err = validate(request);
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }
        if (createFree) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.open_agency)
                    .setMessage(R.string.agency_confirm_free_message)
                    .setPositiveButton(R.string.agency_submit_free, (d, w) -> vm.submitApplication(applicationFrom()))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.open_agency)
                    .setMessage(getString(R.string.agency_confirm_pay_message, priceCoins))
                    .setPositiveButton(getString(R.string.agency_pay_and_submit_format, priceCoins),
                            (d, w) -> vm.submitApplication(applicationFrom()))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void applyAccountCountry() {
        AuthDtos.UserDto user = ContainerProvider.from(this).getSessionManager().getUser();
        String country = user != null && user.country != null ? user.country.trim() : "";
        if (country.isEmpty()) country = Locale.getDefault().getCountry();
        if (country == null || country.trim().isEmpty()) country = "JO";
        CountryCatalog.Entry entry = CountryCatalog.resolve(country);
        if (entry == null) entry = CountryCatalog.resolve("JO");
        form.etCountry.setTag(entry != null ? entry.code : "JO");
        form.etCountry.setText(entry != null ? entry.label() : CountryCatalog.labelWithFlag("JO"));
        form.etCountry.setFocusable(false);
        form.etCountry.setClickable(true);
        form.etCountry.setLongClickable(false);
    }

    private void showCountryPicker() {
        List<CountryCatalog.Entry> countries = CountryCatalog.all();
        String[] labels = new String[countries.size()];
        int selected = -1;
        String current = String.valueOf(form.etCountry.getTag());
        for (int i = 0; i < countries.size(); i++) {
            labels[i] = countries.get(i).label();
            if (countries.get(i).code.equalsIgnoreCase(current)) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("اختر الدولة")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    CountryCatalog.Entry entry = countries.get(which);
                    form.etCountry.setTag(entry.code);
                    form.etCountry.setText(entry.label());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private MiscDtos.AgencyApplicationRequest applicationFrom() {
        MiscDtos.AgencyApplicationRequest r = new MiscDtos.AgencyApplicationRequest();
        r.proposedName = text(form.etAgencyName);
        r.country = String.valueOf(form.etCountry.getTag()).trim();
        r.contactPhone = text(form.etContactPhone);

        // Keep the existing backend contract compatible while the user-facing
        // application intentionally asks for only three fields.
        r.description = "طلب فتح وكالة JEHO باسم " + r.proposedName + " في " + r.country + ".";
        r.businessPlan = ("طلب وكالة JEHO: " + r.proposedName + "، الدولة " + r.country
                + ". سيتم التواصل مع مقدم الطلب على رقم الهاتف المسجل لمراجعة الطلب. ")
                .repeat(2);
        r.experience = "طلب وكالة عبر التطبيق";
        r.contactEmail = "agency.application@jeho.chat";
        r.expectedHostCount = 1;
        r.documentUrls = java.util.Collections.emptyList();
        r.termsAccepted = true;
        r.socialLink = null;
        return r;
    }

    private static String text(android.widget.EditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    private static String validate(MiscDtos.AgencyApplicationRequest request) {
        if (request.proposedName.length() < 3) return "اسم الوكالة يجب أن يكون 3 أحرف على الأقل";
        if (request.country.length() < 2) return "اختر الدولة";
        if (request.contactPhone.length() < 6) return "رقم التواصل يجب أن يتكون من 6 خانات على الأقل";
        return null;
    }
}
