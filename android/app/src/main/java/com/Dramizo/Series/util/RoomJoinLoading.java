package com.Dramizo.Series.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.opensource.svgaplayer.SVGAImageView;

/** Mikoo RoomLoadingDialog: fullscreen dim + {@code svga_room_loading.svga}. */
public final class RoomJoinLoading {

    private RoomJoinLoading() {}

    @Nullable
    public static Dialog show(@Nullable Context context, @Nullable String message) {
        if (!(context instanceof Activity)) return null;
        Activity act = (Activity) context;
        if (act.isFinishing() || act.isDestroyed()) return null;
        Dialog d = new Dialog(act, android.R.style.Theme_Translucent_NoTitleBar);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View root = LayoutInflater.from(act).inflate(R.layout.dialog_room_loading, null, false);
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0f);
        }
        TextView tv = root.findViewById(R.id.tvRoomLoading);
        if (tv != null) {
            if (message != null && !message.isEmpty()) {
                tv.setText(message);
                tv.setVisibility(View.VISIBLE);
            } else {
                tv.setVisibility(View.GONE);
            }
        }
        SVGAImageView svga = root.findViewById(R.id.svRoomLoading);
        // Soft-bind SVGA async — hard load on main freezes low-end devices during room join.
        if (svga != null) {
            svga.post(() -> {
                try {
                    MikooLoadingAnim.bind(svga);
                } catch (Exception ignored) {
                }
            });
        }
        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);
        try {
            d.show();
        } catch (Exception ignored) {
            return null;
        }
        return d;
    }

    public static void dismiss(@Nullable Dialog d) {
        if (d == null) return;
        try {
            View root = d.findViewById(R.id.roomLoadingRoot);
            if (root != null) {
                SVGAImageView svga = root.findViewById(R.id.svRoomLoading);
                MikooLoadingAnim.stop(svga);
            }
            if (d.isShowing()) d.dismiss();
        } catch (Exception ignored) {
        }
    }
}
