package com.Dramizo.Series.presentation.wallet;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.BottomSheetRechargeBinding;
import com.Dramizo.Series.databinding.ListRoomChargeItemBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.AssetCatalog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Mikoo VideoRoomChargeDialog — room / global coin recharge sheet. */
public class RechargePackagesBottomSheet extends BottomSheetDialogFragment {

    public static void show(FragmentManager fm) {
        if (fm == null || fm.isStateSaved()) return;
        if (fm.findFragmentByTag("recharge_packages") != null) return;
        new RechargePackagesBottomSheet().show(fm, "recharge_packages");
    }

    private BottomSheetRechargeBinding binding;

    @Override
    public int getTheme() {
        return R.style.Theme_AuraLive_BottomSheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            FrameLayout sheet =
                    dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundResource(android.R.color.transparent);
            ViewGroup.LayoutParams lp = sheet.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                sheet.setLayoutParams(lp);
            }
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setFitToContents(true);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            if (dialog.getWindow() != null) {
                int bg = ContextCompat.getColor(requireContext(), R.color.color_gift_dialog_bg);
                // Match panel under Android nav buttons.
                dialog.getWindow().setNavigationBarColor(0xE6101111);
                dialog.getWindow().setDimAmount(0.55f);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = BottomSheetRechargeBinding.inflate(inflater, container, false);
        AppContainer c = ContainerProvider.from(requireActivity());

        PkgAdapter adapter = new PkgAdapter(pkg -> purchase(pkg, c));
        binding.rvChare.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.rvChare.setNestedScrollingEnabled(true);
        binding.rvChare.setAdapter(adapter);

        bindBalance(c);
        binding.progress.setVisibility(View.VISIBLE);
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.PackagesResult> r = c.getWalletRepository().getPackages();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                binding.progress.setVisibility(View.GONE);
                if (r.success && r.data != null && r.data.items != null) {
                    List<WalletDtos.RechargePackageDto> pkgs = new ArrayList<>(r.data.items);
                    adapter.submit(pkgs);
                    List<String> skus = new ArrayList<>();
                    for (WalletDtos.RechargePackageDto pkg : pkgs) {
                        if (pkg != null && pkg.sku != null && !pkg.sku.isEmpty()) skus.add(pkg.sku);
                    }
                    if (!skus.isEmpty()) {
                        c.getBillingHelper().queryProducts(skus, () -> {
                            c.getBillingHelper().applyPlayPrices(pkgs);
                            adapter.submit(pkgs);
                        });
                    }
                } else {
                    Toast.makeText(requireContext(),
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
        return binding.getRoot();
    }

    private void bindBalance(AppContainer c) {
        if (binding == null) return;
        binding.tvBalanceLabel.setText(R.string.my_coins);
        binding.tvBalanceValue.setText("…");
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r = c.getWalletRepository().getWallet();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                long coins = (r.success && r.data != null) ? r.data.coins : 0L;
                binding.tvBalanceValue.setText(
                        NumberFormat.getNumberInstance(Locale.US).format(coins));
            });
        });
    }

    private void purchase(WalletDtos.RechargePackageDto pkg, AppContainer c) {
        c.getBillingHelper().queryProducts(Collections.singletonList(pkg.sku), () ->
                c.getBillingHelper().launchPurchase(requireActivity(), pkg.sku,
                        new com.Dramizo.Series.billing.BillingHelper.PurchaseCallback() {
                            @Override
                            public void onPurchaseSuccess(String sku, String purchaseToken,
                                                          String orderId) {
                                int total = pkg.coins + Math.max(0, pkg.bonusCoins);
                                c.getIoExecutor().execute(() -> {
                                    Result<WalletDtos.WalletDto> r =
                                            com.Dramizo.Series.billing.PlayPurchaseFulfillment.verifyWithRetry(
                                                    c.getWalletRepository(),
                                                    sku, purchaseToken, orderId, total,
                                                    pkg.amountForVerify());
                                    requireActivity().runOnUiThread(() -> {
                                        if (r.success) {
                                            c.getBillingHelper().consumePendingPurchase();
                                            Toast.makeText(requireContext(), R.string.purchase,
                                                    Toast.LENGTH_SHORT).show();
                                            dismiss();
                                        } else {
                                            Toast.makeText(requireContext(),
                                                    r.error != null ? r.error
                                                            : "تم الدفع — افتح المحفظة لتأكيد الرصيد",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                                });
                            }

                            @Override
                            public void onPurchaseError(String message) {
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(),
                                                message != null ? message
                                                        : getString(R.string.error_generic),
                                                Toast.LENGTH_LONG).show());
                            }
                        }));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static final class PkgAdapter extends RecyclerView.Adapter<PkgAdapter.VH> {
        private final List<WalletDtos.RechargePackageDto> items = new ArrayList<>();
        private final java.util.function.Consumer<WalletDtos.RechargePackageDto> onClick;

        PkgAdapter(java.util.function.Consumer<WalletDtos.RechargePackageDto> onClick) {
            this.onClick = onClick;
        }

        void submit(List<WalletDtos.RechargePackageDto> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ListRoomChargeItemBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(items.get(position), position, onClick);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            private final ListRoomChargeItemBinding b;

            VH(ListRoomChargeItemBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(WalletDtos.RechargePackageDto pkg, int position,
                      java.util.function.Consumer<WalletDtos.RechargePackageDto> onClick) {
                int total = pkg.coins + Math.max(0, pkg.bonusCoins);
                b.itemChargeGold.setText(String.format(Locale.US, "%,d", total));
                if (pkg.bonusCoins > 0) {
                    b.itemChargeAmount.setVisibility(View.VISIBLE);
                    b.itemChargeAmount.setText(String.format(Locale.US, "%,d", pkg.coins));
                } else {
                    b.itemChargeAmount.setVisibility(View.GONE);
                }
                b.tvPrice.setText(pkg.displayPrice());
                b.ivBi.setImageResource(
                        pkg.coins > 0
                                ? AssetCatalog.rechargeBagForCoins(pkg.coins)
                                : AssetCatalog.rechargeBagForIndex(position));
                View.OnClickListener buy = v -> onClick.accept(pkg);
                b.getRoot().setOnClickListener(buy);
                b.tvPrice.setOnClickListener(buy);
            }
        }
    }
}
