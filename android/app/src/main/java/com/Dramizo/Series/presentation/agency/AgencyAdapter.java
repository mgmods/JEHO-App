package com.Dramizo.Series.presentation.agency;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ItemAgencyBinding;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;

public class AgencyAdapter extends RecyclerView.Adapter<AgencyAdapter.VH> {
    public interface Listener {
        void onJoin(String agencyId);
        void onLeave(String agencyId);
        void onManage(String agencyId);
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
        holder.b.tvName.setText(agency.name);
        holder.b.tvDesc.setText(agency.description != null ? agency.description : "");
        holder.b.tvMembers.setText(
                agency.memberCount + " أعضاء · عمولة " + (int) agency.commissionPercent + "%");
        if (agency.logoUrl == null || agency.logoUrl.isEmpty()) {
            holder.b.imgAgencyDefault.setImageResource(R.drawable.icon_agency);
        } else {
            Glide.with(holder.b.imgAgencyDefault)
                    .load(agency.logoUrl)
                    .placeholder(R.drawable.icon_agency)
                    .error(R.drawable.icon_agency)
                    .into(holder.b.imgAgencyDefault);
        }
        boolean mine = myAgencyId != null && myAgencyId.equals(agency.id);
        if (mine && canManage) {
            holder.b.btnJoin.setText(R.string.agency_manage);
            holder.b.btnJoin.setEnabled(true);
            holder.b.btnJoin.setOnClickListener(v -> listener.onManage(agency.id));
        } else if (mine && canLeave) {
            holder.b.btnJoin.setText(R.string.agency_leave_or_cancel);
            holder.b.btnJoin.setEnabled(true);
            holder.b.btnJoin.setOnClickListener(v -> listener.onLeave(agency.id));
        } else if (mine) {
            holder.b.btnJoin.setText("منضم");
            holder.b.btnJoin.setEnabled(false);
            holder.b.btnJoin.setOnClickListener(null);
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

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemAgencyBinding b;
        VH(ItemAgencyBinding b) { super(b.getRoot()); this.b = b; }
    }
}
