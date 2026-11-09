package com.Dramizo.Series.presentation.settings;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityRoomTrackingPrivacyBinding;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;

import java.util.HashMap;
import java.util.Map;

public class RoomTrackingPrivacyActivity extends ThemedActivity {
    private ActivityRoomTrackingPrivacyBinding binding;
    private boolean ready;
    private String selectedAudience = "everyone";
    private static final String PREFS = "jeho_room_tracking_privacy";
    private static final String LAST_AUDIENCE = "last_enabled_audience";

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRoomTrackingPrivacyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        binding.switchEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (!ready) return;
            binding.groupAudience.setEnabled(checked);
            for (int i = 0; i < binding.groupAudience.getChildCount(); i++) {
                binding.groupAudience.getChildAt(i).setEnabled(checked);
            }
            if (checked) {
                if ("nobody".equals(selectedAudience)) {
                    selectedAudience = getSharedPreferences(PREFS, MODE_PRIVATE)
                            .getString(LAST_AUDIENCE, "everyone");
                    if ("nobody".equals(selectedAudience)) selectedAudience = "everyone";
                }
                checkAudience(selectedAudience);
                saveAudience(selectedAudience);
            } else {
                if (!"nobody".equals(selectedAudience)) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(LAST_AUDIENCE, selectedAudience).apply();
                }
                saveAudience("nobody");
            }
        });
        binding.groupAudience.setOnCheckedChangeListener((group, checkedId) -> {
            if (!ready || !binding.switchEnabled.isChecked()) return;
            String value = audienceForId(checkedId);
            if (value == null) return;
            selectedAudience = value;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(LAST_AUDIENCE, value).apply();
            saveAudience(value);
        });
        loadAudience();
    }

    private void loadAudience() {
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = com.Dramizo.Series.util.ApiCall.execute(
                    ContainerProvider.from(this).getUserApi().roomTrackingPrivacy());
            String audience = result.success && result.data != null && result.data.get("audience") != null
                    ? String.valueOf(result.data.get("audience")) : "everyone";
            if (!"everyone".equals(audience) && !"followers".equals(audience)
                    && !"friends".equals(audience) && !"nobody".equals(audience)) audience = "everyone";
            final String value = audience;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                selectedAudience = value;
                boolean enabled = !"nobody".equals(value);
                if (enabled) getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(LAST_AUDIENCE, value).apply();
                binding.switchEnabled.setChecked(enabled);
                binding.groupAudience.setEnabled(enabled);
                for (int i = 0; i < binding.groupAudience.getChildCount(); i++) {
                    binding.groupAudience.getChildAt(i).setEnabled(enabled);
                }
                checkAudience(enabled ? value : getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString(LAST_AUDIENCE, "everyone"));
                ready = true;
            });
        });
    }

    private void checkAudience(String value) {
        int id = "followers".equals(value) ? R.id.radioFollowers
                : "friends".equals(value) ? R.id.radioFriends
                : "nobody".equals(value) ? R.id.radioNobody : R.id.radioEveryone;
        binding.groupAudience.check(id);
    }

    private String audienceForId(int id) {
        if (id == R.id.radioEveryone) return "everyone";
        if (id == R.id.radioFollowers) return "followers";
        if (id == R.id.radioFriends) return "friends";
        if (id == R.id.radioNobody) return "nobody";
        return null;
    }

    private void saveAudience(String audience) {
        Map<String, String> body = new HashMap<>();
        body.put("audience", audience);
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = com.Dramizo.Series.util.ApiCall.execute(
                    ContainerProvider.from(this).getUserApi().updateRoomTrackingPrivacy(body));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!result.success) {
                    Toast.makeText(this, result.error != null ? result.error
                            : "تعذر حفظ خصوصية التتبع", Toast.LENGTH_SHORT).show();
                    loadAudience();
                } else {
                    Toast.makeText(this, "تم حفظ خصوصية التتبع", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
