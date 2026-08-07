package com.Dramizo.Series.presentation.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityVisitorsBinding;
import com.Dramizo.Series.databinding.ItemFriendRowBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.UserProfileCardSheet;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

/** Followers / following / friends list for any user (Mikoo card taps). */
public class SocialListActivity extends ThemedActivity {
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_TITLE = "title";

    public static final String KIND_FOLLOWERS = "followers";
    public static final String KIND_FOLLOWING = "following";
    public static final String KIND_FRIENDS = "friends";

    public static void open(
            @NonNull FragmentActivity activity,
            @NonNull String userId,
            @NonNull String kind,
            @Nullable String title) {
        Intent i = new Intent(activity, SocialListActivity.class);
        i.putExtra(EXTRA_USER_ID, userId);
        i.putExtra(EXTRA_KIND, kind);
        if (title != null) i.putExtra(EXTRA_TITLE, title);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityVisitorsBinding binding = ActivityVisitorsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        String userId = getIntent().getStringExtra(EXTRA_USER_ID);
        String kind = getIntent().getStringExtra(EXTRA_KIND);
        if (kind == null) kind = KIND_FOLLOWERS;
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) {
            if (KIND_FOLLOWING.equals(kind)) title = getString(R.string.following);
            else if (KIND_FRIENDS.equals(kind)) title = getString(R.string.friends);
            else title = getString(R.string.user_card_stat_following);
        }
        // Title TextView is hard-coded in layout — find first toolbar text.
        View titleView = binding.getRoot().findViewById(android.R.id.title);
        // Layout uses anonymous TextView for title — walk header.
        setTitleOnHeader(binding, title);

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        AppContainer c = ContainerProvider.from(this);
        List<AuthDtos.UserDto> items = new ArrayList<>();
        RecyclerView.Adapter<VH> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new VH(ItemFriendRowBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull VH holder, int position) {
                AuthDtos.UserDto u = items.get(position);
                holder.b.tvName.setText(u.displayName != null ? u.displayName : u.username);
                holder.b.tvId.setText(u.displayPublicId().isEmpty()
                        ? "" : ("ID: " + u.displayPublicId()));
                AvatarCosmetics.bindWear(holder.b.imgAvatar, holder.b.imgFrame, u);
                holder.itemView.setOnClickListener(v -> {
                    if (u == null || u.id == null) return;
                    UserProfileCardSheet.show(
                            SocialListActivity.this,
                            u.id,
                            u.displayName != null ? u.displayName : u.username,
                            u.avatarUrl,
                            Math.max(0, u.vipLevel),
                            Math.max(1, u.level));
                });
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter);

        final String uid = userId;
        final String k = kind;
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<AuthDtos.UserDto>> r = load(c, uid, k);
            runOnUiThread(() -> {
                items.clear();
                if (r.success && r.data != null && r.data.items != null) {
                    items.addAll(r.data.items);
                }
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                if (r.error != null) {
                    Toast.makeText(this, r.error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private static void setTitleOnHeader(ActivityVisitorsBinding binding, String title) {
        try {
            ViewGroup header = (ViewGroup) binding.btnBack.getParent();
            for (int i = 0; i < header.getChildCount(); i++) {
                View child = header.getChildAt(i);
                if (child instanceof android.widget.TextView
                        && child.getId() != binding.btnBack.getId()) {
                    ((android.widget.TextView) child).setText(title);
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Result<MiscDtos.ListResult<AuthDtos.UserDto>> load(
            AppContainer c, String userId, String kind) {
        Call<com.Dramizo.Series.data.remote.dto.ApiResponse<MiscDtos.ListResult<AuthDtos.UserDto>>> call;
        if (KIND_FOLLOWING.equals(kind)) {
            call = c.getUserApi().following(userId, 1);
        } else if (KIND_FRIENDS.equals(kind)) {
            call = c.getUserApi().userFriends(userId, 1);
        } else {
            call = c.getUserApi().followers(userId, 1);
        }
        return ApiCall.execute(call);
    }

    private static class VH extends RecyclerView.ViewHolder {
        final ItemFriendRowBinding b;

        VH(ItemFriendRowBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
