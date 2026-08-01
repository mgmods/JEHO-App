package com.Dramizo.Series.presentation.voiceroom;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.RoomShareCodec;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pick friends / chats and send an in-app room invitation. */
public class ShareRoomBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_ROOM_ID = "room_id";
    private static final String ARG_TITLE = "title";
    private static final String ARG_COVER = "cover";

    public static void show(FragmentManager fm, String roomId, String title) {
        show(fm, roomId, title, null);
    }

    public static void show(FragmentManager fm, String roomId, String title,
                            @Nullable String coverUrl) {
        if (fm == null || roomId == null || roomId.isEmpty() || fm.isStateSaved()) return;
        if (fm.findFragmentByTag("share_room") != null) return;
        ShareRoomBottomSheet sheet = new ShareRoomBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        args.putString(ARG_TITLE, title);
        if (coverUrl != null && !coverUrl.isEmpty()) {
            args.putString(ARG_COVER, coverUrl);
        }
        sheet.setArguments(args);
        sheet.show(fm, "share_room");
    }

    @Override
    public int getTheme() {
        return com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> com.Dramizo.Series.util.AuraDialogHelper.configureShown(dialog));
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.bottom_sheet_share_room, container, false);
        String roomId = getArguments() != null ? getArguments().getString(ARG_ROOM_ID) : null;
        String title = getArguments() != null ? getArguments().getString(ARG_TITLE) : null;
        String coverUrl = getArguments() != null ? getArguments().getString(ARG_COVER) : null;
        RecyclerView recycler = root.findViewById(R.id.recyclerChats);
        View progress = root.findViewById(R.id.progress);
        TextView empty = root.findViewById(R.id.tvEmpty);
        MaterialButton btnSend = root.findViewById(R.id.btnSendShare);
        MaterialButton btnExternal = root.findViewById(R.id.btnShareExternal);

        PickAdapter adapter = new PickAdapter(btnSend::setEnabled);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        if (btnExternal != null) {
            btnExternal.setOnClickListener(v -> {
                if (roomId == null || roomId.isEmpty()) return;
                String link = com.Dramizo.Series.util.InviteReferralHelper.roomOpenUrl(roomId);
                String safeTitle = title != null && !title.isEmpty() ? title : "JEHO CHAT";
                String message = getString(R.string.share_room_external_message, safeTitle, link);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_SUBJECT, safeTitle);
                share.putExtra(Intent.EXTRA_TEXT, message);
                startActivity(Intent.createChooser(share, getString(R.string.share_room_external)));
            });
        }

        progress.setVisibility(View.VISIBLE);
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<ChatDtos.ConversationList> convs = c.getChatRepository().getConversations();
            Result<MiscDtos.ListResult<AuthDtos.UserDto>> friends =
                    ApiCall.execute(c.getUserApi().friends(1));
            List<PickTarget> targets = mergeTargets(
                    convs.success && convs.data != null ? convs.data.items : null,
                    friends.success && friends.data != null ? friends.data.items : null);
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                adapter.submit(targets);
                empty.setVisibility(targets.isEmpty() ? View.VISIBLE : View.GONE);
                recycler.setVisibility(targets.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });

        btnSend.setOnClickListener(v -> {
            List<PickTarget> selected = adapter.selected();
            if (selected.isEmpty() || roomId == null) return;
            btnSend.setEnabled(false);
            String payload = RoomShareCodec.encode(roomId, title, coverUrl);
            progress.setVisibility(View.VISIBLE);
            c.getIoExecutor().execute(() -> {
                int ok = 0;
                for (PickTarget t : selected) {
                    if (t == null) continue;
                    String convId = t.conversationId;
                    if (convId == null || convId.isEmpty()) {
                        if (t.peerId == null || t.peerId.isEmpty()) continue;
                        Map<String, String> body = new HashMap<>();
                        body.put("peerId", t.peerId);
                        Result<ChatDtos.ConversationDto> created =
                                ApiCall.execute(c.getChatApi().create(body));
                        if (!created.success || created.data == null || created.data.id == null) {
                            continue;
                        }
                        convId = created.data.id;
                    }
                    Result<ChatDtos.MessageDto> sent =
                            c.getChatRepository().sendMessage(convId, payload, null);
                    if (sent.success) ok++;
                }
                int sentCount = ok;
                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    progress.setVisibility(View.GONE);
                    Toast.makeText(requireContext(),
                            getString(R.string.share_room_sent, sentCount),
                            Toast.LENGTH_SHORT).show();
                    dismissAllowingStateLoss();
                });
            });
        });
        return root;
    }

    private static List<PickTarget> mergeTargets(
            @Nullable List<ChatDtos.ConversationDto> conversations,
            @Nullable List<AuthDtos.UserDto> friends) {
        List<PickTarget> out = new ArrayList<>();
        Set<String> peerSeen = new HashSet<>();
        if (conversations != null) {
            for (ChatDtos.ConversationDto c : conversations) {
                if (c == null) continue;
                String peerId = c.peer != null ? c.peer.id : null;
                String name = c.peer != null && c.peer.displayName != null && !c.peer.displayName.isEmpty()
                        ? c.peer.displayName
                        : (c.title != null ? c.title : "دردشة");
                String avatar = c.peer != null ? c.peer.avatarUrl : c.avatarUrl;
                if (peerId != null) peerSeen.add(peerId);
                out.add(new PickTarget(c.id, peerId, name, avatar,
                        peerId != null ? "صديق · دردشة" : "دردشة"));
            }
        }
        if (friends != null) {
            for (AuthDtos.UserDto u : friends) {
                if (u == null || u.id == null || peerSeen.contains(u.id)) continue;
                peerSeen.add(u.id);
                String name = u.displayName != null && !u.displayName.isEmpty()
                        ? u.displayName
                        : (u.username != null ? u.username : "صديق");
                out.add(new PickTarget(null, u.id, name, u.avatarUrl, "صديق"));
            }
        }
        return out;
    }

    private static final class PickTarget {
        final String conversationId;
        final String peerId;
        final String name;
        final String avatarUrl;
        final String hint;

        PickTarget(String conversationId, String peerId, String name, String avatarUrl, String hint) {
            this.conversationId = conversationId;
            this.peerId = peerId;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.hint = hint;
        }

        String key() {
            if (conversationId != null && !conversationId.isEmpty()) return "c:" + conversationId;
            return "p:" + peerId;
        }
    }

    private static final class PickAdapter extends RecyclerView.Adapter<PickAdapter.VH> {
        private final List<PickTarget> items = new ArrayList<>();
        private final Set<String> selectedKeys = new HashSet<>();
        private final java.util.function.Consumer<Boolean> onSelectionChanged;

        PickAdapter(java.util.function.Consumer<Boolean> onSelectionChanged) {
            this.onSelectionChanged = onSelectionChanged;
        }

        void submit(List<PickTarget> list) {
            items.clear();
            selectedKeys.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
            onSelectionChanged.accept(false);
        }

        List<PickTarget> selected() {
            List<PickTarget> out = new ArrayList<>();
            for (PickTarget t : items) {
                if (t != null && selectedKeys.contains(t.key())) out.add(t);
            }
            return out;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_share_chat, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            PickTarget item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvHint.setText(item.hint != null ? item.hint : "");
            AvatarCosmetics.bindAvatar(holder.imgAvatar, item.avatarUrl);
            boolean checked = selectedKeys.contains(item.key());
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(checked);
            holder.check.setOnCheckedChangeListener((button, isChecked) -> {
                String key = item.key();
                if (isChecked) selectedKeys.add(key);
                else selectedKeys.remove(key);
                onSelectionChanged.accept(!selectedKeys.isEmpty());
            });
            holder.itemView.setOnClickListener(v -> holder.check.toggle());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final CheckBox check;
            final ImageView imgAvatar;
            final TextView tvName;
            final TextView tvHint;

            VH(View v) {
                super(v);
                check = v.findViewById(R.id.checkSelect);
                imgAvatar = v.findViewById(R.id.imgAvatar);
                tvName = v.findViewById(R.id.tvName);
                tvHint = v.findViewById(R.id.tvHint);
            }
        }
    }
}
