package com.Dramizo.Series.presentation.vip;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.util.ServerAssets;
import com.Dramizo.Series.util.VipStyle;

import java.util.List;

public class VipActivity extends ThemedActivity {
    private VipAdapter adapter;
    private LinearLayout layoutVipPlans;
    private TextView tvCurrentVip;
    private TextView tvVipHeroSub;
    private TextView tvVipEmpty;
    private ImageView imgHeroMedal;
    private ImageView imgHeroFrame;
    private int currentVip;
    private int wealthLevel;
    private List<MiscDtos.VipPlanDto> cachedPlans;

    @Override
    protected boolean wantsRemoteThemeChrome() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vip);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(findViewById(R.id.header));

        findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());
        layoutVipPlans = findViewById(R.id.layoutVipPlans);
        tvCurrentVip = findViewById(R.id.tvCurrentVip);
        tvVipHeroSub = findViewById(R.id.tvVipHeroSub);
        tvVipEmpty = findViewById(R.id.tvVipEmpty);
        imgHeroMedal = findViewById(R.id.imgHeroMedal);
        imgHeroFrame = findViewById(R.id.imgHeroFrame);

        AppContainer c = ContainerProvider.from(this);
        AuthDtos.UserDto me = c.getSessionManager().getUser();
        if (me != null) {
            wealthLevel = Math.max(0, me.wealthLevel);
        }

        VipViewModel vm = new ViewModelProvider(this, new ViewModelFactory(c)).get(VipViewModel.class);
        adapter = new VipAdapter(this, new VipAdapter.Listener() {
            @Override public void onBuy(int level) { vm.purchase(level, 30); }
            @Override public void onBuy(int level, int durationDays) {
                vm.purchase(level, durationDays);
            }
        });

        vm.getVipLevel().observe(this, level -> {
            currentVip = level != null ? level : 0;
            AuthDtos.UserDto u = ContainerProvider.from(this).getSessionManager().getUser();
            if (u != null) wealthLevel = Math.max(0, u.wealthLevel);
            bindHero(currentVip);
            if (adapter != null) {
                adapter.submit(layoutVipPlans, cachedPlans, currentVip, wealthLevel);
            }
        });
        vm.getPlans().observe(this, list -> {
            cachedPlans = list;
            if (adapter != null) {
                adapter.submit(layoutVipPlans, list, currentVip, wealthLevel);
            }
            boolean empty = list == null || list.isEmpty();
            if (tvVipEmpty != null) {
                tvVipEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
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
            if (adapter != null && layoutVipPlans != null) {
                adapter.setPurchaseLocked(Boolean.TRUE.equals(busy), layoutVipPlans);
            }
        });
        bindHero(0);
        vm.load();
    }

    private void bindHero(int vip) {
        if (tvCurrentVip == null) return;
        if (vip > 0) {
            int tier = VipCatalog.clamp(vip);
            tvCurrentVip.setText(getString(R.string.vip_current_level_fmt, vip)
                    + (vip <= 7 ? " · " + VipCatalog.titleAr(tier) : ""));
            if (tvVipHeroSub != null) {
                tvVipHeroSub.setText(getString(R.string.vip_hero_active_sub));
            }
            if (imgHeroMedal != null) {
                imgHeroMedal.setImageResource(VipCatalog.medalDrawable(tier));
                ServerAssets.load(imgHeroMedal, VipCatalog.medalUrl(tier));
                imgHeroMedal.setVisibility(View.VISIBLE);
            }
            if (imgHeroFrame != null) {
                String frame = VipStyle.fixedFramePath(tier);
                if (frame != null) {
                    ServerAssets.load(imgHeroFrame, frame);
                    imgHeroFrame.setVisibility(View.VISIBLE);
                }
            }
        } else {
            tvCurrentVip.setText(R.string.vip_current_none);
            if (tvVipHeroSub != null) {
                tvVipHeroSub.setText(R.string.vip_hero_sub);
            }
            if (imgHeroMedal != null) {
                imgHeroMedal.setImageResource(R.drawable.vip_medal_mikoo_1);
                imgHeroMedal.setAlpha(0.55f);
            }
            if (imgHeroFrame != null) {
                imgHeroFrame.setImageDrawable(null);
                imgHeroFrame.setVisibility(View.INVISIBLE);
            }
        }
        if (imgHeroMedal != null && vip > 0) {
            imgHeroMedal.setAlpha(1f);
        }
    }
}
