package com.Dramizo.Series.presentation.settings;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityAccountManageBinding;
import com.Dramizo.Series.databinding.ItemSavedAccountBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.auth.LoginActivity;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.main.MainActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;

import java.util.ArrayList;
import java.util.List;

public class AccountManageActivity extends ThemedActivity {
    private ActivityAccountManageBinding binding;
    private AppContainer c;
    private SessionManager sm;
    private final List<SessionManager.SavedAccountInfo> accounts = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);
        sm = c.getSessionManager();
        binding.btnBack.setOnClickListener(v -> navigateUp());

        adapter = new Adapter();
        binding.recyclerAccounts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerAccounts.setAdapter(adapter);
        bindCurrentHeader(sm.getUser());

        binding.btnAddAccount.setOnClickListener(v -> {
            sm.saveAccountSnapshot();
            Intent i = new Intent(this, LoginActivity.class);
            i.putExtra("add_account", true);
            startActivity(i);
        });
        sm.saveAccountSnapshot();
        reload();
        refreshCurrentProfileThenReload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sm.saveAccountSnapshot();
        reload();
        refreshCurrentProfileThenReload();
    }

    private void bindCurrentHeader(AuthDtos.UserDto u) {
        if (u != null) {
            String name = u.displayName != null && !u.displayName.trim().isEmpty()
                    ? u.displayName.trim() : (u.username != null ? u.username : "—");
            String pid = u.displayPublicId();
            binding.tvCurrent.setText(name + (pid.isEmpty() ? "" : ("\nID " + pid)));
        } else {
            binding.tvCurrent.setText("—");
        }
    }

    /** Pull latest displayName / avatar / VIP frame so the switcher matches profile. */
    private void refreshCurrentProfileThenReload() {
        c.getIoExecutor().execute(() -> {
            Result<AuthDtos.UserDto> me = ApiCall.execute(c.getUserApi().me());
            if (me.success && me.data != null) {
                sm.updateCachedUser(me.data);
                sm.saveAccountSnapshot();
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                bindCurrentHeader(sm.getUser());
                reload();
            });
        });
    }

    private void reload() {
        accounts.clear();
        accounts.addAll(sm.listSavedAccountDetails());
        adapter.notifyDataSetChanged();
        boolean empty = accounts.isEmpty();
        binding.tvEmptyAccounts.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void switchAccount(String username) {
        sm.saveAccountSnapshot();
        if (sm.switchToAccount(username)) {
            Toast.makeText(this, R.string.account_switched, Toast.LENGTH_SHORT).show();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        } else {
            Toast.makeText(this, R.string.account_switch_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemSavedAccountBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SessionManager.SavedAccountInfo acc = accounts.get(position);
            h.b.tvName.setText(acc.displayName());
            String pid = acc.publicId();
            String uname = acc.usernameKey != null ? acc.usernameKey : "";
            String meta = !pid.isEmpty() ? ("ID " + pid) : uname;
            if (h.b.tvMeta != null) h.b.tvMeta.setText(meta);

            AvatarCosmetics.bindWear(h.b.imgAvatar, h.b.imgFrame, acc.user);

            String currentUser = sm.getUser() != null ? sm.getUser().username : null;
            boolean isCurrent = currentUser != null && currentUser.equals(acc.usernameKey);
            h.b.btnSwitch.setEnabled(!isCurrent);
            h.b.btnSwitch.setAlpha(isCurrent ? 0.45f : 1f);
            h.b.btnSwitch.setText(isCurrent
                    ? getString(R.string.account_current_badge)
                    : getString(R.string.account_switch_action));
            h.b.btnSwitch.setOnClickListener(v -> {
                if (!isCurrent) switchAccount(acc.usernameKey);
            });
            h.b.btnRemove.setOnClickListener(v -> {
                sm.removeSavedAccount(acc.usernameKey);
                reload();
                Toast.makeText(AccountManageActivity.this, R.string.account_removed, Toast.LENGTH_SHORT).show();
            });
        }
        @Override public int getItemCount() { return accounts.size(); }
        class VH extends RecyclerView.ViewHolder {
            final ItemSavedAccountBinding b;
            VH(ItemSavedAccountBinding b) { super(b.getRoot()); this.b = b; }
        }
    }
}
