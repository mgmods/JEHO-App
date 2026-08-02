package com.Dramizo.Series.presentation.cosmetics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.Dramizo.Series.databinding.FragmentCosmeticsCategoryBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.MikooLoadingAnim;

/** One swipeable page of the appearance mall. */
public class CosmeticsCategoryFragment extends Fragment {
    private static final String ARG_TYPE = "type";
    private static final String ARG_HINT = "hint";

    private FragmentCosmeticsCategoryBinding binding;
    private CosmeticsViewModel vm;
    private CosmeticsAdapter adapter;
    private String type;

    public static CosmeticsCategoryFragment newInstance(String type, String hint) {
        CosmeticsCategoryFragment f = new CosmeticsCategoryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        args.putString(ARG_HINT, hint);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCosmeticsCategoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        type = args != null ? args.getString(ARG_TYPE) : "vip_badge";
        String hint = args != null ? args.getString(ARG_HINT) : "";
        binding.tvCosmeticsHint.setText(hint != null ? hint : "");

        vm = new ViewModelProvider(requireActivity(),
                new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(CosmeticsViewModel.class);

        adapter = new CosmeticsAdapter(new CosmeticsAdapter.Listener() {
            @Override public void onPurchase(String cosmeticId) { vm.purchase(cosmeticId); }
            @Override public void onEquip(String cosmeticId) { vm.equip(cosmeticId); }
            @Override public void onUnequip(String cosmeticId) { vm.unequip(cosmeticId); }
            @Override public boolean isOwned(String cosmeticId) { return vm.isOwned(cosmeticId); }
            @Override public boolean isEquipped(String cosmeticId) { return vm.isEquipped(cosmeticId); }
        });
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemViewCacheSize(6);
        binding.recycler.setAdapter(adapter);

        vm.getCatalogPage().observe(getViewLifecycleOwner(), page -> {
            if (page == null || page.type == null) return;
            if (!typeEquals(page.type, type)) return;
            hideLoading();
            adapter.submit(page.items);
            boolean empty = page.items == null || page.items.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (empty) {
                binding.tvEmpty.setText(vm.isBagMode()
                        ? "حقيبتك فارغة في هذا القسم"
                        : "لا توجد عناصر في هذا القسم حالياً");
            }
        });
        vm.getMessage().observe(getViewLifecycleOwner(), m -> {
            if (m != null && isResumed()) {
                Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show();
            }
        });
        vm.getError().observe(getViewLifecycleOwner(), e -> {
            hideLoading();
            if (e != null && isResumed()) {
                com.Dramizo.Series.util.BalanceRedirect.handle(requireActivity(), e);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    void reload() {
        if (vm == null) return;
        showLoading();
        binding.tvEmpty.setVisibility(View.GONE);
        vm.loadForType(type);
    }

    private void showLoading() {
        if (binding == null || binding.svCosmeticsLoading == null) return;
        binding.svCosmeticsLoading.setVisibility(View.VISIBLE);
        MikooLoadingAnim.bind(binding.svCosmeticsLoading);
    }

    private void hideLoading() {
        if (binding == null || binding.svCosmeticsLoading == null) return;
        MikooLoadingAnim.stop(binding.svCosmeticsLoading);
        binding.svCosmeticsLoading.setVisibility(View.GONE);
    }

    private static boolean typeEquals(String a, String b) {
        if (a == null && b == null) return true;
        return a != null && a.equals(b);
    }

    @Override
    public void onDestroyView() {
        hideLoading();
        super.onDestroyView();
        binding = null;
    }
}
