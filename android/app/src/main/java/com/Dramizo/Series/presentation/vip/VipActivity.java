package com.Dramizo.Series.presentation.vip;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.os.Bundle;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityVipBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;

import java.util.List;

public class VipActivity extends ThemedActivity {
    private VipAdapter adapter;
    private int currentVip;
    private int wealthLevel;
    private List<MiscDtos.VipPlanDto> cachedPlans;

    @Override
    protected boolean wantsRemoteThemeChrome() {
        // Match light JEHO chrome (settings / wallet), not black Mikoo noble.
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityVipBinding binding = ActivityVipBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        AppContainer c = ContainerProvider.from(this);
        AuthDtos.UserDto me = c.getSessionManager().getUser();
        if (me != null) {
            wealthLevel = Math.max(0, me.wealthLevel);
        }

        VipViewModel vm = new ViewModelProvider(this, new ViewModelFactory(c)).get(VipViewModel.class);
        adapter = new VipAdapter(new VipAdapter.Listener() {
            @Override public void onBuy(int level) { vm.purchase(level, 30); }
            @Override public void onBuy(int level, int durationDays) {
                vm.purchase(level, durationDays);
            }
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        vm.getVipLevel().observe(this, level -> {
            currentVip = level != null ? level : 0;
            AuthDtos.UserDto u = ContainerProvider.from(this).getSessionManager().getUser();
            if (u != null) wealthLevel = Math.max(0, u.wealthLevel);
            binding.tvCurrentVip.setText(currentVip > 0
                    ? getString(R.string.vip_current_level_fmt, currentVip)
                    : getString(R.string.vip_current_none));
            adapter.submit(cachedPlans, currentVip, wealthLevel);
        });
        vm.getPlans().observe(this, list -> {
            cachedPlans = list;
            adapter.submit(list, currentVip, wealthLevel);
            boolean empty = list == null || list.isEmpty();
            if (binding.tvVipEmpty != null) {
                binding.tvVipEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
            }
            if (empty) {
                Toast.makeText(this, R.string.vip_empty_retry, Toast.LENGTH_SHORT).show();
            }
        });
        vm.getMessage().observe(this, m -> {
            if (m != null) Toast.makeText(this, m, Toast.LENGTH_LONG).show();
        });
        vm.getError().observe(this, e -> {
            if (e != null) com.Dramizo.Series.util.BalanceRedirect.handle(this, e);
        });
        vm.getPurchasing().observe(this, busy -> {
            if (adapter != null) adapter.setPurchaseLocked(Boolean.TRUE.equals(busy));
        });
        vm.load();
    }
}
