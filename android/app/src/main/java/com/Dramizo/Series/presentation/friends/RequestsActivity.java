package com.Dramizo.Series.presentation.friends;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityRequestsBinding;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.google.android.material.tabs.TabLayoutMediator;

public class RequestsActivity extends ThemedActivity {
    public static final String EXTRA_TAB = "tab";

    private static final String[] TYPES = {"friend", "follow", "relation"};
    private static final int[] TITLES = {
            R.string.request_friendship,
            R.string.request_follow,
            R.string.request_relation
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityRequestsBinding binding = ActivityRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        int start = Math.min(Math.max(getIntent().getIntExtra(EXTRA_TAB, 0), 0), TYPES.length - 1);

        binding.pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return RequestListFragment.newInstance(TYPES[position]);
            }

            @Override
            public int getItemCount() {
                return TYPES.length;
            }
        });
        binding.pager.setOffscreenPageLimit(1);

        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) ->
                tab.setText(TITLES[position])
        ).attach();

        binding.pager.setCurrentItem(start, false);
    }
}
