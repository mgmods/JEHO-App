package com.Dramizo.Series.presentation.voiceroom;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.api.RoomCupApi;
import com.Dramizo.Series.data.remote.dto.RoomCupDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.home.RoomCupAdapter;
import com.Dramizo.Series.util.ApiCall;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Collections;
import java.util.List;

/** Mikoo-style room cup leaderboard. */
public class RoomCupSheet extends BottomSheetDialogFragment {
    private static final String TAG = "room_cup";

    public static void showSheet(@NonNull androidx.fragment.app.FragmentManager fm) {
        if (fm.isStateSaved()) return;
        new RoomCupSheet().show(fm, TAG);
    }

    @Override
    public int getTheme() {
        return R.style.Theme_AuraLive_BottomSheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_room_cup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView season = view.findViewById(R.id.tvCupSeason);
        ProgressBar progress = view.findViewById(R.id.progressCup);
        TextView empty = view.findViewById(R.id.tvCupEmpty);
        RecyclerView recycler = view.findViewById(R.id.recyclerCup);
        RoomCupAdapter adapter = new RoomCupAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        progress.setVisibility(View.VISIBLE);
        ContainerProvider.from(requireActivity()).getIoExecutor().execute(() -> {
            RoomCupApi api = ContainerProvider.from(requireActivity()).getRoomCupApi();
            Result<RoomCupDtos.Leaderboard> r = ApiCall.execute(api.leaderboard());
            RoomCupDtos.Leaderboard board = r.success ? r.data : null;
            List<RoomCupDtos.Item> items = board != null && board.items != null
                    ? board.items : Collections.emptyList();
            String seasonText = board != null
                    ? ((board.period != null ? board.period : "")
                    + (board.seasonKey != null ? " · " + board.seasonKey : ""))
                    : "";
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                season.setText(seasonText.trim());
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                recycler.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.submit(items);
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
            lp.height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.65f);
            bottom.setLayoutParams(lp);
        }
    }
}
