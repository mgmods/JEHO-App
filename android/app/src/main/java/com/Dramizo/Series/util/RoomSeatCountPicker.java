package com.Dramizo.Series.util;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.Dramizo.Series.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Reusable visual seat-count picker for room creation and in-room seat management. */
public final class RoomSeatCountPicker {
    public interface Listener { void onConfirmed(int guestSeatCount); }

    private static final int[] COUNTS = {10, 15, 20, 30};
    private static final int GOLD = 0xFFE8D9A5;
    private static final int TEAL = 0xFF43D3A2;

    private RoomSeatCountPicker() {}

    public static void show(@NonNull Activity activity, int currentCount,
                            @NonNull Listener listener) {
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(activity);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_room_seat_count_picker, null);
        AuraDialogHelper.applyContent(root);
        dialog.setContentView(root);

        GridLayout grid = root.findViewById(R.id.gridSeatCountOptions);
        TextView selectedLabel = root.findViewById(R.id.tvSeatCountSelected);
        TextView confirm = root.findViewById(R.id.btnConfirmSeatCount);
        final int[] selected = {nearestCount(currentCount)};
        for (int i = 0; i < COUNTS.length; i++) {
            final int count = COUNTS[i];
            LinearLayout card = createCard(activity, count);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 2), GridLayout.spec(i % 2, 1f));
            lp.width = 0;
            lp.height = dp(activity, 138);
            lp.setMargins(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> {
                selected[0] = count;
                updateSelection(grid, selected[0]);
                selectedLabel.setText("تم اختيار " + count + " مقعدًا");
            });
            grid.addView(card);
        }
        updateSelection(grid, selected[0]);
        selectedLabel.setText("تم اختيار " + selected[0] + " مقعدًا");
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            listener.onConfirmed(selected[0]);
        });
        dialog.show();
    }

    private static LinearLayout createCard(Activity activity, int count) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(activity, 7), dp(activity, 7), dp(activity, 7), dp(activity, 6));
        card.setBackground(cardBackground(false));
        card.setTag(count);

        GridLayout preview = new GridLayout(activity);
        preview.setColumnCount(count == 30 ? 6 : 5);
        preview.setRowCount((count + preview.getColumnCount() - 1) / preview.getColumnCount());
        preview.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        preview.setUseDefaultMargins(false);
        int mini = count == 30 ? 13 : 16;
        for (int i = 0; i < count; i++) {
            FrameLayout seat = new FrameLayout(activity);
            GridLayout.LayoutParams seatLp = new GridLayout.LayoutParams();
            seatLp.width = dp(activity, mini);
            seatLp.height = dp(activity, mini);
            seatLp.setMargins(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
            seat.setLayoutParams(seatLp);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0xFF222B29);
            circle.setStroke(dp(activity, 1), 0xFFB9B28D);
            seat.setBackground(circle);

            ImageView mic = new ImageView(activity);
            mic.setImageResource(R.drawable.ic_pro_mic);
            mic.setColorFilter(GOLD);
            FrameLayout.LayoutParams micLp = new FrameLayout.LayoutParams(
                    dp(activity, mini - 6), dp(activity, mini - 6), Gravity.CENTER);
            seat.addView(mic, micLp);
            preview.addView(seat);
        }
        card.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(activity, 96)));

        TextView label = new TextView(activity);
        label.setText(String.valueOf(count));
        label.setTextColor(Color.WHITE);
        label.setTextSize(17);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 25));
        labelLp.topMargin = dp(activity, 2);
        card.addView(label, labelLp);
        return card;
    }

    private static void updateSelection(GridLayout grid, int selected) {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            boolean active = child.getTag() instanceof Integer && (int) child.getTag() == selected;
            child.setBackground(cardBackground(active));
            if (child instanceof LinearLayout) {
                for (int j = 0; j < ((LinearLayout) child).getChildCount(); j++) {
                    View item = ((LinearLayout) child).getChildAt(j);
                    if (item instanceof TextView) {
                        ((TextView) item).setTextColor(active ? TEAL : Color.WHITE);
                    }
                }
            }
        }
    }

    private static GradientDrawable cardBackground(boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? 0xFF173A33 : 0xFF292E2D);
        bg.setCornerRadius(18f);
        bg.setStroke(selected ? 2 : 1, selected ? TEAL : 0xFF3B4542);
        return bg;
    }

    private static int nearestCount(int value) {
        int best = COUNTS[0], distance = Math.abs(value - best);
        for (int count : COUNTS) {
            int next = Math.abs(value - count);
            if (next < distance) { best = count; distance = next; }
        }
        return best;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
