package com.Dramizo.Series.util;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Standard Material dialogs / bottom sheets — keep configuration minimal so sheets
 * stay stable across Android versions (incl. Samsung One UI).
 */
public final class AuraDialogHelper {
    public static final float MAX_HEIGHT_RATIO = 0.88f;

    private AuraDialogHelper() {}

    public interface OnConfirm {
        void run();
    }

    public interface OnPrompt {
        void onResult(@NonNull String value);
    }

    public interface OnItemSelected {
        void onItem(int index);
    }

    public interface OnMultiChoiceConfirmed {
        void onResult(@NonNull boolean[] selected);
    }

    @NonNull
    private static com.google.android.material.dialog.MaterialAlertDialogBuilder alert(@NonNull Context ctx) {
        // Default Material3 alert — no custom overlay (works on all Android versions).
        return new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx);
    }

    @NonNull
    public static BottomSheetDialog bottomSheet(@NonNull Context ctx) {
        // Theme comes from activity bottomSheetDialogTheme (Material3 default).
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        dialog.setOnShowListener(d -> configureShown(dialog));
        return dialog;
    }

    /** Light setup only — avoid forced expand / inset loops that freeze some devices. */
    public static void configureShown(@NonNull BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;

        ViewGroup.LayoutParams lp = sheet.getLayoutParams();
        if (lp != null) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            sheet.setLayoutParams(lp);
        }

        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setFitToContents(true);
        behavior.setSkipCollapsed(true);
        behavior.setDraggable(true);
        int screenH = sheet.getResources().getDisplayMetrics().heightPixels;
        behavior.setMaxHeight(Math.round(screenH * MAX_HEIGHT_RATIO));
        // Let Material open naturally — do not force STATE_EXPANDED in a post() loop.
    }

    /** Keep for callers; do not restyle aggressively. */
    public static void applyContent(@NonNull View content) {
        if (content == null) return;
        content.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        if (lp != null && lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            content.setLayoutParams(lp);
        }
    }

    /**
     * Let a RecyclerView own vertical scrolls inside a bottom sheet
     * (prevents the sheet from stealing swipe / freezing scroll).
     */
    public static void ownVerticalScroll(@Nullable RecyclerView recycler) {
        if (recycler == null) return;
        recycler.setNestedScrollingEnabled(true);
        recycler.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        recycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                 @NonNull android.view.MotionEvent e) {
                int action = e.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN
                        || action == android.view.MotionEvent.ACTION_MOVE) {
                    ViewParentSafe.requestDisallow(rv);
                }
                return false;
            }
        });
    }

    /** Disable sheet drag so inner lists can scroll freely. */
    public static void lockSheetDrag(@NonNull BottomSheetDialog dialog) {
        dialog.setOnShowListener(d -> {
            configureShown(dialog);
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            BottomSheetBehavior.from(sheet).setDraggable(false);
        });
    }

    @NonNull
    public static BottomSheetDialog showContent(@NonNull Context ctx, @NonNull View content) {
        BottomSheetDialog dialog = bottomSheet(ctx);
        applyContent(content);
        dialog.setContentView(content);
        dialog.show();
        return dialog;
    }

    public static void confirm(
            @NonNull Context ctx,
            @Nullable CharSequence title,
            @Nullable CharSequence message,
            @Nullable CharSequence positive,
            @Nullable OnConfirm onPositive,
            @Nullable CharSequence negative,
            @Nullable OnConfirm onNegative) {
        confirm(ctx, title, message, positive, onPositive, negative, onNegative, true);
    }

    public static void confirm(
            @NonNull Context ctx,
            @Nullable CharSequence title,
            @Nullable CharSequence message,
            @Nullable CharSequence positive,
            @Nullable OnConfirm onPositive,
            @Nullable CharSequence negative,
            @Nullable OnConfirm onNegative,
            boolean cancelable) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder b = alert(ctx);
        if (title != null && title.length() > 0) b.setTitle(title);
        if (message != null && message.length() > 0) b.setMessage(message);
        b.setCancelable(cancelable);
        b.setPositiveButton(
                positive != null ? positive : ctx.getString(android.R.string.ok),
                (d, w) -> {
                    if (onPositive != null) onPositive.run();
                });
        if (negative != null && negative.length() > 0) {
            b.setNegativeButton(negative, (d, w) -> {
                if (onNegative != null) onNegative.run();
            });
        }
        b.show();
    }

    public static void confirmRes(
            @NonNull Context ctx,
            @StringRes int title,
            @StringRes int message,
            @StringRes int positive,
            @Nullable OnConfirm onPositive) {
        confirm(ctx,
                ctx.getString(title),
                ctx.getString(message),
                ctx.getString(positive),
                onPositive,
                ctx.getString(android.R.string.cancel),
                null);
    }

    public static void message(
            @NonNull Context ctx,
            @Nullable CharSequence title,
            @Nullable CharSequence message) {
        confirm(ctx, title, message, ctx.getString(android.R.string.ok), null, null, null);
    }

    public static void list(
            @NonNull Context ctx,
            @Nullable CharSequence title,
            @NonNull CharSequence[] items,
            @Nullable OnItemSelected onItem) {
        alert(ctx)
                .setTitle(title)
                .setItems(items, (d, which) -> {
                    if (onItem != null) onItem.onItem(which);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void multiChoice(
            @NonNull Context ctx,
            @Nullable CharSequence title,
            @NonNull CharSequence[] items,
            @NonNull boolean[] initial,
            @Nullable OnMultiChoiceConfirmed onConfirm) {
        boolean[] selected = new boolean[items.length];
        System.arraycopy(initial, 0, selected, 0, Math.min(initial.length, selected.length));
        alert(ctx)
                .setTitle(title)
                .setMultiChoiceItems(items, selected, (d, which, isChecked) -> selected[which] = isChecked)
                .setPositiveButton("حفظ", (d, w) -> {
                    if (onConfirm != null) onConfirm.onResult(selected.clone());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void prompt(
            @NonNull Context ctx,
            @Nullable CharSequence title,
            @Nullable CharSequence message,
            @Nullable CharSequence hint,
            int inputType,
            @Nullable CharSequence positive,
            @Nullable OnPrompt onOk) {
        final EditText input = new EditText(ctx);
        input.setInputType(inputType != 0 ? inputType : InputType.TYPE_CLASS_TEXT);
        if (hint != null) input.setHint(hint);
        int pad = dp(ctx, 20);
        input.setPadding(pad, pad / 2, pad, pad / 2);
        FrameLayout wrap = new FrameLayout(ctx);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = pad;
        lp.rightMargin = pad;
        wrap.addView(input, lp);
        alert(ctx)
                .setTitle(title)
                .setMessage(message)
                .setView(wrap)
                .setPositiveButton(
                        positive != null ? positive : ctx.getString(android.R.string.ok),
                        (d, w) -> {
                            String value = input.getText() != null
                                    ? input.getText().toString().trim() : "";
                            if (onOk != null) onOk.onResult(value);
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private static int dp(@NonNull Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private static final class ViewParentSafe {
        static void requestDisallow(@NonNull View view) {
            android.view.ViewParent parent = view.getParent();
            if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        }
    }
}
