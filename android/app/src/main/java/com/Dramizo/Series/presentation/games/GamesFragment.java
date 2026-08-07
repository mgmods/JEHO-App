package com.Dramizo.Series.presentation.games;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.DialogDailyTasksBinding;
import com.Dramizo.Series.databinding.FragmentGamesBinding;
import com.Dramizo.Series.databinding.ItemDailyTaskBinding;
import com.Dramizo.Series.databinding.ItemGameCatalogBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.GameUrls;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.MediaAssetSync;
import com.Dramizo.Series.util.MikooGameBridge;
import com.Dramizo.Series.util.MikooGamesCatalog;
import com.Dramizo.Series.util.RewardBurstOverlay;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GamesFragment extends Fragment {
    private FragmentGamesBinding binding;
    private AppContainer c;
    private final List<MiscDtos.GameDto> catalog = new ArrayList<>();
    private GameCatalogAdapter catalogAdapter;
    private GamePromoBannerAdapter promoAdapter;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private int bannerPage;
    private final Runnable bannerAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (binding == null || promoAdapter == null || promoAdapter.getItemCount() <= 1) return;
            bannerPage = (bannerPage + 1) % promoAdapter.getItemCount();
            binding.pagerGameBanners.setCurrentItem(bannerPage, true);
            bannerHandler.postDelayed(this, 3800);
        }
    };

    /** Priority IDs for the promo slider (most played / high engagement). */
    private static final List<String> HOT_IDS = Arrays.asList(
            "lucky77", "slot777", "fishing", "crash", "royal-battle",
            "greedy-lion", "fortune-slot", "cleopatra-slot", "hilo", "olympians"
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGamesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        com.Dramizo.Series.util.RemoteTheme.applyActivityBackground(binding.getRoot(), "games");
        c = ContainerProvider.from(requireActivity());

        if (binding.cardWheel != null && binding.cardWheel.getRoot() != null) {
            binding.cardWheel.getRoot().setVisibility(View.GONE);
        }
        if (binding.cardDice != null && binding.cardDice.getRoot() != null) {
            binding.cardDice.getRoot().setVisibility(View.GONE);
        }
        View casualSection = binding.getRoot().findViewById(R.id.casualGamesSection);
        if (casualSection != null) casualSection.setVisibility(View.GONE);

        catalogAdapter = new GameCatalogAdapter(this::openGame);
        binding.recyclerGames.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.recyclerGames.setAdapter(catalogAdapter);

        promoAdapter = new GamePromoBannerAdapter(this::openGame);
        binding.pagerGameBanners.setAdapter(promoAdapter);
        binding.pagerGameBanners.setOffscreenPageLimit(1);
        binding.pagerGameBanners.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                bannerPage = position;
                updateBannerDots(position);
                bannerHandler.removeCallbacks(bannerAutoScroll);
                if (promoAdapter.getItemCount() > 1) {
                    bannerHandler.postDelayed(bannerAutoScroll, 3800);
                }
            }
        });

        loadConfigGames();

        binding.btnDailyMap.setOnClickListener(v -> {
            if (!isTasksEnabled()) return;
            showTasksDialog();
        });
        applyTasksFeatureVisibility();

        binding.btnContests.setOnClickListener(v ->
                startActivity(new Intent(requireContext(),
                        com.Dramizo.Series.presentation.contests.ContestsActivity.class)));
        binding.btnShop.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GameStoreActivity.class)));

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        loadHeader();
    }

    private void loadConfigGames() {
        applyConfigGames(null);
        c.getIoExecutor().execute(() -> {
            try {
                retrofit2.Response<com.Dramizo.Series.data.remote.dto.ApiResponse<java.util.List<MiscDtos.GameDto>>> resp =
                        c.getConfigApi().games().execute();
                if (!resp.isSuccessful() || resp.body() == null || resp.body().data == null) return;
                java.util.List<MiscDtos.GameDto> list = resp.body().data;
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> applyConfigGames(list));
            } catch (Exception ignored) {
            }
        });
    }

    private void applyConfigGames(@Nullable java.util.List<MiscDtos.GameDto> list) {
        if (binding == null) return;
        catalog.clear();
        catalog.addAll(MikooGamesCatalog.mergeSlots(list));
        catalogAdapter.submit(catalog);
        applyPromoBanners(catalog);
        binding.recyclerGames.post(() -> {
            if (binding != null) binding.recyclerGames.requestLayout();
        });
    }

    private void applyPromoBanners(List<MiscDtos.GameDto> all) {
        if (binding == null || promoAdapter == null) return;
        List<MiscDtos.GameDto> hot = pickHotGames(all, 6);
        if (hot.isEmpty()) {
            binding.gamesBannerHost.setVisibility(View.GONE);
            bannerHandler.removeCallbacks(bannerAutoScroll);
            promoAdapter.submit(null);
            return;
        }
        binding.gamesBannerHost.setVisibility(View.VISIBLE);
        promoAdapter.submit(hot);
        bannerPage = 0;
        binding.pagerGameBanners.setCurrentItem(0, false);
        rebuildBannerDots(hot.size());
        updateBannerDots(0);
        bannerHandler.removeCallbacks(bannerAutoScroll);
        if (hot.size() > 1) {
            bannerHandler.postDelayed(bannerAutoScroll, 3200);
        }
    }

    private static List<MiscDtos.GameDto> pickHotGames(List<MiscDtos.GameDto> all, int max) {
        List<MiscDtos.GameDto> out = new ArrayList<>();
        if (all == null || all.isEmpty()) return out;
        Set<String> used = new HashSet<>();
        for (String id : HOT_IDS) {
            if (out.size() >= max) break;
            for (MiscDtos.GameDto g : all) {
                if (g == null || g.id == null) continue;
                if (!id.equalsIgnoreCase(g.id) || used.contains(g.id.toLowerCase(Locale.US))) continue;
                used.add(g.id.toLowerCase(Locale.US));
                out.add(g);
                break;
            }
        }
        for (MiscDtos.GameDto g : all) {
            if (out.size() >= max) break;
            if (g == null || g.id == null) continue;
            String key = g.id.toLowerCase(Locale.US);
            if (used.contains(key)) continue;
            used.add(key);
            out.add(g);
        }
        return out;
    }

    private void rebuildBannerDots(int count) {
        if (binding == null || binding.gameBannerDots == null) return;
        LinearLayout dots = binding.gameBannerDots;
        dots.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(6f * d);
        int gap = Math.round(5f * d);
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginStart(i == 0 ? 0 : gap);
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(R.drawable.bg_banner_dot);
            dots.addView(dot);
        }
    }

    private void updateBannerDots(int selected) {
        if (binding == null || binding.gameBannerDots == null) return;
        LinearLayout dots = binding.gameBannerDots;
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            d.setAlpha(i == selected ? 1f : 0.35f);
            d.setScaleX(i == selected ? 1.25f : 1f);
            d.setScaleY(i == selected ? 1.25f : 1f);
        }
    }

    private void openGame(MiscDtos.GameDto game) {
        if (game == null) return;
        String title = MikooGamesCatalog.displayTitle(requireContext(), game);
        String url = GameUrls.resolve(requireContext(), game.playUrl);
        Intent i = new Intent(requireContext(), GamePlayActivity.class);
        i.putExtra(GamePlayActivity.EXTRA_URL, url);
        i.putExtra(GamePlayActivity.EXTRA_TITLE, title);
        if (game.coverUrl != null) i.putExtra(GamePlayActivity.EXTRA_COVER_URL, game.coverUrl);
        if (game.id != null) i.putExtra(GamePlayActivity.EXTRA_GAME_ID, game.id);
        if (game.mode != null) i.putExtra(GamePlayActivity.EXTRA_MODE, game.mode);
        else if (MikooGameBridge.isMikooSlot(null, url)) {
            i.putExtra(GamePlayActivity.EXTRA_MODE, "mikoo_slot");
        }
        android.app.Activity act = getActivity();
        if (act == null) {
            startActivity(i);
            return;
        }
        GameAdsHelper ads = GameAdsHelper.get(act);
        ads.refreshConfig(act);
        ads.maybeShowOnOpen(act, () -> startActivity(i));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (c != null) loadHeader();
        if (promoAdapter != null && promoAdapter.getItemCount() > 1) {
            bannerHandler.removeCallbacks(bannerAutoScroll);
            bannerHandler.postDelayed(bannerAutoScroll, 3800);
        }
    }

    @Override
    public void onPause() {
        bannerHandler.removeCallbacks(bannerAutoScroll);
        super.onPause();
    }

    private void loadHeader() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (binding == null) return;
                if (w.success && w.data != null) {
                    String next = String.format(Locale.US, "%,d", w.data.coins);
                    if (!next.contentEquals(binding.tvCoins.getText())) {
                        binding.tvCoins.setText(next);
                    }
                }
                AppLoadingOverlay.hide(requireActivity());
            });
        });
    }

    private boolean isTasksEnabled() {
        return com.Dramizo.Series.util.TasksFeature.isEnabled(requireContext());
    }

    private void applyTasksFeatureVisibility() {
        if (binding == null || binding.btnDailyMap == null) return;
        binding.btnDailyMap.setVisibility(View.GONE);
    }

    private void showTasksDialog() {
        if (!isTasksEnabled()) return;
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(requireContext());
        DialogDailyTasksBinding db = DialogDailyTasksBinding.inflate(getLayoutInflater());
        AuraDialogHelper.applyContent(db.getRoot());
        dialog.setContentView(db.getRoot());
        final TaskAdapter[] holder = new TaskAdapter[1];
        holder[0] = new TaskAdapter(
                task -> claimTask(task, holder[0], dialog),
                () -> checkInTask(holder[0]));
        TaskAdapter taskAdapter = holder[0];
        db.recyclerTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        db.recyclerTasks.setAdapter(taskAdapter);
        dialog.show();
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.TaskDto>> r = ApiCall.execute(c.getTasksApi().daily());
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (r.success && r.data != null) taskAdapter.submit(r.data);
                else Toast.makeText(requireContext(),
                        r.error != null ? r.error : getString(R.string.error_generic),
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void checkInTask(TaskAdapter taskAdapter) {
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> r = ApiCall.execute(c.getTasksApi().checkin());
            Result<List<MiscDtos.TaskDto>> refreshed = null;
            if (r.success) refreshed = ApiCall.execute(c.getTasksApi().daily());
            if (!isAdded()) return;
            final Result<List<MiscDtos.TaskDto>> list = refreshed;
            requireActivity().runOnUiThread(() -> {
                if (r.success) {
                    RewardBurstOverlay.showCheckIn(requireContext());
                    if (list != null && list.success && list.data != null) taskAdapter.submit(list.data);
                } else {
                    Toast.makeText(requireContext(),
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void claimTask(MiscDtos.TaskDto task, TaskAdapter taskAdapter, android.app.Dialog dialog) {
        if (task.claimed) {
            Toast.makeText(requireContext(), R.string.already_claimed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!task.claimable) {
            Toast.makeText(requireContext(), R.string.complete_task_first, Toast.LENGTH_LONG).show();
            return;
        }
        c.getIoExecutor().execute(() -> {
            Result<java.util.Map<String, Object>> r = ApiCall.execute(c.getTasksApi().claim(task.id));
            Result<List<MiscDtos.TaskDto>> refreshed = null;
            if (r.success) refreshed = ApiCall.execute(c.getTasksApi().daily());
            if (!isAdded()) return;
            final Result<List<MiscDtos.TaskDto>> list = refreshed;
            requireActivity().runOnUiThread(() -> {
                if (r.success) {
                    RewardBurstOverlay.showForTask(requireContext(), task);
                    loadHeader();
                    if (list != null && list.success && list.data != null) taskAdapter.submit(list.data);
                } else {
                    Toast.makeText(requireContext(),
                            r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onDestroyView() {
        bannerHandler.removeCallbacks(bannerAutoScroll);
        super.onDestroyView();
        binding = null;
    }

    // ── Promo carousel ──────────────────────────────────────────

    private static final class GamePromoBannerAdapter
            extends RecyclerView.Adapter<GamePromoBannerAdapter.VH> {
        interface Listener { void onPlay(MiscDtos.GameDto game); }

        private final List<MiscDtos.GameDto> items = new ArrayList<>();
        private final Listener listener;

        GamePromoBannerAdapter(Listener listener) {
            this.listener = listener;
        }

        void submit(@Nullable List<MiscDtos.GameDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_game_promo_banner, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MiscDtos.GameDto g = items.get(position);
            String title = MikooGamesCatalog.displayTitle(h.itemView.getContext(), g);
            h.tvTitle.setText(title != null ? title : "");
            if (position == 0) {
                h.tvBadge.setText(R.string.games_promo_hot);
            } else {
                h.tvBadge.setText(R.string.games_promo_featured);
            }
            String cover = resolveCover(g);
            Object prev = h.imgCover.getTag(R.id.tag_image_url);
            if (!(prev instanceof String && cover != null && cover.equals(prev))) {
                h.imgCover.setTag(R.id.tag_image_url, cover);
                h.imgBg.setTag(R.id.tag_image_url, cover);
                Glide.with(h.imgBg)
                        .load(cover)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade(160))
                        .placeholder(ImagePlaceholder.cover())
                        .error(ImagePlaceholder.cover())
                        .into(h.imgBg);
                Glide.with(h.imgCover)
                        .load(cover)
                        .centerCrop()
                        .transition(DrawableTransitionOptions.withCrossFade(160))
                        .placeholder(ImagePlaceholder.game())
                        .error(ImagePlaceholder.game())
                        .into(h.imgCover);
            }
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPlay(g);
            });
        }

        private static String resolveCover(MiscDtos.GameDto g) {
            if (g == null) return null;
            String cover = AssetCatalog.absoluteUrl(g.coverUrl);
            if (cover != null && !cover.isEmpty()
                    && !MediaAssetSync.isPackageDefaultCover(cover, g.id)) {
                return MediaAssetSync.bust(cover);
            }
            if (g.id != null && !g.id.isEmpty()
                    && MikooGameBridge.isMikooSlot(g.mode, g.playUrl)) {
                return MediaAssetSync.mikooCoverUrl(g.id);
            }
            return MediaAssetSync.bust(cover);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final ImageView imgBg;
            final ImageView imgCover;
            final TextView tvTitle;
            final TextView tvBadge;

            VH(@NonNull View itemView) {
                super(itemView);
                imgBg = itemView.findViewById(R.id.imgPromoBg);
                imgCover = itemView.findViewById(R.id.imgPromoCover);
                tvTitle = itemView.findViewById(R.id.tvPromoTitle);
                tvBadge = itemView.findViewById(R.id.tvPromoBadge);
            }
        }
    }

    // ── Catalog grid ────────────────────────────────────────────

    private static class GameCatalogAdapter extends RecyclerView.Adapter<GameCatalogAdapter.VH> {
        interface Listener { void onPlay(MiscDtos.GameDto game); }

        private final List<MiscDtos.GameDto> items = new ArrayList<>();
        private final Listener listener;

        GameCatalogAdapter(Listener listener) {
            this.listener = listener;
        }

        void submit(List<MiscDtos.GameDto> data) {
            if (sameCatalog(items, data)) return;
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        private static boolean sameCatalog(List<MiscDtos.GameDto> a, List<MiscDtos.GameDto> b) {
            if (a == b) return true;
            if (b == null) return a.isEmpty();
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++) {
                MiscDtos.GameDto x = a.get(i);
                MiscDtos.GameDto y = b.get(i);
                String xid = x != null ? x.id : null;
                String yid = y != null ? y.id : null;
                if (xid == null ? yid != null : !xid.equals(yid)) return false;
                String xc = x != null ? x.coverUrl : null;
                String yc = y != null ? y.coverUrl : null;
                if (xc == null ? yc != null : !xc.equals(yc)) return false;
            }
            return true;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemGameCatalogBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MiscDtos.GameDto g = items.get(position);
            String label = MikooGamesCatalog.displayTitle(h.itemView.getContext(), g);
            h.b.tvTitle.setText(label != null ? label : "");

            String cover = AssetCatalog.absoluteUrl(g.coverUrl);
            String jpg = null;
            String png = null;
            final boolean packageSlot =
                    MikooGameBridge.isMikooSlot(g.mode, g.playUrl) && g.id != null && !g.id.isEmpty();
            if (packageSlot) {
                jpg = MediaAssetSync.mikooCoverUrl(g.id);
                png = MediaAssetSync.mikooCoverUrlPng(g.id);
            }
            // Prefer dashboard custom cover (uploads/CDN). Only fall back to package art.
            boolean useCustom = cover != null && !cover.isEmpty()
                    && !MediaAssetSync.isPackageDefaultCover(cover, g.id);
            if (useCustom) {
                cover = MediaAssetSync.bust(cover);
            } else if (packageSlot) {
                cover = jpg;
            } else {
                cover = MediaAssetSync.bust(cover);
            }
            final String loadUrl = (cover != null && !cover.isEmpty()) ? cover : jpg;
            final String pngFallback = useCustom ? null : png;
            Object prev = h.b.imgCover.getTag(R.id.tag_image_url);
            String tag = loadUrl != null ? loadUrl + "#" + MediaAssetSync.epoch() : null;
            if (!(prev instanceof String && tag != null && tag.equals(prev)
                    && h.b.imgCover.getDrawable() != null)) {
                h.b.imgCover.setTag(R.id.tag_image_url, tag);
                float density = h.itemView.getResources().getDisplayMetrics().density;
                int side = Math.round(78f * density * 2f);
                com.bumptech.glide.request.RequestOptions opts = MediaAssetSync.tileOptions(side);
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> req =
                        Glide.with(h.b.imgCover)
                                .load(loadUrl)
                                .apply(opts)
                                .dontAnimate()
                                .placeholder(ImagePlaceholder.game());
                if (pngFallback != null && !pngFallback.isEmpty()
                        && loadUrl != null && !loadUrl.equals(pngFallback)) {
                    req = req.error(Glide.with(h.b.imgCover)
                            .load(pngFallback)
                            .apply(opts)
                            .placeholder(ImagePlaceholder.game())
                            .error(ImagePlaceholder.game()));
                } else {
                    req = req.error(ImagePlaceholder.game());
                }
                req.into(h.b.imgCover);
            }
            h.itemView.setOnClickListener(v -> listener.onPlay(g));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemGameCatalogBinding b;

            VH(ItemGameCatalogBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }

    // ── Tasks (kept) ────────────────────────────────────────────

    private static class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.VH> {
        interface Listener { void onClaim(MiscDtos.TaskDto task); }

        private final List<MiscDtos.TaskDto> items = new ArrayList<>();
        private final Listener listener;
        private final Runnable checkInListener;

        TaskAdapter(Listener listener, Runnable checkInListener) {
            this.listener = listener;
            this.checkInListener = checkInListener;
        }

        void submit(List<MiscDtos.TaskDto> data) {
            items.clear();
            if (data != null) items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemDailyTaskBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MiscDtos.TaskDto t = items.get(position);
            h.b.tvTitle.setText(t.title);
            String progress = t.progressLabel != null
                    ? t.progressLabel
                    : (t.current + "/" + Math.max(1, t.target));
            long rewardCoins = Math.max(0, t.rewardPoints) + Math.max(0, t.rewardSilver);
            String reward = progress + (rewardCoins > 0 ? (" · " + rewardCoins + " coins") : "");
            h.b.tvReward.setText(reward);
            int target = Math.max(1, t.target);
            int current = Math.max(0, Math.min(t.current, target));
            h.b.progressTask.setVisibility(View.VISIBLE);
            h.b.progressTask.setMax(target);
            h.b.progressTask.setProgress(current);
            boolean isCheckin = t.type != null && t.type.toLowerCase(Locale.US).startsWith("checkin");
            if (h.b.imgTaskIcon != null) {
                int icon = R.drawable.ic_asset_tasks;
                if (isCheckin) icon = R.drawable.ic_asset_chest_gold;
                else if (t.claimed) icon = R.drawable.ic_asset_gift;
                else if (t.claimable) icon = R.drawable.ic_asset_coin_gold;
                h.b.imgTaskIcon.setImageResource(icon);
            }
            if (h.b.tvTaskIcon != null) h.b.tvTaskIcon.setVisibility(View.GONE);
            if (t.claimed) {
                h.b.btnClaim.setText(R.string.claimed_done);
                h.b.btnClaim.setEnabled(false);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF3A3A48));
                h.b.btnClaim.setTextColor(0xFFB8B8C8);
                h.b.btnClaim.setOnClickListener(null);
            } else if (t.claimable) {
                h.b.btnClaim.setText(R.string.claim);
                h.b.btnClaim.setEnabled(true);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                h.itemView.getContext().getColor(R.color.hiyoo_gold)));
                h.b.btnClaim.setTextColor(0xFF1A1208);
                h.b.btnClaim.setOnClickListener(v -> listener.onClaim(t));
            } else if (isCheckin) {
                h.b.btnClaim.setText(R.string.check_in_action);
                h.b.btnClaim.setEnabled(true);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF34D399));
                h.b.btnClaim.setTextColor(0xFF06251A);
                h.b.btnClaim.setOnClickListener(v -> checkInListener.run());
            } else {
                h.b.btnClaim.setText(R.string.claim_first);
                h.b.btnClaim.setEnabled(false);
                h.b.btnClaim.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF2A2A38));
                h.b.btnClaim.setTextColor(0xFFE8C96A);
                h.b.btnClaim.setOnClickListener(null);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final ItemDailyTaskBinding b;

            VH(ItemDailyTaskBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
