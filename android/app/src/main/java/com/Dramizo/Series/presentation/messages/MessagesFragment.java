package com.Dramizo.Series.presentation.messages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.FragmentMessagesBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.chat.ChatConversationActivity;
import com.Dramizo.Series.presentation.chat.ChatPreviewAdapter;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.friends.FriendsActivity;
import com.Dramizo.Series.presentation.friends.RequestsActivity;
import com.Dramizo.Series.presentation.notifications.OfficialNewsActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AppLoadingOverlay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MessagesFragment extends Fragment {
    public static final String ACTION_OFFICIAL_NEWS_UPDATED =
            "com.Dramizo.Series.OFFICIAL_NEWS_UPDATED";

    private FragmentMessagesBinding binding;
    private MessagesViewModel viewModel;
    private ChatPreviewAdapter adapter;
    private final BroadcastReceiver officialNewsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadOfficialNewsPreview();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(binding.getRoot(), "chat");
        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(requireActivity())))
                .get(MessagesViewModel.class);

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        adapter = new ChatPreviewAdapter(item -> {
            Intent i = new Intent(requireContext(), ChatConversationActivity.class);
            i.putExtra(ChatConversationActivity.EXTRA_CONVERSATION_ID, item.id);
            String title = item.title != null ? item.title
                    : (item.peer != null ? (item.peer.displayName != null ? item.peer.displayName : item.peer.username)
                    : getString(R.string.conversation_default));
            i.putExtra(ChatConversationActivity.EXTRA_TITLE, title);
            if (item.peer != null) {
                i.putExtra(ChatConversationActivity.EXTRA_PEER_ID, item.peer.id);
                if (item.peer.avatarUrl != null) {
                    i.putExtra(ChatConversationActivity.EXTRA_AVATAR, item.peer.avatarUrl);
                }
                if (item.peer.hostBadgeUrl != null) {
                    i.putExtra(ChatConversationActivity.EXTRA_HOST_BADGE, item.peer.hostBadgeUrl);
                }
            } else if (item.avatarUrl != null) {
                i.putExtra(ChatConversationActivity.EXTRA_AVATAR, item.avatarUrl);
            }
            startActivity(i);
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setNestedScrollingEnabled(true);
        binding.recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Mikoo-style: SRL must ask the RecyclerView, not the FrameLayout wrapper.
        binding.swipe.setOnChildScrollUpCallback((parent, child) ->
                binding.recycler != null && binding.recycler.canScrollVertically(-1));
        binding.swipe.setOnRefreshListener(() -> {
            viewModel.load(true);
            loadOfficialNewsPreview();
        });
        binding.btnFriends.setOnClickListener(v -> openFriendsTab(0));
        binding.btnRequests.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), RequestsActivity.class)));
        loadPendingRequestBadge();

        if (binding.officialNewsCardMsg != null) {
            binding.officialNewsCardMsg.getRoot().setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), OfficialNewsActivity.class)));
            loadOfficialNewsPreview();
        }

        viewModel.getConversations().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            boolean empty = list == null || list.isEmpty();
            binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.swipe.setRefreshing(false);
            AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.getError().observe(getViewLifecycleOwner(), e -> {
            if (e != null) Toast.makeText(requireContext(), e, Toast.LENGTH_SHORT).show();
            binding.swipe.setRefreshing(false);
            if (isAdded()) AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.load();
    }

    private void openFriendsTab(int tab) {
        Intent i = new Intent(requireContext(), FriendsActivity.class);
        i.putExtra(FriendsActivity.EXTRA_TAB, tab);
        startActivity(i);
    }

    private void loadOfficialNewsPreview() {
        if (binding == null || binding.officialNewsCardMsg == null || !isAdded()) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.OfficialNewsPreviewDto> r =
                    ApiCall.execute(c.getNotificationApi().officialPreview());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> applyOfficialPreview(
                    r.success ? r.data : null));
        });
    }

    private void applyOfficialPreview(@Nullable MiscDtos.OfficialNewsPreviewDto p) {
        if (binding == null || binding.officialNewsCardMsg == null) return;
        String preview = getString(R.string.official_news_hint);
        if (p != null) {
            if (p.lastBody != null && !p.lastBody.isEmpty()) {
                preview = p.lastBody;
            } else if (p.lastTitle != null && !p.lastTitle.isEmpty()) {
                preview = p.lastTitle;
            }
            binding.officialNewsCardMsg.tvOfficialTime.setText(formatPreviewTime(p.lastAt));
            if (p.unread > 0) {
                binding.officialNewsCardMsg.tvOfficialUnread.setVisibility(View.VISIBLE);
                binding.officialNewsCardMsg.tvOfficialUnread.setText(
                        p.unread > 99 ? "99+" : String.valueOf(p.unread));
            } else {
                binding.officialNewsCardMsg.tvOfficialUnread.setVisibility(View.GONE);
            }
        } else {
            binding.officialNewsCardMsg.tvOfficialTime.setText("");
            binding.officialNewsCardMsg.tvOfficialUnread.setVisibility(View.GONE);
        }
        binding.officialNewsCardMsg.tvOfficialLast.setText(preview);
    }

    private static String formatPreviewTime(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            String s = raw.length() > 19 ? raw.substring(0, 19) : raw;
            Date d = iso.parse(s);
            if (d == null) return "";
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Soft refresh only — full reload was causing the whole chat list to blink.
        if (viewModel != null) viewModel.load(false);
        loadPendingRequestBadge();
        registerOfficialNewsReceiver();
    }

    @Override
    public void onPause() {
        unregisterOfficialNewsReceiver();
        super.onPause();
    }

    private void registerOfficialNewsReceiver() {
        if (!isAdded()) return;
        IntentFilter filter = new IntentFilter(ACTION_OFFICIAL_NEWS_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().registerReceiver(officialNewsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(officialNewsReceiver, filter);
        }
    }

    private void unregisterOfficialNewsReceiver() {
        try {
            if (isAdded()) requireContext().unregisterReceiver(officialNewsReceiver);
        } catch (Exception ignored) {
        }
    }

    private void loadPendingRequestBadge() {
        if (binding == null || binding.tvRequestBadge == null) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            int total = 0;
            try {
                for (String type : new String[]{"friend", "follow", "relation"}) {
                    Result<java.util.List<MiscDtos.SocialRequestDto>> r =
                            ApiCall.execute(c.getUserApi().requests(type));
                    if (r.success && r.data != null) {
                        total += r.data.size();
                    }
                }
            } catch (Exception ignored) {
            }
            final int count = total;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null || binding.tvRequestBadge == null) return;
                if (count > 0) {
                    binding.tvRequestBadge.setVisibility(View.VISIBLE);
                    binding.tvRequestBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                } else {
                    binding.tvRequestBadge.setVisibility(View.GONE);
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
