package com.Dramizo.Series.presentation.admin

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.Dramizo.Series.data.remote.api.AdminApi
import com.Dramizo.Series.data.remote.dto.ApiResponse
import com.Dramizo.Series.presentation.common.ContainerProvider
import com.Dramizo.Series.presentation.common.ThemedActivity
import com.Dramizo.Series.util.StaffRoleHelper
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Native JEHO super-admin console.
 * All reads and mutations go to the existing authenticated backend Admin API;
 * AdminGuard remains authoritative. No WebView and no local/mock admin data.
 */
class AdminConsoleActivity : ThemedActivity() {
    private lateinit var api: AdminApi
    private lateinit var body: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var backButton: TextView
    private var currentSection = "home"

    private data class Module(val key: String, val title: String, val subtitle: String, val icon: String)

    private val modules = listOf(
        Module("users", "إدارة المستخدمين", "بحث، مراجعة الحالة والحظر", "👥"),
        Module("rooms", "إدارة الرومات", "عرض الرومات وإغلاق روم عند الحاجة", "🏠"),
        Module("streams", "البث المباشر", "عرض البث وإنهاؤه من الخادم", "📡"),
        Module("agencies", "الوكالات", "عرض الوكالات وتعليق وكالة", "🏢"),
        Module("agencyApplications", "طلبات الوكالات", "قبول الطلبات أو رفضها", "📨"),
        Module("rechargeApplications", "طلبات وكلاء الشحن", "مراجعة طلبات الوكلاء", "💳"),
        Module("rechargeAgents", "وكلاء الشحن", "تفعيل الوكلاء أو إيقافهم", "💰"),
        Module("operators", "المديرون والصلاحيات", "إدارة مديري لوحة التحكم المحدودين", "🛡️"),
        Module("reports", "البلاغات", "مراجعة البلاغات وحسمها", "🚩"),
        Module("genderVerifications", "توثيق الجنس", "مراجعة طلبات التوثيق وقبولها أو رفضها", "🪪"),
        Module("withdrawals", "طلبات السحب", "مراجعة طلبات السحب", "🏦"),
        Module("recharges", "طلبات الشحن", "إتمام أو إلغاء طلب شحن", "🪙"),
        Module("walletTransactions", "حركة المحفظة", "عرض معاملات المحفظة", "📒"),
        Module("walletAdjust", "تعديل رصيد مستخدم", "إضافة أو خصم رصيد مع سبب وسجل مراجعة", "🧮"),
        Module("cosmetics", "الإطارات والدخوليات", "تفعيل أو تعطيل المقتنيات", "✨"),
        Module("grantCosmetics", "إهداء المميزات", "منح إطار أو دخولية بمدة محددة أو دائمة", "🎁"),
        Module("vanityIds", "الآي دي المميز", "إدارة الآيديات وإهداؤها بمدة أو بشكل دائم", "🔢"),
        Module("gifts", "الهدايا والمميزات", "عرض كتالوج الهدايا الحالي", "🎁"),
        Module("vip", "العضويات VIP", "مراجعة العضويات ومنح VIP", "👑"),
        Module("notifications", "رسائل وإشعارات الإدارة", "إرسال رسالة تظهر في قسم رسالة الإدارة داخل الدردشة مع إشعار للمستخدم", "🔔"),
        Module("logs", "سجل الإدارة", "مراجعة سجل عمليات الإدارة", "🧾")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = try { ContainerProvider.from(this).sessionManager.user } catch (_: Exception) { null }
        if (!StaffRoleHelper.isSuper(user)) {
            Toast.makeText(this, "هذه الصفحة مخصصة للسوبر أدمن فقط", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        api = ContainerProvider.from(this).adminApi
        buildShell()
        showHome()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(BG)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(0xFF252A5B.toInt(), 0xFF6245A8.toInt())
            )
        }
        backButton = TextView(this).apply {
            text = "‹ رجوع"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            visibility = View.GONE
            setOnClickListener { showHome() }
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleView = TextView(this).apply {
            text = "مركز إدارة JEHO"
            textSize = 19f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "تحكّم مباشر من داخل التطبيق"
            textSize = 12f
            setTextColor(0xFFE3DDF7.toInt())
            setPadding(0, dp(3), 0, 0)
        }
        heading.addView(titleView)
        heading.addView(subtitle)
        header.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(backButton)
        root.addView(header)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(ACCENT)
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        val scroll = ScrollView(this).apply { clipToPadding = false; setPadding(dp(14), dp(12), dp(14), dp(24)) }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showHome() {
        currentSection = "home"
        titleView.text = "مركز إدارة JEHO"
        backButton.visibility = View.GONE
        body.removeAllViews()
        val intro = TextView(this).apply {
            text = "أهلًا بك في مركز الإدارة\nاختر القسم الذي تريد إدارته. جميع البيانات والأوامر تُقرأ من الخادم مباشرة."
            textSize = 14f
            setTextColor(TEXT)
            setPadding(dp(4), dp(4), dp(4), dp(14))
        }
        body.addView(intro)
        load(api.overview()) { data ->
            val stats = mapOfEntries(data)
            if (stats.isNotEmpty()) {
                val card = card()
                addText(card, "نظرة عامة", 16f, true, TEXT)
                val display = stats.entries.take(6).joinToString("\n") { (k, v) -> "${prettyKey(k)}: ${v ?: "—"}" }
                addText(card, display, 13f, false, MUTED)
                body.addView(card)
            }
        }
        addText(body, "أدوات الإدارة", 16f, true, TEXT)
        val rows = modules.chunked(2)
        rows.forEach { pair ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            pair.forEachIndexed { index, module ->
                val tile = card().apply {
                    setPadding(dp(12), dp(13), dp(12), dp(13))
                    isClickable = true
                    isFocusable = true
                    foreground = android.util.TypedValue().let {
                        val out = android.util.TypedValue()
                        theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                        if (out.resourceId != 0) androidx.core.content.ContextCompat.getDrawable(this@AdminConsoleActivity, out.resourceId) else null
                    }
                    setOnClickListener { openModule(module.key) }
                }
                val icon = TextView(this).apply {
                    text = module.icon
                    textSize = 23f
                    gravity = Gravity.CENTER
                    setBackground(roundRect(0xFFF0ECFB.toInt(), 14))
                    layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
                }
                tile.addView(icon)
                addText(tile, module.title, 14f, true, TEXT)
                addText(tile, module.subtitle, 11f, false, MUTED)
                addText(tile, "فتح القسم  ←", 11f, true, ACCENT)
                val lp = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    bottomMargin = dp(10)
                    if (index == 0) marginEnd = dp(5) else marginStart = dp(5)
                }
                row.addView(tile, lp)
            }
            if (pair.size == 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(5) })
            }
            body.addView(row)
        }
    }

    private fun openModule(key: String) {
        currentSection = key
        val module = modules.firstOrNull { it.key == key }
        titleView.text = module?.title ?: "مركز إدارة JEHO"
        backButton.visibility = View.VISIBLE
        body.removeAllViews()
        when (key) {
            "users" -> {
                val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val search = EditText(this).apply {
                    hint = "ابحث بالاسم أو المعرّف"
                    textSize = 14f
                    setSingleLine(true)
                    setTextColor(TEXT)
                    setHintTextColor(MUTED)
                    background = roundRect(Color.WHITE, 12)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                searchRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
                searchRow.addView(button("بحث") { loadUsers(search.text.toString().trim()) })
                body.addView(searchRow)
                loadUsers("")
            }
            "rooms" -> load(api.rooms(1, 40)) { renderItems(it, "rooms") }
            "streams" -> load(api.streams(1, 40)) { renderItems(it, "streams") }
            "agencies" -> load(api.agencies(1, 40)) { renderItems(it, "agencies") }
            "agencyApplications" -> load(api.agencyApplications(1, 40)) { renderItems(it, "agencyApplications") }
            "rechargeApplications" -> load(api.rechargeAgentApplications()) { renderItems(it, "rechargeApplications") }
            "rechargeAgents" -> load(api.rechargeAgents()) { renderItems(it, "rechargeAgents") }
            "operators" -> {
                body.addView(button("＋ إضافة مدير محدود") { showCreateOperatorDialog() })
                load(api.operators()) { renderItems(it, "operators") }
            }
            "reports" -> load(api.reports(1, 40)) { renderItems(it, "reports") }
            "genderVerifications" -> load(api.genderVerifications("pending")) { renderItems(it, "genderVerifications") }
            "withdrawals" -> load(api.withdrawals(1, 40)) { renderItems(it, "withdrawals") }
            "recharges" -> load(api.recharges(1, 40)) { renderItems(it, "recharges") }
            "walletTransactions" -> load(api.walletTransactions(1, 50)) { renderItems(it, "walletTransactions") }
            "walletAdjust" -> {
                body.addView(button("＋ تعديل رصيد مستخدم") { showAdjustWalletDialog() })
                addText(body, "كل تعديل يحتاج معرّف المستخدم وسبباً واضحاً وتأكيداً قبل الإرسال.", 13f, false, MUTED)
            }
            "cosmetics" -> load(api.cosmetics()) { renderItems(it, "cosmetics") }
            "grantCosmetics" -> load(api.cosmetics()) { renderItems(it, "grantCosmetics") }
            "vanityIds" -> {
                body.addView(button("＋ إضافة آي دي إلى الكتالوج") { showCreateVanityDialog() })
                load(api.vanityIds()) { renderItems(it, "vanityIds") }
            }
            "gifts" -> load(api.gifts()) { renderItems(it, "gifts") }
            "vip" -> {
                body.addView(button("＋ منح عضوية VIP") { showAssignVipDialog() })
                body.addView(button("＋ إنشاء خطة VIP") { showCreateVipPlanDialog() })
                load(api.vipMembers(1, 40)) { renderItems(it, "vip") }
                val plansButton = button("إدارة خطط VIP") {
                    load(api.vipPlans()) { renderItems(it, "vipPlans") }
                }
                body.addView(plansButton)
            }
            "notifications" -> {
                body.addView(button("＋ رسالة إدارة (دردشة + إشعار)") { showNotificationDialog() })
                load(api.notifications(1, 40)) { renderItems(it, "notifications") }
            }
            "logs" -> load(api.logs(1, 50)) { renderItems(it, "logs") }
        }
    }

    private fun loadUsers(search: String) {
        body.removeViews(1, (body.childCount - 1).coerceAtLeast(0))
        load(api.users(1, 40, search)) { renderItems(it, "users") }
    }

    private fun load(call: Call<ApiResponse<Any>>, onSuccess: (Any?) -> Unit) {
        progress.visibility = View.VISIBLE
        call.enqueue(object : Callback<ApiResponse<Any>> {
            override fun onResponse(call: Call<ApiResponse<Any>>, response: Response<ApiResponse<Any>>) {
                progress.visibility = View.GONE
                val envelope = response.body()
                if (response.isSuccessful && envelope != null && envelope.success) {
                    onSuccess(envelope.data)
                } else {
                    val message = envelope?.message?.takeIf { it.isNotBlank() }
                        ?: "تعذر تنفيذ الطلب (HTTP ${response.code()})"
                    showError(message)
                }
            }

            override fun onFailure(call: Call<ApiResponse<Any>>, error: Throwable) {
                progress.visibility = View.GONE
                showError("تعذر الاتصال بالخادم. تحقق من الإنترنت ثم أعد المحاولة.")
            }
        })
    }

    private fun renderItems(data: Any?, type: String) {
        val items = extractItems(data)
        if (items.isEmpty()) {
            val empty = card()
            addText(empty, "لا توجد بيانات للعرض أو أن الخادم أعاد قائمة فارغة.", 14f, false, MUTED)
            body.addView(empty)
            return
        }
        items.forEach { item ->
            val card = card()
            val id = value(item, "id", "userId", "roomId", "agencyId", "streamId")
            val primary = value(item, "displayName", "name", "title", "username", "email", "roomName") ?: "عنصر بدون اسم"
            val status = value(item, "status", "state", "isActive", "type")
            val role = value(item, "staffRole", "role")
            val code = value(item, "code")
            val publicId = value(item, "publicId")
            val roomTitle = value(item, "title", "name", "roomName") ?: ""
            val accessMode = value(item, "accessMode") ?: "public"
            val entryFee = value(item, "entryFeeCoins") ?: "0"
            val vipLevel = value(item, "level") ?: "1"
            val vipPrice = value(item, "coinPriceMonthly", "price") ?: "0"
            addText(card, primary, 15f, true, TEXT)
            if (!id.isNullOrBlank()) addText(card, "المعرّف: $id", 11f, false, MUTED)
            if (!status.isNullOrBlank()) addText(card, "الحالة: $status", 12f, false, MUTED)
            val detail = listOf("createdAt", "role", "level", "membersCount", "ownerName", "coinPrice", "diamondValue")
                .mapNotNull { k -> value(item, k)?.let { "${prettyKey(k)}: $it" } }
            if (detail.isNotEmpty()) addText(card, detail.take(3).joinToString("   •   "), 12f, false, MUTED)

            when (type) {
                "users" -> if (!id.isNullOrBlank()) {
                    val banned = status?.contains("banned", true) == true
                    card.addView(button("تعديل الدور والحالة") { showUserAccessDialog(id, role, status) })
                    card.addView(button(if (banned) "إلغاء الحظر" else "حظر المستخدم") {
                        confirm(if (banned) "إلغاء حظر المستخدم؟" else "تأكيد حظر المستخدم؟") {
                            load(if (banned) api.unbanUser(id) else api.banUser(id)) {
                                toast(if (banned) "تم إرسال طلب إلغاء الحظر للخادم" else "تم إرسال طلب الحظر للخادم")
                                openModule("users")
                            }
                        }
                    })
                    card.addView(button("حذف الحساب") {
                        confirm("سيُطلب حذف الحساب. تأكد من الهوية والضرورة قبل المتابعة.") {
                            load(api.deleteUser(id)) {
                                toast("استجاب الخادم لطلب حذف الحساب")
                                openModule("users")
                            }
                        }
                    })
                }
                "rooms" -> if (!id.isNullOrBlank()) {
                    card.addView(button("تعديل إعدادات الروم") {
                        showEditRoomDialog(id, roomTitle, accessMode, entryFee)
                    })
                    card.addView(button("إغلاق الروم") {
                        confirm("سيتم إغلاق الروم على الخادم. هل تريد المتابعة؟") {
                            load(api.closeRoom(id)) { toast("استجاب الخادم لطلب إغلاق الروم"); openModule("rooms") }
                        }
                    })
                }
                "streams" -> if (!id.isNullOrBlank()) {
                    card.addView(button("إنهاء البث") {
                        confirm("سيتم إنهاء البث على الخادم. هل تريد المتابعة؟") {
                            load(api.endStream(id)) { toast("استجاب الخادم لطلب إنهاء البث"); openModule("streams") }
                        }
                    })
                    card.addView(button("إنهاء قسري للبث") {
                        confirm("استخدم الإنهاء القسري فقط إذا فشل الإنهاء العادي. متابعة؟") {
                            load(api.forceEndStream(id, mapOf("reason" to "super_admin_force_end"))) {
                                toast("استجاب الخادم لطلب الإنهاء القسري"); openModule("streams")
                            }
                        }
                    })
                }
                "agencies" -> if (!id.isNullOrBlank()) card.addView(button("تعليق الوكالة") {
                    confirm("تعليق الوكالة قد يؤثر على أعضائها. هل تريد المتابعة؟") {
                        load(api.suspendAgency(id, mapOf("reason" to "تم التعليق من تطبيق JEHO"))) {
                            toast("استجاب الخادم لطلب تعليق الوكالة"); openModule("agencies")
                        }
                    }
                })
                "vipPlans" -> if (!id.isNullOrBlank()) {
                    card.addView(button("تعديل الخطة") { showEditVipPlanDialog(id, primary, vipLevel, vipPrice) })
                    val active = status?.equals("true", true) == true
                    card.addView(button(if (active) "تعطيل الخطة" else "تفعيل الخطة") {
                        confirm("تغيير حالة خطة VIP؟") {
                            load(api.updateVipPlan(id, mapOf("isActive" to !active))) {
                                toast("تم تحديث خطة VIP")
                                load(api.vipPlans()) { renderItems(it, "vipPlans") }
                            }
                        }
                    })
                }
                "vip" -> if (!id.isNullOrBlank()) {
                    card.addView(button("سحب عضوية VIP") {
                        confirm("سحب عضوية VIP من المستخدم؟") {
                            load(api.revokeVip(id)) {
                                toast("استجاب الخادم لطلب سحب VIP")
                                openModule("vip")
                            }
                        }
                    })
                }
                "agencyApplications" -> if (!id.isNullOrBlank()) {
                    card.addView(button("قبول الطلب") {
                        confirm("تأكيد قبول طلب الوكالة؟") {
                            load(api.approveAgencyApplication(id, mapOf("reviewNote" to "تمت الموافقة من تطبيق JEHO"))) {
                                toast("تمت معالجة طلب الوكالة"); openModule("agencyApplications")
                            }
                        }
                    })
                    card.addView(button("رفض الطلب") {
                        confirm("تأكيد رفض طلب الوكالة؟") {
                            load(api.rejectAgencyApplication(id, mapOf("reviewNote" to "لم يستوفِ الطلب شروط القبول"))) {
                                toast("تمت معالجة طلب الوكالة"); openModule("agencyApplications")
                            }
                        }
                    })
                }
                "rechargeApplications" -> if (!id.isNullOrBlank()) {
                    card.addView(button("قبول وكيل الشحن") { showApproveRechargeAgentDialog(id) })
                    card.addView(button("رفض الطلب") {
                        confirm("رفض طلب وكيل الشحن؟") {
                            load(api.rejectRechargeAgentApplication(id, mapOf("note" to "لم يستوفِ الطلب شروط القبول"))) {
                                toast("تمت معالجة طلب وكيل الشحن"); openModule("rechargeApplications")
                            }
                        }
                    })
                }
                "rechargeAgents" -> if (!id.isNullOrBlank()) {
                    val active = status?.equals("active", true) == true
                    card.addView(button(if (active) "إيقاف الوكيل" else "تفعيل الوكيل") {
                        confirm("تأكيد تغيير حالة وكيل الشحن؟") {
                            load(api.updateRechargeAgent(id, mapOf("status" to if (active) "suspended" else "active"))) {
                                toast("تم تحديث حالة الوكيل"); openModule("rechargeAgents")
                            }
                        }
                    })
                    card.addView(button("تعديل رصيد الوكيل") { showAdjustFloatDialog(id) })
                }
                "genderVerifications" -> if (!id.isNullOrBlank()) {
                    card.addView(button("قبول التوثيق") {
                        confirm("تأكيد قبول طلب التوثيق؟") {
                            load(api.approveGenderVerification(id, mapOf("note" to "تمت المراجعة من تطبيق JEHO"))) {
                                toast("تمت معالجة طلب التوثيق"); openModule("genderVerifications")
                            }
                        }
                    })
                    card.addView(button("رفض التوثيق") {
                        confirm("تأكيد رفض طلب التوثيق؟") {
                            load(api.rejectGenderVerification(id, mapOf("note" to "تمت المراجعة من تطبيق JEHO"))) {
                                toast("تمت معالجة طلب التوثيق"); openModule("genderVerifications")
                            }
                        }
                    })
                }
                "operators" -> if (!id.isNullOrBlank()) {
                    val active = status?.contains("suspended", true) != true && status?.contains("inactive", true) != true
                    card.addView(button("تعديل صلاحيات المدير") { showEditOperatorDialog(id, item) })
                    card.addView(button(if (active) "تعطيل المدير" else "تفعيل المدير") {
                        confirm("تغيير صلاحية دخول هذا المدير؟") {
                            load(api.updateOperator(id, mapOf("active" to !active))) {
                                toast("تم تحديث المدير"); openModule("operators")
                            }
                        }
                    })
                    card.addView(button("إزالة المدير") {
                        confirm("سيتم سحب صلاحية لوحة التحكم من هذا المدير. متابعة؟") {
                            load(api.deleteOperator(id)) { toast("تمت إزالة المدير"); openModule("operators") }
                        }
                    })
                }
                "reports" -> if (!id.isNullOrBlank()) {
                    card.addView(button("حسم البلاغ") {
                        confirm("تأكيد وضع البلاغ كمحلول؟") {
                            load(api.resolveReport(id, mapOf("adminNote" to "تمت المراجعة من تطبيق JEHO"))) {
                                toast("تم تحديث البلاغ"); openModule("reports")
                            }
                        }
                    })
                    card.addView(button("رفض البلاغ") {
                        confirm("تأكيد رفض البلاغ؟") {
                            load(api.dismissReport(id, mapOf("adminNote" to "تمت المراجعة ولا يلزم إجراء"))) {
                                toast("تم تحديث البلاغ"); openModule("reports")
                            }
                        }
                    })
                }
                "withdrawals" -> if (!id.isNullOrBlank()) {
                    card.addView(button("اعتماد السحب") {
                        confirm("اعتماد طلب السحب؟ تحقق من الدفع فعلياً قبل الاعتماد.") {
                            load(api.approveWithdrawal(id)) { toast("استجاب الخادم لطلب اعتماد السحب"); openModule("withdrawals") }
                        }
                    })
                    card.addView(button("رفض السحب") {
                        confirm("رفض طلب السحب؟") {
                            load(api.rejectWithdrawal(id, mapOf("adminNote" to "تم الرفض من تطبيق JEHO"))) {
                                toast("تمت معالجة طلب السحب"); openModule("withdrawals")
                            }
                        }
                    })
                }
                "recharges" -> if (!id.isNullOrBlank()) {
                    card.addView(button("إتمام طلب الشحن") {
                        confirm("لا تعتمد إلا بعد التحقق من الدفع. متابعة؟") {
                            load(api.completeRecharge(id, mapOf("note" to "تم التحقق يدوياً من تطبيق JEHO"))) {
                                toast("استجاب الخادم لطلب إتمام الشحن"); openModule("recharges")
                            }
                        }
                    })
                    card.addView(button("إلغاء طلب الشحن") {
                        confirm("تأكيد إلغاء طلب الشحن؟") {
                            load(api.cancelRecharge(id)) { toast("تمت معالجة طلب الشحن"); openModule("recharges") }
                        }
                    })
                }
                "gifts" -> if (!id.isNullOrBlank()) {
                    val active = status?.equals("true", true) == true || status?.equals("active", true) == true
                    card.addView(button(if (active) "تعطيل الهدية" else "تفعيل الهدية") {
                        confirm("تغيير حالة الهدية؟") {
                            load(api.updateGift(id, mapOf("isActive" to !active))) {
                                toast("تم تحديث الهدية"); openModule("gifts")
                            }
                        }
                    })
                }
                "grantCosmetics" -> if (!code.isNullOrBlank()) {
                    card.addView(button("إهداء هذه الميزة لمستخدم") { showGrantCosmeticDialog(code) })
                }
                "vanityIds" -> if (!id.isNullOrBlank() && !publicId.isNullOrBlank()) {
                    card.addView(button("إهداء الآي دي لمستخدم") { showGrantVanityDialog(publicId) })
                    when (status?.lowercase()) {
                        "owned", "reserved" -> card.addView(button("سحب الآي دي وإعادته للسوق") {
                            confirm("سيُستعاد الآي دي السابق للمالك إن وجد، ثم يعود هذا الآي دي للسوق. متابعة؟") {
                                load(api.revokeVanityId(id)) { toast("تمت معالجة طلب سحب الآي دي"); openModule("vanityIds") }
                            }
                        })
                        "disabled" -> card.addView(button("إعادة تفعيل الآي دي") {
                            confirm("إعادة الآي دي إلى السوق؟") {
                                load(api.enableVanityId(id)) { toast("تم تفعيل الآي دي"); openModule("vanityIds") }
                            }
                        })
                        else -> card.addView(button("تعطيل الآي دي") {
                            confirm("إخفاء الآي دي من السوق؟ إذا كان مملوكاً سيُعاد الآي دي السابق لصاحبه.") {
                                load(api.disableVanityId(id)) { toast("تم تعطيل الآي دي"); openModule("vanityIds") }
                            }
                        })
                    }
                }
                "cosmetics" -> if (!id.isNullOrBlank()) {
                    val active = status?.contains("true", true) == true || status?.contains("active", true) == true
                    card.addView(button(if (active) "تعطيل الميزة" else "تفعيل الميزة") {
                        confirm("تغيير حالة الميزة؟") {
                            load(api.updateCosmetic(id, mapOf("isActive" to !active))) {
                                toast("تم تحديث الميزة"); openModule("cosmetics")
                            }
                        }
                    })
                }
            }
            body.addView(card)
        }
    }

    private fun showAdjustWalletDialog() {
        val form = formFields(listOf(
            "معرّف المستخدم (UUID)",
            "تغيير العملات (+ إضافة / - خصم)",
            "تغيير الألماس (+ إضافة / - خصم)",
            "تغيير العملات الفضية (اختياري)",
            "تغيير نقاط اللعب (اختياري)",
            "السبب / ملاحظة إلزامية"
        ))
        AlertDialog.Builder(this).setTitle("تعديل محفظة المستخدم").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("مراجعة ثم تنفيذ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userId = form.second[0].text.toString().trim()
                        val coins = form.second[1].text.toString().trim().toIntOrNull() ?: 0
                        val diamonds = form.second[2].text.toString().trim().toIntOrNull() ?: 0
                        val silver = form.second[3].text.toString().trim().toIntOrNull() ?: 0
                        val points = form.second[4].text.toString().trim().toIntOrNull() ?: 0
                        val note = form.second[5].text.toString().trim()
                        if (userId.isBlank() || note.isBlank() ||
                            (coins == 0 && diamonds == 0 && silver == 0 && points == 0)) {
                            toast("أدخل معرّف المستخدم وتغييراً غير صفري وسبباً واضحاً")
                            return@setOnClickListener
                        }
                        val payload = mapOf(
                            "coinsDelta" to coins, "diamondsDelta" to diamonds,
                            "silverCoinsDelta" to silver, "gamePointsDelta" to points,
                            "note" to note, "reason" to note
                        )
                        confirm("تأكيد تعديل رصيد المستخدم؟ العملات: " + coins +
                            "، الألماس: " + diamonds + "، الفضية: " + silver +
                            "، النقاط: " + points + ". لا يمكن تجاهل أثر العملية.") {
                            load(api.adjustWallet(userId, payload)) {
                                dialog.dismiss()
                                toast("استجاب الخادم لطلب تعديل المحفظة")
                                openModule("walletTransactions")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showEditRoomDialog(id: String, title: String, accessMode: String, entryFee: String) {
        val form = formFields(listOf("اسم الروم", "وضع الدخول: free أو paid أو permanent", "رسوم الدخول بالعملات"))
        form.second[0].setText(title)
        form.second[1].setText(accessMode)
        form.second[2].setText(entryFee)
        AlertDialog.Builder(this).setTitle("تعديل الروم").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newTitle = form.second[0].text.toString().trim()
                        val mode = form.second[1].text.toString().trim().lowercase()
                        val fee = form.second[2].text.toString().trim().toLongOrNull()
                        if (newTitle.isBlank() || mode !in setOf("free", "paid", "permanent") || fee == null || fee < 0) {
                            toast("تحقق من الاسم ووضع الدخول (free/paid/permanent) والرسوم")
                            return@setOnClickListener
                        }
                        confirm("حفظ إعدادات الروم؟") {
                            load(api.updateRoom(id, mapOf("title" to newTitle, "accessMode" to mode, "entryFeeCoins" to fee))) {
                                dialog.dismiss()
                                toast("تم تحديث إعدادات الروم")
                                openModule("rooms")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showAdjustFloatDialog(id: String) {
        val form = formFields(listOf("مقدار التغيير بالعملات (+ إضافة / - خصم)", "ملاحظة إلزامية"))
        AlertDialog.Builder(this).setTitle("تعديل رصيد وكيل الشحن").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("مراجعة ثم تعديل", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val amount = form.second[0].text.toString().trim().toLongOrNull()
                        val note = form.second[1].text.toString().trim()
                        if (amount == null || amount == 0L || note.isBlank()) {
                            toast("أدخل مقداراً غير صفري وملاحظة")
                            return@setOnClickListener
                        }
                        confirm("تأكيد تغيير رصيد الوكيل بمقدار " + amount + " عملة؟") {
                            load(api.adjustRechargeAgentFloat(id, mapOf("amount" to amount, "note" to note))) {
                                dialog.dismiss()
                                toast("تمت معالجة تعديل رصيد الوكيل")
                                openModule("rechargeAgents")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showEditOperatorDialog(id: String, item: Any) {
        val map = item as? Map<*, *>
        val currentName = map?.get("displayName")?.toString() ?: ""
        val currentPermissions = (map?.get("permissions") as? Map<*, *>)
            ?.entries?.filter { it.value?.toString() in setOf("read", "write") }
            ?.joinToString(",") { it.key.toString() + ":" + it.value.toString() }
            ?.takeIf { it.isNotBlank() }
            ?: "users:read,rooms:read"
        val form = formFields(listOf("اسم المدير", "كلمة مرور جديدة (اتركها فارغة للإبقاء)", "الصلاحيات: users:read,rooms:write"))
        form.second[0].setText(currentName)
        form.second[2].setText(currentPermissions)
        AlertDialog.Builder(this).setTitle("تعديل صلاحيات المدير").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = form.second[0].text.toString().trim()
                        val password = form.second[1].text.toString()
                        val rawPermissions = form.second[2].text.toString().trim()
                        val permissions = mutableMapOf<String, String>()
                        rawPermissions.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { pair ->
                            val bits = pair.split(":")
                            if (bits.size == 2 && bits[1].trim().lowercase() in setOf("read", "write")) {
                                permissions[bits[0].trim()] = bits[1].trim().lowercase()
                            }
                        }
                        if (permissions.isEmpty()) {
                            toast("أدخل صلاحية واحدة على الأقل بصيغة users:read")
                            return@setOnClickListener
                        }
                        confirm("سيتم استبدال صلاحيات هذا المدير بالقائمة الجديدة. متابعة؟") {
                            val payload = mutableMapOf<String, Any>("displayName" to name, "permissions" to permissions)
                            if (password.isNotBlank()) {
                                if (password.length < 8) {
                                    toast("كلمة المرور الجديدة يجب أن تكون 8 أحرف على الأقل")
                                    return@confirm
                                }
                                payload["password"] = password
                            }
                            load(api.updateOperator(id, payload)) {
                                dialog.dismiss()
                                toast("تم تحديث صلاحيات المدير")
                                openModule("operators")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showUserAccessDialog(id: String, currentRole: String?, currentStatus: String?) {
        val form = formFields(listOf("الدور: super أو manager أو none", "الحالة: active أو suspended أو banned"))
        form.second[0].setText(currentRole ?: "none")
        form.second[1].setText(currentStatus ?: "active")
        AlertDialog.Builder(this).setTitle("صلاحيات المستخدم").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val role = form.second[0].text.toString().trim().lowercase()
                        val status = form.second[1].text.toString().trim().lowercase()
                        if (role !in setOf("super", "manager", "none") ||
                            status !in setOf("active", "suspended", "banned")) {
                            toast("القيم المسموحة: super/manager/none و active/suspended/banned")
                            return@setOnClickListener
                        }
                        confirm("تغيير الدور أو الحالة قد يغيّر وصول المستخدم إلى أجزاء من النظام. تأكيد؟") {
                            load(api.updateUser(id, mapOf("staffRole" to role, "status" to status))) {
                                dialog.dismiss()
                                toast("تم تحديث صلاحيات المستخدم")
                                openModule("users")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showGrantCosmeticDialog(code: String) {
        val form = formFields(listOf("معرّف المستخدم", "المدة: 7 أو 15 أو 30 أو permanent"))
        AlertDialog.Builder(this).setTitle("إهداء إطار أو دخولية").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("مراجعة ثم إهداء", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userId = form.second[0].text.toString().trim()
                        val term = form.second[1].text.toString().trim().lowercase()
                        val permanent = term == "permanent" || term == "دائم"
                        val days = term.toIntOrNull()
                        if (userId.isBlank() || (!permanent && (days == null || days !in listOf(7, 15, 30)))) {
                            toast("أدخل معرّف المستخدم ومدة 7 أو 15 أو 30 أو permanent")
                            return@setOnClickListener
                        }
                        val durationLabel = if (permanent) "بشكل دائم" else "لمدة " + days + " يوم"
                        confirm("إهداء " + code + " إلى المستخدم المحدد " + durationLabel + "؟") {
                            load(api.grantCosmetic(mapOf(
                                "userId" to userId, "cosmeticCode" to code,
                                "days" to (days ?: 7), "permanent" to permanent
                            ))) {
                                dialog.dismiss()
                                toast("استجاب الخادم لطلب إهداء الميزة")
                                openModule("grantCosmetics")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showGrantVanityDialog(publicId: String) {
        val form = formFields(listOf("معرّف المستخدم", "المدة: 7 أو 15 أو 30 أو permanent"))
        AlertDialog.Builder(this).setTitle("إهداء الآي دي " + publicId).setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("مراجعة ثم إهداء", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userId = form.second[0].text.toString().trim()
                        val term = form.second[1].text.toString().trim().lowercase()
                        val permanent = term == "permanent" || term == "دائم"
                        val days = term.toIntOrNull()
                        if (userId.isBlank() || (!permanent && days !in listOf(7, 15, 30))) {
                            toast("أدخل معرّف المستخدم ومدة 7 أو 15 أو 30 أو permanent")
                            return@setOnClickListener
                        }
                        val durationLabel = if (permanent) "بشكل دائم" else "لمدة " + days + " يوم"
                        confirm("إهداء الآي دي " + publicId + " إلى المستخدم المحدد " + durationLabel + "؟") {
                            load(api.assignVanityId(mapOf(
                                "userId" to userId, "publicId" to publicId,
                                "days" to (days ?: 7), "permanent" to permanent
                            ))) {
                                dialog.dismiss()
                                toast("استجاب الخادم لطلب إهداء الآي دي")
                                openModule("vanityIds")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showCreateVanityDialog() {
        val form = formFields(listOf("الآي دي الرقمي (3 إلى 12 خانة)", "السعر بالعملات"))
        AlertDialog.Builder(this).setTitle("إضافة آي دي مميز").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("إضافة", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val publicId = form.second[0].text.toString().trim()
                        val price = form.second[1].text.toString().trim().toLongOrNull() ?: 0L
                        if (!publicId.matches(Regex("\\d{3,12}")) || price < 0) {
                            toast("الآي دي يجب أن يكون 3 إلى 12 رقماً والسعر غير سالب")
                            return@setOnClickListener
                        }
                        confirm("إضافة الآي دي " + publicId + " إلى الكتالوج؟") {
                            load(api.createVanityId(mapOf("publicId" to publicId, "priceCoins" to price))) {
                                dialog.dismiss()
                                toast("تمت معالجة طلب إضافة الآي دي")
                                openModule("vanityIds")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showCreateOperatorDialog() {
        val form = formFields(listOf("اسم المدير", "البريد الإلكتروني", "كلمة المرور (8 أحرف على الأقل)"))
        AlertDialog.Builder(this).setTitle("إضافة مدير محدود").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("إنشاء", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val displayName = form.second[0].text.toString().trim()
                        val email = form.second[1].text.toString().trim()
                        val password = form.second[2].text.toString()
                        if (email.isBlank() || password.length < 8) {
                            toast("أدخل البريد وكلمة مرور من 8 أحرف على الأقل")
                            return@setOnClickListener
                        }
                        confirm("سيُنشأ مدير محدود بصلاحية قراءة المستخدمين والرومات فقط. متابعة؟") {
                            load(api.createOperator(mapOf(
                                "displayName" to displayName, "email" to email, "password" to password,
                                "permissions" to mapOf("users" to "read", "rooms" to "read")
                            ))) {
                                dialog.dismiss()
                                toast("تم إرسال طلب إنشاء المدير للخادم")
                                openModule("operators")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showApproveRechargeAgentDialog(id: String) {
        val form = formFields(listOf("رصيد الشحن الأولي بالعملات", "ملاحظة (اختياري)"))
        AlertDialog.Builder(this).setTitle("قبول وكيل الشحن").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("مراجعة ثم قبول", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val coins = form.second[0].text.toString().trim().toLongOrNull()
                        if (coins == null || coins < 0) {
                            toast("أدخل رصيداً صحيحاً (صفر أو أكثر)")
                            return@setOnClickListener
                        }
                        val note = form.second[1].text.toString().trim()
                        confirm("تأكيد قبول الوكيل برصيد أولي $coins عملة؟") {
                            load(api.approveRechargeAgentApplication(id, mapOf("floatCoins" to coins, "note" to note))) {
                                dialog.dismiss()
                                toast("تمت معالجة طلب وكيل الشحن")
                                openModule("rechargeApplications")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showCreateVipPlanDialog() {
        val form = formFields(listOf("اسم الخطة", "مستوى VIP (1–10)", "السعر الشهري بالعملات"))
        AlertDialog.Builder(this).setTitle("إنشاء خطة VIP").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("إنشاء", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = form.second[0].text.toString().trim()
                        val level = form.second[1].text.toString().trim().toIntOrNull()
                        val price = form.second[2].text.toString().trim().toIntOrNull()
                        if (name.isBlank() || level == null || level !in 1..10 || price == null || price < 0) {
                            toast("أدخل اسماً ومستوى من 1 إلى 10 وسعراً غير سالب")
                            return@setOnClickListener
                        }
                        confirm("إنشاء خطة VIP بالمستوى " + level + " والسعر " + price + "؟") {
                            load(api.createVipPlan(mapOf("name" to name, "level" to level, "price" to price, "isActive" to true))) {
                                dialog.dismiss()
                                toast("تم إنشاء خطة VIP")
                                load(api.vipPlans()) { renderItems(it, "vipPlans") }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showEditVipPlanDialog(id: String, currentName: String, currentLevel: String, currentPrice: String) {
        val form = formFields(listOf("اسم الخطة", "مستوى VIP (1–10)", "السعر الشهري بالعملات"))
        form.second[0].setText(currentName)
        form.second[1].setText(currentLevel)
        form.second[2].setText(currentPrice)
        AlertDialog.Builder(this).setTitle("تعديل خطة VIP").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = form.second[0].text.toString().trim()
                        val level = form.second[1].text.toString().trim().toIntOrNull()
                        val price = form.second[2].text.toString().trim().toIntOrNull()
                        if (name.isBlank() || level == null || level !in 1..10 || price == null || price < 0) {
                            toast("أدخل اسماً ومستوى من 1 إلى 10 وسعراً غير سالب")
                            return@setOnClickListener
                        }
                        confirm("حفظ تعديلات خطة VIP؟") {
                            load(api.updateVipPlan(id, mapOf("name" to name, "level" to level, "price" to price))) {
                                dialog.dismiss()
                                toast("تم تحديث خطة VIP")
                                load(api.vipPlans()) { renderItems(it, "vipPlans") }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showAssignVipDialog() {
        val form = formFields(listOf("معرّف المستخدم", "معرّف خطة VIP", "المدة: 7 أو 15 أو 30 أو permanent"))
        AlertDialog.Builder(this)
            .setTitle("منح عضوية VIP")
            .setView(form.first)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("مراجعة ثم منح", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userId = form.second[0].text.toString().trim()
                        val planId = form.second[1].text.toString().trim()
                        val term = form.second[2].text.toString().trim().lowercase()
                        val permanent = term == "permanent" || term == "دائم"
                        val days = term.toIntOrNull()
                        if (userId.isBlank() || planId.isBlank() ||
                            (!permanent && (days == null || days !in listOf(7, 15, 30)))) {
                            toast("أدخل معرّف المستخدم والخطة ومدة 7 أو 15 أو 30 أو permanent")
                            return@setOnClickListener
                        }
                        val durationLabel = if (permanent) "بشكل دائم" else "لمدة " + days + " يوم"
                        confirm("تأكيد منح VIP للمستخدم المحدد " + durationLabel + "؟") {
                            load(api.assignVip(mapOf("userId" to userId, "planId" to planId, "days" to (days ?: 30), "permanent" to permanent))) {
                                dialog.dismiss()
                                toast("استجاب الخادم لطلب منح VIP")
                                openModule("vip")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showDirectMessageDialog() {
        val form = formFields(listOf("معرّف المستخدم (ID العام أو UUID)", "نص الرسالة"))
        AlertDialog.Builder(this)
            .setTitle("رسالة دردشة مباشرة")
            .setView(form.first)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إرسال", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userLookup = form.second[0].text.toString().trim()
                        val message = form.second[1].text.toString().trim()
                        if (userLookup.isBlank() || message.isBlank()) {
                            toast("معرّف المستخدم ونص الرسالة مطلوبان")
                            return@setOnClickListener
                        }
                        if (message.length > 4000) {
                            toast("الرسالة أطول من الحد المسموح (4000 حرف)")
                            return@setOnClickListener
                        }
                        confirm("سيُبحث عن المستخدم بالـ ID ثم تُرسل رسالة دردشة من حساب السوبر أدمن. تأكيد؟") {
                            progress.visibility = View.VISIBLE
                            api.users(1, 40, userLookup).enqueue(object : Callback<ApiResponse<Any>> {
                                override fun onResponse(
                                    call: Call<ApiResponse<Any>>,
                                    response: Response<ApiResponse<Any>>
                                ) {
                                    if (!response.isSuccessful || response.body()?.success != true) {
                                        progress.visibility = View.GONE
                                        showError(response.body()?.message ?: "تعذر البحث عن المستخدم (HTTP ${response.code()})")
                                        return
                                    }
                                    val page = response.body()?.data as? Map<*, *>
                                    val users = page?.get("items") as? List<*> ?: emptyList<Any>()
                                    val matched = users.firstOrNull { item ->
                                        val row = item as? Map<*, *>
                                        row != null && (row["id"]?.toString() == userLookup ||
                                            row["publicId"]?.toString() == userLookup)
                                    } as? Map<*, *>
                                    val peerUuid = matched?.get("id")?.toString()
                                    if (peerUuid.isNullOrBlank()) {
                                        progress.visibility = View.GONE
                                        showError("لم يُعثر على مستخدم مطابق تمامًا لهذا الـ ID")
                                        return
                                    }
                                    api.startDirectConversation(mapOf("peerId" to peerUuid))
                                        .enqueue(object : Callback<ApiResponse<Any>> {
                                            override fun onResponse(
                                                call: Call<ApiResponse<Any>>,
                                                response: Response<ApiResponse<Any>>
                                            ) {
                                                if (!response.isSuccessful || response.body()?.success != true) {
                                                    progress.visibility = View.GONE
                                                    showError(response.body()?.message ?: "تعذر فتح المحادثة (HTTP ${response.code()})")
                                                    return
                                                }
                                                val data = response.body()?.data
                                                val conversationId = (data as? Map<*, *>)?.get("id")?.toString()
                                                if (conversationId.isNullOrBlank()) {
                                                    progress.visibility = View.GONE
                                                    showError("استجابة المحادثة لا تحتوي على معرّف صالح")
                                                    return
                                                }
                                                api.sendDirectMessage(
                                                    conversationId,
                                                    mapOf("type" to "text", "content" to message)
                                                ).enqueue(object : Callback<ApiResponse<Any>> {
                                                    override fun onResponse(
                                                        call: Call<ApiResponse<Any>>,
                                                        response: Response<ApiResponse<Any>>
                                                    ) {
                                                        progress.visibility = View.GONE
                                                        if (response.isSuccessful && response.body()?.success == true) {
                                                            dialog.dismiss()
                                                            toast("تم إرسال رسالة الدردشة")
                                                        } else {
                                                            showError(response.body()?.message ?: "فشل إرسال الرسالة (HTTP ${response.code()})")
                                                        }
                                                    }
                                                    override fun onFailure(call: Call<ApiResponse<Any>>, error: Throwable) {
                                                        progress.visibility = View.GONE
                                                        showError(error.message ?: "تعذر الاتصال بالخادم")
                                                    }
                                                })
                                            }
                                            override fun onFailure(call: Call<ApiResponse<Any>>, error: Throwable) {
                                                progress.visibility = View.GONE
                                                showError(error.message ?: "تعذر الاتصال بالخادم")
                                            }
                                        })
                                }
                                override fun onFailure(call: Call<ApiResponse<Any>>, error: Throwable) {
                                    progress.visibility = View.GONE
                                    showError(error.message ?: "تعذر الاتصال بالخادم")
                                }
                            })
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showNotificationDialog() {
        val form = formFields(listOf(
            "عنوان الرسالة",
            "محتوى الرسالة",
            "معرّف المستخدم (فارغ = إرسال جماعي)",
            "رابط صورة HTTPS (اختياري)",
            "رابط أو مسار داخل التطبيق HTTPS (اختياري)"
        ))
        AlertDialog.Builder(this)
            .setTitle("إرسال رسالة الإدارة")
            .setView(form.first)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إرسال", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = form.second[0].text.toString().trim()
                        val message = form.second[1].text.toString().trim()
                        val userId = form.second[2].text.toString().trim()
                        val imageUrl = form.second[3].text.toString().trim()
                        val link = form.second[4].text.toString().trim()
                        if (title.isBlank() || message.isBlank()) {
                            toast("عنوان الرسالة ونصها مطلوبان")
                            return@setOnClickListener
                        }
                        confirm(if (userId.isBlank()) "ستصل رسالة الإدارة إلى جميع المستخدمين في قسم رسالة الإدارة داخل الدردشة، مع إشعار. تأكيد الإرسال؟" else "ستصل الرسالة إلى هذا المستخدم داخل قسم رسالة الإدارة في الدردشة، مع إشعار. تأكيد الإرسال؟") {
                            val payload = mutableMapOf("title" to title, "body" to message, "channel" to "both")
                            if (imageUrl.isNotBlank()) payload["imageUrl"] = imageUrl
                            if (link.isNotBlank()) payload["link"] = link
                            if (userId.isNotBlank()) {
                                payload["userId"] = userId
                                payload["audience"] = "user"
                            } else payload["audience"] = "all"
                            load(api.sendAdminMessage(payload)) {
                                dialog.dismiss()
                                toast("تم قبول طلب الإرسال من الخادم؛ افحص وصول رسالة الإدارة للمستلم")
                                openModule("notifications")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun formFields(labels: List<String>): Pair<LinearLayout, List<EditText>> {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val inputs = labels.map { label ->
            val input = EditText(this).apply {
                hint = label
                textSize = 14f
                setSingleLine(label != "نص الإشعار" && label != "محتوى الرسالة")
                setTextColor(TEXT)
                setHintTextColor(MUTED)
                background = roundRect(0xFFF5F4FA.toInt(), 10)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            layout.addView(input, LinearLayout.LayoutParams(-1, if (label == "نص الإشعار" || label == "محتوى الرسالة") dp(100) else dp(52)).apply {
                bottomMargin = dp(8)
            })
            input
        }
        return layout to inputs
    }

    private fun extractItems(data: Any?): List<Any> {
        if (data is List<*>) return data.filterNotNull()
        if (data is Map<*, *>) {
            val items = data["items"]
            if (items is List<*>) return items.filterNotNull()
            val nested = data["data"]
            if (nested is List<*>) return nested.filterNotNull()
        }
        return emptyList()
    }

    private fun mapOfEntries(data: Any?): Map<String, Any?> {
        if (data !is Map<*, *>) return emptyMap()
        return data.entries.associate { it.key.toString() to it.value }
    }

    private fun value(item: Any, vararg keys: String): String? {
        if (item !is Map<*, *>) return item.toString()
        for (key in keys) {
            val v = item[key] ?: continue
            if (v is Map<*, *>) continue
            return v.toString().takeIf { it.isNotBlank() && it != "null" }
        }
        return null
    }

    private fun prettyKey(key: String): String = when (key) {
        "users", "usersCount", "totalUsers" -> "المستخدمون"
        "rooms", "roomsCount", "totalRooms" -> "الرومات"
        "activeStreams", "streamsCount" -> "البث المباشر"
        "agencies", "agenciesCount" -> "الوكالات"
        "createdAt" -> "تاريخ الإنشاء"
        "membersCount" -> "عدد الأعضاء"
        "coinPrice" -> "السعر"
        "diamondValue" -> "قيمة الألماس"
        else -> key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun confirm(message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNegativeButton("رجوع", null)
            .setPositiveButton("تأكيد") { _, _ -> action() }
            .show()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = roundRect(Color.WHITE, 18)
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    }

    private fun addText(parent: LinearLayout, text: String, size: Float, bold: Boolean, color: Int) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(3))
        }
        parent.addView(view)
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = roundRect(ACCENT, 12)
        setPadding(dp(10), dp(2), dp(10), dp(2))
        setOnClickListener { action() }
    }

    private fun roundRect(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun showError(message: String) {
        Toast.makeText(this, message.take(180), Toast.LENGTH_LONG).show()
        if (body.childCount == 0) {
            val c = card()
            addText(c, message, 14f, false, MUTED)
            body.addView(c)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val BG = 0xFFF5F6FB.toInt()
        private const val TEXT = 0xFF252744.toInt()
        private const val MUTED = 0xFF73758D.toInt()
        private const val ACCENT = 0xFF6B4CC2.toInt()
    }
}
