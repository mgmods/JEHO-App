package com.Dramizo.Series.presentation.admin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.Dramizo.Series.data.remote.api.AdminApi
import com.Dramizo.Series.data.remote.api.RoomApi
import com.Dramizo.Series.data.remote.dto.ApiResponse
import com.Dramizo.Series.presentation.common.ContainerProvider
import com.Dramizo.Series.presentation.common.ThemedActivity
import com.Dramizo.Series.util.StaffRoleHelper
import com.Dramizo.Series.util.StaffIdentityConfigCache
import com.Dramizo.Series.util.AssetCatalog
import com.bumptech.glide.Glide
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
    private lateinit var roomApi: RoomApi
    private lateinit var body: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var titleView: TextView
    private lateinit var backButton: TextView
    private var currentSection = "home"
    private var currentRole = StaffRoleHelper.NONE
    @Volatile private var roleRefreshInFlight = false
    private var notificationImageUrl: String? = null
    private var notificationImageStatus: TextView? = null
    private var notificationImageUploadInProgress = false
    private val staffIdentityFields = mutableMapOf<String, EditText>()
    private var pendingIdentityFieldKey: String? = null
    private var staffIdentityUploadInProgress = false
    private val notificationImageRequestCode = 7042

    private data class Module(val key: String, val title: String, val subtitle: String, val icon: String)

    // Keep the native Super Admin focused on the operations requested for JEHO.
    // Other backend/dashboard tools remain untouched and are not deleted.
    private val modules = listOf(
        Module("users", "المستخدمون", "بحث بالـ ID وتقييد الحساب أو حظره", "👥"),
        Module("genderVerifications", "طلبات التحقق", "مراجعة طلبات التحقق المعلّقة", "🪪"),
        Module("rooms", "إدارة الرومات", "تعديل الرومات وإغلاقها", "🏠"),
        Module("streams", "البث المباشر", "متابعة البث وإنهاؤه عند الحاجة", "📡"),
        Module("reports", "البلاغات والمراجعة", "مراجعة بلاغات المستخدمين وتصعيد الحالات", "🛡️"),
        Module("agencies", "الوكالات", "إدارة الوكالات وتعليقها أو حذفها", "🏢"),
        Module("agencyApplications", "طلبات الوكالات", "قبول الطلبات لتفعيل وكالة جديدة", "📨"),
        Module("rechargeApplications", "طلبات وكلاء الشحن", "مراجعة الطلبات وقبولها أو رفضها", "💳"),
        Module("rechargeAgents", "وكلاء الشحن", "إضافة الوكلاء وإدارة حالتهم", "💰"),
        Module("recharges", "عمليات الشحن", "مراجعة عمليات الشحن المسجلة", "💳"),
        Module("withdrawals", "طلبات السحب", "مراجعة طلبات السحب وحالاتها", "🏦"),
        Module("walletTransactions", "حركة المحفظة", "متابعة سجل معاملات العملات", "🧾"),
        Module("walletAdjust", "تعديل رصيد المحفظة", "تعديل رصيد بمبرر وتأكيد", "🪙"),
        Module("notifications", "الرسائل والإشعارات", "رسائل عامة أو خاصة بعنوان ومحتوى", "🔔"),
        Module("toastManager", "التوستات والمؤثرات", "خلفيات التوستات واللمعة والمدة وزر المحظوظ", "✨"),
        Module("mysteriousMan", "الرجل الغامض", "تفعيل الهوية الموحدة ومستويات VIP", "🕶️"),
        Module("luckyCannon", "مدفع المحظوظ", "التفعيل ومدة الظهور والصوت", "💎"),
        Module("supportChest", "صندوق الدعم", "الهدف والمكافأة وظهور الأيقونة", "🎁"),
        Module("roomPkSettings", "تحديات PK بين الرومات", "تشغيل الميزة أو إيقافها للجميع", "⚔️"),
        Module("vanityIds", "الآي دي المميز", "إدارة الآيديات وإهداؤها بمدة محددة", "🔢"),
        Module("cosmetics", "كتالوج المميزات", "مراجعة المميزات المتاحة في المتجر", "🖼️"),
        Module("gifts", "كتالوج الهدايا", "مراجعة الهدايا المتاحة في النظام", "🎁"),
        Module("grantCosmetics", "الإطارات والدخوليات", "إهداء المميزات لمدة 7 أو 15 أو 30 يومًا أو دائمًا", "✨"),
        Module("vip", "عضويات VIP", "منح VIP وإدارة العضويات والخطط", "👑"),
        Module("logs", "سجل العمليات", "مراجعة سجل التغييرات الإدارية", "🧾"),
        Module("staffIdentity", "هوية الرتب الإدارية", "تخصيص الشارة والإطار وبطاقة الاسم والدخولية لكل رتبة", "💎"),
        Module("operators", "إدارة صلاحيات الإدارة", "إدارة حسابات الإدارة المحدودة — للسوبر أدمن فقط", "🛡️")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // System/device Back should navigate out of the current admin section first.
        // Only leave the admin console when already on its home screen.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleAdminBack()
            }
        })
        val user = try { ContainerProvider.from(this).sessionManager.user } catch (_: Exception) { null }
        if (!StaffRoleHelper.isStaff(user)) {
            Toast.makeText(this, "هذه الصفحة مخصصة لفريق إدارة JEHO", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentRole = StaffRoleHelper.normalize(user)
        api = ContainerProvider.from(this).adminApi
        roomApi = ContainerProvider.from(this).roomApi
        buildShell()
        val requestedSection = intent.getStringExtra("admin_initial_section")
        if (requestedSection.isNullOrBlank()) {
            showHome()
        } else if (requestedSection == "home") {
            showHome()
        } else if (modules.any { it.key == requestedSection }) {
            // Open a real connected section requested from the light native admin hub.
            // The normal role gate above still runs before any API-backed section opens.
            openModule(requestedSection)
        } else {
            showHome()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read the signed-in account from the server on every return to this screen.
        // Refresh returns the database-backed user role; cached staffRole is never authoritative.
        refreshStaffRoleFromServer()
    }

    private fun refreshStaffRoleFromServer() {
        if (roleRefreshInFlight || !::api.isInitialized) return
        val session = try { ContainerProvider.from(this).sessionManager } catch (_: Exception) { return }
        val refreshToken = session.refreshToken
        if (refreshToken.isNullOrBlank()) {
            revokeLocalStaffAccess()
            return
        }
        roleRefreshInFlight = true
        try {
            ContainerProvider.from(this).authApi.refresh(
                com.Dramizo.Series.data.remote.dto.AuthDtos.RefreshTokenRequest(refreshToken)
            ).enqueue(object : Callback<com.Dramizo.Series.data.remote.dto.ApiResponse<com.Dramizo.Series.data.remote.dto.AuthDtos.AuthResult>> {
                override fun onResponse(
                    call: Call<com.Dramizo.Series.data.remote.dto.ApiResponse<com.Dramizo.Series.data.remote.dto.AuthDtos.AuthResult>>,
                    response: Response<com.Dramizo.Series.data.remote.dto.ApiResponse<com.Dramizo.Series.data.remote.dto.AuthDtos.AuthResult>>
                ) {
                    roleRefreshInFlight = false
                    if (isFinishing || isDestroyed) return
                    val result = response.body()?.data
                    val freshUser = if (response.isSuccessful && response.body()?.success == true) result?.user else null
                    if (freshUser != null) {
                        val oldRole = currentRole
                        session.saveSession(result)
                        val freshRole = StaffRoleHelper.normalize(freshUser)
                        if (!StaffRoleHelper.isStaff(freshUser)) {
                            revokeLocalStaffAccess()
                            return
                        }
                        if (freshRole != oldRole) {
                            currentRole = freshRole
                            StaffIdentityConfigCache.refresh(this@AdminConsoleActivity, null)
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) showHome()
                            }
                        }
                    } else if (response.code() == 401 || response.code() == 403) {
                        revokeLocalStaffAccess()
                    }
                }
                override fun onFailure(
                    call: Call<com.Dramizo.Series.data.remote.dto.ApiResponse<com.Dramizo.Series.data.remote.dto.AuthDtos.AuthResult>>,
                    error: Throwable
                ) {
                    roleRefreshInFlight = false
                    // A transient network failure must not log out a legitimate staff member.
                }
            })
        } catch (_: Exception) {
            roleRefreshInFlight = false
        }
    }

    private fun revokeLocalStaffAccess() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            currentRole = StaffRoleHelper.NONE
            StaffIdentityConfigCache.refresh(this, null)
            Toast.makeText(this, "تم سحب صلاحية الإدارة من هذا الحساب", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(BG)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
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
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            visibility = View.GONE
            setOnClickListener { handleAdminBack() }
        }
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
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
        val scroll = ScrollView(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            clipToPadding = false
            setPadding(dp(14), dp(12), dp(14), dp(24))
        }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    /**
     * Native Android control-center home. No WebView/HTML.
     * Uses a clear hierarchy, live server metrics, searchable two-column action cards,
     * role-specific visibility and compact mobile-first surfaces.
     */
    private fun showHome() {
        currentSection = "home"
        titleView.text = "لوحة JEHO"
        backButton.visibility = View.GONE
        body.removeAllViews()
        body.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val roleLabel = when (currentRole) {
            StaffRoleHelper.SUPER -> "السوبر أدمن"
            StaffRoleHelper.ADMIN -> "الأدمن"
            else -> "المانجر"
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(0xFF20184B.toInt(), 0xFF49329A.toInt(), 0xFF7656D8.toInt()))
                .apply { cornerRadius = dp(26).toFloat() }
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) }
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "J"; textSize = 23f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFB7A6FF.toInt(), 0xFF8165E8.toInt()))
                .apply { cornerRadius = dp(16).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginStart = dp(12) }
        }
        brandRow.addView(mark)
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        addText(brandText, "JEHO  /  CONTROL CENTER", 10f, true, 0xFFDCD4FF.toInt())
        addText(brandText, "مساحة الإدارة", 19f, true, Color.WHITE)
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        val rolePill = TextView(this).apply {
            text = roleLabel; textSize = 11f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = GradientDrawable().apply {
                setColor(0x28FFFFFF); cornerRadius = dp(30).toFloat()
                setStroke(dp(1), 0x55FFFFFF)
            }
        }
        brandRow.addView(rolePill)
        hero.addView(brandRow)
        addText(hero, "كل شيء تحت السيطرة", 23f, true, Color.WHITE)
        val intro = TextView(this).apply {
            text = "إدارة المجتمع والرومات والمحفظة والمميزات من داخل تطبيق JEHO."
            textSize = 13f; setTextColor(0xFFEAE5FF.toInt())
            setLineSpacing(dp(3).toFloat(), 1.05f); setPadding(0, dp(4), 0, dp(15))
        }
        hero.addView(intro)
        val secure = TextView(this).apply {
            text = "●  اتصال مباشر بالخادم     •     صلاحيات حسب الرتبة"
            textSize = 11f; setTextColor(0xFFC8F7DE.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0x18FFFFFF); cornerRadius = dp(12).toFloat()
            }
        }
        hero.addView(secure)
        body.addView(hero)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val headingText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        addText(headingText, "نظرة عامة", 18f, true, TEXT)
        addText(headingText, "مؤشرات النظام المتاحة", 12f, false, MUTED)
        heading.addView(headingText, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = TextView(this).apply {
            text = "↻ تحديث"; textSize = 12f; gravity = Gravity.CENTER
            setTextColor(ACCENT); setPadding(dp(12), dp(9), dp(12), dp(9))
            background = roundRect(0xFFEDE8FF.toInt(), 20)
            setOnClickListener { showHome() }
        }
        heading.addView(refresh)
        body.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val metrics = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        body.addView(metrics, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        load(api.overview()) { data ->
            metrics.removeAllViews()
            val stats = mapOfEntries(data)
            val specs = listOf(
                Triple("المستخدمون", listOf("users", "usersCount", "totalUsers"), "♙"),
                Triple("الرومات", listOf("rooms", "roomsCount", "totalRooms"), "⌂"),
                Triple("البث المباشر", listOf("activeStreams", "streamsCount", "liveStreams"), "◉"),
                Triple("الوكالات", listOf("agencies", "agenciesCount", "totalAgencies"), "◇")
            )
            val known = specs.mapNotNull { spec ->
                val key = spec.second.firstOrNull { stats.containsKey(it) }
                if (key == null) null else Triple(spec.first, stats[key]?.toString() ?: "—", spec.third)
            }
            val shown = if (known.isNotEmpty()) known else stats.entries.take(4).map {
                Triple(prettyKey(it.key), it.value?.toString() ?: "—", "✦")
            }
            if (shown.isEmpty()) {
                val empty = card()
                addText(empty, "الإحصائيات غير متاحة حاليًا", 13f, true, MUTED)
                addText(empty, "يمكنك متابعة أقسام الإدارة أو المحاولة لاحقًا.", 12f, false, MUTED)
                metrics.addView(empty)
            } else shown.chunked(2).forEach { pair ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                }
                pair.forEach { item ->
                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                        setPadding(dp(14), dp(13), dp(14), dp(13))
                        background = GradientDrawable().apply {
                            setColor(Color.WHITE); cornerRadius = dp(19).toFloat()
                            setStroke(dp(1), 0xFFE9E6F4.toInt())
                        }
                        elevation = dp(1).toFloat()
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                            marginStart = dp(4); marginEnd = dp(4); bottomMargin = dp(8)
                        }
                    }
                    val icon = TextView(this).apply {
                        text = item.third; textSize = 21f; gravity = Gravity.CENTER
                        setTextColor(ACCENT); background = roundRect(0xFFF0ECFF.toInt(), 13)
                        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { bottomMargin = dp(9) }
                    }
                    cell.addView(icon)
                    addText(cell, item.first, 12f, true, MUTED)
                    addText(cell, item.second, 25f, true, TEXT)
                    row.addView(cell)
                }
                if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                metrics.addView(row)
            }
        }

        val visibleModules = modules.filter { roleCanAccessModule(it.key) }

        val search = EditText(this).apply {
            hint = "⌕   ابحث عن مستخدمين، رومات، هدايا..."
            textSize = 14f; setSingleLine(true)
            layoutDirection = View.LAYOUT_DIRECTION_RTL; textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            setTextColor(TEXT); setHintTextColor(MUTED)
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = dp(17).toFloat()
                setStroke(dp(1), 0xFFE3E0F0.toInt())
            }
            setPadding(dp(16), dp(12), dp(16), dp(12)); elevation = dp(1).toFloat()
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(2); bottomMargin = dp(12)
        })

        val groups = listOf(
            "إدارة المجتمع" to listOf("users", "genderVerifications", "rooms", "streams", "reports"),
            "الوكالات والشحن" to listOf("agencies", "agencyApplications", "rechargeApplications", "rechargeAgents", "recharges", "withdrawals"),
            "المالية والمحفظة" to listOf("walletTransactions", "walletAdjust"),
            "المتجر والهدايا" to listOf("cosmetics", "gifts"),
            "الرسائل وتجربة المستخدم" to listOf("notifications", "toastManager", "mysteriousMan", "luckyCannon", "supportChest", "roomPkSettings"),
            "العضويات والمظهر" to listOf("vanityIds", "grantCosmetics", "vip", "staffIdentity"),
            "الإدارة والأمان" to listOf("logs", "operators")
        )
        val tileRecords = mutableListOf<Pair<View, Module>>()
        val groupRecords = mutableListOf<Pair<View, List<Module>>>()
        groups.forEach { group ->
            val groupModules = visibleModules.filter { it.key in group.second }
            if (groupModules.isEmpty()) return@forEach
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
            }
            val groupHeading = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(2), dp(10), dp(2), dp(10))
            }
            groupHeading.addView(View(this).apply {
                background = roundRect(ACCENT, 4)
                layoutParams = LinearLayout.LayoutParams(dp(4), dp(23)).apply { marginStart = dp(9) }
            })
            addText(groupHeading, group.first, 15f, true, TEXT)
            groupHeading.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            groupHeading.addView(TextView(this).apply {
                text = groupModules.size.toString() + " أقسام"; textSize = 10f
                setTextColor(MUTED); gravity = Gravity.CENTER
                setPadding(dp(8), dp(5), dp(8), dp(5)); background = roundRect(0xFFEDEBF5.toInt(), 20)
            })
            block.addView(groupHeading)
            val grid = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            }
            groupModules.chunked(2).forEach { pair ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                }
                pair.forEach { module ->
                    val tile = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                        gravity = Gravity.TOP; minimumHeight = dp(132)
                        setPadding(dp(13), dp(13), dp(13), dp(13))
                        background = GradientDrawable().apply {
                            setColor(Color.WHITE); cornerRadius = dp(19).toFloat()
                            setStroke(dp(1), 0xFFE7E4F1.toInt())
                        }
                        elevation = dp(1).toFloat(); isClickable = true; isFocusable = true
                        foreground = android.util.TypedValue().let {
                            val out = android.util.TypedValue()
                            theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                            if (out.resourceId != 0) androidx.core.content.ContextCompat.getDrawable(this@AdminConsoleActivity, out.resourceId) else null
                        }
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                            marginStart = dp(4); marginEnd = dp(4); bottomMargin = dp(8)
                        }
                        setOnClickListener { openModule(module.key) }
                    }
                    val tileTop = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    tileTop.addView(TextView(this).apply {
                        text = module.icon; textSize = 22f; gravity = Gravity.CENTER
                        setTextColor(ACCENT); background = roundRect(0xFFF0ECFF.toInt(), 13)
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(11) }
                    })
                    tileTop.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                    tileTop.addView(TextView(this).apply {
                        text = "↗"; textSize = 17f; setTextColor(0xFF9992B8.toInt()); gravity = Gravity.CENTER
                    })
                    tile.addView(tileTop)
                    addText(tile, module.title, 13f, true, TEXT)
                    tile.addView(TextView(this).apply {
                        text = module.subtitle; textSize = 10.5f; setTextColor(MUTED)
                        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                        setLineSpacing(dp(2).toFloat(), 1.0f); setPadding(0, dp(4), 0, 0)
                    })
                    row.addView(tile)
                    tileRecords.add(tile to module)
                }
                if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, dp(1), 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                })
                grid.addView(row)
            }
            block.addView(grid); body.addView(block); groupRecords.add(block to groupModules)
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
                tileRecords.forEach { pair ->
                    val m = pair.second
                    val match = q.isEmpty() || m.title.lowercase(java.util.Locale.ROOT).contains(q) ||
                        m.subtitle.lowercase(java.util.Locale.ROOT).contains(q) ||
                        m.key.lowercase(java.util.Locale.ROOT).contains(q)
                    pair.first.visibility = if (match) View.VISIBLE else View.GONE
                }
                groupRecords.forEach { pair ->
                    pair.first.visibility = if (q.isEmpty() || pair.second.any { m ->
                        m.title.lowercase(java.util.Locale.ROOT).contains(q) ||
                            m.subtitle.lowercase(java.util.Locale.ROOT).contains(q) ||
                            m.key.lowercase(java.util.Locale.ROOT).contains(q)
                    }) View.VISIBLE else View.GONE
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun roleCanAccessModule(key: String): Boolean {
        return when (currentRole) {
            StaffRoleHelper.SUPER -> true
            StaffRoleHelper.ADMIN -> key in setOf(
                "users", "rooms", "streams", "reports", "agencies", "agencyApplications",
                "cosmetics", "gifts", "grantCosmetics", "vip", "vanityIds"
            )
            StaffRoleHelper.MANAGER -> key in setOf(
                "rooms", "streams", "reports", "cosmetics", "gifts", "grantCosmetics"
            )
            else -> false
        }
    }

    private fun openModule(key: String) {
        if (!roleCanAccessModule(key)) {
            toast(when (currentRole) {
                StaffRoleHelper.MANAGER -> "صلاحية المانجر تقتصر على إشراف الرومات والبث والبلاغات وإهداء الإطارات والدخوليات"
                StaffRoleHelper.ADMIN -> "هذا القسم غير متاح لرتبة الأدمن"
                else -> "هذا القسم مخصص لفريق الإدارة"
            })
            return
        }
        currentSection = key
        val module = modules.firstOrNull { it.key == key }
        titleView.text = module?.title ?: "مركز إدارة JEHO"
        backButton.visibility = View.VISIBLE
        body.removeAllViews()
        if (key != "users") module?.let { addSectionIntro(it) }
        when (key) {
            "users" -> {
                val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                val search = EditText(this).apply {
                    hint = "ID المستخدم أو UUID أو الاسم أو البريد"
                    textSize = 15f
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    textDirection = View.TEXT_DIRECTION_RTL
                    gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                    setSingleLine(true)
                    imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                    setTextColor(TEXT)
                    setHintTextColor(MUTED)
                    background = roundRect(Color.WHITE, 12)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                            loadUsers(text.toString().trim())
                            true
                        } else false
                    }
                }
                searchRow.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
                searchRow.addView(button("بحث") {
                    val query = search.text.toString().trim()
                    if (query.isBlank()) toast("أدخل ID المستخدم أو الاسم للبحث")
                    else loadUsers(query)
                })
                body.addView(searchRow)
                module?.let { addSectionIntro(it) }
                loadUsers("")
            }
            "rooms" -> load(api.rooms(1, 40)) { renderItems(it, "rooms") }
            "streams" -> load(api.streams(1, 40)) { renderItems(it, "streams") }
            "agencies" -> {
                body.addView(button("＋ إضافة وكالة عبر مراجعة الطلب") { openModule("agencyApplications") })
                addText(body, "تُنشأ الوكالة عند قبول طلبها حتى يتم تجهيز المالك وعضوية الوكالة ورومها الدائم وكود التفعيل بشكل صحيح.", 12f, false, MUTED)
                body.addView(button("↻ تحديث قائمة الوكالات") { openModule("agencies") })
                load(api.agencies(1, 40)) { renderItems(it, "agencies") }
            }
            "agencyApplications" -> load(api.agencyApplications(1, 40)) { renderItems(it, "agencyApplications") }
            "rechargeApplications" -> load(api.rechargeAgentApplications()) { renderItems(it, "rechargeApplications") }
            "rechargeAgents" -> {
                body.addView(button("＋ إضافة وكيل شحن") { showAssignRechargeAgentDialog() })
                load(api.rechargeAgents()) { renderItems(it, "rechargeAgents") }
            }
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
                if (currentRole == StaffRoleHelper.SUPER) {
                    body.addView(button("＋ إضافة آي دي إلى الكتالوج") { showCreateVanityDialog() })
                }
                load(api.vanityIds()) { renderItems(it, "vanityIds") }
            }
            "gifts" -> load(api.gifts()) { renderItems(it, "gifts") }
            "vip" -> {
                body.addView(button("＋ منح عضوية VIP") { showAssignVipDialog() })
                if (currentRole == StaffRoleHelper.SUPER) {
                    body.addView(button("＋ إنشاء خطة VIP") { showCreateVipPlanDialog() })
                }
                load(api.vipMembers(1, 40)) { renderItems(it, "vip") }
                val plansButton = button("إدارة خطط VIP") {
                    load(api.vipPlans()) { renderItems(it, "vipPlans") }
                }
                if (currentRole == StaffRoleHelper.SUPER) body.addView(plansButton)
            }
            "toastManager" -> showToastManager()
            "mysteriousMan" -> showMysteriousManSettings()
            "luckyCannon" -> showLuckyCannonSettings()
            "supportChest" -> showSupportChestSettings()
            "roomPkSettings" -> showRoomPkSettings()
            "notifications" -> {
                body.addView(button("＋ رسالة عامة أو خاصة + إشعار") { showNotificationDialog() })
                body.addView(button("✉ رسالة دردشة مباشرة إلى ID محدد") { showDirectMessageDialog() })
                load(api.notifications(1, 40)) { renderItems(it, "notifications") }
            }
            "logs" -> load(api.logs(1, 50)) { renderItems(it, "logs") }
            "staffIdentity" -> showStaffIdentityEditor()
        }
    }

    private fun showRoomPkSettings() {
        load(api.adminSettings()) { raw ->
            val root = featureMap(raw)
            val settingsValue = root["settings"] ?: root["items"] ?: raw
            val settings = settingsValue as? Map<*, *>
            val row = (settingsValue as? List<*>)?.firstOrNull {
                it is Map<*, *> && it["key"]?.toString() == "features.room_pk_enabled"
            } as? Map<*, *>
            val rawValue = settings?.get("features.room_pk_enabled") ?: row?.get("value")
            val enabledNow = when (rawValue?.toString()?.lowercase()) {
                "0", "false", "no", "off" -> false
                "1", "true", "yes", "on" -> true
                else -> true
            }
            val panel = card()
            addText(panel, "تحديات PK بين الرومات", 17f, true, TEXT)
            addText(panel, "هذا المفتاح عام فقط. عند الإيقاف تتوقف تحديات الرومات للجميع، ولا يتغير اتصال ZEGO أو الصوت أو المقاعد.", 12f, false, MUTED)
            val enabled = featureCheck(panel, "تفعيل تحديات PK", enabledNow)
            body.addView(panel)
            body.addView(button("حفظ حالة تحديات PK") {
                val payload = mapOf("features.room_pk_enabled" to enabled.isChecked.toString())
                load(api.updateAdminSettings(payload)) {
                    toast(if (enabled.isChecked) "تم تفعيل تحديات PK" else "تم إيقاف تحديات PK")
                    openModule("roomPkSettings")
                }
            })
        }
    }

    private fun featureField(parent: LinearLayout, label: String, value: String, hint: String = ""): EditText {
        addText(parent, label, 13f, true, TEXT)
        val input = EditText(this).apply {
            setSingleLine(true)
            textSize = 13f
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            this.hint = hint
            background = roundRect(Color.WHITE, 12)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setText(value)
        }
        parent.addView(input, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        return input
    }

    private fun featureCheck(parent: LinearLayout, label: String, value: Boolean): android.widget.CheckBox {
        return android.widget.CheckBox(this).apply {
            text = label
            isChecked = value
            setTextColor(TEXT)
            textSize = 13f
            parent.addView(this)
        }
    }

    private fun featureMap(data: Any?): Map<*, *> = data as? Map<*, *> ?: emptyMap<Any, Any>()
    private fun featureString(map: Map<*, *>, key: String, fallback: String = ""): String =
        map[key]?.toString() ?: fallback
    private fun featureBool(map: Map<*, *>, key: String, fallback: Boolean = false): Boolean =
        map[key] as? Boolean ?: fallback

    private fun showMysteriousManSettings() {
        load(api.mysteriousManConfig()) { raw ->
            val cfg = featureMap(raw)
            val panel = card()
            addText(panel, "إعدادات الرجل الغامض", 17f, true, TEXT)
            addText(panel, "إيقاف الميزة من هنا يلغي وضع الهوية الغامضة للمستخدمين على الخادم.", 12f, false, MUTED)
            val enabled = featureCheck(panel, "تفعيل الميزة عالمياً", featureBool(cfg, "enabled"))
            val levels = featureField(panel, "مستويات VIP المسموحة (مثال: 1,2,3)", (cfg["eligibleVipLevels"] as? List<*>)?.joinToString(",") ?: "")
            val avatar = featureField(panel, "رابط الصورة الموحدة", featureString(cfg, "avatarUrl"), "https://… أو /uploads/…")
            val entryMode = featureField(panel, "نوع الدخولية: video أو text", featureString(cfg, "entryMode", "video"))
            val entryText = featureField(panel, "نص الدخولية", featureString(cfg, "entryText", "دخل الرجل الغامض"))
            val entryEffect = featureField(panel, "رابط الدخولية المتحركة", featureString(cfg, "entryEffectUrl"), "https://… أو /uploads/…")
            val messageEffect = featureField(panel, "رابط تأثير الرسائل", featureString(cfg, "messageEffectUrl"), "https://… أو /uploads/…")
            body.addView(panel)
            body.addView(button("حفظ إعدادات الرجل الغامض") {
                val payload = linkedMapOf<String, Any>(
                    "enabled" to enabled.isChecked,
                    "eligibleVipLevels" to levels.text.toString().split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.distinct().sorted(),
                    "avatarUrl" to avatar.text.toString().trim(),
                    "entryMode" to if (entryMode.text.toString().trim().equals("text", true)) "text" else "video",
                    "entryText" to entryText.text.toString().trim(),
                    "entryEffectUrl" to entryEffect.text.toString().trim(),
                    "messageEffectUrl" to messageEffect.text.toString().trim()
                )
                load(api.updateMysteriousManConfig(payload)) {
                    toast("تم حفظ إعدادات الرجل الغامض")
                    openModule("mysteriousMan")
                }
            })
        }
    }

    private fun showLuckyCannonSettings() {
        load(api.luckyCannonConfig()) { raw ->
            val cfg = featureMap(raw)
            val panel = card()
            addText(panel, "مدفع هدايا المحظوظ", 17f, true, TEXT)
            addText(panel, "تُطبّق الإعدادات على زر المحظوظ فقط، ولا تغيّر إرسال الهدايا أو خصم العملات.", 12f, false, MUTED)
            val enabled = featureCheck(panel, "تفعيل زر المحظوظ داخل الرومات", featureBool(cfg, "enabled", true))
            val duration = featureField(panel, "مدة الظهور بالثواني (1–10)", featureString(cfg, "durationSeconds", "2"))
            val sound = featureCheck(panel, "صوت ضغط خفيف", featureBool(cfg, "soundEnabled", true))
            body.addView(panel)
            body.addView(button("حفظ إعدادات مدفع المحظوظ") {
                val payload = linkedMapOf<String, Any>(
                    "enabled" to enabled.isChecked,
                    "durationSeconds" to (duration.text.toString().toIntOrNull() ?: 2).coerceIn(1, 10),
                    "soundEnabled" to sound.isChecked
                )
                load(api.updateLuckyCannonConfig(payload)) {
                    toast("تم حفظ إعدادات مدفع المحظوظ")
                    openModule("luckyCannon")
                }
            })
        }
    }

    private fun showSupportChestSettings() {
        load(api.supportChestConfig()) { raw ->
            val cfg = featureMap(raw)
            val panel = card()
            addText(panel, "صندوق الدعم اليومي", 17f, true, TEXT)
            val enabled = featureCheck(panel, "تفعيل الصندوق", featureBool(cfg, "enabled"))
            val iconVisible = featureCheck(panel, "إظهار أيقونة الصندوق", featureBool(cfg, "iconVisible", true))
            val mode = featureField(panel, "نوع الهدف: coins أو diamonds أو both", featureString(cfg, "goalMode", "coins"))
            val coinTarget = featureField(panel, "هدف العملات", featureString(cfg, "coinTarget", "100000"))
            val diamondTarget = featureField(panel, "هدف الماسات", featureString(cfg, "diamondTarget", "10000"))
            val giftIds = featureField(panel, "معرّفات الهدايا المؤهلة (مفصولة بفواصل)", (cfg["giftIds"] as? List<*>)?.joinToString(",") ?: "")
            val rewardType = featureField(panel, "نوع المكافأة: none / coins / diamonds / frame / badge", featureString(cfg, "rewardType", "none"))
            val rewardItem = featureField(panel, "معرّف عنصر المكافأة (للإطار أو الشارة)", featureString(cfg, "rewardItemId"))
            val rewardAmount = featureField(panel, "قيمة المكافأة", featureString(cfg, "rewardAmount", "0"))
            val instructions = featureField(panel, "تعليمات الصندوق", featureString(cfg, "instructions", ""))
            body.addView(panel)
            body.addView(button("حفظ إعدادات صندوق الدعم") {
                val payload = linkedMapOf<String, Any>(
                    "enabled" to enabled.isChecked,
                    "iconVisible" to iconVisible.isChecked,
                    "goalMode" to mode.text.toString().trim().lowercase().let { if (it in listOf("coins", "diamonds", "both")) it else "coins" },
                    "coinTarget" to (coinTarget.text.toString().toLongOrNull() ?: 100000L),
                    "diamondTarget" to (diamondTarget.text.toString().toLongOrNull() ?: 10000L),
                    "giftIds" to giftIds.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                    "rewardType" to rewardType.text.toString().trim().lowercase().let { if (it in listOf("none", "coins", "diamonds", "frame", "badge")) it else "none" },
                    "rewardItemId" to rewardItem.text.toString().trim(),
                    "rewardAmount" to (rewardAmount.text.toString().toLongOrNull() ?: 0L),
                    "instructions" to instructions.text.toString().trim()
                )
                load(api.updateSupportChestConfig(payload)) {
                    toast("تم حفظ إعدادات صندوق الدعم")
                    openModule("supportChest")
                }
            })
        }
    }

    private fun showToastManager() {
        body.addView(button("↻ تحميل إعدادات التوستات") { openModule("toastManager") })
        addText(body, "تُحفظ الإعدادات على الخادم وتُطبّق على أجهزة التطبيق. الحركة نفسها تعمل محلياً لتقليل ضغط Blitz.", 12f, false, MUTED)
        load(api.toastManagerConfig()) { data ->
            val config = data as? Map<*, *> ?: emptyMap<Any, Any>()
            fun nested(key: String): MutableMap<String, Any?> {
                val source = config[key] as? Map<*, *> ?: emptyMap<Any, Any>()
                return source.entries.associate { it.key.toString() to it.value }.toMutableMap()
            }
            val gift = nested("giftSend")
            val normal = nested("luckyWinNormal")
            val high = nested("luckyWinHigh")
            val luckyButton = nested("luckyButton")
            fun field(parent: LinearLayout, label: String, value: String, hint: String = ""): EditText {
                addText(parent, label, 13f, true, TEXT)
                val input = EditText(this).apply {
                    setSingleLine(true)
                    textSize = 13f
                    setTextColor(TEXT)
                    setHintTextColor(MUTED)
                    this.hint = hint
                    background = roundRect(Color.WHITE, 12)
                    setPadding(dp(12), dp(9), dp(12), dp(9))
                    setText(value)
                }
                parent.addView(input, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
                return input
            }
            fun checked(parent: LinearLayout, label: String, value: Boolean): android.widget.CheckBox {
                return android.widget.CheckBox(this).apply {
                    text = label
                    isChecked = value
                    setTextColor(TEXT)
                    textSize = 13f
                    parent.addView(this)
                }
            }
            fun value(map: Map<String, Any?>, key: String, fallback: String): String =
                map[key]?.toString()?.takeIf { it.isNotBlank() } ?: fallback
            fun bool(map: Map<String, Any?>, key: String, fallback: Boolean): Boolean =
                map[key] as? Boolean ?: fallback

            val giftCard = card()
            addText(giftCard, "توست إرسال الهدايا والعداد X", 16f, true, TEXT)
            val giftUrl = field(giftCard, "رابط الخلفية", value(gift, "backgroundUrl", ""), "https://…")
            val giftDuration = field(giftCard, "مدة الظهور بالثواني (1–12)", value(gift, "durationSeconds", "3"))
            val giftShine = checked(giftCard, "لمعة متحركة", bool(gift, "shineEnabled", true))
            val giftGlow = checked(giftCard, "توهج الإطار", bool(gift, "frameGlowEnabled", true))
            addToastLivePreview(giftCard, giftUrl, "توست إرسال الهدايا", "اسم المستخدم  ·  الهدية ×7")
            body.addView(giftCard)

            val normalCard = card()
            addText(normalCard, "توست الربح المحظوظ العادي", 16f, true, TEXT)
            val normalUrl = field(normalCard, "رابط الخلفية", value(normal, "backgroundUrl", ""), "https://…")
            val normalDuration = field(normalCard, "مدة الظهور بالثواني (1–12)", value(normal, "durationSeconds", "4"))
            val normalShine = checked(normalCard, "لمعة متحركة", bool(normal, "shineEnabled", true))
            val normalGlow = checked(normalCard, "توهج الإطار", bool(normal, "frameGlowEnabled", true))
            addToastLivePreview(normalCard, normalUrl, "توست الربح المحظوظ", "الفائز  ·  ×7  ·  +12,500")
            body.addView(normalCard)

            val highCard = card()
            addText(highCard, "توست الربح العالي", 16f, true, TEXT)
            val highUrl = field(highCard, "رابط الخلفية", value(high, "backgroundUrl", ""), "https://…")
            val highDuration = field(highCard, "مدة الظهور بالثواني (1–12)", value(high, "durationSeconds", "5"))
            val highShine = checked(highCard, "لمعة متحركة", bool(high, "shineEnabled", true))
            val highGlow = checked(highCard, "توهج الإطار", bool(high, "frameGlowEnabled", true))
            addToastLivePreview(highCard, highUrl, "توست الربح العالي", "الفائز  ·  ×177  ·  +250,000")
            body.addView(highCard)

            val buttonCard = card()
            addText(buttonCard, "زر المحظوظ المنبثق", 16f, true, TEXT)
            val buttonUrl = field(buttonCard, "رابط أيقونة الزر", value(luckyButton, "iconUrl", ""), "https://…")
            val buttonDuration = field(buttonCard, "مدة ظهور الزر بالثواني (1–12)", value(luckyButton, "durationSeconds", "5"))
            val buttonPulse = checked(buttonCard, "نبضة وحركة خفيفة للزر", bool(luckyButton, "pulseEnabled", true))
            addToastIconPreview(buttonCard, buttonUrl)
            body.addView(buttonCard)

            body.addView(button("حفظ وتطبيق الإعدادات") {
                fun seconds(input: EditText, fallback: Int): Int =
                    (input.text.toString().toIntOrNull() ?: fallback).coerceIn(1, 12)
                val nextGift = gift.toMutableMap().apply {
                    put("backgroundUrl", giftUrl.text.toString().trim())
                    put("durationSeconds", seconds(giftDuration, 3))
                    put("shineEnabled", giftShine.isChecked)
                    put("frameGlowEnabled", giftGlow.isChecked)
                }
                val nextNormal = normal.toMutableMap().apply {
                    put("backgroundUrl", normalUrl.text.toString().trim())
                    put("durationSeconds", seconds(normalDuration, 4))
                    put("shineEnabled", normalShine.isChecked)
                    put("frameGlowEnabled", normalGlow.isChecked)
                }
                val nextHigh = high.toMutableMap().apply {
                    put("backgroundUrl", highUrl.text.toString().trim())
                    put("durationSeconds", seconds(highDuration, 5))
                    put("shineEnabled", highShine.isChecked)
                    put("frameGlowEnabled", highGlow.isChecked)
                }
                val nextButton = luckyButton.toMutableMap().apply {
                    put("iconUrl", buttonUrl.text.toString().trim())
                    put("durationSeconds", seconds(buttonDuration, 5))
                    put("pulseEnabled", buttonPulse.isChecked)
                }
                val payload = linkedMapOf<String, Any>(
                    "giftSend" to nextGift,
                    "luckyWinNormal" to nextNormal,
                    "luckyWinHigh" to nextHigh,
                    "luckyButton" to nextButton
                )
                load(api.updateToastManagerConfig(payload)) {
                    toast("تم حفظ إعدادات التوستات")
                    openModule("toastManager")
                }
            })
        }
    }

    /**
     * Live native preview of the exact background URL configured in the dashboard.
     * FIT_CENTER preserves the uploaded artwork and its transparency; the sample copy
     * is an overlay only and is not baked into or uploaded with the image.
     */
    private fun addToastLivePreview(parent: LinearLayout, urlField: EditText, title: String, sample: String) {
        addText(parent, "معاينة حية — $title", 12f, true, ACCENT)
        val frame = FrameLayout(this).apply {
            background = roundRect(0xFFF0EDF8.toInt(), 16)
            clipToPadding = true
            layoutParams = LinearLayout.LayoutParams(-1, dp(174)).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
        }
        val artwork = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "الخلفية الفعلية لـ $title"
        }
        frame.addView(artwork, FrameLayout.LayoutParams(-1, -1))
        val overlay = TextView(this).apply {
            text = sample
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                setColor(0x990E1020.toInt())
                cornerRadius = dp(10).toFloat()
            }
        }
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(10))
        })
        parent.addView(frame)
        fun refresh() {
            val raw = urlField.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) {
                Glide.with(this).clear(artwork)
                artwork.setImageDrawable(null)
            } else {
                val resolved = AssetCatalog.absoluteUrl(raw) ?: raw
                Glide.with(this).load(resolved).fitCenter().into(artwork)
            }
        }
        urlField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        refresh()
    }

    private fun addToastIconPreview(parent: LinearLayout, urlField: EditText) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundRect(0xFFF8F7FC.toInt(), 12)
        }
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "أيقونة زر المحظوظ"
        }
        row.addView(icon, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(10) })
        row.addView(TextView(this).apply {
            text = "معاينة حية لأيقونة الزر من الرابط المحدد"
            textSize = 12f
            setTextColor(MUTED)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
        fun refresh() {
            val raw = urlField.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) {
                Glide.with(this).clear(icon)
                icon.setImageDrawable(null)
            } else {
                val resolved = AssetCatalog.absoluteUrl(raw) ?: raw
                Glide.with(this).load(resolved).fitCenter().into(icon)
            }
        }
        urlField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        refresh()
    }

    private fun showTemporaryBanDialog(userId: String) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val hoursInput = EditText(this).apply {
            hint = "مدة الحظر بالساعات (1 إلى 24)"
            setText("24")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            background = roundRect(0xFFF5F4FA.toInt(), 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val reasonInput = EditText(this).apply {
            hint = "سبب الحظر (مطلوب)"
            setSingleLine(false)
            minLines = 2
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            background = roundRect(0xFFF5F4FA.toInt(), 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        form.addView(hoursInput, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(8) })
        form.addView(reasonInput, LinearLayout.LayoutParams(-1, dp(88)))
        val dialog = AlertDialog.Builder(this)
            .setTitle("حظر مؤقت للمستخدم")
            .setMessage("يُرفع الحظر تلقائياً عند انتهاء المدة. الحد الأقصى للأدمن 24 ساعة.")
            .setView(form)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("تأكيد الحظر", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hours = hoursInput.text.toString().trim().toIntOrNull()
                val reason = reasonInput.text.toString().trim()
                if (hours == null || hours !in 1..24) {
                    hoursInput.error = "أدخل مدة بين 1 و24 ساعة"
                    return@setOnClickListener
                }
                if (reason.isBlank()) {
                    reasonInput.error = "سبب الحظر مطلوب"
                    return@setOnClickListener
                }
                dialog.dismiss()
                confirm("تأكيد حظر المستخدم لمدة $hours ساعة؟") {
                    load(api.temporaryBanUser(userId, mapOf("hours" to hours, "reason" to reason))) {
                        toast("تم تسجيل الحظر المؤقت؛ ينتهي خلال $hours ساعة")
                        openModule("users")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun addSectionIntro(module: Module) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                intArrayOf(0xFFF0ECFB.toInt(), 0xFFFFFFFF.toInt())
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFFE5DFF7.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
                bottomMargin = dp(12)
            }
        }
        val icon = TextView(this).apply {
            text = module.icon
            textSize = 25f
            gravity = Gravity.CENTER
            background = roundRect(Color.WHITE, 13)
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply { marginStart = dp(12) }
        }
        panel.addView(icon)
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        addText(copy, module.title, 15f, true, TEXT)
        val description = TextView(this).apply {
            text = module.subtitle
            textSize = 12f
            setTextColor(MUTED)
            maxLines = 3
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(2), 0, 0)
        }
        copy.addView(description)
        panel.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(panel)

        val accessCard = card().apply {
            setPadding(dp(13), dp(11), dp(13), dp(11))
        }
        val accessTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val roleLabel = when (currentRole) {
            StaffRoleHelper.SUPER -> "سوبر أدمن"
            StaffRoleHelper.ADMIN -> "أدمن"
            else -> "مانجر"
        }
        val rolePill = TextView(this).apply {
            text = "صلاحياتك · $roleLabel"
            textSize = 11f
            setTextColor(ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = roundRect(0xFFF0ECFB.toInt(), 18)
        }
        accessTop.addView(rolePill, LinearLayout.LayoutParams(0, -2, 1f))
        accessTop.addView(button("↻ تحديث") { openModule(module.key) })
        accessCard.addView(accessTop)
        val scopeText = when (currentRole) {
            StaffRoleHelper.SUPER -> "صلاحيات الإدارة الكاملة المتاحة لهذا القسم، مع تأكيد العمليات الحساسة."
            StaffRoleHelper.ADMIN -> "تظهر لك إجراءات الإشراف المسموحة لرتبتك؛ التحقق النهائي يتم على الخادم."
            else -> "وصول إشرافي محدود حسب القسم؛ لا تتوفر أدوات السوبر أدمن أو تغيير الإعدادات العامة."
        }
        addText(accessCard, scopeText, 11f, false, MUTED)
        body.addView(accessCard)
    }

    private fun loadUsers(search: String) {
        // Preserve the search controls and section introduction while replacing results.
        // Search row + section intro + role/access card stay in place while results refresh.
        val fixedViews = if (body.childCount >= 3) 3 else body.childCount
        if (body.childCount > fixedViews) body.removeViews(fixedViews, body.childCount - fixedViews)
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
        val extractedItems = extractItems(data)
        val grantableTypes = setOf("entry_effect", "profile_card", "nameplate")
        val items = if (type == "grantCosmetics" && currentRole != StaffRoleHelper.SUPER) {
            extractedItems.filter { item ->
                value(item, "type")?.trim()?.lowercase() in grantableTypes
            }
        } else extractedItems
        val countPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = GradientDrawable().apply {
                setColor(0xFFF0ECFB.toInt())
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
        }
        addText(countPanel, "النتائج المعروضة", 12f, true, TEXT)
        val count = TextView(this).apply {
            text = items.size.toString()
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            background = roundRect(Color.WHITE, 20)
            setPadding(dp(11), dp(4), dp(11), dp(4))
        }
        countPanel.addView(count, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        body.addView(countPanel)
        if (items.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(22), dp(25), dp(22), dp(25))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), 0xFFE8E7F2.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            }
            val emptyIcon = TextView(this).apply {
                text = when (type) {
                    "users" -> "👥"
                    "rooms" -> "🏠"
                    "reports" -> "🛡️"
                    "logs" -> "🧾"
                    else -> "📂"
                }
                textSize = 28f
                gravity = Gravity.CENTER
                background = roundRect(0xFFF0ECFB.toInt(), 18)
                layoutParams = LinearLayout.LayoutParams(dp(62), dp(62)).apply { bottomMargin = dp(12) }
            }
            empty.addView(emptyIcon)
            addText(empty, when (type) {
                "users" -> "لم يتم العثور على مستخدمين"
                "rooms" -> "لا توجد رومات لعرضها"
                "reports" -> "لا توجد بلاغات في القائمة الحالية"
                "logs" -> "لا توجد عمليات مسجلة للعرض"
                else -> "لا توجد عناصر حالياً"
            }, 15f, true, TEXT)
            val explanation = TextView(this).apply {
                text = "قد تكون القائمة فارغة حالياً. يمكنك تحديث القسم أو تجربة بحث مختلف."
                textSize = 12f
                setTextColor(MUTED)
                gravity = Gravity.CENTER
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(5), 0, 0)
            }
            empty.addView(explanation)
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
            if (type in listOf("cosmetics", "grantCosmetics", "gifts")) {
                addLiveAssetPreview(card, item, type, primary)
            }
            if (!id.isNullOrBlank()) addText(card, "معرّف السجل: $id", 11f, false, MUTED)
            if (type == "users" && !publicId.isNullOrBlank() && publicId != id) {
                val publicIdRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                addText(publicIdRow, "ID المستخدم: $publicId", 12f, true, ACCENT)
                publicIdRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                publicIdRow.addView(button("نسخ ID") { copyToClipboard("ID المستخدم", publicId) }, LinearLayout.LayoutParams(-2, dp(40)))
                card.addView(publicIdRow)
            }
            if (!status.isNullOrBlank()) card.addView(statusChip(status))
            val detailKeys = when (type) {
                "users" -> listOf("username", "role", "coins", "balance", "diamonds", "level", "vipExpiresAt", "createdAt", "lastSeenAt")
                "reports" -> listOf("reporterName", "reporterId", "targetName", "targetId", "targetType", "reason", "category", "createdAt")
                "rooms" -> listOf("ownerName", "membersCount", "accessMode", "entryFeeCoins", "createdAt")
                "streams" -> listOf("hostName", "viewerCount", "startedAt", "createdAt")
                "agencies" -> listOf("ownerName", "membersCount", "status", "createdAt")
                "agencyApplications", "rechargeApplications", "genderVerifications" -> listOf("applicantName", "userName", "userId", "reason", "createdAt")
                "withdrawals", "recharges" -> listOf("userName", "userId", "amount", "coins", "status", "createdAt")
                "logs" -> listOf("actorName", "actorId", "action", "targetType", "targetId", "createdAt")
                else -> listOf("createdAt", "role", "level", "membersCount", "ownerName", "coinPrice", "diamondValue")
            }
            val detail = detailKeys.mapNotNull { k -> value(item, k)?.takeIf { it.isNotBlank() }?.let { "${prettyKey(k)}: $it" } }
            if (detail.isNotEmpty()) {
                val detailPanel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(dp(11), dp(9), dp(11), dp(9))
                    background = GradientDrawable().apply {
                        setColor(0xFFF7F7FC.toInt())
                        cornerRadius = dp(12).toFloat()
                    }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) }
                }
                detail.take(if (type == "reports" || type == "logs") 6 else 4).forEach { line ->
                    val lineView = TextView(this).apply {
                        text = line
                        textSize = 12f
                        setTextColor(MUTED)
                        setPadding(0, dp(3), 0, dp(3))
                        maxLines = 2
                    }
                    detailPanel.addView(lineView)
                }
                card.addView(detailPanel)
            }
            if (type == "reports") {
                val description = value(item, "description", "message", "details", "content", "reason")
                if (!description.isNullOrBlank()) {
                    addText(card, "تفاصيل البلاغ", 12f, true, TEXT)
                    val reportBody = TextView(this).apply {
                        text = description
                        textSize = 13f
                        setTextColor(TEXT)
                        maxLines = 6
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setLineSpacing(dp(3).toFloat(), 1f)
                        setPadding(0, dp(4), 0, dp(8))
                    }
                    card.addView(reportBody)
                }
            }

            when (type) {
                "users", "rooms" -> if (!id.isNullOrBlank()) {
                    card.addView(button("فتح تفاصيل ${if (type == "users") "المستخدم" else "الروم"} والإجراءات  ←") {
                        showEntityDetailScreen(item, type)
                    })
                    addText(card, if (type == "users") {
                        when (currentRole) {
                            StaffRoleHelper.SUPER -> "رتبتك: سوبر أدمن — صلاحيات إدارة الحساب الكاملة."
                            StaffRoleHelper.ADMIN -> "رتبتك: أدمن — إجراءات الإشراف والحظر المؤقت فقط."
                            else -> "رتبتك: مانجر — لا تتوفر إدارة حسابات المستخدمين من هذه الصفحة."
                        }
                    } else {
                        when (currentRole) {
                            StaffRoleHelper.SUPER -> "رتبتك: سوبر أدمن — إدارة الروم وإجراءاته."
                            StaffRoleHelper.ADMIN -> "رتبتك: أدمن — إدارة الروم وفق صلاحيات الخادم."
                            else -> "رتبتك: مانجر — إشراف الروم ضمن الصلاحيات الممنوحة."
                        }
                    }, 11f, false, MUTED)
                }
                "streams" -> if (!id.isNullOrBlank()) {
                    card.addView(button("إنهاء البث") {
                        confirm("سيتم إنهاء البث على الخادم. هل تريد المتابعة؟") {
                            load(api.endStream(id)) { toast("استجاب الخادم لطلب إنهاء البث"); openModule("streams") }
                        }
                    })
                    if (currentRole == StaffRoleHelper.SUPER) {
                        card.addView(button("إنهاء قسري للبث") {
                            confirm("استخدم الإنهاء القسري فقط إذا فشل الإنهاء العادي. متابعة؟") {
                                load(api.forceEndStream(id, mapOf("reason" to "super_admin_force_end"))) {
                                    toast("استجاب الخادم لطلب الإنهاء القسري"); openModule("streams")
                                }
                            }
                        })
                    }
                }
                "agencies" -> if (!id.isNullOrBlank()) {
                    val suspended = status?.contains("suspended", true) == true
                    card.addView(button(if (suspended) "إعادة تفعيل الوكالة" else "تعليق الوكالة") {
                        confirm(if (suspended) "إعادة تفعيل الوكالة؟" else "تعليق الوكالة قد يغلق روماتها. هل تريد المتابعة؟") {
                            load(if (suspended) api.approveAgency(id) else api.suspendAgency(id, mapOf("reason" to "تم التعليق من تطبيق JEHO"))) {
                                toast(if (suspended) "تم إرسال طلب تفعيل الوكالة" else "تم إرسال طلب تعليق الوكالة")
                                openModule("agencies")
                            }
                        }
                    })
                    card.addView(button("حذف الوكالة نهائيًا") {
                        confirm("تحذير: حذف الوكالة قد ينهي بث روماتها ويحذف ارتباطاتها. لا يمكن التراجع. متابعة؟") {
                            load(api.deleteAgency(id)) {
                                toast("استجاب الخادم لطلب حذف الوكالة")
                                openModule("agencies")
                            }
                        }
                    })
                }
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
                    card.addView(button("حذف وكيل الشحن") {
                        confirm("سيُحذف تسجيل الوكيل. تأكد من تسوية رصيده والتزاماته أولًا. متابعة؟") {
                            load(api.deleteRechargeAgent(id)) {
                                toast("استجاب الخادم لطلب حذف وكيل الشحن")
                                openModule("rechargeAgents")
                            }
                        }
                    })
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
                    card.addView(button("مراجعة البلاغ واتخاذ إجراء") {
                        showReportDecisionDialog(id, true)
                    })
                    card.addView(button("رفض البلاغ") {
                        showReportDecisionDialog(id, false)
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
                "gifts" -> if (!id.isNullOrBlank() && currentRole == StaffRoleHelper.SUPER) {
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
                    if (currentRole == StaffRoleHelper.SUPER) when (status?.lowercase()) {
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
                "cosmetics" -> if (!id.isNullOrBlank() && currentRole == StaffRoleHelper.SUPER) {
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

    /**
     * Shows the actual icon/preview URL returned by the live catalog API.
     * This is read-only; it never purchases, grants, or sends an item.
     */
    private fun addLiveAssetPreview(parent: LinearLayout, item: Any, type: String, title: String) {
        val assetUrl = if (type == "gifts") {
            value(item, "iconUrl", "previewUrl", "thumbnailUrl")
        } else {
            value(item, "previewUrl", "thumbnailUrl", "iconUrl", "imageUrl")
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val absolute = AssetCatalog.absoluteUrl(assetUrl) ?: assetUrl
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFFF8F7FC.toInt())
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0xFFE9E5F4.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "معاينة $title"
            background = roundRect(Color.WHITE, 10)
        }
        row.addView(image, LinearLayout.LayoutParams(dp(94), dp(94)).apply { marginStart = dp(12) })
        Glide.with(this).load(absolute).fitCenter().into(image)
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        addText(textCol, "معاينة مباشرة من الكتالوج", 12f, true, ACCENT)
        addText(textCol, if (type == "gifts") "أيقونة الهدية الفعلية" else "صورة العنصر الفعلية", 11f, false, MUTED)
        textCol.addView(button("تكبير المعاينة") {
            val large = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.WHITE)
                contentDescription = "المعاينة الكاملة لـ $title"
            }
            Glide.with(this).load(absolute).fitCenter().into(large)
            AlertDialog.Builder(this).setTitle(title)
                .setView(large.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(300)) })
                .setPositiveButton("إغلاق", null).show()
        })
        row.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
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

    /**
     * Native, grouped detail sheet for users and rooms. Mutations still use the existing
     * AdminApi and are authorized by AdminGuard on the server.
     */
    private var detailReturnSection = "home"

    private fun handleAdminBack() {
        when (currentSection) {
            "userDetail", "roomDetail" -> openModule(detailReturnSection)
            "home" -> finish()
            else -> showHome()
        }
    }

    private fun showEntityDetailScreen(item: Any, type: String) {
        val id = value(item, "id", "userId", "roomId") ?: return
        val isUser = type == "users"
        detailReturnSection = type
        currentSection = if (isUser) "userDetail" else "roomDetail"
        titleView.text = if (isUser) "تفاصيل المستخدم" else "تفاصيل الروم"
        backButton.visibility = View.VISIBLE
        body.removeAllViews()
        val name = value(item, "displayName", "name", "title", "username", "email", "roomName")
            ?: if (isUser) "مستخدم" else "روم"
        val status = value(item, "status", "state", "isActive", "type") ?: "غير محدد"
        val role = value(item, "staffRole", "role") ?: "بدون رتبة إدارية"
        val publicId = value(item, "publicId")
        val roomTitle = value(item, "title", "name", "roomName") ?: name
        val accessMode = value(item, "accessMode") ?: "public"
        val entryFee = value(item, "entryFeeCoins") ?: "0"

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(14), dp(6), dp(14), dp(8))
        }
        fun section(title: String, subtitle: String = ""): LinearLayout {
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                setPadding(dp(12), dp(11), dp(12), dp(11))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(15).toFloat()
                    setStroke(dp(1), 0xFFE8E7F2.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
            }
            addText(panel, title, 14f, true, TEXT)
            if (subtitle.isNotBlank()) addText(panel, subtitle, 11f, false, MUTED)
            content.addView(panel)
            return panel
        }
        val overview = section(if (isUser) "ملف المستخدم" else "ملف الروم", "المعلومات المعروضة من بيانات الخادم")
        addText(overview, name, 16f, true, TEXT)
        addText(overview, "معرّف السجل: $id", 12f, false, MUTED)
        if (isUser && !publicId.isNullOrBlank()) {
            addText(overview, "ID العام: $publicId", 12f, true, ACCENT)
            overview.addView(button("نسخ ID العام") { copyToClipboard("ID المستخدم", publicId) })
        }
        addText(overview, "الحالة: $status", 12f, true, TEXT)
        if (isUser) addText(overview, "الرتبة الإدارية: $role", 12f, false, MUTED)
        val detailKeys = if (isUser) {
            listOf("username", "email", "coins", "balance", "diamonds", "level", "vipLevel", "vipExpiresAt", "createdAt", "lastSeenAt", "country", "gender")
        } else {
            listOf("ownerName", "ownerId", "membersCount", "accessMode", "entryFeeCoins", "createdAt", "description", "isActive", "roomType")
        }
        val details = detailKeys.mapNotNull { key ->
            value(item, key)?.takeIf { it.isNotBlank() }?.let { prettyKey(key) to it }
        }
        if (details.isNotEmpty()) {
            val detailPanel = section("تفاصيل إضافية")
            details.forEach { (label, value) -> addText(detailPanel, "$label: $value", 12f, false, MUTED) }
        }
        val permissions = section("الصلاحيات حسب رتبتك")
        val permissionText = if (isUser) when (currentRole) {
            StaffRoleHelper.SUPER -> "سوبر أدمن: تعديل الرتبة والحالة، الحظر الدائم أو إلغاؤه، الحظر المؤقت، والحذف عند الضرورة."
            StaffRoleHelper.ADMIN -> "أدمن: الحظر المؤقت حتى 24 ساعة وفك الحظر المؤقت الذي أصدرته أنت. لا يملك تغيير الرتب أو الحظر الدائم أو حذف الحساب."
            else -> "مانجر: لا يملك أدوات تغيير الحسابات؛ يقتصر دوره على الرومات والبث والبلاغات حسب صلاحيات الخادم."
        } else when (currentRole) {
            StaffRoleHelper.SUPER -> "سوبر أدمن: إدارة إعدادات الروم وإغلاقه وفق صلاحيات الخادم."
            StaffRoleHelper.ADMIN -> "أدمن: إجراءات الروم المتاحة له وفق صلاحيات الخادم."
            else -> "مانجر: إشراف الروم وإجراءاته المحددة من الخادم."
        }
        addText(permissions, permissionText, 12f, false, MUTED)

        if (isUser) {
            val actions = section("إجراءات الحساب", "تظهر الإجراءات وفق رتبتك، ويظل الخادم المرجع النهائي للصلاحيات.")
            if (currentRole == StaffRoleHelper.SUPER || currentRole == StaffRoleHelper.ADMIN) {
                actions.addView(button("حظر مؤقت حتى 24 ساعة") { showTemporaryBanDialog(id) })
                actions.addView(button("فك الحظر المؤقت") {
                    confirm("سيُفك الحظر المؤقت الذي أصدرته أنت، أو أي حظر مؤقت عند السوبر أدمن. لن يُفك الحظر الدائم.") {
                        load(api.temporaryUnbanUser(id)) {
                            toast("تم إرسال طلب فك الحظر المؤقت")
                            openModule("users")
                        }
                    }
                })
            }
            if (currentRole == StaffRoleHelper.SUPER) {
                actions.addView(button("تعديل الرتبة والحالة") { showUserAccessDialog(id, role, status) })
                val normalizedStatus = status.trim().lowercase()
                val banned = normalizedStatus.contains("banned") || normalizedStatus.contains("blocked") ||
                    normalizedStatus.contains("محظور") || normalizedStatus == "ban" || normalizedStatus == "1"
                actions.addView(button(if (banned) "إلغاء الحظر الدائم" else "حظر المستخدم") {
                    confirm(if (banned) "إلغاء حظر المستخدم؟" else "تأكيد حظر المستخدم؟") {
                        load(if (banned) api.unbanUser(id) else api.banUser(id)) {
                            toast("استجاب الخادم لطلب تحديث الحظر")
                            openModule("users")
                        }
                    }
                })
                actions.addView(button("حذف الحساب") {
                    confirm("تحذير: حذف الحساب إجراء حساس. تأكد من هوية الحساب وضرورة الحذف. متابعة؟") {
                        load(api.deleteUser(id)) {
                            toast("استجاب الخادم لطلب حذف الحساب")
                            openModule("users")
                        }
                    }
                })
            } else {
                addText(actions, "لا توجد إجراءات إضافية متاحة لرتبتك.", 12f, false, MUTED)
            }
        } else {
            val actions = section("إجراءات الروم", "التغييرات تُرسل إلى الخادم ولا تغيّر منطق الصوت أو اتصال ZEGO.")
            if (currentRole == StaffRoleHelper.SUPER || currentRole == StaffRoleHelper.ADMIN) {
                actions.addView(button("تعديل إعدادات الروم") {
                    showEditRoomDialog(id, roomTitle, accessMode, entryFee)
                })
            }
            if (currentRole == StaffRoleHelper.SUPER || currentRole == StaffRoleHelper.ADMIN) {
                actions.addView(button("إغلاق الروم") {
                    confirm("سيتم إغلاق الروم على الخادم. هذا الإجراء قد يؤثر على الموجودين فيه. هل تريد المتابعة؟") {
                        load(api.closeRoom(id)) {
                            toast("استجاب الخادم لطلب إغلاق الروم")
                            openModule("rooms")
                        }
                    }
                })
            }
            if (currentRole == StaffRoleHelper.ADMIN || currentRole == StaffRoleHelper.MANAGER) {
                addText(actions, "إشراف على هذا الروم فقط: لا يتغير حظر الحساب العام ولا إعدادات الصوت أو ZEGO.", 11f, false, MUTED)
                val targetInput = EditText(this).apply {
                    hint = "UUID المستخدم المراد الإشراف عليه"
                    textSize = 13f
                    setSingleLine(true)
                    setTextColor(TEXT)
                    setHintTextColor(MUTED)
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    textDirection = View.TEXT_DIRECTION_LTR
                    background = roundRect(0xFFF7F6FC.toInt(), 10)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                actions.addView(targetInput, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
                fun targetId(): String? {
                    val target = targetInput.text.toString().trim()
                    if (target.isBlank()) {
                        targetInput.error = "معرّف المستخدم مطلوب"
                        return null
                    }
                    return target
                }
                actions.addView(button("كتم مايك المستخدم داخل الروم") {
                    val target = targetId() ?: return@button
                    load(roomApi.setMic(id, mapOf("userId" to target, "muted" to true))) {
                        toast("تم إرسال طلب كتم المايك لهذا الروم")
                    }
                })
                actions.addView(button("فك كتم المايك داخل الروم") {
                    val target = targetId() ?: return@button
                    load(roomApi.setMic(id, mapOf("userId" to target, "muted" to false))) {
                        toast("تم إرسال طلب فك كتم المايك لهذا الروم")
                    }
                })
                actions.addView(button("إنزال المستخدم من المايك فقط") {
                    val target = targetId() ?: return@button
                    confirm("سيبقى المستخدم داخل الروم وسيُزال من المايك فقط. متابعة؟") {
                        load(roomApi.forceLeaveSeat(id, mapOf("userId" to target, "reason" to "إجراء إشرافي من لوحة JEHO"))) {
                            toast("تم إرسال طلب إنزال المستخدم من المايك")
                        }
                    }
                })
                actions.addView(button("إخراج المستخدم من الروم") {
                    val target = targetId() ?: return@button
                    confirm("سيتم إخراج المستخدم من هذا الروم فقط. متابعة؟") {
                        load(roomApi.kick(id, mapOf("userId" to target, "reason" to "إجراء إشرافي من لوحة JEHO"))) {
                            toast("تم إرسال طلب إخراج المستخدم من الروم")
                        }
                    }
                })
            }
        }

        body.addView(content)
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

    private fun showAssignRechargeAgentDialog() {
        val form = formFields(listOf(
            "معرّف المستخدم (UUID)",
            "رصيد الوكيل الابتدائي بالعملات (اختياري)",
            "العمولة بالنقاط الأساسية BPS (اختياري)",
            "الحد اليومي للعملات (اختياري)",
            "ملاحظات داخلية (اختياري)"
        ))
        AlertDialog.Builder(this).setTitle("إضافة وكيل شحن")
            .setView(form.first).setNegativeButton("إلغاء", null)
            .setPositiveButton("مراجعة وإضافة", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userId = form.second[0].text.toString().trim()
                        val floatCoins = form.second[1].text.toString().trim().toLongOrNull()
                        val commissionBps = form.second[2].text.toString().trim().toIntOrNull()
                        val dailyLimit = form.second[3].text.toString().trim().toLongOrNull()
                        val notes = form.second[4].text.toString().trim()
                        if (userId.isBlank()) {
                            toast("أدخل معرّف المستخدم أولًا")
                            return@setOnClickListener
                        }
                        if ((floatCoins != null && floatCoins < 0) ||
                            (commissionBps != null && commissionBps !in 0..10000) ||
                            (dailyLimit != null && dailyLimit < 0)) {
                            toast("تحقق من الرصيد والعمولة والحد اليومي")
                            return@setOnClickListener
                        }
                        val payload = mutableMapOf<String, Any>("userId" to userId)
                        if (floatCoins != null) payload["floatCoins"] = floatCoins
                        if (commissionBps != null) payload["commissionBps"] = commissionBps
                        if (dailyLimit != null) payload["dailyLimitCoins"] = dailyLimit
                        if (notes.isNotBlank()) payload["notes"] = notes
                        confirm("إضافة المستخدم كوكيل شحن؟ سيتم تطبيق الإعدادات التي أدخلتها.") {
                            load(api.assignRechargeAgent(payload)) {
                                dialog.dismiss()
                                toast("استجاب الخادم لطلب إضافة وكيل الشحن")
                                openModule("rechargeAgents")
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

    private fun showReportDecisionDialog(reportId: String, resolve: Boolean) {
        val noteInput = EditText(this).apply {
            hint = "ملاحظة الإدارة (اختيارية)"
            textSize = 14f
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            gravity = Gravity.TOP or Gravity.START
            minLines = 3
            maxLines = 5
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundRect(0xFFF5F4FA.toInt(), 12)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(TextView(this@AdminConsoleActivity).apply {
                text = if (resolve) "أضف ملاحظة توضح كيف تمت معالجة البلاغ. سيتم تسجيل القرار على الخادم." else "وضّح سبب رفض البلاغ ليسهل الرجوع إلى القرار لاحقاً."
                textSize = 13f
                setTextColor(MUTED)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, 0, 0, dp(10))
            })
            addView(noteInput, LinearLayout.LayoutParams(-1, -2))
        }
        val title = if (resolve) "حسم البلاغ" else "رفض البلاغ"
        AlertDialog.Builder(this).setTitle(title).setView(content)
            .setNegativeButton("إلغاء", null).setPositiveButton("متابعة", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val note = noteInput.text.toString().trim()
                        if (note.length > 300) {
                            toast("اختصر الملاحظة إلى 300 حرف")
                            return@setOnClickListener
                        }
                        val defaultNote = if (resolve) "تمت المراجعة من تطبيق JEHO" else "تمت المراجعة ولا يلزم إجراء"
                        confirm(if (resolve) "تأكيد حسم البلاغ وتسجيل القرار؟" else "تأكيد رفض البلاغ وتسجيل القرار؟") {
                            val payload = mapOf("adminNote" to note.ifBlank { defaultNote })
                            load(if (resolve) api.resolveReport(reportId, payload) else api.dismissReport(reportId, payload)) {
                                dialog.dismiss()
                                toast(if (resolve) "تم حسم البلاغ" else "تم رفض البلاغ")
                                openModule("reports")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showUserAccessDialog(id: String, currentRole: String?, currentStatus: String?) {
        val roleValues = listOf("none", "manager", "admin", "super")
        val roleLabels = listOf("بدون رتبة إدارية", "مانجر", "أدمن", "سوبر أدمن")
        val statusValues = listOf("active", "suspended", "banned")
        val statusLabels = listOf("نشط", "موقوف مؤقتاً", "محظور")
        fun makeSpinner(labels: List<String>, selected: Int): Spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@AdminConsoleActivity, android.R.layout.simple_spinner_dropdown_item, labels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selected.coerceIn(labels.indices))
            background = roundRect(0xFFF5F4FA.toInt(), 10)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        val roleSpinner = makeSpinner(roleLabels, roleValues.indexOf(currentRole?.lowercase()).let { if (it < 0) 0 else it })
        val statusSpinner = makeSpinner(statusLabels, statusValues.indexOf(currentStatus?.lowercase()).let { if (it < 0) 0 else it })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(TextView(this@AdminConsoleActivity).apply {
                text = "الدور الإداري"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
                setPadding(0, 0, 0, dp(6))
            })
            addView(roleSpinner, LinearLayout.LayoutParams(-1, dp(50)))
            addView(TextView(this@AdminConsoleActivity).apply {
                text = "حالة الحساب"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT)
                setPadding(0, dp(14), 0, dp(6))
            })
            addView(statusSpinner, LinearLayout.LayoutParams(-1, dp(50)))
            addView(TextView(this@AdminConsoleActivity).apply {
                text = "اختر الدور والحالة معًا. إذا كان الحساب موقوفًا أو محظورًا، فلن يعمل تسجيل الدخول حتى تختار «نشط» صراحةً. يظل الخادم مسؤولًا عن التحقق من الصلاحيات."
                textSize = 12f
                setTextColor(MUTED)
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(12), 0, 0)
            })
        }
        AlertDialog.Builder(this).setTitle("صلاحيات المستخدم").setView(content)
            .setNegativeButton("إلغاء", null).setPositiveButton("حفظ التغييرات", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val role = roleValues[roleSpinner.selectedItemPosition]
                        val status = statusValues[statusSpinner.selectedItemPosition]
                        val roleLabel = roleLabels[roleSpinner.selectedItemPosition]
                        val statusLabel = statusLabels[statusSpinner.selectedItemPosition]
                        confirm("تأكيد تحديث الحساب إلى: $roleLabel، والحالة: $statusLabel؟") {
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
        val allowedDays = when (currentRole) {
            StaffRoleHelper.MANAGER -> listOf(1, 3, 7)
            StaffRoleHelper.ADMIN -> listOf(1, 3, 7, 15)
            else -> listOf(7, 15, 30)
        }
        val allowPermanent = currentRole == StaffRoleHelper.SUPER
        val durationHint = if (allowPermanent) "المدة: 7 أو 15 أو 30 أو permanent" else "المدة بالأيام: " + allowedDays.joinToString(" أو ")
        val form = formFields(listOf("معرّف المستخدم", durationHint))
        AlertDialog.Builder(this).setTitle("إهداء إطار أو دخولية").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("مراجعة ثم إهداء", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userId = form.second[0].text.toString().trim()
                        val term = form.second[1].text.toString().trim().lowercase()
                        val permanent = term == "permanent" || term == "دائم"
                        val days = term.toIntOrNull()
                        if (userId.isBlank() || (permanent && !allowPermanent) || (!permanent && (days == null || days !in allowedDays))) {
                            toast(if (allowPermanent) "أدخل معرّف المستخدم ومدة 7 أو 15 أو 30 أو permanent" else "المدة المسموحة: " + allowedDays.joinToString(" أو ") + " أيام")
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
        val allowedDays = if (currentRole == StaffRoleHelper.ADMIN) listOf(1, 3, 7, 15) else listOf(7, 15, 30)
        val allowPermanent = currentRole == StaffRoleHelper.SUPER
        val durationHint = if (allowPermanent) "المدة: 7 أو 15 أو 30 أو permanent" else "المدة بالأيام: " + allowedDays.joinToString(" أو ")
        val form = formFields(listOf("معرّف المستخدم", durationHint))
        AlertDialog.Builder(this).setTitle("إهداء الآي دي " + publicId).setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("مراجعة ثم إهداء", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val userId = form.second[0].text.toString().trim()
                        val term = form.second[1].text.toString().trim().lowercase()
                        val permanent = term == "permanent" || term == "دائم"
                        val days = term.toIntOrNull()
                        if (userId.isBlank() || (permanent && !allowPermanent) || (!permanent && days !in allowedDays)) {
                            toast(if (allowPermanent) "أدخل معرّف المستخدم ومدة 7 أو 15 أو 30 أو permanent" else "المدة المسموحة: " + allowedDays.joinToString(" أو ") + " أيام")
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
        val allowedDays = if (currentRole == StaffRoleHelper.ADMIN) listOf(1, 3, 7, 15) else listOf(7, 15, 30)
        val allowPermanent = currentRole == StaffRoleHelper.SUPER
        val durationHint = if (allowPermanent) "المدة: 7 أو 15 أو 30 أو permanent" else "المدة بالأيام: " + allowedDays.joinToString(" أو ")
        val form = formFields(listOf("معرّف المستخدم", "معرّف خطة VIP", durationHint))
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
                            (permanent && !allowPermanent) ||
                            (!permanent && (days == null || days !in allowedDays))) {
                            toast(if (allowPermanent) "أدخل معرّف المستخدم والخطة ومدة 7 أو 15 أو 30 أو permanent" else "المدة المسموحة: " + allowedDays.joinToString(" أو ") + " أيام")
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
        notificationImageUrl = null
        notificationImageUploadInProgress = false
        val form = formFields(listOf(
            "عنوان الرسالة", "محتوى الرسالة",
            "معرّف المستخدم أو Public ID (فارغ = إرسال جماعي)",
            "رابط إضافي HTTPS أو رقم الروم (اختياري)",
            "عنوان الزر الأول (اختياري)", "رابط الزر الأول",
            "عنوان الزر الثاني (اختياري)", "رابط الزر الثاني"
        ))
        form.second[1].setSingleLine(false)
        form.second[1].minLines = 3
        form.second[1].minHeight = dp(92)
        val imageRow = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        imageRow.addView(button("＋ رفع صورة من الجهاز") {
            val pick = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(pick, "اختيار صورة الرسالة"), notificationImageRequestCode)
        })
        notificationImageStatus = TextView(this).apply {
            text = "لا توجد صورة مرفقة"; textSize = 12f; setTextColor(MUTED)
            setPadding(0, dp(6), 0, dp(6))
        }
        imageRow.addView(notificationImageStatus)
        form.first.addView(imageRow, 2)
        AlertDialog.Builder(this).setTitle("إرسال رسالة الإدارة").setView(form.first)
            .setNegativeButton("إلغاء", null).setPositiveButton("معاينة وإرسال", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val title = form.second[0].text.toString().trim()
                        val message = form.second[1].text.toString().trim()
                        val userId = form.second[2].text.toString().trim()
                        val rawLink = form.second[3].text.toString().trim()
                        val b1t = form.second[4].text.toString().trim()
                        val b1u = form.second[5].text.toString().trim()
                        val b2t = form.second[6].text.toString().trim()
                        val b2u = form.second[7].text.toString().trim()
                        val link = if (rawLink.matches(Regex("\\d+"))) "https://jeho-own-backend.mgs.blitz.cloud/open/room/" + rawLink else rawLink
                        if (title.isBlank() || message.isBlank()) { toast("عنوان الرسالة ونصها مطلوبان"); return@setOnClickListener }
                        if (notificationImageUploadInProgress) { toast("انتظر حتى يكتمل رفع الصورة"); return@setOnClickListener }
                        if ((b1t.isBlank() != b1u.isBlank()) || (b2t.isBlank() != b2u.isBlank())) {
                            toast("أدخل عنوان ورابط كل زر معًا"); return@setOnClickListener
                        }
                        if (listOf(link, b1u, b2u).any { it.isNotBlank() && !isSafeMessageLink(it) }) {
                            toast("تحقق من الروابط؛ يجب أن تكون HTTPS أو رابط تطبيق مدعوم"); return@setOnClickListener
                        }
                        val image = notificationImageUrl.orEmpty()
                        val html = buildAdminMessageHtml(message, image, listOf(b1t to b1u, b2t to b2u).filter { it.first.isNotBlank() && it.second.isNotBlank() })
                        val targetText = if (userId.isBlank()) "جميع المستخدمين" else "المستخدم المحدد"
                        confirm("سيتم إرسال الرسالة إلى " + targetText + " مع الصورة والأزرار. تأكيد؟") {
                            val payload = mutableMapOf("title" to title, "body" to message, "type" to "chat", "channel" to "both", "bodyHtml" to html)
                            if (image.isNotBlank()) payload["imageUrl"] = image
                            if (link.isNotBlank()) payload["link"] = link
                            if (userId.isNotBlank()) { payload["userId"] = userId; payload["audience"] = "user" }
                            else payload["audience"] = "all"
                            load(api.sendAdminMessage(payload)) {
                                dialog.dismiss(); toast("تم قبول طلب الإرسال من الخادم؛ تحقّق من وصول الرسالة"); openModule("notifications")
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun isSafeMessageLink(value: String): Boolean {
        val link = value.trim().lowercase()
        return link.startsWith("https://") || link.startsWith("http://") || link.startsWith("/") ||
            link.startsWith("jeho://") || link.startsWith("auralive://") || link.startsWith("uploads/") || link.startsWith("assets/")
    }

    private fun escapeHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

    private fun buildAdminMessageHtml(message: String, imageUrl: String, buttons: List<Pair<String, String>>): String {
        val image = if (imageUrl.isNotBlank()) "<img src=\"" + escapeHtml(imageUrl) + "\" alt=\"صورة الرسالة\" style=\"display:block;width:100%;height:auto;max-height:360px;object-fit:contain;border-radius:14px;margin:12px 0;\" />" else ""
        val ctas = buttons.joinToString("") { pair ->
            "<a href=\"" + escapeHtml(pair.second) + "\" style=\"display:inline-block;margin:6px 4px;padding:11px 16px;border-radius:10px;background:#6245A8;color:#ffffff;text-decoration:none;font-weight:700;\">" + escapeHtml(pair.first) + "</a>"
        }
        val bodyHtml = escapeHtml(message).replace("\n", "<br/>")
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" /></head><body style=\"margin:0;padding:12px;background:#f5f3fb;color:#252744;font-family:sans-serif;direction:rtl;text-align:right;\"><div class=\"admin-message-card\" style=\"background:#ffffff;border:1px solid #e7e2f3;border-radius:16px;padding:14px;\"><div style=\"font-size:15px;line-height:1.7;white-space:pre-wrap;\">" + bodyHtml + "</div>" + image + "<div style=\"margin-top:8px;text-align:center;\">" + ctas + "</div></div></body></html>"
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == notificationImageRequestCode) uploadNotificationImage(uri)
        else if (requestCode == staffIdentityUploadRequestCode) uploadStaffIdentityAsset(uri)
    }

    private fun uploadNotificationImage(uri: Uri) {
        notificationImageUploadInProgress = true
        notificationImageStatus?.text = "جارٍ رفع الصورة..."
        try {
            val mime = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("تعذر قراءة الصورة")
            if (bytes.isEmpty() || bytes.size > 20 * 1024 * 1024) throw IllegalArgumentException("حجم الصورة يجب ألا يتجاوز 20 ميغابايت")
            val ext = when (mime.lowercase()) { "image/png" -> ".png"; "image/webp" -> ".webp"; "image/gif" -> ".gif"; else -> ".jpg" }
            val part = MultipartBody.Part.createFormData("file", "admin-message-" + System.currentTimeMillis() + ext, bytes.toRequestBody(mime.toMediaType()))
            ContainerProvider.from(this).getUploadApi().upload(part).enqueue(object : Callback<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>> {
                override fun onResponse(call: Call<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>>, response: Response<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>>) {
                    notificationImageUploadInProgress = false
                    val result = response.body()?.data
                    if (response.isSuccessful && response.body()?.success == true && !result?.url.isNullOrBlank()) {
                        notificationImageUrl = result!!.url
                        notificationImageStatus?.text = "✓ تم رفع الصورة بنجاح"
                        notificationImageStatus?.setTextColor(0xFF23834B.toInt())
                    } else {
                        notificationImageUrl = null
                        notificationImageStatus?.text = "فشل رفع الصورة؛ جرّب مرة أخرى"
                        notificationImageStatus?.setTextColor(0xFFC0392B.toInt())
                        toast("تعذر رفع الصورة (HTTP " + response.code() + ")")
                    }
                }
                override fun onFailure(call: Call<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>>, error: Throwable) {
                    notificationImageUploadInProgress = false
                    notificationImageUrl = null
                    notificationImageStatus?.text = "فشل رفع الصورة؛ تحقق من الاتصال"
                    toast("تعذر رفع الصورة: " + (error.message ?: "خطأ اتصال"))
                }
            })
        } catch (error: Exception) {
            notificationImageUploadInProgress = false
            notificationImageUrl = null
            notificationImageStatus?.text = "فشل تجهيز الصورة"
            toast(error.message ?: "تعذر تجهيز الصورة")
        }
    }

    private fun showStaffIdentityEditor() {
        staffIdentityFields.clear()
        body.removeAllViews()
        addText(body, "هوية الرتب الإدارية", 20f, true, TEXT)
        addText(body, "ارفع الأصول من الجهاز أو أدخل رابط HTTPS. تُحفظ الإعدادات في قاعدة البيانات، والملفات تعتمد على تخزين Supabase الدائم إذا كان مفعّلًا على الخادم.", 13f, false, MUTED)
        load(api.staffIdentity()) { raw ->
            val root = raw as? Map<*, *> ?: emptyMap<Any, Any>()
            val roles = listOf(
                Triple("super", "السوبر أدمن", 0xFFD8B35B.toInt()),
                Triple("admin", "الأدمن", 0xFF9AA8C7.toInt()),
                Triple("manager", "المانجر", 0xFF37A889.toInt())
            )
            val assetFields = listOf(
                "badgeUrl" to "رابط الشارة",
                "frameUrl" to "رابط إطار الصورة",
                "nameplateUrl" to "رابط بطاقة الاسم",
                "messageCardUrl" to "رابط بطاقة الرسائل",
                "entryEffectUrl" to "رابط الدخولية"
            )
            roles.forEach { (roleKey, roleTitle, color) ->
                val roleData = root[roleKey] as? Map<*, *> ?: emptyMap<Any, Any>()
                val panel = card()
                val heading = TextView(this).apply {
                    text = roleTitle
                    textSize = 17f
                    setTextColor(color)
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, 0, 0, dp(8))
                }
                panel.addView(heading)
                val labelKey = "$roleKey.label"
                val labelInput = identityInput("اسم الرتبة", roleData["label"]?.toString().orEmpty())
                staffIdentityFields[labelKey] = labelInput
                panel.addView(labelInput)
                val colorKey = "$roleKey.accentColor"
                val colorInput = identityInput("لون الهوية HEX مثال #D8B35B", roleData["accentColor"]?.toString().orEmpty())
                staffIdentityFields[colorKey] = colorInput
                panel.addView(colorInput)
                assetFields.forEach { (assetKey, assetLabel) ->
                    val fieldKey = "$roleKey.$assetKey"
                    val input = identityInput(assetLabel, roleData[assetKey]?.toString().orEmpty())
                    staffIdentityFields[fieldKey] = input
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
                    row.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
                    row.addView(button("رفع") {
                        pendingIdentityFieldKey = fieldKey
                        val pick = Intent(Intent.ACTION_GET_CONTENT).apply { type = if (assetKey == "entryEffectUrl") "*/*" else "image/*" }
                        startActivityForResult(Intent.createChooser(pick, "اختيار $assetLabel"), staffIdentityUploadRequestCode)
                    }, LinearLayout.LayoutParams(dp(76), dp(48)))
                    panel.addView(row)
                }
                addIdentityAssetPreview(panel, roleKey)
                body.addView(panel)
            }
            body.addView(button("حفظ الهوية الإدارية") {
                if (staffIdentityUploadInProgress) { toast("انتظر حتى ينتهي رفع الملف"); return@button }
                val payload = mutableMapOf<String, Any>()
                roles.forEach { (roleKey, _, _) ->
                    val values = mutableMapOf<String, Any>()
                    listOf("label", "accentColor", "badgeUrl", "frameUrl", "nameplateUrl", "messageCardUrl", "entryEffectUrl").forEach { key ->
                        values[key] = staffIdentityFields["$roleKey.$key"]?.text?.toString()?.trim().orEmpty()
                    }
                    payload[roleKey] = values
                }
                confirm("حفظ هويات السوبر أدمن والأدمن والمانجر؟") {
                    load(api.updateStaffIdentity(payload)) {
                        StaffIdentityConfigCache.refresh(this) {
                            toast("تم حفظ الهوية وتحديث إطارات الرتب في التطبيق")
                            showStaffIdentityEditor()
                        }
                    }
                }
            })
        }
    }

    private fun addIdentityAssetPreview(parent: LinearLayout, roleKey: String) {
        addText(parent, "معاينة الأصول الحية", 12f, true, ACCENT)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundRect(0xFFF8F7FC.toInt(), 12)
        }
        val specs = listOf(
            Triple("badgeUrl", "الشارة", "الشارة"),
            Triple("frameUrl", "الإطار", "إطار الصورة"),
            Triple("nameplateUrl", "الاسم", "بطاقة الاسم"),
            Triple("messageCardUrl", "الرسائل", "بطاقة الرسائل")
        )
        specs.forEach { (fieldKey, label, description) ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "$description لرتبة $roleKey"
            }
            column.addView(image, LinearLayout.LayoutParams(dp(62), dp(62)))
            val caption = TextView(this).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(MUTED)
            }
            column.addView(caption)
            row.addView(column)
            val input = staffIdentityFields["$roleKey.$fieldKey"]
            fun refresh() {
                val raw = input?.text?.toString()?.trim().orEmpty()
                if (raw.isBlank()) {
                    Glide.with(this@AdminConsoleActivity).clear(image)
                    image.setImageDrawable(null)
                } else {
                    val resolved = AssetCatalog.absoluteUrl(raw) ?: raw
                    Glide.with(this@AdminConsoleActivity).load(resolved).fitCenter().into(image)
                }
            }
            input?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refresh() }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            refresh()
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        })
    }

    private fun identityInput(hintText: String, value: String): EditText = EditText(this).apply {
        hint = hintText
        setText(value)
        textSize = 12f
        setSingleLine(true)
        setTextColor(TEXT)
        setHintTextColor(MUTED)
        background = roundRect(0xFFF5F4FA.toInt(), 10)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(5) }
    }

    private fun uploadStaffIdentityAsset(uri: Uri) {
        val key = pendingIdentityFieldKey ?: return
        staffIdentityUploadInProgress = true
        try {
            val mime = contentResolver.getType(uri) ?: "image/png"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("تعذر قراءة الملف")
            if (bytes.isEmpty() || bytes.size > 20 * 1024 * 1024) throw IllegalArgumentException("حجم الملف يجب ألا يتجاوز 20 ميغابايت")
            val ext = when (mime.lowercase()) {
                "image/png" -> ".png"
                "image/webp" -> ".webp"
                "image/gif" -> ".gif"
                "video/mp4" -> ".mp4"
                "application/octet-stream" -> ".svga"
                else -> ".jpg"
            }
            val part = MultipartBody.Part.createFormData("file", "staff-identity-" + key.replace(".", "-") + ext, bytes.toRequestBody(mime.toMediaType()))
            ContainerProvider.from(this).getUploadApi().upload(part).enqueue(object : Callback<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>> {
                override fun onResponse(call: Call<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>>, response: Response<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>>) {
                    staffIdentityUploadInProgress = false
                    val result = response.body()?.data
                    val input = staffIdentityFields[key]
                    if (response.isSuccessful && response.body()?.success == true && !result?.url.isNullOrBlank() && input != null) {
                        input.setText(result!!.url)
                        toast("تم رفع الأصل؛ احفظ الإعدادات لاعتماد التغيير")
                    } else {
                        toast("تعذر رفع الأصل (HTTP " + response.code() + ")")
                    }
                }
                override fun onFailure(call: Call<ApiResponse<com.Dramizo.Series.data.remote.api.UploadApi.UploadResult>>, error: Throwable) {
                    staffIdentityUploadInProgress = false
                    toast("تعذر رفع الأصل: " + (error.message ?: "خطأ اتصال"))
                }
            })
        } catch (error: Exception) {
            staffIdentityUploadInProgress = false
            toast(error.message ?: "تعذر تجهيز الملف")
        }
    }

    private fun formFields(labels: List<String>): Pair<LinearLayout, List<EditText>> {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val inputs = labels.map { label ->
            val input = EditText(this).apply {
                hint = label
                textSize = 15f
                layoutDirection = View.LAYOUT_DIRECTION_RTL
                textDirection = View.TEXT_DIRECTION_RTL
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
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

    private fun statusChip(rawStatus: String): View {
        val normalized = rawStatus.trim().lowercase(java.util.Locale.ROOT)
        val label = when (normalized) {
            "active", "enabled", "true", "approved", "resolved", "completed", "success" -> "نشط"
            "inactive", "disabled", "false" -> "غير نشط"
            "pending", "waiting", "open" -> "قيد المراجعة"
            "rejected", "dismissed", "failed" -> "مرفوض"
            "suspended", "banned", "blocked", "closed" -> "موقوف"
            "live", "streaming" -> "مباشر"
            "public" -> "عام"
            "private" -> "خاص"
            else -> rawStatus
        }
        val tone = when (normalized) {
            "active", "enabled", "true", "approved", "resolved", "completed", "success", "live", "streaming" -> 0xFF16794B.toInt()
            "pending", "waiting", "open" -> 0xFF996515.toInt()
            "rejected", "dismissed", "failed", "suspended", "banned", "blocked", "closed", "inactive", "disabled", "false" -> 0xFFB42335.toInt()
            else -> ACCENT
        }
        return TextView(this).apply {
            text = label
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(tone)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                setColor(when (tone) {
                    0xFF16794B.toInt() -> 0xFFE8F7EF.toInt()
                    0xFF996515.toInt() -> 0xFFFFF4D8.toInt()
                    0xFFB42335.toInt() -> 0xFFFFEBED.toInt()
                    else -> 0xFFF0ECFB.toInt()
                })
                cornerRadius = dp(30).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5); bottomMargin = dp(4) }
        }
    }

    private fun prettyKey(key: String): String = when (key) {
        "users", "usersCount", "totalUsers" -> "المستخدمون"
        "rooms", "roomsCount", "totalRooms" -> "الرومات"
        "activeStreams", "streamsCount" -> "البث المباشر"
        "agencies", "agenciesCount" -> "الوكالات"
        "createdAt" -> "تاريخ الإنشاء"
        "lastSeenAt" -> "آخر ظهور"
        "vipExpiresAt" -> "انتهاء VIP"
        "membersCount" -> "عدد الأعضاء"
        "username" -> "اسم المستخدم"
        "role", "staffRole" -> "الدور"
        "coins", "balance" -> "رصيد العملات"
        "diamonds" -> "رصيد الألماس"
        "level" -> "المستوى"
        "coinPrice" -> "السعر"
        "diamondValue" -> "قيمة الألماس"
        "active" -> "نشط"
        "inactive" -> "غير نشط"
        "pending" -> "قيد المراجعة"
        "approved" -> "مقبول"
        "rejected" -> "مرفوض"
        "suspended" -> "موقوف مؤقتًا"
        "banned", "blocked" -> "محظور"
        "status" -> "الحالة"
        "id", "userId" -> "المعرّف"
        "roomId" -> "معرّف الروم"
        "name", "title" -> "الاسم"
        "description" -> "الوصف"
        "message" -> "الرسالة"
        "type" -> "النوع"
        "amount" -> "المبلغ"
        "duration" -> "المدة"
        "expiresAt" -> "تاريخ الانتهاء"
        "updatedAt" -> "تاريخ التحديث"
        "ownerId" -> "معرّف المالك"
        "ownerName" -> "اسم المالك"
        "isActive" -> "حالة التفعيل"
        "isVerified" -> "موثّق"
        "country" -> "الدولة"
        "phone" -> "رقم الهاتف"
        "email" -> "البريد الإلكتروني"
        "reason" -> "السبب"
        "adminNote" -> "ملاحظة الإدارة"
        "reporterId" -> "معرّف المُبلّغ"
        "targetId" -> "معرّف المُبلّغ عنه"
        "category" -> "التصنيف"
        "count" -> "العدد"
        "total" -> "الإجمالي"
        "page" -> "الصفحة"
        "limit" -> "عدد النتائج"
        else -> key.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .let { raw -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
    }

    private fun copyToClipboard(label: String, value: String) {
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
        toast("تم نسخ $label")
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
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), 0xFFE8E7F2.toInt())
        }
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) }
    }

    private fun addText(parent: LinearLayout, text: String, size: Float, bold: Boolean, color: Int) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            textDirection = View.TEXT_DIRECTION_RTL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(3))
        }
        parent.addView(view)
    }

    /**
     * Shared JEHO-native action button. One visual language across admin
     * sections, with RTL layout, rounded surface and visible press feedback.
     */
    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        gravity = Gravity.CENTER
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = 0
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        val shape = GradientDrawable().apply {
            setColor(ACCENT)
            cornerRadius = dp(15).toFloat()
            setStroke(dp(1), 0xFF8069D0.toInt())
        }
        val pressed = GradientDrawable().apply {
            setColor(0xFF4F369D.toInt())
            cornerRadius = dp(15).toFloat()
        }
        background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFFFFF),
            shape,
            pressed
        )
        backgroundTintList = null
        elevation = dp(2).toFloat()
        stateListAnimator = null
        setPadding(dp(16), dp(8), dp(16), dp(8))
        contentDescription = label
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
        private const val staffIdentityUploadRequestCode = 7043
    }
}
