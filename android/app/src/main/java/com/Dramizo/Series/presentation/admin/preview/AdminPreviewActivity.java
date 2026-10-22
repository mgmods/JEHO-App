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

/**
 * Isolated native-only design preview. All records and actions are local demo data.
 * No AdminApi, Retrofit, backend, or production mutations are used by this screen.
 */
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
        // The preview is intentionally restricted to a real server-authorized super admin.
        if (!StaffRoleHelper.isSuper(user)) {
            Toast.makeText(this, "معاينة اللوحة متاحة للسوبر أدمن فقط", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
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
        TextView role = label("سوبر أدمن", 11, PURPLE, true);
        role.setGravity(Gravity.CENTER);
        role.setPadding(dp(11), dp(7), dp(11), dp(7));
        role.setBackground(pill(PURPLE_LIGHT, 14, 0));
        titleRow.addView(role);
        top.addView(titleRow);
        TextView subtitle = label("نموذج مستقل · بيانات تجريبية فقط", 12, MUTED, false);
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
        preview.setOnClickListener(v -> showInfo("وضع المعاينة", "كل العناصر والأزرار في هذه النسخة تجريبية محلية. لا يتم حفظ أي تغيير على الخادم."));
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
        String target;
        switch (key) {
            case "overview": target = null; break;
            case "roles": target = "operators"; break;
            case "store": target = "cosmetics"; break;
            case "messages": target = "notifications"; break;
            case "settings": target = "roomPkSettings"; break;
            default: target = key; break;
        }
        Intent intent = new Intent(this, com.Dramizo.Series.presentation.admin.AdminConsoleActivity.class);
        if (target != null) intent.putExtra("admin_initial_section", target);
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
        if (module == null) return;
        addSectionHeader(module.title, module.description);
        if ("overview".equals(key)) {
            addNotice("نسخة تجريبية معزولة", "هذه الشاشة أصلية من Android. البيانات والعناصر المعروضة أمثلة محلية، ولا تتصل بواجهات الإدارة الحقيقية.");
            LinearLayout stats = new LinearLayout(this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.addView(statCard("المستخدمون", "12,480", "+8.2%", PURPLE), weighted());
            stats.addView(statCard("الغرف النشطة", "186", "الآن", 0xFF248A73), weighted());
            stats.addView(statCard("طلبات المراجعة", "24", "للمعاينة", 0xFFE09A31), weighted());
            content.addView(stats);
            addGap(14);
            addCardTitle("الوصول السريع", "كل قسم يفتح شاشة فرعية داخل النموذج");
            for (Module item : modules) {
                if ("overview".equals(item.key) || !matches(item.title + " " + item.description, query)) continue;
                addModuleRow(item);
            }
            if (!hasModuleMatch(query)) addEmpty(query);
            addCardTitle("معاينة نشاط حديث", "بيانات وهمية للتصميم");
            addRecord("✦", "تحديث إطار تجريبي", "منذ دقيقتين · معاينة فقط", "عرض التفاصيل");
            addRecord("♙", "حساب تجريبي: نور", "المعرّف DEMO-1024 · نشط", "عرض الحساب");
        } else if ("users".equals(key)) {
            addNotice("قائمة مستخدمين تجريبية", "البحث والتفاصيل يعملان محلياً. الحظر وتعديل الرتب غير موصولين بالخادم.");
            addRecord("ن", "نور الشام", "ID: DEMO-1024 · مستوى 32 · VIP", "التفاصيل");
            addRecord("م", "محمد الورد", "ID: DEMO-2088 · مستوى 18 · عادي", "التفاصيل");
            addRecord("ل", "ليان", "ID: DEMO-3140 · مستوى 41 · VIP", "التفاصيل");
            addRecord("س", "سامر", "ID: DEMO-4771 · مستوى 12 · عادي", "التفاصيل");
            addLocalAction("اختبار البحث المحلي", "جرّب كتابة اسم أو معرّف تجريبي في شريط البحث بالأعلى.");
        } else if ("rooms".equals(key)) {
            addNotice("غرف وهمية", "لا يتم الانضمام إلى الغرف الحقيقية أو تعديل إعداداتها.");
            addRecord("♫", "جلسة السهرة", "ROOM-DEMO-01 · 6 / 12 مقعداً", "تفاصيل الغرفة");
            addRecord("♫", "أصدقاء للأبد", "ROOM-DEMO-02 · 9 / 12 مقعداً", "تفاصيل الغرفة");
            addRecord("♫", "استراحة JEHO", "ROOM-DEMO-03 · 3 / 12 مقعداً", "تفاصيل الغرفة");
        } else if ("roles".equals(key)) {
            addNotice("عرض الصلاحيات فقط", "الرتبة التجريبية هنا لا تمنح أي صلاحية حقيقية في النظام.");
            addRecord("✦", "سوبر أدمن تجريبي", "كل أقسام النموذج متاحة · بلا وصول إداري فعلي", "التفاصيل");
            addRecord("◆", "أدمن", "إشراف وحظر مؤقت حسب سياسة المنصة", "التفاصيل");
            addRecord("◇", "مانجر", "إدارة نطاق الغرف والبلاغات", "التفاصيل");
            addLocalAction("مصفوفة الصلاحيات", "في النسخة المعتمدة لاحقاً، يجب أن تبقى صلاحيات الإنتاج مفروضة من الخادم.");
        } else if ("store".equals(key)) {
            addNotice("معرض المتجر التجريبي", "العناصر التالية بطاقات تصميم فقط وليست عناصر قابلة للشراء.");
            addRecord("👑", "عضوية VIP", "تجربة عرض باقات العضوية", "معاينة");
            addRecord("▣", "إطار متحرك بنفسجي", "عنصر تجميلي تجريبي", "معاينة");
            addRecord("✧", "دخولية ملكية", "تأثير دخول تجريبي", "معاينة");
            addRecord("◈", "معرّف مميز", "نموذج عرض المعرّفات", "معاينة");
        } else if ("gifts".equals(key)) {
            addNotice("معاينة الهدايا والتوستات", "لا يتم إرسال هدايا ولا خصم عملات. خيارات المعاينة لا تحفظ إعدادات الداشبورد.");
            addRecord("🎁", "توست إرسال هدية", "اسم المرسل · الهدية · X", "معاينة التوست");
            addRecord("✦", "توست هدية محظوظة", "صورة · X · قيمة الربح", "معاينة التوست");
            addRecord("◇", "زر هدايا المحظوظ", "شكل الزر والحركة فقط", "معاينة");
            addLocalAction("اختبار موضع التوست", "هذه النسخة تعرض شكل عناصر الإدارة فقط؛ الربط الفعلي يأتي بعد موافقتك.");
        } else if ("messages".equals(key)) {
            addNotice("رسائل محلية فقط", "لا توجد رسائل بث أو إشعارات حقيقية في نموذج التصميم.");
            addRecord("◉", "الأخبار الرسمية", "معاينة بطاقة خبر", "عرض");
            addRecord("✉", "رسائل الإدارة", "معاينة بطاقة رسالة", "عرض");
            addRecord("♧", "الإشعارات", "معاينة قائمة الإشعارات", "عرض");
        } else if ("settings".equals(key)) {
            addNotice("إعدادات شكلية", "تغيير الخيارات هنا لا يغير إعدادات التطبيق أو الخادم.");
            addSettingRow("المظهر", "فاتح · أبيض ورمادي وبنفسجي");
            addSettingRow("اللغة", "العربية · اتجاه RTL");
            addSettingRow("مصدر البيانات", "محلي تجريبي");
            addSettingRow("اتصال API", "غير متصل في هذه المعاينة");
            addLocalAction("حدود الأمان", "لن تُنفذ أي عملية إنتاجية قبل اعتماد التصميم ومراجعة صلاحيات الخادم.");
        }
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
        View.OnClickListener open = v -> openConnectedSection(activeSection);
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
        for (Module m : modules) if (!"overview".equals(m.key) && matches(m.title + " " + m.description, query)) return true;
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
