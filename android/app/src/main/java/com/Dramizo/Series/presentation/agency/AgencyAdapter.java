package com.Dramizo.Series.presentation.agency;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ItemAgencyBinding;
import com.Dramizo.Series.util.AgencyVerifiedBadge;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class AgencyAdapter extends RecyclerView.Adapter<AgencyAdapter.VH> {
    public interface Listener {
        void onJoin(String agencyId);
        void onLeave(String agencyId);
        void onManage(String agencyId);
        /** Open live agency room when card is live / user taps enter. */
        default void onEnterLive(String agencyId, String openRoomId) {}
    }

    private final List<MiscDtos.AgencyDto> items = new ArrayList<>();
    private final Listener listener;
    private String myAgencyId;
    private boolean canLeave; // member but not owner
    private boolean canManage;

    public AgencyAdapter(Listener listener) { this.listener = listener; }

    public void submit(
            List<MiscDtos.AgencyDto> data,
            String myAgencyId,
            boolean canLeave,
            boolean canManage) {
        items.clear();
        if (data != null) items.addAll(data);
        this.myAgencyId = myAgencyId;
        this.canLeave = canLeave;
        this.canManage = canManage;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemAgencyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        MiscDtos.AgencyDto agency = items.get(position);
        holder.b.tvName.setText(agency.name != null ? agency.name : "");
        AgencyVerifiedBadge.bind(holder.b.tvName, null, agency);
        holder.b.tvDesc.setText(agency.description != null ? agency.description : "");

        boolean live = agency.isLive;
        int members = Math.max(0, agency.memberCount);
        int liveViewers = Math.max(0, agency.liveViewerCount);
        StringBuilder meta = new StringBuilder();
        if (agency.publicId != null && !agency.publicId.isEmpty()) {
            meta.append("ID ").append(agency.publicId);
        }
        if (meta.length() > 0) meta.append(" · ");
        meta.append(holder.itemView.getContext().getString(
                R.string.agency_card_members_fmt, members));
        if (live && liveViewers > 0) {
            meta.append(" · ").append(holder.itemView.getContext().getString(
                    R.string.agency_card_viewers_fmt, liveViewers));
        }
        holder.b.tvMembers.setText(meta.toString());

        if (holder.b.tvLiveBadge != null) {
            holder.b.tvLiveBadge.setVisibility(live ? View.VISIBLE : View.GONE);
        }
        if (holder.b.tvLiveViewers != null) {
            if (live && liveViewers > 0) {
                holder.b.tvLiveViewers.setVisibility(View.VISIBLE);
                holder.b.tvLiveViewers.setText(String.valueOf(liveViewers));
            } else {
                holder.b.tvLiveViewers.setVisibility(View.GONE);
            }
        }

        String cover = firstUrl(agency.coverUrl, agency.logoUrl);
        if (cover == null) {
            holder.b.imgCover.setImageResource(R.drawable.icon_agency);
        } else {
            Glide.with(holder.b.imgCover)
                    .load(AssetCatalog.absoluteUrl(cover))
                    .placeholder(R.drawable.icon_agency)
                    .error(R.drawable.icon_agency)
                    .into(holder.b.imgCover);
        }

        String logo = agency.logoUrl != null && !agency.logoUrl.isEmpty()
                ? agency.logoUrl : cover;
        if (logo == null || logo.isEmpty()) {
            holder.b.imgAgencyDefault.setImageResource(R.drawable.icon_agency);
        } else {
            Glide.with(holder.b.imgAgencyDefault)
                    .load(AssetCatalog.absoluteUrl(logo))
                    .placeholder(R.drawable.icon_agency)
                    .error(R.drawable.icon_agency)
                    .into(holder.b.imgAgencyDefault);
        }

        if (holder.b.imgFrame != null) {
            String frame = agency.frameUrl;
            if (frame != null && !frame.isEmpty()) {
                holder.b.imgFrame.setVisibility(View.VISIBLE);
                Glide.with(holder.b.imgFrame)
                        .load(AssetCatalog.absoluteUrl(frame))
                        .into(holder.b.imgFrame);
            } else {
                holder.b.imgFrame.setVisibility(View.GONE);
                holder.b.imgFrame.setImageDrawable(null);
            }
        }

        boolean mine = myAgencyId != null && myAgencyId.equals(agency.id);
        holder.itemView.setOnClickListener(v -> {
            if (live && agency.openRoomId != null && !agency.openRoomId.isEmpty()) {
                listener.onEnterLive(agency.id, agency.openRoomId);
            }
        });
        if (mine && canManage) {
            holder.b.btnJoin.setText(R.string.agency_manage);
            holder.b.btnJoin.setEnabled(true);
            holder.b.btnJoin.setOnClickListener(v -> listener.onManage(agency.id));
        } else if (mine && canLeave) {
            holder.b.btnJoin.setText(R.string.agency_leave_or_cancel);
            holder.b.btnJoin.setEnabled(true);
            holder.b.btnJoin.setOnClickListener(v -> listener.onLeave(agency.id));
        } else if (mine) {
            holder.b.btnJoin.setText(R.string.agency_joined_label);
            holder.b.btnJoin.setEnabled(false);
            holder.b.btnJoin.setOnClickListener(null);
        } else if (live) {
            holder.b.btnJoin.setText(R.string.agency_card_enter);
            holder.b.btnJoin.setEnabled(true);
            holder.b.btnJoin.setOnClickListener(v ->
                    listener.onEnterLive(agency.id, agency.openRoomId));
        } else if (myAgencyId != null) {
            holder.b.btnJoin.setText("—");
            holder.b.btnJoin.setEnabled(false);
            holder.b.btnJoin.setOnClickListener(null);
        } else {
            holder.b.btnJoin.setText(R.string.agency_join);
            holder.b.btnJoin.setEnabled(true);
            holder.b.btnJoin.setOnClickListener(v -> listener.onJoin(agency.id));
        }
    }

    private static String firstUrl(String... urls) {
        if (urls == null) return null;
        for (String u : urls) {
            if (u != null && !u.trim().isEmpty()) return u.trim();
        }
        return null;
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemAgencyBinding b;
        VH(ItemAgencyBinding b) { super(b.getRoot()); this.b = b; }
    }
}
