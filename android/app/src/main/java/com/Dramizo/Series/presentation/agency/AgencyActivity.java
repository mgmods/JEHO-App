package com.Dramizo.Series.presentation.agency;

import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityAgencyBinding;
import com.Dramizo.Series.databinding.DialogAgencyConfirmBinding;
import com.Dramizo.Series.databinding.DialogCreateAgencyBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.AgencyRoomLauncher;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.CountryCatalog;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Collections;
import java.util.Locale;

public class AgencyActivity extends ThemedActivity {
    private AgencyViewModel vm;
    private AgencyAdapter adapter;
    private String myAgencyId;
    private MiscDtos.AgencyMineDto myAgency;
    private boolean canLeave;
    private boolean canManage;
    private boolean canDeleteAgency;
    private ActivityAgencyBinding binding;
    private BottomSheetDialog applicationSheet;
    private com.google.android.material.button.MaterialButton applicationSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAgencyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnRefresh.setOnClickListener(v -> vm.load());
        vm = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(AgencyViewModel.class);
        adapter = new AgencyAdapter(new AgencyAdapter.Listener() {
            @Override public void onJoin(String agencyId) {
                // Join is by private activation code only — focus the code field.
                if (binding.cardJoinByCode.getVisibility() == View.VISIBLE) {
                    binding.etActivationCode.requestFocus();
                    Toast.makeText(AgencyActivity.this,
                            R.string.agency_join_by_code_hint, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onLeave(String agencyId) {
                boolean pending = isPendingMembership(myAgency);
                showConfirmSheet(
                        pending
                                ? getString(R.string.agency_cancel_join_title)
                                : "مغادرة الوكالة",
                        pending
                                ? getString(R.string.agency_cancel_join_message)
                                : "هل تريد مغادرة هذه الوكالة؟",
                        pending
                                ? getString(R.string.agency_cancel_join)
                                : "مغادرة",
                        () -> vm.leave(agencyId));
            }
            @Override public void onManage(String agencyId) { openManage(agencyId); }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        binding.btnJoinByCode.setOnClickListener(v -> {
            String code = binding.etActivationCode.getText() != null
                    ? binding.etActivationCode.getText().toString().trim() : "";
            if (code.length() < 4) {
                Toast.makeText(this, "أدخل كود التفعيل الصحيح", Toast.LENGTH_SHORT).show();
                return;
            }
            vm.joinByCode(code);
        });
        binding.btnCopyCode.setOnClickListener(v -> {
            CharSequence code = binding.tvActivationCode.getText();
            if (code == null || code.length() == 0) return;
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("agency_code", code));
                Toast.makeText(this, R.string.agency_copied, Toast.LENGTH_SHORT).show();
            }
        });
        binding.btnManageAgency.setOnClickListener(v -> {
            if (myAgencyId != null) openManage(myAgencyId);
        });
        if (binding.btnWithdrawAgencyEarnings != null) {
            binding.btnWithdrawAgencyEarnings.setOnClickListener(v -> {
                Intent i = new Intent(this,
                        com.Dramizo.Series.presentation.wallet.BagActivity.class);
                i.putExtra(com.Dramizo.Series.presentation.wallet.BagActivity.EXTRA_TAB, 1);
                i.putExtra(com.Dramizo.Series.presentation.wallet.BagActivity.EXTRA_DIAMOND_ACTION,
                        "withdraw");
                startActivity(i);
            });
        }
        if (binding.btnDistributeAgencyEarnings != null) {
            binding.btnDistributeAgencyEarnings.setOnClickListener(v -> showDistributeDialog());
        }
        vm.getAgencies().observe(this, list -> {
            // Browse-all list retired — my agency card is the only surface.
            adapter.submit(Collections.emptyList(), myAgencyId, canLeave, canManage);
            binding.tvEmpty.setVisibility(View.GONE);
            binding.recycler.setVisibility(View.GONE);
        });
        vm.getMine().observe(this, m -> {
            if (m != null && m.application != null && m.agency == null
                    && "approved".equalsIgnoreCase(m.application.status)) {
                // An approved application is only valid while its created agency still exists.
                m.application = null;
            }
            myAgency = m;
            if (m != null && m.agency != null) {
                myAgencyId = m.agency.id;
                String role = m.role != null ? m.role.toLowerCase(Locale.US) : "";
                boolean pendingJoin = isPendingMembership(m);
                canLeave = pendingJoin || !"owner".equals(role);
                canManage = !pendingJoin && ("owner".equals(role) || "manager".equals(role));
                canDeleteAgency = !pendingJoin && "owner".equals(role);
                binding.cardJoinByCode.setVisibility(View.GONE);
                binding.cardMyAgency.setVisibility(View.VISIBLE);
                if (binding.rowOwnerEarningsActions != null) {
                    binding.rowOwnerEarningsActions.setVisibility(
                            !pendingJoin && "owner".equals(role) ? View.VISIBLE : View.GONE);
                }
                binding.tvMyAgencyName.setText(
                        m.agency.name != null ? m.agency.name : getString(R.string.agency));
                binding.tvMyAgencyMeta.setText(formatAgencyMeta(m));
                if (canManage && m.agency.activationCode != null
                        && !m.agency.activationCode.isEmpty()) {
                    binding.rowActivationCode.setVisibility(View.VISIBLE);
                    binding.tvActivationCode.setText(m.agency.activationCode);
                } else {
                    binding.rowActivationCode.setVisibility(View.GONE);
                }
                adapter.submit(Collections.emptyList(), myAgencyId, canLeave, canManage);
                if (pendingJoin) {
                    binding.tvEarnings.setVisibility(View.VISIBLE);
                    binding.tvEarnings.setText(R.string.agency_join_pending_hint);
                } else if (m.earnings == null) {
                    binding.tvEarnings.setVisibility(View.VISIBLE);
                    binding.tvEarnings.setText(
                            "أرباح العمولة تظهر لصاحب الوكالة / المدير فقط");
                }
            } else {
                myAgencyId = null;
                canLeave = false;
                canManage = false;
                canDeleteAgency = false;
                binding.cardJoinByCode.setVisibility(View.VISIBLE);
                binding.cardMyAgency.setVisibility(View.GONE);
                binding.tvEarnings.setVisibility(View.GONE);
                binding.rowActivationCode.setVisibility(View.GONE);
                if (binding.rowOwnerEarningsActions != null) {
                    binding.rowOwnerEarningsActions.setVisibility(View.GONE);
                }
                adapter.submit(Collections.emptyList(), null, false, false);
            }
            bindApplicationStatus(m);
            syncAgencyRoomButton();
        });
        vm.getEarnings().observe(this, e -> {
            if (e == null || e.totals == null) {
                return;
            }
            binding.cardMyAgency.setVisibility(View.VISIBLE);
            binding.tvEarnings.setVisibility(View.VISIBLE);
            binding.tvEarnings.setText(String.format(Locale.US,
                    "عمولتك المستلمة: %,d ألماس\n" +
                    "إجمالي هدايا الأعضاء: %,d\n" +
                    "تقدير: مضيفين %,d · تطبيق %,d · وكيل %,d\n" +
                    "النسب: وكيل %.0f%% · تطبيق %.0f%% · مضيف ≈ %.0f%%\n" +
                    "السحب الذاتي من المحفظة · التوزيع لصاحب الوكالة فقط",
                    e.totals.ownerCommissionEarned,
                    e.totals.grossGiftsDiamonds,
                    e.totals.estimatedHostShare,
                    e.totals.estimatedPlatformCut,
                    e.totals.estimatedAgentShare,
                    e.commissionPercent,
                    e.platformCutPercent,
                    e.hostSharePercent));
            if (binding.rowOwnerEarningsActions != null) {
                boolean isOwner = myAgency != null && myAgency.role != null
                        && "owner".equalsIgnoreCase(myAgency.role);
                binding.rowOwnerEarningsActions.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            }
        });
        vm.getMessage().observe(this, m -> {
            if ("application_submitted".equals(m)) {
                if (applicationSheet != null) applicationSheet.dismiss();
                Toast.makeText(this, R.string.agency_application_submitted, Toast.LENGTH_SHORT).show();
            } else if ("agency_deleted".equals(m)) {
                Toast.makeText(this, R.string.agency_deleted, Toast.LENGTH_LONG).show();
            } else if (m != null) {
                Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
            }
        });
        vm.getSubmitting().observe(this, submitting -> {
            if (applicationSubmit == null) return;
            boolean busy = Boolean.TRUE.equals(submitting);
            applicationSubmit.setEnabled(!busy);
            applicationSubmit.setText(busy
                    ? R.string.loading
                    : R.string.agency_submit_application);
        });
        vm.getError().observe(this, e -> {
            if (e == null) return;
            com.Dramizo.Series.util.BalanceRedirect.handle(this, e);
        });
        binding.btnBecomeAgent.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, AgencyCreateActivity.class)));
        binding.btnAgencyRoom.setOnClickListener(v -> {
            String name = myAgency != null && myAgency.agency != null ? myAgency.agency.name : null;
            AgencyRoomLauncher.open(this, myAgencyId, name);
        });
        binding.btnDeleteAgency.setOnClickListener(v -> confirmDeleteAgency());
        vm.load();
    }

    private void confirmDeleteAgency() {
        if (myAgencyId == null || !canDeleteAgency) return;
        String name = myAgency != null && myAgency.agency != null && myAgency.agency.name != null
                ? myAgency.agency.name : "";
        showConfirmSheet(
                getString(R.string.agency_delete_confirm_title),
                getString(R.string.agency_delete_confirm_message)
                        + (name.isEmpty() ? "" : "\n\n«" + name + "»"),
                getString(R.string.agency_delete),
                () -> vm.deleteAgency(myAgencyId));
    }

    private void syncAgencyRoomButton() {
        boolean eligible = myAgency != null && myAgency.isEligibleHost();
        binding.btnAgencyRoom.setVisibility(eligible ? View.VISIBLE : View.GONE);
        binding.btnDeleteAgency.setVisibility(canDeleteAgency ? View.VISIBLE : View.GONE);
        binding.btnManageAgency.setVisibility(canManage ? View.VISIBLE : View.GONE);
    }

    private void bindApplicationStatus(MiscDtos.AgencyMineDto mine) {
        // Once the agency exists, the dashboard card is enough — hide stale "approved" banner.
        if (mine != null && mine.agency != null) {
            binding.cardApplicationStatus.setVisibility(View.GONE);
            binding.btnBecomeAgent.setVisibility(View.GONE);
            return;
        }
        MiscDtos.AgencyApplicationDto application = mine != null ? mine.application : null;
        if (application == null) {
            binding.cardApplicationStatus.setVisibility(View.GONE);
            binding.btnBecomeAgent.setVisibility(View.VISIBLE);
            binding.btnBecomeAgent.setText(R.string.open_agency);
            return;
        }
        String status = application.status != null
                ? application.status.toLowerCase(Locale.US) : "pending";
        binding.cardApplicationStatus.setVisibility(View.VISIBLE);
        binding.tvReviewNote.setVisibility(
                application.reviewNote != null && !application.reviewNote.trim().isEmpty()
                        ? View.VISIBLE : View.GONE);
        if (binding.tvReviewNote.getVisibility() == View.VISIBLE) {
            binding.tvReviewNote.setText(getString(
                    R.string.agency_review_note_format, application.reviewNote));
        }
        switch (status) {
            case "changes_requested":
            case "changes-requested":
                binding.tvApplicationBadge.setText(R.string.agency_status_changes);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_pending);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_changes);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_changes_body);
                binding.btnBecomeAgent.setVisibility(View.VISIBLE);
                binding.btnBecomeAgent.setText(R.string.agency_resubmit);
                break;
            case "rejected":
                binding.tvApplicationBadge.setText(R.string.agency_status_rejected);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_bad);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_rejected);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_rejected_body);
                binding.btnBecomeAgent.setVisibility(View.VISIBLE);
                binding.btnBecomeAgent.setText(R.string.agency_apply_again);
                break;
            case "approved":
                binding.tvApplicationBadge.setText(R.string.agency_status_approved);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_ok);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_approved);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_approved_body);
                binding.btnBecomeAgent.setVisibility(View.GONE);
                break;
            default:
                binding.tvApplicationBadge.setText(R.string.agency_status_pending);
                binding.tvApplicationBadge.setBackgroundResource(R.drawable.bg_agency_badge_pending);
                binding.tvApplicationStatusTitle.setText(R.string.agency_status_pending);
                binding.tvApplicationStatusBody.setText(R.string.agency_status_pending_body);
                binding.btnBecomeAgent.setVisibility(View.GONE);
                break;
        }
    }

    private String formatAgencyMeta(MiscDtos.AgencyMineDto m) {
        if (m == null || m.agency == null) return "";
        if (isPendingMembership(m)) {
            return getString(R.string.agency_join_pending_meta);
        }
        String status = m.agencyStatus != null && !m.agencyStatus.isEmpty()
                ? m.agencyStatus
                : (m.agency.status != null ? m.agency.status : "");
        String statusLabel = "";
        if ("suspended".equalsIgnoreCase(status)) {
            statusLabel = " · موقوفة";
        } else if ("pending".equalsIgnoreCase(status)) {
            statusLabel = " · قيد المراجعة";
        }
        return String.format(Locale.US,
                "%s · %d أعضاء · عمولة %.0f%%%s",
                roleAr(m.role),
                m.agency.memberCount,
                m.agency.commissionPercent,
                statusLabel);
    }

    private static boolean isPendingMembership(MiscDtos.AgencyMineDto m) {
        if (m == null || m.membershipStatus == null) return false;
        return "pending".equalsIgnoreCase(m.membershipStatus.trim());
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

    private BottomSheetDialog newSheet() {
        return AuraDialogHelper.bottomSheet(this);
    }

    private void showConfirmSheet(String title, String message, String yesLabel, Runnable onYes) {
        BottomSheetDialog sheet = newSheet();
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

    private void showCreateDialog() {
        if (myAgency != null && myAgency.application != null) {
            String current = myAgency.application.status;
            if (current == null || "pending".equalsIgnoreCase(current)
                    || "approved".equalsIgnoreCase(current)) {
                Toast.makeText(this, R.string.agency_duplicate_application, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        vm.loadPricing();
        BottomSheetDialog sheet = newSheet();
        applicationSheet = sheet;
        DialogCreateAgencyBinding form = DialogCreateAgencyBinding.inflate(LayoutInflater.from(this));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundResource(R.drawable.bg_aura_dialog_surface);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, pad, pad, pad);
        int panelHeight = (int) (getResources().getDisplayMetrics().heightPixels
                * (AuraDialogHelper.MAX_HEIGHT_RATIO - 0.02f));
        wrap.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, panelHeight));
        TextView title = new TextView(this);
        title.setText(R.string.open_agency);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setGravity(android.view.Gravity.END);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        TextView hint = new TextView(this);
        hint.setText(R.string.agency_application_paid_hint);
        hint.setTextColor(getColor(R.color.aurora_mint));
        hint.setTextSize(12);
        hint.setGravity(android.view.Gravity.END);
        hint.setPadding(0, pad / 3, 0, pad / 2);
        TextView priceView = new TextView(this);
        priceView.setTextColor(0xFFFFD54F);
        priceView.setTextSize(14);
        priceView.setGravity(android.view.Gravity.END);
        priceView.setTypeface(priceView.getTypeface(), android.graphics.Typeface.BOLD);
        priceView.setPadding(0, 0, 0, pad / 2);
        MiscDtos.AgencyPricingDto cached = vm.getPricing().getValue();
        int priceCoins = cached != null ? Math.max(0, cached.createPriceCoins) : 0;
        if (priceCoins > 0) {
            priceView.setText(getString(R.string.agency_create_price_format, priceCoins));
        } else {
            priceView.setText(R.string.agency_application_loading_price);
        }
        androidx.lifecycle.Observer<MiscDtos.AgencyPricingDto> priceObserver = p -> {
            if (p == null) return;
            int coins = Math.max(0, p.createPriceCoins);
            if (coins > 0) {
                priceView.setText(getString(R.string.agency_create_price_format, coins));
            } else {
                priceView.setText(R.string.agency_application_free_hint);
            }
        };
        vm.getPricing().observe(this, priceObserver);
        wrap.addView(title);
        wrap.addView(hint);
        wrap.addView(priceView);
        LinearLayout.LayoutParams formLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        form.getRoot().setLayoutParams(formLp);
        wrap.addView(form.getRoot());
        prefillApplication(form);
        applyAccountCountry(form);
        com.google.android.material.button.MaterialButton submit = form.btnSubmitAgency;
        applicationSubmit = submit;
        submit.setOnClickListener(v -> {
            MiscDtos.AgencyApplicationRequest request = applicationFrom(form);
            String validationError = validateApplication(request);
            if (validationError != null) {
                Toast.makeText(this, validationError, Toast.LENGTH_LONG).show();
                return;
            }
            if (!request.termsAccepted) {
                Toast.makeText(this, R.string.agency_accept_terms, Toast.LENGTH_SHORT).show();
                return;
            }
            vm.submitApplication(request);
        });
        form.btnCancelAgency.setOnClickListener(v -> sheet.dismiss());
        AuraDialogHelper.applyContent(wrap);
        sheet.setContentView(wrap);
        sheet.setOnDismissListener(dialog -> {
            vm.getPricing().removeObserver(priceObserver);
            if (applicationSheet == sheet) applicationSheet = null;
            if (applicationSubmit == submit) applicationSubmit = null;
        });
        sheet.show();
        View bottomSheet = sheet.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(
                    com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private String validateApplication(MiscDtos.AgencyApplicationRequest request) {
        if (request.proposedName.length() < 3) {
            return "اسم الوكالة يجب أن يكون 3 أحرف على الأقل";
        }
        if (request.businessPlan.length() < 50) {
            return "خطة العمل يجب أن تكون واضحة ولا تقل عن 50 حرفاً";
        }
        if (request.country.length() < 2) {
            return "أدخل اسم الدولة بشكل صحيح";
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(request.contactEmail).matches()) {
            return "أدخل بريداً إلكترونياً صحيحاً";
        }
        if (request.contactPhone.length() < 6) {
            return "رقم التواصل يجب أن يتكون من 6 خانات على الأقل";
        }
        if (request.experience.length() < 10) {
            return "اكتب خبرتك في 10 أحرف على الأقل";
        }
        if (request.expectedHostCount < 1) {
            return "أدخل عدد المضيفين المتوقع";
        }
        return null;
    }

    private void prefillApplication(DialogCreateAgencyBinding form) {
        MiscDtos.AgencyApplicationDto a =
                myAgency != null ? myAgency.application : null;
        if (a == null) return;
        form.etAgencyName.setText(a.proposedName);
        form.etAgencyDesc.setText(a.businessPlan != null ? a.businessPlan : a.description);
        form.etCountry.setText(a.country);
        form.etContactEmail.setText(a.contactEmail);
        form.etContactPhone.setText(a.contactPhone);
        form.etExperience.setText(a.experience);
        if (a.expectedHostCount > 0) {
            form.etExpectedHostCount.setText(String.valueOf(a.expectedHostCount));
        }
    }

    private void applyAccountCountry(DialogCreateAgencyBinding form) {
        com.Dramizo.Series.data.remote.dto.AuthDtos.UserDto user =
                ContainerProvider.from(this).getSessionManager().getUser();
        String country = user != null && user.country != null ? user.country.trim() : "";
        if (country.isEmpty()) {
            country = java.util.Locale.getDefault().getCountry();
        }
        if (country == null || country.trim().isEmpty()) country = "JO";
        CountryCatalog.Entry entry = CountryCatalog.resolve(country);
        String canonicalCountry = entry != null ? entry.code : country;
        form.etCountry.setTag(canonicalCountry);
        form.etCountry.setText(CountryCatalog.labelWithFlag(country));
        form.etCountry.setFocusable(false);
        form.etCountry.setClickable(false);
        form.etCountry.setLongClickable(false);
        form.etCountry.setAlpha(0.92f);
    }

    private MiscDtos.AgencyApplicationRequest applicationFrom(DialogCreateAgencyBinding form) {
        MiscDtos.AgencyApplicationRequest r = new MiscDtos.AgencyApplicationRequest();
        r.proposedName = text(form.etAgencyName);
        r.description = text(form.etAgencyDesc);
        r.businessPlan = r.description;
        Object accountCountry = form.etCountry.getTag();
        r.country = accountCountry instanceof String
                ? ((String) accountCountry).trim()
                : text(form.etCountry);
        r.contactEmail = text(form.etContactEmail);
        r.contactPhone = text(form.etContactPhone);
        r.socialLink = null;
        r.experience = text(form.etExperience);
        try {
            r.expectedHostCount = Integer.parseInt(text(form.etExpectedHostCount));
        } catch (NumberFormatException ignored) {
            r.expectedHostCount = 0;
        }
        r.documentUrls = java.util.Collections.emptyList();
        r.termsAccepted = form.checkTerms.isChecked();
        return r;
    }

    private static String text(android.widget.EditText input) {
        return input.getText() != null ? input.getText().toString().trim() : "";
    }

    private void showDistributeDialog() {
        if (myAgencyId == null) return;
        final android.widget.EditText etUser = new android.widget.EditText(this);
        etUser.setHint("معرّف العضو (UUID أو رقم عام)");
        etUser.setSingleLine(true);
        final android.widget.EditText etAmount = new android.widget.EditText(this);
        etAmount.setHint("كمية الألماس");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, pad);
        box.addView(etUser);
        box.addView(etAmount);
        scroll.addView(box);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("توزيع أرباح الوكالة")
                .setMessage("صاحب الوكالة فقط — يُخصم من رصيدك ويُضاف لعضو الوكالة.")
                .setView(scroll)
                .setPositiveButton("توزيع", (d, w) -> {
                    String uid = etUser.getText() != null ? etUser.getText().toString().trim() : "";
                    long diamonds = 0;
                    try {
                        diamonds = Long.parseLong(etAmount.getText() != null
                                ? etAmount.getText().toString().trim() : "0");
                    } catch (NumberFormatException ignored) {}
                    if (uid.isEmpty() || diamonds < 1) {
                        Toast.makeText(this, "أدخل عضواً وكمية صحيحة", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long finalDiamonds = diamonds;
                    ContainerProvider.from(this).getIoExecutor().execute(() -> {
                        java.util.Map<String, Object> body = new java.util.HashMap<>();
                        body.put("userId", uid);
                        body.put("diamonds", finalDiamonds);
                        Result<java.util.Map<String, Object>> r =
                                com.Dramizo.Series.util.ApiCall.execute(
                                        ContainerProvider.from(this).getAgencyApi()
                                                .distribute(myAgencyId, body));
                        runOnUiThread(() -> {
                            if (r.success) {
                                Toast.makeText(this, "تم التوزيع بنجاح", Toast.LENGTH_SHORT).show();
                                vm.load();
                            } else {
                                com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        EdgeToEdgeHelper.keepAboveImeOnFocus(etUser);
        EdgeToEdgeHelper.keepAboveImeOnFocus(etAmount);
        etUser.requestFocus();
    }

    private void openManage(String agencyId) {
        if (agencyId == null || agencyId.isEmpty()) return;
        android.content.Intent i = new android.content.Intent(this, AgencyManageActivity.class);
        i.putExtra(AgencyManageActivity.EXTRA_AGENCY_ID, agencyId);
        startActivity(i);
    }
}
