package com.Dramizo.Series.presentation.admin.preview;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.StaffRoleHelper;
import com.Dramizo.Series.presentation.common.ContainerProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Native role-aware hub. Real data and actions open in AdminConsoleActivity; server guards remain authoritative. */
public final class AdminPreviewActivity extends ThemedActivity {
    private static final int INK = Color.rgb(35, 35, 52);
    private static final int MUTED = Color.rgb(119, 120, 139);
    private static final int PURPLE = Color.rgb(111, 76, 210);
    private static final int PURPLE_LIGHT = Color.rgb(243, 239, 255);
    private static final int SURFACE = Color.WHITE;
    private static final int PAGE = Color.rgb(247, 247, 251);
    private static final int BORDER = Color.rgb(233, 232, 241);

    private LinearLayout root;
    private LinearLayout content;
    private LinearLayout moduleRail;
    private EditText search;
    private String activeSection = "overview";
    private String currentRole = StaffRoleHelper.NONE;
    private final List<Module> modules = Arrays.asList(
            new Module("overview", "نظرة عامة", "⌂", "الإحصائيات الحية"),
            new Module("users", "المستخدمون", "♙", "بحث وإدارة الحسابات"),
            new Module("rooms", "الغرف الصوتية", "♫", "الغرف والحالة والنشاط"),
            new Module("roles", "الرتب والصلاحيات", "✦", "حسابات الإدارة والصلاحيات"),
            new Module("agencies", "الوكالات", "▦", "إدارة الوكالات"),
            new Module("agencyApplications", "طلبات الوكالات", "✉", "مراجعة الطلبات"),
            new Module("rechargeApplications", "طلبات وكلاء الشحن", "▤", "قبول ورفض الطلبات"),
            new Module("rechargeAgents", "وكلاء الشحن", "◈", "إدارة الوكلاء"),
            new Module("recharges", "عمليات الشحن", "＄", "سجل الشحن"),
            new Module("withdrawals", "طلبات السحب", "↗", "مراجعة السحوبات"),
            new Module("walletTransactions", "حركة المحفظة", "◉", "سجل المعاملات"),
            new Module("walletAdjust", "تعديل رصيد المحفظة", "＋", "تعديل موثق مع سبب"),
            new Module("streams", "البث المباشر", "◍", "متابعة البث"),
            new Module("reports", "البلاغات والمراجعة", "⚑", "مراجعة البلاغات"),
            new Module("genderVerifications", "طلبات التحقق", "✓", "مراجعة طلبات التحقق"),
            new Module("operators", "مديرو الإدارة", "♜", "إدارة المدراء المحدودين"),
            new Module("store", "المتجر والعناصر", "◇", "VIP والإطارات والدخوليات"),
            new Module("cosmetics", "كتالوج العناصر", "▣", "العناصر التجميلية"),
            new Module("grantCosmetics", "إهداء الإطارات والدخوليات", "✧", "منح العناصر بمدة"),
            new Module("vanityIds", "الآي دي المميز", "＃", "كتالوج الآيديات"),
            new Module("vip", "عضويات VIP", "♛", "العضويات والخطط"),
            new Module("gifts", "كتالوج الهدايا", "♢", "الهدايا المتاحة"),
            new Module("toastManager", "التوستات والمؤثرات", "✦", "إعدادات التوست الحقيقية"),
            new Module("mysteriousMan", "الرجل الغامض", "◈", "إعدادات الهوية الموحدة"),
            new Module("luckyCannon", "مدفع المحظوظ", "💎", "إعدادات زر المحظوظ"),
            new Module("supportChest", "صندوق الدعم", "🎁", "الأهداف والمكافآت"),
            new Module("roomPkSettings", "تحديات PK", "⚔", "تفعيل وإعدادات التحديات"),
            new Module("messages", "الرسائل والإشعارات", "✉", "الأخبار والرسائل الإدارية"),
            new Module("notifications", "إرسال الرسائل", "✉", "رسائل عامة وخاصة"),
            new Module("logs", "سجل العمليات", "≡", "سجل التغييرات الإدارية"),
            new Module("staffIdentity", "هوية الرتب", "✦", "الشارات والإطارات الإدارية"),
            new Module("settings", "إعدادات اللوحة", "⚙", "فتح إعدادات الإدارة")
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AuthDtos.UserDto user = null;
        try {
            user = ContainerProvider.from(this).getSessionManager().getUser();
        } catch (Exception ignored) { }
        // The hub is available to authenticated staff, while every section is filtered by role.
        if (!StaffRoleHelper.isStaff(user)) {
            Toast.makeText(this, "مركز الإدارة مخصص لحسابات فريق JEHO", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        currentRole = StaffRoleHelper.normalize(user);
        buildScreen();
        renderSection(activeSection, "");
    }

    private void buildScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(14), dp(20), dp(14));
        top.setBackgroundColor(SURFACE);
        TextView eyebrow = label("JEHO · ADMIN STUDIO", 10, PURPLE, true);
        eyebrow.setLetterSpacing(.08f);
        top.addView(eyebrow);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = label("لوحة الإدارة التجريبية", 22, INK, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        titleRow.addView(title);
        String roleText = StaffRoleHelper.SUPER.equals(currentRole) ? "سوبر أدمن" : StaffRoleHelper.ADMIN.equals(currentRole) ? "أدمن" : "مانجر";
        TextView role = label(roleText, 11, PURPLE, true);
        role.setGravity(Gravity.CENTER);
        role.setPadding(dp(11), dp(7), dp(11), dp(7));
        role.setBackground(pill(PURPLE_LIGHT, 14, 0));
        titleRow.addView(role);
        top.addView(titleRow);
        TextView subtitle = label("تصميم أصلي · أقسام الإدارة المتصلة", 12, MUTED, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(5);
        top.addView(subtitle, subLp);
        root.addView(top);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(INK);
        search.setHintTextColor(MUTED);
        search.setHint("ابحث داخل أقسام النموذج...");
        search.setPadding(dp(15), 0, dp(15), 0);
        search.setBackground(pill(SURFACE, 14, BORDER));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(48));
        searchLp.setMargins(dp(16), dp(12), dp(16), dp(8));
        root.addView(search, searchLp);

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setFillViewport(false);
        navScroll.setHorizontalScrollBarEnabled(false);
        moduleRail = new LinearLayout(this);
        moduleRail.setOrientation(LinearLayout.VERTICAL);
        moduleRail.setPadding(dp(12), dp(4), dp(12), dp(4));
        navScroll.addView(moduleRail);
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, dp(64));
        root.addView(navScroll, navLp);
        rebuildModuleRail("");

        ScrollView bodyScroll = new ScrollView(this);
        bodyScroll.setFillViewport(true);
        bodyScroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(10), dp(16), dp(28));
        bodyScroll.addView(content);
        root.addView(bodyScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(10), dp(9), dp(10), dp(9));
        bottom.setBackgroundColor(SURFACE);
        TextView home = label("⌂  الرئيسية", 12, PURPLE, true);
        home.setGravity(Gravity.CENTER);
        home.setPadding(dp(18), dp(8), dp(18), dp(8));
        home.setBackground(pill(PURPLE_LIGHT, 18, 0));
        home.setOnClickListener(v -> openConnectedSection("overview"));
        TextView preview = label("◉  وضع المعاينة", 12, MUTED, true);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(18), dp(8), dp(18), dp(8));
        preview.setOnClickListener(v -> openConnectedSection("overview"));
        bottom.addView(home, new LinearLayout.LayoutParams(0, -2, 1f));
        bottom.addView(preview, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(bottom);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                rebuildModuleRail(s == null ? "" : s.toString());
                renderSection(activeSection, s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void rebuildModuleRail(String query) {
        if (moduleRail == null) return;
        moduleRail.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (Module module : modules) {
            if (!isModuleAllowed(module.key)) continue;
            if (!matches(module.title + " " + module.description, query)) continue;
            TextView chip = label(module.icon + "  " + module.title, 12,
                    activeSection.equals(module.key) ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13), dp(10), dp(13), dp(10));
            chip.setBackground(pill(activeSection.equals(module.key) ? PURPLE : SURFACE, 14,
                    activeSection.equals(module.key) ? PURPLE : BORDER));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(4), 0, dp(4), 0);
            chip.setOnClickListener(v -> openConnectedSection(module.key));
            row.addView(chip, lp);
        }
        moduleRail.addView(row);
    }

    /**
     * Keeps this light native landing screen while routing each management area to the
     * existing authenticated AdminConsoleActivity implementation. Server AdminGuard
     * remains the authority; this screen never performs privileged mutations itself.
     */
    private void openConnectedSection(String key) {
        if (!isModuleAllowed(key)) {
            Toast.makeText(this, "هذا القسم غير متاح لرتبتك", Toast.LENGTH_SHORT).show();
            return;
        }
        String target = targetSection(key);
        Intent intent = new Intent(this, com.Dramizo.Series.presentation.admin.AdminConsoleActivity.class);
        if (!"home".equals(target)) intent.putExtra("admin_initial_section", target);
        startActivity(intent);
    }

    private void selectSection(String key) {
        activeSection = key;
        String query = search == null ? "" : search.getText().toString();
        rebuildModuleRail(query);
        renderSection(key, query);
    }

    private void renderSection(String key, String query) {
        if (content == null) return;
        content.removeAllViews();
        Module module = findModule(key);
        if (module == null || !isModuleAllowed(key)) return;
        addSectionHeader(module.title, module.description);
        if ("overview".equals(key)) {
            addNotice("مركز إدارة JEHO", "هذه الواجهة تحافظ على التصميم المعتمد، بينما تُفتح البيانات الحقيقية والإجراءات من أقسام الإدارة المتصلة. تظهر لكل رتبة الأقسام المسموح بها فقط.");
            LinearLayout stats = new LinearLayout(this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.addView(statCard("رتبتك", StaffRoleHelper.SUPER.equals(currentRole) ? "سوبر" : StaffRoleHelper.ADMIN.equals(currentRole) ? "أدمن" : "مانجر", "صلاحيات معتمدة", PURPLE), weighted());
            stats.addView(statCard("مصدر البيانات", "الخادم", "مباشر", 0xFF248A73), weighted());
            stats.addView(statCard("الوضع", "آمن", "لا توجد بيانات وهمية", 0xFFE09A31), weighted());
            content.addView(stats);
            addGap(14);
            addCardTitle("أقسامك المتاحة", "اضغط على القسم لفتح بياناته الحقيقية وإجراءاته");
            for (Module item : modules) {
                if ("overview".equals(item.key) || !isModuleAllowed(item.key)
                        || !matches(item.title + " " + item.description, query)) continue;
                addModuleRow(item);
            }
            if (!hasModuleMatch(query)) addEmpty(query);
        } else {
            String target = targetSection(key);
            addNotice("القسم متصل بلوحة الإدارة", "سيتم فتح شاشة الإدارة الأصلية وقراءة البيانات الحالية من الخادم. أي إجراء سيخضع لصلاحيات رتبتك والتحقق من الخادم.");
            content.addView(makeActionButton("فتح " + module.title + " مباشرة", () -> openConnectedSection(key)));
            if ("cosmetics".equals(target) || "grantCosmetics".equals(target) || "gifts".equals(target)
                    || "toastManager".equals(target) || "staffIdentity".equals(target)) {
                addNotice("المعاينة الحية", "داخل القسم المتصل ستظهر الأصول التي يرجعها الخادم، مع معاينة مرئية للإطارات والهدايا وخلفيات التوستات قبل اعتماد الإعدادات.");
            }
        }
    }

    private TextView makeActionButton(String text, Runnable action) {
        TextView button = label("↗  " + text, 13, PURPLE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setBackground(pill(PURPLE_LIGHT, 14, BORDER));
        button.setOnClickListener(v -> action.run());
        addCard(button);
        return button;
    }

    private String targetSection(String key) {
        switch (key) {
            case "overview": return "home";
            case "roles": return "operators";
            case "store": return "cosmetics";
            case "messages": return "notifications";
            case "settings": return "roomPkSettings";
            default: return key;
        }
    }

    private boolean isModuleAllowed(String key) {
        String target = targetSection(key);
        if ("home".equals(target)) return true;
        if (StaffRoleHelper.SUPER.equals(currentRole)) return true;
        if (StaffRoleHelper.ADMIN.equals(currentRole)) {
            return Arrays.asList(
                    "users", "rooms", "streams", "reports", "agencies", "agencyApplications",
                    "cosmetics", "gifts", "grantCosmetics", "vip", "vanityIds"
            ).contains(target);
        }
        if (StaffRoleHelper.MANAGER.equals(currentRole)) {
            return Arrays.asList(
                    "rooms", "streams", "reports", "cosmetics", "gifts", "grantCosmetics"
            ).contains(target);
        }
        return false;
    }

    private void addSectionHeader(String title, String subtitle) {
        TextView t = label(title, 20, INK, true);
        content.addView(t);
        TextView s = label(subtitle, 12, MUTED, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(14);
        content.addView(s, lp);
    }

    private void addNotice(String title, String message) {
        LinearLayout box = card();
        box.setBackground(pill(PURPLE_LIGHT, 16, 0));
        addText(box, title, 13, PURPLE, true);
        addText(box, message, 12, INK, false);
        addCard(box);
    }

    private void addCardTitle(String title, String subtitle) {
        LinearLayout box = card();
        addText(box, title, 15, INK, true);
        addText(box, subtitle, 11, MUTED, false);
        addCard(box);
    }

    private void addModuleRow(Module module) {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label(module.icon, 20, PURPLE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(pill(PURPLE_LIGHT, 13, 0));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
        box.addView(icon, ilp);
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(12), 0, dp(8), 0);
        TextView title = label(module.title, 14, INK, true);
        TextView desc = label(module.description, 11, MUTED, false);
        textCol.addView(title);
        textCol.addView(desc);
        box.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = label("‹", 24, MUTED, false);
        box.addView(arrow);
        box.setOnClickListener(v -> openConnectedSection(module.key));
        addCard(box);
    }

    private void addRecord(String iconText, String title, String detail, String action) {
        if (!matches(iconText + " " + title + " " + detail + " " + action,
                search == null ? "" : search.getText().toString())) return;
        LinearLayout box = card();
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = label(iconText, 18, PURPLE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(pill(PURPLE_LIGHT, 13, 0));
        box.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(11), 0, dp(8), 0);
        textCol.addView(label(title, 13, INK, true));
        textCol.addView(label(detail, 11, MUTED, false));
        box.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView button = label(action, 11, PURPLE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), dp(8), dp(9), dp(8));
        button.setBackground(pill(PURPLE_LIGHT, 10, 0));
        box.addView(button);
        View.OnClickListener open = v -> {
            String target = activeSection;
            if ("overview".equals(target)) {
                if (title.contains("كتالوج")) target = "cosmetics";
                else if (title.contains("المستخدمين")) target = "users";
            }
            openConnectedSection(target);
        };
        button.setOnClickListener(open);
        box.setOnClickListener(open);
        addCard(box);
    }

    private void addSettingRow(String title, String value) {
        if (!matches(title + " " + value, search == null ? "" : search.getText().toString())) return;
        LinearLayout box = card();
        box.setOrientation(LinearLayout.VERTICAL);
        addText(box, title, 13, INK, true);
        addText(box, value, 12, MUTED, false);
        addCard(box);
    }

    private void addLocalAction(String title, String body) {
        TextView button = label("ⓘ  " + title, 13, PURPLE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setBackground(pill(SURFACE, 14, BORDER));
        button.setOnClickListener(v -> showInfo(title, body));
        addCard(button);
    }

    private View statCard(String title, String value, String hint, int accent) {
        LinearLayout box = card();
        box.setPadding(dp(10), dp(13), dp(10), dp(13));
        addText(box, title, 10, MUTED, false);
        addText(box, value, 19, INK, true);
        addText(box, hint, 10, accent, true);
        return box;
    }

    private void addEmpty(String query) {
        if (query == null || query.trim().isEmpty()) return;
        LinearLayout box = card();
        addText(box, "لا توجد نتائج مطابقة", 14, INK, true);
        addText(box, "جرّب كلمة بحث أخرى.", 12, MUTED, false);
        addCard(box);
    }

    private boolean hasModuleMatch(String query) {
        for (Module m : modules) if (!"overview".equals(m.key) && isModuleAllowed(m.key) && matches(m.title + " " + m.description, query)) return true;
        return false;
    }

    private Module findModule(String key) {
        for (Module m : modules) if (m.key.equals(key)) return m;
        return null;
    }

    private boolean matches(String value, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        box.setBackground(pill(SURFACE, 16, BORDER));
        return box;
    }

    private void addCard(View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(9);
        content.addView(view, lp);
    }

    private void addGap(int size) {
        View gap = new View(this);
        content.addView(gap, new LinearLayout.LayoutParams(1, dp(size)));
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private void addText(LinearLayout parent, String value, int size, int color, boolean bold) {
        TextView text = label(value, size, color, bold);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(3);
        parent.addView(text, lp);
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setFontFeatureSettings("kern");
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        else view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable pill(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("حسناً", null)
                .show();
    }

    private static final class Module {
        final String key;
        final String title;
        final String icon;
        final String description;
        Module(String key, String title, String icon, String description) {
            this.key = key;
            this.title = title;
            this.icon = icon;
            this.description = description;
        }
    }
}
