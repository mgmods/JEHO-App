package com.Dramizo.Series.presentation.gifts;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.GiftDtos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ViewPager pages = gift category tabs.
 * Selection updates only the gift cell highlight — never rebuilds pages.
 */
public final class GiftCategoryPagerAdapter
        extends RecyclerView.Adapter<GiftCategoryPagerAdapter.PageVH> {

    public interface Listener {
        void onGiftClick(@Nullable GiftDtos.GiftDto gift);
    }

    private final Listener listener;
    private final List<List<GiftDtos.GiftDto>> pages = new ArrayList<>();
    private final List<PageVH> boundHolders = new ArrayList<>();
    @Nullable private String selectedId;

    public GiftCategoryPagerAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    /** One list per tab, same order as TabLayout. */
    public void submit(@Nullable List<List<GiftDtos.GiftDto>> byTab,
                       @Nullable String selectedGiftId) {
        List<List<GiftDtos.GiftDto>> next = new ArrayList<>();
        if (byTab != null) {
            for (List<GiftDtos.GiftDto> page : byTab) {
                next.add(page != null ? new ArrayList<>(page) : new ArrayList<>());
            }
        }
        boolean sameSize = next.size() == pages.size();
        boolean sameContent = sameSize;
        if (sameContent) {
            for (int i = 0; i < pages.size(); i++) {
                if (!sameGiftList(pages.get(i), next.get(i))) {
                    sameContent = false;
                    break;
                }
            }
        }
        selectedId = selectedGiftId;
        if (sameContent) {
            for (PageVH holder : boundHolders) {
                if (holder != null) holder.adapter.setSelectedId(selectedId);
            }
            return;
        }
        pages.clear();
        pages.addAll(next);
        notifyDataSetChanged();
    }

    public void setSelectedId(@Nullable String id) {
        if (Objects.equals(selectedId, id)) return;
        selectedId = id;
        for (PageVH holder : boundHolders) {
            if (holder != null) holder.adapter.setSelectedId(selectedId);
        }
    }

    public int pageCount() {
        return pages.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public PageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_gift_page, parent, false);
        return new PageVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PageVH holder, int position) {
        List<GiftDtos.GiftDto> gifts = position < pages.size()
                ? pages.get(position) : Collections.emptyList();
        holder.bind(gifts, selectedId, listener);
        if (!boundHolders.contains(holder)) boundHolders.add(holder);
    }

    @Override
    public void onViewRecycled(@NonNull PageVH holder) {
        boundHolders.remove(holder);
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    private static boolean sameGiftList(
            @Nullable List<GiftDtos.GiftDto> a, @Nullable List<GiftDtos.GiftDto> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            GiftDtos.GiftDto x = a.get(i);
            GiftDtos.GiftDto y = b.get(i);
            if (x == y) continue;
            if (x == null || y == null) return false;
            if (!Objects.equals(x.id, y.id)
                    || !Objects.equals(x.iconUrl, y.iconUrl)
                    || x.coinPrice != y.coinPrice) {
                return false;
            }
        }
        return true;
    }

    static final class PageVH extends RecyclerView.ViewHolder {
        private final RecyclerView recycler;
        final GiftAdapter adapter;

        PageVH(@NonNull View itemView) {
            super(itemView);
            recycler = itemView.findViewById(R.id.rvGiftPage);
            adapter = new GiftAdapter(null);
            recycler.setLayoutManager(new GridLayoutManager(itemView.getContext(), 4));
            recycler.setNestedScrollingEnabled(true);
            recycler.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            recycler.setItemAnimator(null);
            recycler.setAdapter(adapter);
            // Vertical scroll owns the gesture; horizontal still lets ViewPager2 change tabs.
            final int touchSlop = ViewConfiguration.get(itemView.getContext()).getScaledTouchSlop();
            recycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                private float downX;
                private float downY;
                private boolean decided;

                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                     @NonNull MotionEvent e) {
                    int action = e.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        downX = e.getX();
                        downY = e.getY();
                        decided = false;
                        setParentsDisallow(rv, true);
                    } else if (action == MotionEvent.ACTION_MOVE && !decided) {
                        float dx = Math.abs(e.getX() - downX);
                        float dy = Math.abs(e.getY() - downY);
                        if (dx > touchSlop || dy > touchSlop) {
                            decided = true;
                            // Vertical → keep gifts scrolling; horizontal → release to ViewPager.
                            setParentsDisallow(rv, dy >= dx);
                        }
                    } else if (action == MotionEvent.ACTION_UP
                            || action == MotionEvent.ACTION_CANCEL) {
                        decided = false;
                        setParentsDisallow(rv, false);
                    }
                    return false;
                }
            });
        }

        private static void setParentsDisallow(@NonNull View child, boolean disallow) {
            ViewParent p = child.getParent();
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(disallow);
                p = p.getParent();
            }
        }

        void bind(List<GiftDtos.GiftDto> gifts, @Nullable String selectedId, Listener listener) {
            adapter.setListener(listener::onGiftClick);
            adapter.submit(gifts);
            adapter.setSelectedId(selectedId);
        }
    }
}
