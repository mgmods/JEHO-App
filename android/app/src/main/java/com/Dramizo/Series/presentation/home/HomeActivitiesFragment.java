package com.Dramizo.Series.presentation.home;

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
import com.Dramizo.Series.data.remote.dto.EventDtos;
import com.Dramizo.Series.databinding.FragmentHomeActivitiesBinding;
import com.Dramizo.Series.databinding.ItemPlazaEventBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AvatarImageLoader;
import com.Dramizo.Series.util.ErrorToasts;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Home Activities tab: public plaza events only (manage yours from Profile). */
public class HomeActivitiesFragment extends Fragment {
    public static final int TAB_ACTIVITIES = 3;

    private FragmentHomeActivitiesBinding binding;
    private final List<EventDtos.EventDto> items = new ArrayList<>();
    private Adapter adapter;
    private boolean loading;
    private long lastLoadAtMs;

    public static HomeActivitiesFragment newInstance() {
        return new HomeActivitiesFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeActivitiesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new Adapter();
        binding.recyclerEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerEvents.setAdapter(adapter);
        binding.recyclerEvents.setNestedScrollingEnabled(true);
        // Outer HomeFragment SwipeRefresh owns pull-to-refresh — nested SRL causes flip/jitter.
        binding.swipeEvents.setEnabled(false);
        binding.swipeEvents.setOnRefreshListener(null);
        notifyHostSized();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (System.currentTimeMillis() - lastLoadAtMs > 15_000L || items.isEmpty()) {
            load();
        }
    }

    /** Called from HomeFragment pull-to-refresh. */
    public void reloadFromHost() {
        load();
    }

    public boolean canScrollListUp() {
        return binding != null
                && binding.recyclerEvents != null
                && binding.recyclerEvents.canScrollVertically(-1);
    }

    private void load() {
        if (binding == null || loading) return;
        loading = true;
        lastLoadAtMs = System.currentTimeMillis();
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<EventDtos.EventList> r = ApiCall.execute(c.getEventsApi().list("square"));
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                loading = false;
                if (binding == null) return;
                binding.swipeEvents.setRefreshing(false);
                items.clear();
                if (r.success && r.data != null && r.data.items != null) {
                    items.addAll(r.data.items);
                }
                adapter.notifyDataSetChanged();
                View emptyView = binding.tvEmptyEvents;
                if (emptyView != null) {
                    emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                }
                if (r.error != null) ErrorToasts.show(requireContext(), r.error);
                notifyHostSized();
            });
        });
    }

    private void toggleSubscribe(EventDtos.EventDto event) {
        if (event == null || event.id == null) return;
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<EventDtos.EventDto> r = ApiCall.execute(
                    event.subscribed
                            ? c.getEventsApi().unsubscribe(event.id)
                            : c.getEventsApi().subscribe(event.id));
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.success && r.data != null) {
                    for (int i = 0; i < items.size(); i++) {
                        if (event.id.equals(items.get(i).id)) {
                            items.set(i, r.data);
                            adapter.notifyItemChanged(i);
                            break;
                        }
                    }
                } else if (r.error != null) {
                    ErrorToasts.show(requireContext(), r.error);
                }
            });
        });
    }

    private void openEvent(EventDtos.EventDto event) {
        if (event == null) return;
        if (event.roomId != null && !event.roomId.isEmpty()) {
            Intent i = new Intent(requireContext(), VoiceRoomActivity.class);
            i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, event.roomId);
            startActivity(i);
            return;
        }
        Toast.makeText(requireContext(),
                event.description != null ? event.description : event.title,
                Toast.LENGTH_SHORT).show();
    }

    private void notifyHostSized() {
        Fragment parent = getParentFragment();
        if (parent instanceof HomeFragment) {
            ((HomeFragment) parent).onFeedPageEmpty(TAB_ACTIVITIES, false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemPlazaEventBinding b = ItemPlazaEventBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new VH(b);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            EventDtos.EventDto e = items.get(position);
            h.b.tvEventTitle.setText(e.title != null ? e.title : "");
            h.b.tvEventTag.setText(tagLabel(e));
            h.b.tvEventTime.setText(formatTime(e));
            h.b.tvEventMeta.setText(metaLine(e));

            // Highlight agency-opening events (Mikoo ceremony vibe).
            boolean agencyOpen = e.isAgencyRoom
                    || (e.tag != null && e.tag.toLowerCase(Locale.US).contains("agency"));
            try {
                h.b.tvEventTag.setBackgroundResource(
                        agencyOpen ? R.drawable.bg_event_tag_agency : R.drawable.bg_event_tag_pill);
            } catch (Exception ignored) {
                // drawable missing on older skins
            }

            String cover = e.coverUrl;
            if ((cover == null || cover.isEmpty()) && e.agencyLogoUrl != null) {
                cover = e.agencyLogoUrl;
            }
            if ((cover == null || cover.isEmpty()) && e.roomCoverUrl != null) {
                cover = e.roomCoverUrl;
            }
            Glide.with(h.b.imgEventCover)
                    .load(AssetCatalog.absoluteUrl(cover))
                    .placeholder(ImagePlaceholder.cover())
                    .error(ImagePlaceholder.cover())
                    .centerCrop()
                    .into(h.b.imgEventCover);

            String avatar = e.host != null ? e.host.avatarUrl : null;
            if ((avatar == null || avatar.isEmpty()) && e.agencyLogoUrl != null) {
                avatar = e.agencyLogoUrl;
            }
            AvatarImageLoader.load(h.b.imgHostAvatar, avatar);

            boolean sub = e.subscribed;
            h.b.btnSubscribe.setText(sub ? R.string.event_subscribed : R.string.event_subscribe);
            h.b.btnSubscribe.setAlpha(sub ? 0.7f : 1f);
            h.b.btnSubscribe.setOnClickListener(v -> toggleSubscribe(e));
            h.itemView.setOnClickListener(v -> openEvent(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemPlazaEventBinding b;
            VH(ItemPlazaEventBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    private String tagLabel(EventDtos.EventDto e) {
        if (e != null && e.isAgencyRoom) {
            return getString(R.string.event_tag_agency_open);
        }
        String tag = e != null ? e.tag : null;
        if (tag == null) return getString(R.string.event_tag_party);
        switch (tag.toLowerCase(Locale.US)) {
            case "game": return getString(R.string.event_tag_game);
            case "music": return getString(R.string.event_tag_music);
            case "agency":
            case "agency_open":
            case "open_agency":
                return getString(R.string.event_tag_agency_open);
            case "party":
            default: return getString(R.string.event_tag_party);
        }
    }

    private String metaLine(EventDtos.EventDto e) {
        if (e.isAgencyRoom && e.agencyName != null && !e.agencyName.isEmpty()) {
            return e.agencyName + " · " + getString(R.string.event_subscribers_count,
                    Math.max(0, e.subscribersCount));
        }
        String host = "";
        if (e.host != null) {
            if (e.host.displayName != null && !e.host.displayName.isEmpty()) host = e.host.displayName;
            else if (e.host.username != null) host = e.host.username;
        }
        String room = e.roomTitle != null ? e.roomTitle : "";
        String left = !room.isEmpty() ? room : host;
        if (left.isEmpty()) left = statusLabel(e.status);
        return left + " · " + getString(R.string.event_subscribers_count, Math.max(0, e.subscribersCount));
    }

    private String statusLabel(String status) {
        if ("live".equalsIgnoreCase(status)) return getString(R.string.event_status_live);
        if ("ended".equalsIgnoreCase(status)) return getString(R.string.event_status_ended);
        return getString(R.string.event_status_upcoming);
    }

    private String formatTime(EventDtos.EventDto e) {
        String status = statusLabel(e.status);
        String start = formatIso(e.startAt);
        if (start.isEmpty()) return status;
        return status + " · " + start;
    }

    private static String formatIso(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            String cleaned = iso.replace("Z", "");
            int dot = cleaned.indexOf('.');
            if (dot > 0) cleaned = cleaned.substring(0, dot);
            if (cleaned.length() > 19) cleaned = cleaned.substring(0, 19);
            Date d = in.parse(cleaned);
            if (d == null) return iso;
            SimpleDateFormat out = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            return out.format(d);
        } catch (Exception ignored) {
            return iso.length() > 16 ? iso.substring(0, 16).replace('T', ' ') : iso;
        }
    }
}
