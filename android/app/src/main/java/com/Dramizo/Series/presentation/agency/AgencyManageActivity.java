package com.Dramizo.Series.presentation.agency;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityAgencyManageBinding;
import com.Dramizo.Series.databinding.DialogAgencyConfirmBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.cosmetics.CosmeticsActivity;
import com.Dramizo.Series.presentation.cosmetics.CosmeticsViewModel;
import com.Dramizo.Series.util.AgencyRoomLauncher;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;

/** Full-screen agency management (members, code, notice) — not a bottom sheet. */
public class AgencyManageActivity extends ThemedActivity {
    public static final String EXTRA_AGENCY_ID = "agency_id";

    private ActivityAgencyManageBinding binding;
    private AgencyViewModel vm;
    private String agencyId;
    private boolean isOwner;
    private MiscDtos.AgencyMineDto myAgency;
    private String[] addRoleKeys = new String[]{"host", "member"};
    private final java.util.List<MiscDtos.AgencyMemberDto> allMembers = new java.util.ArrayList<>();
    private String memberQuery = "";

    private final String[] styleKeys = {"welcome", "elite", "family", "spark"};
    private final String[] styleLabels = {
            "ترحيب — أهلاً بك في العائلة",
            "نخبة — انضمام للنخبة",
            "أسرة — أسرة الوكالة",
            "إشراقة — إشراقة جديدة"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAgencyManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        agencyId = getIntent().getStringExtra(EXTRA_AGENCY_ID);
        if (agencyId == null || agencyId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.btnBack.setOnClickListener(v -> navigateUp());
        vm = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(AgencyViewModel.class);

        android.widget.ArrayAdapter<String> styleAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, styleLabels);
        binding.spinnerNotificationStyle.setAdapter(styleAdapter);

        binding.btnCopyActivationCode.setOnClickListener(v -> {
            CharSequence code = binding.tvActivationCode.getText();
            if (code == null || code.length() == 0 || "—".contentEquals(code)) {
                Toast.makeText(this, "لا يوجد كود بعد — اطلبه من لوحة الإدارة", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("activationCode", code));
                Toast.makeText(this, R.string.agency_copied, Toast.LENGTH_SHORT).show();
            }
        });

        binding.spinnerNotificationStyle.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    private boolean ready;
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                               int position, long id) {
                        if (!ready) {
                            ready = true;
                            return;
                        }
                        if (position < 0 || position >= styleKeys.length) return;
                        vm.updateNotificationStyle(agencyId, styleKeys[position]);
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });

        binding.btnOpenAgencyStream.setOnClickListener(v -> {
            String name = null;
            if (myAgency != null && myAgency.agency != null && myAgency.agency.name != null) {
                name = myAgency.agency.name;
            }
            AgencyRoomLauncher.open(this, agencyId, name);
        });

        binding.btnDeleteAgencyManage.setOnClickListener(v -> confirmDeleteAgency());

        binding.btnAddMember.setOnClickListener(v -> {
            String uid = binding.etUserId.getText() != null
                    ? binding.etUserId.getText().toString().trim() : "";
            if (uid.isEmpty()) {
                Toast.makeText(this, "أدخل ID المستخدم من الملف الشخصي", Toast.LENGTH_SHORT).show();
                return;
            }
            int roleIdx = binding.spinnerAddRole.getSelectedItemPosition();
            if (roleIdx < 0 || roleIdx >= addRoleKeys.length) roleIdx = 0;
            vm.addMember(agencyId, uid, addRoleKeys[roleIdx]);
            binding.etUserId.setText("");
            binding.getRoot().postDelayed(this::reload, 700);
        });

        // Keep focused EditTexts above the soft keyboard inside NestedScrollView.
        EdgeToEdgeHelper.keepAboveImeOnFocus(binding.etUserId);
        if (binding.etMemberSearch != null) {
            EdgeToEdgeHelper.keepAboveImeOnFocus(binding.etMemberSearch);
        }
        if (binding.etAgencyName != null) {
            EdgeToEdgeHelper.keepAboveImeOnFocus(binding.etAgencyName);
        }
        if (binding.etAgencyWelcome != null) {
            EdgeToEdgeHelper.keepAboveImeOnFocus(binding.etAgencyWelcome);
        }

        if (binding.btnSaveAgencyBranding != null) {
            binding.btnSaveAgencyBranding.setOnClickListener(v -> {
                String name = binding.etAgencyName.getText() != null
                        ? binding.etAgencyName.getText().toString().trim() : "";
                String welcome = binding.etAgencyWelcome.getText() != null
                        ? binding.etAgencyWelcome.getText().toString().trim() : "";
                if (name.length() < 2) {
                    Toast.makeText(this, "أدخل اسم وكالة صالحاً", Toast.LENGTH_SHORT).show();
                    return;
                }
                vm.updateAgencyBranding(agencyId, name, welcome);
            });
        }
        if (binding.btnAgencyMallFrames != null) {
            binding.btnAgencyMallFrames.setOnClickListener(v -> openMall("host_badge"));
        }
        if (binding.btnAgencyMallBadges != null) {
            binding.btnAgencyMallBadges.setOnClickListener(v -> openMall("level_badge"));
        }

        vm.getMessage().observe(this, m -> {
            if ("agency_deleted".equals(m)) {
                Toast.makeText(this, R.string.agency_deleted, Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            } else if (m != null) {
                Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
            }
        });
        vm.getError().observe(this, e -> {
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_LONG).show();
        });

        if (binding.etMemberSearch != null) {
            binding.etMemberSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(android.text.Editable s) {
                    memberQuery = s != null ? s.toString().trim() : "";
                    renderMembers();
                }
            });
        }

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fresh data when returning from other screens; skip first frame after onCreate.
    }

    private void reload() {
        binding.progressMembers.setVisibility(View.VISIBLE);
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyDto> r = ApiCall.execute(c.getAgencyApi().get(agencyId));
            Result<MiscDtos.ListResult<MiscDtos.AgencyMemberDto>> pendingResult =
                    ApiCall.execute(c.getAgencyApi().joinRequests(agencyId));
            Result<MiscDtos.AgencyMineDto> mineResult = c.getAgencyRepository().mine();
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                binding.progressMembers.setVisibility(View.GONE);
                if (!r.success || r.data == null) {
                    Toast.makeText(this,
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (mineResult.success && mineResult.data != null) {
                    myAgency = mineResult.data;
                    String role = mineResult.data.role != null
                            ? mineResult.data.role.toLowerCase(Locale.US) : "";
                    isOwner = "owner".equals(role)
                            && mineResult.data.agency != null
                            && agencyId.equals(mineResult.data.agency.id);
                    if (mineResult.data.agency != null) {
                        MiscDtos.AgencyDto ag = mineResult.data.agency;
                        if (binding.tvAgencyHeroName != null) {
                            binding.tvAgencyHeroName.setText(
                                    ag.name != null && !ag.name.isEmpty() ? ag.name : "وكالة");
                        }
                        if (binding.tvAgencyHeroMeta != null) {
                            binding.tvAgencyHeroMeta.setText(
                                    "أعضاء " + Math.max(0, ag.memberCount)
                                            + " · " + roleAr(role));
                        }
                        if (binding.imgAgencyLogo != null) {
                            if (ag.logoUrl != null && !ag.logoUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(AssetCatalog.absoluteUrl(ag.logoUrl))
                                        .circleCrop()
                                        .placeholder(R.drawable.icon_agency)
                                        .into(binding.imgAgencyLogo);
                            } else {
                                binding.imgAgencyLogo.setImageResource(R.drawable.icon_agency);
                            }
                        }
                        if (binding.boxAgencyBranding != null) {
                            binding.boxAgencyBranding.setVisibility(isOwner ? View.VISIBLE : View.GONE);
                            if (isOwner) {
                                if (binding.etAgencyName != null) {
                                    binding.etAgencyName.setText(ag.name != null ? ag.name : "");
                                }
                                if (binding.etAgencyWelcome != null) {
                                    String welcome = ag.description != null ? ag.description : "";
                                    binding.etAgencyWelcome.setText(welcome);
                                }
                            }
                        }
                        String code = mineResult.data.agency.activationCode;
                        if (code != null && !code.isEmpty()) {
                            binding.tvActivationCode.setText(code);
                        }
                        String style = mineResult.data.agency.notificationStyle;
                        if (style != null) {
                            for (int i = 0; i < styleKeys.length; i++) {
                                if (styleKeys[i].equalsIgnoreCase(style)) {
                                    binding.spinnerNotificationStyle.setSelection(i);
                                    break;
                                }
                            }
                        }
                        // Read-only commission — admin sets it from dashboard.
                        binding.boxCommission.setVisibility(View.VISIBLE);
                        binding.tvCommission.setText(String.format(Locale.US,
                                getString(R.string.agency_commission_value_format),
                                mineResult.data.agency.commissionPercent));
                    }
                }
                binding.btnDeleteAgencyManage.setVisibility(isOwner ? View.VISIBLE : View.GONE);

                addRoleKeys = isOwner
                        ? new String[]{"host", "manager", "member"}
                        : new String[]{"host", "member"};
                String[] addRoleLabels = isOwner
                        ? new String[]{"مضيف (يفتح البث)", "أدمن وكالة", "عضو"}
                        : new String[]{"مضيف (يفتح البث)", "عضو"};
                binding.spinnerAddRole.setAdapter(new android.widget.ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_dropdown_item, addRoleLabels));

                java.util.ArrayList<MiscDtos.AgencyMemberDto> merged = new java.util.ArrayList<>();
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                if (pendingResult.success && pendingResult.data != null
                        && pendingResult.data.items != null) {
                    for (MiscDtos.AgencyMemberDto m : pendingResult.data.items) {
                        if (m == null || m.userId == null || m.userId.isEmpty()) continue;
                        if (!m.isPending()) m.status = "pending";
                        merged.add(m);
                        seen.add(m.userId);
                    }
                }
                if (r.data.members != null) {
                    for (MiscDtos.AgencyMemberDto m : r.data.members) {
                        if (m == null || m.userId == null || m.userId.isEmpty()) continue;
                        if (seen.contains(m.userId)) continue;
                        merged.add(m);
                        seen.add(m.userId);
                    }
                }
                r.data.members = merged;
                allMembers.clear();
                allMembers.addAll(merged);

                binding.tilUserId.setVisibility(View.VISIBLE);
                binding.btnAddMember.setVisibility(View.VISIBLE);
                binding.tvAddRoleLabel.setVisibility(View.VISIBLE);
                binding.spinnerAddRole.setVisibility(View.VISIBLE);
                boolean canOpen = myAgency != null && myAgency.isEligibleHost()
                        && myAgency.agency != null && agencyId.equals(myAgency.agency.id);
                binding.btnOpenAgencyStream.setVisibility(canOpen ? View.VISIBLE : View.GONE);
                renderMembers();
            });
        });
    }

    private void confirmDeleteAgency() {
        if (!isOwner || agencyId == null) return;
        String name = myAgency != null && myAgency.agency != null && myAgency.agency.name != null
                ? myAgency.agency.name : "";
        showConfirmSheet(
                getString(R.string.agency_delete_confirm_title),
                getString(R.string.agency_delete_confirm_message)
                        + (name.isEmpty() ? "" : "\n\n«" + name + "»"),
                getString(R.string.agency_delete),
                () -> vm.deleteAgency(agencyId));
    }

    private void renderMembers() {
        java.util.ArrayList<MiscDtos.AgencyMemberDto> filtered = new java.util.ArrayList<>();
        String q = memberQuery == null ? "" : memberQuery.toLowerCase(Locale.US);
        for (MiscDtos.AgencyMemberDto m : allMembers) {
            if (m == null || m.userId == null) continue;
            if (m.status != null && "rejected".equalsIgnoreCase(m.status)) continue;
            if (!q.isEmpty()) {
                String name = m.user != null
                        ? (m.user.displayName != null ? m.user.displayName : m.user.username)
                        : "";
                String publicId = m.user != null ? m.user.displayPublicId() : "";
                String username = m.user != null ? m.user.username : "";
                String hay = ((name != null ? name : "") + " "
                        + (publicId != null ? publicId : "") + " "
                        + (username != null ? username : "") + " "
                        + m.userId).toLowerCase(Locale.US);
                if (!hay.contains(q)) continue;
            }
            filtered.add(m);
        }
        bindMembersList(filtered);
    }

    private void bindMembersList(java.util.List<MiscDtos.AgencyMemberDto> members) {
        LinearLayout box = binding.boxMembers;
        box.removeAllViews();
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        int count = 0;
        int textPrimary = getColor(R.color.text_primary);
        int textSecondary = getColor(R.color.text_secondary);
        if (members != null) {
            for (MiscDtos.AgencyMemberDto m : members) {
                if (m == null || m.userId == null) continue;
                count++;
                String name = m.user != null
                        ? (m.user.displayName != null ? m.user.displayName : m.user.username)
                        : m.userId;
                String publicId = m.user != null ? m.user.displayPublicId() : "";
                String role = roleAr(m.role);
                boolean pending = m.isPending();
                boolean suspended = !pending && (m.isActive == null || !m.isActive);

                LinearLayout rowWrap = new LinearLayout(this);
                rowWrap.setOrientation(LinearLayout.VERTICAL);
                rowWrap.setPadding(pad, pad, pad, pad);
                rowWrap.setBackgroundResource(R.drawable.bg_wallet_section);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = pad / 2;
                rowWrap.setLayoutParams(lp);
                if (pending) rowWrap.setBackgroundColor(0x22FFB74D);
                else if (suspended) rowWrap.setBackgroundColor(0x22FF5252);

                TextView title = new TextView(this);
                title.setText(name != null ? name : "عضو");
                title.setTextColor(textPrimary);
                title.setTextSize(15);
                title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
                rowWrap.addView(title);

                TextView meta = new TextView(this);
                String statusLabel = pending ? "طلب انضمام" : (suspended ? "معلّق · " + role : role);
                String line = statusLabel;
                if (publicId != null && !publicId.isEmpty()) {
                    line = "ID " + publicId + " · " + statusLabel;
                }
                meta.setText(line);
                meta.setTextColor(textSecondary);
                meta.setTextSize(12);
                meta.setPadding(0, pad / 4, 0, 0);
                rowWrap.addView(meta);

                if (pending) {
                    LinearLayout actions = new LinearLayout(this);
                    actions.setOrientation(LinearLayout.HORIZONTAL);
                    actions.setPadding(0, pad / 2, 0, 0);
                    TextView approve = new TextView(this);
                    approve.setText("قبول");
                    approve.setTextColor(getColor(R.color.aurora_mint));
                    approve.setTextSize(14);
                    approve.setPadding(pad, pad / 2, pad * 2, pad / 2);
                    approve.setOnClickListener(v -> {
                        vm.approveJoin(agencyId, m.userId);
                        binding.getRoot().postDelayed(this::reload, 700);
                    });
                    TextView reject = new TextView(this);
                    reject.setText("رفض");
                    reject.setTextColor(0xFFE57373);
                    reject.setTextSize(14);
                    reject.setPadding(pad, pad / 2, pad, pad / 2);
                    reject.setOnClickListener(v -> {
                        vm.rejectJoin(agencyId, m.userId);
                        binding.getRoot().postDelayed(this::reload, 700);
                    });
                    actions.addView(approve);
                    actions.addView(reject);
                    rowWrap.addView(actions);
                } else {
                    boolean isOwnerMember = "owner".equalsIgnoreCase(m.role);
                    if (!isOwnerMember) {
                        rowWrap.setOnClickListener(v -> showMemberActions(
                                m, name, publicId, suspended));
                    }
                }
                box.addView(rowWrap);
            }
        }
        binding.tvEmptyMembers.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        binding.tvEmptyMembers.setTextColor(textSecondary);
        if (count == 0 && memberQuery != null && !memberQuery.isEmpty()) {
            binding.tvEmptyMembers.setText("لا نتائج للبحث");
        } else {
            binding.tvEmptyMembers.setText(R.string.agency_no_members);
        }
    }

    private void showMemberActions(
            MiscDtos.AgencyMemberDto m,
            String name,
            String publicId,
            boolean suspended) {
        String idHint = publicId != null && !publicId.isEmpty() ? " (ID " + publicId + ")" : "";
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        java.util.ArrayList<Runnable> actions = new java.util.ArrayList<>();

        if (suspended) {
            labels.add("إلغاء التعليق");
            actions.add(() -> {
                vm.unsuspendMember(agencyId, m.userId);
                binding.getRoot().postDelayed(this::reload, 700);
            });
        } else {
            if (isOwner && !"manager".equalsIgnoreCase(m.role)) {
                labels.add("رفع أدمن وكالة");
                actions.add(() -> {
                    vm.updateMemberRole(agencyId, m.userId, "manager");
                    binding.getRoot().postDelayed(this::reload, 700);
                });
            }
            if (isOwner && "manager".equalsIgnoreCase(m.role)) {
                labels.add("إزالة أدمن → مضيف");
                actions.add(() -> {
                    vm.updateMemberRole(agencyId, m.userId, "host");
                    binding.getRoot().postDelayed(this::reload, 700);
                });
            }
            if (!"host".equalsIgnoreCase(m.role) && !"manager".equalsIgnoreCase(m.role)) {
                labels.add("تعيين مضيف (يفتح البث)");
                actions.add(() -> {
                    vm.updateMemberRole(agencyId, m.userId, "host");
                    binding.getRoot().postDelayed(this::reload, 700);
                });
            }
            if ("host".equalsIgnoreCase(m.role)
                    || (isOwner && "manager".equalsIgnoreCase(m.role))) {
                labels.add("تخفيض إلى عضو");
                actions.add(() -> {
                    vm.updateMemberRole(agencyId, m.userId, "member");
                    binding.getRoot().postDelayed(this::reload, 700);
                });
            }
            labels.add("تعليق العضو");
            actions.add(() -> showConfirmSheet(
                    "تعليق عضو",
                    "تعليق " + name + idHint + "؟ لن يستطيع فتح بث الوكالة حتى يُلغى التعليق.",
                    "تعليق",
                    () -> {
                        vm.suspendMember(agencyId, m.userId);
                        binding.getRoot().postDelayed(this::reload, 700);
                    }));
        }
        labels.add("طرد نهائياً");
        actions.add(() -> showConfirmSheet(
                "طرد عضو",
                "طرد " + name + idHint + "؟",
                "طرد",
                () -> {
                    vm.removeMember(agencyId, m.userId);
                    binding.getRoot().postDelayed(this::reload, 700);
                }));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(name + idHint)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which >= 0 && which < actions.size()) actions.get(which).run();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void showConfirmSheet(String title, String message, String yesLabel, Runnable onYes) {
        BottomSheetDialog sheet = AuraDialogHelper.bottomSheet(this);
        DialogAgencyConfirmBinding form = DialogAgencyConfirmBinding.inflate(getLayoutInflater());
        form.tvConfirmTitle.setText(title);
        form.tvConfirmMessage.setText(message);
        form.btnConfirmYes.setText(yesLabel);
        form.btnConfirmYes.setOnClickListener(v -> {
            sheet.dismiss();
            onYes.run();
        });
        form.btnConfirmNo.setOnClickListener(v -> sheet.dismiss());
        AuraDialogHelper.applyContent(form.getRoot());
        sheet.setContentView(form.getRoot());
        sheet.show();
    }

    private static String roleAr(String role) {
        if (role == null) return "عضو";
        switch (role.toLowerCase(Locale.US)) {
            case "owner": return "مالك";
            case "manager": return "مدير";
            case "host": return "مضيف";
            default: return "عضو";
        }
    }

    private void openMall(String type) {
        android.content.Intent i = new android.content.Intent(this, CosmeticsActivity.class);
        if (type != null) i.putExtra(CosmeticsViewModel.EXTRA_TYPE, type);
        startActivity(i);
    }
}
