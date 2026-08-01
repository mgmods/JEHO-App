package com.Dramizo.Series.presentation.cosmetics;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityCosmeticsBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.wallet.BagActivity;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * Mall (مول): Mikoo-style tabs — head cover / ride / bubbles + extras.
 */
public class CosmeticsActivity extends ThemedActivity {
    /** Order must match LABELS / HINTS. */
    static final String[] TYPES = {
            "vip_badge", "entry_effect",
            "level_badge", "room_card", "room_background"
    };
    private static final String[] LABELS = {
            "قطاع الراس", "الدخولية",
            "شارات المستوى", "كروت الروم", "خلفيات الروم"
    };
    private static final String[] HINTS = {
            "غطاء رأس متحرك مع صورتك — اشترِ ثم ارتدِ",
            "الدخولية عند دخول الغرفة (سيارات / مؤثرات)",
            "شارات المستوى بجانب الاسم",
            "إطار كرت الغرفة في القائمة الرئيسية",
            "خلفيات كاملة داخل الروم — طبّقها من إعدادات الغرفة"
    };

    private ActivityCosmeticsBinding binding;
    private CosmeticsViewModel vm;
    private boolean bagMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCosmeticsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(CosmeticsViewModel.class);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnRecharge.setOnClickListener(v ->
                startActivity(new Intent(this, BagActivity.class)));
        binding.btnBag.setOnClickListener(v -> toggleBagMode());

        String initialType = getIntent().getStringExtra(CosmeticsViewModel.EXTRA_TYPE);
        int initialTab = tabForType(initialType);
        bagMode = getIntent().getBooleanExtra("bag_mode", false);
        vm.setBagMode(bagMode);
        refreshBagButton();

        binding.pagerCosmetics.setAdapter(new PagerAdapter(this));
        binding.pagerCosmetics.setOffscreenPageLimit(1);
        new TabLayoutMediator(binding.tabCosmetics, binding.pagerCosmetics,
                (tab, position) -> tab.setText(LABELS[position])).attach();

        binding.pagerCosmetics.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                reloadVisible();
            }
        });

        if (initialTab >= 0 && initialTab < TYPES.length) {
            binding.pagerCosmetics.setCurrentItem(initialTab, false);
        }
    }

    private void toggleBagMode() {
        bagMode = !bagMode;
        vm.setBagMode(bagMode);
        refreshBagButton();
        Toast.makeText(this,
                bagMode ? getString(R.string.my_bag_mall) : getString(R.string.appearance_store),
                Toast.LENGTH_SHORT).show();
        reloadVisible();
    }

    private void refreshBagButton() {
        if (binding.btnBag == null) return;
        binding.btnBag.setAlpha(bagMode ? 1f : 0.75f);
        binding.btnBag.setTextColor(bagMode ? 0xFFE8A317 : 0xFFB8860B);
        if (binding.tvTitle != null) {
            binding.tvTitle.setText(bagMode
                    ? getString(R.string.my_bag_mall)
                    : getString(R.string.appearance_store));
        }
    }

    private void reloadVisible() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (f instanceof CosmeticsCategoryFragment && f.isResumed()) {
                ((CosmeticsCategoryFragment) f).reload();
            }
        }
    }

    static int tabForType(String type) {
        if (type == null || type.isEmpty()) return 0;
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(type)) return i;
        }
        if ("badges".equals(type) || "level_badge".equals(type)) return 2;
        if ("frames".equals(type) || "vip_badge".equals(type) || "host_badge".equals(type)) return 0;
        if ("join_toast".equals(type) || "entry_effect".equals(type)) return 1;
        if ("room_card".equals(type)) return 3;
        if ("room_background".equals(type)) return 4;
        return 0;
    }

    private static final class PagerAdapter extends FragmentStateAdapter {
        PagerAdapter(@NonNull FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return CosmeticsCategoryFragment.newInstance(TYPES[position], HINTS[position]);
        }

        @Override
        public int getItemCount() {
            return TYPES.length;
        }
    }
}
