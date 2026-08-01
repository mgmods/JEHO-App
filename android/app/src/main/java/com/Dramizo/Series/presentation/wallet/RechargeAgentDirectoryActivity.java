package com.Dramizo.Series.presentation.wallet;

import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.CountryCatalog;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Contact a recharge agent — package must be selected before WhatsApp/Telegram
 * so order data is never sent as zeros.
 */
public class RechargeAgentDirectoryActivity extends ThemedActivity {

    public static final String EXTRA_SKU = "sku";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_COINS = "coins";
    public static final String EXTRA_BONUS = "bonus";
    public static final String EXTRA_PRICE = "price";
    public static final String EXTRA_PRICE_LABEL = "price_label";

    private AppContainer c;
    private TextView tvOrderSummary;
    private TextView tvPickPackageHint;
    private TextView tvEmpty;
    private ProgressBar progress;
    private LinearLayout countryChips;
    private RecyclerView recyclerPackages;
    private AgentAdapter adapter;
    private PackagePickAdapter packageAdapter;

    private final List<WalletDtos.AgentDirectoryEntry> allItems = new ArrayList<>();
    private final List<WalletDtos.RechargePackageDto> packages = new ArrayList<>();
    private final List<String> countries = new ArrayList<>();
    private String selectedCountry = null;

    private String sku;
    private String label;
    private int coins;
    private int bonus;
    private double price;
    private String priceLabel;

    public static Intent intent(Context ctx, @Nullable WalletDtos.RechargePackageDto pkg) {
        Intent i = new Intent(ctx, RechargeAgentDirectoryActivity.class);
        if (pkg != null) {
            i.putExtra(EXTRA_SKU, pkg.sku);
            i.putExtra(EXTRA_LABEL, pkg.label);
            i.putExtra(EXTRA_COINS, pkg.coins);
            i.putExtra(EXTRA_BONUS, pkg.bonusCoins);
            i.putExtra(EXTRA_PRICE, pkg.amountForVerify());
            i.putExtra(EXTRA_PRICE_LABEL, pkg.displayPrice());
        }
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recharge_agent_directory);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(findViewById(R.id.header));
        c = ContainerProvider.from(this);

        sku = getIntent().getStringExtra(EXTRA_SKU);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        coins = getIntent().getIntExtra(EXTRA_COINS, 0);
        bonus = getIntent().getIntExtra(EXTRA_BONUS, 0);
        price = getIntent().getDoubleExtra(EXTRA_PRICE, 0);
        priceLabel = getIntent().getStringExtra(EXTRA_PRICE_LABEL);
        if (priceLabel == null || priceLabel.isEmpty()) {
            priceLabel = price > 0 ? String.format(Locale.US, "$%.2f", price) : "";
        }

        tvOrderSummary = findViewById(R.id.tvOrderSummary);
        tvPickPackageHint = findViewById(R.id.tvPickPackageHint);
        tvEmpty = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);
        countryChips = findViewById(R.id.countryChips);
        recyclerPackages = findViewById(R.id.recyclerPackages);
        RecyclerView recycler = findViewById(R.id.recycler);

        findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());

        packageAdapter = new PackagePickAdapter(this::applyPackage);
        recyclerPackages.setLayoutManager(new GridLayoutManager(this, 3));
        recyclerPackages.setNestedScrollingEnabled(false);
        recyclerPackages.setAdapter(packageAdapter);

        adapter = new AgentAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        refreshOrderUi();
        if (!hasValidPackage()) {
            loadPackages();
        }
        loadDirectory();
    }

    private boolean hasValidPackage() {
        return coins > 0 && ((sku != null && !sku.isEmpty())
                || (label != null && !label.isEmpty()));
    }

    private void applyPackage(@NonNull WalletDtos.RechargePackageDto pkg) {
        sku = pkg.sku;
        label = pkg.label;
        coins = pkg.coins;
        bonus = pkg.bonusCoins;
        price = pkg.amountForVerify();
        priceLabel = pkg.displayPrice();
        refreshOrderUi();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "تم اختيار الباقة — يمكنك الآن مراسلة الوكيل", Toast.LENGTH_SHORT).show();
    }

    private void refreshOrderUi() {
        if (hasValidPackage()) {
            int total = coins + Math.max(0, bonus);
            String pkgName = label != null && !label.isEmpty() ? label : sku;
            tvOrderSummary.setText(String.format(Locale.US,
                    "%s · %,d كوينز%s",
                    pkgName, total,
                    priceLabel != null && !priceLabel.isEmpty() ? (" · " + priceLabel) : ""));
            if (tvPickPackageHint != null) tvPickPackageHint.setVisibility(View.GONE);
            if (recyclerPackages != null) recyclerPackages.setVisibility(View.GONE);
        } else {
            tvOrderSummary.setText("لم تُختر باقة بعد");
            if (tvPickPackageHint != null) tvPickPackageHint.setVisibility(View.VISIBLE);
            if (recyclerPackages != null) {
                recyclerPackages.setVisibility(packages.isEmpty() ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void loadPackages() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.PackagesResult> r =
                    ApiCall.execute(c.getWalletApi().packages());
            runOnUiThread(() -> {
                packages.clear();
                if (r.success && r.data != null && r.data.items != null) {
                    packages.addAll(r.data.items);
                }
                packageAdapter.submit(packages);
                refreshOrderUi();
            });
        });
    }

    private void loadDirectory() {
        progress.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.AgentDirectoryResult> result =
                    ApiCall.execute(c.getWalletApi().rechargeAgentDirectory(null));
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (!result.success || result.data == null
                        || result.data.items == null || result.data.items.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(result.error != null ? result.error
                            : "لا يوجد وكلاء شحن حالياً — أضفهم من لوحة التحكم");
                    return;
                }
                allItems.clear();
                allItems.addAll(result.data.items);
                countries.clear();
                if (result.data.countries != null) countries.addAll(result.data.countries);
                buildCountryChips();
                applyFilter();
            });
        });
    }

    private void buildCountryChips() {
        countryChips.removeAllViews();
        addChip("🌐 الكل", null, selectedCountry == null);
        for (String country : countries) {
            if (country == null || country.trim().isEmpty()) continue;
            addChip(CountryCatalog.labelWithFlag(country), country,
                    country.equalsIgnoreCase(selectedCountry));
        }
    }

    private void addChip(String title, String countryKey, boolean selected) {
        TextView chip = new TextView(this);
        chip.setText(title);
        chip.setTextColor(0xFFFFFFFF);
        chip.setTextSize(13f);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackgroundResource(selected
                ? R.drawable.bg_viewer_pill_rose
                : R.drawable.bg_room_tool_circle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(8));
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            selectedCountry = countryKey;
            buildCountryChips();
            applyFilter();
        });
        countryChips.addView(chip);
    }

    private void applyFilter() {
        List<WalletDtos.AgentDirectoryEntry> filtered = new ArrayList<>();
        for (WalletDtos.AgentDirectoryEntry e : allItems) {
            if (selectedCountry == null
                    || (e.country != null && e.country.equalsIgnoreCase(selectedCountry))) {
                filtered.add(e);
            }
        }
        adapter.submit(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        if (filtered.isEmpty()) tvEmpty.setText("لا يوجد وكلاء في هذه الدولة");
    }

    private String buildOrderMessage() {
        SessionManager session = c.getSessionManager();
        String displayName = session.getDisplayName();
        String publicId = null;
        AuthDtos.UserDto user = session.getUser();
        if (user != null) {
            publicId = user.displayPublicId();
            if ((displayName == null || displayName.isEmpty()) && user.displayName != null) {
                displayName = user.displayName;
            }
        }
        if (publicId == null || publicId.isEmpty()) {
            publicId = session.getUserId();
        }
        int total = coins + Math.max(0, bonus);
        String pkgName = label != null && !label.isEmpty() ? label : (sku != null ? sku : "باقة");
        StringBuilder sb = new StringBuilder();
        sb.append("مرحباً، أريد شراء كوينز من تطبيق JEHO CHAT").append('\n').append('\n');
        sb.append("📦 الباقة: ").append(pkgName).append('\n');
        sb.append("🪙 الكوينز: ").append(coins);
        if (bonus > 0) sb.append(" + هدية ").append(bonus);
        sb.append('\n');
        sb.append("✅ الإجمالي المطلوب شحنه: ").append(total).append(" كوينز").append('\n');
        if (priceLabel != null && !priceLabel.isEmpty()) {
            sb.append("💵 السعر: ").append(priceLabel).append('\n');
        } else if (price > 0) {
            sb.append("💵 السعر: $").append(String.format(Locale.US, "%.2f", price)).append('\n');
        }
        if (sku != null && !sku.isEmpty()) {
            sb.append("🔖 SKU: ").append(sku).append('\n');
        }
        sb.append('\n');
        sb.append("👤 اسمي: ").append(displayName != null ? displayName : "—").append('\n');
        sb.append("🆔 رقم المستخدم: ").append(publicId != null ? publicId : "—").append('\n');
        sb.append('\n');
        sb.append("الرجاء شحن حسابي بعد التحويل. شكراً لك 🙏");
        return sb.toString();
    }

    private boolean guardPackageSelected() {
        if (hasValidPackage()) return true;
        Toast.makeText(this, "اختر باقة أولاً ثم راسل الوكيل", Toast.LENGTH_LONG).show();
        if (recyclerPackages != null && !packages.isEmpty()) {
            recyclerPackages.setVisibility(View.VISIBLE);
            if (tvPickPackageHint != null) tvPickPackageHint.setVisibility(View.VISIBLE);
        } else {
            loadPackages();
        }
        return false;
    }

    private void openWhatsApp(String raw, String message) {
        if (!guardPackageSelected()) return;
        String digits = raw == null ? "" : raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        digits = digits.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            Toast.makeText(this, "رقم واتساب غير صالح", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.name());
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + digits + "?text=" + encoded));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح واتساب", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTelegram(String raw, String message) {
        if (!guardPackageSelected()) return;
        copyMessage(message);
        String value = raw == null ? "" : raw.trim();
        String url;
        if (value.startsWith("http://") || value.startsWith("https://")) {
            url = value;
        } else if (value.startsWith("@")) {
            url = "https://t.me/" + value.substring(1);
        } else if (value.matches("^[A-Za-z0-9_]{3,}$")) {
            url = "https://t.me/" + value;
        } else {
            String digits = value.replaceAll("[^0-9+]", "");
            url = "https://t.me/+" + digits.replace("+", "");
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            Toast.makeText(this, "تم نسخ تفاصيل الطلب — الصقها في تيليجرام", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح تيليجرام (الطلب منسوخ)", Toast.LENGTH_LONG).show();
        }
    }

    private void copyMessage(String message) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("hams_order", message));
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private final class PackagePickAdapter extends RecyclerView.Adapter<PackagePickAdapter.VH> {
        interface Listener { void onPick(WalletDtos.RechargePackageDto pkg); }
        private final List<WalletDtos.RechargePackageDto> items = new ArrayList<>();
        private final Listener listener;
        PackagePickAdapter(Listener listener) { this.listener = listener; }
        void submit(List<WalletDtos.RechargePackageDto> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(dp(8), dp(10), dp(8), dp(10));
            tv.setBackgroundResource(R.drawable.bg_wallet_section);
            tv.setTextColor(0xFF1A1A1A);
            tv.setTextSize(12f);
            tv.setGravity(android.view.Gravity.CENTER);
            return new VH(tv);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WalletDtos.RechargePackageDto pkg = items.get(position);
            String name = pkg.label != null ? pkg.label : (pkg.sku != null ? pkg.sku : "باقة");
            h.tv.setText(name + "\n" + pkg.coins + "🪙");
            boolean selected = coins > 0 && pkg.coins == coins
                    && ((pkg.sku != null && pkg.sku.equals(sku))
                    || (pkg.label != null && pkg.label.equals(label)));
            h.tv.setAlpha(selected ? 1f : 0.85f);
            h.tv.setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            h.itemView.setOnClickListener(v -> listener.onPick(pkg));
        }
        @Override public int getItemCount() { return items.size(); }
        static final class VH extends RecyclerView.ViewHolder {
            final TextView tv;
            VH(TextView tv) { super(tv); this.tv = tv; }
        }
    }

    private final class AgentAdapter extends RecyclerView.Adapter<AgentAdapter.VH> {
        private final List<WalletDtos.AgentDirectoryEntry> items = new ArrayList<>();

        void submit(List<WalletDtos.AgentDirectoryEntry> next) {
            items.clear();
            if (next != null) items.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recharge_agent, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WalletDtos.AgentDirectoryEntry e = items.get(position);
            String name = e.displayName != null && !e.displayName.trim().isEmpty()
                    ? e.displayName.trim() : "وكيل شحن";
            h.tvName.setText(name);
            h.tvCountry.setText(CountryCatalog.labelWithFlag(e.country));
            h.tvHint.setText(hasValidPackage()
                    ? "تواصل ليشحن حسابك — تفاصيل الباقة تُرسل تلقائياً"
                    : "اختر باقة أعلاه أولاً ثم اضغط واتساب");
            String letter = name.substring(0, 1).toUpperCase(Locale.US);
            h.tvAvatarLetter.setText(letter);
            android.widget.ImageView cover = h.itemView.findViewById(R.id.imgAgentCover);
            android.widget.ImageView avatar = h.itemView.findViewById(R.id.imgAgentAvatar);
            if (cover != null) cover.setVisibility(View.GONE);
            if (avatar != null) {
                if (e.avatarUrl != null && !e.avatarUrl.isEmpty()) {
                    avatar.setVisibility(View.VISIBLE);
                    h.tvAvatarLetter.setVisibility(View.GONE);
                    com.bumptech.glide.Glide.with(avatar)
                            .load(com.Dramizo.Series.util.AssetCatalog.absoluteUrl(e.avatarUrl))
                            .circleCrop()
                            .into(avatar);
                } else {
                    avatar.setVisibility(View.GONE);
                    h.tvAvatarLetter.setVisibility(View.VISIBLE);
                }
            }
            if (e.notes != null && !e.notes.trim().isEmpty()) {
                h.tvNotes.setVisibility(View.VISIBLE);
                h.tvNotes.setText(e.notes.trim());
            } else {
                h.tvNotes.setVisibility(View.GONE);
            }
            boolean hasWa = e.whatsapp != null && !e.whatsapp.trim().isEmpty();
            boolean hasTg = e.telegram != null && !e.telegram.trim().isEmpty();
            h.btnWhatsapp.setVisibility(hasWa ? View.VISIBLE : View.GONE);
            h.btnTelegram.setVisibility(hasTg ? View.VISIBLE : View.GONE);
            h.btnWhatsapp.setAlpha(hasValidPackage() ? 1f : 0.45f);
            h.btnTelegram.setAlpha(hasValidPackage() ? 1f : 0.45f);
            h.btnWhatsapp.setOnClickListener(v -> openWhatsApp(e.whatsapp, buildOrderMessage()));
            h.btnTelegram.setOnClickListener(v -> openTelegram(e.telegram, buildOrderMessage()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView tvAvatarLetter;
            final TextView tvName;
            final TextView tvCountry;
            final TextView tvHint;
            final TextView tvNotes;
            final View btnWhatsapp;
            final View btnTelegram;

            VH(@NonNull View itemView) {
                super(itemView);
                tvAvatarLetter = itemView.findViewById(R.id.tvAvatarLetter);
                tvName = itemView.findViewById(R.id.tvName);
                tvCountry = itemView.findViewById(R.id.tvCountry);
                tvHint = itemView.findViewById(R.id.tvHint);
                tvNotes = itemView.findViewById(R.id.tvNotes);
                btnWhatsapp = itemView.findViewById(R.id.btnWhatsapp);
                btnTelegram = itemView.findViewById(R.id.btnTelegram);
            }
        }
    }
}
