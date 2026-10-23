package com.Dramizo.Series.presentation.wallet;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

/** Native daily check-in dialog. Claims remain backed by TasksApi.checkin(). */
public class SignInDialog extends DialogFragment {

    private static final int GREEN = Color.rgb(33, 134, 83);
    private static final int DARK = Color.rgb(35, 45, 40);
    private static final int MUTED = Color.rgb(112, 122, 116);

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
        for (int i = 0; i < 7; i++) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackgroundResource(R.drawable.bg_sign_day_cell);
            card.setPadding(dp(4), dp(8), dp(4), dp(8));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(106);
            lp.columnSpec = GridLayout.spec(i < 4 ? i : i - 4, 1f);
            lp.rowSpec = GridLayout.spec(i < 4 ? 0 : 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(lp);

            TextView day = new TextView(requireContext());
            day.setText("اليوم " + (i + 1));
            day.setTextColor(DARK);
            day.setTextSize(11f);
            day.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            day.setGravity(Gravity.CENTER);

            ImageView reward = new ImageView(requireContext());
            LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            imageLp.topMargin = dp(5);
            imageLp.bottomMargin = dp(4);
            reward.setLayoutParams(imageLp);
            reward.setImageResource(R.drawable.ic_gold_coin);
            reward.setScaleType(ImageView.ScaleType.FIT_CENTER);
            reward.setContentDescription("مكافأة اليوم " + (i + 1));

            TextView rewardLabel = new TextView(requireContext());
            rewardLabel.setText("مكافأة");
            rewardLabel.setTextColor(MUTED);
            rewardLabel.setTextSize(10f);
            rewardLabel.setGravity(Gravity.CENTER);

            card.addView(day);
            card.addView(reward);
            card.addView(rewardLabel);
            grid.addView(card);
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
                        : (reward != null ? reward : "سجّل حضورك يومياً واحصل على مكافأتك"));
                binding.bltvCheckIn.setEnabled(!done);
                binding.bltvCheckIn.setAlpha(1f);
                binding.bltvCheckIn.setText(done ? "تم الاستلام" : "استلام");
                binding.bltvCheckIn.setTextColor(GREEN);
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
                    if (binding != null) {
                        binding.bltvCheckIn.setText("تم الاستلام");
                        binding.bltvCheckIn.setEnabled(false);
                        binding.bltvCheckIn.setTextColor(GREEN);
                    }
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
