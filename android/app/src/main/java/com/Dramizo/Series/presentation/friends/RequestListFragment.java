package com.Dramizo.Series.presentation.friends;

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
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.FragmentRequestListBinding;
import com.Dramizo.Series.databinding.ItemRequestRowBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.List;

public class RequestListFragment extends Fragment {
    private static final String ARG_TYPE = "type";

    private FragmentRequestListBinding binding;
    private AppContainer c;
    private String type = "friend";
    private RequestAdapter adapter;
    private final RealtimeClient.UserListener userListener = new RealtimeClient.UserListener() {
        @Override
        public void onUserEvent(String event, com.google.gson.JsonObject payload) {
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> load());
        }
    };

    public static RequestListFragment newInstance(String type) {
        RequestListFragment f = new RequestListFragment();
        Bundle b = new Bundle();
        b.putString(ARG_TYPE, type);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRequestListBinding.inflate(inflater, container, false);
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(binding.getRoot(), "chat");
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) type = getArguments().getString(ARG_TYPE, "friend");
        c = ContainerProvider.from(requireActivity());
        String token = c.getSessionManager().getAccessToken();
        RealtimeClient.getInstance().connect(token);
        adapter = new RequestAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        RealtimeClient.getInstance().addUserListener(userListener);
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (c != null) load();
    }

    private boolean loadsAcceptedRelations() {
        return !"follow".equals(type);
    }

    void load() {
        if (binding == null || c == null) return;
        binding.tvEmpty.setVisibility(View.GONE);
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.SocialRequestDto>> pending =
                    ApiCall.execute(c.getUserApi().requests(type));
            List<MiscDtos.SocialRequestDto> items = new ArrayList<>();
            if (pending.success && pending.data != null) items.addAll(pending.data);
            if (loadsAcceptedRelations()) {
                Result<List<MiscDtos.SocialRequestDto>> accepted =
                        ApiCall.execute(c.getUserApi().relations(type));
                if (accepted.success && accepted.data != null) {
                    for (MiscDtos.SocialRequestDto a : accepted.data) {
                        if (a == null) continue;
                        boolean dup = false;
                        for (MiscDtos.SocialRequestDto p : items) {
                            if (p != null && p.id != null && p.id.equals(a.id)) { dup = true; break; }
                        }
                        if (!dup) items.add(a);
                    }
                }
            }
            if (!isAdded()) return;
            final List<MiscDtos.SocialRequestDto> finalItems = items;
            final String err = pending.error;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                adapter.submit(finalItems);
                binding.tvEmpty.setVisibility(finalItems.isEmpty() ? View.VISIBLE : View.GONE);
                if (err != null && finalItems.isEmpty()) {
                    Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.VH> {
        private final List<MiscDtos.SocialRequestDto> items = new ArrayList<>();

        void submit(List<MiscDtos.SocialRequestDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemRequestRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MiscDtos.SocialRequestDto req = items.get(position);
            String name = req.user != null
                    ? (req.user.displayName != null ? req.user.displayName : req.user.username)
                    : "مستخدم";
            h.b.tvName.setText(name);
            boolean accepted = req.status != null && "accepted".equalsIgnoreCase(req.status);
            String publicId = req.user != null ? req.user.displayPublicId() : "";
            String idLine = accepted ? (typeLabel() + " · مقبولة") : typeLabel();
            if (publicId != null && !publicId.isEmpty()) {
                idLine = "ID: " + publicId + " · " + idLine;
            }
            h.b.tvId.setText(idLine);
            if (req.user != null) {
                AvatarCosmetics.bindWear(h.b.imgAvatar, h.b.imgFrame, req.user);
                int vip = Math.max(0, req.user.vipLevel);
                int level = Math.max(1, req.user.level);
                long wealth = req.user.wealthLevel > 0
                        ? req.user.wealthLevel
                        : Math.max(1, req.user.wealthScore);
                long charm = req.user.popularityLevel > 0
                        ? req.user.popularityLevel
                        : Math.max(1, Math.max(req.user.charmScore, req.user.popularityScore));
                if (h.b.tvVipChip != null) {
                    if (vip > 0) {
                        h.b.tvVipChip.setVisibility(View.VISIBLE);
                        h.b.tvVipChip.setText("VIP" + vip);
                    } else {
                        h.b.tvVipChip.setVisibility(View.GONE);
                    }
                }
                AvatarCosmetics.styleProfileChips(
                        h.b.tvLevelChip,
                        h.b.tvCharmChip,
                        h.b.tvWealthChip,
                        level,
                        charm,
                        wealth);
            } else {
                AvatarCosmetics.bindWear(h.b.imgAvatar, h.b.imgFrame, null, null);
                if (h.b.tvVipChip != null) h.b.tvVipChip.setVisibility(View.GONE);
                if (h.b.tvLevelChip != null) h.b.tvLevelChip.setVisibility(View.GONE);
                if (h.b.tvWealthChip != null) h.b.tvWealthChip.setVisibility(View.GONE);
                if (h.b.tvCharmChip != null) h.b.tvCharmChip.setVisibility(View.GONE);
            }
            if (accepted) {
                h.b.btnAccept.setVisibility(View.GONE);
                h.b.btnReject.setVisibility(View.VISIBLE);
                h.b.btnReject.setText("إنهاء");
                h.b.btnAccept.setOnClickListener(null);
                h.b.btnReject.setOnClickListener(v -> endBond(req));
            } else {
                h.b.btnAccept.setVisibility(View.VISIBLE);
                h.b.btnReject.setVisibility(View.VISIBLE);
                h.b.btnReject.setText("رفض");
                h.b.btnAccept.setOnClickListener(v -> decide(req.id, true));
                h.b.btnReject.setOnClickListener(v -> decide(req.id, false));
            }
        }

        private String typeLabel() {
            switch (type) {
                case "follow": return getString(R.string.request_follow);
                case "relation": return getString(R.string.request_relation);
                case "guardian": return getString(R.string.guardian);
                case "sibling": return "أخوة";
                case "fans": return "معجبين";
                case "love": return "حب";
                case "couple": return "أزواج";
                default: return getString(R.string.request_friendship);
            }
        }

        private void decide(String id, boolean accept) {
            c.getIoExecutor().execute(() -> {
                Result<Object> r = ApiCall.execute(accept
                        ? c.getUserApi().acceptRequest(id)
                        : c.getUserApi().rejectRequest(id));
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    String msg = accept
                            ? (r.success ? "تم القبول" : (r.error != null ? r.error : "خطأ"))
                            : (r.success ? "تم الرفض" : (r.error != null ? r.error : "خطأ"));
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    if (r.success) load();
                });
            });
        }

        private void endBond(MiscDtos.SocialRequestDto req) {
            if (req == null || req.user == null || req.user.id == null) return;
            AuraDialogHelper.confirm(requireContext(),
                    "إنهاء الارتباط",
                    "هل تريد إنهاء هذا الارتباط؟",
                    "إنهاء",
                    () -> c.getIoExecutor().execute(() -> {
                        Result<Object> r = ApiCall.execute(
                                c.getUserApi().endBond(req.user.id, type));
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    r.success ? "تم إنهاء الارتباط"
                                            : (r.error != null ? r.error : "خطأ"),
                                    Toast.LENGTH_SHORT).show();
                            if (r.success) load();
                        });
                    }),
                    getString(android.R.string.cancel),
                    null);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemRequestRowBinding b;
            VH(ItemRequestRowBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    @Override
    public void onDestroyView() {
        RealtimeClient.getInstance().removeUserListener(userListener);
        super.onDestroyView();
        binding = null;
    }
}
