package com.Dramizo.Series.presentation.settings;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.databinding.ActivityMessageSettingsBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.ViewModelFactory;

/** Mikoo MessageSettingActivity — switches, no dialog. */
public class MessageSettingsActivity extends ThemedActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMessageSettingsBinding binding =
                ActivityMessageSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SettingsViewModel vm = new ViewModelProvider(this,
                new ViewModelFactory(ContainerProvider.from(this)))
                .get(SettingsViewModel.class);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.switchMute.setChecked(vm.isMuteMessageNotifications());
        binding.switchMute.setOnCheckedChangeListener((b, checked) ->
                vm.setMuteMessageNotifications(checked));

        binding.switchMuteCelebrations.setChecked(vm.isMuteCelebrationPopups());
        binding.switchMuteCelebrations.setOnCheckedChangeListener((b, checked) ->
                vm.setMuteCelebrationPopups(checked));

        // Friends-only: reuse privacy prefs if present, else local flag on SettingsViewModel.
        binding.switchFriendsOnly.setChecked(vm.isFriendsOnlyMessages());
        binding.switchFriendsOnly.setOnCheckedChangeListener((b, checked) ->
                vm.setFriendsOnlyMessages(checked));
    }
}
