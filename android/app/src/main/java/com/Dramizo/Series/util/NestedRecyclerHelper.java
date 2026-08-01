package com.Dramizo.Series.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Expand RecyclerView height inside NestedScrollView so all room rows are visible. */
public final class NestedRecyclerHelper {
    private NestedRecyclerHelper() {}

    public static void attach(@NonNull RecyclerView recycler) {
        recycler.setNestedScrollingEnabled(false);
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null) return;
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                resize(recycler);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                resize(recycler);
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                resize(recycler);
            }
        });
        resize(recycler);
    }

    public static void resize(@NonNull RecyclerView recycler) {
        resize(recycler, null);
    }

    @SuppressWarnings("unchecked")
    public static void resize(@NonNull RecyclerView recycler, @Nullable Runnable after) {
        recycler.post(() -> {
            RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                    (RecyclerView.Adapter<RecyclerView.ViewHolder>) recycler.getAdapter();
            if (adapter == null) {
                if (after != null) after.run();
                return;
            }
            int width = recycler.getWidth()
                    - recycler.getPaddingLeft()
                    - recycler.getPaddingRight();
            if (width <= 0) {
                recycler.post(() -> resize(recycler, after));
                return;
            }

            int span = 1;
            RecyclerView.LayoutManager lm = recycler.getLayoutManager();
            if (lm instanceof GridLayoutManager) {
                span = Math.max(1, ((GridLayoutManager) lm).getSpanCount());
            }
            // Approximate vertical gutter from RoomGridSpacingDecoration (10dp typical).
            int rowGap = 0;
            if (recycler.getItemDecorationCount() > 0
                    && recycler.getItemDecorationAt(0) instanceof RoomGridSpacingDecoration) {
                rowGap = ((RoomGridSpacingDecoration) recycler.getItemDecorationAt(0)).getSpacingPx();
            }

            int itemWidth = Math.max(1, width / span);
            int widthSpec = View.MeasureSpec.makeMeasureSpec(itemWidth, View.MeasureSpec.EXACTLY);
            int height = recycler.getPaddingTop() + recycler.getPaddingBottom();
            int rowMax = 0;
            int count = adapter.getItemCount();
            for (int i = 0; i < count; i++) {
                int type = adapter.getItemViewType(i);
                RecyclerView.ViewHolder holder = adapter.createViewHolder(recycler, type);
                adapter.onBindViewHolder(holder, i);
                View item = holder.itemView;
                item.measure(
                        widthSpec,
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int itemH = item.getMeasuredHeight();
                ViewGroup.LayoutParams rawLp = item.getLayoutParams();
                if (rawLp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) rawLp;
                    itemH += mlp.topMargin + mlp.bottomMargin;
                }
                rowMax = Math.max(rowMax, itemH);
                boolean endOfRow = ((i + 1) % span == 0) || (i == count - 1);
                if (endOfRow) {
                    height += rowMax;
                    int rowIndex = i / span;
                    if (rowIndex > 0) height += rowGap;
                    rowMax = 0;
                }
            }
            float density = recycler.getResources().getDisplayMetrics().density;
            // Bottom breathing room under last row (home bottom nav).
            height += Math.round(24f * density);

            ViewGroup.LayoutParams lp = recycler.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, height);
            } else {
                lp.height = Math.max(height, 0);
            }
            recycler.setLayoutParams(lp);
            recycler.post(() -> {
                if (after != null) after.run();
            });
        });
    }
}
