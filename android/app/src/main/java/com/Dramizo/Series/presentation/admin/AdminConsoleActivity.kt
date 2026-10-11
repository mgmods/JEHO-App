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
        Module("gifts", "الهدايا والمميزات", "عرض كتالوج الهدايا الحالي", "🎁"),
        Module("vip", "العضويات VIP", "مراجعة العضويات ومنح VIP", "👑"),
        Module("notifications", "الإشعارات", "مراجعة الإشعارات وإرسال إشعار", "🔔"),
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
        modules.forEach { module ->
            val card = card()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val icon = TextView(this).apply {
                text = module.icon
                textSize = 25f
                gravity = Gravity.CENTER
                setBackground(roundRect(0xFFF0ECFB.toInt(), 16))
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            }
            addText(texts, module.title, 15f, true, TEXT)
            addText(texts, module.subtitle, 12f, false, MUTED)
            row.addView(icon)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val open = button("فتح") { openModule(module.key) }
            row.addView(open)
            card.addView(row)
            body.addView(card)
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
                    singleLine = true
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
            "gifts" -> load(api.gifts()) { renderItems(it, "gifts") }
            "vip" -> {
                body.addView(button("＋ منح عضوية VIP") { showAssignVipDialog() })
                load(api.vipMembers(1, 40)) { renderItems(it, "vip") }
                val plansButton = button("عرض خطط VIP") {
                    load(api.vipPlans()) { renderItems(it, "vipPlans") }
                }
                body.addView(plansButton)
            }
            "notifications" -> {
                body.addView(button("＋ إرسال إشعار") { showNotificationDialog() })
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
            addText(card, primary, 15f, true, TEXT)
            if (!id.isNullOrBlank()) addText(card, "المعرّف: $id", 11f, false, MUTED)
            if (!status.isNullOrBlank()) addText(card, "الحالة: $status", 12f, false, MUTED)
            val detail = listOf("createdAt", "role", "level", "membersCount", "ownerName", "coinPrice", "diamondValue")
                .mapNotNull { k -> value(item, k)?.let { "${prettyKey(k)}: $it" } }
            if (detail.isNotEmpty()) addText(card, detail.take(3).joinToString("   •   "), 12f, false, MUTED)

            when (type) {
                "users" -> if (!id.isNullOrBlank()) {
                    val banned = status?.contains("banned", true) == true
                    card.addView(button(if (banned) "إلغاء الحظر" else "حظر المستخدم") {
                        confirm(if (banned) "إلغاء حظر المستخدم؟" else "تأكيد حظر المستخدم؟") {
                            load(if (banned) api.unbanUser(id) else api.banUser(id)) {
                                toast(if (banned) "تم إرسال طلب إلغاء الحظر للخادم" else "تم إرسال طلب الحظر للخادم")
                                openModule("users")
                            }
                        }
                    })
                }
                "rooms" -> if (!id.isNullOrBlank()) card.addView(button("إغلاق الروم") {
                    confirm("سيتم إغلاق الروم على الخادم. هل تريد المتابعة؟") {
                        load(api.closeRoom(id)) { toast("استجاب الخادم لطلب إغلاق الروم"); openModule("rooms") }
                    }
                })
                "streams" -> if (!id.isNullOrBlank()) card.addView(button("إنهاء البث") {
                    confirm("سيتم إنهاء البث على الخادم. هل تريد المتابعة؟") {
                        load(api.endStream(id)) { toast("استجاب الخادم لطلب إنهاء البث"); openModule("streams") }
                    }
                })
                "agencies" -> if (!id.isNullOrBlank()) card.addView(button("تعليق الوكالة") {
                    confirm("تعليق الوكالة قد يؤثر على أعضائها. هل تريد المتابعة؟") {
                        load(api.suspendAgency(id, mapOf("reason" to "تم التعليق من تطبيق JEHO"))) {
                            toast("استجاب الخادم لطلب تعليق الوكالة"); openModule("agencies")
                        }
                    }
                })
            }
            body.addView(card)
        }
    }

    private fun showAssignVipDialog() {
        val form = formFields(listOf("معرّف المستخدم", "معرّف خطة VIP"))
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
                        if (userId.isBlank() || planId.isBlank()) {
                            toast("أدخل معرّف المستخدم ومعرّف الخطة")
                            return@setOnClickListener
                        }
                        confirm("تأكيد منح VIP للمستخدم المحدد؟") {
                            load(api.assignVip(mapOf("userId" to userId, "planId" to planId))) {
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

    private fun showNotificationDialog() {
        val form = formFields(listOf("عنوان الإشعار", "نص الإشعار", "معرّف مستخدم (اختياري)"))
        AlertDialog.Builder(this)
            .setTitle("إرسال إشعار")
            .setView(form.first)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إرسال", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = form.second[0].text.toString().trim()
                        val message = form.second[1].text.toString().trim()
                        val userId = form.second[2].text.toString().trim()
                        if (title.isBlank() || message.isBlank()) {
                            toast("العنوان ونص الإشعار مطلوبان")
                            return@setOnClickListener
                        }
                        confirm(if (userId.isBlank()) "سيُرسل الإشعار إلى الجمهور الافتراضي. تأكيد؟" else "إرسال الإشعار لهذا المستخدم؟") {
                            val payload = mutableMapOf("title" to title, "body" to message, "channel" to "both")
                            if (userId.isNotBlank()) {
                                payload["userId"] = userId
                                payload["audience"] = "user"
                            } else payload["audience"] = "all"
                            load(api.sendNotification(payload)) {
                                dialog.dismiss()
                                toast("استجاب الخادم لطلب الإرسال")
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
                setSingleLine(label != "نص الإشعار")
                setTextColor(TEXT)
                setHintTextColor(MUTED)
                background = roundRect(0xFFF5F4FA.toInt(), 10)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            layout.addView(input, LinearLayout.LayoutParams(-1, if (label == "نص الإشعار") dp(90) else dp(52)).apply {
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
