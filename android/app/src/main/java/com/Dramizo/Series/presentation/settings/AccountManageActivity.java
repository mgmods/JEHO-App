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
import com.Dramizo.Series.presentation.auth.LoginActivity;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class AccountManageActivity extends ThemedActivity {
    private ActivityAccountManageBinding binding;
    private SessionManager sm;
    private final List<String> accounts = new ArrayList<>();
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountManageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        sm = ContainerProvider.from(this).getSessionManager();
        binding.btnBack.setOnClickListener(v -> navigateUp());

        AuthDtos.UserDto u = sm.getUser();
        binding.tvCurrent.setText(u != null
                ? ((u.displayName != null ? u.displayName : "") + "\nID: " + (u.displayPublicId().isEmpty() ? "—" : u.displayPublicId()))
                : "—");

        adapter = new Adapter();
        binding.recyclerAccounts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerAccounts.setAdapter(adapter);

        binding.btnAddAccount.setOnClickListener(v -> {
            sm.saveAccountSnapshot();
            Intent i = new Intent(this, LoginActivity.class);
            i.putExtra("add_account", true);
            startActivity(i);
        });
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        accounts.clear();
        accounts.addAll(sm.listSavedAccounts());
        adapter.notifyDataSetChanged();
        boolean empty = accounts.isEmpty();
        binding.tvEmptyAccounts.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void switchAccount(String username) {
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
            String name = accounts.get(position);
            h.b.tvName.setText(name);
            h.b.btnSwitch.setOnClickListener(v -> switchAccount(name));
            h.b.btnRemove.setOnClickListener(v -> {
                sm.removeSavedAccount(name);
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
