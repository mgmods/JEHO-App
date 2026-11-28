package com.Dramizo.Series.presentation.profile;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.databinding.ActivityVisitorsBinding;
import com.Dramizo.Series.databinding.ItemProfileSupporterBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarCosmetics;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Weekly leaderboard of accounts that actually sent gifts to this profile. */
public class SupportersActivity extends ThemedActivity {
    private ActivityVisitorsBinding binding;
    private final List<GiftDtos.GiftSupporterDto> items = new ArrayList<>();
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVisitorsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        setHeaderTitle("الداعمون");

        String userId = getIntent().getStringExtra(ProfileActivity.EXTRA_USER_ID);
        if (userId == null || userId.trim().isEmpty()) {
            finish();
            return;
        }

        RecyclerView recycler = binding.recycler;
        recycler.setLayoutManager(new LinearLayoutManager(this));
        RecyclerView.Adapter<Holder> adapter = new RecyclerView.Adapter<Holder>() {
            @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                return new Holder(ItemProfileSupporterBinding.inflate(
                        LayoutInflater.from(parent.getContext()), parent, false));
            }
            @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
                GiftDtos.GiftSupporterDto item = items.get(position);
                holder.binding.tvSupporterName.setText(displayName(item));
                holder.binding.tvSupporterDiamonds.setText("♦ " + numberFormat.format(Math.max(0L, item.totalSupport)));
                AvatarCosmetics.bindStacked(holder.binding.imgSupporterAvatar,
                        holder.binding.imgSupporterFrame, holder.binding.supporterHostSignal,
                        item.avatarUrl, item.frameUrl, null);
                holder.itemView.setOnClickListener(v -> openSupporterProfile(item));
            }
            @Override public int getItemCount() { return items.size(); }
        };
        recycler.setAdapter(adapter);

        AppContainer container = ContainerProvider.from(this);
        container.getIoExecutor().execute(() -> {
            Result<List<GiftDtos.GiftSupporterDto>> result =
                    ApiCall.execute(container.getGiftApi().supporters(userId.trim()));
            runOnUiThread(() -> {
                if (binding == null || isFinishing() || isDestroyed()) return;
                items.clear();
                if (result.success && result.data != null) items.addAll(result.data);
                adapter.notifyDataSetChanged();
                binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                binding.tvEmpty.setText("لا يوجد داعمون هذا الأسبوع");
                renderTopThree();
            });
        });
    }

    private void renderTopThree() {
        ViewGroup root = (ViewGroup) binding.recycler.getParent().getParent();
        LinearLayout podium = new LinearLayout(this);
        podium.setOrientation(LinearLayout.VERTICAL);
        podium.setPadding(dp(14), dp(10), dp(14), dp(12));
        TextView title = new TextView(this);
        title.setText("الأكثر دعماً هذا الأسبوع");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        podium.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        podium.addView(row, rowLp);
        for (int i = 0; i < Math.min(3, items.size()); i++) {
            GiftDtos.GiftSupporterDto item = items.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(4), dp(8), dp(4), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(i == 0 ? 0xFF3C3152 : 0xFF282336);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), i == 0 ? 0xFFFFD36A : 0xFF51465F);
            card.setBackground(bg);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cardLp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(card, cardLp);

            TextView rank = new TextView(this);
            rank.setText(i == 0 ? "🥇 #1" : i == 1 ? "🥈 #2" : "🥉 #3");
            rank.setTextColor(i == 0 ? 0xFFFFD36A : 0xFFE5D9F0);
            rank.setTextSize(12);
            rank.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(rank);

            FrameLayoutAvatar avatar = new FrameLayoutAvatar(this);
            AvatarCosmetics.bindStacked(avatar.image, avatar.frame, avatar.signal,
                    item.avatarUrl, item.frameUrl, null);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dp(58), dp(58));
            avatarLp.topMargin = dp(5);
            card.addView(avatar, avatarLp);

            TextView name = new TextView(this);
            name.setText(displayName(item));
            name.setTextColor(Color.WHITE);
            name.setTextSize(11);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nameLp.topMargin = dp(4);
            card.addView(name, nameLp);

            TextView diamonds = new TextView(this);
            diamonds.setText("♦ " + numberFormat.format(Math.max(0L, item.totalSupport)));
            diamonds.setTextColor(0xFFFFD36A);
            diamonds.setTextSize(11);
            diamonds.setGravity(Gravity.CENTER);
            diamonds.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            card.addView(diamonds);
            card.setOnClickListener(v -> openSupporterProfile(item));
        }
        root.addView(podium, 1);
    }

    private static final class FrameLayoutAvatar extends android.widget.FrameLayout {
        final ImageView image;
        final ImageView frame;
        final com.Dramizo.Series.util.HostSignalView signal;
        FrameLayoutAvatar(android.content.Context context) {
            super(context);
            image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageResource(R.drawable.ic_default_avatar);
            LayoutParams p = new LayoutParams(dpStatic(context, 48), dpStatic(context, 48), Gravity.CENTER);
            addView(image, p);
            frame = new ImageView(context);
            frame.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(frame, new LayoutParams(-1, -1));
            signal = new com.Dramizo.Series.util.HostSignalView(context);
            addView(signal, new LayoutParams(-1, -1));
        }
    }

    private int dp(float value) { return dpStatic(this, value); }
    private static int dpStatic(android.content.Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private String displayName(GiftDtos.GiftSupporterDto item) {
        return item.displayName == null || item.displayName.trim().isEmpty()
                ? (item.username == null ? "مستخدم" : item.username) : item.displayName;
    }

    private void openSupporterProfile(GiftDtos.GiftSupporterDto item) {
        if (item.id == null || item.id.trim().isEmpty()) return;
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, item.id);
        startActivity(intent);
    }

    private void setHeaderTitle(String title) {
        try {
            ViewGroup header = (ViewGroup) binding.btnBack.getParent();
            for (int i = 0; i < header.getChildCount(); i++) {
                View child = header.getChildAt(i);
                if (child instanceof TextView && child.getId() != binding.btnBack.getId()) {
                    ((TextView) child).setText(title);
                    return;
                }
            }
        } catch (Exception ignored) { }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final ItemProfileSupporterBinding binding;
        Holder(ItemProfileSupporterBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
