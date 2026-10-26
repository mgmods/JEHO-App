package com.Dramizo.Series.presentation.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityMessageSettingsBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;

public class MessageSettingsActivity extends ThemedActivity {
    private boolean applyingServerState;
    private ActivityMessageSettingsBinding binding;
    private SettingsViewModel vm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMessageSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this,
                new ViewModelFactory(ContainerProvider.from(this)))
                .get(SettingsViewModel.class);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.switchMute.setChecked(vm.isMuteMessageNotifications());
        binding.switchMute.setOnCheckedChangeListener((button, checked) ->
                vm.setMuteMessageNotifications(checked));
        binding.switchMuteCelebrations.setChecked(vm.isMuteCelebrationPopups());
        binding.switchMuteCelebrations.setOnCheckedChangeListener((button, checked) ->
                vm.setMuteCelebrationPopups(checked));

        binding.switchDmEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (!applyingServerState) saveCurrentAudience(checked);
        });
        binding.radioDmAudience.setOnCheckedChangeListener((group, checkedId) -> {
            if (!applyingServerState && checkedId != -1) {
                saveCurrentAudience(binding.switchDmEnabled.isChecked());
            }
        });

        vm.getDmPrivacy().observe(this, user -> {
            if (user == null) return;
            applyingServerState = true;
            binding.switchDmEnabled.setChecked(user.dmEnabled == null || user.dmEnabled);
            String audience = user.dmAudience == null ? "everyone" : user.dmAudience;
            int selected = R.id.radioDmEveryone;
            if ("followers".equals(audience)) selected = R.id.radioDmFollowers;
            else if ("friends".equals(audience)) selected = R.id.radioDmFriends;
            else if ("nobody".equals(audience)) selected = R.id.radioDmNobody;
            binding.radioDmAudience.check(selected);
            binding.radioDmAudience.setEnabled(user.dmEnabled == null || user.dmEnabled);
            for (int i = 0; i < binding.radioDmAudience.getChildCount(); i++) {
                binding.radioDmAudience.getChildAt(i).setEnabled(user.dmEnabled == null || user.dmEnabled);
            }
            binding.textDmPrivacyStatus.setText(Boolean.FALSE.equals(user.dmEnabled)
                    ? "الخاص مغلق ولن يتمكن أحد من مراسلتك"
                    : audienceLabel(audience));
            applyingServerState = false;
        });
        vm.getMessage().observe(this, message -> {
            if (message != null && !message.trim().isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
        vm.getError().observe(this, error -> {
            if (error != null && !error.trim().isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            }
        });
        vm.getDmPrivacySaving().observe(this, saving -> {
            if (saving != null && saving) {
                binding.textDmPrivacyStatus.setText("جارٍ حفظ إعدادات الخصوصية...");
            }
        });
        vm.loadDmPrivacy();
    }

    private void saveCurrentAudience(boolean enabled) {
        int checkedId = binding.radioDmAudience.getCheckedRadioButtonId();
        String audience = "everyone";
        if (checkedId == R.id.radioDmFollowers) audience = "followers";
        else if (checkedId == R.id.radioDmFriends) audience = "friends";
        else if (checkedId == R.id.radioDmNobody) audience = "nobody";
        vm.saveDmPrivacy(enabled, audience);
    }

    private String audienceLabel(String audience) {
        if ("followers".equals(audience)) return "المتابعون لك فقط يمكنهم مراسلتك";
        if ("friends".equals(audience)) return "الأصدقاء الذين تتابعهم يمكنهم مراسلتك";
        if ("nobody".equals(audience)) return "لا أحد يمكنه مراسلتك";
        return "يمكن للجميع مراسلتك";
    }
}
