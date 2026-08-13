package com.Dramizo.Series.presentation.friends;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityFriendsBinding;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * One hub: friends / following / fans + friend / follow / relation requests.
 */
public class FriendsActivity extends ThemedActivity {
    public static final String EXTRA_TAB = "tab";

    public static final int TAB_FRIENDS = 0;
    public static final int TAB_FOLLOWING = 1;
    public static final int TAB_FANS = 2;
    public static final int TAB_REQ_FRIEND = 3;
    public static final int TAB_REQ_FOLLOW = 4;
    public static final int TAB_REQ_RELATION = 5;
    private static final int TAB_COUNT = 6;

    private static final String[] REQ_TYPES = {"friend", "follow", "relation"};
    private static final int[] TAB_TITLES = {
            R.string.friends,
            R.string.following,
            R.string.fans,
            R.string.request_friendship,
            R.string.request_follow,
            R.string.request_relation
    };

    private ActivityFriendsBinding binding;
    private long lastRealtimeReloadMs;
    private final RealtimeClient.UserListener userListener = new RealtimeClient.UserListener() {
        @Override
        public void onUserEvent(String event, com.google.gson.JsonObject payload) {
            long now = System.currentTimeMillis();
            if (now - lastRealtimeReloadMs < 8_000L) return;
            lastRealtimeReloadMs = now;
            runOnUiThread(() -> {
                if (binding == null) return;
                int page = binding.pager.getCurrentItem();
                for (Fragment frag : getSupportFragmentManager().getFragments()) {
                    if (frag instanceof FriendsListFragment
                            && frag.getArguments() != null
                            && frag.getArguments().getInt("mode", -1) == page) {
                        ((FriendsListFragment) frag).reload();
                    } else if (frag instanceof RequestListFragment && page >= TAB_REQ_FRIEND) {
                        ((RequestListFragment) frag).reload();
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
        int initial = Math.min(Math.max(getIntent().getIntExtra(EXTRA_TAB, TAB_FRIENDS), 0), TAB_COUNT - 1);

        binding.pager.setAdapter(new PagerAdapter(this));
        binding.pager.setOffscreenPageLimit(2);
        new TabLayoutMediator(binding.tabs, binding.pager, (tab, position) ->
                tab.setText(TAB_TITLES[position])
        ).attach();
        binding.pager.setCurrentItem(initial, false);
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
            if (position >= TAB_REQ_FRIEND) {
                return RequestListFragment.newInstance(REQ_TYPES[position - TAB_REQ_FRIEND]);
            }
            return FriendsListFragment.newInstance(position);
        }

        @Override public int getItemCount() { return TAB_COUNT; }
    }
}
