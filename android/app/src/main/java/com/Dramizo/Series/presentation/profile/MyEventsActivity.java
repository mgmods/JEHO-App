package com.Dramizo.Series.presentation.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.EventDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ActivityMyEventsBinding;
import com.Dramizo.Series.databinding.DialogMyEventFormBinding;
import com.Dramizo.Series.databinding.ItemMyEventBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.ErrorToasts;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.RoomUiHelper;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Profile «فعالياتي»: create / edit / delete hosted plaza events. */
public class MyEventsActivity extends ThemedActivity {
    private ActivityMyEventsBinding binding;
    private final List<EventDtos.EventDto> items = new ArrayList<>();
    private Adapter adapter;
    private boolean loading;
    @Nullable private RoomDtos.RoomDto cachedHostRoom;
    @Nullable private MiscDtos.AgencyDto cachedAgency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMyEventsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.fabCreate.setOnClickListener(v -> promptCreateOrEdit(null));
        liftFabAboveSystemBars();
        adapter = new Adapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);
        binding.swipe.setOnRefreshListener(this::load);
        prefetchHostRoom();
    }

    /** FAB sits outside contentRoot — lift it above gesture/nav bar. */
    private void liftFabAboveSystemBars() {
        if (binding.fabCreate == null) return;
        final int base = Math.round(20 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabCreate, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams raw = v.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;
                lp.setMargins(base, base, base, base + bars.bottom);
                v.setLayoutParams(lp);
            } else {
                v.setTranslationY(-bars.bottom);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.fabCreate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        prefetchHostRoom();
    }

    private void prefetchHostRoom() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            RoomDtos.RoomDto room = findHostRoom(c);
            MiscDtos.AgencyDto agency = null;
            if (room != null && RoomUiHelper.isAgencyRoom(room)) {
                Result<MiscDtos.AgencyMineDto> mine = ApiCall.execute(c.getAgencyApi().mine());
                if (mine.success && mine.data != null && mine.data.agency != null) {
                    agency = mine.data.agency;
                }
            }
            MiscDtos.AgencyDto finalAgency = agency;
            runOnUiThread(() -> {
                cachedHostRoom = room;
                cachedAgency = finalAgency;
            });
        });
    }

    private void load() {
        if (loading) return;
        loading = true;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<EventDtos.EventList> r = ApiCall.execute(c.getEventsApi().list("mine"));
            runOnUiThread(() -> {
                loading = false;
                binding.swipe.setRefreshing(false);
                items.clear();
                if (r.success && r.data != null && r.data.items != null) {
                    items.addAll(r.data.items);
                }
                adapter.notifyDataSetChanged();
                boolean empty = items.isEmpty();
                binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.tvEmpty.setVisibility(View.VISIBLE);
                if (r.error != null) ErrorToasts.show(this, r.error);
            });
        });
    }

    private void promptCreateOrEdit(@Nullable EventDtos.EventDto existing) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        DialogMyEventFormBinding form = DialogMyEventFormBinding.inflate(getLayoutInflater());
        sheet.setContentView(form.getRoot());
        padSheetAboveSystemBars(form.getRoot());

        final String[] selectedTag = {
                existing != null && existing.tag != null ? existing.tag : "party"
        };
        form.tvFormTitle.setText(existing == null ? R.string.event_create : R.string.event_edit);
        if (existing != null) {
            if (existing.title != null) form.etTitle.setText(existing.title);
            if (existing.description != null) form.etDescription.setText(existing.description);
        }
        applyTagChips(form, selectedTag[0]);
        form.chipParty.setOnClickListener(v -> {
            selectedTag[0] = "party";
            applyTagChips(form, selectedTag[0]);
        });
        form.chipGame.setOnClickListener(v -> {
            selectedTag[0] = "game";
            applyTagChips(form, selectedTag[0]);
        });
        form.chipMusic.setOnClickListener(v -> {
            selectedTag[0] = "music";
            applyTagChips(form, selectedTag[0]);
        });

        bindLinkedRoomPreview(form, existing);

        form.btnCancel.setOnClickListener(v -> sheet.dismiss());
        form.btnSave.setOnClickListener(v -> {
            String title = form.etTitle.getText() != null
                    ? form.etTitle.getText().toString().trim() : "";
            if (title.isEmpty()) {
                Toast.makeText(this, R.string.event_create_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            String desc = form.etDescription.getText() != null
                    ? form.etDescription.getText().toString().trim() : "";
            sheet.dismiss();
            if (existing == null) {
                createEvent(title, desc, selectedTag[0]);
            } else {
                updateEvent(existing.id, title, desc, selectedTag[0]);
            }
        });
        sheet.show();
        form.etTitle.requestFocus();
        ViewCompat.requestApplyInsets(form.getRoot());
    }

    /** Keep create/edit sheet actions above the system navigation bar. */
    private void padSheetAboveSystemBars(@NonNull View sheetRoot) {
        final int baseBottom = Math.round(22 * getResources().getDisplayMetrics().density);
        final int left = sheetRoot.getPaddingLeft();
        final int top = sheetRoot.getPaddingTop();
        final int right = sheetRoot.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(sheetRoot, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(left, top, right, baseBottom + bars.bottom);
            return insets;
        });
    }

    private void applyTagChips(DialogMyEventFormBinding form, String tag) {
        boolean party = "party".equalsIgnoreCase(tag);
        boolean game = "game".equalsIgnoreCase(tag);
        boolean music = "music".equalsIgnoreCase(tag);
        form.chipParty.setBackgroundResource(
                party ? R.drawable.bg_event_tab_active : R.drawable.bg_event_tab_inactive);
        form.chipGame.setBackgroundResource(
                game ? R.drawable.bg_event_tab_active : R.drawable.bg_event_tab_inactive);
        form.chipMusic.setBackgroundResource(
                music ? R.drawable.bg_event_tab_active : R.drawable.bg_event_tab_inactive);
        form.chipParty.setTextColor(getColor(party ? android.R.color.white : R.color.text_primary));
        form.chipGame.setTextColor(getColor(game ? android.R.color.white : R.color.text_primary));
        form.chipMusic.setTextColor(getColor(music ? android.R.color.white : R.color.text_primary));
    }

    private void bindLinkedRoomPreview(DialogMyEventFormBinding form,
                                       @Nullable EventDtos.EventDto existing) {
        String roomTitle = null;
        String roomCover = null;
        boolean agency = false;
        String agencyName = null;
        String agencyLogo = null;

        if (existing != null) {
            roomTitle = existing.roomTitle;
            roomCover = firstNonEmpty(existing.roomCoverUrl, existing.coverUrl);
            agency = existing.isAgencyRoom
                    || (existing.agencyId != null && !existing.agencyId.isEmpty());
            agencyName = existing.agencyName;
            agencyLogo = existing.agencyLogoUrl;
        } else if (cachedHostRoom != null) {
            roomTitle = cachedHostRoom.title;
            roomCover = cachedHostRoom.coverUrl;
            agency = RoomUiHelper.isAgencyRoom(cachedHostRoom);
            if (agency && cachedAgency != null) {
                agencyName = cachedAgency.name;
                agencyLogo = cachedAgency.logoUrl;
            }
        }

        if (roomTitle == null || roomTitle.isEmpty()) {
            form.tvLinkedRoomTitle.setText(R.string.event_no_room);
            form.tvLinkedRoomMeta.setText("");
            form.imgLinkedType.setImageResource(R.drawable.ic_room_type_personal_3d);
            form.imgLinkedAgency.setVisibility(View.GONE);
            Glide.with(form.imgLinkedRoom).clear(form.imgLinkedRoom);
            form.imgLinkedRoom.setImageResource(ImagePlaceholder.cover());
            return;
        }

        form.tvLinkedRoomTitle.setText(roomTitle);
        form.imgLinkedType.setImageResource(
                agency ? R.drawable.ic_room_type_agency_3d : R.drawable.ic_room_type_personal_3d);
        if (agency && agencyName != null && !agencyName.isEmpty()) {
            form.tvLinkedRoomMeta.setText(getString(R.string.event_agency_name, agencyName));
        } else {
            form.tvLinkedRoomMeta.setText(
                    agency ? R.string.event_type_agency : R.string.event_type_personal);
        }
        Glide.with(form.imgLinkedRoom)
                .load(AssetCatalog.absoluteUrl(roomCover))
                .placeholder(ImagePlaceholder.cover())
                .error(ImagePlaceholder.cover())
                .centerCrop()
                .into(form.imgLinkedRoom);
        if (agency && agencyLogo != null && !agencyLogo.isEmpty()) {
            form.imgLinkedAgency.setVisibility(View.VISIBLE);
            Glide.with(form.imgLinkedAgency)
                    .load(AssetCatalog.absoluteUrl(agencyLogo))
                    .placeholder(R.drawable.icon_agency)
                    .error(R.drawable.icon_agency)
                    .centerCrop()
                    .into(form.imgLinkedAgency);
        } else {
            form.imgLinkedAgency.setVisibility(View.GONE);
        }
    }

    private void createEvent(String title, String description, String tag) {
        AppContainer c = ContainerProvider.from(this);
        EventDtos.CreateEventRequest body = new EventDtos.CreateEventRequest();
        body.title = title;
        body.description = description.isEmpty() ? null : description;
        body.tag = tag;
        body.isPublic = true;
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = utcFmt();
        body.startAt = fmt.format(new Date(now));
        body.endAt = fmt.format(new Date(now + 2L * 60L * 60L * 1000L));
        c.getIoExecutor().execute(() -> {
            attachMyRoom(c, body);
            Result<EventDtos.EventDto> r = ApiCall.execute(c.getEventsApi().create(body));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, R.string.event_created, Toast.LENGTH_SHORT).show();
                    load();
                } else {
                    ErrorToasts.show(this, r.error != null ? r.error : getString(R.string.event_create));
                }
            });
        });
    }

    private void updateEvent(String id, String title, String description, String tag) {
        AppContainer c = ContainerProvider.from(this);
        EventDtos.CreateEventRequest body = new EventDtos.CreateEventRequest();
        body.title = title;
        body.description = description;
        body.tag = tag;
        c.getIoExecutor().execute(() -> {
            Result<EventDtos.EventDto> r = ApiCall.execute(c.getEventsApi().update(id, body));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, R.string.event_updated, Toast.LENGTH_SHORT).show();
                    load();
                } else {
                    ErrorToasts.show(this, r.error);
                }
            });
        });
    }

    private void deleteEvent(EventDtos.EventDto event) {
        if (event == null || event.id == null) return;
        AuraDialogHelper.confirm(this,
                getString(R.string.event_delete),
                getString(R.string.event_delete_confirm),
                getString(R.string.event_delete),
                () -> {
                    AppContainer c = ContainerProvider.from(this);
                    c.getIoExecutor().execute(() -> {
                        Result<Object> r = ApiCall.execute(c.getEventsApi().delete(event.id));
                        runOnUiThread(() -> {
                            if (r.success) {
                                Toast.makeText(this, R.string.event_deleted, Toast.LENGTH_SHORT).show();
                                load();
                            } else {
                                ErrorToasts.show(this, r.error);
                            }
                        });
                    });
                },
                getString(android.R.string.cancel),
                null);
    }

    @Nullable
    private static RoomDtos.RoomDto findHostRoom(AppContainer c) {
        try {
            Result<MiscDtos.ListResult<RoomDtos.RoomDto>> rooms =
                    ApiCall.execute(c.getRoomApi().list(1, 40));
            String myId = c.getSessionManager().getUserId();
            if (!rooms.success || rooms.data == null || rooms.data.items == null || myId == null) {
                return null;
            }
            RoomDtos.RoomDto personal = null;
            RoomDtos.RoomDto agency = null;
            for (RoomDtos.RoomDto r : rooms.data.items) {
                if (r == null || !myId.equals(r.hostId)) continue;
                if (!RoomUiHelper.isAgencyRoom(r)) {
                    if (personal == null) personal = r;
                } else if (agency == null) {
                    agency = r;
                }
            }
            return personal != null ? personal : agency;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void attachMyRoom(AppContainer c, EventDtos.CreateEventRequest body) {
        RoomDtos.RoomDto room = findHostRoom(c);
        if (room == null) return;
        body.roomId = room.id;
        body.coverUrl = room.coverUrl;
    }

    private static SimpleDateFormat utcFmt() {
        SimpleDateFormat fmt =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt;
    }

    private String statusLabel(String status) {
        if ("live".equalsIgnoreCase(status)) return getString(R.string.event_status_live);
        if ("ended".equalsIgnoreCase(status)) return getString(R.string.event_status_ended);
        return getString(R.string.event_status_upcoming);
    }

    private int statusBackground(String status) {
        if ("live".equalsIgnoreCase(status)) return R.drawable.bg_event_status_live;
        if ("ended".equalsIgnoreCase(status)) return R.drawable.bg_event_status_ended;
        return R.drawable.bg_event_status_upcoming;
    }

    private String tagLabel(String tag) {
        if (tag == null) return getString(R.string.event_tag_party);
        switch (tag.toLowerCase(Locale.US)) {
            case "game": return getString(R.string.event_tag_game);
            case "music": return getString(R.string.event_tag_music);
            case "party":
            default: return getString(R.string.event_tag_party);
        }
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
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(d);
        } catch (Exception ignored) {
            return iso.length() > 16 ? iso.substring(0, 16).replace('T', ' ') : iso;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemMyEventBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            EventDtos.EventDto e = items.get(position);
            h.b.tvTitle.setText(e.title != null ? e.title : "");
            h.b.tvStatus.setText(statusLabel(e.status));
            h.b.tvStatus.setBackgroundResource(statusBackground(e.status));
            h.b.tvTag.setText(tagLabel(e.tag));

            String start = formatIso(e.startAt);
            String end = formatIso(e.endAt);
            String time = start;
            if (!start.isEmpty() && !end.isEmpty()) {
                time = getString(R.string.event_time_range, start, end);
            }
            h.b.tvMeta.setText(
                    (time.isEmpty() ? "" : time + " · ")
                            + getString(R.string.event_subscribers_count,
                            Math.max(0, e.subscribersCount)));

            if (e.description != null && !e.description.isEmpty()) {
                h.b.tvDesc.setVisibility(View.VISIBLE);
                h.b.tvDesc.setText(e.description);
            } else {
                h.b.tvDesc.setVisibility(View.GONE);
            }

            boolean agency = e.isAgencyRoom
                    || (e.agencyId != null && !e.agencyId.isEmpty());
            h.b.imgRoomType.setImageResource(
                    agency ? R.drawable.ic_room_type_agency_3d
                            : R.drawable.ic_room_type_personal_3d);

            String roomTitle = e.roomTitle != null && !e.roomTitle.isEmpty()
                    ? e.roomTitle
                    : getString(R.string.event_no_room);
            h.b.tvRoomTitle.setText(roomTitle);
            if (agency && e.agencyName != null && !e.agencyName.isEmpty()) {
                h.b.tvRoomKind.setText(getString(R.string.event_agency_name, e.agencyName));
            } else {
                h.b.tvRoomKind.setText(
                        agency ? R.string.event_type_agency : R.string.event_type_personal);
            }

            Glide.with(h.b.imgCover)
                    .load(AssetCatalog.absoluteUrl(e.coverUrl))
                    .placeholder(ImagePlaceholder.cover())
                    .error(ImagePlaceholder.cover())
                    .centerCrop()
                    .into(h.b.imgCover);

            String roomCover = firstNonEmpty(e.roomCoverUrl, e.coverUrl);
            Glide.with(h.b.imgRoomCover)
                    .load(AssetCatalog.absoluteUrl(roomCover))
                    .placeholder(ImagePlaceholder.cover())
                    .error(ImagePlaceholder.cover())
                    .centerCrop()
                    .into(h.b.imgRoomCover);

            if (agency && e.agencyLogoUrl != null && !e.agencyLogoUrl.isEmpty()) {
                h.b.imgAgency.setVisibility(View.VISIBLE);
                Glide.with(h.b.imgAgency)
                        .load(AssetCatalog.absoluteUrl(e.agencyLogoUrl))
                        .placeholder(R.drawable.icon_agency)
                        .error(R.drawable.icon_agency)
                        .centerCrop()
                        .into(h.b.imgAgency);
            } else if (agency) {
                h.b.imgAgency.setVisibility(View.VISIBLE);
                h.b.imgAgency.setImageResource(R.drawable.icon_agency);
            } else {
                h.b.imgAgency.setVisibility(View.GONE);
            }

            h.b.btnEdit.setOnClickListener(v -> promptCreateOrEdit(e));
            h.b.btnDelete.setOnClickListener(v -> deleteEvent(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemMyEventBinding b;
            VH(ItemMyEventBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
