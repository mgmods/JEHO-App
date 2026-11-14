package com.Dramizo.Series.presentation.settings;

import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityPrivacyBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;

import java.util.ArrayList;
import java.util.List;

public class PrivacyActivity extends ThemedActivity {
    private boolean ready;
    private String selectedGiftId;
    private String selectedGiftName;
    private ActivityPrivacyBinding binding;
    private AppContainer c;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPrivacyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> me = c.getUserRepository().getMe();
            runOnUiThread(() -> {
                if (me.success && me.data != null) {
                    binding.switchOnline.setChecked(me.data.showOnlineStatus == null || me.data.showOnlineStatus);
                    binding.switchDm.setChecked(me.data.allowDmFromStrangers == null || me.data.allowDmFromStrangers);
                    boolean gate = Boolean.TRUE.equals(me.data.dmGiftGateEnabled);
                    binding.switchDmGiftGate.setChecked(gate);
                    selectedGiftId = me.data.dmRequiredGiftId;
                    binding.btnPickDmGift.setVisibility(gate ? View.VISIBLE : View.GONE);
                    refreshGiftLabel();
                }
                ready = true;
            });
        });

        binding.switchOnline.setOnCheckedChangeListener((b, checked) -> {
            if (ready) persist();
        });
        binding.switchDm.setOnCheckedChangeListener((b, checked) -> {
            if (ready) persist();
        });
        binding.switchDmGiftGate.setOnCheckedChangeListener((b, checked) -> {
            binding.btnPickDmGift.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (ready) persist();
        });
        binding.btnPickDmGift.setOnClickListener(v -> pickGift());
    }

    private void refreshGiftLabel() {
        if (selectedGiftId == null || selectedGiftId.isEmpty()) {
            binding.btnPickDmGift.setText(R.string.privacy_pick_required_gift);
        } else {
            String name = selectedGiftName != null ? selectedGiftName : selectedGiftId;
            binding.btnPickDmGift.setText(getString(R.string.privacy_required_gift_label, name));
        }
    }

    private void pickGift() {
        c.getIoExecutor().execute(() -> {
            Result<GiftDtos.GiftList> list = ApiCall.execute(c.getGiftApi().list());
            runOnUiThread(() -> {
                if (!list.success || list.data == null || list.data.isEmpty()) {
                    Toast.makeText(this, R.string.no_gifts_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                List<String> labels = new ArrayList<>();
                List<GiftDtos.GiftDto> gifts = list.data;
                labels.add(getString(R.string.any_gift));
                for (GiftDtos.GiftDto g : gifts) {
                    labels.add((g.name != null ? g.name : getString(R.string.gift_message)) + " · " + g.coinPrice);
                }
                AuraDialogHelper.list(this, getString(R.string.choose_gift_title), labels.toArray(new String[0]), which -> {
                    if (which <= 0) {
                        selectedGiftId = null;
                        selectedGiftName = null;
                    } else {
                        GiftDtos.GiftDto g = gifts.get(which - 1);
                        selectedGiftId = g.id;
                        selectedGiftName = g.name;
                    }
                    refreshGiftLabel();
                    persist();
                });
            });
        });
    }

    private void persist() {
        boolean showOnline = binding.switchOnline.isChecked();
        boolean allowDm = binding.switchDm.isChecked();
        boolean gate = binding.switchDmGiftGate.isChecked();
        c.getIoExecutor().execute(() -> {
            MiscDtos.UpdateProfileRequest req = new MiscDtos.UpdateProfileRequest(null, null, null, null, null);
            req.showOnlineStatus = showOnline;
            req.allowDmFromStrangers = allowDm;
            req.dmGiftGateEnabled = gate;
            req.dmRequiredGiftId = gate ? selectedGiftId : null;
            Result<AuthDtos.UserDto> r = c.getUserRepository().updateProfile(req);
            runOnUiThread(() -> {
                if (r.success) Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
                else Toast.makeText(this,
                        r.error != null ? r.error : getString(R.string.error_generic),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }
}
