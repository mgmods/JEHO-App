package com.Dramizo.Series.presentation.agency;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityAgencyManageBinding;
import com.Dramizo.Series.databinding.DialogAgencyConfirmBinding;
import com.Dramizo.Series.databinding.DialogAgencySheetFormBinding;
import com.Dramizo.Series.databinding.DialogAgencySheetListBinding;
import com.Dramizo.Series.databinding.ItemAgencyMenuRowBinding;
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
import com.Dramizo.Series.util.RoomOpenChooser;
import com.Dramizo.Series.util.StaffRoleHelper;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Agency manage hub — custom menu rows + custom bottom-sheet dialogs. */
public class AgencyManageActivity extends ThemedActivity {
    public static final String EXTRA_AGENCY_ID = "agency_id";

    private ActivityAgencyManageBinding binding;
    private AgencyViewModel vm;
    private String agencyId;
    private boolean isOwner;
    private boolean isPlatformSuper;
    private MiscDtos.AgencyMineDto myAgency;
    private String activationCode = "";
    private int knownTotalMembers = 0;
    private final List<MiscDtos.AgencyMemberDto> pendingMembers = new ArrayList<>();
    private String notificationStyle = "welcome";

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

        setupMenuRow(binding.menuSearchHosts, R.string.agency_manage_menu_search, v -> dialogSearchHosts());
        setupMenuRow(binding.menuAddHost, R.string.agency_manage_menu_add, v -> dialogAddHostChooser());
        setupMenuRow(binding.menuPending, R.string.agency_manage_menu_pending, v -> dialogPending());
        setupMenuRow(binding.menuCode, R.string.agency_manage_menu_code, v -> dialogActivationCode());
        setupMenuRow(binding.menuStyle, R.string.agency_manage_menu_style, v -> dialogNoticeStyle());
        setupMenuRow(binding.menuBranding, R.string.agency_manage_menu_branding, v -> dialogBranding());
        setupMenuRow(binding.menuMall, R.string.agency_manage_menu_mall, v -> openMall("host_badge"));

        binding.menuLive.setOnClickListener(v -> {
            if (myAgency != null && myAgency.isEligibleHost()) {
                RoomOpenChooser.showChooser(this, myAgency);
            } else if (isPlatformSuper || isOwner) {
                String name = myAgency != null && myAgency.agency != null
                        ? myAgency.agency.name
                        : (binding.tvAgencyHeroName != null
                        ? binding.tvAgencyHeroName.getText().toString() : null);
                AgencyRoomLauncher.open(this, agencyId, name);
            } else {
                String name = myAgency != null && myAgency.agency != null ? myAgency.agency.name : null;
                AgencyRoomLauncher.open(this, agencyId, name);
            }
        });
        binding.menuDelete.setOnClickListener(v -> confirmDeleteAgency());

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

        reload();
    }

    private void setupMenuRow(ItemAgencyMenuRowBinding row, int labelRes, View.OnClickListener click) {
        if (row == null) return;
        row.tvMenuLabel.setText(labelRes);
        row.getRoot().setOnClickListener(click);
    }

    private void setMenuLabel(ItemAgencyMenuRowBinding row, CharSequence text) {
        if (row != null && row.tvMenuLabel != null) row.tvMenuLabel.setText(text);
    }

    private void reload() {
        binding.progressMembers.setVisibility(View.VISIBLE);
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyDto> r = ApiCall.execute(c.getAgencyApi().get(agencyId));
            Result<MiscDtos.ListResult<MiscDtos.AgencyMemberDto>> pendingResult =
                    ApiCall.execute(c.getAgencyApi().joinRequests(agencyId));
            Result<MiscDtos.ListResult<MiscDtos.AgencyMemberDto>> countProbe =
                    ApiCall.execute(c.getAgencyApi().listMembers(agencyId, "", 1, 1, "active"));
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
                if (countProbe.success && countProbe.data != null) {
                    knownTotalMembers = countProbe.data.resolveTotal();
                } else if (r.data.memberCount > 0) {
                    knownTotalMembers = r.data.memberCount;
                }

                if (mineResult.success && mineResult.data != null) {
                    myAgency = mineResult.data;
                    String role = mineResult.data.role != null
                            ? mineResult.data.role.toLowerCase(Locale.US) : "";
                    isOwner = "owner".equals(role)
                            && mineResult.data.agency != null
                            && agencyId.equals(mineResult.data.agency.id);
                }
                try {
                    AuthDtos.UserDto me = ContainerProvider.from(this)
                            .getSessionManager().getUser();
                    isPlatformSuper = StaffRoleHelper.isSuper(me);
                } catch (Exception ignored) {
                    isPlatformSuper = false;
                }
                if (isPlatformSuper) isOwner = true;

                if (mineResult.success && mineResult.data != null && mineResult.data.agency != null
                        && agencyId.equals(mineResult.data.agency.id)) {
                    MiscDtos.AgencyDto ag = mineResult.data.agency;
                    binding.tvAgencyHeroName.setText(
                            ag.name != null && !ag.name.isEmpty() ? ag.name : "وكالة");
                    String role = mineResult.data.role != null
                            ? mineResult.data.role.toLowerCase(Locale.US) : "";
                    binding.tvAgencyHeroMeta.setText(getString(
                            R.string.agency_manage_meta_format,
                            Math.max(0, knownTotalMembers > 0
                                    ? knownTotalMembers : ag.memberCount),
                            isPlatformSuper ? "سوبر أدمن" : roleAr(role)));
                    if (ag.logoUrl != null && !ag.logoUrl.isEmpty()) {
                        Glide.with(this)
                                .load(AssetCatalog.absoluteUrl(ag.logoUrl))
                                .circleCrop()
                                .placeholder(R.drawable.icon_agency)
                                .into(binding.imgAgencyLogo);
                    }
                    activationCode = ag.activationCode != null ? ag.activationCode : "";
                    if (ag.notificationStyle != null) notificationStyle = ag.notificationStyle;
                    binding.tvCommission.setVisibility(View.VISIBLE);
                    binding.tvCommission.setText(String.format(Locale.US,
                            getString(R.string.agency_commission_value_format),
                            ag.commissionPercent));
                } else if (r.success && r.data != null) {
                    // Platform super managing an agency they do not own/belong to
                    MiscDtos.AgencyDto ag = r.data;
                    binding.tvAgencyHeroName.setText(
                            ag.name != null && !ag.name.isEmpty() ? ag.name : "وكالة");
                    binding.tvAgencyHeroMeta.setText(getString(
                            R.string.agency_manage_meta_format,
                            Math.max(0, knownTotalMembers > 0
                                    ? knownTotalMembers : ag.memberCount),
                            isPlatformSuper ? "سوبر أدمن" : "—"));
                    if (ag.logoUrl != null && !ag.logoUrl.isEmpty()) {
                        Glide.with(this)
                                .load(AssetCatalog.absoluteUrl(ag.logoUrl))
                                .circleCrop()
                                .placeholder(R.drawable.icon_agency)
                                .into(binding.imgAgencyLogo);
                    }
                    activationCode = ag.activationCode != null ? ag.activationCode : "";
                    binding.tvCommission.setVisibility(View.VISIBLE);
                    binding.tvCommission.setText(String.format(Locale.US,
                            getString(R.string.agency_commission_value_format),
                            ag.commissionPercent));
                }

                pendingMembers.clear();
                if (pendingResult.success && pendingResult.data != null
                        && pendingResult.data.items != null) {
                    for (MiscDtos.AgencyMemberDto m : pendingResult.data.items) {
                        if (m == null || m.userId == null || m.userId.isEmpty()) continue;
                        if (!m.isPending()) m.status = "pending";
                        pendingMembers.add(m);
                    }
                }
                setMenuLabel(binding.menuPending, pendingMembers.isEmpty()
                        ? getString(R.string.agency_manage_menu_pending)
                        : getString(R.string.agency_manage_menu_pending)
                        + " (" + pendingMembers.size() + ")");

                if (binding.menuBranding != null) {
                    binding.menuBranding.getRoot().setVisibility(isOwner ? View.VISIBLE : View.GONE);
                }
                binding.menuDelete.setVisibility(isOwner ? View.VISIBLE : View.GONE);
                boolean canOpen = isPlatformSuper
                        || (myAgency != null && myAgency.isEligibleHost()
                        && myAgency.agency != null && agencyId.equals(myAgency.agency.id));
                binding.menuLive.setVisibility(canOpen ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ─── Custom bottom sheets ───────────────────────────────────────────

    @NonNull
    private BottomSheetDialog openSheet(@NonNull View content) {
        BottomSheetDialog sheet = AuraDialogHelper.bottomSheet(this);
        AuraDialogHelper.applyContent(content);
        sheet.setContentView(content);
        sheet.show();
        return sheet;
    }

    private void dialogSearchHosts() {
        DialogAgencySheetFormBinding form = DialogAgencySheetFormBinding.inflate(getLayoutInflater());
        form.tvSheetTitle.setText(R.string.agency_manage_menu_search);
        form.tvSheetMessage.setVisibility(View.VISIBLE);
        form.tvSheetMessage.setText(R.string.agency_members_search_min);
        form.etSheetInput.setHint(R.string.agency_members_search_min);
        form.btnSheetOk.setText(R.string.agency_search_members);
        BottomSheetDialog sheet = openSheet(form.getRoot());
        EdgeToEdgeHelper.keepAboveImeOnFocus(form.etSheetInput);
        form.btnSheetCancel.setOnClickListener(v -> sheet.dismiss());
        form.btnSheetOk.setOnClickListener(v -> {
            String q = textOf(form.etSheetInput);
            if (q.isEmpty()) {
                Toast.makeText(this, R.string.agency_members_search_min, Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();
            runSearchAndShow(q);
        });
    }

    private void runSearchAndShow(String q) {
        binding.progressMembers.setVisibility(View.VISIBLE);
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<MiscDtos.AgencyMemberDto>> r =
                    ApiCall.execute(c.getAgencyApi().listMembers(agencyId, q, 1, 20, "active"));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                binding.progressMembers.setVisibility(View.GONE);
                List<MiscDtos.AgencyMemberDto> hits = new ArrayList<>();
                if (r.success && r.data != null && r.data.items != null) hits.addAll(r.data.items);
                if (hits.isEmpty()) {
                    Toast.makeText(this, R.string.agency_no_search_hits, Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayList<String> labels = new ArrayList<>();
                ArrayList<Runnable> actions = new ArrayList<>();
                for (MiscDtos.AgencyMemberDto m : hits) {
                    String name = m.user != null
                            ? (m.user.displayName != null ? m.user.displayName : m.user.username)
                            : m.userId;
                    String pid = m.user != null ? m.user.displayPublicId() : "";
                    labels.add((name != null ? name : "—")
                            + (pid.isEmpty() ? "" : (" · ID " + pid))
                            + " · " + roleAr(m.role));
                    actions.add(() -> showMemberActions(
                            m, name, pid, m.isActive == null || !m.isActive));
                }
                showCustomList(getString(R.string.agency_manage_menu_search), labels, actions);
            });
        });
    }

    private void dialogAddHostChooser() {
        openAgencyChatInvite();
    }

    private void openAgencyChatInvite() {
        // Always re-read the secret code so invite cards are never sent incomplete.
        String readyCode = activationCode != null ? activationCode.trim() : "";
        String nameHint = myAgency != null && myAgency.agency != null ? myAgency.agency.name : null;
        String logoHint = myAgency != null && myAgency.agency != null ? myAgency.agency.logoUrl : null;
        if (readyCode.length() >= 4) {
            com.Dramizo.Series.presentation.voiceroom.ShareRoomBottomSheet.showAgencyInvite(
                    getSupportFragmentManager(),
                    agencyId,
                    nameHint,
                    logoHint,
                    readyCode);
            return;
        }
        Toast.makeText(this, R.string.loading, Toast.LENGTH_SHORT).show();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyMineDto> mineResult = ApiCall.execute(c.getAgencyApi().mine());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String code = "";
                String name = nameHint;
                String logo = logoHint;
                if (mineResult.success && mineResult.data != null
                        && mineResult.data.agency != null
                        && agencyId != null
                        && agencyId.equals(mineResult.data.agency.id)) {
                    code = mineResult.data.agency.activationCode != null
                            ? mineResult.data.agency.activationCode.trim() : "";
                    activationCode = code;
                    if (mineResult.data.agency.name != null) name = mineResult.data.agency.name;
                    if (mineResult.data.agency.logoUrl != null) logo = mineResult.data.agency.logoUrl;
                }
                if (code.length() < 4) {
                    Toast.makeText(this, R.string.agency_invite_missing_code, Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                com.Dramizo.Series.presentation.voiceroom.ShareRoomBottomSheet.showAgencyInvite(
                        getSupportFragmentManager(),
                        agencyId,
                        name,
                        logo,
                        code);
            });
        });
    }

    private void dialogAddHost() {
        DialogAgencySheetFormBinding form = DialogAgencySheetFormBinding.inflate(getLayoutInflater());
        form.tvSheetTitle.setText(R.string.agency_manage_menu_add);
        form.tvSheetMessage.setVisibility(View.VISIBLE);
        form.tvSheetMessage.setText("أدخل آي دي المضيف من الملف الشخصي");
        form.etSheetInput.setHint("ID المضيف");
        form.btnSheetOk.setText(R.string.agency_manage_menu_add);

        final String[] roleKeys = isOwner
                ? new String[]{"host", "manager"}
                : new String[]{"host"};
        final String[] roleLabels = isOwner
                ? new String[]{"مضيف (أرباح فقط)", "أدمن وكالة (بث)"}
                : new String[]{"مضيف (أرباح فقط)"};
        final int[] roleIdx = {0};
        form.boxRoleChoices.setVisibility(View.VISIBLE);
        form.boxRoleChoices.removeAllViews();
        TextView[] chips = new TextView[roleLabels.length];
        for (int i = 0; i < roleLabels.length; i++) {
            final int idx = i;
            TextView chip = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_agency_sheet_choice, form.boxRoleChoices, false);
            chip.setText(roleLabels[i]);
            chips[i] = chip;
            chip.setOnClickListener(v -> {
                roleIdx[0] = idx;
                styleRoleChips(chips, idx);
            });
            form.boxRoleChoices.addView(chip);
        }
        styleRoleChips(chips, 0);

        BottomSheetDialog sheet = openSheet(form.getRoot());
        EdgeToEdgeHelper.keepAboveImeOnFocus(form.etSheetInput);
        form.btnSheetCancel.setOnClickListener(v -> sheet.dismiss());
        form.btnSheetOk.setOnClickListener(v -> {
            String uid = textOf(form.etSheetInput);
            if (uid.isEmpty()) {
                Toast.makeText(this, "أدخل ID المضيف", Toast.LENGTH_SHORT).show();
                return;
            }
            int i = Math.max(0, Math.min(roleIdx[0], roleKeys.length - 1));
            sheet.dismiss();
            vm.addMember(agencyId, uid, roleKeys[i]);
            binding.getRoot().postDelayed(this::reload, 700);
        });
    }

    private void styleRoleChips(TextView[] chips, int selected) {
        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) continue;
            boolean on = i == selected;
            chips[i].setBackgroundResource(on
                    ? R.drawable.bg_agency_menu_row_gold
                    : R.drawable.bg_agency_menu_row);
            chips[i].setTextColor(on ? 0xFF9A6700 : getColor(R.color.text_primary));
        }
    }

    private void dialogPending() {
        if (pendingMembers.isEmpty()) {
            Toast.makeText(this, "لا توجد طلبات انضمام", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        for (MiscDtos.AgencyMemberDto m : pendingMembers) {
            String name = m.user != null
                    ? (m.user.displayName != null ? m.user.displayName : m.user.username)
                    : m.userId;
            String pid = m.user != null ? m.user.displayPublicId() : "";
            String label = (name != null ? name : "—") + (pid.isEmpty() ? "" : (" · ID " + pid));
            labels.add(label);
            actions.add(() -> showPendingReview(m, label));
        }
        showCustomList(getString(R.string.agency_manage_menu_pending), labels, actions);
    }

    private void showPendingReview(MiscDtos.AgencyMemberDto m, String label) {
        DialogAgencyConfirmBinding form = DialogAgencyConfirmBinding.inflate(getLayoutInflater());
        form.tvConfirmTitle.setText(R.string.agency_join_request_badge);
        form.tvConfirmTitle.setGravity(android.view.Gravity.CENTER);
        form.tvConfirmMessage.setText(label);
        form.tvConfirmMessage.setGravity(android.view.Gravity.CENTER);
        form.btnConfirmYes.setText(R.string.agency_approve_join);
        form.btnConfirmNo.setText(R.string.agency_reject_join);
        BottomSheetDialog sheet = openSheet(form.getRoot());
        form.btnConfirmYes.setOnClickListener(v -> {
            sheet.dismiss();
            vm.approveJoin(agencyId, m.userId);
            binding.getRoot().postDelayed(this::reload, 700);
        });
        form.btnConfirmNo.setOnClickListener(v -> {
            sheet.dismiss();
            vm.rejectJoin(agencyId, m.userId);
            binding.getRoot().postDelayed(this::reload, 700);
        });
    }

    private void dialogActivationCode() {
        DialogAgencyConfirmBinding form = DialogAgencyConfirmBinding.inflate(getLayoutInflater());
        form.tvConfirmTitle.setText(R.string.agency_manage_menu_code);
        form.tvConfirmTitle.setGravity(android.view.Gravity.CENTER);
        String code = activationCode == null || activationCode.isEmpty() ? "—" : activationCode;
        form.tvConfirmMessage.setText(code);
        form.tvConfirmMessage.setGravity(android.view.Gravity.CENTER);
        form.tvConfirmMessage.setTextSize(22);
        form.tvConfirmMessage.setTextColor(getColor(R.color.text_primary));
        form.btnConfirmYes.setText(R.string.copy);
        form.btnConfirmNo.setText(R.string.cancel);
        BottomSheetDialog sheet = openSheet(form.getRoot());
        form.btnConfirmYes.setOnClickListener(v -> {
            if (activationCode == null || activationCode.isEmpty()) {
                Toast.makeText(this, "لا يوجد كود بعد", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("activationCode", activationCode));
                Toast.makeText(this, R.string.agency_copied, Toast.LENGTH_SHORT).show();
            }
            sheet.dismiss();
        });
        form.btnConfirmNo.setOnClickListener(v -> sheet.dismiss());
    }

    private void dialogNoticeStyle() {
        int selected = 0;
        for (int i = 0; i < styleKeys.length; i++) {
            if (styleKeys[i].equalsIgnoreCase(notificationStyle)) {
                selected = i;
                break;
            }
        }
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        for (int i = 0; i < styleLabels.length; i++) {
            final int idx = i;
            String mark = (idx == selected) ? "✓ " : "";
            labels.add(mark + styleLabels[i]);
            actions.add(() -> {
                notificationStyle = styleKeys[idx];
                vm.updateNotificationStyle(agencyId, styleKeys[idx]);
            });
        }
        showCustomList(getString(R.string.agency_manage_menu_style), labels, actions);
    }

    private void dialogBranding() {
        if (!isOwner) return;
        String curName = myAgency != null && myAgency.agency != null ? myAgency.agency.name : "";
        String curWelcome = myAgency != null && myAgency.agency != null
                ? (myAgency.agency.description != null ? myAgency.agency.description : "") : "";
        DialogAgencySheetFormBinding form = DialogAgencySheetFormBinding.inflate(getLayoutInflater());
        form.tvSheetTitle.setText(R.string.agency_manage_menu_branding);
        form.etSheetInput.setHint("اسم الوكالة");
        form.etSheetInput.setText(curName != null ? curName : "");
        form.etSheetInput2.setVisibility(View.VISIBLE);
        form.etSheetInput2.setHint("نص الترحيب");
        form.etSheetInput2.setText(curWelcome);
        form.btnSheetOk.setText(R.string.settings_saved);
        BottomSheetDialog sheet = openSheet(form.getRoot());
        EdgeToEdgeHelper.keepAboveImeOnFocus(form.etSheetInput);
        EdgeToEdgeHelper.keepAboveImeOnFocus(form.etSheetInput2);
        form.btnSheetCancel.setOnClickListener(v -> sheet.dismiss());
        form.btnSheetOk.setOnClickListener(v -> {
            String name = textOf(form.etSheetInput);
            String welcome = textOf(form.etSheetInput2);
            if (name.length() < 2) {
                Toast.makeText(this, "أدخل اسم وكالة صالحاً", Toast.LENGTH_SHORT).show();
                return;
            }
            sheet.dismiss();
            vm.updateAgencyBranding(agencyId, name, welcome);
            binding.getRoot().postDelayed(this::reload, 700);
        });
    }

    private void showCustomList(
            @NonNull CharSequence title,
            @NonNull List<String> labels,
            @NonNull List<Runnable> actions) {
        DialogAgencySheetListBinding form = DialogAgencySheetListBinding.inflate(getLayoutInflater());
        form.tvSheetTitle.setText(title);
        form.listSheetItems.removeAllViews();
        BottomSheetDialog sheet = openSheet(form.getRoot());
        form.btnSheetCancel.setOnClickListener(v -> sheet.dismiss());
        for (int i = 0; i < labels.size(); i++) {
            final int idx = i;
            TextView item = (TextView) LayoutInflater.from(this)
                    .inflate(R.layout.item_agency_sheet_choice, form.listSheetItems, false);
            item.setText(labels.get(i));
            item.setOnClickListener(v -> {
                sheet.dismiss();
                if (idx >= 0 && idx < actions.size()) actions.get(idx).run();
            });
            form.listSheetItems.addView(item);
        }
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

    private void showMemberActions(
            MiscDtos.AgencyMemberDto m,
            String name,
            String publicId,
            boolean suspended) {
        if (m == null || "owner".equalsIgnoreCase(m.role)) return;
        String idHint = publicId != null && !publicId.isEmpty() ? " (ID " + publicId + ")" : "";
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();

        if (suspended) {
            labels.add("إلغاء التعليق");
            actions.add(() -> {
                vm.unsuspendMember(agencyId, m.userId);
                binding.getRoot().postDelayed(this::reload, 700);
            });
        } else {
            if (isOwner && !"manager".equalsIgnoreCase(m.role)) {
                labels.add("رفع أدمن وكالة (بث)");
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
                labels.add("تعيين مضيف");
                actions.add(() -> {
                    vm.updateMemberRole(agencyId, m.userId, "host");
                    binding.getRoot().postDelayed(this::reload, 700);
                });
            }
            labels.add("تعليق المضيف");
            actions.add(() -> {
                vm.suspendMember(agencyId, m.userId);
                binding.getRoot().postDelayed(this::reload, 700);
            });
        }
        labels.add("طرد نهائي");
        actions.add(() -> showConfirmSheet(
                "طرد مضيف",
                "طرد " + name + idHint + "؟",
                "طرد",
                () -> {
                    vm.removeMember(agencyId, m.userId);
                    binding.getRoot().postDelayed(this::reload, 700);
                }));

        showCustomList(name + idHint, labels, actions);
    }

    private void showConfirmSheet(String title, String message, String yesLabel, Runnable onYes) {
        DialogAgencyConfirmBinding form = DialogAgencyConfirmBinding.inflate(getLayoutInflater());
        form.tvConfirmTitle.setText(title);
        form.tvConfirmTitle.setGravity(android.view.Gravity.CENTER);
        form.tvConfirmMessage.setText(message);
        form.tvConfirmMessage.setGravity(android.view.Gravity.CENTER);
        form.btnConfirmYes.setText(yesLabel);
        // Convert Material buttons look — styles already AuraLive custom.
        BottomSheetDialog sheet = openSheet(form.getRoot());
        form.btnConfirmYes.setOnClickListener(v -> {
            sheet.dismiss();
            onYes.run();
        });
        form.btnConfirmNo.setOnClickListener(v -> sheet.dismiss());
    }

    private static String textOf(@Nullable EditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private static String roleAr(String role) {
        if (role == null) return "مضيف";
        switch (role.toLowerCase(Locale.US)) {
            case "owner": return "مالك";
            case "manager": return "مدير";
            case "host": return "مضيف";
            default: return "مضيف";
        }
    }

    private void openMall(String type) {
        android.content.Intent i = new android.content.Intent(this, CosmeticsActivity.class);
        if (type != null) i.putExtra(CosmeticsViewModel.EXTRA_TYPE, type);
        startActivity(i);
    }
}
