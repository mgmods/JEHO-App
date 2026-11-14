package com.Dramizo.Series.presentation.voiceroom;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.chat.ChatPreviewAdapter;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Collections;
import java.util.List;

/** Mikoo white in-room private messages dialog. */
public class RoomPrivateMsgSheet extends BottomSheetDialogFragment {
    private static final String TAG = "room_private_msg";

    public static void showSheet(@NonNull androidx.fragment.app.FragmentManager fm) {
        if (fm.isStateSaved()) return;
        new RoomPrivateMsgSheet().show(fm, TAG);
    }

    @Override
    public int getTheme() {
        return R.style.Theme_AuraLive_BottomSheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_room_private_msg, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ImageView close = view.findViewById(R.id.btnPrivateMsgClose);
        ProgressBar progress = view.findViewById(R.id.progressPrivateMsg);
        TextView empty = view.findViewById(R.id.tvPrivateMsgEmpty);
        RecyclerView recycler = view.findViewById(R.id.recyclerPrivateMsg);
        if (close != null) close.setOnClickListener(v -> dismissAllowingStateLoss());

        ChatPreviewAdapter adapter = new ChatPreviewAdapter(item -> {
            String title = item.title != null ? item.title
                    : (item.peer != null
                    ? (item.peer.displayName != null ? item.peer.displayName : item.peer.username)
                    : "محادثة");
            String peerId = item.peer != null ? item.peer.id : null;
            String avatar = item.peer != null ? item.peer.avatarUrl : null;
            // Stay in the voice room — keep Zego/realtime via room keep-alive.
            if (getActivity() instanceof VoiceRoomActivity) {
                ((VoiceRoomActivity) getActivity()).openPrivateConversation(
                        item.id, peerId, title, avatar);
            } else {
                Intent i = new Intent(requireContext(), ChatConversationActivity.class);
                i.putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, item.id);
                i.putExtra(ChatConversationActivity.EXTRA_TITLE, title);
                if (peerId != null) {
                    i.putExtra(ChatConversationActivity.EXTRA_PEER_ID, peerId);
                }
                if (avatar != null) {
                    i.putExtra(ChatConversationActivity.EXTRA_AVATAR, avatar);
                }
                startActivity(i);
            }
            dismissAllowingStateLoss();
        });
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        progress.setVisibility(View.VISIBLE);
        ContainerProvider.from(requireActivity()).getIoExecutor().execute(() -> {
            Result<ChatDtos.ConversationList> r =
                    ContainerProvider.from(requireActivity()).getConversationsUseCase.execute();
            List<ChatDtos.ConversationDto> list = r.success && r.data != null && r.data.items != null
                    ? r.data.items : Collections.emptyList();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                recycler.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.submit(list);
            });
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        View bottom = getDialog() != null
                ? getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet)
                : null;
        if (bottom != null) {
            bottom.setBackgroundResource(android.R.color.transparent);
            ViewGroup.LayoutParams lp = bottom.getLayoutParams();
            lp.height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.72f);
            bottom.setLayoutParams(lp);
        }
    }
}
