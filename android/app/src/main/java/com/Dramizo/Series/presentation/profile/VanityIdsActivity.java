package com.Dramizo.Series.presentation.profile;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.VanityDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AppLoadingOverlay;
import com.Dramizo.Series.util.AuraDialogHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Special ID store — compact white list + search in the app bar.
 */
public class VanityIdsActivity extends ThemedActivity {

    private static final int C_BG = 0xFFFFFFFF;
    private static final int C_LINE = 0xFFEEEEEE;
    private static final int C_TEXT = 0xFF1A1A1A;
    private static final int C_MUTED = 0xFF595959;
    private static final int C_HINT = 0xFF9F9F9F;
    private static final int C_ACCENT = 0xFFFE2C55;
    private static final int C_SURFACE = 0xFFF7F7F7;

    private AppContainer c;
    private EditText etSearch;
    private TextView tvCoins;
    private LinearLayout listBox;
    private TextView tvEmpty;

    private final List<VanityDtos.VanityItem> allItems = new ArrayList<>();
    @Nullable private VanityDtos.VanityItem myLease;
    private long coinsBal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        c = ContainerProvider.from(this);
        setContentView(buildRoot());
        EdgeToEdgeHelper.keepAboveImeOnFocus(etSearch);
        load();
    }

    @NonNull
    private View buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(C_BG);

        LinearLayout content = new LinearLayout(this);
        content.setId(R.id.contentRoot);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content);

        content.addView(buildTopBar());
        content.addView(hairline());
        // Pure store — no profile avatar / name / frame / personal ID strip.

        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(listBox);

        tvEmpty = new TextView(this);
        tvEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvEmpty.setTextColor(C_HINT);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setPadding(dp(24), dp(40), dp(24), dp(40));
        tvEmpty.setVisibility(View.GONE);
        body.addView(tvEmpty);

        scroll.addView(body);
        content.addView(scroll);
        return root;
    }

    /** Back + search field in the bar (no giant search block). */
    @NonNull
    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        bar.setPadding(dp(2), 0, dp(10), 0);
        bar.setBackgroundColor(C_BG);

        ImageView back = new ImageView(this);
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setPadding(dp(11), dp(11), dp(11), dp(11));
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setColorFilter(C_TEXT);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(v -> navigateUp());
        bar.addView(back);

        etSearch = new EditText(this);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        etSearch.setLayoutParams(etLp);
        etSearch.setBackground(roundRect(C_SURFACE, 0, 18));
        etSearch.setHint("بحث عن آي دي…");
        etSearch.setHintTextColor(C_HINT);
        etSearch.setTextColor(C_TEXT);
        etSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etSearch.setSingleLine(true);
        etSearch.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        etSearch.setPadding(dp(12), 0, dp(12), 0);
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            rebuildList();
            return true;
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                rebuildList();
            }
        });
        bar.addView(etSearch);

        // Coins only — never avatar / name / frame / personal ID
        tvCoins = new TextView(this);
        tvCoins.setText("—");
        tvCoins.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvCoins.setTextColor(C_MUTED);
        tvCoins.setMaxLines(1);
        tvCoins.setPadding(dp(8), 0, dp(4), 0);
        bar.addView(tvCoins);
        return bar;
    }

    /**
     * Compact row: ID · meta | price · action
     * No tall backgrounds / no full-width hero strip.
     */
    @NonNull
    private View buildItemRow(@NonNull VanityDtos.VanityItem item) {
        String pid = item.publicId != null ? item.publicId : "—";
        boolean renew = myLease != null
                && item.publicId != null
                && item.publicId.equals(myLease.publicId)
                && myLease.active;
        long full = Math.max(0, item.priceCoins);
        long half = item.renewPriceCoins > 0 ? item.renewPriceCoins : (full + 1) / 2;
        long showPrice = renew ? half : full;
        int days = item.leaseDays > 0 ? item.leaseDays : 30;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.setBackgroundColor(C_BG);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mid.setPadding(0, 0, dp(10), 0);

        TextView tvId = new TextView(this);
        tvId.setText(pid);
        tvId.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvId.setTextColor(C_TEXT);
        tvId.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvId.setMaxLines(1);
        tvId.setEllipsize(null);
        mid.addView(tvId);

        String meta;
        if (renew) {
            String exp = myLease.expiresAt != null
                    ? ("ينتهي " + formatExpires(myLease.expiresAt) + " · ") : "";
            meta = exp + "تجديد " + days + " يوم · نصف السعر";
        } else {
            meta = tierLabel(pid) + " · " + days + " يوم";
        }
        TextView tvMeta = new TextView(this);
        tvMeta.setText(meta);
        tvMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvMeta.setTextColor(C_HINT);
        tvMeta.setMaxLines(1);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.topMargin = dp(2);
        tvMeta.setLayoutParams(metaLp);
        mid.addView(tvMeta);
        row.addView(mid);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvPrice = new TextView(this);
        tvPrice.setText(String.format(Locale.US, "%,d", showPrice));
        tvPrice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvPrice.setTextColor(C_TEXT);
        tvPrice.setTypeface(tvPrice.getTypeface(), Typeface.BOLD);
        tvPrice.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        priceLp.setMarginEnd(dp(10));
        tvPrice.setLayoutParams(priceLp);
        right.addView(tvPrice);

        // Wider, less pill — rectangular action (not “blob”)
        TextView action = new TextView(this);
        action.setText(renew ? "تجديد" : "شراء");
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        action.setTextColor(Color.WHITE);
        action.setTypeface(action.getTypeface(), Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setMinWidth(dp(92));
        action.setIncludeFontPadding(false);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        action.setLayoutParams(actLp);
        action.setPadding(dp(20), 0, dp(20), 0);
        action.setBackground(roundRect(C_ACCENT, 0, 8));
        action.setOnClickListener(v -> purchase(item));
        right.addView(action);
        row.addView(right);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(row);
        wrap.addView(hairline());
        return wrap;
    }

    @NonNull
    private View hairline() {
        View line = new View(this);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2 == 0 ? 1 : 1)));
        // 1 physical px when density high, else 1dp
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        line.setLayoutParams(lp);
        line.setBackgroundColor(C_LINE);
        return line;
    }

    private void rebuildList() {
        String q = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim() : "";
        listBox.removeAllViews();
        int count = 0;
        for (VanityDtos.VanityItem it : allItems) {
            if (it == null || it.publicId == null) continue;
            if (!q.isEmpty() && !it.publicId.contains(q)) continue;
            listBox.addView(buildItemRow(it));
            count++;
        }
        boolean empty = count == 0;
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            tvEmpty.setText(q.isEmpty()
                    ? "لا توجد آي ديات متاحة"
                    : "لا نتائج");
        }
    }

    private void refreshCoinsLabel() {
        if (tvCoins != null) {
            tvCoins.setText(String.format(Locale.US, "%,d ◆", coinsBal));
        }
    }

    private void load() {
        AppLoadingOverlay.showUntilReady(this);
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
            Result<AuthDtos.UserDto> me = ApiCall.execute(c.getUserApi().me());
            Result<VanityDtos.Catalog> cat = ApiCall.execute(c.getVanityApi().list(null));
            Result<VanityDtos.MineResult> mine = ApiCall.execute(c.getVanityApi().mine());
            if (me.success && me.data != null) {
                c.getSessionManager().updateCachedUser(me.data);
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                AppLoadingOverlay.hide(this);
                coinsBal = (w.success && w.data != null) ? w.data.coins : 0;
                myLease = mine.success && mine.data != null ? mine.data.item : null;
                refreshCoinsLabel();

                allItems.clear();
                if (cat.success && cat.data != null && cat.data.items != null) {
                    allItems.addAll(cat.data.items);
                }
                if (myLease != null && myLease.active && myLease.publicId != null) {
                    boolean listed = false;
                    for (VanityDtos.VanityItem it : allItems) {
                        if (myLease.publicId.equals(it.publicId)) {
                            listed = true;
                            break;
                        }
                    }
                    if (!listed) allItems.add(0, myLease);
                }
                Collections.sort(allItems, Comparator
                        .comparingInt((VanityDtos.VanityItem a) ->
                                a.publicId != null ? a.publicId.length() : 99)
                        .thenComparingLong(a -> a.priceCoins));
                if (myLease != null && myLease.active && myLease.publicId != null) {
                    for (int i = 0; i < allItems.size(); i++) {
                        if (myLease.publicId.equals(allItems.get(i).publicId)) {
                            allItems.add(0, allItems.remove(i));
                            break;
                        }
                    }
                }
                rebuildList();
                if (cat.error != null && allItems.isEmpty()) {
                    Toast.makeText(this, cat.error, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void purchase(VanityDtos.VanityItem item) {
        if (item == null || item.publicId == null) return;
        boolean renew = myLease != null
                && item.publicId.equals(myLease.publicId)
                && myLease.active;
        long full = Math.max(0, item.priceCoins);
        long price = renew
                ? (item.renewPriceCoins > 0 ? item.renewPriceCoins : (full + 1) / 2)
                : full;
        int days = item.leaseDays > 0 ? item.leaseDays : 30;
        String title = renew ? ("تجديد " + item.publicId) : ("شراء " + item.publicId);
        String msg = renew
                ? String.format(Locale.US,
                "تجديد %d يوماً بنصف السعر:\n%,d كوينز (بدلاً من %,d)",
                days, price, full)
                : String.format(Locale.US,
                "مدة الاشتراك %d يوماً.\nسيتم خصم %,d كوينز ويظهر الرقم %s بدل آيديك.",
                days, price, item.publicId);
        AuraDialogHelper.confirm(this, title, msg, renew ? "تجديد" : "شراء",
                () -> c.getIoExecutor().execute(() -> {
                    ApiCall.execute(c.getVanityApi().reserve(item.publicId));
                    Result<VanityDtos.PurchaseResult> r =
                            ApiCall.execute(c.getVanityApi().purchase(item.publicId));
                    runOnUiThread(() -> {
                        if (r.success) {
                            Toast.makeText(this,
                                    r.data != null && r.data.renew
                                            ? "تم تجديد الآي دي (+" + days + " يوم)"
                                            : "تم شراء الآي دي لمدة " + days + " يوم",
                                    Toast.LENGTH_SHORT).show();
                            load();
                        } else {
                            com.Dramizo.Series.util.BalanceRedirect.handle(this, r.error);
                        }
                    });
                }),
                getString(android.R.string.cancel),
                null);
    }

    @NonNull
    private GradientDrawable roundRect(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String formatExpires(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        String s = iso.replace('T', ' ').replace("Z", "");
        if (s.length() > 16) s = s.substring(0, 16);
        return s;
    }

    private static String tierLabel(String publicId) {
        int n = publicId != null ? publicId.length() : 0;
        if (n > 0 && n <= 4) return "نادر جداً";
        if (n <= 6) return "نادر";
        if (n <= 8) return "مميز";
        return "كلاسيك";
    }
}
