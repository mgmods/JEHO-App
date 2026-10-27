package com.Dramizo.Series.presentation.cp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
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
    private String currentView = "home";
    private String currentType = "love";
    private String discoverType = "";
    private boolean loadingHome;
    private boolean loadingRequests;
    private boolean loadingDiscover;

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
        screenContent = new LinearLayout(this);
        screenContent.setOrientation(LinearLayout.VERTICAL);
        page.addView(screenContent, matchWrap());

        renderScreen();
        loadHome();
        loadRequests();
        if ("discover".equals(currentView)) loadDiscover(discoverType);
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
        hero.setBackground(roundGradient(new int[]{0xFF8E285E, 0xFFD64E88, 0xFFE5B765}, dp(25)));
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
        card.setPadding(dp(5), dp(12), dp(5), dp(12));
        card.setBackground(round(Color.WHITE, dp(17), LINE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(card, lp);
        TextView number = text(value, 20, ROSE, true);
        number.setGravity(Gravity.CENTER);
        card.addView(number);
        TextView caption = text(label, 10, MUTED, false);
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(0, dp(3), 0, 0);
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
        else if ("discover".equals(currentView)) loadDiscover(discoverType);
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
            default: renderHome(); break;
        }
    }

    private void renderHome() {
        sectionTitle("روابطك", "تابع علاقتك الحالية وتفاصيلها");
        List<Map<String, Object>> active = new ArrayList<>();
        for (Map<String, Object> row : homeData) {
            if ("active".equals(str(row.get("status")))) active.add(row);
        }
        if (active.isEmpty()) {
            emptyState("♡", "ما عندك علاقة نشطة حالياً",
                    "اختَر نوع العلاقة وأرسل طلباً. لن تتفعّل أي علاقة قبل موافقة الطرف الآخر.");
            TextView start = actionButton("إرسال طلب ارتباط", true);
            screenContent.addView(start, matchWrap());
            start.setOnClickListener(v -> showView("send"));
        } else {
            for (Map<String, Object> row : active) addRelationshipCard(row, false);
        }

        sectionTitle("اختر نوع الرابطة", "كل علاقة تبدأ بطلب وقبول متبادل");
        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        screenContent.addView(firstRow, matchWrap());
        String[][] types = {
                {"love", "♥", "الحب", "رابطة عاطفية"},
                {"marriage", "💞", "الزواج", "رابطة مميزة"},
                {"engagement", "💍", "الخطوبة", "خطوة رسمية"},
                {"friendship", "✦", "الصداقة", "رابطة صداقة"},
                {"brotherhood", "♛", "الأخوّة", "رابطة أخوية"}
        };
        for (int i = 0; i < types.length; i++) {
            final String type = types[i][0];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(6), dp(14), dp(6), dp(14));
            card.setBackground(round(Color.WHITE, dp(17), LINE));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(108), 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            firstRow.addView(card, lp);
            TextView symbol = text(types[i][1], 25, ROSE, true);
            symbol.setGravity(Gravity.CENTER);
            card.addView(symbol);
            TextView name = text(types[i][2], 13, INK, true);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(4), 0, dp(2));
            card.addView(name);
            TextView detail = text(types[i][3], 10, MUTED, false);
            detail.setGravity(Gravity.CENTER);
            card.addView(detail);
            card.setOnClickListener(v -> {
                currentType = type;
                showView("send");
            });
        }

        sectionTitle("مركز عالم CP", "ميزات إضافية ضمن خطة تطوير النظام");
        addFutureCard("🎯", "المهام والتحديات", "مهام مشتركة ومكافآت", "يتطلب ربط مهام CP ومكافآتها بالخادم والداشبورد.");
        addFutureCard("💎", "متجر CP", "هدايا وإطارات وتأثيرات", "يتطلب ربط كتالوج CP والمشتريات والمخزون قبل تفعيل الشراء.");
        addFutureCard("🏆", "ترتيب الأزواج", "عالمي وإقليمي وموسمي", "يتطلب خدمة ترتيب ومواسم موثوقة من الخادم.");
        addFutureCard("📖", "الذكريات والإنجازات", "رحلة العلاقة المشتركة", "يتطلب واجهة بيانات للذكريات والإنجازات.");
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
            chip.setOnClickListener(v -> { currentType = type[0]; renderScreen(); });
        }

        sectionTitle("معرّف المستخدم", null);
        EditText idInput = new EditText(this);
        idInput.setSingleLine(true);
        idInput.setTextSize(14);
        idInput.setTextColor(INK);
        idInput.setHintTextColor(MUTED);
        idInput.setHint("أدخل ID المستخدم");
        idInput.setInputType(InputType.TYPE_CLASS_TEXT);
        idInput.setPadding(dp(15), dp(12), dp(15), dp(12));
        idInput.setBackground(round(Color.WHITE, dp(15), LINE));
        screenContent.addView(idInput, matchWrap());

        TextView send = actionButton("إرسال طلب " + typeName(currentType), true);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(-1, -2);
        sendLp.topMargin = dp(12);
        screenContent.addView(send, sendLp);
        send.setOnClickListener(v -> sendRequest(idInput.getText().toString()));
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
        screenContent.addView(card, matchWrap());
        Map<String, Object> self = map(row.get("self"));
        Map<String, Object> partner = map(row.get("partner"));
        if (self.isEmpty()) self = map(row.get("userA"));
        if (partner.isEmpty()) partner = map(row.get("userB"));
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        pair.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(pair, matchWrap());
        addAvatar(pair, self, 48);
        TextView heart = text("♥", 18, ROSE, true);
        heart.setGravity(Gravity.CENTER);
        pair.addView(heart, new LinearLayout.LayoutParams(dp(28), dp(42)));
        addAvatar(pair, partner, 48);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(0, -2, 1f);
        detailLp.setMargins(dp(10), 0, 0, 0);
        pair.addView(details, detailLp);
        TextView names = text(personName(self) + " ♥ " + personName(partner), 13, INK, true);
        details.addView(names);
        TextView meta = text(typeName(str(row.get("type"))) + "  ·  المستوى " + defaultString(row.get("level"), "1"), 11, MUTED, false);
        meta.setPadding(0, dp(4), 0, 0);
        details.addView(meta);
        TextView points = text(numberString(row.get("points")) + " نقطة", 11, ROSE, true);
        points.setPadding(0, dp(4), 0, 0);
        details.addView(points);

        String partnerId = str(partner.get("id"));
        if (!partnerId.isEmpty()) {
            TextView profile = actionButton("عرض الملف الشخصي", false);
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
            plp.topMargin = dp(10);
            card.addView(profile, plp);
            profile.setOnClickListener(v -> UserProfileCardSheet.show(CpCenterActivity.this, partnerId));
        }
        if (!publicList && !"true".equals(String.valueOf(row.get("legacy")))) {
            TextView end = actionButton("إنهاء العلاقة", false);
            end.setTextColor(Color.rgb(173, 60, 56));
            end.setBackground(round(0xFFFFEEEE, dp(13), 0xFFF6D7D7));
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2);
            elp.topMargin = dp(8);
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

    private void addFutureCard(String icon, String title, String subtitle, String explanation) {
        LinearLayout card = panel();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        screenContent.addView(card, lp);
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
        TextView badge = text("قريباً", 10, PURPLE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(6), dp(9), dp(6));
        badge.setBackground(round(0xFFF1E8FB, dp(12), 0xFFE3D5F3));
        row.addView(badge);
        card.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(explanation)
                .setPositiveButton("حسناً", (d, w) -> d.dismiss())
                .show());
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
        frame.setBackground(round(0xFFFFEAF2, dp(sizeDp / 2), LINE));
        frame.setClipToOutline(true);
        parent.addView(frame, new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        TextView fallback = text("♡", 22, ROSE, true);
        fallback.setGravity(Gravity.CENTER);
        frame.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        String url = avatarUrl(person.get("avatarUrl"));
        if (url != null && !url.trim().isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            frame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            Glide.with(this).load(url).circleCrop().placeholder(round(0xFFFFEAF2, dp(sizeDp / 2), LINE))
                    .error(round(0xFFFFEAF2, dp(sizeDp / 2), LINE)).into(image);
        }
    }

    private void sectionTitle(String title, @Nullable String subtitle) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(9);
        screenContent.addView(block, lp);
        block.addView(text(title, 17, INK, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(subtitle, 11, MUTED, false);
            sub.setPadding(0, dp(3), 0, 0);
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
        TextView heading = text(title, 14, INK, true);
        heading.setGravity(Gravity.CENTER);
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
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(round(Color.WHITE, dp(19), LINE));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(6));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView button = text(label, 13, primary ? Color.WHITE : ROSE, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        button.setBackground(round(primary ? ROSE : 0xFFFFEAF2, dp(14), primary ? ROSE : LINE));
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
                || "discover".equals(value) ? value : "home";
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
        if ("discover".equals(currentView)) loadDiscover(discoverType);
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
        if ("discover".equals(currentView)) renderScreen();
        String safe = safeType(type);
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().discoverCp(safe.isEmpty() ? null : safe));
            List<Map<String, Object>> rows = result.success && result.data != null
                    ? new ArrayList<>(result.data) : new ArrayList<>();
            runOnUiThread(() -> {
                loadingDiscover = false;
                discoverData.clear();
                discoverData.addAll(rows);
                if ("discover".equals(currentView)) renderScreen();
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

    @Override protected boolean wantsContentSystemPadding() { return false; }

    @Override public void onBackPressed() { finish(); }
}