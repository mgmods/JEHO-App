package com.Dramizo.Series.presentation.cp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.UserProfileCardSheet;
import com.Dramizo.Series.presentation.invite.InvitationActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native Android CP center. The legacy cp-center.html asset is intentionally retained
 * for compatibility/history, but is no longer loaded by this Activity.
 */
public class CpCenterActivity extends ThemedActivity {
    public static final String EXTRA_VIEW = "cp_html_view";
    public static final String EXTRA_TYPE = "cp_html_type";

    private static final int INK = Color.rgb(66, 36, 57);
    private static final int MUTED = Color.rgb(143, 111, 129);
    private static final int ROSE = Color.rgb(190, 55, 111);
    private static final int PURPLE = Color.rgb(113, 65, 167);
    private static final int LINE = Color.rgb(241, 218, 230);
    private static final int PAPER = Color.rgb(255, 248, 252);

    private AppContainer c;
    private final Gson gson = new Gson();
    private LinearLayout root;
    private LinearLayout screenContent;
    private LinearLayout statsRow;
    private TextView activeStat;
    private TextView incomingStat;
    private TextView sentStat;
    private final List<Map<String, Object>> homeData = new ArrayList<>();
    private final List<Map<String, Object>> incomingData = new ArrayList<>();
    private final List<Map<String, Object>> sentData = new ArrayList<>();
    private final List<Map<String, Object>> discoverData = new ArrayList<>();
    private final List<Map<String, Object>> catalogData = new ArrayList<>();
    private final List<Map<String, Object>> cpCosmeticsData = new ArrayList<>();
    private final List<Map<String, Object>> cpInventoryData = new ArrayList<>();
    private final List<Map<String, Object>> memoriesData = new ArrayList<>();
    private final List<Map<String, Object>> tasksData = new ArrayList<>();
    private String currentView = "home";
    private String currentType = "love";
    private String discoverType = "";
    private boolean loadingHome;
    private boolean loadingRequests;
    private boolean loadingDiscover;
    private boolean loadingCatalog;
    private boolean loadingCpCosmetics;
    private String busyCosmeticId = "";
    private boolean loadingMemories;
    private boolean loadingTasks;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        c = ContainerProvider.from(this);
        currentView = safeView(getIntent().getStringExtra(EXTRA_VIEW));
        String initialType = safeType(getIntent().getStringExtra(EXTRA_TYPE));
        if (!initialType.isEmpty()) {
            currentType = initialType;
            discoverType = initialType;
        }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(255, 249, 251), Color.rgb(250, 240, 255), Color.rgb(255, 244, 246)}));

        setContentView(root);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(root);

        buildHeader();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildHero(page);
        buildStats(page);
        buildNavigation(page);
        updateNavigation();
        screenContent = new LinearLayout(this);
        screenContent.setOrientation(LinearLayout.VERTICAL);
        page.addView(screenContent, matchWrap());

        renderScreen();
        loadHome();
        loadRequests();
        if ("discover".equals(currentView) || "leaderboard".equals(currentView)) loadDiscover(discoverType);
        if ("store".equals(currentView)) { loadCatalog(); loadCpCosmetics(); }
        if ("memories".equals(currentView)) loadMemories();
        if ("tasks".equals(currentView)) loadTasks();
    }

    private void buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(10), dp(4), dp(12), dp(4));
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        TextView back = text("‹", 34, ROSE, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.rgb(252, 231, 241), dp(15), LINE));
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setContentDescription("رجوع");
        back.setOnClickListener(v -> finish());

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1f);
        titleLp.setMargins(dp(10), 0, dp(10), 0);
        bar.addView(titles, titleLp);
        TextView title = text("عالم CP", 19, INK, true);
        title.setGravity(Gravity.CENTER);
        titles.addView(title);
        TextView subtitle = text("روابط تجمع القلوب", 11, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        titles.addView(subtitle);

        TextView refresh = text("↻", 25, ROSE, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(round(Color.WHITE, dp(15), LINE));
        bar.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));
        refresh.setContentDescription("تحديث البيانات");
        refresh.setOnClickListener(v -> refreshAll());
    }

    private void buildHero(LinearLayout page) {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(19), dp(20), dp(20));
        hero.setBackground(roundGradient(new int[]{0xFF9E245C, 0xFFD94383, 0xFFFF8DB8}, dp(25)));
        hero.setElevation(dp(3));
        page.addView(hero, matchWrap());

        TextView overline = text("JEHO  •  CP UNIVERSE", 10, 0xFFFFE8F0, true);
        overline.setLetterSpacing(0.08f);
        hero.addView(overline);

        TextView heading = text("علاقتك… حكاية تستحق أن تُروى ♡", 21, Color.WHITE, true);
        heading.setPadding(0, dp(8), 0, dp(3));
        hero.addView(heading);

        TextView description = text(
                "تابع روابطك وطلباتك، واصنعوا معاً ذكريات وإنجازات داخل JEHO.",
                12, 0xFFFFF0F5, false);
        description.setLineSpacing(dp(3), 1f);
        hero.addView(description);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = dp(14);
        hero.addView(actions, actionsLp);
        TextView start = text("＋  ابدأ رابطاً جديداً", 13, ROSE, true);
        start.setGravity(Gravity.CENTER);
        start.setPadding(dp(14), dp(11), dp(14), dp(11));
        start.setBackground(round(Color.WHITE, dp(14), Color.TRANSPARENT));
        actions.addView(start, new LinearLayout.LayoutParams(0, -2, 1f));
        start.setOnClickListener(v -> showView("send"));

        TextView invite = text("رمز الدعوة", 12, Color.WHITE, true);
        invite.setGravity(Gravity.CENTER);
        invite.setPadding(dp(12), dp(11), dp(12), dp(11));
        invite.setBackground(round(0x35FFFFFF, dp(14), 0x55FFFFFF));
        LinearLayout.LayoutParams inviteLp = new LinearLayout.LayoutParams(-2, -2);
        inviteLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(invite, inviteLp);
        invite.setOnClickListener(v -> startActivity(new Intent(this, InvitationActivity.class)));
    }

    private void buildStats(LinearLayout page) {
        statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.topMargin = dp(12);
        rowLp.bottomMargin = dp(10);
        page.addView(statsRow, rowLp);
        activeStat = addStat(statsRow, "—", "روابطي النشطة");
        incomingStat = addStat(statsRow, "—", "طلبات واردة");
        sentStat = addStat(statsRow, "—", "طلبات مرسلة");
    }

    private TextView addStat(LinearLayout row, String value, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(5), dp(13), dp(5), dp(13));
        card.setBackground(roundGradient(new int[]{Color.WHITE, 0xFFFFF0F6}, dp(22)));
        card.setElevation(dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(116), 1f);
        lp.setMargins(dp(4), dp(2), dp(4), dp(2));
        row.addView(card, lp);
        TextView heart = text("♥", 16, 0xFFE05B91, true);
        heart.setGravity(Gravity.CENTER);
        heart.setBackground(roundGradient(new int[]{0xFFFFD8E8, 0xFFFFEFF6}, dp(17)));
        LinearLayout.LayoutParams heartLp = new LinearLayout.LayoutParams(dp(35), dp(31));
        heartLp.bottomMargin = dp(6);
        card.addView(heart, heartLp);
        TextView number = text(value, 25, 0xFFB82F6C, true);
        number.setGravity(Gravity.CENTER);
        card.addView(number);
        TextView caption = text(label, 11, INK, true);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(4), 0, 0);
        card.addView(caption);
        return number;
    }

    private void buildNavigation(LinearLayout page) {
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.bottomMargin = dp(8);
        page.addView(horizontal, hp);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -2));
        addNav(tabs, "الرئيسية", "home");
        addNav(tabs, "إرسال طلب", "send");
        addNav(tabs, "الواردة", "incoming");
        addNav(tabs, "المرسلة", "sent");
        addNav(tabs, "العلاقات", "discover");
        addNav(tabs, "المتجر", "store");
        addNav(tabs, "المهام", "tasks");
        addNav(tabs, "الترتيب", "leaderboard");
        addNav(tabs, "الذكريات", "memories");
        addNav(tabs, "الإعدادات", "settings");
    }

    private void addNav(LinearLayout tabs, String label, String view) {
        TextView chip = text(label, 12, INK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(15), dp(10), dp(15), dp(10));
        chip.setBackground(round("home".equals(currentView) && "home".equals(view)
                ? ROSE : Color.WHITE, dp(22), LINE));
        if ("home".equals(currentView) && "home".equals(view)) chip.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, dp(7), 0);
        tabs.addView(chip, lp);
        chip.setOnClickListener(v -> showView(view));
        chip.setTag("cp_nav_" + view);
    }

    private void updateNavigation() {
        if (root == null) return;
        // Navigation chips are updated in-place, without rebuilding the screen shell.
        updateNavTree(root);
    }

    private void updateNavTree(View view) {
        if (view instanceof TextView) {
            Object tag = view.getTag();
            if (tag instanceof String && ((String) tag).startsWith("cp_nav_")) {
                String key = ((String) tag).substring("cp_nav_".length());
                boolean selected = key.equals(currentView);
                view.setBackground(round(selected ? ROSE : Color.WHITE, dp(22), LINE));
                ((TextView) view).setTextColor(selected ? Color.WHITE : INK);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) updateNavTree(group.getChildAt(i));
        }
    }

    private void showView(String view) {
        currentView = safeView(view);
        updateNavigation();
        renderScreen();
        if ("home".equals(currentView)) loadHome();
        else if ("incoming".equals(currentView) || "sent".equals(currentView)) loadRequests();
        else if ("discover".equals(currentView) || "leaderboard".equals(currentView)) loadDiscover(discoverType);
        else if ("store".equals(currentView)) { loadCatalog(); loadCpCosmetics(); }
        else if ("memories".equals(currentView)) loadMemories();
        else if ("tasks".equals(currentView)) loadTasks();
    }

    private void renderScreen() {
        if (screenContent == null) return;
        screenContent.removeAllViews();
        updateStats();
        switch (currentView) {
            case "send": renderSend(); break;
            case "incoming": renderIncoming(); break;
            case "sent": renderSent(); break;
            case "discover": renderDiscover(); break;
            case "store": renderStore(); break;
            case "tasks": renderTasks(); break;
            case "leaderboard": renderLeaderboard(); break;
            case "memories": renderMemories(); break;
            case "settings": renderSettings(); break;
            default: renderHome(); break;
        }
    }

    private void renderHome() {
        sectionTitle("روابطك", "مساحتكما الخاصة تبدأ من هنا");
        List<Map<String, Object>> active = new ArrayList<>();
        for (Map<String, Object> row : homeData) if ("active".equals(str(row.get("status")))) active.add(row);
        if (active.isEmpty()) {
            LinearLayout welcome = panel();
            welcome.setGravity(Gravity.CENTER);
            welcome.setPadding(dp(20), dp(22), dp(20), dp(22));
            welcome.setBackground(roundGradient(new int[]{0xFFFFE0ED, 0xFFFFF5FA, 0xFFF0E6FA}, dp(26)));
            screenContent.addView(welcome, matchWrap());
            TextView bigHeart = text("♥", 56, 0xFFD94B88, true);
            bigHeart.setGravity(Gravity.CENTER);
            welcome.addView(bigHeart);
            TextView heading = text("قصتكم تبدأ بخطوة", 19, INK, true);
            heading.setGravity(Gravity.CENTER);
            heading.setPadding(0, dp(5), 0, dp(4));
            welcome.addView(heading);
            TextView body = text("اختَر نوع الرابطة وأرسل دعوة. تبقى العلاقة بانتظار موافقة الطرف الآخر.", 13, MUTED, false);
            body.setGravity(Gravity.CENTER);
            body.setLineSpacing(dp(4), 1f);
            welcome.addView(body);
            TextView start = actionButton("＋  إرسال طلب ارتباط", true);
            LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(-1, -2);
            startLp.topMargin = dp(15);
            welcome.addView(start, startLp);
            start.setOnClickListener(v -> showView("send"));
        } else for (Map<String, Object> row : active) addRelationshipCard(row, false);

        sectionTitle("اختر نوع الرابطة", "كل نوع له طابعه الخاص");
        HorizontalScrollView typeScroller = new HorizontalScrollView(this);
        typeScroller.setHorizontalScrollBarEnabled(false);
        typeScroller.setClipToPadding(false);
        typeScroller.setPadding(0, dp(2), 0, dp(9));
        screenContent.addView(typeScroller, matchWrap());
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        typeScroller.addView(firstRow, new HorizontalScrollView.LayoutParams(-2, -2));
        String[][] types = {{"love","♥","الحب","رابطة عاطفية"},{"marriage","💞","الزواج","رابطة مميزة"},{"engagement","💍","الخطوبة","خطوة رسمية"},{"friendship","✦","الصداقة","رابطة صداقة"},{"brotherhood","♛","الأخوّة","رابطة أخوية"}};
        for (int i = 0; i < types.length; i++) {
            final String type = types[i][0];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(13), dp(16), dp(13), dp(16));
            card.setBackground(roundGradient(new int[]{Color.WHITE, 0xFFFFEDF5, 0xFFF7F0FC}, dp(24)));
            card.setElevation(dp(3));
            card.setClickable(true);
            card.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(158), dp(158));
            lp.setMargins(dp(2), dp(4), dp(10), dp(5));
            firstRow.addView(card, lp);
            FrameLayout iconFrame = new FrameLayout(this);
            iconFrame.setBackground(roundGradient(new int[]{0xFFFFCFE2, 0xFFFFEAF3}, dp(25)));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(60), dp(60));
            iconLp.bottomMargin = dp(10);
            card.addView(iconFrame, iconLp);
            TextView symbol = text(types[i][1], 31, ROSE, true);
            symbol.setGravity(Gravity.CENTER);
            iconFrame.addView(symbol, new FrameLayout.LayoutParams(-1, -1));
            TextView name = text(types[i][2], 15, INK, true);
            name.setGravity(Gravity.CENTER);
            card.addView(name);
            TextView detail = text(types[i][3], 11, MUTED, false);
            detail.setGravity(Gravity.CENTER);
            detail.setPadding(0, dp(4), 0, 0);
            card.addView(detail);
            card.setOnClickListener(v -> { currentType = type; showView("send"); });
        }
        sectionTitle("اكتشف عالم CP", "كل ما تحتاجه لعلاقتكما في مكان واحد");
        addFeatureCard("tasks", "المهام والتحديات", "إنجازات مشتركة ومكافآت يومية", "✦");
        addFeatureCard("store", "متجر CP", "مظاهر وإطارات تضيف بصمتكما", "♧");
        addFeatureCard("leaderboard", "قمة المتصدرين", "شاهد أجمل الروابط في JEHO", "♛");
        addFeatureCard("memories", "ذكرياتنا", "احفظوا لحظاتكم وصوركم", "♡");
        sectionTitle("طلبات الارتباط", "تابع الدعوات بكل سهولة");
        addFeatureCard("incoming", "الطلبات الواردة", "قبول أو رفض الدعوات المرسلة إليك", "↓");
        addFeatureCard("sent", "الطلبات المرسلة", "تابع حالة دعواتك", "↑");
        addFeatureCard("discover", "العلاقات النشطة", "استكشف الروابط في مجتمع JEHO", "♥");
        addFeatureCard("settings", "إعدادات العلاقة", "إدارة الروابط الحالية بأمان", "⚙");
    }

    private void renderSend() {
        sectionTitle("إرسال طلب ارتباط", "اختر النوع وأدخل معرّف المستخدم");
        TextView explainer = text("سيصل طلبك للطرف الآخر ليقبله أو يرفضه. لا تُفعّل العلاقة تلقائياً، والطلب يخضع لقواعد الخادم.", 13, MUTED, false);
        explainer.setLineSpacing(dp(4), 1f);
        explainer.setPadding(dp(14), dp(13), dp(14), dp(13));
        explainer.setBackground(round(Color.WHITE, dp(17), LINE));
        screenContent.addView(explainer, matchWrap());

        sectionTitle("نوع العلاقة", null);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        screenContent.addView(horizontal, matchWrap());
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        String[][] types = {{"love","الحب"}, {"marriage","الزواج"}, {"engagement","الخطوبة"}, {"friendship","الصداقة"}, {"brotherhood","الأخوّة"}};
        for (String[] type : types) {
            TextView chip = text(type[1], 12, currentType.equals(type[0]) ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(10), dp(14), dp(10));
            chip.setBackground(round(currentType.equals(type[0]) ? ROSE : Color.WHITE, dp(20), LINE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(7), 0);
            chips.addView(chip, lp);
            chip.setOnClickListener(v -> { currentType = type[0]; renderScreen(); });        }

        sectionTitle("معرّف المستخدم", null);
        EditText idInput = new EditText(this);
        idInput.setSingleLine(true);
        idInput.setTextSize(14);
        idInput.setTextColor(INK);
        idInput.setHintTextColor(MUTED);
        idInput.setHint("أدخل ID المستخدم");        idInput.setInputType(InputType.TYPE_CLASS_TEXT);
        idInput.setPadding(dp(15), dp(12), dp(15), dp(12));
        idInput.setBackground(round(Color.WHITE, dp(15), LINE));
        screenContent.addView(idInput, matchWrap());

        TextView send = actionButton("إرسال طلب " + typeName(currentType), true);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(-1, -2);
        sendLp.topMargin = dp(12);
        screenContent.addView(send, sendLp);        send.setOnClickListener(v -> sendRequest(idInput.getText().toString()));
        TextView sentLink = actionButton("عرض الطلبات المرسلة", false);
        LinearLayout.LayoutParams sentLp = new LinearLayout.LayoutParams(-1, -2);
        sentLp.topMargin = dp(8);
        screenContent.addView(sentLink, sentLp);
        sentLink.setOnClickListener(v -> showView("sent"));
    }

    private void renderIncoming() {
        sectionTitle("الطلبات الواردة", "اختر القبول أو الرفض لكل طلب");
        List<Map<String, Object>> pending = new ArrayList<>();
        for (Map<String, Object> row : incomingData) if ("pending".equals(str(row.get("status")))) pending.add(row);
        if (pending.isEmpty()) {
            emptyState("✉", "لا توجد طلبات معلّقة", "ستظهر هنا طلبات الارتباط الجديدة.");
            return;
        }
        for (Map<String, Object> request : pending) {
            Map<String, Object> sender = map(request.get("sender"));
            LinearLayout card = panel();
            screenContent.addView(card, matchWrap());
            addPersonRow(card, sender, "طلب " + typeName(str(request.get("type"))), safeStatus(str(request.get("status"))));
            LinearLayout buttons = new LinearLayout(this);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
            bp.topMargin = dp(12);
            card.addView(buttons, bp);
            TextView accept = actionButton("قبول العلاقة", true);
            TextView reject = actionButton("رفض", false);
            buttons.addView(accept, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rejectLp = new LinearLayout.LayoutParams(0, -2, 1f);
            rejectLp.setMargins(dp(8), 0, 0, 0);
            buttons.addView(reject, rejectLp);
            String requestId = str(request.get("id"));
            accept.setOnClickListener(v -> respondRequest(requestId, true));
            reject.setOnClickListener(v -> respondRequest(requestId, false));
        }
    }

    private void renderSent() {
        sectionTitle("الطلبات المرسلة", "تابع حالة طلباتك وألغِ الطلب المعلّق عند الحاجة");
        if (sentData.isEmpty()) {
            emptyState("↗", "ما أرسلت طلبات بعد", "ابدأ بإرسال طلب ارتباط إلى مستخدم آخر.");
            TextView send = actionButton("إرسال طلب", true);
            screenContent.addView(send, matchWrap());
            send.setOnClickListener(v -> showView("send"));
            return;
        }
        for (Map<String, Object> request : sentData) {
            LinearLayout card = panel();
            screenContent.addView(card, matchWrap());
            addPersonRow(card, map(request.get("recipient")),
                    typeName(str(request.get("type"))), safeStatus(str(request.get("status"))));
            if ("pending".equals(str(request.get("status")))) {
                TextView cancel = actionButton("إلغاء الطلب", false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(10);
                card.addView(cancel, lp);
                String requestId = str(request.get("id"));
                cancel.setOnClickListener(v -> cancelRequest(requestId));
            }
        }
    }

    private void renderDiscover() {
        sectionTitle("العلاقات النشطة", "استكشف الروابط التي تم قبولها");
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        screenContent.addView(horizontal, matchWrap());
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(chips, new HorizontalScrollView.LayoutParams(-2, -2));
        String[][] types = {{"","الكل"},{"love","الحب"},{"marriage","الزواج"},{"engagement","الخطوبة"},{"friendship","الصداقة"},{"brotherhood","الأخوّة"}};
        for (String[] type : types) {
            TextView chip = text(type[1], 12, discoverType.equals(type[0]) ? Color.WHITE : INK, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(13), dp(9), dp(13), dp(9));
            chip.setBackground(round(discoverType.equals(type[0]) ? ROSE : Color.WHITE, dp(20), LINE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(0, 0, dp(7), 0);
            chips.addView(chip, lp);
            chip.setOnClickListener(v -> { discoverType = type[0]; renderScreen(); loadDiscover(discoverType); });
        }
        if (loadingDiscover) {
            emptyState("♡", "جارٍ تحميل العلاقات", "لحظة واحدة…");
        } else if (discoverData.isEmpty()) {
            emptyState("♡", "لا توجد علاقات للعرض", "جرّب نوعاً آخر أو عُد لاحقاً.");
        } else {
            for (Map<String, Object> row : discoverData) addRelationshipCard(row, true);
        }
    }

    private void addRelationshipCard(Map<String, Object> row, boolean publicList) {
        LinearLayout card = panel();
        card.setPadding(dp(17), dp(18), dp(17), dp(18));
        card.setBackground(roundGradient(new int[]{Color.WHITE, 0xFFFFF2F8, 0xFFF9F2FF}, dp(26)));
        card.setElevation(dp(3));
        screenContent.addView(card, matchWrap());
        TextView ribbon = text("♥  " + (publicList ? "علاقة نشطة" : "رابطكما الخاص"), 11, ROSE, true);
        ribbon.setGravity(Gravity.CENTER);
        ribbon.setPadding(dp(13), dp(7), dp(13), dp(7));
        ribbon.setBackground(round(0xFFFFE4EF, dp(18), 0xFFF7D0E1));
        card.addView(ribbon, matchWrap());

        Map<String, Object> self = map(row.get("self"));
        Map<String, Object> partner = map(row.get("partner"));
        if (self.isEmpty()) self = map(row.get("userA"));
        if (partner.isEmpty()) partner = map(row.get("userB"));
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(-1, -2);
        pairLp.topMargin = dp(13);
        card.addView(pair, pairLp);
        addAvatar(pair, self, 92);
        FrameLayout heartBadge = new FrameLayout(this);
        heartBadge.setBackground(roundGradient(new int[]{0xFFFFC4DC, 0xFFFFE9F2}, dp(24)));
        LinearLayout.LayoutParams heartLp = new LinearLayout.LayoutParams(dp(48), dp(48));
        heartLp.setMargins(dp(5), 0, dp(5), 0);
        pair.addView(heartBadge, heartLp);
        TextView bigHeart = text("♥", 28, 0xFFBF2E70, true);
        bigHeart.setGravity(Gravity.CENTER);
        heartBadge.addView(bigHeart, new FrameLayout.LayoutParams(-1, -1));
        addAvatar(pair, partner, 92);
        TextView names = text(personName(self) + "  ♥  " + personName(partner), 17, INK, true);
        names.setGravity(Gravity.CENTER);
        names.setPadding(0, dp(12), 0, dp(3));
        card.addView(names);
        TextView type = text(typeName(str(row.get("type"))) + "  ·  المستوى " + defaultString(row.get("level"), "1"), 12, MUTED, false);
        type.setGravity(Gravity.CENTER);
        card.addView(type);

        LinearLayout score = new LinearLayout(this);
        score.setGravity(Gravity.CENTER);
        score.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(-1, -2);
        scoreLp.topMargin = dp(14);
        scoreLp.bottomMargin = dp(3);
        card.addView(score, scoreLp);
        LinearLayout pointsCard = new LinearLayout(this);
        pointsCard.setOrientation(LinearLayout.VERTICAL);
        pointsCard.setGravity(Gravity.CENTER);
        pointsCard.setPadding(dp(8), dp(10), dp(8), dp(10));
        pointsCard.setBackground(round(Color.WHITE, dp(17), 0xFFF4D4E2));
        score.addView(pointsCard, new LinearLayout.LayoutParams(0, -2, 1f));
        pointsCard.addView(text("♥", 15, ROSE, true));
        pointsCard.addView(text(numberString(row.get("points")), 22, INK, true));
        pointsCard.addView(text("نقاط العلاقة", 10, MUTED, false));
        LinearLayout levelCard = new LinearLayout(this);
        levelCard.setOrientation(LinearLayout.VERTICAL);
        levelCard.setGravity(Gravity.CENTER);
        levelCard.setPadding(dp(8), dp(10), dp(8), dp(10));
        levelCard.setBackground(round(Color.WHITE, dp(17), 0xFFE8D9F4));
        LinearLayout.LayoutParams levelLp = new LinearLayout.LayoutParams(0, -2, 1f);
        levelLp.setMargins(dp(8), 0, 0, 0);
        score.addView(levelCard, levelLp);
        levelCard.addView(text("✦", 15, 0xFF8B5AA7, true));
        levelCard.addView(text(defaultString(row.get("level"), "1"), 22, INK, true));
        levelCard.addView(text("مستوى الرابطة", 10, MUTED, false));

        String partnerId = str(partner.get("id"));
        if (!partnerId.isEmpty()) {
            TextView profile = actionButton("عرض الملف الشخصي  ↗", true);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
            plp.topMargin = dp(14);
            card.addView(profile, plp);
            profile.setOnClickListener(v -> UserProfileCardSheet.show(CpCenterActivity.this, partnerId));
        }
        if (!publicList && !"true".equals(String.valueOf(row.get("legacy")))) {
            TextView end = actionButton("إنهاء العلاقة", false);
            end.setTextColor(Color.rgb(173, 60, 56));
            end.setBackground(round(0xFFFFEEEE, dp(16), 0xFFF6D7D7));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2);
            elp.topMargin = dp(9);
            card.addView(end, elp);
            String relationId = str(row.get("id"));
            end.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("إنهاء العلاقة")
                    .setMessage("هل أنت متأكد من إنهاء العلاقة؟ لا يمكن التراجع عن هذا الإجراء.")
                    .setNegativeButton("رجوع", (d, w) -> d.dismiss())
                    .setPositiveButton("إنهاء العلاقة", (d, w) -> endRelationship(relationId))
                    .show());
        }
    }


    private void renderCpCosmeticStore() {
        sectionTitle("مظاهر CP الحصرية", "عناصر مصممة لعلاقتكما · شراء آمن وارتداء من الحقيبة");
        if (loadingCpCosmetics) {
            emptyState("💗", "جارٍ تجهيز المظاهر", "نحضر العناصر والأسعار وحالتها من الخادم…");
            return;
        }
        if (cpCosmeticsData.isEmpty()) {
            LinearLayout empty = panel();
            screenContent.addView(empty, matchWrap());
            empty.addView(text("💎  قريباً في متجر CP", 15, INK, true));
            TextView hint = text("يمكن للإدارة إضافة إطارات وبطاقات دخول ولوحات أسماء خاصة عبر قسم إعدادات CP في الداشبورد.", 12, MUTED, false);
            hint.setPadding(0, dp(6), 0, 0);
            hint.setLineSpacing(dp(3), 1f);
            empty.addView(hint);
            return;
        }
        for (int i = 0; i < cpCosmeticsData.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(8);
            screenContent.addView(row, rowLp);
            addCpCosmeticCard(row, cpCosmeticsData.get(i));
            if (i + 1 < cpCosmeticsData.size()) addCpCosmeticCard(row, cpCosmeticsData.get(i + 1));
        }
    }

    private void addCpCosmeticCard(LinearLayout row, Map<String, Object> item) {
        LinearLayout card = panel();
        card.setPadding(dp(10), dp(11), dp(10), dp(11));
        card.setContentDescription("عنصر متجر CP: " + defaultString(item.get("name"), "مظهر CP"));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1f);
        cardLp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(card, cardLp);
        String id = str(item.get("id"));
        String title = defaultString(item.get("name"), "مظهر CP");
        String description = str(item.get("description"));
        boolean owned = hasCpCosmetic(id);
        boolean equipped = isCpCosmeticEquipped(id);
        String preview = avatarUrl(item.get("previewUrl"));
        FrameLayout previewFrame = new FrameLayout(this);
        previewFrame.setBackground(roundGradient(new int[]{0xFFFFE6F0, 0xFFF4E9FF}, dp(16)));
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(-1, dp(112));
        previewLp.bottomMargin = dp(9);
        card.addView(previewFrame, previewLp);
        if (preview != null && !preview.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("معاينة " + title);
            previewFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            Glide.with(this).load(preview).into(image);
        } else {
            TextView symbol = text("♡", 32, ROSE, true);
            symbol.setGravity(Gravity.CENTER);
            previewFrame.addView(symbol, new FrameLayout.LayoutParams(-1, -1));
        }
        TextView name = text(title, 13, INK, true);
        name.setMaxLines(2);
        card.addView(name);
        String type = cosmeticTypeName(str(item.get("type")));
        TextView category = text(type, 10, MUTED, false);
        category.setPadding(0, dp(3), 0, 0);
        card.addView(category);
        if (!description.isEmpty()) {
            TextView desc = text(description, 11, MUTED, false);
            desc.setMaxLines(3);
            desc.setPadding(0, dp(4), 0, 0);
            card.addView(desc);
        }
        TextView price = text(numberString(item.get("coinPrice")) + " 💎", 13, ROSE, true);
        price.setPadding(0, dp(8), 0, dp(8));
        card.addView(price);
        TextView action = actionButton(equipped ? "مرتدٍ ✓" : owned ? "ارتداء العنصر" : "شراء الآن", !busyCosmeticId.equals(id));
        card.addView(action, new LinearLayout.LayoutParams(-1, -2));
        action.setOnClickListener(v -> {
            if (id.isEmpty()) return;
            if (busyCosmeticId.equals(id)) return;
            if (owned && equipped) {
                unequipCpCosmetic(id);
            } else if (owned) {
                equipCpCosmetic(id);
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("تأكيد شراء " + title)
                    .setMessage("السعر: " + numberString(item.get("coinPrice")) + " عملة. ستتم مراجعة الرصيد وخصم العملات من الخادم فقط، وستكون مدة الملكية " + defaultString(map(item.get("meta")).get("leaseDays"), "30") + " يوماً.")
                    .setNegativeButton("إلغاء", (d, w) -> d.dismiss())
                    .setPositiveButton("تأكيد الشراء", (d, w) -> purchaseCpCosmetic(id))
                    .show();
            }
        });
    }

    private String cosmeticTypeName(String type) {
        switch (type) {
            case "profile_card": return "بطاقة الملف الشخصي";
            case "entry_effect": return "تأثير دخول";
            case "room_card": return "بطاقة الروم";
            case "nameplate": return "لوحة الاسم";
            case "chat_bubble": return "فقاعة الدردشة";
            default: return "مظهر CP";
        }
    }

    private boolean hasCpCosmetic(String id) {
        if (id == null || id.isEmpty()) return false;
        for (Map<String, Object> owned : cpInventoryData) {
            String cosmeticId = str(owned.get("cosmeticId"));
            if (cosmeticId.isEmpty()) cosmeticId = str(map(owned.get("cosmetic")).get("id"));
            if (id.equals(cosmeticId)) return true;
        }
        return false;
    }

    private boolean isCpCosmeticEquipped(String id) {
        for (Map<String, Object> owned : cpInventoryData) {
            String cosmeticId = str(owned.get("cosmeticId"));
            if (cosmeticId.isEmpty()) cosmeticId = str(map(owned.get("cosmetic")).get("id"));
            if (id.equals(cosmeticId)) return Boolean.TRUE.equals(owned.get("equipped"));
        }
        return false;
    }

    private void loadCpCosmetics() {
        if (loadingCpCosmetics || isFinishing()) return;
        loadingCpCosmetics = true;
        if ("store".equals(currentView)) renderScreen();
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> catalog = ApiCall.execute(c.getUserApi().cosmeticsCatalog());
            Result<List<Map<String, Object>>> inventory = ApiCall.execute(c.getUserApi().cosmeticsInventory());
            List<Map<String, Object>> rows = new ArrayList<>();
            if (catalog.success && catalog.data != null) {
                for (Map<String, Object> item : catalog.data) {
                    Map<String, Object> meta = map(item.get("meta"));
                    String code = str(item.get("code"));
                    if (Boolean.TRUE.equals(meta.get("cpStore")) || code.startsWith("cp_")) rows.add(item);
                }
            }
            List<Map<String, Object>> owned = inventory.success && inventory.data != null
                    ? new ArrayList<>(inventory.data) : new ArrayList<>();
            runOnUiThread(() -> {
                loadingCpCosmetics = false;
                cpCosmeticsData.clear();
                cpCosmeticsData.addAll(rows);
                cpInventoryData.clear();
                cpInventoryData.addAll(owned);
                if ("store".equals(currentView)) renderScreen();
                if (!catalog.success) toast(catalog.error != null ? catalog.error : "تعذر تحميل مظاهر CP");
            });
        });
    }

    private void purchaseCpCosmetic(String id) {
        busyCosmeticId = id;
        Map<String, String> body = new HashMap<>();
        body.put("cosmeticId", id);
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().purchaseCosmetic(body));
            runOnUiThread(() -> {
                busyCosmeticId = "";
                if (result.success) {
                    toast("تم الشراء بنجاح! ستجد العنصر في الحقيبة ❤️");
                    loadCpCosmetics();
                } else toast(result.error != null ? result.error : "لم يكتمل الشراء. تحقق من الرصيد وحاول مجدداً.");
            });
        });
    }

    private void equipCpCosmetic(String id) {
        busyCosmeticId = id;
        Map<String, String> body = new HashMap<>();
        body.put("cosmeticId", id);
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().equipCosmetic(body));
            runOnUiThread(() -> {
                busyCosmeticId = "";
                if (result.success) {
                    toast("تم ارتداء العنصر بنجاح ✨");
                    loadCpCosmetics();
                } else toast(result.error != null ? result.error : "تعذر ارتداء العنصر");
            });
        });
    }

    private void unequipCpCosmetic(String id) {
        busyCosmeticId = id;
        Map<String, String> body = new HashMap<>();
        body.put("cosmeticId", id);
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().unequipCosmetic(body));
            runOnUiThread(() -> {
                busyCosmeticId = "";
                if (result.success) {
                    toast("تمت إزالة ارتداء العنصر");
                    loadCpCosmetics();
                } else toast(result.error != null ? result.error : "تعذر إزالة العنصر");
            });
        });
    }

    private void renderStore() {
        sectionTitle("متجر CP", "مظاهر وهدايا تضيف لمسة خاصة إلى رابطكما");
        LinearLayout banner = panel();
        banner.setBackground(roundGradient(new int[]{0xFFFFE1EE, 0xFFFFF5FA, 0xFFF0E4FF}, dp(20)));
        screenContent.addView(banner, matchWrap());
        banner.addView(text("💗  اجعلوا رابطكم مميزاً", 17, INK, true));
        TextView blurb = text("إطارات مشتركة، تأثيرات دخول، بطاقات أسماء وهدايا مخصصة.", 12, MUTED, false);
        blurb.setPadding(0, dp(6), 0, dp(2));
        banner.addView(blurb);
        renderCpCosmeticStore();
        if (loadingCatalog) {
            emptyState("💎", "جارٍ تحميل المتجر", "نحضر عناصر CP من الخادم…");
            return;
        }
        if (catalogData.isEmpty()) {
            emptyState("💎", "لا توجد عناصر متاحة حالياً", "تحقق من إعداد كتالوج CP في الخادم ثم حدّث الصفحة.");
            TextView refresh = actionButton("إعادة تحميل المتجر", true);
            screenContent.addView(refresh, matchWrap());
            refresh.setOnClickListener(v -> loadCatalog());
            return;
        }
        sectionTitle("العناصر المتاحة", "الأسعار والتوفر حسب الكتالوج");
        for (Map<String, Object> item : catalogData) {
            LinearLayout card = panel();
            screenContent.addView(card, matchWrap());
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(row, matchWrap());
            FrameLayout giftFrame = new FrameLayout(this);
            giftFrame.setBackground(round(0xFFFFEAF2, dp(15), LINE));
            row.addView(giftFrame, new LinearLayout.LayoutParams(dp(54), dp(54)));
            TextView symbol = text("💝", 25, ROSE, true);
            symbol.setGravity(Gravity.CENTER);
            giftFrame.addView(symbol, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.setMargins(dp(12), 0, 0, 0);
            row.addView(copy, cp);
            Map<String, Object> gift = map(item.get("gift"));
            String giftName = firstValue(gift, "name");
            String type = firstValue(item, "type");
            String title = !type.isEmpty() ? typeName(type) + (giftName.isEmpty() ? "" : " · " + giftName) : firstValue(item, "name", "title", "displayName", "label");
            if (title.isEmpty()) title = giftName;
            String iconUrl = avatarUrl(gift.get("iconUrl"));
            if (iconUrl != null && !iconUrl.trim().isEmpty()) {
                ImageView giftIcon = new ImageView(this);
                giftIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                giftFrame.addView(giftIcon, new FrameLayout.LayoutParams(-1, -1));                Glide.with(this).load(iconUrl).into(giftIcon);
            }
            String desc = firstValue(item, "description", "subtitle", "details");
            String price = firstValue(item, "price", "priceCoins", "coinPrice", "cost", "coins");
            if ("false".equalsIgnoreCase(str(item.get("enabled")))) price = "غير مفعّل";
            copy.addView(text(title.isEmpty() ? "عنصر CP" : title, 14, INK, true));
            if (!desc.isEmpty()) {
                TextView sub = text(desc, 11, MUTED, false);
                sub.setPadding(0, dp(4), 0, 0);
                copy.addView(sub);
            }
            TextView priceView = text(price.isEmpty() ? "السعر حسب العنصر" : ("غير مفعّل".equals(price) ? price : price + " 💎"), 11, ROSE, true);
            priceView.setPadding(0, dp(5), 0, 0);
            copy.addView(priceView);
            final String itemTitle = title;
            final String itemDesc = desc;
            final String itemPrice = price;
            TextView detail = actionButton("التفاصيل", false);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
            detailLp.topMargin = dp(10);
            card.addView(detail, detailLp);
            detail.setOnClickListener(v -> new AlertDialog.Builder(this)
                     .setTitle(itemTitle.isEmpty() ? "عنصر CP" : itemTitle)
                    .setMessage((itemDesc.isEmpty() ? "عنصر من كتالوج CP." : itemDesc)
                            + "\n\n" + (itemPrice.isEmpty() ? "لم يحدد الخادم سعراً لهذا العنصر." : "السعر: " + itemPrice)
                            + "\n\nالشراء غير مفعّل حتى تتوفر واجهة شراء آمنة على الخادم.")
                    .setPositiveButton("حسناً", (d, w) -> d.dismiss()).show());
        }
    }


    private void renderLeaderboard() {
        sectionTitle("قمة عالم CP", "المتصدرون • نقاط العلاقات النشطة من الخادم");
        if (loadingDiscover) { emptyState("♥", "جارٍ تحميل المتصدرين", "نحدّث ترتيب الأزواج…"); return; }
        if (discoverData.isEmpty()) {
            emptyState("♥", "العرش بانتظاركم", "سيظهر هنا المتصدرون عند توفر علاقات نشطة.");
            TextView refresh = actionButton("↻  تحديث الترتيب", true);
            screenContent.addView(refresh, matchWrap());
            refresh.setOnClickListener(v -> loadDiscover(""));
            return;
        }
        int count = discoverData.size();
        Map<String, Object> champion = discoverData.get(0);
        Map<String, Object> a = map(champion.get("userA"));
        Map<String, Object> b = map(champion.get("userB"));
        LinearLayout crownCard = panel();
        crownCard.setPadding(dp(18), dp(20), dp(18), dp(20));
        crownCard.setBackground(roundGradient(new int[]{0xFF6D1745, 0xFFB92F70, 0xFFE86FA2}, dp(28)));
        crownCard.setElevation(dp(4));
        screenContent.addView(crownCard, matchWrap());
        TextView crown = text("♛  #1  متصدرو عالم CP", 14, 0xFFFFE1ED, true);
        crown.setGravity(Gravity.CENTER);
        crownCard.addView(crown);
        LinearLayout pair = new LinearLayout(this);
        pair.setGravity(Gravity.CENTER);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pairLp = new LinearLayout.LayoutParams(-1, -2);
        pairLp.topMargin = dp(12);
        crownCard.addView(pair, pairLp);
        addAvatar(pair, a, 112);
        FrameLayout centerHeart = new FrameLayout(this);
        centerHeart.setBackground(roundGradient(new int[]{0xFFFFE1ED, 0xFFFF9EC4}, dp(28)));
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(dp(56), dp(56));
        centerLp.setMargins(dp(7), 0, dp(7), 0);
        pair.addView(centerHeart, centerLp);
        TextView heart = text("♥", 34, 0xFF9F245B, true);
        heart.setGravity(Gravity.CENTER);
        centerHeart.addView(heart, new FrameLayout.LayoutParams(-1, -1));
        addAvatar(pair, b, 112);
        TextView names = text(personName(a) + "  ♥  " + personName(b), 18, Color.WHITE, true);
        names.setGravity(Gravity.CENTER);
        names.setPadding(0, dp(12), 0, dp(3));
        crownCard.addView(names);
        TextView kind = text(typeName(str(champion.get("type"))) + "  ·  المستوى " + defaultString(champion.get("level"), "1"), 12, 0xFFFFE4EF, false);
        kind.setGravity(Gravity.CENTER);
        crownCard.addView(kind);
        TextView score = text("♥  " + numberString(champion.get("points")) + " نقطة", 22, Color.WHITE, true);
        score.setGravity(Gravity.CENTER);
        score.setPadding(dp(16), dp(10), dp(16), dp(10));
        score.setBackground(round(0x30FFFFFF, dp(20), 0x55FFFFFF));
        LinearLayout.LayoutParams scoreLp = new LinearLayout.LayoutParams(-1, -2);
        scoreLp.topMargin = dp(14);
        crownCard.addView(score, scoreLp);

        if (count > 1) {
            sectionTitle("أقرب المنافسين", "المركزان الثاني والثالث");
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = dp(8);
            screenContent.addView(row, rowLp);
            for (int index = 1; index < Math.min(3, count); index++) {
                Map<String, Object> entry = discoverData.get(index);
                Map<String, Object> p1 = map(entry.get("userA"));
                Map<String, Object> p2 = map(entry.get("userB"));
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(8), dp(14), dp(8), dp(14));
                card.setBackground(roundGradient(index == 1
                        ? new int[]{0xFFF0E7FA, Color.WHITE}
                        : new int[]{0xFFFFEAF2, Color.WHITE}, dp(23)));
                card.setElevation(dp(2));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
                cp.setMargins(dp(3), 0, dp(3), 0);
                row.addView(card, cp);
                TextView rank = text(index == 1 ? "♛  #2" : "♡  #3", 13, index == 1 ? 0xFF8152A2 : ROSE, true);
                rank.setGravity(Gravity.CENTER);
                card.addView(rank);
                LinearLayout faces = new LinearLayout(this);
                faces.setGravity(Gravity.CENTER);
                faces.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams facesLp = new LinearLayout.LayoutParams(-1, -2);
                facesLp.topMargin = dp(8);
                card.addView(faces, facesLp);
                addAvatar(faces, p1, 66);
                TextView miniHeart = text("♥", 14, ROSE, true);
                miniHeart.setGravity(Gravity.CENTER);
                faces.addView(miniHeart, new LinearLayout.LayoutParams(dp(18), dp(30)));
                addAvatar(faces, p2, 66);
                TextView coupleName = text(personName(p1) + " ♥ " + personName(p2), 12, INK, true);
                coupleName.setGravity(Gravity.CENTER);
                coupleName.setMaxLines(2);
                coupleName.setPadding(0, dp(8), 0, dp(3));
                card.addView(coupleName);
                TextView points = text(numberString(entry.get("points")) + " نقطة", 14, ROSE, true);
                points.setGravity(Gravity.CENTER);
                points.setPadding(0, dp(5), 0, 0);
                card.addView(points);
            }
        }
        if (count > 3) {
            sectionTitle("قائمة المتصدرين", "ترتيب بقية العلاقات");
            for (int index = 3; index < count; index++) {
                Map<String, Object> entry = discoverData.get(index);
                Map<String, Object> p1 = map(entry.get("userA"));
                Map<String, Object> p2 = map(entry.get("userB"));
                LinearLayout card = panel();
                screenContent.addView(card, matchWrap());
                LinearLayout line = new LinearLayout(this);
                line.setGravity(Gravity.CENTER_VERTICAL);
                line.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(line, matchWrap());
                TextView rank = text("#" + (index + 1), 15, ROSE, true);
                rank.setGravity(Gravity.CENTER);
                rank.setBackground(round(0xFFFFE8F2, dp(14), 0xFFF7D6E4));
                line.addView(rank, new LinearLayout.LayoutParams(dp(48), dp(42)));
                LinearLayout faces = new LinearLayout(this);
                faces.setGravity(Gravity.CENTER);
                faces.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams facesLp = new LinearLayout.LayoutParams(-2, -2);
                facesLp.setMargins(dp(5), 0, dp(5), 0);
                line.addView(faces, facesLp);
                addAvatar(faces, p1, 54);
                TextView mini = text("♥", 12, ROSE, true);
                mini.setGravity(Gravity.CENTER);
                faces.addView(mini, new LinearLayout.LayoutParams(dp(16), dp(24)));
                addAvatar(faces, p2, 54);
                LinearLayout detail = new LinearLayout(this);
                detail.setOrientation(LinearLayout.VERTICAL);
                line.addView(detail, new LinearLayout.LayoutParams(0, -2, 1f));
                detail.addView(text(personName(p1) + " ♥ " + personName(p2), 12, INK, true));
                TextView meta = text(typeName(str(entry.get("type"))) + " · " + numberString(entry.get("points")) + " نقطة", 11, MUTED, false);
                meta.setPadding(0, dp(4), 0, 0);
                detail.addView(meta);
            }
        }
    }



    private void renderTasks() {
        sectionTitle("المهام والتحديات", "إنجازات يومية مشتركة ومكافآت نقاط CP");
        LinearLayout hero = panel();
        hero.setBackground(roundGradient(new int[]{0xFFFFE1EE, 0xFFFFF5FA}, dp(20)));
        screenContent.addView(hero, matchWrap());
        hero.addView(text("🎯  كل خطوة تقرّبكم أكثر", 17, INK, true));
        TextView note = text("أكملوا شروط المهمة ثم استلموا المكافأة. يتحقق الخادم من الإنجاز ويمنع استلام المكافأة أكثر من مرة يومياً.", 12, MUTED, false);
        note.setPadding(0, dp(5), 0, 0);
        note.setLineSpacing(dp(3), 1f);
        hero.addView(note);
        if (loadingTasks) {
            emptyState("🎯", "جارٍ تحميل المهام", "نحدّث تقدم العلاقة…");
            return;
        }
        if (tasksData.isEmpty()) {
            emptyState("♡", "اربط حسابك بعلاقة CP", "تظهر المهام اليومية بعد تفعيل علاقة وقبول الطلب.");
            TextView send = actionButton("إرسال طلب ارتباط", true);
            screenContent.addView(send, matchWrap());
            send.setOnClickListener(v -> showView("send"));
            return;
        }
        for (Map<String, Object> task : tasksData) {
            LinearLayout card = panel();
            screenContent.addView(card, matchWrap());
            String title = defaultString(task.get("title"), "مهمة CP");
            String description = str(task.get("description"));
            card.addView(text(title, 15, INK, true));
            if (!description.isEmpty()) {
                TextView sub = text(description, 12, MUTED, false);
                sub.setPadding(0, dp(4), 0, 0);
                card.addView(sub);
            }
            int progress = Math.max(0, (int) numberLong(task.get("progress")));
            int target = Math.max(1, (int) numberLong(task.get("target")));
            int percent = Math.min(100, (progress * 100) / target);
            TextView progressText = text(progress + " / " + target + "  ·  " + percent + "%", 11, ROSE, true);
            progressText.setPadding(0, dp(10), 0, dp(5));
            card.addView(progressText);
            FrameLayout track = new FrameLayout(this);
            track.setBackground(round(0xFFFFEAF2, dp(8), LINE));
            card.addView(track, new LinearLayout.LayoutParams(-1, dp(8)));
            View fill = new View(this);
            fill.setBackground(round(ROSE, dp(8), Color.TRANSPARENT));
            FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(Math.max(dp(4), dp(260) * percent / 100), -1, Gravity.RIGHT);
            track.addView(fill, fillLp);
            TextView reward = text("المكافأة: +" + numberString(task.get("rewardPoints")) + " نقطة CP", 12, ROSE, true);
            reward.setPadding(0, dp(9), 0, dp(8));
            card.addView(reward);
            boolean claimed = Boolean.TRUE.equals(task.get("claimed"));
            boolean canClaim = Boolean.TRUE.equals(task.get("canClaim"));
            TextView claim = actionButton(claimed ? "تم الاستلام ✓" : canClaim ? "استلام المكافأة" : "أكمل المهمة أولاً", canClaim);
            card.addView(claim, matchWrap());
            claim.setOnClickListener(v -> {
                if (claimed) {
                    toast("استلمت مكافأة هذه المهمة اليوم.");
                } else if (canClaim) {
                    claimTask(str(task.get("id")));
                } else {
                    toast("أكمل شرط المهمة أولاً لتحصل على المكافأة.");
                }
            });
        }
    }

    private long numberLong(Object value) {
        try { return (long) Double.parseDouble(str(value)); }
        catch (Exception ignored) { return 0L; }
    }

    private void loadTasks() {
        if (loadingTasks || isFinishing()) return;
        loadingTasks = true;
        if ("tasks".equals(currentView)) renderScreen();
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().cpTasks());
            List<Map<String, Object>> rows = result.success && result.data != null
                    ? new ArrayList<>(result.data) : new ArrayList<>();
            runOnUiThread(() -> {
                loadingTasks = false;
                tasksData.clear();
                tasksData.addAll(rows);
                if ("tasks".equals(currentView)) renderScreen();
                if (!result.success) toast(result.error != null ? result.error : "تعذر تحميل مهام CP");
            });
        });
    }

    private void claimTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) return;
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().claimCpTask(taskId));
            runOnUiThread(() -> {
                if (result.success) {
                    toast("تم استلام مكافأة المهمة وإضافة النقاط إلى العلاقة ❤️");
                    loadTasks();
                    loadHome();
                } else toast(result.error != null ? result.error : "تعذر استلام مكافأة المهمة");
            });
        });
    }

    private void renderMemories() {
        sectionTitle("ذكرياتنا", "لحظات مشتركة تبقى محفوظة داخل علاقتكما");
        LinearLayout banner = panel();
        banner.setBackground(roundGradient(new int[]{0xFFFFE1EE, 0xFFFFF5FA}, dp(20)));
        screenContent.addView(banner, matchWrap());
        banner.addView(text("♡  قصتكم تستحق أن تُحفظ", 17, INK, true));
        TextView description = text("أضيفوا كلمات وصوراً لتوثيق لحظاتكما. الذكريات متاحة فقط لأطراف العلاقة النشطة.", 12, MUTED, false);
        description.setPadding(0, dp(5), 0, 0);
        description.setLineSpacing(dp(3), 1f);
        banner.addView(description);
        TextView add = actionButton("＋  إضافة ذكرى", true);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, -2);
        addLp.topMargin = dp(10);
        screenContent.addView(add, addLp);
        add.setOnClickListener(v -> showAddMemoryDialog());
        if (loadingMemories) {
            emptyState("📖", "جارٍ تحميل الذكريات", "نحضر ذكريات علاقتكما…");
            return;
        }
        if (memoriesData.isEmpty()) {
            emptyState("♡", "ابدأوا أول ذكرى", "أول لحظة جميلة يمكن أن تكون بداية قصتكم.");
            return;
        }
        for (Map<String, Object> memory : memoriesData) {
            LinearLayout card = panel();
            screenContent.addView(card, matchWrap());
            Map<String, Object> author = map(memory.get("author"));
            addPersonRow(card, author, "ذكرى مشتركة", defaultString(memory.get("createdAt"), ""));
            String body = str(memory.get("text"));
            if (!body.isEmpty()) {
                TextView note = text(body, 14, INK, false);
                note.setPadding(0, dp(12), 0, dp(6));
                note.setLineSpacing(dp(4), 1f);
                card.addView(note);
            }
            String imageUrl = avatarUrl(memory.get("imageUrl"));
            if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(round(0xFFFFEAF2, dp(16), LINE));
                image.setClipToOutline(true);
                LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, dp(190));
                imageLp.topMargin = dp(6);
                card.addView(image, imageLp);
                Glide.with(this).load(imageUrl).centerCrop().into(image);
            }
            String ownerId = str(memory.get("userId"));
            if (!ownerId.isEmpty() && ownerId.equals(c.getSessionManager().getUserId())) {
                TextView delete = actionButton("حذف ذكراي", false);
                LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(-1, -2);
                deleteLp.topMargin = dp(10);
                card.addView(delete, deleteLp);
                String id = str(memory.get("id"));
                delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle("حذف الذكرى")
                        .setMessage("هل تريد حذف هذه الذكرى نهائياً؟")
                        .setNegativeButton("إلغاء", (d, w) -> d.dismiss())
                        .setPositiveButton("حذف", (d, w) -> deleteMemory(id))
                        .show());
            }
        }
    }

    private void showAddMemoryDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(8), dp(4), dp(8), dp(4));
        EditText note = new EditText(this);
        note.setHint("اكتب ذكرى جميلة (حتى 500 حرف)");
        note.setTextSize(14);
        note.setTextColor(INK);
        note.setHintTextColor(MUTED);
        note.setMinLines(3);
        note.setGravity(Gravity.TOP | Gravity.START);
        note.setBackground(round(Color.WHITE, dp(14), LINE));
        note.setPadding(dp(12), dp(10), dp(12), dp(10));
        form.addView(note, matchWrap());
        EditText image = new EditText(this);
        image.setHint("رابط صورة اختياري (https://...)");
        image.setTextSize(13);
        image.setTextColor(INK);
        image.setHintTextColor(MUTED);
        image.setSingleLine(true);
        image.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        image.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, -2);
        imageLp.topMargin = dp(8);
        form.addView(image, imageLp);
        new AlertDialog.Builder(this)
                .setTitle("إضافة ذكرى جديدة")
                .setView(form)
                .setNegativeButton("إلغاء", (d, w) -> d.dismiss())
                .setPositiveButton("حفظ الذكرى", (d, w) -> createMemory(note.getText().toString(), image.getText().toString()))
                .show();
    }

    private void createMemory(String note, String imageUrl) {
        Map<String, String> body = new HashMap<>();
        body.put("text", note == null ? "" : note.trim());
        body.put("imageUrl", imageUrl == null ? "" : imageUrl.trim());
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().createCpMemory(body));
            runOnUiThread(() -> {
                if (result.success) {
                    toast("تم حفظ الذكرى لكما ❤️");
                    loadMemories();
                } else toast(result.error != null ? result.error : "تعذر حفظ الذكرى");
            });
        });
    }

    private void deleteMemory(String id) {
        if (id == null || id.trim().isEmpty()) return;
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().deleteCpMemory(id));
            runOnUiThread(() -> {
                if (result.success) {
                    toast("تم حذف الذكرى.");
                    loadMemories();
                } else toast(result.error != null ? result.error : "تعذر حذف الذكرى");
            });
        });
    }

    private void loadMemories() {
        if (loadingMemories || isFinishing()) return;
        loadingMemories = true;
        if ("memories".equals(currentView)) renderScreen();
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().cpMemories());
            List<Map<String, Object>> rows = result.success && result.data != null
                    ? new ArrayList<>(result.data) : new ArrayList<>();
            runOnUiThread(() -> {
                loadingMemories = false;
                memoriesData.clear();
                memoriesData.addAll(rows);
                if ("memories".equals(currentView)) renderScreen();
                if (!result.success) toast(result.error != null ? result.error : "تعذر تحميل الذكريات");
            });
        });
    }

    private void renderPlannedPage(String title, String subtitle, String requirement) {
        sectionTitle(title, subtitle);
        LinearLayout hero = panel();
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(22), dp(24), dp(22), dp(24));
        screenContent.addView(hero, matchWrap());
        TextView heart = text("♡", 42, ROSE, true);
        heart.setGravity(Gravity.CENTER);
        hero.addView(heart);
        TextView heading = text("مساحة مصممة لعلاقتكما", 16, INK, true);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(8), 0, dp(5));
        hero.addView(heading);
        TextView body = text(subtitle, 12, MUTED, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(3), 1f);
        hero.addView(body);
        LinearLayout note = panel();
        screenContent.addView(note, matchWrap());
        note.addView(text("حالة الربط", 14, INK, true));
        TextView details = text(requirement, 12, MUTED, false);
        details.setPadding(0, dp(6), 0, 0);
        details.setLineSpacing(dp(3), 1f);
        note.addView(details);
        TextView refresh = actionButton("تحديث بيانات CP", false);
        screenContent.addView(refresh, matchWrap());
        refresh.setOnClickListener(v -> refreshAll());
    }

    private void renderSettings() {
        sectionTitle("إعدادات العلاقة", "إدارة العلاقة بوضوح وأمان");
        boolean any = false;
        for (Map<String, Object> row : homeData) {
            if (!"active".equals(str(row.get("status")))) continue;
            any = true;
            addRelationshipCard(row, false);
        }
        if (!any) {
            emptyState("♡", "لا توجد علاقة نشطة", "يمكنك إرسال طلب جديد من الصفحة الرئيسية.");
            TextView go = actionButton("العودة للرئيسية", true);
            screenContent.addView(go, matchWrap());
            go.setOnClickListener(v -> showView("home"));
        }
        LinearLayout help = panel();
        screenContent.addView(help, matchWrap());
        help.addView(text("الخصوصية والأمان", 14, INK, true));
        TextView detail = text("القبول والرفض وإنهاء العلاقة تُرسل إلى الخادم. لا تتغير حالة العلاقة محلياً قبل تأكيد نجاح العملية.", 12, MUTED, false);
        detail.setPadding(0, dp(5), 0, 0);
        help.addView(detail);
    }

    private String firstValue(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = str(values.get(key));
            if (!value.isEmpty() && !"null".equals(value)) return value;
        }
        return "";
    }

    private void loadCatalog() {
        if (loadingCatalog || isFinishing()) return;
        loadingCatalog = true;
        if ("store".equals(currentView)) renderScreen();
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().cpCatalog());
            List<Map<String, Object>> rows = result.success && result.data != null
                    ? new ArrayList<>(result.data) : new ArrayList<>();
            runOnUiThread(() -> {
                loadingCatalog = false;
                catalogData.clear();
                catalogData.addAll(rows);
                if ("store".equals(currentView)) renderScreen();
                if (!result.success) toast(result.error != null ? result.error : "تعذر تحميل متجر CP");
            });
        });
    }

    private void addFeatureCard(String icon, String title, String subtitle, String targetView) {
        LinearLayout card = panel();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        screenContent.addView(card, lp);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(title + "، فتح القسم");
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, matchWrap());
        TextView symbol = text(icon, 25, ROSE, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(round(0xFFFFEFF6, dp(14), 0xFFF6DDE9));
        row.addView(symbol, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, -2, 1f);
        clp.setMargins(dp(12), 0, dp(8), 0);
        row.addView(copy, clp);
        copy.addView(text(title, 14, INK, true));
        TextView sub = text(subtitle, 11, MUTED, false);
        sub.setPadding(0, dp(4), 0, 0);
        copy.addView(sub);
        TextView action = text("فتح  ›", 11, PURPLE, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(10), dp(8), dp(10), dp(8));
        action.setBackground(round(0xFFF1E8FB, dp(12), 0xFFE3D5F3));
        row.addView(action);
        card.setOnClickListener(v -> showView(targetView));
    }
    private void addPersonRow(LinearLayout card, Map<String, Object> person, String subtitle, String status) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(row, matchWrap());
        addAvatar(row, person, 50);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(12), 0, 0, 0);
        row.addView(details, lp);
        details.addView(text(personName(person), 14, INK, true));
        TextView sub = text(subtitle + "  ·  " + status, 11, MUTED, false);
        sub.setPadding(0, dp(4), 0, 0);
        details.addView(sub);
    }

    private void addAvatar(LinearLayout parent, Map<String, Object> person, int sizeDp) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(round(0xFFFFE8F2, dp(sizeDp / 3), 0xFFF4BBD2));
        frame.setElevation(dp(2));
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        frameLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        parent.addView(frame, frameLp);
        TextView backing = text("♥", sizeDp + 9, 0xFFFFD0E3, true);
        backing.setGravity(Gravity.CENTER);
        frame.addView(backing, new FrameLayout.LayoutParams(-1, -1));
        HeartImageView image = new HeartImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams imageLp = new FrameLayout.LayoutParams(-1, -1);
        imageLp.setMargins(dp(3), dp(3), dp(3), dp(3));
        frame.addView(image, imageLp);
        String url = avatarUrl(person.get("avatarUrl"));
        if (url != null && !url.trim().isEmpty()) {
            Glide.with(this).load(url).centerCrop()
                    .placeholder(round(0xFFFFEAF2, dp(sizeDp / 3), LINE))
                    .error(round(0xFFFFEAF2, dp(sizeDp / 3), LINE)).into(image);
        } else {
            image.setImageDrawable(null);
            backing.setText("♡");
            backing.setTextColor(ROSE);
        }
    }
    private void sectionTitle(String title, @Nullable String subtitle) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(17);
        lp.bottomMargin = dp(11);
        screenContent.addView(block, lp);
        LinearLayout headingRow = new LinearLayout(this);
        headingRow.setOrientation(LinearLayout.HORIZONTAL);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        block.addView(headingRow, matchWrap());
        TextView heading = text(title, 19, INK, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        headingRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView heart = text("♥", 15, ROSE, true);
        heart.setGravity(Gravity.CENTER);
        heart.setBackground(round(0xFFFFE5F0, dp(14), 0xFFF5D0E0));
        headingRow.addView(heart, new LinearLayout.LayoutParams(dp(34), dp(32)));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 12, MUTED, false);
            sub.setPadding(0, dp(5), 0, 0);
            sub.setLineSpacing(dp(3), 1f);
            block.addView(sub);
        }
    }

    private void emptyState(String symbol, String title, String description) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18), dp(24), dp(18), dp(24));
        box.setBackground(round(Color.WHITE, dp(19), LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(8);
        screenContent.addView(box, lp);
        TextView icon = text(symbol, 30, ROSE, true);
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);
        TextView heading = text(title, 14, INK, true);        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(8), 0, dp(4));
        box.addView(heading);
        TextView body = text(description, 12, MUTED, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(3), 1f);
        box.addView(body);
    }

    private LinearLayout panel() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(roundGradient(new int[]{Color.WHITE, 0xFFFFFAFD}, dp(23)));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(8));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = text(label, 14, primary ? Color.WHITE : 0xFFB52D69, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(52));
        button.setPadding(dp(18), dp(13), dp(18), dp(13));
        if (primary) {
            button.setBackground(roundGradient(new int[]{0xFFCB3A7A, 0xFF8F2054}, dp(17)));
            button.setElevation(dp(3));
        } else {
            button.setBackground(round(0xFFFFEDF5, dp(17), 0xFFF1C9DC));
        }
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(true);
        return view;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable roundGradient(int[] colors, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String safeView(String value) {
        return "send".equals(value) || "incoming".equals(value) || "sent".equals(value)
                || "discover".equals(value) || "store".equals(value) || "tasks".equals(value)
                || "leaderboard".equals(value) || "memories".equals(value) || "settings".equals(value) ? value : "home";
    }

    private String safeType(String value) {
        for (String allowed : new String[]{"brotherhood", "friendship", "love", "engagement", "marriage"}) {
            if (allowed.equals(value)) return value;
        }
        return "";
    }

    private String typeName(String type) {
        switch (type == null ? "" : type) {
            case "brotherhood": return "الأخوّة";
            case "friendship": return "الصداقة";
            case "love": return "الحب";
            case "engagement": return "الخطوبة";
            case "marriage": return "الزواج";
            default: return "علاقة CP";
        }
    }

    private String safeStatus(String status) {
        switch (status == null ? "" : status) {
            case "pending": return "بانتظار الرد";
            case "accepted": return "تم القبول";
            case "rejected": return "مرفوض";
            case "expired": return "انتهت المهلة";
            case "cancelled": return "ملغي";
            case "active": return "رابطة نشطة";
            default: return status == null || status.isEmpty() ? "—" : status;
        }
    }

    private String str(Object value) { return value == null ? "" : String.valueOf(value); }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new HashMap<>();
    }

    private String personName(Map<String, Object> user) {
        String name = str(user.get("displayName"));
        if (name.isEmpty()) name = str(user.get("username"));
        if (name.isEmpty()) name = str(user.get("name"));
        return name.isEmpty() ? "عضو JEHO" : name;
    }

    private String defaultString(Object value, String fallback) {
        String result = str(value);
        return result.isEmpty() || "null".equals(result) ? fallback : result;
    }

    private String numberString(Object value) {
        try { return String.format(java.util.Locale.getDefault(), "%,d", (long) Double.parseDouble(str(value))); }
        catch (Exception ignored) { return "0"; }
    }

    private String avatarUrl(Object value) {
        if (value == null) return null;
        String url = AssetCatalog.absoluteUrl(String.valueOf(value));
        if (url != null && url.startsWith("http://")) return "https://" + url.substring(7);
        return url;
    }

    private void updateStats() {
        if (activeStat == null) return;
        int active = 0;
        for (Map<String, Object> row : homeData) if ("active".equals(str(row.get("status")))) active++;
        int incoming = 0;
        for (Map<String, Object> row : incomingData) if ("pending".equals(str(row.get("status")))) incoming++;
        int sent = 0;
        for (Map<String, Object> row : sentData) if ("pending".equals(str(row.get("status")))) sent++;
        activeStat.setText(String.valueOf(active));
        incomingStat.setText(String.valueOf(incoming));
        sentStat.setText(String.valueOf(sent));
    }

    private void refreshAll() {
        loadHome();
        loadRequests();
        if ("discover".equals(currentView) || "leaderboard".equals(currentView)) loadDiscover(discoverType);
        if ("store".equals(currentView)) { loadCatalog(); loadCpCosmetics(); }
        if ("memories".equals(currentView)) loadMemories();
        if ("tasks".equals(currentView)) loadTasks();
        Toast.makeText(this, "جارٍ تحديث بيانات CP…", Toast.LENGTH_SHORT).show();
    }

    private void loadHome() {
        if (loadingHome || isFinishing()) return;
        loadingHome = true;
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().myCpRelationships());
            List<Map<String, Object>> rows = result.success && result.data != null
                    ? new ArrayList<>(result.data) : new ArrayList<>();
            if (rows.isEmpty()) {
                Result<com.Dramizo.Series.data.remote.dto.MiscDtos.CpStatusDto> legacy =
                        ApiCall.execute(c.getUserApi().myCp());
                if (legacy.success && legacy.data != null && legacy.data.hasCp && legacy.data.partner != null) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", legacy.data.relationId != null ? legacy.data.relationId : "legacy-cp");
                    row.put("type", "love");
                    row.put("status", "active");
                    row.put("points", legacy.data.bondScore);
                    row.put("level", Math.max(1, legacy.data.level));
                    row.put("legacy", true);
                    row.put("partner", gson.fromJson(gson.toJson(legacy.data.partner), Map.class));
                    AuthDtos.UserDto self = c.getSessionManager().getUser();
                    if (self != null) row.put("self", gson.fromJson(gson.toJson(self), Map.class));
                    rows.add(row);
                }
            }
            final List<Map<String, Object>> finalRows = rows;
            runOnUiThread(() -> {
                loadingHome = false;
                homeData.clear();
                homeData.addAll(finalRows);
                updateStats();
                if ("home".equals(currentView)) renderScreen();
            });
        });
    }

    private void loadRequests() {
        if (loadingRequests || isFinishing()) return;
        loadingRequests = true;
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> incoming = ApiCall.execute(c.getUserApi().cpRequests());
            Result<List<Map<String, Object>>> sent = ApiCall.execute(c.getUserApi().cpSentRequests());
            List<Map<String, Object>> in = incoming.success && incoming.data != null
                    ? new ArrayList<>(incoming.data) : new ArrayList<>();
            List<Map<String, Object>> out = sent.success && sent.data != null
                    ? new ArrayList<>(sent.data) : new ArrayList<>();
            runOnUiThread(() -> {
                loadingRequests = false;
                incomingData.clear();
                incomingData.addAll(in);
                sentData.clear();
                sentData.addAll(out);
                updateStats();
                if ("incoming".equals(currentView) || "sent".equals(currentView)) renderScreen();
                if (!incoming.success && incoming.error != null) toast(incoming.error);
                else if (!sent.success && sent.error != null) toast(sent.error);
            });
        });
    }

    private void loadDiscover(String type) {
        if (loadingDiscover || isFinishing()) return;
        loadingDiscover = true;
        if ("discover".equals(currentView) || "leaderboard".equals(currentView)) renderScreen();
        String safe = safeType(type);
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().discoverCp(safe.isEmpty() ? null : safe));
            List<Map<String, Object>> rows = result.success && result.data != null
                    ? new ArrayList<>(result.data) : new ArrayList<>();
            runOnUiThread(() -> {
                loadingDiscover = false;
                discoverData.clear();
                discoverData.addAll(rows);
                if ("discover".equals(currentView) || "leaderboard".equals(currentView)) renderScreen();
                if (!result.success) toast(result.error != null ? result.error : "تعذر تحميل العلاقات النشطة");
            });
        });
    }

    private void sendRequest(String recipientId) {
        String recipient = recipientId == null ? "" : recipientId.trim();
        String type = safeType(currentType);
        if (recipient.isEmpty() || type.isEmpty()) {
            toast("أدخل معرّف المستخدم واختر نوع العلاقة.");
            return;
        }
        String myId = c.getSessionManager().getUserId();
        if (myId != null && myId.equals(recipient)) {
            toast("لا يمكنك إرسال طلب ارتباط إلى حسابك.");
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("recipientId", recipient);
        body.put("type", type);
        body.put("idempotencyKey", UUID.randomUUID().toString());
        Toast.makeText(this, "جارٍ إرسال الطلب…", Toast.LENGTH_SHORT).show();
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().createProfileCpRequest(body));
            runOnUiThread(() -> {
                if (result.success) {
                    toast("تم إرسال طلب " + typeName(type) + ". ينتظر موافقة الطرف الآخر.");
                    showView("sent");
                    loadRequests();
                } else toast(result.error != null ? result.error : "تعذر إرسال طلب العلاقة");
            });
        });
    }

    private void respondRequest(String requestId, boolean accept) {
        if (requestId == null || requestId.trim().isEmpty()) {
            toast("تعذر تحديد الطلب.");
            return;
        }
        Map<String, Boolean> body = new HashMap<>();
        body.put("accept", accept);
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().respondCpRequest(requestId, body));
            runOnUiThread(() -> {
                if (result.success) {
                    String status = result.data == null ? "" : str(result.data.get("status"));
                    toast("expired".equals(status) ? "انتهت مهلة الطلب." :
                            accept ? "تم قبول العلاقة بنجاح." : "تم رفض الطلب.");
                    loadHome();
                    loadRequests();
                    renderScreen();
                } else toast(result.error != null ? result.error : "تعذرت معالجة الطلب");
            });
        });
    }

    private void cancelRequest(String requestId) {
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().cancelCpRequest(requestId));
            runOnUiThread(() -> {
                if (result.success) {
                    toast("تم إلغاء الطلب.");
                    loadRequests();
                } else toast(result.error != null ? result.error : "تعذر إلغاء الطلب");
            });
        });
    }

    private void endRelationship(String relationshipId) {
        if (relationshipId == null || relationshipId.trim().isEmpty()) return;
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().endCpRelationship(relationshipId));
            runOnUiThread(() -> {
                if (result.success) {
                    toast("تم إنهاء العلاقة.");
                    loadHome();
                    loadDiscover("");
                } else toast(result.error != null ? result.error : "تعذر إنهاء العلاقة");
            });
        });
    }

    private void toast(String message) {
        if (isFinishing() || isDestroyed()) return;
        Toast.makeText(this, message == null || message.trim().isEmpty() ? "حدث خطأ غير متوقع" : message,
                Toast.LENGTH_LONG).show();
    }


    /** Draws portrait photos through a heart-shaped mask for the CP visual identity. */
    private static class HeartImageView extends ImageView {
        private final Path heartPath = new Path();
        HeartImageView(android.content.Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) { super.onDraw(canvas); return; }
            heartPath.reset();
            heartPath.moveTo(w * 0.5f, h * 0.94f);
            heartPath.cubicTo(w * 0.40f, h * 0.82f, w * 0.04f, h * 0.58f, w * 0.06f, h * 0.31f);
            heartPath.cubicTo(w * 0.08f, h * 0.04f, w * 0.39f, h * 0.02f, w * 0.50f, h * 0.25f);
            heartPath.cubicTo(w * 0.61f, h * 0.02f, w * 0.92f, h * 0.04f, w * 0.94f, h * 0.31f);
            heartPath.cubicTo(w * 0.96f, h * 0.58f, w * 0.60f, h * 0.82f, w * 0.50f, h * 0.94f);
            heartPath.close();
            int save = canvas.save();
            canvas.clipPath(heartPath);
            super.onDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    @Override protected boolean wantsContentSystemPadding() { return false; }

    @Override public void onBackPressed() { finish(); }
}