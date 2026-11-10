package com.Dramizo.Series.presentation.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityNotificationsBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.NotificationRouter;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends ThemedActivity {
    public static final String EXTRA_CP_REQUEST_ID = "cp_notification_request_id";
    public static final String EXTRA_CP_REQUEST_TITLE = "cp_notification_request_title";
    public static final String EXTRA_CP_REQUEST_BODY = "cp_notification_request_body";
    private ActivityNotificationsBinding binding;
    private NotificationsViewModel vm;
    private NotificationAdapter adapter;
    private List<MiscDtos.NotificationDto> all = new ArrayList<>();
    /** 0 = personal, 1 = general */
    private int tab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.notifications_personal));
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.notifications_general));
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab t) {
                tab = t.getPosition();
                applyFilter();
            }
            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });

        vm = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(NotificationsViewModel.class);
        adapter = new NotificationAdapter(n -> {
            if (n != null && n.id != null && !n.isRead) vm.markRead(n.id);
            if (isBadgeAwardNotification(n)) {
                String currentUserId = ContainerProvider.from(this).getSessionManager().getUserId();
                Intent badges = new Intent(this, com.Dramizo.Series.presentation.profile.BadgeActivity.class);
                badges.putExtra(com.Dramizo.Series.presentation.profile.BadgeActivity.EXTRA_USER_ID, currentUserId);
                startActivity(badges);
                return;
            }
            if (isCpRequestNotification(n)) {
                showCpRequestPrompt(n);
                return;
            }
            if (isAgencyJoinRequestNotification(n)) {
                showAgencyJoinRequestPrompt(n);
                return;
            }
            if (n != null) {
                Intent target = NotificationRouter.build(this, n.type, n.data);
                if (!(target.getComponent() != null
                        && target.getComponent().getClassName().equals(getClass().getName()))) {
                    startActivity(target);
                }
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        vm.getItems().observe(this, list -> {
            all = list != null ? new ArrayList<>(list) : new ArrayList<>();
            applyFilter();
        });
        vm.getError().observe(this, e -> {
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
        });
        vm.load();
        String cpRequestId = getIntent().getStringExtra(EXTRA_CP_REQUEST_ID);
        if (cpRequestId != null && !cpRequestId.trim().isEmpty()) {
            String cpTitle = getIntent().getStringExtra(EXTRA_CP_REQUEST_TITLE);
            String cpBody = getIntent().getStringExtra(EXTRA_CP_REQUEST_BODY);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> showCpRequestPrompt(cpRequestId, cpTitle, cpBody), 350);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent == null) return;
        String requestId = intent.getStringExtra(EXTRA_CP_REQUEST_ID);
        if (requestId == null || requestId.trim().isEmpty()) return;
        String title = intent.getStringExtra(EXTRA_CP_REQUEST_TITLE);
        String body = intent.getStringExtra(EXTRA_CP_REQUEST_BODY);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                () -> showCpRequestPrompt(requestId, title, body), 250);
    }

    private static String notificationValue(MiscDtos.NotificationDto n, String key) {
        if (n == null || n.data == null || n.data.get(key) == null) return null;
        String value = String.valueOf(n.data.get(key)).trim();
        return value.isEmpty() || "null".equalsIgnoreCase(value) ? null : value;
    }

    private static boolean isAgencyJoinRequestNotification(MiscDtos.NotificationDto n) {
        String action = notificationValue(n, "action");
        return "join_request".equalsIgnoreCase(action)
                || "agency_club_request".equalsIgnoreCase(action);
    }

    private void showAgencyJoinRequestPrompt(MiscDtos.NotificationDto n) {
        String agencyId = notificationValue(n, "agencyId");
        String userId = notificationValue(n, "applicantId");
        boolean clubRequest = "agency_club_request".equalsIgnoreCase(notificationValue(n, "action"));
        if (clubRequest) userId = notificationValue(n, "requesterId");
        if (agencyId == null || userId == null) {
            startActivity(NotificationRouter.build(this, n.type, n.data));
            return;
        }
        final String targetUserId = userId;
        String title = n.title == null ? "طلب انضمام جديد" : n.title;
        String message = n.body == null ? "يوجد طلب انضمام إلى الوكالة." : n.body;
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, 0);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        logo.setBackgroundResource(R.drawable.bg_notification_avatar);
        logo.setClipToOutline(true);
        android.widget.LinearLayout.LayoutParams logoLp = new android.widget.LinearLayout.LayoutParams(
                Math.round(76 * getResources().getDisplayMetrics().density),
                Math.round(76 * getResources().getDisplayMetrics().density));
        logoLp.bottomMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        content.addView(logo, logoLp);
        String logoUrl = notificationValue(n, "agencyLogoUrl");
        if (logoUrl != null) {
            com.bumptech.glide.Glide.with(this)
                    .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(logoUrl))
                    .placeholder(R.drawable.icon_agency)
                    .error(R.drawable.icon_agency)
                    .circleCrop()
                    .into(logo);
        } else {
            logo.setImageResource(R.drawable.icon_agency);
        }
        android.widget.TextView details = new android.widget.TextView(this);
        details.setText(message);
        details.setTextColor(0xFF514B5C);
        details.setTextSize(15);
        details.setGravity(android.view.Gravity.CENTER);
        details.setPadding(0, 0, 0, pad);
        content.addView(details, new android.widget.LinearLayout.LayoutParams(-1, -2));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setPositiveButton("✓ موافقة", (dialog, which) ->
                        respondToAgencyJoinRequest(agencyId, targetUserId, clubRequest, true))
                .setNegativeButton("رفض الطلب", (dialog, which) ->
                        respondToAgencyJoinRequest(agencyId, targetUserId, clubRequest, false))
                .setNeutralButton("لاحقاً", (dialog, which) -> {
                    Intent target = NotificationRouter.build(this, n.type, n.data);
                    startActivity(target);
                })
                .show();
    }

    private void respondToAgencyJoinRequest(String agencyId, String userId,
                                            boolean clubRequest, boolean accept) {
        com.Dramizo.Series.di.AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            com.Dramizo.Series.domain.model.Result<?> result;
            if (clubRequest) {
                result = com.Dramizo.Series.util.ApiCall.execute(accept
                        ? container.getAgencyApi().approveClubRequest(agencyId, userId)
                        : container.getAgencyApi().rejectClubRequest(agencyId, userId));
            } else {
                result = com.Dramizo.Series.util.ApiCall.execute(accept
                        ? container.getAgencyApi().approveJoin(agencyId, userId)
                        : container.getAgencyApi().rejectJoin(agencyId, userId));
            }
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String message = !result.success
                        ? (result.error != null ? result.error : "تعذرت معالجة طلب الانضمام")
                        : (accept ? "تم قبول طلب الانضمام بنجاح" : "تم رفض طلب الانضمام");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                vm.load();
            });
        });
    }

    private void showCpRequestPrompt(MiscDtos.NotificationDto notification) {
        if (notification == null || notification.data == null) return;
        Object rawId = notification.data.get("requestId");
        if (rawId == null || String.valueOf(rawId).trim().isEmpty()) {
            Intent target = NotificationRouter.build(this, notification.type, notification.data);
            startActivity(target);
            return;
        }
        showCpRequestPrompt(
                String.valueOf(rawId),
                notification.title,
                notification.body);
    }

    private void showCpRequestPrompt(String requestId, String title, String body) {
        if (isFinishing() || requestId == null || requestId.trim().isEmpty()) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setIcon(R.drawable.gift_cp_hx)
                .setTitle(title == null || title.trim().isEmpty() ? "طلب ارتباط جديد" : title)
                .setMessage(body == null || body.trim().isEmpty()
                        ? "لديك طلب ارتباط جديد. هل تريد قبول العلاقة أم رفضها؟" : body)
                .setPositiveButton("قبول العلاقة", (dialog, which) -> respondToCpRequest(requestId, true))
                .setNegativeButton("رفض الطلب", (dialog, which) -> respondToCpRequest(requestId, false))
                .setNeutralButton("لاحقاً", (dialog, which) -> {
                    Intent target = new Intent(this, com.Dramizo.Series.presentation.cp.CpCenterActivity.class);
                    target.putExtra(com.Dramizo.Series.presentation.cp.CpCenterActivity.EXTRA_VIEW, "incoming");
                    startActivity(target);
                })
                .show();
    }

    private void respondToCpRequest(String requestId, boolean accept) {
        java.util.Map<String, Boolean> body = new java.util.HashMap<>();
        body.put("accept", accept);
        com.Dramizo.Series.di.AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            com.Dramizo.Series.domain.model.Result<java.util.Map<String, Object>> result =
                    com.Dramizo.Series.util.ApiCall.execute(
                            container.getUserApi().respondCpRequest(requestId, body));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String message = !result.success
                        ? (result.error != null ? result.error : "تعذرت معالجة طلب الارتباط")
                        : (accept ? "تم قبول العلاقة بنجاح" : "تم رفض الطلب");
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                vm.load();
            });
        });
    }

    private void applyFilter() {
        List<MiscDtos.NotificationDto> filtered = new ArrayList<>();
        for (MiscDtos.NotificationDto n : all) {
            if (tab == 0) {
                if (isPersonal(n)) filtered.add(n);
            } else {
                if (!isPersonal(n)) filtered.add(n);
            }
        }
        adapter.submit(filtered);
        binding.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static boolean isBadgeAwardNotification(MiscDtos.NotificationDto n) {
        if (n == null || n.data == null) return false;
        return "badge_earned".equalsIgnoreCase(String.valueOf(n.data.get("kind")));
    }

    private static boolean isCpRequestNotification(MiscDtos.NotificationDto n) {
        if (n == null || n.data == null) return false;
        return matchesCpRequest(n.data.get("type"))
                || matchesCpRequest(n.data.get("notificationKind"))
                || matchesCpRequest(n.data.get("eventType"));
    }

    private static boolean matchesCpRequest(Object value) {
        return value != null && "cp_relationship_request".equalsIgnoreCase(String.valueOf(value));
    }

    /** Messages, follows, gifts, live/room opens — personal. System/VIP/wallet/agency — general. */
    private static boolean isPersonal(MiscDtos.NotificationDto n) {
        if (n == null || n.type == null) return false;
        String t = n.type.toLowerCase();
        if ("chat".equals(t) || "message".equals(t) || "follow".equals(t)
                || "gift".equals(t) || "live".equals(t) || "stream".equals(t)
                || "friend".equals(t) || "relation".equals(t) || "room".equals(t)) {
            return true;
        }
        // A dashboard-awarded badge belongs in the recipient's personal inbox.
        if (isBadgeAwardNotification(n)) return true;
        if (n.data != null) {
            Object action = n.data.get("action");
            if (action != null && (String.valueOf(action).toLowerCase().startsWith("agency_club_")
                    || "join_request".equalsIgnoreCase(String.valueOf(action))
                    || "join_request_sent".equalsIgnoreCase(String.valueOf(action)))) {
                return true;
            }
            Object nested = n.data.get("type");
            Object kind = n.data.get("notificationKind");
            Object eventType = n.data.get("eventType");
            if (matchesCpRequest(nested) || matchesCpRequest(kind) || matchesCpRequest(eventType)) return true;
            if (nested != null) {
                String nt = String.valueOf(nested).toLowerCase();
                if ("room".equals(nt) || "live".equals(nt) || "chat".equals(nt)
                        || "message".equals(nt)) return true;
            }
        }
        return false;
    }
}
