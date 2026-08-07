package com.Dramizo.Series.presentation.common;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.agency.AgencyManageActivity;
import com.Dramizo.Series.util.AgencyUi;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Mikoo family / guild info card — open from agency room header or user-card family strip.
 */
public final class AgencyFamilyInfoSheet {
    private AgencyFamilyInfoSheet() {}

    public static void show(@NonNull FragmentActivity activity, @Nullable String agencyId) {
        show(activity, agencyId, null, null);
    }

    public static void show(
            @NonNull FragmentActivity activity,
            @Nullable String agencyId,
            @Nullable String logoHint,
            @Nullable String coverHint) {
        if (agencyId == null || agencyId.trim().isEmpty()) {
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        final String id = agencyId.trim();
        final AppContainer container = ContainerProvider.from(activity);

        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(activity);
        View sheet = activity.getLayoutInflater().inflate(R.layout.dialog_agency_family_info, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            AuraDialogHelper.configureShown(dialog);
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) bs.setBackgroundResource(android.R.color.transparent);
        });

        ImageView imgLogo = sheet.findViewById(R.id.imgAgencyLogo);
        ImageView imgBanner = sheet.findViewById(R.id.imgAgencyBanner);
        // Instant brand face from room header while API loads full agency.
        AgencyUi.bindLogo(imgLogo, logoHint, coverHint);
        TextView tvName = sheet.findViewById(R.id.tvAgencyName);
        TextView tvGid = sheet.findViewById(R.id.tvAgencyGid);
        ImageView btnCopy = sheet.findViewById(R.id.btnCopyAgencyId);
        TextView tvFollowers = sheet.findViewById(R.id.tvFollowers);
        TextView tvRooms = sheet.findViewById(R.id.tvRooms);
        TextView tvMaxOnline = sheet.findViewById(R.id.tvMaxOnline);
        TextView tvGifts = sheet.findViewById(R.id.tvGifts);
        ImageView imgOwner = sheet.findViewById(R.id.imgOwnerAvatar);
        TextView tvOwner = sheet.findViewById(R.id.tvOwnerName);
        TextView tvMedals = sheet.findViewById(R.id.tvMedals);
        TextView btnFollow = sheet.findViewById(R.id.btnFollowAgency);
        TextView btnOpen = sheet.findViewById(R.id.btnOpenAgencyManage);

        final String[] gidHold = {""};
        final boolean[] following = {false};

        if (btnOpen != null) {
            btnOpen.setOnClickListener(v -> {
                dialog.dismiss();
                try {
                    Intent i = new Intent(activity, AgencyManageActivity.class);
                    i.putExtra(AgencyManageActivity.EXTRA_AGENCY_ID, id);
                    activity.startActivity(i);
                } catch (Exception ignored) {
                }
            });
        }

        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                String gid = gidHold[0];
                if (gid == null || gid.isEmpty()) return;
                ClipboardManager cm =
                        (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("GID", gid));
                    Toast.makeText(activity, R.string.user_id_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnFollow != null) {
            btnFollow.setOnClickListener(v -> {
                btnFollow.setEnabled(false);
                final boolean unfollow = following[0];
                container.getIoExecutor().execute(() -> {
                    Result<Map<String, Object>> r = ApiCall.execute(
                            unfollow
                                    ? container.getAgencyApi().unfollowAgency(id)
                                    : container.getAgencyApi().followAgency(id));
                    activity.runOnUiThread(() -> {
                        btnFollow.setEnabled(true);
                        if (!dialog.isShowing()) return;
                        if (r.success) {
                            following[0] = !unfollow;
                            applyFollowUi(btnFollow, following[0]);
                            if (r.data != null && tvFollowers != null) {
                                Object fc = r.data.get("followerCount");
                                if (fc instanceof Number) {
                                    tvFollowers.setText(formatCount(((Number) fc).longValue()));
                                }
                            }
                        } else {
                            Toast.makeText(
                                            activity,
                                            r.error != null
                                                    ? r.error
                                                    : activity.getString(R.string.error_generic),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
                });
            });
        }

        container.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyDto> r =
                    ApiCall.execute(container.getAgencyApi().get(id));
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                if (!r.success || r.data == null) {
                    Toast.makeText(
                                    activity,
                                    r.error != null ? r.error : activity.getString(R.string.error_generic),
                                    Toast.LENGTH_SHORT)
                            .show();
                    dialog.dismiss();
                    return;
                }
                MiscDtos.AgencyDto a = r.data;
                if (tvName != null) {
                    tvName.setText(a.name != null ? a.name : activity.getString(R.string.agency));
                }
                String gid = a.publicId != null ? a.publicId.trim() : "";
                // Never fall back to owner.publicId — agency has its own GID.
                gidHold[0] = gid;
                if (tvGid != null) {
                    tvGid.setText(gid.isEmpty() ? "GID: —" : ("GID:" + gid));
                    if (btnCopy != null) {
                        btnCopy.setVisibility(gid.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                }
                if (tvFollowers != null) {
                    long fc = a.followerCount > 0 ? a.followerCount : Math.max(0, a.memberCount);
                    tvFollowers.setText(formatCount(fc));
                }
                if (tvRooms != null) tvRooms.setText(formatCount(Math.max(0, a.roomCount)));
                if (tvMaxOnline != null) {
                    long max = Math.max(a.maxOnline, Math.max(a.liveOnline, a.liveViewerCount));
                    tvMaxOnline.setText(formatCount(Math.max(0, max)));
                }
                if (tvGifts != null) {
                    tvGifts.setText(formatCount(Math.max(0, a.giftsReceivedDiamonds)));
                }
                int tier = a.level > 0 ? a.level : a.medals;
                if (tier <= 0) {
                    tier = AgencyUi.bannerTierFromDiamonds(a.totalDiamonds);
                }
                AgencyUi.bindBanner(imgBanner, tvMedals, tier);
                if (tvMedals != null) {
                    tvMedals.setVisibility(View.VISIBLE);
                    tvMedals.setText("Lv." + Math.max(1, tier));
                }
                following[0] = Boolean.TRUE.equals(a.isFollowing);
                applyFollowUi(btnFollow, following[0]);

                if (a.owner != null) {
                    if (tvOwner != null) {
                        String n = a.owner.displayName != null && !a.owner.displayName.isEmpty()
                                ? a.owner.displayName
                                : (a.owner.username != null ? a.owner.username : "—");
                        tvOwner.setText(n);
                    }
                    AvatarCosmetics.bindAvatar(imgOwner, a.owner.avatarUrl);
                    final String ownerId = a.owner.id;
                    if (imgOwner != null && ownerId != null && !ownerId.isEmpty()) {
                        imgOwner.setOnClickListener(v -> {
                            dialog.dismiss();
                            UserProfileCardSheet.show(activity, ownerId, a.owner.displayName, a.owner.avatarUrl);
                        });
                    }
                } else if (tvOwner != null) {
                    tvOwner.setText("—");
                }

                AgencyUi.bindLogo(imgLogo, a.logoUrl, a.coverUrl);
            });
        });

        dialog.show();
    }

    private static void applyFollowUi(@Nullable TextView btn, boolean following) {
        if (btn == null) return;
        btn.setSelected(following);
        btn.setText(following
                ? R.string.agency_family_following
                : R.string.agency_family_follow);
        btn.setTextColor(following ? 0xFFFFFFFF : 0xFF0B1A16);
    }

    private static String formatCount(long n) {
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(Math.max(0, n));
    }

    @Nullable
    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }
}
