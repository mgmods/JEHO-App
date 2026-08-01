package com.Dramizo.Series.presentation.voiceroom;

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
import com.Dramizo.Series.data.remote.api.RoomCupApi;
import com.Dramizo.Series.data.remote.dto.RoomCupDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.home.RoomCupAdapter;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarImageLoader;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Mikoo RoomHourRankingNewDialog:
 * - Room Rank (كأس الروم)
 * - This Rank: ثروة / جاذبية × يومي / أسبوعي / شهري
 */
public class RoomContributeSheet extends BottomSheetDialogFragment {
    private static final String TAG = "room_contribute";
    private static final String ARG_ROOM = "room_id";

    private String roomId;
    private View pageRoomRank;
    private View pageThisRank;
    private ImageView tabRoomRank;
    private ImageView tabThisRank;
    private TextView tabWealth;
    private TextView tabCharm;
    private TextView tabPeriodDay;
    private TextView tabPeriodWeek;
    private TextView tabPeriodMonth;
    private ProgressBar progressContribute;
    private ProgressBar progressRoomCup;
    private TextView emptyContribute;
    private TextView emptyRoomCup;
    private RecyclerView recyclerContribute;
    private RecyclerView recyclerRoomCup;
    private ContributeAdapter contributeAdapter;
    private RoomCupAdapter cupAdapter;

    private String category = "wealth";
    private String period = "day";
    private boolean showingThisRank = true;

    public static void showSheet(@NonNull androidx.fragment.app.FragmentManager fm,
                                 @Nullable String roomId) {
        if (fm.isStateSaved() || roomId == null || roomId.isEmpty()) return;
        RoomContributeSheet sheet = new RoomContributeSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM, roomId);
        sheet.setArguments(args);
        sheet.show(fm, TAG);
    }

    @Override
    public int getTheme() {
        return R.style.Theme_AuraLive_BottomSheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_room_contribute, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        roomId = getArguments() != null ? getArguments().getString(ARG_ROOM) : null;

        pageRoomRank = view.findViewById(R.id.pageRoomRank);
        pageThisRank = view.findViewById(R.id.pageThisRank);
        tabRoomRank = view.findViewById(R.id.tabRoomRank);
        tabThisRank = view.findViewById(R.id.tabThisRank);
        tabWealth = view.findViewById(R.id.tabWealth);
        tabCharm = view.findViewById(R.id.tabCharm);
        tabPeriodDay = view.findViewById(R.id.tabPeriodDay);
        tabPeriodWeek = view.findViewById(R.id.tabPeriodWeek);
        tabPeriodMonth = view.findViewById(R.id.tabPeriodMonth);
        progressContribute = view.findViewById(R.id.progressContribute);
        progressRoomCup = view.findViewById(R.id.progressRoomCup);
        emptyContribute = view.findViewById(R.id.tvContributeEmpty);
        emptyRoomCup = view.findViewById(R.id.tvRoomCupEmpty);
        recyclerContribute = view.findViewById(R.id.recyclerContribute);
        recyclerRoomCup = view.findViewById(R.id.recyclerRoomCup);

        contributeAdapter = new ContributeAdapter(item -> {
            if (getActivity() instanceof VoiceRoomActivity room) {
                room.showUserCardFromContribute(item.userId, item.displayName, item.avatarUrl);
            }
        });
        recyclerContribute.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerContribute.setAdapter(contributeAdapter);

        cupAdapter = new RoomCupAdapter();
        recyclerRoomCup.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerRoomCup.setAdapter(cupAdapter);

        tabRoomRank.setOnClickListener(v -> selectOuterPage(false));
        tabThisRank.setOnClickListener(v -> selectOuterPage(true));
        tabWealth.setOnClickListener(v -> {
            category = "wealth";
            refreshCategoryUi();
            loadContribute();
        });
        tabCharm.setOnClickListener(v -> {
            category = "charm";
            refreshCategoryUi();
            loadContribute();
        });
        tabPeriodDay.setOnClickListener(v -> selectPeriod("day"));
        tabPeriodWeek.setOnClickListener(v -> selectPeriod("week"));
        tabPeriodMonth.setOnClickListener(v -> selectPeriod("month"));

        selectOuterPage(true);
        refreshCategoryUi();
        refreshPeriodUi();
        loadContribute();
    }

    private void selectOuterPage(boolean thisRank) {
        showingThisRank = thisRank;
        pageThisRank.setVisibility(thisRank ? View.VISIBLE : View.GONE);
        pageRoomRank.setVisibility(thisRank ? View.GONE : View.VISIBLE);
        // Mikoo: selected = *_p (gold), unselected = *_c (gray)
        tabRoomRank.setImageResource(thisRank ? R.drawable.hr_t1_c : R.drawable.hr_t1_p);
        tabThisRank.setImageResource(thisRank ? R.drawable.hr_t2_p : R.drawable.hr_t2_c);
        if (!thisRank) loadRoomCup();
    }

    private void selectPeriod(String p) {
        period = p;
        refreshPeriodUi();
        loadContribute();
    }

    private void refreshCategoryUi() {
        boolean wealth = "wealth".equals(category);
        tabWealth.setTextColor(wealth ? 0xFFFFFFFF : 0x66FFFFFF);
        tabCharm.setTextColor(wealth ? 0x66FFFFFF : 0xFFFFFFFF);
        tabWealth.setTypeface(null, wealth ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tabCharm.setTypeface(null, wealth ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
    }

    private void refreshPeriodUi() {
        stylePeriod(tabPeriodDay, "day".equals(period));
        stylePeriod(tabPeriodWeek, "week".equals(period));
        stylePeriod(tabPeriodMonth, "month".equals(period));
    }

    private void stylePeriod(TextView tv, boolean on) {
        tv.setBackgroundResource(on ? R.drawable.bg_contribute_period_on : 0);
        tv.setTextColor(on ? 0xFFFFFFFF : 0x66FFFFFF);
    }

    private void loadContribute() {
        if (roomId == null || roomId.isEmpty()) return;
        progressContribute.setVisibility(View.VISIBLE);
        emptyContribute.setVisibility(View.GONE);
        String type = category;
        String periodQ = period;
        ContainerProvider.from(requireActivity()).getIoExecutor().execute(() -> {
            Result<RoomDtos.ContributeResult> r = ApiCall.execute(
                    ContainerProvider.from(requireActivity())
                            .getRoomApi()
                            .contribute(roomId, type, periodQ, 50));
            List<RoomDtos.SupporterDto> items = r.success && r.data != null && r.data.items != null
                    ? r.data.items : Collections.emptyList();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                progressContribute.setVisibility(View.GONE);
                emptyContribute.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerContribute.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                contributeAdapter.submit(items);
            });
        });
    }

    private void loadRoomCup() {
        progressRoomCup.setVisibility(View.VISIBLE);
        emptyRoomCup.setVisibility(View.GONE);
        ContainerProvider.from(requireActivity()).getIoExecutor().execute(() -> {
            RoomCupApi api = ContainerProvider.from(requireActivity()).getRoomCupApi();
            Result<RoomCupDtos.Leaderboard> r = ApiCall.execute(api.leaderboard());
            RoomCupDtos.Leaderboard board = r.success ? r.data : null;
            List<RoomCupDtos.Item> items = board != null && board.items != null
                    ? board.items : Collections.emptyList();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                progressRoomCup.setVisibility(View.GONE);
                emptyRoomCup.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerRoomCup.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                cupAdapter.submit(items);
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

    static final class ContributeAdapter extends RecyclerView.Adapter<ContributeAdapter.VH> {
        interface Listener { void onClick(RoomDtos.SupporterDto item); }
        private final List<RoomDtos.SupporterDto> items = new ArrayList<>();
        private final Listener listener;
        ContributeAdapter(Listener listener) { this.listener = listener; }
        void submit(List<RoomDtos.SupporterDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room_contribute, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            RoomDtos.SupporterDto item = items.get(position);
            h.rank.setText(String.valueOf(item.rank > 0 ? item.rank : position + 1));
            h.nick.setText(item.displayName != null ? item.displayName : "—");
            h.score.setText(formatScore(item.score));
            AvatarImageLoader.load(h.avatar, item.avatarUrl);
            h.itemView.setOnClickListener(v -> listener.onClick(item));
        }
        @Override public int getItemCount() { return items.size(); }
        static String formatScore(long v) {
            if (v >= 1_000_000_000L) return String.format(Locale.US, "%.1fB", v / 1_000_000_000f);
            if (v >= 1_000_000L) return String.format(Locale.US, "%.1fM", v / 1_000_000f);
            if (v >= 1_000L) return String.format(Locale.US, "%.1fK", v / 1_000f);
            return String.valueOf(Math.max(0L, v));
        }
        static final class VH extends RecyclerView.ViewHolder {
            final TextView rank, nick, score;
            final ImageView avatar;
            VH(View v) {
                super(v);
                rank = v.findViewById(R.id.tvRank);
                nick = v.findViewById(R.id.tvNick);
                score = v.findViewById(R.id.tvScore);
                avatar = v.findViewById(R.id.imgAvatar);
            }
        }
    }
}
