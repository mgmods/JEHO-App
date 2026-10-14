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
            if (n != null && n.data != null
                    && "cp_relationship_request".equals(String.valueOf(n.data.get("type")))) {
                showCpRequestPrompt(n);
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

    /** Messages, follows, gifts, live/room opens — personal. System/VIP/wallet/agency — general. */
    private static boolean isPersonal(MiscDtos.NotificationDto n) {
        if (n == null || n.type == null) return false;
        String t = n.type.toLowerCase();
        if ("chat".equals(t) || "message".equals(t) || "follow".equals(t)
                || "gift".equals(t) || "live".equals(t) || "stream".equals(t)
                || "friend".equals(t) || "relation".equals(t) || "room".equals(t)) {
            return true;
        }
        if (n.data != null) {
            Object nested = n.data.get("type");
            if (nested != null) {
                String nt = String.valueOf(nested).toLowerCase();
                if ("room".equals(nt) || "live".equals(nt) || "chat".equals(nt)
                        || "message".equals(nt) || "cp_relationship_request".equals(nt)) return true;
            }
        }
        return false;
    }
}
