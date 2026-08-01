package com.Dramizo.Series.presentation.search;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.databinding.ActivitySearchBinding;
import com.Dramizo.Series.databinding.ItemFriendRowBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.FlagImages;
import com.Dramizo.Series.util.RoomCardBinder;
import com.Dramizo.Series.util.RoomUiHelper;
import com.Dramizo.Series.util.VipStyle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class SearchActivity extends ThemedActivity {
    private static final int KIND_USER = 1;
    private static final int KIND_ROOM = 2;

    private static final int FILTER_ALL = 0;
    private static final int FILTER_ROOMS = 1;
    private static final int FILTER_PEOPLE = 2;

    private ActivitySearchBinding binding;
    private final List<Row> allItems = new ArrayList<>();
    private final List<Row> visibleItems = new ArrayList<>();
    private Adapter adapter;
    private AppContainer c;
    private Runnable pendingSearch;
    private int filterMode = FILTER_ALL;
    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        EdgeToEdgeHelper.padBottom(binding.recycler);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnClear.setOnClickListener(v -> {
            binding.etQuery.setText("");
            binding.etQuery.requestFocus();
        });

        c = ContainerProvider.from(this);
        adapter = new Adapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        setupTabs();
        updateEmptyState(true);

        binding.etQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(binding.etQuery.getText() != null
                        ? binding.etQuery.getText().toString().trim() : "");
                hideKeyboard(v);
                return true;
            }
            return false;
        });

        binding.etQuery.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                boolean hasText = s != null && s.length() > 0;
                binding.btnClear.setVisibility(hasText ? View.VISIBLE : View.GONE);
                if (pendingSearch != null) binding.getRoot().removeCallbacks(pendingSearch);
                pendingSearch = () -> runSearch(s != null ? s.toString().trim() : "");
                binding.getRoot().postDelayed(pendingSearch, 280);
            }
        });

        binding.etQuery.requestFocus();
        binding.etQuery.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(binding.etQuery, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    private void setupTabs() {
        binding.tabAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        binding.tabRooms.setOnClickListener(v -> setFilter(FILTER_ROOMS));
        binding.tabPeople.setOnClickListener(v -> setFilter(FILTER_PEOPLE));
        setFilter(FILTER_ALL);
    }

    private void setFilter(int mode) {
        filterMode = mode;
        styleTab(binding.tabAll, mode == FILTER_ALL);
        styleTab(binding.tabRooms, mode == FILTER_ROOMS);
        styleTab(binding.tabPeople, mode == FILTER_PEOPLE);
        applyFilter();
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setSelected(selected);
        tab.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.white : R.color.text_secondary));
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void applyFilter() {
        visibleItems.clear();
        for (Row row : allItems) {
            if (filterMode == FILTER_ROOMS && row.kind != KIND_ROOM) continue;
            if (filterMode == FILTER_PEOPLE && row.kind != KIND_USER) continue;
            visibleItems.add(row);
        }
        adapter.notifyDataSetChanged();
        updateEmptyState(lastQuery.isEmpty());
    }

    private void updateEmptyState(boolean noQuery) {
        boolean empty = visibleItems.isEmpty();
        binding.emptyWrap.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            binding.tvEmpty.setText(noQuery
                    ? getString(R.string.search_empty_hint)
                    : getString(R.string.search_no_results));
        }
    }

    private void runSearch(String q) {
        lastQuery = q;
        if (q.length() < 1) {
            allItems.clear();
            applyFilter();
            return;
        }
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<AuthDtos.UserDto>> users = ApiCall.execute(c.getUserApi().search(q, 1));
            Result<MiscDtos.ListResult<RoomDtos.RoomDto>> rooms =
                    ApiCall.execute(c.getRoomApi().list(1, q));

            List<Row> next = new ArrayList<>();
            LinkedHashSet<String> seenRooms = new LinkedHashSet<>();
            if (rooms.success && rooms.data != null && rooms.data.items != null) {
                for (RoomDtos.RoomDto r : rooms.data.items) {
                    if (r == null || r.id == null || !seenRooms.add(r.id)) continue;
                    Row row = new Row();
                    row.kind = KIND_ROOM;
                    row.room = r;
                    row.title = r.title != null && !r.title.isEmpty() ? r.title : "غرفة";
                    String typeLabel = RoomUiHelper.isAgencyRoom(r)
                            ? getString(R.string.room_badge_agency)
                            : getString(R.string.room_badge_personal);
                    String roomId = RoomUiHelper.displayRoomId(r);
                    row.subtitle = "غرفة · " + typeLabel + " · ID " + roomId
                            + " · " + Math.max(0, r.viewerCount) + " متواجد"
                            + (r.hasPassword ? " · 🔒" : "");
                    String cover = RoomCardBinder.isGenericServerCoverForList(r.coverUrl)
                            && r.host != null && r.host.avatarUrl != null
                            ? r.host.avatarUrl : r.coverUrl;
                    row.imageUrl = cover;
                    next.add(row);
                }
            }
            if (users.success && users.data != null && users.data.items != null) {
                for (AuthDtos.UserDto u : users.data.items) {
                    if (u == null) continue;
                    Row row = new Row();
                    row.kind = KIND_USER;
                    row.user = u;
                    row.title = u.displayName != null && !u.displayName.isEmpty()
                            ? u.displayName : u.username;
                    String pid = u.displayPublicId();
                    row.subtitle = "شخص · ID: " + (pid.isEmpty() ? "—" : pid);
                    row.imageUrl = u.avatarUrl;
                    next.add(row);
                }
            }

            String err = users.error != null ? users.error : rooms.error;
            runOnUiThread(() -> {
                allItems.clear();
                allItems.addAll(next);
                applyFilter();
                if (err != null && allItems.isEmpty()) {
                    Toast.makeText(SearchActivity.this, err, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void openRow(Row row) {
        if (row.kind == KIND_USER && row.user != null) {
            Intent i = new Intent(this, ProfileActivity.class);
            i.putExtra(ProfileActivity.EXTRA_USER_ID, row.user.id);
            startActivity(i);
        } else if (row.kind == KIND_ROOM && row.room != null) {
            openRoom(row.room);
        }
    }

    private void openRoom(RoomDtos.RoomDto room) {
        if (room.hasPassword) {
            AuraDialogHelper.prompt(this,
                    getString(R.string.room_locked_title),
                    null,
                    getString(R.string.room_password_hint),
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    getString(R.string.enter_room),
                    pwd -> {
                        Intent i = new Intent(this, VoiceRoomActivity.class);
                        i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, room.id);
                        if (pwd != null && !pwd.isEmpty()) {
                            i.putExtra(VoiceRoomActivity.EXTRA_PASSWORD, pwd);
                        }
                        startActivity(i);
                    });
            return;
        }
        Intent i = new Intent(this, VoiceRoomActivity.class);
        i.putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, room.id);
        startActivity(i);
    }

    private void hideKeyboard(View view) {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private static class Row {
        int kind;
        String title;
        String subtitle;
        String imageUrl;
        AuthDtos.UserDto user;
        RoomDtos.RoomDto room;
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemFriendRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Row row = visibleItems.get(position);
            h.b.tvName.setText(row.title != null ? row.title : "");
            h.b.tvId.setText(row.subtitle != null ? row.subtitle : "");
            if (row.user != null) {
                AvatarCosmetics.bindWear(h.b.imgAvatar, h.b.imgFrame, row.user);
                FlagImages.bind(h.b.imgCountryFlag, row.user.country);
                bindBadges(h, row.user);
            } else {
                AvatarCosmetics.bindAvatar(h.b.imgAvatar, AssetCatalog.absoluteUrl(row.imageUrl));
                AvatarCosmetics.applyFrame(h.b.imgFrame, null);
                FlagImages.bind(h.b.imgCountryFlag, row.room != null && row.room.host != null
                        ? row.room.host.country : null);
                hideBadges(h);
            }
            h.itemView.setOnClickListener(v -> openRow(row));
        }

        private void bindBadges(VH h, AuthDtos.UserDto user) {
            if (h.b.rowBadges == null) return;
            int vip = Math.max(0, user.vipLevel);
            int level = Math.max(1, user.level);
            if (h.b.tvVipChip != null) {
                if (vip > 0) {
                    h.b.tvVipChip.setVisibility(View.VISIBLE);
                    h.b.tvVipChip.setText("VIP" + vip);
                    VipStyle.applyChip(h.b.tvVipChip, vip);
                } else {
                    h.b.tvVipChip.setVisibility(View.GONE);
                }
            }
            if (h.b.tvLevelChip != null) {
                h.b.tvLevelChip.setVisibility(View.VISIBLE);
                h.b.tvLevelChip.setText("Lv." + level);
            }
            if (h.b.tvWealthChip != null) h.b.tvWealthChip.setVisibility(View.GONE);
            if (h.b.tvCharmChip != null) h.b.tvCharmChip.setVisibility(View.GONE);
        }

        private void hideBadges(VH h) {
            if (h.b.tvVipChip != null) h.b.tvVipChip.setVisibility(View.GONE);
            if (h.b.tvLevelChip != null) h.b.tvLevelChip.setVisibility(View.GONE);
            if (h.b.tvWealthChip != null) h.b.tvWealthChip.setVisibility(View.GONE);
            if (h.b.tvCharmChip != null) h.b.tvCharmChip.setVisibility(View.GONE);
        }

        @Override public int getItemCount() { return visibleItems.size(); }

        class VH extends RecyclerView.ViewHolder {
            final ItemFriendRowBinding b;
            VH(ItemFriendRowBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
