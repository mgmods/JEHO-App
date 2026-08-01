package com.Dramizo.Series.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.gifts.GiftRecipient;
import com.Dramizo.Series.presentation.gifts.GiftRecipientAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * Mikoo GiftUserAvatarView — mic recipient strip + «الكل» chip.
 * Inflates {@code view_gift_user_avatar} merge into this LinearLayout.
 */
public class GiftUserAvatarView extends LinearLayout {
    public interface Listener {
        void onRecipientSelected(@NonNull GiftRecipient recipient);
        void onSelectAllMic();
    }

    private RecyclerView recycler;
    private TextView btnAllMic;
    private GiftRecipientAdapter adapter;
    private final List<GiftRecipient> recipients = new ArrayList<>();
    private boolean allMic;
    @Nullable private Listener listener;

    public GiftUserAvatarView(Context context) {
        super(context);
        init(context);
    }

    public GiftUserAvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GiftUserAvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_gift_user_avatar, this, true);
        recycler = findViewById(R.id.rv_gift_mic_list);
        btnAllMic = findViewById(R.id.blt_gift_all_mic);
        if (recycler != null) {
            recycler.setLayoutManager(new LinearLayoutManager(
                    context, LinearLayoutManager.HORIZONTAL, false));
        }
        if (btnAllMic != null) {
            btnAllMic.setBackgroundResource(R.drawable.bg_room_gift_all_mic);
            btnAllMic.setOnClickListener(v -> {
                allMic = true;
                if (adapter != null) adapter.setSelectAll(true);
                if (listener != null) listener.onSelectAllMic();
            });
        }
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setAgencyRoom(boolean agency) {
        if (adapter == null) {
            // Ensure adapter exists so flag sticks after first submit.
            ensureAdapter();
        }
        if (adapter != null) adapter.setAgencyRoom(agency);
    }

    private void ensureAdapter() {
        if (recycler == null || adapter != null) return;
        adapter = new GiftRecipientAdapter(new GiftRecipientAdapter.Listener() {
            @Override
            public void onRecipientSelected(GiftRecipient recipient) {
                allMic = false;
                setAvatarAllMic(false);
                if (listener != null) listener.onRecipientSelected(recipient);
            }

            @Override
            public void onSelectAllMic() {
                allMic = true;
                setAvatarAllMic(true);
                if (listener != null) listener.onSelectAllMic();
            }
        });
        recycler.setAdapter(adapter);
    }

    public void submit(@Nullable List<GiftRecipient> list,
                       @Nullable String selectedUserId,
                       boolean selectAll) {
        recipients.clear();
        if (list != null) recipients.addAll(list);
        allMic = selectAll;
        if (recycler == null) return;
        ensureAdapter();
        adapter.submit(recipients, selectedUserId, selectAll);
        setAvatarAllMic(selectAll);
    }

    public void setAvatarAllMic(boolean all) {
        allMic = all;
        if (btnAllMic == null) return;
        btnAllMic.setAlpha(all ? 1f : 0.55f);
        btnAllMic.setSelected(all);
    }

    public void setPersonalAvatar(boolean personal) {
        setAvatarAllMic(!personal);
    }

    @NonNull
    public List<GiftRecipient> getSelectMicAccounts() {
        if (allMic) return new ArrayList<>(recipients);
        List<GiftRecipient> out = new ArrayList<>();
        if (adapter != null) {
            String id = adapter.getSelectedUserId();
            for (GiftRecipient r : recipients) {
                if (r != null && id != null && id.equals(r.userId)) out.add(r);
            }
        }
        return out;
    }

    public boolean isAllMic() {
        return allMic;
    }
}
