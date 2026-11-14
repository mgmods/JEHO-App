package com.Dramizo.Series.util;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Even gutters for 2-col room cards (Mikoo-style gaps, not glued together). */
public final class RoomGridSpacingDecoration extends RecyclerView.ItemDecoration {
    private final int spacingPx;

    public RoomGridSpacingDecoration(int spacingPx) {
        this.spacingPx = Math.max(0, spacingPx);
    }

    public int getSpacingPx() {
        return spacingPx;
    }

    @Override
    public void getItemOffsets(
            @NonNull Rect outRect,
            @NonNull View view,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state) {
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        int span = 2;
        if (lm instanceof GridLayoutManager) {
            span = Math.max(1, ((GridLayoutManager) lm).getSpanCount());
        }
        int pos = parent.getChildAdapterPosition(view);
        if (pos == RecyclerView.NO_POSITION) return;
        int col = pos % span;
        // Distribute spacing evenly across columns.
        outRect.left = spacingPx - col * spacingPx / span;
        outRect.right = (col + 1) * spacingPx / span;
        if (pos >= span) outRect.top = spacingPx;
        outRect.bottom = 0;
    }
}
