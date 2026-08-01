package com.Dramizo.Series.presentation.ranking;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityRankingBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.google.android.material.tabs.TabLayout;

public class RankingActivity extends ThemedActivity {
    public static final String EXTRA_CATEGORY = "ranking_category";

    private RankingViewModel viewModel;
    private ActivityRankingBinding binding;
    private RankingAdapter adapter;
    private String period = "daily";
    private String category = "rich";

    private static final String[] CATEGORIES = {
            "rich", "popular", "gifts", "host", "agency", "room"
    };
    private static final int[] CATEGORY_LABELS = {
            R.string.ranking_wealth,
            R.string.ranking_popularity,
            R.string.ranking_gifts,
            R.string.ranking_hosts,
            R.string.ranking_agencies,
            R.string.ranking_rooms
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRankingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        String requestedCategory = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (requestedCategory != null && !requestedCategory.isEmpty()) {
            category = requestedCategory;
        }
        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(RankingViewModel.class);
        adapter = new RankingAdapter(userId -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra(ProfileActivity.EXTRA_USER_ID, userId);
            startActivity(intent);
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.daily));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.weekly));
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.monthly));
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) period = "daily";
                else if (tab.getPosition() == 1) period = "weekly";
                else period = "monthly";
                viewModel.load(period, category);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { viewModel.load(period, category); }
        });

        for (int label : CATEGORY_LABELS) {
            binding.tabCategory.addTab(binding.tabCategory.newTab().setText(label));
        }
        int initialCategory = indexForCategory(category);
        if (initialCategory >= 0 && initialCategory < binding.tabCategory.getTabCount()) {
            binding.tabCategory.getTabAt(initialCategory).select();
        }
        binding.tabCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int i = tab.getPosition();
                if (i >= 0 && i < CATEGORIES.length) category = CATEGORIES[i];
                adapter.setCategory(category);
                binding.tvCategoryDescription.setText(descriptionFor(category));
                viewModel.load(period, category);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) { viewModel.load(period, category); }
        });
        adapter.setCategory(category);
        binding.tvCategoryDescription.setText(descriptionFor(category));

        viewModel.getEntries().observe(this, list -> {
            adapter.submit(list);
            boolean empty = list == null || list.isEmpty();
            binding.tvEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        viewModel.getError().observe(this, e -> {
            if (e != null) Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
        });
        viewModel.load(period, category);
    }

    private static int indexForCategory(String category) {
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equals(category)) return i;
        }
        return 0;
    }

    private String descriptionFor(String category) {
        switch (category) {
            case "rich":
                return getString(R.string.ranking_wealth_description);
            case "popular":
                return getString(R.string.ranking_popularity_description);
            case "gifts":
                return getString(R.string.ranking_gifts_description);
            case "host":
                return getString(R.string.ranking_hosts_description);
            case "agency":
                return getString(R.string.ranking_agencies_description);
            case "room":
                return getString(R.string.ranking_rooms_description);
            default:
                return getString(R.string.ranking_community_description);
        }
    }
}
