package com.Dramizo.Series.presentation.friends;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityFriendsBinding;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.google.android.material.tabs.TabLayoutMediator;

public class FriendsActivity extends ThemedActivity {
    public static final String EXTRA_TAB = "tab";

    private ActivityFriendsBinding binding;
    private final RealtimeClient.UserListener userListener = new RealtimeClient.UserListener() {
        @Override
        public void onUserEvent(String event, com.google.gson.JsonObject payload) {
            runOnUiThread(() -> {
                if (binding == null) return;
                int page = binding.pager.getCurrentItem();
                Fragment f = getSupportFragmentManager()
                        .findFragmentByTag("f" + page);
                // ViewPager2 tags fragments as "f" + itemId
                for (Fragment frag : getSupportFragmentManager().getFragments()) {
                    if (frag instanceof FriendsListFragment) {
                        ((FriendsListFragment) frag).reload();
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFriendsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        RealtimeClient.getInstance().connect(
                com.Dramizo.Series.presentation.common.ContainerProvider.from(this)
                        .getSessionManager().getAccessToken());
        RealtimeClient.getInstance().addUserListener(userListener);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        if (binding.btnInviteCode != null) {
            binding.btnInviteCode.setOnClickListener(v ->
                    startActivity(new android.content.Intent(
                            this, com.Dramizo.Series.presentation.invite.InvitationActivity.class)));
        }
        int initial = Math.min(Math.max(getIntent().getIntExtra(EXTRA_TAB, 0), 0), 2);

        binding.pager.setAdapter(new PagerAdapter(this));
        binding.pager.setOffscreenPageLimit(2);
        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) -> {
            if (position == 0) tab.setText(R.string.friends);
            else if (position == 1) tab.setText(R.string.following);
            else tab.setText(R.string.fans);
        }).attach();
        binding.pager.setCurrentItem(initial, false);
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                Fragment f = findPageFragment(position);
                if (f instanceof FriendsListFragment) ((FriendsListFragment) f).reload();
            }
        });
    }

    private Fragment findPageFragment(int position) {
        return getSupportFragmentManager().findFragmentByTag("f" + position);
    }

    @Override
    protected void onDestroy() {
        RealtimeClient.getInstance().removeUserListener(userListener);
        super.onDestroy();
    }

    private static class PagerAdapter extends FragmentStateAdapter {
        PagerAdapter(@NonNull FragmentActivity activity) { super(activity); }
        @NonNull @Override
        public Fragment createFragment(int position) {
            return FriendsListFragment.newInstance(position);
        }
        @Override public int getItemCount() { return 3; }
    }
}
