package com.Dramizo.Series.presentation.games;



import android.content.Intent;

import android.os.Bundle;

import android.view.LayoutInflater;

import android.view.View;

import android.view.ViewGroup;

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

import com.Dramizo.Series.util.MikooGameBridge;
import com.Dramizo.Series.util.MikooGamesCatalog;
import com.Dramizo.Series.util.ImagePlaceholder;
import com.Dramizo.Series.util.RewardBurstOverlay;

import com.bumptech.glide.Glide;

import com.google.android.material.bottomsheet.BottomSheetDialog;



import java.util.ArrayList;

import java.util.List;

import java.util.Locale;



public class GamesFragment extends Fragment {

    private FragmentGamesBinding binding;

    private AppContainer c;

    private final List<MiscDtos.GameDto> catalog = new ArrayList<>();

    private GameCatalogAdapter catalogAdapter;



    @Nullable

    @Override

    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

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

        catalogAdapter = new GameCatalogAdapter(game -> openGame(game));

        binding.recyclerGames.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        binding.recyclerGames.setAdapter(catalogAdapter);



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



    private void bindCasualCard(View root, android.widget.TextView titleView,

                                String title, String playUrl, String mode, String gameId) {

        if (titleView != null && title != null) titleView.setText(title);

        if (root != null) {

            MiscDtos.GameDto g = new MiscDtos.GameDto();

            g.title = title;

            g.playUrl = playUrl;

            g.mode = mode;

            g.id = gameId;

            root.setOnClickListener(v -> openGame(g));

        }

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
        binding.recyclerGames.post(() -> {
            if (binding != null) binding.recyclerGames.requestLayout();
        });
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
        // Toolbar stub is already gone-sized; keep GONE when product disabled.
        binding.btnDailyMap.setVisibility(
                isTasksEnabled() ? View.GONE : View.GONE);
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

                else Toast.makeText(requireContext(), r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_SHORT).show();

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

                    Toast.makeText(requireContext(), r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();

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

                    Toast.makeText(requireContext(), r.error != null ? r.error : getString(R.string.error_generic), Toast.LENGTH_LONG).show();

                }

            });

        });

    }



    @Override

    public void onDestroyView() {

        super.onDestroyView();

        binding = null;

    }



    private static class GameCatalogAdapter extends RecyclerView.Adapter<GameCatalogAdapter.VH> {

        interface Listener { void onPlay(MiscDtos.GameDto game); }



        private final List<MiscDtos.GameDto> items = new ArrayList<>();

        private final Listener listener;



        GameCatalogAdapter(Listener listener) { this.listener = listener; }



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



        @NonNull @Override

        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            return new VH(ItemGameCatalogBinding.inflate(

                    LayoutInflater.from(parent.getContext()), parent, false));

        }



        @Override

        public void onBindViewHolder(@NonNull VH h, int position) {

            MiscDtos.GameDto g = items.get(position);

            String label = MikooGamesCatalog.displayTitle(h.itemView.getContext(), g);

            h.b.tvTitle.setText(label != null ? label : "لعبة");

            String cover = AssetCatalog.absoluteUrl(g.coverUrl);
            String fallback = null;
            if (MikooGameBridge.isMikooSlot(g.mode, g.playUrl) && g.id != null && !g.id.isEmpty()) {
                fallback = com.Dramizo.Series.util.ApiOrigin.origin()
                        + "/games/mikoo/covers/" + g.id + ".png?v=20260804g";
                if (cover == null || cover.isEmpty()
                        || cover.contains("/games/mikoo/covers/")
                        || cover.endsWith(".jpg")) {
                    cover = fallback;
                } else if (!cover.contains("v=")) {
                    cover = cover + (cover.contains("?") ? "&" : "?") + "v=20260804g";
                }
            }
            String loadUrl = (cover != null && !cover.isEmpty()) ? cover : fallback;

            Object prev = h.b.imgCover.getTag(R.id.tag_image_url);
            if (!(prev instanceof String && loadUrl != null && loadUrl.equals(prev)
                    && h.b.imgCover.getDrawable() != null)) {
                h.b.imgCover.setTag(R.id.tag_image_url, loadUrl);
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> req =
                        Glide.with(h.b.imgCover).load(loadUrl)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                .dontAnimate();
                if (h.b.imgCover.getDrawable() == null) {
                    req = req.placeholder(ImagePlaceholder.game());
                }
                if (fallback != null && !fallback.isEmpty()) {
                    req = req.error(Glide.with(h.b.imgCover).load(fallback)
                            .placeholder(ImagePlaceholder.game()));
                } else {
                    req = req.error(ImagePlaceholder.game());
                }
                req.into(h.b.imgCover);
            }

            h.itemView.setOnClickListener(v -> listener.onPlay(g));

        }



        @Override public int getItemCount() { return items.size(); }



        static class VH extends RecyclerView.ViewHolder {

            final ItemGameCatalogBinding b;

            VH(ItemGameCatalogBinding b) { super(b.getRoot()); this.b = b; }

        }

    }



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

        @NonNull @Override

        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            return new VH(ItemDailyTaskBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));

        }

        @Override

        public void onBindViewHolder(@NonNull VH h, int position) {

            MiscDtos.TaskDto t = items.get(position);

            h.b.tvTitle.setText(t.title);

            String progress = t.progressLabel != null ? t.progressLabel : (t.current + "/" + Math.max(1, t.target));

            long rewardCoins = Math.max(0, t.rewardPoints) + Math.max(0, t.rewardSilver);
            String reward = progress + (rewardCoins > 0 ? (" · " + rewardCoins + " كوينز") : "");

            h.b.tvReward.setText(reward);

            int target = Math.max(1, t.target);

            int current = Math.max(0, Math.min(t.current, target));

            h.b.progressTask.setVisibility(View.VISIBLE);

            h.b.progressTask.setMax(target);

            h.b.progressTask.setProgress(current);

            boolean isCheckin = t.type != null && t.type.toLowerCase().startsWith("checkin");

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

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {

            final ItemDailyTaskBinding b;

            VH(ItemDailyTaskBinding b) { super(b.getRoot()); this.b = b; }

        }

    }

}

