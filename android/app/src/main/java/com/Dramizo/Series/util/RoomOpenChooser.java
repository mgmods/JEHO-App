package com.Dramizo.Series.util;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.DialogRoomOpenChooserBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * For eligible agency hosts: choose personal My Room vs agency room.
 * Everyone else opens personal My Room directly.
 */
public final class RoomOpenChooser {
    private RoomOpenChooser() {}

    public static void open(@NonNull Activity activity) {
        AppContainer c = ContainerProvider.from(activity);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyMineDto> result = c.getAgencyRepository().mine();
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                MiscDtos.AgencyMineDto mine = result.success ? result.data : null;
                if (mine != null && mine.isEligibleHost()) {
                    showChooser(activity, mine);
                } else {
                    MyRoomLauncher.open(activity);
                }
            });
        });
    }

    /** Use when {@code agencies/mine} is already loaded (e.g. Agency hub). */
    public static void openWithMine(
            @NonNull Activity activity,
            @Nullable MiscDtos.AgencyMineDto mine
    ) {
        if (mine != null && mine.isEligibleHost()) {
            showChooser(activity, mine);
        } else {
            MyRoomLauncher.open(activity);
        }
    }

    public static void showChooser(
            @NonNull Activity activity,
            @NonNull MiscDtos.AgencyMineDto mine
    ) {
        if (mine.agency == null || mine.agency.id == null || mine.agency.id.isEmpty()) {
            MyRoomLauncher.open(activity);
            return;
        }
        BottomSheetDialog sheet = AuraDialogHelper.bottomSheet(activity);
        DialogRoomOpenChooserBinding form =
                DialogRoomOpenChooserBinding.inflate(LayoutInflater.from(activity));
        String agencyName = mine.agency.name != null && !mine.agency.name.isEmpty()
                ? mine.agency.name
                : activity.getString(R.string.agency);
        form.tvChooserSubtitle.setText(
                activity.getString(R.string.room_open_chooser_subtitle_format, agencyName));

        form.btnOpenAgencyRoom.setOnClickListener(v -> {
            sheet.dismiss();
            AgencyRoomLauncher.open(activity, mine.agency.id, mine.agency.name);
        });
        form.btnOpenPersonalRoom.setOnClickListener(v -> {
            sheet.dismiss();
            MyRoomLauncher.open(activity);
        });
        form.btnChooserCancel.setOnClickListener(v -> sheet.dismiss());

        AuraDialogHelper.applyContent(form.getRoot());
        sheet.setContentView(form.getRoot());
        try {
            sheet.show();
        } catch (Exception e) {
            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show();
            MyRoomLauncher.open(activity);
        }
    }
}
