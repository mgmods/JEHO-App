package com.Dramizo.Series.presentation.notifications;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityOfficialNewsBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;

import java.util.ArrayList;
import java.util.List;

/** Read-only Official News chat — system / agency / wallet / vip announcements. */
public class OfficialNewsActivity extends ThemedActivity {
    private ActivityOfficialNewsBinding binding;
    private OfficialNewsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOfficialNewsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        adapter = new OfficialNewsAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        binding.recycler.setLayoutManager(lm);
        binding.recycler.setAdapter(adapter);
        load();
    }

    private void load() {
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<MiscDtos.NotificationDto>> r =
                    ApiCall.execute(c.getNotificationApi().officialList(1));
            ApiCall.execute(c.getNotificationApi().officialMarkAllRead());
            if (isFinishing()) return;
            runOnUiThread(() -> {
                List<MiscDtos.NotificationDto> items = new ArrayList<>();
                if (r.success && r.data != null && r.data.items != null) {
                    items.addAll(r.data.items);
                } else if (!r.success && r.error != null) {
                    Toast.makeText(this, r.error, Toast.LENGTH_SHORT).show();
                }
                adapter.submit(items);
                binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (!items.isEmpty()) {
                    binding.recycler.scrollToPosition(items.size() - 1);
                }
                // Clear parent Messages list badge
                sendBroadcast(new android.content.Intent(
                        com.Dramizo.Series.presentation.messages.MessagesFragment.ACTION_OFFICIAL_NEWS_UPDATED)
                        .setPackage(getPackageName()));
            });
        });
    }
}
