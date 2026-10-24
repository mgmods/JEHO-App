package com.Dramizo.Series.presentation.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.databinding.ActivityStoreHubBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.cosmetics.CosmeticsActivity;
import com.Dramizo.Series.presentation.vip.VipActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.GenderVerifiedBadge;
import com.Dramizo.Series.util.HostSignalView;
import com.Dramizo.Series.util.VipStyle;
import com.Dramizo.Series.util.VanityIdStyle;

/**
 * Profile store hub: mall, badges, VIP, level, special ID.
 * Header mirrors profile (name + verify badge + avatar frame wear).
 */
public class StoreHubActivity extends ThemedActivity {

    private ActivityStoreHubBinding binding;
    private AppContainer c;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStoreHubBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.rowMall.setOnClickListener(v ->
                startActivity(new Intent(this, CosmeticsActivity.class)));
        binding.rowBadges.setOnClickListener(v -> openHtmlStoreSection("badges"));
        binding.rowVip.setOnClickListener(v -> openHtmlStoreSection("vip"));
        binding.rowLevel.setOnClickListener(v -> openHtmlStoreSection("level"));
        binding.rowVanity.setOnClickListener(v -> openHtmlStoreSection("id"));
    }

    private void openHtmlStoreSection(String section) {
        Intent intent = new Intent(this, CosmeticsActivity.class);
        intent.putExtra("store_section", section);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUserHeader();
    }

    private void refreshUserHeader() {
        // Instant paint from session, then refresh from network for wear changes after mall.
        AuthDtos.UserDto cached = c.getSessionManager().getUser();
        if (cached != null) paintUser(cached);
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> me = ApiCall.execute(c.getUserApi().me());
            if (me.success && me.data != null) {
                c.getSessionManager().updateCachedUser(me.data);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) paintUser(me.data);
                });
            }
        });
    }

    private void paintUser(AuthDtos.UserDto user) {
        if (binding == null || user == null) return;
        String name = user.displayName != null && !user.displayName.isEmpty()
                ? user.displayName
                : (user.username != null ? user.username : "—");
        binding.tvDisplayName.setText(name);
        GenderVerifiedBadge.bind(binding.tvDisplayName, null, user);
        String pid = user.displayPublicId();
        binding.tvUserId.setText(pid.isEmpty() ? "—" : pid);
        VanityIdStyle.bind(binding.tvUserId, c.getVanityApi(), pid, user.vipLevel);
        String nobilityFrame = VipStyle.profileNobilityFrameUrl(
                Math.max(0, user.vipLevel), user.vipTouUrl);
        String profileFrame = nobilityFrame != null ? nobilityFrame : user.vipBadgeUrl;
        HostSignalView.prefetchWear(this, profileFrame);
        AvatarCosmetics.bindProfileWear(
                binding.webHostSignal,
                binding.imgAvatar,
                binding.imgFrame,
                user.avatarUrl,
                profileFrame,
                user.hostBadgeUrl,
                user.hostBadgeMeta,
                1);
    }
}
