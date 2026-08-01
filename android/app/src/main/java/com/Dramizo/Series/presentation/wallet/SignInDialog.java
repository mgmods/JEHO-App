package com.Dramizo.Series.presentation.wallet;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.DialogSignInBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Mikoo SignInDialog — daily check-in via TasksApi.checkin(). */
public class SignInDialog extends DialogFragment {

    public static void show(@Nullable FragmentManager fm) {
        if (fm == null || fm.isStateSaved()) return;
        if (fm.findFragmentByTag("sign_in") != null) return;
        new SignInDialog().show(fm, "sign_in");
    }

    private DialogSignInBinding binding;
    private boolean claiming;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogSignInBinding.inflate(inflater, container, false);
        buildDayCells();
        binding.bltvCheckIn.setOnClickListener(v -> doCheckIn());
        loadHint();
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private void buildDayCells() {
        if (binding == null) return;
        GridLayout grid = binding.gridDays;
        grid.removeAllViews();
        String[] labels = {"يوم 1", "يوم 2", "يوم 3", "يوم 4", "يوم 5", "يوم 6", "يوم 7"};
        for (int i = 0; i < labels.length; i++) {
            TextView cell = new TextView(requireContext());
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(64);
            lp.columnSpec = GridLayout.spec(i % 4, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            cell.setLayoutParams(lp);
            cell.setBackgroundResource(R.drawable.bg_sign_day_cell);
            cell.setGravity(Gravity.CENTER);
            cell.setText(labels[i] + "\n+coins");
            cell.setTextColor(0xFF663300);
            cell.setTextSize(11f);
            grid.addView(cell);
        }
    }

    private void loadHint() {
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.TaskDto>> r = ApiCall.execute(c.getTasksApi().daily());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                boolean done = false;
                String reward = null;
                if (r.success && r.data != null) {
                    for (MiscDtos.TaskDto t : r.data) {
                        if (t == null || t.type == null) continue;
                        if (!t.type.toLowerCase(Locale.US).startsWith("checkin")) continue;
                        done = t.claimed;
                        int rewardAmt = Math.max(t.rewardSilver, t.rewardPoints);
                        if (rewardAmt > 0) {
                            reward = String.format(Locale.US, "+%,d كوينز", rewardAmt);
                        }
                        break;
                    }
                }
                binding.tvSignHint.setText(done
                        ? getString(R.string.check_in_done)
                        : (reward != null ? reward : "سجّل حضورك يومياً واربح كوينز"));
                binding.bltvCheckIn.setEnabled(!done);
                binding.bltvCheckIn.setAlpha(done ? 0.45f : 1f);
                binding.bltvCheckIn.setText(done
                        ? R.string.check_in_done
                        : R.string.check_in_action);
            });
        });
    }

    private void doCheckIn() {
        if (claiming || binding == null) return;
        claiming = true;
        binding.bltvCheckIn.setEnabled(false);
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> r = ApiCall.execute(c.getTasksApi().checkin());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                claiming = false;
                if (r.success) {
                    Toast.makeText(requireContext(), R.string.check_in_done, Toast.LENGTH_SHORT).show();
                    loadHint();
                } else {
                    Toast.makeText(requireContext(),
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    if (binding != null) binding.bltvCheckIn.setEnabled(true);
                }
            });
        });
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
