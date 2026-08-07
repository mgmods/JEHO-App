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
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.gifts.GiftBottomSheet;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AgencyUi;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.FlagImages;
import com.Dramizo.Series.util.GenderVerifiedBadge;
import com.Dramizo.Series.util.VipStyle;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.HashMap;
import java.util.Map;

/**
 * Same Mikoo-style user card as the in-room profile sheet, for use anywhere
 * outside VoiceRoom (chat, friends, ranking, search, …).
 * Room admin actions stay in {@code VoiceRoomActivity#showUserCard}.
 */
public final class UserProfileCardSheet {
    private UserProfileCardSheet() {}

    public static void show(@NonNull FragmentActivity activity, @Nullable String userId) {
        show(activity, userId, null, null, 0, 1);
    }

    public static void show(
            @NonNull FragmentActivity activity,
            @Nullable String userId,
            @Nullable String name,
            @Nullable String avatarUrl) {
        show(activity, userId, name, avatarUrl, 0, 1);
    }

    public static void show(
            @NonNull FragmentActivity activity,
            @Nullable String userId,
            @Nullable String name,
            @Nullable String avatarUrl,
            int vipLevel,
            int userLevel) {
        if (userId == null || userId.trim().isEmpty()) {
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        final String uid = userId.trim();
        final AppContainer container = ContainerProvider.from(activity);
        final String myId = resolveMyUserId(container);

        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(activity);
        View sheet = activity.getLayoutInflater().inflate(R.layout.dialog_room_user_card, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        dialog.setOnShowListener(d -> {
            AuraDialogHelper.configureShown(dialog);
            View bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) bs.setBackgroundResource(android.R.color.transparent);
        });

        TextView tvName = sheet.findViewById(R.id.tvUserName);
        TextView tvUserId = sheet.findViewById(R.id.tvUserId);
        ImageView btnCopyId = sheet.findViewById(R.id.btnCopyId);
        ImageView imgCountryFlag = sheet.findViewById(R.id.imgCountryFlag);
        ImageView imgGender = sheet.findViewById(R.id.imgGender);
        TextView tvOnlineStatus = sheet.findViewById(R.id.tvOnlineStatus);
        TextView tvStatFollowing = sheet.findViewById(R.id.tvStatFollowing);
        TextView tvStatFans = sheet.findViewById(R.id.tvStatFans);
        TextView tvStatVisitors = sheet.findViewById(R.id.tvStatVisitors);
        TextView tvPropFamily = sheet.findViewById(R.id.tvPropFamily);
        TextView tvPropSupporters = sheet.findViewById(R.id.tvPropSupportersCount);
        TextView tvPropFriends = sheet.findViewById(R.id.tvPropFriendsCount);
        View propFamily = sheet.findViewById(R.id.propFamily);
        ImageView imgVipMedal = sheet.findViewById(R.id.imgVipMedal);
        ImageView imgEntryRide = sheet.findViewById(R.id.imgEntryRide);
        ImageView img = sheet.findViewById(R.id.imgUserAvatar);
        ImageView frame = sheet.findViewById(R.id.imgUserFrame);
        ImageView vipHead = sheet.findViewById(R.id.imgVipHead);
        ImageView hostBadge = sheet.findViewById(R.id.imgUserHostBadge);
        View rowAgency = sheet.findViewById(R.id.rowAgency);
        ImageView imgAgencyLogo = sheet.findViewById(R.id.imgAgencyLogo);
        TextView tvAgencyName = sheet.findViewById(R.id.tvAgencyName);
        TextView tvAgencyGid = sheet.findViewById(R.id.tvAgencyGid);
        View userCardBg = sheet.findViewById(R.id.userCardBg);
        View moreActionsBlock = sheet.findViewById(R.id.moreActionsBlock);
        TextView chipVip = sheet.findViewById(R.id.chipVip);
        TextView chipMember = sheet.findViewById(R.id.chipMember);
        TextView chipCharm = sheet.findViewById(R.id.chipCharm);
        TextView chipWealth = sheet.findViewById(R.id.chipWealth);
        TextView chipFirstDay = sheet.findViewById(R.id.chipFirstDay);
        TextView btnHi = sheet.findViewById(R.id.btnHi);
        View rowHostTasks = sheet.findViewById(R.id.rowHostTasks);

        final String[] publicIdHold = {""};
        final String[] displayNameHold = {name != null ? name : ""};
        final String[] avatarHold = {avatarUrl != null ? avatarUrl : ""};
        final String[] vipFrameHold = {VipStyle.profileNobilityFrameUrl(vipLevel, null)};

        if (tvName != null) {
            tvName.setText(displayNameHold[0].isEmpty() ? "مستخدم" : displayNameHold[0]);
        }
        AvatarCosmetics.styleUserCardBadges(
                chipVip, chipMember, chipCharm, chipWealth, vipLevel, userLevel, 0L, 0L);
        if (chipMember != null) chipMember.setText(String.valueOf(Math.max(1, userLevel)));
        styleStatChips(chipCharm, chipWealth);
        AgencyUi.applyUserCardSheet(userCardBg, moreActionsBlock, vipLevel);
        bindVipMedal(imgVipMedal, vipLevel, null);
        bindVipHead(vipHead, vipLevel, null);
        AvatarCosmetics.bindStacked(img, frame, avatarHold[0], vipFrameHold[0]);
        AvatarCosmetics.applyHostWear(frame, hostBadge, img, vipFrameHold[0], null, null, null);
        if (rowAgency != null) rowAgency.setVisibility(View.GONE);
        if (rowHostTasks != null) rowHostTasks.setVisibility(View.GONE);
        if (btnHi != null) btnHi.setVisibility(View.GONE);
        if (chipFirstDay != null) chipFirstDay.setVisibility(View.GONE);

        UserSocialNav.wireUserCard(activity, sheet, uid, myId, dialog::dismiss);

        // Hide in-room admin / mic controls outside VoiceRoom.
        hide(sheet, R.id.actMute);
        hide(sheet, R.id.actModerator);
        hide(sheet, R.id.actInviteGuest);
        hide(sheet, R.id.actTaskInvite);
        hide(sheet, R.id.actXo);
        hide(sheet, R.id.actKick);
        hide(sheet, R.id.actBan);
        hide(sheet, R.id.rowMicControls);
        hide(sheet, R.id.actOpenMic);
        hide(sheet, R.id.actCloseMic);
        hide(sheet, R.id.actUnmute);
        // @-mention is room-only.
        hide(sheet, R.id.actAt);

        TextView actionsTitle = sheet.findViewById(R.id.tvUserActionsTitle);
        if (actionsTitle != null) {
            actionsTitle.setText("خيارات المستخدم والتفاعل");
            actionsTitle.setTextColor(0xFF374151);
        }

        if (btnCopyId != null) {
            btnCopyId.setOnClickListener(v -> {
                String pid = publicIdHold[0];
                if (pid == null || pid.isEmpty()) return;
                ClipboardManager cm =
                        (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("ID", pid));
                    Toast.makeText(activity, "تم نسخ المعرف", Toast.LENGTH_SHORT).show();
                }
            });
        }

        container.getIoExecutor().execute(() -> {
            // Count as a profile visit (Mikoo visitor counter).
            if (myId != null && !myId.equals(uid)) {
                try {
                    container.getUserApi().visit(uid).execute();
                } catch (Exception ignored) {
                }
            }
            Result<AuthDtos.UserDto> r =
                    ApiCall.execute(container.getUserApi().getUser(uid));
            if (!r.success || r.data == null) return;
            AuthDtos.UserDto u = r.data;
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                int liveVip = Math.max(0, u.vipLevel);
                vipFrameHold[0] = VipStyle.profileNobilityFrameUrl(liveVip, u.vipTouUrl);
                if (u.avatarUrl != null && !u.avatarUrl.isEmpty()) avatarHold[0] = u.avatarUrl;
                if (u.displayName != null && !u.displayName.isEmpty()) {
                    displayNameHold[0] = u.displayName;
                }
                AvatarCosmetics.bindAvatar(img, avatarHold[0]);
                AvatarCosmetics.applyHostWear(
                        frame, hostBadge, img, vipFrameHold[0], null, null, null);
                bindVipHead(vipHead, liveVip,
                        u.vipHeadUrl != null ? u.vipHeadUrl : VipStyle.fixedHeadPath(liveVip));
                // Head owns: ride entry + VIP medal (Mikoo property head).
                bindEntryRide(imgEntryRide, firstNonEmpty(u.entryAnimationUrl, u.entryEffectUrl));
                bindVipMedal(imgVipMedal, liveVip, u.levelBadgeUrl);
                bindAgency(activity, rowAgency, imgAgencyLogo, tvAgencyName, tvAgencyGid, u.agency);
                AgencyUi.applyUserCardSheet(userCardBg, moreActionsBlock, liveVip);
                if (tvName != null) {
                    tvName.setText(
                            displayNameHold[0].isEmpty() ? "مستخدم" : displayNameHold[0]);
                    GenderVerifiedBadge.bind(tvName, null, u.genderVerified);
                }
                bindGenderIcon(imgGender, u.gender);
                bindOnlineStatus(tvOnlineStatus, u);
                // تحت الـ ID: متابعون · مشجع · زائر
                if (tvStatFollowing != null) {
                    tvStatFollowing.setText(String.valueOf(Math.max(0, u.followersCount)));
                }
                if (tvStatFans != null) {
                    tvStatFans.setText(String.valueOf(Math.max(0, u.followingCount)));
                }
                if (tvStatVisitors != null) {
                    tvStatVisitors.setText(String.valueOf(Math.max(0, u.visitorsCount)));
                }
                if (tvPropFriends != null) {
                    tvPropFriends.setText(String.valueOf(Math.max(0, u.friendsCount)));
                }
                if (tvPropSupporters != null) {
                    tvPropSupporters.setText(String.valueOf(Math.max(0, u.followersCount)));
                }
                if (tvPropFamily != null) {
                    if (u.agency != null && u.agency.name != null && !u.agency.name.isEmpty()) {
                        tvPropFamily.setText(u.agency.name.trim());
                    } else {
                        tvPropFamily.setText(R.string.user_card_prop_family);
                    }
                }
                UserSocialNav.bindFamilyClick(
                        activity,
                        propFamily,
                        u.agency != null ? u.agency.id : null,
                        dialog::dismiss);
                if (tvUserId != null) {
                    String pid = u.publicId != null ? u.publicId.trim() : "";
                    publicIdHold[0] = pid;
                    if (!pid.isEmpty()) {
                        tvUserId.setVisibility(View.VISIBLE);
                        tvUserId.setText("ID:" + pid);
                        if (btnCopyId != null) btnCopyId.setVisibility(View.VISIBLE);
                    } else {
                        tvUserId.setVisibility(View.GONE);
                        if (btnCopyId != null) btnCopyId.setVisibility(View.GONE);
                    }
                }
                if (imgCountryFlag != null) {
                    FlagImages.bind(imgCountryFlag, u.country);
                }
                long popularity = Math.max(
                        Math.max(0, u.popularityLevel),
                        Math.max(0, Math.max(u.charmScore, u.popularityScore)));
                AvatarCosmetics.styleUserCardBadges(
                        chipVip, chipMember, chipCharm, chipWealth,
                        liveVip, Math.max(1, u.level),
                        popularity, Math.max(0, u.wealthScore));
                if (chipMember != null) {
                    chipMember.setText(String.valueOf(Math.max(1, u.level)));
                }
                if (chipCharm != null) {
                    long popLv = u.popularityLevel > 0 ? u.popularityLevel : popularity;
                    chipCharm.setText(String.valueOf(Math.max(0, popLv)));
                }
                if (chipWealth != null) {
                    long wl = u.wealthLevel > 0 ? u.wealthLevel : u.wealthScore;
                    chipWealth.setText(String.valueOf(Math.max(0, wl)));
                }
                styleStatChips(chipCharm, chipWealth);
                boolean showHi = u.showHiBadge || u.isFirstDay;
                if (chipFirstDay != null) {
                    chipFirstDay.setVisibility(showHi ? View.VISIBLE : View.GONE);
                }
                if (btnHi != null) {
                    btnHi.setVisibility(showHi ? View.VISIBLE : View.GONE);
                    btnHi.setOnClickListener(v -> {
                        View chat = sheet.findViewById(R.id.actChat);
                        if (chat != null) chat.performClick();
                    });
                }
            });
        });

        View actProfile = sheet.findViewById(R.id.actProfile);
        if (actProfile != null) {
            actProfile.setOnClickListener(v -> {
                dialog.dismiss();
                UserSocialNav.openFullProfile(activity, uid);
            });
        }

        View actGift = sheet.findViewById(R.id.actGift);
        if (actGift != null) {
            actGift.setOnClickListener(v -> {
                if (uid.equals(myId)) {
                    Toast.makeText(activity, R.string.cannot_gift_yourself, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                dialog.dismiss();
                sheet.post(() ->
                        GiftBottomSheet.showForChat(activity.getSupportFragmentManager(), uid));
            });
        }

        View actChat = sheet.findViewById(R.id.actChat);
        if (actChat != null) {
            boolean alreadyChatting = activity instanceof ChatConversationActivity;
            actChat.setOnClickListener(v -> {
                if (uid.equals(myId)) {
                    Toast.makeText(activity, R.string.cannot_message_yourself, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                if (alreadyChatting) {
                    dialog.dismiss();
                    return;
                }
                dialog.dismiss();
                container.getIoExecutor().execute(() -> {
                    Map<String, String> body = new HashMap<>();
                    body.put("peerId", uid);
                    Result<ChatDtos.ConversationDto> r =
                            ApiCall.execute(container.getChatApi().create(body));
                    activity.runOnUiThread(() -> {
                        if (!r.success || r.data == null || r.data.id == null) {
                            Toast.makeText(
                                            activity,
                                            r.error != null
                                                    ? r.error
                                                    : activity.getString(R.string.error_generic),
                                            Toast.LENGTH_SHORT)
                                    .show();
                            return;
                        }
                        Intent i = new Intent(activity, ChatConversationActivity.class);
                        i.putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, r.data.id);
                        i.putExtra(ChatConversationActivity.EXTRA_PEER_ID, uid);
                        i.putExtra(
                                ChatConversationActivity.EXTRA_TITLE,
                                !displayNameHold[0].isEmpty()
                                        ? displayNameHold[0]
                                        : "محادثة");
                        if (!avatarHold[0].isEmpty()) {
                            i.putExtra(ChatConversationActivity.EXTRA_AVATAR, avatarHold[0]);
                        }
                        if (vipFrameHold[0] != null && !vipFrameHold[0].isEmpty()) {
                            i.putExtra(
                                    ChatConversationActivity.EXTRA_HOST_BADGE, vipFrameHold[0]);
                        }
                        activity.startActivity(i);
                    });
                });
            });
        }

        ImageView actFollow = sheet.findViewById(R.id.actFollow);
        if (actFollow != null) {
            final boolean[] following = {false};
            final boolean[] followBusy = {false};
            applyFollowHeart(actFollow, false);
            if (!uid.equals(myId)) {
                container.getIoExecutor().execute(() -> {
                    Result<AuthDtos.UserDto> profile =
                            ApiCall.execute(container.getUserApi().getUser(uid));
                    if (profile.success
                            && profile.data != null
                            && Boolean.TRUE.equals(profile.data.isFollowing)) {
                        following[0] = true;
                        activity.runOnUiThread(() -> {
                            if (actFollow.getWindowToken() != null) {
                                applyFollowHeart(actFollow, true);
                            }
                        });
                    }
                });
            }
            actFollow.setOnClickListener(v -> {
                if (uid.equals(myId)) {
                    Toast.makeText(activity, R.string.cannot_follow_yourself, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                if (followBusy[0]) return;
                followBusy[0] = true;
                actFollow.setEnabled(false);
                final boolean unfollow = following[0];
                // Optimistic UI: following = solid, not following = faded.
                applyFollowHeart(actFollow, !unfollow);
                container.getIoExecutor().execute(() -> {
                    try {
                        retrofit2.Response<?> resp =
                                (unfollow
                                                ? container.getUserApi().unfollow(uid)
                                                : container.getUserApi().follow(uid))
                                        .execute();
                        activity.runOnUiThread(() -> {
                            followBusy[0] = false;
                            actFollow.setEnabled(true);
                            if (resp.isSuccessful()) {
                                following[0] = !unfollow;
                                applyFollowHeart(actFollow, following[0]);
                                Toast.makeText(
                                                activity,
                                                unfollow
                                                        ? "تم إلغاء المتابعة ✓"
                                                        : "تمت المتابعة ✓",
                                                Toast.LENGTH_SHORT)
                                        .show();
                            } else {
                                applyFollowHeart(actFollow, following[0]);
                                Toast.makeText(
                                                activity,
                                                activity.getString(R.string.error_generic),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                        });
                    } catch (Exception e) {
                        activity.runOnUiThread(() -> {
                            followBusy[0] = false;
                            actFollow.setEnabled(true);
                            applyFollowHeart(actFollow, following[0]);
                            Toast.makeText(
                                            activity,
                                            activity.getString(R.string.error_generic),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        });
                    }
                });
            });
        }

        View actFriend = sheet.findViewById(R.id.actFriend);
        if (actFriend != null) {
            actFriend.setOnClickListener(v -> {
                if (uid.equals(myId)) {
                    Toast.makeText(activity, R.string.cannot_friend_yourself, Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                dialog.dismiss();
                container.getIoExecutor().execute(() -> {
                    try {
                        Map<String, String> body = new HashMap<>();
                        body.put("type", "friend");
                        retrofit2.Response<?> resp =
                                container.getUserApi().sendRequest(uid, body).execute();
                        activity.runOnUiThread(() ->
                                Toast.makeText(
                                                activity,
                                                resp.isSuccessful()
                                                        ? "تم إرسال طلب الصداقة ✓"
                                                        : activity.getString(
                                                                R.string.error_generic),
                                                Toast.LENGTH_SHORT)
                                        .show());
                    } catch (Exception e) {
                        activity.runOnUiThread(() ->
                                Toast.makeText(
                                                activity,
                                                activity.getString(R.string.error_generic),
                                                Toast.LENGTH_SHORT)
                                        .show());
                    }
                });
            });
        }

        View actReport = sheet.findViewById(R.id.actReport);
        if (actReport != null) {
            actReport.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(
                                activity,
                                "للإبلاغ: افتح الملف ← المزيد",
                                Toast.LENGTH_SHORT)
                        .show();
                Intent i = new Intent(activity, ProfileActivity.class);
                i.putExtra(ProfileActivity.EXTRA_USER_ID, uid);
                activity.startActivity(i);
            });
        }

        dialog.show();
    }

    @Nullable
    private static String resolveMyUserId(@NonNull AppContainer container) {
        String id = container.getSessionManager().getUserId();
        if (id != null && !id.isEmpty()) return id;
        AuthDtos.UserDto me = container.getSessionManager().getUser();
        return me != null ? me.id : null;
    }

    private static void hide(@NonNull View root, int id) {
        View v = root.findViewById(id);
        if (v != null) v.setVisibility(View.GONE);
    }

    /**
     * Mikoo attention heart: followed = solid green icon, not following = faded.
     * Stays on the card so the user can toggle and see the state.
     */
    private static void applyFollowHeart(@Nullable ImageView heart, boolean following) {
        if (heart == null) return;
        if (following) {
            heart.setImageResource(R.drawable.icon_attentioned);
            heart.setAlpha(1f);
        } else {
            heart.setImageResource(R.drawable.icon_attention);
            heart.setAlpha(0.42f);
        }
    }

    private static void styleStatChips(@Nullable TextView charm, @Nullable TextView wealth) {
        if (charm != null) charm.setTextColor(0xFFFFFFFF);
        if (wealth != null) wealth.setTextColor(0xFFFFFFFF);
    }

    private static void bindEntryRide(
            @Nullable ImageView rideView, @Nullable String entryUrl) {
        if (rideView == null) return;
        String url = entryUrl != null ? entryUrl.trim() : "";
        if (url.isEmpty()) {
            rideView.setVisibility(View.GONE);
            rideView.setImageDrawable(null);
            return;
        }
        // Prefer static preview: strip video to PNG sibling if possible.
        String preview = url;
        if (preview.endsWith(".mp4") || preview.endsWith(".svga")) {
            int dot = preview.lastIndexOf('.');
            if (dot > 0) preview = preview.substring(0, dot) + ".png";
        }
        rideView.setVisibility(View.VISIBLE);
        try {
            Glide.with(rideView.getContext())
                    .load(AssetCatalog.absoluteUrl(preview))
                    .error(R.drawable.ic_medal_default)
                    .into(rideView);
        } catch (Exception e) {
            rideView.setImageResource(R.drawable.ic_medal_default);
        }
    }

    private static void bindVipHead(
            @Nullable ImageView vipHead, int vipLevel, @Nullable String headUrl) {
        if (vipHead == null) return;
        if (vipLevel <= 0) {
            vipHead.setVisibility(View.GONE);
            vipHead.setImageDrawable(null);
            return;
        }
        String path = headUrl != null && !headUrl.isEmpty()
                ? headUrl
                : VipStyle.fixedHeadPath(vipLevel);
        if (path == null || path.isEmpty()) {
            vipHead.setVisibility(View.GONE);
            return;
        }
        vipHead.setVisibility(View.VISIBLE);
        com.Dramizo.Series.util.ServerAssets.load(vipHead, path);
    }

    private static void bindVipMedal(
            @Nullable ImageView medal, int vipLevel, @Nullable String levelBadgeUrl) {
        if (medal == null) return;
        if (vipLevel <= 0) {
            medal.setVisibility(View.GONE);
            medal.setImageDrawable(null);
            return;
        }
        String path = levelBadgeUrl;
        if (path == null || path.isEmpty() || !path.contains("vip_medal")) {
            int t = Math.min(7, Math.max(1, vipLevel));
            path = "/assets/cosmetics/vip/vip_medal_mikoo_" + t + ".png";
        }
        medal.setVisibility(View.VISIBLE);
        com.Dramizo.Series.util.ServerAssets.load(medal, path);
    }

    private static void bindAgency(
            @NonNull FragmentActivity activity,
            @Nullable View rowAgency,
            @Nullable ImageView logoView,
            @Nullable TextView nameView,
            @Nullable TextView gidView,
            @Nullable AuthDtos.UserDto.AgencySnip agency) {
        if (rowAgency == null) return;
        if (agency == null || agency.name == null || agency.name.trim().isEmpty()) {
            rowAgency.setVisibility(View.GONE);
            return;
        }
        rowAgency.setVisibility(View.VISIBLE);
        if (nameView != null) nameView.setText(agency.name.trim());
        if (gidView != null) {
            String gid = agency.publicId != null ? agency.publicId.trim() : "";
            // Agency GID only — never owner user publicId.
            gidView.setVisibility(View.VISIBLE);
            gidView.setText(gid.isEmpty() ? "GID: —" : ("GID:" + gid));
        }
        int tier = agency.level > 0
                ? agency.level
                : AgencyUi.bannerTierFromDiamonds(agency.totalDiamonds);
        ImageView banner = rowAgency.findViewById(R.id.imgAgencyBannerBg);
        TextView levelView = rowAgency.findViewById(R.id.tvAgencyLevel);
        AgencyUi.bindBanner(banner, levelView, tier);
        if (logoView != null) {
            AgencyUi.bindLogo(logoView, agency.logoUrl, agency.coverUrl);
        }
        rowAgency.setOnClickListener(v -> {
            if (agency.id == null || agency.id.isEmpty()) return;
            try {
                AgencyFamilyInfoSheet.show(
                        activity, agency.id, agency.logoUrl, agency.coverUrl);
            } catch (Exception ignored) {
            }
        });
    }

    private static void bindGenderIcon(@Nullable ImageView view, @Nullable String gender) {
        if (view == null) return;
        if (gender == null || gender.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        String g = gender.trim().toLowerCase();
        if ("male".equals(g) || "m".equals(g) || "ذكر".equals(gender)) {
            view.setVisibility(View.VISIBLE);
            view.setImageResource(R.drawable.ic_gender_male);
        } else if ("female".equals(g) || "f".equals(g)
                || "أنثى".equals(gender) || "انثى".equals(gender)) {
            view.setVisibility(View.VISIBLE);
            view.setImageResource(R.drawable.ic_gender_female);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    private static void bindOnlineStatus(
            @Nullable TextView view, @NonNull AuthDtos.UserDto u) {
        if (view == null) return;
        if (Boolean.TRUE.equals(u.isOnline)) {
            view.setVisibility(View.VISIBLE);
            view.setText(R.string.online_now);
            view.setTextColor(0xFF7CFFB2);
        } else {
            view.setVisibility(View.GONE);
        }
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
