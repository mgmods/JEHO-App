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
import com.Dramizo.Series.presentation.notifications.OfficialNewsActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AppLoadingOverlay;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Messages tab — clean conversation inbox list. */
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
            loadAdminMessagesPreview();
        }
    };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        applySectionWallpaper();
        viewModel = new ViewModelProvider(
                this, new ViewModelFactory(ContainerProvider.from(requireActivity())))
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
                    : (item.peer != null
                    ? (item.peer.displayName != null
                    ? item.peer.displayName : item.peer.username)
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

        binding.swipe.setColorSchemeResources(R.color.gift_accent);
        binding.swipe.setOnChildScrollUpCallback((parent, child) ->
                binding.recycler != null && binding.recycler.canScrollVertically(-1));
        binding.swipe.setOnRefreshListener(() -> {
            viewModel.load(true);
            loadOfficialNewsPreview();
            loadAdminMessagesPreview();
        });

        View.OnClickListener openHub = v -> {
            int tab = 0;
            if (binding.tvRequestBadge != null
                    && binding.tvRequestBadge.getVisibility() == View.VISIBLE) {
                tab = FriendsActivity.TAB_REQ_FRIEND;
            }
            openFriendsTab(tab);
        };
        if (binding.btnFriends != null) {
            binding.btnFriends.setOnClickListener(openHub);
        }
        if (binding.btnMsgMenu != null) {
            binding.btnMsgMenu.setOnClickListener(openHub);
        }
        if (binding.btnRequests != null) {
            binding.btnRequests.setOnClickListener(openHub);
        }
        if (binding.tvRequestBadge != null) {
            binding.tvRequestBadge.setOnClickListener(openHub);
        }

        loadPendingRequestBadge();

        if (binding.officialNewsCardMsg != null) {
            binding.officialNewsCardMsg.getRoot().setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), OfficialNewsActivity.class)));
            loadOfficialNewsPreview();
        }

        if (binding.adminMessagesCardMsg != null) {
            binding.adminMessagesCardMsg.getRoot().setOnClickListener(v ->
                    startActivity(new Intent(requireContext(),
                            com.Dramizo.Series.presentation.notifications.AdminMessagesActivity.class)));
            loadAdminMessagesPreview();
        }

        viewModel.getConversations().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            boolean empty = list == null || list.isEmpty();
            if (binding.tvEmpty != null) {
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }
            binding.swipe.setRefreshing(false);
            if (isAdded()) AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.getError().observe(getViewLifecycleOwner(), e -> {
            if (e != null) Toast.makeText(requireContext(), e, Toast.LENGTH_SHORT).show();
            binding.swipe.setRefreshing(false);
            if (isAdded()) AppLoadingOverlay.hide(requireActivity());
        });
        viewModel.load();
    }

    @Override
    public void onResume() {
        super.onResume();
        applySectionWallpaper();
    }

    /** Applies the shared wallpaper only to the inbox list, never to opened conversations. */
    private void applySectionWallpaper() {
        if (binding == null || !isAdded()) return;
        String sectionWallpaper = com.Dramizo.Series.util.RemoteTheme.backgroundUrl(requireContext(), "chat");
        if (sectionWallpaper == null || sectionWallpaper.trim().isEmpty()) {
            binding.imgThemeBg.setVisibility(View.GONE);
            binding.getRoot().setBackgroundResource(R.drawable.bg_home_full_gradient);
            return;
        }
        binding.getRoot().setBackgroundResource(R.drawable.bg_home_full_gradient);
        binding.imgThemeBg.setVisibility(View.VISIBLE);
        com.Dramizo.Series.util.RemoteTheme.loadInto(
                binding.imgThemeBg, sectionWallpaper, R.drawable.bg_home_full_gradient);
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

    private void loadAdminMessagesPreview() {
        if (binding == null || binding.adminMessagesCardMsg == null || !isAdded()) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.OfficialNewsPreviewDto> r =
                    ApiCall.execute(c.getNotificationApi().adminMessagesPreview());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> applyAdminMessagesPreview(
                    r.success ? r.data : null));
        });
    }

    private void applyAdminMessagesPreview(@Nullable MiscDtos.OfficialNewsPreviewDto p) {
        if (binding == null || binding.adminMessagesCardMsg == null) return;
        String preview = getString(R.string.admin_messages_hint);
        if (p != null) {
            if (p.lastBody != null && !p.lastBody.isEmpty()) preview = p.lastBody;
            else if (p.lastTitle != null && !p.lastTitle.isEmpty()) preview = p.lastTitle;
            binding.adminMessagesCardMsg.tvAdminTime.setText(formatPreviewTime(p.lastAt));
            if (p.unread > 0) {
                binding.adminMessagesCardMsg.tvAdminUnread.setVisibility(View.VISIBLE);
                binding.adminMessagesCardMsg.tvAdminUnread.setText(
                        p.unread > 99 ? "99+" : String.valueOf(p.unread));
            } else {
                binding.adminMessagesCardMsg.tvAdminUnread.setVisibility(View.GONE);
            }
        } else {
            binding.adminMessagesCardMsg.tvAdminTime.setText("");
            binding.adminMessagesCardMsg.tvAdminUnread.setVisibility(View.GONE);
        }
        binding.adminMessagesCardMsg.tvAdminLast.setText(preview);
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
        // Soft reload: keep list visible; force only via pull-to-refresh.
        if (viewModel != null) viewModel.load(false);
        loadPendingRequestBadge();
        loadOfficialNewsPreview();
        loadAdminMessagesPreview();
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
            requireContext().registerReceiver(
                    officialNewsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
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
                // Friend requests first (what users expect from the badge).
                Result<java.util.List<MiscDtos.SocialRequestDto>> friend =
                        ApiCall.execute(c.getUserApi().requests("friend"));
                if (friend.success && friend.data != null) total += friend.data.size();
                for (String type : new String[]{"follow", "relation"}) {
                    Result<java.util.List<MiscDtos.SocialRequestDto>> r =
                            ApiCall.execute(c.getUserApi().requests(type));
                    if (r.success && r.data != null) total += r.data.size();
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
