package com.Dramizo.Series.presentation.profile;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityProfileBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.CountryCatalog;
import com.Dramizo.Series.util.DeviceTimeFormat;
import com.Dramizo.Series.util.FlagImages;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.text.NumberFormat;

public class ProfileActivity extends ThemedActivity {
    public static final String EXTRA_USER_ID = "user_id";
    private ActivityProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.apply(this);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnBack, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = bars.top + (int) (8 * getResources().getDisplayMetrics().density);
            v.setLayoutParams(lp);
            return insets;
        });
        EdgeToEdgeHelper.padBottom(binding.contentRoot);
        AppContainer container = ContainerProvider.from(this);
        ProfileViewModel vm = new ViewModelProvider(this, new ViewModelFactory(container))
                .get(ProfileViewModel.class);
        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        String myId = container.getSessionManager().getUserId();

        binding.btnFollow.setOnClickListener(v -> {
            if (userId == null) return;
            if (myId != null && userId.equals(myId)) {
                Toast.makeText(this, "هذا حسابك", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnFollow.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                        container.getUserApi().follow(userId));
                runOnUiThread(() -> {
                    binding.btnFollow.setEnabled(true);
                    if (r.success) {
                        Toast.makeText(this, "تمت المتابعة", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                r.error != null ? r.error : getString(R.string.error_generic),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        binding.btnFriend.setOnClickListener(v -> {
            if (userId == null) return;
            if (myId != null && userId.equals(myId)) {
                Toast.makeText(this, "هذا حسابك", Toast.LENGTH_SHORT).show();
                return;
            }
            sendSocialRequest(userId, "friend", "صداقة");
        });
        if (binding.btnFriend != null) {
            binding.btnFriend.setOnLongClickListener(v -> {
                if (userId == null || myId == null || userId.equals(myId)) return false;
                AuraDialogHelper.confirm(this,
                        "إلغاء الصداقة",
                        "هل تريد إزالة هذا المستخدم من قائمة الأصدقاء؟",
                        "إلغاء الصداقة",
                        () -> {
                            container.getIoExecutor().execute(() -> {
                                Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                                        container.getUserApi().unfriend(userId));
                                runOnUiThread(() -> {
                                    if (r.success) {
                                        Toast.makeText(this, "تم إلغاء الصداقة", Toast.LENGTH_SHORT).show();
                                        vm.loadUser(userId);
                                    } else {
                                        Toast.makeText(this,
                                                r.error != null ? r.error : getString(R.string.error_generic),
                                                Toast.LENGTH_LONG).show();
                                    }
                                });
                            });
                        },
                        getString(android.R.string.cancel),
                        null);
                return true;
            });
        }
        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnMessage.setOnClickListener(v -> {
            if (userId == null || userId.isEmpty()) return;
            if (myId != null && userId.equals(myId)) {
                Toast.makeText(this, "لا يمكن مراسلة نفسك", Toast.LENGTH_SHORT).show();
                return;
            }
            binding.btnMessage.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Map<String, String> body = new HashMap<>();
                body.put("peerId", userId);
                Result<com.Dramizo.Series.data.remote.dto.ChatDtos.ConversationDto> r =
                        com.Dramizo.Series.util.ApiCall.execute(container.getChatApi().create(body));
                runOnUiThread(() -> {
                    binding.btnMessage.setEnabled(true);
                    if (!r.success || r.data == null || r.data.id == null) {
                        Toast.makeText(this,
                                r.error != null ? r.error : getString(R.string.error_generic),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent i = new Intent(this, com.Dramizo.Series.presentation.chat.ChatConversationActivity.class);
                    i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_CONVERSATION_ID, r.data.id);
                    i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_PEER_ID, userId);
                    if (r.data.peer != null) {
                        String title = r.data.peer.displayName != null ? r.data.peer.displayName : r.data.peer.username;
                        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_TITLE, title);
                        i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_AVATAR, r.data.peer.avatarUrl);
                        if (r.data.peer.vipBadgeUrl != null) {
                            i.putExtra(com.Dramizo.Series.presentation.chat.ChatConversationActivity.EXTRA_HOST_BADGE,
                                    r.data.peer.vipBadgeUrl);
                        }
                    }
                    startActivity(i);
                });
            });
        });

        binding.rowGiftHistory.setOnClickListener(v ->
                startActivity(new Intent(this, GiftHistoryActivity.class)));

        if (userId != null && myId != null && !userId.equals(myId)) {
            container.getIoExecutor().execute(() -> {
                try { container.getUserApi().visit(userId).execute(); } catch (Exception ignored) {}
            });
        }

        vm.getUser().observe(this, user -> {
            if (user == null) return;
            binding.tvDisplayName.setText(user.displayName);
            com.Dramizo.Series.util.GenderVerifiedBadge.bind(binding.tvDisplayName, null, user);
            final String publicId = user.displayPublicId();
            binding.tvUsername.setText(publicId.isEmpty() ? "ID: —" : ("ID: " + publicId));
            binding.btnCopyId.setOnClickListener(v -> {
                if (publicId.isEmpty()) {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    return;
                }
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("user_id", publicId));
                    Toast.makeText(this, R.string.user_id_copied, Toast.LENGTH_SHORT).show();
                }
            });
            binding.tvBio.setText(user.bio != null && !user.bio.isEmpty() ? user.bio : getString(R.string.lazy_no_signature));
            binding.tvStats.setText(getString(R.string.friends) + " " + Math.max(0, user.friendsCount)
                    + " · " + getString(R.string.following) + " " + user.followingCount
                    + " · " + getString(R.string.fans) + " " + user.followersCount);
            binding.tvLevelChip.setText("Lv." + Math.max(1, user.level));
            com.Dramizo.Series.util.ServerAssets.loadCompoundStart(
                    binding.tvLevelChip, user.levelBadgeUrl, 24);
            CountryCatalog.Entry countryEntry = CountryCatalog.resolve(user.country);
            if (countryEntry != null) {
                binding.tvCountryChip.setText(countryEntry.nameAr);
            } else {
                binding.tvCountryChip.setText(user.country != null && !user.country.isEmpty() ? user.country : "—");
            }
            if (binding.imgCountryFlag != null) {
                FlagImages.bind(binding.imgCountryFlag, user.country);
            }
            binding.tvAgeChip.setText(String.valueOf(ageFrom(user.birthday)));
            binding.tvAgeChip.setBackgroundResource(R.drawable.bg_chip_age);
            binding.tvAgeChip.setTextColor(0xFFFFFFFF);
            binding.tvLevelChip.setBackgroundResource(R.drawable.bg_chip_level);
            binding.tvLevelChip.setTextColor(0xFFFFFFFF);
            int vip = Math.max(0, user.vipLevel);
            int level = Math.max(1, user.level);
            long charm = user.popularityLevel > 0 ? user.popularityLevel
                    : Math.max(0, Math.max(user.charmScore, user.popularityScore));
            long wealth = user.wealthLevel > 0 ? user.wealthLevel
                    : Math.max(0, user.wealthScore);
            binding.tvVipChip.setText("VIP " + vip);
            binding.tvVipChip.setVisibility(vip > 0 ? View.VISIBLE : View.GONE);
            if (vip > 0) {
                binding.tvVipChip.setBackgroundResource(R.drawable.bg_chip_vip);
                binding.tvVipChip.setTextColor(0xFFFFFFFF);
            }
            binding.tvCharmChip.setText("🌙 " + formatScore(charm));
            binding.tvCharmChip.setVisibility(View.VISIBLE);
            binding.tvCharmChip.setBackgroundResource(R.drawable.bg_chip_charm);
            binding.tvCharmChip.setTextColor(0xFFFFFFFF);
            binding.tvWealthChip.setText("❤ " + formatScore(wealth));
            binding.tvWealthChip.setVisibility(View.VISIBLE);
            binding.tvWealthChip.setBackgroundResource(R.drawable.bg_chip_wealth);
            binding.tvWealthChip.setTextColor(0xFFFFFFFF);
            AvatarCosmetics.bindProfileWear(
                    binding.webProfileHostSignal,
                    binding.imgAvatar,
                    binding.imgFrame,
                    user.avatarUrl,
                    user.vipBadgeUrl,
                    user.hostBadgeUrl,
                    user.hostBadgeMeta,
                    1);
            com.Dramizo.Series.util.HostSignalView.prefetchWear(this, user.vipBadgeUrl);
            // Wear on avatar only — never chips under the name.
            if (binding.imgHostBadge != null) {
                binding.imgHostBadge.setVisibility(View.GONE);
                binding.imgHostBadge.setImageDrawable(null);
            }
            if (binding.imgVipBadge != null) {
                binding.imgVipBadge.setVisibility(View.GONE);
                binding.imgVipBadge.setImageDrawable(null);
            }
            if (Boolean.TRUE.equals(user.isOnline)) {
                binding.tvLastSeen.setText(R.string.online_now);
                binding.tvLastSeen.setTextColor(getColor(R.color.aurora_mint));
            } else if (user.lastSeenAt != null && !user.lastSeenAt.isEmpty()) {
                binding.tvLastSeen.setText(
                        DeviceTimeFormat.lastSeen(this, user.lastSeenAt));
                binding.tvLastSeen.setTextColor(getColor(R.color.text_secondary));
            } else {
                binding.tvLastSeen.setText(R.string.last_seen_recently);
                binding.tvLastSeen.setTextColor(getColor(R.color.text_secondary));
            }
            if (user.coverUrl != null && !user.coverUrl.isEmpty()) {
                Glide.with(this)
                        .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(user.coverUrl))
                        .placeholder(ImagePlaceholder.cover())
                        .into(binding.imgCover);
            } else {
                binding.imgCover.setImageResource(ImagePlaceholder.cover());
            }
            if (myId != null && myId.equals(user.id)) {
                binding.btnFollow.setVisibility(View.GONE);
                if (binding.btnFriend != null) binding.btnFriend.setVisibility(View.GONE);
            }
        });
        vm.getError().observe(this, e -> {
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
        });
        if (userId != null) vm.loadUser(userId); else vm.loadMe();
    }

    private String formatScore(long value) {
        return NumberFormat.getIntegerInstance(getResources().getConfiguration().getLocales().get(0))
                .format(Math.max(0, value));
    }

    private void sendSocialRequest(String targetId, String type, String labelAr) {
        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            Map<String, String> body = new HashMap<>();
            body.put("type", type);
            Result<Object> r = com.Dramizo.Series.util.ApiCall.execute(
                    container.getUserApi().sendRequest(targetId, body));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, "تم إرسال طلب " + labelAr, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private int ageFrom(String birthday) {
        if (birthday == null || birthday.length() < 4) return 18;
        try {
            int year = Integer.parseInt(birthday.substring(0, 4));
            return Math.max(1, Calendar.getInstance().get(Calendar.YEAR) - year);
        } catch (Exception e) {
            return 18;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && binding.webProfileHostSignal != null
                && binding.webProfileHostSignal.getVisibility() == android.view.View.VISIBLE) {
            binding.webProfileHostSignal.resumeMotion();
        }
    }

    @Override
    protected void onDestroy() {
        if (binding != null && binding.webProfileHostSignal != null) {
            binding.webProfileHostSignal.destroy();
        }
        super.onDestroy();
    }
}
