package com.Dramizo.Series.presentation.settings;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivitySettingsBinding;
import com.Dramizo.Series.presentation.auth.LoginActivity;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.LocaleHelper;

/** Mikoo SettingActivity parity — flat white rows, no invented dialogs. */
public class SettingsActivity extends ThemedActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = "ar";
        try {
            lang = newBase.getSharedPreferences("auralive_lang", MODE_PRIVATE)
                    .getString("language", "ar");
        } catch (Exception ignored) {
        }
        super.attachBaseContext(LocaleHelper.wrap(newBase, lang));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingsBinding binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SettingsViewModel vm = new ViewModelProvider(this,
                new ViewModelFactory(ContainerProvider.from(this)))
                .get(SettingsViewModel.class);

        binding.btnBack.setOnClickListener(v -> navigateUp());

        label(binding.rowTheme.getRoot(), R.string.settings_theme);
        label(binding.rowLanguage.getRoot(), R.string.language);
        label(binding.rowNotifications.getRoot(), R.string.settings_notifications);
        label(binding.rowMessages.getRoot(), R.string.settings_messages);
        label(binding.rowBindAccount.getRoot(), R.string.settings_bind_account);
        label(binding.rowPassword.getRoot(), R.string.settings_password);
        label(binding.rowFeedback.getRoot(), R.string.settings_feedback);
        label(binding.rowChildSafety.getRoot(), R.string.settings_child_safety);
        label(binding.rowAbout.getRoot(), R.string.about);
        label(binding.rowNetwork.getRoot(), R.string.settings_network);
        label(binding.rowUserAgreement.getRoot(), R.string.settings_user_agreement);
        label(binding.rowChildPolicy.getRoot(), R.string.settings_child_policy);
        label(binding.rowShare.getRoot(), R.string.share_app);

        TextView langValue = binding.rowLanguage.getRoot().findViewById(R.id.tvSettingValue);
        if (langValue != null) {
            langValue.setText(SettingsViewModel.displayLanguageLabel(this, vm.getLanguage()));
        }
        // Keep stub TextView in sync for ViewBinding compatibility.
        if (binding.tvSelectedLanguage != null) {
            binding.tvSelectedLanguage.setText(
                    SettingsViewModel.displayLanguageLabel(this, vm.getLanguage()));
        }

        binding.tvAppVersion.setText(com.Dramizo.Series.BuildConfig.VERSION_NAME);

        binding.rowTheme.getRoot().setOnClickListener(v -> {
            String[] opts = new String[]{
                    getString(R.string.settings_theme_light),
                    getString(R.string.settings_theme_dark)
            };
            AuraDialogHelper.list(this, getString(R.string.settings_theme), opts, which -> {
                boolean dark = which == 1;
                vm.setDarkMode(dark);
                if (binding.switchDark != null) binding.switchDark.setChecked(dark);
            });
        });
        binding.rowLanguage.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, LanguageActivity.class)));
        if (binding.btnLanguagePicker != null) {
            binding.btnLanguagePicker.setOnClickListener(v ->
                    startActivity(new Intent(this, LanguageActivity.class)));
        }
        // Mikoo: open system notification settings — never a custom dialog.
        binding.rowNotifications.getRoot().setOnClickListener(v -> openSystemNotificationSettings());
        binding.rowMessages.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, MessageSettingsActivity.class)));
        binding.rowBindAccount.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, AccountSecurityActivity.class)));
        binding.rowPassword.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, AccountSecurityActivity.class)));
        binding.rowFeedback.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, CustomerSupportActivity.class)));
        binding.rowChildSafety.getRoot().setOnClickListener(v ->
                openPolicy("/legal/child-safety.html", getString(R.string.settings_child_safety)));
        binding.rowAbout.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        binding.rowNetwork.getRoot().setOnClickListener(v ->
                startActivity(new Intent(this, NetworkAnalysisActivity.class)));
        binding.rowUserAgreement.getRoot().setOnClickListener(v ->
                openPolicy("/legal/terms.html", getString(R.string.settings_user_agreement)));
        binding.rowChildPolicy.getRoot().setOnClickListener(v ->
                openPolicy("/legal/child-safety.html", getString(R.string.settings_child_policy)));
        // Privacy-style policy (if row used elsewhere) — child policy maps above.
        binding.rowShare.getRoot().setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                    "JEHO CHAT — " + com.Dramizo.Series.util.InviteReferralHelper.playStoreUrl(null));
            startActivity(Intent.createChooser(share, getString(R.string.share_app)));
        });
        binding.rowVersion.setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
        binding.rowSwitchAccount.setOnClickListener(v ->
                startActivity(new Intent(this, AccountManageActivity.class)));

        binding.btnLogout.setOnClickListener(v -> {
            ContainerProvider.from(this).getSessionManager().saveAccountSnapshot();
            vm.logout();
        });
        vm.getLoggedOut().observe(this, out -> {
            if (Boolean.TRUE.equals(out)) {
                Intent i = new Intent(this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            }
        });
        vm.getError().observe(this, e -> {
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
        });
        vm.getMessage().observe(this, m -> {
            if (m != null) Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            ActivitySettingsBinding b = ActivitySettingsBinding.bind(
                    findViewById(R.id.contentRoot));
            TextView langValue = b.rowLanguage.getRoot().findViewById(R.id.tvSettingValue);
            String tag = ContainerProvider.from(this).getSessionManager().getLanguage();
            String label = SettingsViewModel.displayLanguageLabel(this, tag);
            if (langValue != null) langValue.setText(label);
            if (b.tvSelectedLanguage != null) b.tvSelectedLanguage.setText(label);
        } catch (Exception ignored) {
        }
    }

    private void openSystemNotificationSettings() {
        try {
            Intent i = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                i.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
            }
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_notifications, Toast.LENGTH_SHORT).show();
        }
    }

    private static void label(View row, int titleRes) {
        TextView tv = row.findViewById(R.id.tvSettingTitle);
        if (tv != null) tv.setText(titleRes);
    }

    private void openPolicy(String path, String title) {
        Intent i = new Intent(this, com.Dramizo.Series.presentation.web.PromoWebActivity.class);
        i.putExtra(com.Dramizo.Series.presentation.web.PromoWebActivity.EXTRA_URL,
                com.Dramizo.Series.util.ApiOrigin.origin() + path);
        i.putExtra(com.Dramizo.Series.presentation.web.PromoWebActivity.EXTRA_TITLE, title);
        startActivity(i);
    }
}
