package com.Dramizo.Series.presentation.friends;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.FragmentFriendsListBinding;
import com.Dramizo.Series.databinding.ItemFriendRowBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.FlagImages;
import com.Dramizo.Series.util.VipStyle;

import java.util.ArrayList;
import java.util.List;

/** One swipe page: friends (0), following (1), or fans (2). */
public class FriendsListFragment extends Fragment {
    private static final String ARG_MODE = "mode";

    private int mode;
    private FragmentFriendsListBinding binding;
    private FriendAdapter adapter;
    private String myId;

    public static FriendsListFragment newInstance(int mode) {
        FriendsListFragment f = new FriendsListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_MODE, mode);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getArguments() != null ? getArguments().getInt(ARG_MODE, 0) : 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFriendsListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new FriendAdapter(user -> {
            Intent i = new Intent(requireContext(), ProfileActivity.class);
            i.putExtra(ProfileActivity.EXTRA_USER_ID, user.id);
            startActivity(i);
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        if (binding.swipe != null) {
            binding.swipe.setOnRefreshListener(this::load);
            binding.swipe.setColorSchemeResources(R.color.aurora_mint, R.color.aurora_gold);
        }
        if (binding.tvEmptyText != null) {
            int emptyRes = mode == 0 ? R.string.friends_empty
                    : (mode == 2 ? R.string.fans_empty : R.string.following_empty);
            binding.tvEmptyText.setText(emptyRes);
        }

        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> me = c.getUserRepository().getMe();
            if (me.success && me.data != null) myId = me.data.id;
            if (isAdded()) requireActivity().runOnUiThread(this::load);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (myId != null) load();
    }

    void reload() {
        load();
    }

    private void load() {
        if (myId == null || binding == null || !isAdded()) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<AuthDtos.UserDto>> r;
            if (mode == 0) r = ApiCall.execute(c.getUserApi().friends(1));
            else if (mode == 2) r = ApiCall.execute(c.getUserApi().followers(myId, 1));
            else r = ApiCall.execute(c.getUserApi().following(myId, 1));
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (binding.swipe != null) binding.swipe.setRefreshing(false);
                if (r.success && r.data != null && r.data.items != null) {
                    adapter.submit(r.data.items);
                } else {
                    adapter.submit(new ArrayList<>());
                    if (r.error != null) {
                        Toast.makeText(requireContext(), r.error, Toast.LENGTH_SHORT).show();
                    }
                }
                boolean empty = adapter.getItemCount() == 0;
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private static class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.VH> {
        interface Listener { void onClick(AuthDtos.UserDto u); }
        private final List<AuthDtos.UserDto> items = new ArrayList<>();
        private final Listener listener;
        FriendAdapter(Listener listener) { this.listener = listener; }
        void submit(List<AuthDtos.UserDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemFriendRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AuthDtos.UserDto u = items.get(position);
            String displayName = u.displayName != null && !u.displayName.isEmpty()
                    ? u.displayName
                    : (u.username != null ? u.username : "مستخدم");
            h.b.tvName.setText(displayName);
            h.b.tvId.setText("ID: " + (u.displayPublicId().isEmpty() ? "—" : u.displayPublicId()));
            FlagImages.bind(h.b.imgCountryFlag, u.country);

            int vip = Math.max(0, u.vipLevel);
            if (vip > 0) {
                h.b.tvName.setTextColor(VipStyle.nameColor(vip));
                h.b.tvVipChip.setVisibility(View.VISIBLE);
                h.b.tvVipChip.setText("VIP" + vip);
            } else {
                h.b.tvName.setTextColor(h.itemView.getContext().getColor(R.color.text_primary));
                h.b.tvVipChip.setVisibility(View.GONE);
            }
            long wealth = u.wealthLevel > 0 ? u.wealthLevel : Math.max(1, u.wealthScore);
            long charm = u.popularityLevel > 0
                    ? u.popularityLevel
                    : Math.max(1, Math.max(u.charmScore, u.popularityScore));
            AvatarCosmetics.styleProfileChips(
                    h.b.tvLevelChip,
                    h.b.tvCharmChip,
                    h.b.tvWealthChip,
                    Math.max(1, u.level),
                    charm,
                    wealth);

            AvatarCosmetics.bindWear(h.b.imgAvatar, h.b.imgFrame, u);
            h.itemView.setOnClickListener(v -> listener.onClick(u));
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            final ItemFriendRowBinding b;
            VH(ItemFriendRowBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
