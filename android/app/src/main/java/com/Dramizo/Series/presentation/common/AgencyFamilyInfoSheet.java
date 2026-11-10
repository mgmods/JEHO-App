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
import com.Dramizo.Series.util.StaffRoleHelper;
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
        TextView tvClubSummary = sheet.findViewById(R.id.tvAgencyClubSummary);
        TextView tvClubMembers = sheet.findViewById(R.id.tvAgencyClubMembers);
        TextView btnClubJoin = sheet.findViewById(R.id.btnAgencyClubJoin);
        TextView btnClubRequests = sheet.findViewById(R.id.btnAgencyClubRequests);
        final boolean[] isActiveClubSupporter = {false};

        if (btnClubJoin != null) {
            btnClubJoin.setOnClickListener(v -> {
                if (isActiveClubSupporter[0]) {
                    new android.app.AlertDialog.Builder(activity)
                            .setTitle("مغادرة نادي الوكالة")
                            .setMessage("هل تريد مغادرة نادي هذه الوكالة؟")
                            .setNegativeButton("إلغاء", null)
                            .setPositiveButton("مغادرة", (d, which) -> {
                                btnClubJoin.setEnabled(false);
                                container.getIoExecutor().execute(() -> {
                                    Result<Map<String, Object>> result = ApiCall.execute(
                                            container.getAgencyApi().leaveClub(id));
                                    activity.runOnUiThread(() -> {
                                        if (!dialog.isShowing()) return;
                                        if (result.success) {
                                            isActiveClubSupporter[0] = false;
                                            btnClubJoin.setText("طلب الانضمام إلى النادي");
                                            btnClubJoin.setEnabled(true);
                                            Toast.makeText(activity, "غادرت نادي الوكالة", Toast.LENGTH_SHORT).show();
                                        } else {
                                            btnClubJoin.setEnabled(true);
                                            Toast.makeText(activity,
                                                    result.error != null ? result.error : activity.getString(R.string.error_generic),
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                });
                            })
                            .show();
                    return;
                }
                btnClubJoin.setEnabled(false);
                container.getIoExecutor().execute(() -> {
                    Result<Map<String, Object>> request = ApiCall.execute(
                            container.getAgencyApi().requestClubJoin(id));
                    activity.runOnUiThread(() -> {
                        if (!dialog.isShowing()) return;
                        if (request.success) {
                            String clubStatus = request.data == null ? "pending"
                                    : String.valueOf(request.data.get("status"));
                            if ("active".equalsIgnoreCase(clubStatus)
                                    || "active_staff".equalsIgnoreCase(clubStatus)
                                    || "active_host".equalsIgnoreCase(clubStatus)
                                    || "active_member".equalsIgnoreCase(clubStatus)) {
                                isActiveClubSupporter[0] = "active".equalsIgnoreCase(clubStatus);
                                btnClubJoin.setText(isActiveClubSupporter[0]
                                        ? "مغادرة النادي" : "أنت ضمن أعضاء/طاقم هذه الوكالة");
                                Toast.makeText(activity, "أنت بالفعل ضمن نادي أو طاقم الوكالة",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                btnClubJoin.setText("طلبك قيد مراجعة صاحب الوكالة");
                                Toast.makeText(activity,
                                        Boolean.TRUE.equals(request.data == null ? null : request.data.get("alreadyRequested"))
                                                ? "طلبك موجود مسبقاً وقيد المراجعة"
                                                : "تم إرسال طلبك إلى إدارة الوكالة",
                                        Toast.LENGTH_SHORT).show();
                            }
                            btnClubJoin.setEnabled(isActiveClubSupporter[0]);
                        } else {
                            btnClubJoin.setEnabled(true);
                            Toast.makeText(activity,
                                    request.error != null ? request.error : activity.getString(R.string.error_generic),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            });
        }

        TextView btnOpen = sheet.findViewById(R.id.btnOpenAgencyManage);

        final String[] gidHold = {""};
        final boolean[] following = {false};

        if (btnOpen != null) {
            // Fail closed while membership/role is being verified.
            btnOpen.setVisibility(View.GONE);
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
            Result<MiscDtos.AgencyMineDto> mine = container.getAgencyRepository().mine();
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
                boolean isSuper = false;
                try {
                    isSuper = StaffRoleHelper.isSuper(container.getSessionManager().getUser());
                } catch (Exception ignored) { }
                String role = mine != null && mine.success && mine.data != null
                        && mine.data.role != null
                        ? mine.data.role.trim().toLowerCase(java.util.Locale.US) : "";
                boolean belongsToThisAgency = mine != null && mine.success && mine.data != null
                        && mine.data.agency != null && mine.data.agency.id != null
                        && id.equals(mine.data.agency.id)
                        && !"pending".equalsIgnoreCase(mine.data.membershipStatus);
                boolean canManageThisAgency = isSuper || (belongsToThisAgency
                        && ("owner".equals(role) || "manager".equals(role)));
                if (btnOpen != null) {
                    btnOpen.setVisibility(canManageThisAgency ? View.VISIBLE : View.GONE);
                }
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


        // Club membership is distinct from following the agency and from host employment.
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> club = ApiCall.execute(container.getAgencyApi().getClub(id));
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing() || !club.success || club.data == null) return;
                Map<String, Object> data = club.data;
                Object countsValue = data.get("counts");
                Map<?, ?> counts = countsValue instanceof Map ? (Map<?, ?>) countsValue : java.util.Collections.emptyMap();
                int total = counts.get("total") instanceof Number ? ((Number) counts.get("total")).intValue() : 0;
                int hosts = counts.get("staff") instanceof Number ? ((Number) counts.get("staff")).intValue() : 0;
                int supporters = counts.get("supporters") instanceof Number ? ((Number) counts.get("supporters")).intValue() : 0;
                int agencyMembers = counts.get("agencyMembers") instanceof Number ? ((Number) counts.get("agencyMembers")).intValue() : 0;
                int pending = counts.get("pending") instanceof Number ? ((Number) counts.get("pending")).intValue() : 0;
                if (tvClubSummary != null) {
                    tvClubSummary.setText("نادي الوكالة • " + formatCount(total) +
                            " عضو • " + formatCount(hosts) + " مضيف/إدارة • " + formatCount(agencyMembers) + " عضو وكالة • " +
                            formatCount(supporters) + " داعم" +
                            (pending > 0 ? " • " + formatCount(pending) + " طلب بانتظارك" : ""));
                }
                if (btnClubRequests != null) {
                    boolean canReview = Boolean.TRUE.equals(data.get("canReview"));
                    btnClubRequests.setVisibility(canReview && pending > 0 ? View.VISIBLE : View.GONE);
                    btnClubRequests.setText("مراجعة طلبات الانضمام • " + formatCount(pending));
                    btnClubRequests.setOnClickListener(v ->
                            loadClubRequests(activity, dialog, container, id));
                }
                Object membersValue = data.get("members");
                java.util.ArrayList<String> rosterItems = new java.util.ArrayList<>();
                StringBuilder rosterPreview = new StringBuilder();
                if (membersValue instanceof java.util.List) {
                    for (Object item : (java.util.List<?>) membersValue) {
                        if (!(item instanceof Map)) continue;
                        Map<?, ?> member = (Map<?, ?>) item;
                        Object name = member.get("displayName");
                        if (name == null) name = member.get("username");
                        String role = String.valueOf(member.get("role") == null ? "" : member.get("role"));
                        String roleLabel = "host".equalsIgnoreCase(role) ? "مضيف الوكالة" :
                                ("owner".equalsIgnoreCase(role) ? "صاحب الوكالة" :
                                ("manager".equalsIgnoreCase(role) ? "إدارة الوكالة" :
                                ("member".equalsIgnoreCase(role) ? "عضو الوكالة" : "داعم النادي")));
                        String display = (name == null ? "مستخدم" : String.valueOf(name)) + "  ·  " + roleLabel;
                        rosterItems.add(display);
                        if (rosterPreview.length() < 150) {
                            if (rosterPreview.length() > 0) rosterPreview.append("  •  ");
                            rosterPreview.append(display);
                        }
                    }
                }
                if (tvClubMembers != null) {
                    tvClubMembers.setText(rosterItems.isEmpty() ? "لا يوجد أعضاء بعد" :
                            rosterPreview + (rosterItems.size() > 3 ? "  ·  عرض الكل" : ""));
                    tvClubMembers.setOnClickListener(v -> {
                        if (rosterItems.isEmpty()) return;
                        new android.app.AlertDialog.Builder(activity)
                                .setTitle("أعضاء نادي الوكالة")
                                .setItems(rosterItems.toArray(new String[0]), (d, which) -> { })
                                .setPositiveButton("إغلاق", null)
                                .show();
                    });
                }
                String myStatus = String.valueOf(data.get("myStatus"));
                if (btnClubJoin != null) {
                    if ("active_staff".equals(myStatus) || "active_host".equals(myStatus)) {
                        btnClubJoin.setText("أنت ضمن طاقم هذه الوكالة");
                        btnClubJoin.setEnabled(false);
                    } else if ("active_member".equals(myStatus)) {
                        btnClubJoin.setText("أنت عضو في هذه الوكالة");
                        btnClubJoin.setEnabled(false);
                    } else if ("active".equals(myStatus)) {
                        isActiveClubSupporter[0] = true;
                        btnClubJoin.setText("مغادرة النادي");
                        btnClubJoin.setEnabled(true);
                    } else if ("pending".equals(myStatus)) {
                        btnClubJoin.setText("طلبك قيد مراجعة صاحب الوكالة");
                        btnClubJoin.setEnabled(false);
                    } else {
                        btnClubJoin.setText("طلب الانضمام إلى النادي");
                        btnClubJoin.setEnabled(true);
                    }
                }
            });
        });

        dialog.show();
    }

    private static void loadClubRequests(@NonNull FragmentActivity activity, @NonNull BottomSheetDialog parent,
            @NonNull AppContainer container, @NonNull String agencyId) {
        container.getIoExecutor().execute(() -> {
            Result<java.util.List<Map<String, Object>>> result = ApiCall.execute(container.getAgencyApi().clubRequests(agencyId));
            activity.runOnUiThread(() -> {
                if (!parent.isShowing()) return;
                if (!result.success) {
                    Toast.makeText(activity,
                            result.error != null ? result.error : "تعذر تحميل طلبات النادي. تحقق من الاتصال وحاول مجدداً.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (result.data == null || result.data.isEmpty()) {
                    Toast.makeText(activity, "لا توجد طلبات انضمام معلّقة حالياً", Toast.LENGTH_SHORT).show();
                    return;
                }
                showNextClubRequest(activity, parent, container, agencyId, result.data, 0);
            });
        });
    }

    private static void showNextClubRequest(@NonNull FragmentActivity activity, @NonNull BottomSheetDialog parent,
            @NonNull AppContainer container, @NonNull String agencyId,
            @NonNull java.util.List<Map<String, Object>> requests, int index) {
        if (!parent.isShowing() || index >= requests.size()) {
            Toast.makeText(activity, "تمت مراجعة طلبات النادي", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> request = requests.get(index);
        String userId = String.valueOf(request.get("userId"));
        Object rawName = request.get("displayName");
        String name = rawName == null ? String.valueOf(request.get("username")) : String.valueOf(rawName);
        Object rawPublicId = request.get("publicId");
        String publicId = rawPublicId == null ? "" : String.valueOf(rawPublicId).trim();
        String details = "يرغب " + name + " بالانضمام إلى نادي الوكالة.";
        if (!publicId.isEmpty()) details += "\nمعرّف المستخدم: " + publicId;
        AuraDialogHelper.confirm(
                activity,
                "طلب انضمام إلى نادي الوكالة",
                details,
                "موافقة",
                () -> reviewClubRequest(activity, parent, container, agencyId, userId, true),
                "رفض",
                () -> reviewClubRequest(activity, parent, container, agencyId, userId, false));
    }

    private static void reviewClubRequest(@NonNull FragmentActivity activity, @NonNull BottomSheetDialog parent,
            @NonNull AppContainer container, @NonNull String agencyId, @NonNull String userId, boolean approve) {
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(approve
                ? container.getAgencyApi().approveClubRequest(agencyId, userId)
                : container.getAgencyApi().rejectClubRequest(agencyId, userId));
            activity.runOnUiThread(() -> {
                if (!result.success) {
                    Toast.makeText(activity, "تعذر تنفيذ القرار. حدّث الطلبات وحاول مرة أخرى.", Toast.LENGTH_SHORT).show();
                    return;
                }
                loadClubRequests(activity, parent, container, agencyId);
            });
        });
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
