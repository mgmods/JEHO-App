package com.Dramizo.Series.presentation.voiceroom;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.util.InviteReferralHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Mikoo {@code RoomMoreOperatorDialog}: tabs غرفة / أداة / تأثير + grid icons.
 * Layouts/icons copied from Mikoo APK resources.
 */
final class RoomMoreOperatorSheet {
    private RoomMoreOperatorSheet() {}

    interface Host {
        Context context();
        boolean isStaff();
        boolean isOwner();
        boolean roomLocked();
        boolean giftSoundsOn();
        boolean entryEffectsOn();
        boolean lowGiftEffectsOn();
        boolean charmOn();
        boolean chatZoneOn();
        boolean bannerOn();
        boolean micInteractOn();
        boolean roomSpeakerMuted();
        @Nullable String roomId();
        @Nullable String roomTitle();
        void onMoreAction(@NonNull String action);
    }

    static void show(@NonNull Host host) {
        Context ctx = host.context();
        // Mikoo RoomMoreOperatorDialog = bottom DialogFragment (not Material BottomSheet).
        // Material sheets clip height + show a top handle line — use full 398dp panel.
        Dialog dialog = new Dialog(ctx, R.style.MikooBottomPanelDialog);
        View sheet = LayoutInflater.from(ctx).inflate(R.layout.dialog_room_more_operator, null);
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.BOTTOM;
            lp.dimAmount = 0.45f;
            window.setAttributes(lp);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        TextView tvRoom = sheet.findViewById(R.id.tv_room);
        TextView tvTool = sheet.findViewById(R.id.tv_tool);
        TextView tvEffect = sheet.findViewById(R.id.tv_effect);
        ImageView ivRoom = sheet.findViewById(R.id.iv_room);
        ImageView ivTool = sheet.findViewById(R.id.iv_tool);
        ImageView ivEffect = sheet.findViewById(R.id.iv_effect);
        ViewPager pager = sheet.findViewById(R.id.viewPager);

        boolean staff = host.isStaff();
        List<List<Item>> pages = new ArrayList<>();
        if (staff) pages.add(buildRoomItems(host));
        pages.add(buildToolItems(host));
        if (staff) pages.add(buildEffectItems(host));

        // Guest: only Tool tab visible (Mikoo behavior).
        if (!staff) {
            tvRoom.setVisibility(View.GONE);
            ivRoom.setVisibility(View.GONE);
            tvEffect.setVisibility(View.GONE);
            ivEffect.setVisibility(View.GONE);
            tvTool.setTextColor(0xFFFFFFFF);
            ivTool.setVisibility(View.VISIBLE);
            ViewGroup.MarginLayoutParams toolLp =
                    (ViewGroup.MarginLayoutParams) tvTool.getLayoutParams();
            if (toolLp != null) {
                toolLp.setMarginStart(
                        ctx.getResources().getDimensionPixelSize(R.dimen.dp_32));
                tvTool.setLayoutParams(toolLp);
            }
        }

        MorePagerAdapter adapter = new MorePagerAdapter(pages, item -> {
            dialog.dismiss();
            host.onMoreAction(item.action);
        });
        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(Math.max(1, pages.size()));

        Runnable syncTabs = () -> {
            int page = pager.getCurrentItem();
            int roomPage = staff ? 0 : -1;
            int toolPage = staff ? 1 : 0;
            int effectPage = staff ? 2 : -1;
            selectTab(tvRoom, ivRoom, page == roomPage);
            selectTab(tvTool, ivTool, page == toolPage);
            selectTab(tvEffect, ivEffect, page == effectPage);
        };
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override public void onPageSelected(int position) { syncTabs.run(); }
        });
        syncTabs.run();

        if (staff) {
            tvRoom.setOnClickListener(v -> pager.setCurrentItem(0, true));
            tvTool.setOnClickListener(v -> pager.setCurrentItem(1, true));
            tvEffect.setOnClickListener(v -> pager.setCurrentItem(2, true));
        }

        dialog.show();
    }

    private static void selectTab(TextView tv, ImageView ind, boolean on) {
        if (tv == null || ind == null) return;
        if (tv.getVisibility() != View.VISIBLE) return;
        tv.setTextColor(on ? 0xFFFFFFFF : 0x4DFFFFFF);
        ind.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private static List<Item> buildRoomItems(Host host) {
        List<Item> list = new ArrayList<>();
        list.add(item(R.drawable.more_btn_set, R.string.setting, "settings"));
        boolean locked = host.roomLocked();
        list.add(item(
                locked ? R.drawable.more_btn_lock_kai : R.drawable.more_btn_lock,
                withState(host, R.string.lock, locked),
                "lock"));
        list.add(item(R.drawable.more_btn_mic, R.string.room_more_mic_mode, "mic_mode"));
        list.add(item(R.drawable.more_btn_theme, R.string.room_more_theme, "theme"));
        list.add(item(
                host.chatZoneOn()
                        ? R.drawable.icon_room_opera_public_screen_open
                        : R.drawable.icon_room_opera_public_screen_close,
                withState(host, R.string.room_more_chat_zone, host.chatZoneOn()),
                "chat_zone"));
        list.add(item(
                host.charmOn() ? R.drawable.icon_room_charm_open : R.drawable.icon_room_charm_close,
                withState(host, R.string.room_more_charm, host.charmOn()),
                "charm"));
        list.add(item(
                host.giftSoundsOn()
                        ? R.drawable.icon_room_opera_gift_sound_open
                        : R.drawable.icon_room_opera_gift_sound_close,
                withState(host, R.string.room_more_gift_sound, host.giftSoundsOn()),
                "gift_sound"));
        list.add(item(
                host.bannerOn()
                        ? R.drawable.icon_room_room_ban_open
                        : R.drawable.icon_room_room_ban_close,
                withState(host, R.string.room_more_banner, host.bannerOn()),
                "banner"));
        list.add(item(R.drawable.icon_room_opera_blacklist, R.string.room_more_blacklist, "blacklist"));
        if (host.isOwner()) {
            list.add(item(R.drawable.icon_room_opera_admin, R.string.room_more_admin, "admin"));
        }
        list.add(item(
                host.micInteractOn()
                        ? R.drawable.icon_room_seat_interaction_open
                        : R.drawable.icon_room_seat_interaction_close,
                withStateNl(host, R.string.room_more_mic_interact, host.micInteractOn()),
                "mic_interact"));
        list.add(item(R.drawable.icon_room_send_photo, R.string.room_more_photo, "photo"));
        return list;
    }

    private static List<Item> buildToolItems(Host host) {
        List<Item> list = new ArrayList<>();
        boolean muted = host.roomSpeakerMuted();
        list.add(item(
                muted ? R.drawable.mute_close : R.drawable.mute_open,
                muted ? R.string.room_more_mute_off : R.string.room_more_mute_on,
                "mute"));
        list.add(item(R.drawable.icon_room_opera_invatation_friend,
                R.string.room_more_invite_friends, "invite"));
        list.add(item(R.mipmap.icon_room_tool_link, R.string.room_more_copy_link, "copy_link"));
        list.add(item(R.mipmap.icon_room_tool_whatsapp, R.string.room_more_whatsapp, "whatsapp"));
        return list;
    }

    private static List<Item> buildEffectItems(Host host) {
        List<Item> list = new ArrayList<>();
        boolean entryOn = host.entryEffectsOn();
        list.add(item(
                entryOn
                        ? R.drawable.icon_room_opera_other_enter_effect_open
                        : R.drawable.icon_room_opera_other_enter_effect_close,
                entryOn ? R.string.room_more_entry_effect_off : R.string.room_more_entry_effect_on,
                "entry_effect"));
        list.add(item(
                host.lowGiftEffectsOn() ? R.drawable.low_open : R.drawable.low_close,
                R.string.room_more_low_gift,
                "low_gift"));
        return list;
    }

    private static CharSequence withState(Host host, @StringRes int label, boolean on) {
        return host.context().getString(label) + " "
                + host.context().getString(on ? R.string.room_more_str_on : R.string.room_more_str_off);
    }

    private static CharSequence withStateNl(Host host, @StringRes int label, boolean on) {
        return host.context().getString(label) + "\n"
                + host.context().getString(on ? R.string.room_more_str_on : R.string.room_more_str_off);
    }

    private static Item item(@DrawableRes int icon, @StringRes int title, String action) {
        Item i = new Item();
        i.icon = icon;
        i.titleRes = title;
        i.action = action;
        return i;
    }

    private static Item item(@DrawableRes int icon, CharSequence title, String action) {
        Item i = new Item();
        i.icon = icon;
        i.titleText = title;
        i.action = action;
        return i;
    }

    static void copyRoomLink(@NonNull Context ctx, @Nullable String roomId) {
        if (roomId == null || roomId.isEmpty()) return;
        String link = InviteReferralHelper.roomOpenUrl(roomId);
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("room", link));
            Toast.makeText(ctx, R.string.room_more_link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    static void shareWhatsApp(@NonNull Context ctx, @Nullable String roomId, @Nullable String title) {
        if (roomId == null || roomId.isEmpty()) return;
        String link = InviteReferralHelper.roomOpenUrl(roomId);
        String safeTitle = title != null && !title.isEmpty() ? title : "JEHO CHAT";
        String message = ctx.getString(R.string.share_room_external_message, safeTitle, link);
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.setPackage("com.whatsapp");
            i.putExtra(Intent.EXTRA_TEXT, message);
            ctx.startActivity(i);
        } catch (Exception e) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, message);
            ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.room_more_whatsapp)));
        }
    }

    private static final class Item {
        @DrawableRes int icon;
        @StringRes int titleRes;
        @Nullable CharSequence titleText;
        String action;
    }

    private interface Click { void onClick(Item item); }

    private static final class MorePagerAdapter extends PagerAdapter {
        private final List<List<Item>> pages;
        private final Click click;

        MorePagerAdapter(List<List<Item>> pages, Click click) {
            this.pages = pages;
            this.click = click;
        }

        @Override public int getCount() { return pages.size(); }

        @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View page = LayoutInflater.from(container.getContext())
                    .inflate(R.layout.fragment_room_more_item, container, false);
            RecyclerView rv = page.findViewById(R.id.recyclerView);
            rv.setLayoutManager(new GridLayoutManager(container.getContext(), 4));
            rv.setAdapter(new GridAdapter(pages.get(position), click));
            container.addView(page);
            return page;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }
    }

    private static final class GridAdapter extends RecyclerView.Adapter<GridAdapter.VH> {
        private final List<Item> items;
        private final Click click;

        GridAdapter(List<Item> items, Click click) {
            this.items = items;
            this.click = click;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_room_more, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Item item = items.get(position);
            h.icon.setImageResource(item.icon);
            if (item.titleText != null) h.title.setText(item.titleText);
            else h.title.setText(item.titleRes);
            h.itemView.setOnClickListener(v -> click.onClick(item));
        }

        @Override public int getItemCount() { return items.size(); }

        static final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            VH(View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.more_icon_image);
                title = itemView.findViewById(R.id.tv_more_title);
            }
        }
    }
}
