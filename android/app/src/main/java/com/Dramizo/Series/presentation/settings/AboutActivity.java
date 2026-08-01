package com.Dramizo.Series.presentation.settings;

import android.content.Intent;
import android.os.Bundle;

import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityAboutBinding;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.web.PromoWebActivity;
import com.Dramizo.Series.util.ApiOrigin;

public class AboutActivity extends ThemedActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAboutBinding binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.tvVersion.setText(getString(R.string.about_version_fmt,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        binding.btnPrivacy.setOnClickListener(v -> {
            Intent i = new Intent(this, PromoWebActivity.class);
            // Canonical: /privacy.html (also mirrored at /promos/privacy.html)
            i.putExtra(PromoWebActivity.EXTRA_URL, ApiOrigin.origin() + "/privacy.html");
            i.putExtra(PromoWebActivity.EXTRA_TITLE, getString(R.string.privacy_policy));
            startActivity(i);
        });
        binding.btnSupport.setOnClickListener(v ->
                startActivity(new Intent(this, CustomerSupportActivity.class)));
        binding.btnShareApp.setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                    getString(R.string.about_share_body) + "\n"
                            + com.Dramizo.Series.util.InviteReferralHelper.playStoreUrl(null));
            startActivity(Intent.createChooser(share, getString(R.string.share_app)));
        });
    }
}
