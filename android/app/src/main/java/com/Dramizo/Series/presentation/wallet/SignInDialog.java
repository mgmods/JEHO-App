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

import com.bumptech.glide.Glide;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.DialogSignInBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native seven-day login rewards; configuration and claims are server-backed. */
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
    private final List<ImageView> rewardImages = new ArrayList<>();
    private final List<TextView> rewardLabels = new ArrayList<>();
    private final List<TextView> dayLabels = new ArrayList<>();
    private MiscDtos.DailyLoginStatusDto status;

    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = DialogSignInBinding.inflate(inflater, container, false);
        buildDayCells();
        binding.bltvCheckIn.setOnClickListener(v -> claimToday());
        loadRewards();
        return binding.getRoot();
    }

    @Override public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            d.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private void buildDayCells() {
        if (binding == null) return;
        GridLayout grid = binding.gridDays;
        grid.removeAllViews();
        rewardImages.clear(); rewardLabels.clear(); dayLabels.clear();
        for (int i = 0; i < 7; i++) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackgroundResource(R.drawable.bg_sign_day_cell);
            card.setPadding(dp(4), dp(7), dp(4), dp(7));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = dp(98);
            lp.columnSpec = GridLayout.spec(i < 4 ? i : i - 4, 1f);
            lp.rowSpec = GridLayout.spec(i < 4 ? 0 : 1);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(lp);

            TextView day = new TextView(requireContext());
            day.setText("اليوم " + (i + 1)); day.setTextColor(DARK); day.setTextSize(11f);
            day.setTypeface(Typeface.DEFAULT, Typeface.BOLD); day.setGravity(Gravity.CENTER);
            ImageView image = new ImageView(requireContext());
            image.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
            image.setImageResource(R.drawable.ic_gold_coin);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("صورة مكافأة اليوم " + (i + 1));
            TextView label = new TextView(requireContext());
            label.setText("عملات"); label.setTextColor(MUTED); label.setTextSize(10f);
            label.setGravity(Gravity.CENTER); label.setMaxLines(2);
            card.addView(day); card.addView(image); card.addView(label);
            grid.addView(card);
            dayLabels.add(day); rewardImages.add(image); rewardLabels.add(label);
        }
    }

    private void loadRewards() {
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.DailyLoginStatusDto> r = ApiCall.execute(c.getTasksApi().dailyLoginStatus());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (!r.success || r.data == null) {
                    binding.tvSignHint.setText("تعذر تحميل المكافآت. تحقق من اتصالك وحاول مجددًا.");
                    binding.bltvCheckIn.setEnabled(true);
                    return;
                }
                status = r.data;
                if (!status.enabled) {
                    binding.tvSignHint.setText("مكافآت تسجيل الدخول متوقفة حاليًا");
                    binding.bltvCheckIn.setEnabled(false);
                    binding.bltvCheckIn.setText("غير متاح");
                    return;
                }
                List<MiscDtos.DailyLoginRewardDto> days = status.days;
                for (int i = 0; i < 7; i++) {
                    MiscDtos.DailyLoginRewardDto day = days != null && i < days.size() ? days.get(i) : null;
                    ImageView image = rewardImages.get(i);
                    TextView label = rewardLabels.get(i);
                    if (day != null && "cosmetic".equalsIgnoreCase(day.type)) {
                        String url = nonEmpty(day.previewUrl) ? day.previewUrl : day.animationUrl;
                        // The selected catalogue item supplies its own artwork URL (frame, entry effect, etc.).
                        if (nonEmpty(url)) {
                            Glide.with(this).load(url).placeholder(R.drawable.ic_gold_coin)
                                    .error(R.drawable.ic_gold_coin).into(image);
                        } else image.setImageResource(R.drawable.ic_gold_coin);
                        label.setText(nonEmpty(day.name) ? day.name : (nonEmpty(day.label) ? day.label : "عنصر تجميلي"));
                        if (day.durationDays > 0) label.setText(label.getText() + "\n" + day.durationDays + " يوم");
                    } else {
                        image.setImageResource(R.drawable.ic_gold_coin);
                        long amount = day == null ? 0 : day.amount;
                        label.setText(nonEmpty(day == null ? null : day.label) ? day.label : String.format(Locale.getDefault(), "%,d كوينز", amount));
                    }
                    dayLabels.get(i).setText("اليوم " + (i + 1) + (day != null && day.claimed ? " ✓" : ""));
                    if (day != null && day.day == status.currentDay) {
                        dayLabels.get(i).setTextColor(GREEN);
                    } else dayLabels.get(i).setTextColor(DARK);
                }
                MiscDtos.DailyLoginRewardDto reward = status.reward;
                String summary = reward == null ? "سجّل حضورك اليومي واحصل على مكافأتك"
                        : ("cosmetic".equalsIgnoreCase(reward.type)
                        ? (nonEmpty(reward.name) ? reward.name : "مكافأة تجميلية") + (reward.durationDays > 0 ? " · " + reward.durationDays + " يوم" : "")
                        : String.format(Locale.getDefault(), "+%,d كوينز", reward.amount));
                binding.tvSignHint.setText(status.alreadyClaimed ? "تم استلام مكافأة اليوم بنجاح" : "مكافأة اليوم " + status.currentDay + ": " + summary);
                binding.bltvCheckIn.setEnabled(!status.alreadyClaimed && status.shouldShow);
                binding.bltvCheckIn.setText(status.alreadyClaimed ? "تم الاستلام" : "استلام");
                binding.bltvCheckIn.setTextColor(GREEN);
            });
        });
    }

    private void claimToday() {
        if (claiming || binding == null || (status != null && (status.alreadyClaimed || !status.enabled))) return;
        claiming = true;
        binding.bltvCheckIn.setEnabled(false);
        AppContainer c = ContainerProvider.from(requireActivity());
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> r = ApiCall.execute(c.getTasksApi().claimDailyLogin());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                claiming = false;
                if (r.success) {
                    if (binding != null) {
                        binding.bltvCheckIn.setText("تم الاستلام");
                        binding.bltvCheckIn.setEnabled(false);
                        binding.bltvCheckIn.setTextColor(GREEN);
                    }
                    Toast.makeText(requireContext(), "تم الاستلام بنجاح", Toast.LENGTH_SHORT).show();
                    loadRewards();
                } else {
                    Toast.makeText(requireContext(), r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();
                    if (binding != null) binding.bltvCheckIn.setEnabled(true);
                }
            });
        });
    }

    private boolean nonEmpty(String value) { return value != null && !value.trim().isEmpty(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        rewardImages.clear(); rewardLabels.clear(); dayLabels.clear();
    }
}
