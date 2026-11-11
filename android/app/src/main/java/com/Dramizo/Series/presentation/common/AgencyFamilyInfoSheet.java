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
        show(activity, agencyId, null, null, false);
    }

    public static void show(
            @NonNull FragmentActivity activity,
            @Nullable String agencyId,
            @Nullable String logoHint,
            @Nullable String coverHint) {
        show(activity, agencyId, logoHint, coverHint, false);
    }



    private static void show(
            @NonNull FragmentActivity activity,
            @Nullable String agencyId,
            @Nullable String logoHint,
            @Nullable String coverHint,
            boolean openRequestsOnLoad) {
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
        final MiscDtos.AgencyDto[] aHolder = {null};

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
                aHolder[0] = a;
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
                if (tvFollowers != null) tvFollowers.setText(formatCount(Math.max(0, a.followerCount)));
                following[0] = Boolean.TRUE.equals(a.isFollowing);
                applyFollowUi(btnFollow, following[0]);
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


        TextView membersTab = sheet.findViewById(R.id.btnAgencyMembersTab);
        TextView followersTab = sheet.findViewById(R.id.btnAgencyFollowersTab);
        TextView roomsTab = sheet.findViewById(R.id.btnAgencyRoomsTab);
        TextView messageTab = sheet.findViewById(R.id.btnAgencyMessageTab);
        TextView rosterSummary = sheet.findViewById(R.id.tvAgencyRosterSummary);
        android.widget.LinearLayout peopleList = sheet.findViewById(R.id.agencyPeopleList);
        if (membersTab != null) membersTab.setOnClickListener(v -> {
            applyAgencyTabUi(membersTab, followersTab, roomsTab, messageTab, 0);
            loadAgencyPeople(activity, container, id, peopleList, rosterSummary, false);
        });
        if (followersTab != null) followersTab.setOnClickListener(v -> {
            applyAgencyTabUi(membersTab, followersTab, roomsTab, messageTab, 1);
            loadAgencyPeople(activity, container, id, peopleList, rosterSummary, true);
        });
        if (roomsTab != null) roomsTab.setOnClickListener(v -> {
            applyAgencyTabUi(membersTab, followersTab, roomsTab, messageTab, 2);
            loadAgencyRoom(activity, container, id, peopleList, rosterSummary, dialog);
        });
        if (messageTab != null) messageTab.setOnClickListener(v -> {
            applyAgencyTabUi(membersTab, followersTab, roomsTab, messageTab, 3);
            if (aHolder[0] != null && aHolder[0].owner != null && aHolder[0].owner.id != null) {
                UserProfileCardSheet.show(activity, aHolder[0].owner.id, aHolder[0].owner.displayName, aHolder[0].owner.avatarUrl);
            } else {
                Toast.makeText(activity, "المراسلة غير متاحة حالياً", Toast.LENGTH_SHORT).show();
            }
        });
        loadAgencyPeople(activity, container, id, peopleList, rosterSummary, false);
        dialog.show();
    }


    private static int agencyRoleOrder(@Nullable String role) {
        if ("owner".equalsIgnoreCase(role)) return 0;
        if ("manager".equalsIgnoreCase(role)) return 1;
        if ("host".equalsIgnoreCase(role)) return 2;
        return 3;
    }

    private static void applyAgencyTabUi(@Nullable TextView members, @Nullable TextView followers,
            @Nullable TextView rooms, @Nullable TextView message, int selected) {
        TextView[] tabs = {members, followers, rooms, message};
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) {
                tabs[i].setTextColor(i == selected ? 0xFFFFFFFF : 0xBFFFFFFF);
                tabs[i].setTypeface(null, i == selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
        }
    }

    private static void loadAgencyRoom(@NonNull FragmentActivity activity, @NonNull AppContainer container,
            @NonNull String agencyId, @Nullable android.widget.LinearLayout list,
            @Nullable TextView summary, @NonNull BottomSheetDialog dialog) {
        if (list == null) return;
        list.removeAllViews();
        MiscDtos.AgencyDto agency = null;
        // Room-list endpoint is not exposed by the current AgencyApi contract.
        // Show the verified room count instead of calling a non-existent API.
        TextView info = new TextView(activity);
        info.setText("غرف الوكالة المسجلة: " + (summary != null ? summary.getText() : "—")
                + "\\nيمكن فتح غرفة الوكالة من قائمة الغرف داخل التطبيق.");
        info.setTextColor(0xFFFFFFFF);
        info.setTextSize(14);
        info.setGravity(android.view.Gravity.CENTER);
        info.setPadding(dp(activity, 16), dp(activity, 22), dp(activity, 16), dp(activity, 22));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF151F28);
        bg.setCornerRadius(dp(activity, 16));
        bg.setStroke(dp(activity, 1), 0xFF34414D);
        info.setBackground(bg);
        list.addView(info, new android.widget.LinearLayout.LayoutParams(-1, -2));
        if (summary != null) summary.setText("الغرف");
    }

    private static void loadAgencyPeople(@NonNull FragmentActivity activity, @NonNull AppContainer container,
            @NonNull String agencyId, @Nullable android.widget.LinearLayout list,
            @Nullable TextView summary, boolean followers) {
        if (list == null) return;
        list.removeAllViews();
        TextView loading = new TextView(activity);
        loading.setText("جارٍ تحميل القائمة...");
        loading.setTextColor(0xBFFFFFFF);
        loading.setGravity(android.view.Gravity.CENTER);
        list.addView(loading);
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(followers
                    ? container.getAgencyApi().publicFollowers(agencyId, 1, 40)
                    : container.getAgencyApi().publicMembers(agencyId, 1, 40));
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                list.removeAllViews();
                if (!result.success || result.data == null) {
                    TextView error = new TextView(activity);
                    error.setText("تعذر تحميل القائمة");
                    error.setTextColor(0xFFFF8A8A);
                    error.setGravity(android.view.Gravity.CENTER);
                    list.addView(error);
                    return;
                }
                Object raw = result.data.get("items");
                java.util.List<?> rows = raw instanceof java.util.List ? new java.util.ArrayList<>((java.util.List<?>) raw) : new java.util.ArrayList<>();
                if (!followers) {
                    ((java.util.List<Object>) rows).sort((left, right) -> {
                        String lr = left instanceof Map ? String.valueOf(((Map<?, ?>) left).get("role")) : "";
                        String rr = right instanceof Map ? String.valueOf(((Map<?, ?>) right).get("role")) : "";
                        return Integer.compare(agencyRoleOrder(lr), agencyRoleOrder(rr));
                    });
                }
                Object count = result.data.get("total");
                long total = count instanceof Number ? ((Number) count).longValue() : rows.size();
                if (summary != null) summary.setText((followers ? "المتابعون • " : "الأعضاء • ") + formatCount(total));
                if (rows.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText(followers ? "لا يوجد متابعون حتى الآن" : "لا يوجد أعضاء حتى الآن");
                    empty.setTextColor(0xBFFFFFFF);
                    empty.setGravity(android.view.Gravity.CENTER);
                    empty.setPadding(0, dp(activity, 18), 0, dp(activity, 18));
                    list.addView(empty);
                    return;
                }
                for (Object value : rows) {
                    if (!(value instanceof Map)) continue;
                    Map<?, ?> row = (Map<?, ?>) value;
                    Map<?, ?> user = row.get("user") instanceof Map ? (Map<?, ?>) row.get("user") : row;
                    String name = String.valueOf(user.get("displayName") != null ? user.get("displayName") :
                            (user.get("username") != null ? user.get("username") : "مستخدم"));
                    String publicId = String.valueOf(user.get("publicId") == null ? "—" : user.get("publicId"));
                    String avatar = user.get("avatarUrl") == null ? null : String.valueOf(user.get("avatarUrl"));
                    String role = String.valueOf(row.get("role") == null ? "" : row.get("role"));
                    String roleLabel = followers ? "متابع للوكالة" :
                            "owner".equalsIgnoreCase(role) ? "قائد الوكالة" :
                            "manager".equalsIgnoreCase(role) ? "مشرف الوكالة" :
                            "host".equalsIgnoreCase(role) ? "مضيف الوكالة" : "عضو الوكالة";
                    android.widget.LinearLayout card = new android.widget.LinearLayout(activity);
                    card.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                    card.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    card.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
                    android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                    bg.setColor(0xFF151F28);
                    bg.setCornerRadius(dp(activity, 14));
                    bg.setStroke(dp(activity, 1), 0xFF34414D);
                    card.setBackground(bg);
                    android.widget.ImageView avatarView = new android.widget.ImageView(activity);
                    android.widget.LinearLayout.LayoutParams avatarLp = new android.widget.LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46));
                    avatarLp.setMarginEnd(dp(activity, 10));
                    card.addView(avatarView, avatarLp);
                    AvatarCosmetics.bindAvatar(avatarView, avatar);
                    android.widget.LinearLayout details = new android.widget.LinearLayout(activity);
                    details.setOrientation(android.widget.LinearLayout.VERTICAL);
                    card.addView(details, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));
                    TextView title = new TextView(activity);
                    title.setText(roleLabel + "  •  " + name);
                    title.setTextColor(0xFFFFFFFF);
                    title.setTextSize(14);
                    title.setTypeface(null, android.graphics.Typeface.BOLD);
                    details.addView(title);
                    TextView idLine = new TextView(activity);
                    String country = String.valueOf(user.get("country") == null ? "" : user.get("country"));
                    idLine.setText("ID: " + publicId + (country.trim().isEmpty() || "null".equals(country) ? "" : "   •   " + country));
                    idLine.setTextColor(0xFFB6C1CC);
                    idLine.setTextSize(11);
                    details.addView(idLine);
                    String city = String.valueOf(user.get("city") == null ? "" : user.get("city"));
                    Object levelRaw = user.get("level");
                    String extra = !city.trim().isEmpty() && !"null".equals(city) ? city : "";
                    if (levelRaw instanceof Number) extra += (extra.isEmpty() ? "" : "   •   ") + "المستوى " + ((Number) levelRaw).intValue();
                    if (!extra.isEmpty()) {
                        TextView metaLine = new TextView(activity);
                        metaLine.setText(extra);
                        metaLine.setTextColor(0xFF8997A6);
                        metaLine.setTextSize(10);
                        details.addView(metaLine);
                    }
                    android.widget.LinearLayout.LayoutParams cardLp = new android.widget.LinearLayout.LayoutParams(-1, -2);
                    cardLp.bottomMargin = dp(activity, 7);
                    list.addView(card, cardLp);
                }
            });
        });
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void applyFollowUi(@Nullable TextView btn, boolean following) {
        if (btn == null) return;
        btn.setSelected(following);
        btn.setText(following
                ? R.string.agency_family_following
                : R.string.agency_family_follow);
        btn.setVisibility(View.VISIBLE);\n        btn.setTextColor(0xFFFFFFFF);\n        btn.setEnabled(true);\n        btn.bringToFront();
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
