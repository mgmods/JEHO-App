package com.Dramizo.Series.presentation.main

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.Dramizo.Series.R
import com.Dramizo.Series.data.remote.dto.DramaDtos
import com.Dramizo.Series.data.remote.dto.MiscDtos
import com.Dramizo.Series.databinding.ActivityMainBinding
import com.Dramizo.Series.di.AppContainer
import com.Dramizo.Series.domain.model.Result
import com.Dramizo.Series.presentation.common.ContainerProvider
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper
import com.Dramizo.Series.presentation.common.ThemedActivity
import com.Dramizo.Series.presentation.drama.DramaFragment
import com.Dramizo.Series.presentation.games.GamesFragment
import com.Dramizo.Series.presentation.home.HomeFragment
import com.Dramizo.Series.presentation.messages.MessagesFragment
import com.Dramizo.Series.presentation.profile.ProfileFragment
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomActivity
import com.Dramizo.Series.realtime.RealtimeClient
import com.Dramizo.Series.rtc.RoomRtcEngine
import com.Dramizo.Series.service.VoiceRoomForegroundService
import com.Dramizo.Series.util.ApiCall
import com.Dramizo.Series.util.AppFeatures
import com.Dramizo.Series.util.AppLoadingOverlay
import com.Dramizo.Series.util.AssetCatalog
import com.Dramizo.Series.util.AssetIcons
import com.Dramizo.Series.util.AuraDialogHelper
import com.Dramizo.Series.util.GenderVerificationGate
import com.Dramizo.Series.util.InviteReferralHelper
import com.Dramizo.Series.util.LocalEngagementScheduler
import com.Dramizo.Series.util.PlayInAppUpdateHelper
import com.Dramizo.Series.util.RemoteNavIcons
import com.Dramizo.Series.util.RemoteTheme
import com.Dramizo.Series.util.RoomJoinPrefetch
import com.Dramizo.Series.util.RoomOpenChooser
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.JsonObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Main shell (bottom tabs + ViewPager2). Ported to Kotlin for cleaner navigation
 * and incremental Java/Kotlin interop — same behavior as the previous Java version.
 *
 * Tabs: 0 Home · 1 Drama · 2 Games · 3 Messages · 4 Profile
 */
class MainActivity : ThemedActivity() {

    companion object {
        const val EXTRA_OPEN_CREATE_ROOM = "open_create_room"
        const val EXTRA_OPEN_HOME = "open_home"
        const val EXTRA_OPEN_MESSAGES = "open_messages"
        const val EXTRA_OPEN_DRAMA = "open_drama"
        private const val FLOATING_PREFS = "home_floating_widgets"
    }

    private var binding: ActivityMainBinding? = null
    private var mainPager: ViewPager2? = null
    private var currentPage: Int = 0
    private var dramaEnabled: Boolean = true
    private var agencyMine: MiscDtos.AgencyMineDto? = null
    private var agencyStateLoaded: Boolean = false
    private var activeRoomDragConfigured: Boolean = false
    private var taskInviteListener: RealtimeClient.RoomListener? = null
    private var playInAppUpdateHelper: PlayInAppUpdateHelper? = null

    /** First open of each tab may show a short loader; revisits stay stable. */
    private val pageWarmed = BooleanArray(5)

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            syncFcmToken()
            if (granted) {
                LocalEngagementScheduler.rescheduleNow(this)
            } else {
                Toast.makeText(
                    this,
                    "فعّل الإشعارات من الإعدادات لاستلام تنبيهات المهام والرومات",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.apply(this)

        val b = ActivityMainBinding.inflate(layoutInflater)
        binding = b
        setContentView(b.root)

        playInAppUpdateHelper = PlayInAppUpdateHelper(this)
        RemoteTheme.applyActivityBackground(this, "home")
        EdgeToEdgeHelper.padBottom(b.customBottomBar)

        val container = ContainerProvider.from(this)
        // Never block first paint on feature flags — fetch off the main thread.
        container.ioExecutor.execute { AppFeatures.refresh(container) }
        if (GenderVerificationGate.needsVerification(
                container.sessionManager.user,
                container.sessionManager,
            )
        ) {
            startActivity(GenderVerificationGate.blockingIntent(this))
            finish()
            return
        }

        applyNavIcons()
        RemoteNavIcons.hydrateFromCache(container)
        applyNavIcons()
        applyDynamicBottomNavigation()
        container.ioExecutor.execute {
            RemoteNavIcons.refreshBlocking(container)
            runOnUiThread {
                if (binding == null) return@runOnUiThread
                applyDynamicBottomNavigation()
                highlightPage(currentPage)
            }
        }
        RemoteTheme.applyActivityBackground(this, "home")
        b.labelCreateRoom.setText(R.string.my_room)

        container.sessionManager.accessToken?.let { RealtimeClient.getInstance().connect(it) }
        attachTaskInviteListener(container)

        requestNotificationsPermission()
        syncFcmToken()
        setupMainPager()

        b.tabPartyWrap.setOnClickListener { go(R.id.nav_home) }
        b.tabDramaWrap.setOnClickListener { go(R.id.nav_drama) }
        b.tabGamesWrap.setOnClickListener { go(R.id.nav_games) }
        b.tabChatWrap.setOnClickListener { go(R.id.nav_messages) }
        b.tabMeWrap.setOnClickListener { go(R.id.nav_profile) }
        b.tabParty.setOnClickListener { go(R.id.nav_home) }
        b.tabDrama.setOnClickListener { go(R.id.nav_drama) }
        b.tabGames.setOnClickListener { go(R.id.nav_games) }
        b.tabChat.setOnClickListener { go(R.id.nav_messages) }
        b.tabMe.setOnClickListener { go(R.id.nav_profile) }
        b.tabCreateRoomWrap.setOnClickListener { handleAgencyAction() }
        b.tabCreateRoom.setOnClickListener { b.tabCreateRoomWrap.performClick() }

        loadAgencyAction(container)
        highlightPage(0)
        loadDramaConfig(container)

        intent?.let { openFromIntentExtras(it) }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (mainPager != null && mainPager!!.currentItem != 0) {
                        go(R.id.nav_home)
                        return
                    }
                    finish()
                }
            },
        )

        b.root.postDelayed({
            if (isFinishing || playInAppUpdateHelper == null) return@postDelayed
            playInAppUpdateHelper?.checkForUpdate()
        }, 1500L)

        maybeOpenPendingInvite(intent)
        maybeOpenPendingRoom(intent)
        b.root.postDelayed({
            if (isFinishing) return@postDelayed
            if (InviteReferralHelper.shouldAutoOpenInvite(this)) {
                val synthetic = Intent().putExtra(InviteReferralHelper.EXTRA_OPEN_INVITE, true)
                maybeOpenPendingInvite(synthetic)
            }
            maybeOpenPendingRoom(null)
        }, 2200L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntentExtras(intent)
        maybeOpenPendingInvite(intent)
        maybeOpenPendingRoom(intent)
    }

    private fun openFromIntentExtras(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_CREATE_ROOM, false)) {
            intent.removeExtra(EXTRA_OPEN_CREATE_ROOM)
            RoomOpenChooser.open(this)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_HOME, false)) {
            intent.removeExtra(EXTRA_OPEN_HOME)
            go(R.id.nav_home)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_MESSAGES, false)) {
            intent.removeExtra(EXTRA_OPEN_MESSAGES)
            go(R.id.nav_messages)
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_DRAMA, false)) {
            intent.removeExtra(EXTRA_OPEN_DRAMA)
            go(R.id.nav_drama)
        }
    }

    private fun maybeOpenPendingRoom(intent: Intent?) {
        var roomId = intent?.getStringExtra("pending_room_id")
        if (roomId.isNullOrEmpty()) {
            roomId = InviteReferralHelper.takePendingRoom(this)
        } else {
            intent?.removeExtra("pending_room_id")
            InviteReferralHelper.takePendingRoom(this)
        }
        if (roomId.isNullOrEmpty()) return
        RoomJoinPrefetch.begin(this, roomId, null)
        startActivity(
            Intent(this, VoiceRoomActivity::class.java)
                .putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, roomId)
                .putExtra(VoiceRoomActivity.EXTRA_IS_HOST, false),
        )
    }

    private fun maybeOpenPendingInvite(intent: Intent?) {
        val openFlag = intent?.getBooleanExtra(InviteReferralHelper.EXTRA_OPEN_INVITE, false) == true
        val auto = InviteReferralHelper.shouldAutoOpenInvite(this)
        if (!openFlag && !auto) return

        var code = intent?.getStringExtra(InviteReferralHelper.EXTRA_PENDING_INVITE)
        if (code.isNullOrEmpty()) {
            code = InviteReferralHelper.peekPendingCode(this)
        }
        intent?.removeExtra(InviteReferralHelper.EXTRA_OPEN_INVITE)
        intent?.removeExtra(InviteReferralHelper.EXTRA_PENDING_INVITE)
        InviteReferralHelper.markInvitePrompted(this)
        val bindCode = code
        val b = binding ?: return
        b.root.postDelayed({
            if (isFinishing) return@postDelayed
            val i = Intent(this, com.Dramizo.Series.presentation.invite.InvitationActivity::class.java)
            if (!bindCode.isNullOrEmpty()) {
                i.putExtra(com.Dramizo.Series.presentation.invite.InvitationActivity.EXTRA_CODE, bindCode)
            }
            i.putExtra(com.Dramizo.Series.presentation.invite.InvitationActivity.EXTRA_AUTO_BIND, true)
            startActivity(i)
        }, 700L)
    }

    private fun requestNotificationsPermission() {
        LocalEngagementScheduler.ensureScheduled(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                Toast.makeText(
                    this,
                    "فعّل الإشعارات من الإعدادات لاستلام تنبيهات المهام والرومات",
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun syncFcmToken() {
        val c = ContainerProvider.from(this)
        if (c.sessionManager.accessToken == null) return
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { fcm ->
                c.ioExecutor.execute {
                    c.notificationRepository.registerDevice(fcm, "android")
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun go(destId: Int) {
        val page = destToPage(destId)
        val pager = mainPager
        if (page < 0 || pager == null) return
        if (page == 1 && !dramaEnabled) return
        if (pager.currentItem == page) return
        if (destId != R.id.nav_create_room && page < pageWarmed.size && !pageWarmed[page]) {
            AppLoadingOverlay.showUntilReady(this)
        } else {
            AppLoadingOverlay.hide(this)
        }
        pager.setCurrentItem(page, true)
    }

    private fun setupMainPager() {
        val b = binding ?: return
        val pager = b.mainPager
        mainPager = pager
        // Bottom tabs are tap-only. Home sub-tabs (حار / دولة / نشاطات) are also tap-only
        // — see HomeFragment.pagerFeed.setUserInputEnabled(false).
        pager.isUserInputEnabled = false
        // Keep only neighbors warm — avoids freeze on first install.
        pager.offscreenPageLimit = 1
        pager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment = when (position) {
                1 -> DramaFragment()
                2 -> GamesFragment()
                3 -> MessagesFragment()
                4 -> ProfileFragment()
                else -> HomeFragment()
            }

            override fun getItemCount(): Int = 5
        }
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPage = position
                    if (position in pageWarmed.indices) pageWarmed[position] = true
                    highlightPage(position)
                    RemoteTheme.applyActivityBackground(this@MainActivity, pageScreenKey(position))
                    AppLoadingOverlay.hide(this@MainActivity)
                }
            },
        )
    }

    private fun destToPage(destId: Int): Int = when (destId) {
        R.id.nav_home -> 0
        R.id.nav_drama -> 1
        R.id.nav_games -> 2
        R.id.nav_messages -> 3
        R.id.nav_profile -> 4
        else -> -1
    }

    private fun highlightPage(page: Int) {
        val b = binding ?: return
        highlightTab(
            page == 0, b.tabParty, b.labelParty,
            RemoteNavIcons.TAB_PARTY,
            AssetIcons.TAB_PARTY_NORMAL, AssetIcons.TAB_PARTY_SELECTED,
        )
        highlightTab(
            page == 1, b.tabDrama, b.labelDrama,
            RemoteNavIcons.TAB_DRAMA,
            AssetIcons.TAB_DRAMA_NORMAL, AssetIcons.TAB_DRAMA_SELECTED,
        )
        highlightTab(
            page == 2, b.tabGames, b.labelGames,
            RemoteNavIcons.TAB_GAMES,
            AssetIcons.TAB_GAME_NORMAL, AssetIcons.TAB_GAME_SELECTED,
        )
        highlight(false, b.tabCreateRoom, b.labelCreateRoom)
        highlightTab(
            page == 3, b.tabChat, b.labelChat,
            RemoteNavIcons.TAB_CHAT,
            AssetIcons.TAB_CHAT_NORMAL, AssetIcons.TAB_CHAT_SELECTED,
        )
        highlightTab(
            page == 4, b.tabMe, b.labelMe,
            RemoteNavIcons.TAB_ME,
            AssetIcons.TAB_ME_NORMAL, AssetIcons.TAB_ME_SELECTED,
        )
    }

    private fun pageScreenKey(page: Int): String = when (page) {
        1 -> "drama"
        2 -> "game"
        3 -> "chat"
        4 -> "profile"
        else -> "home"
    }

    private fun handleAgencyAction() {
        RoomOpenChooser.open(this)
    }

    private fun loadAgencyAction(container: AppContainer?) {
        if (container == null) return
        container.ioExecutor.execute {
            val result: Result<MiscDtos.AgencyMineDto> = container.agencyRepository.mine()
            runOnUiThread {
                if (binding == null) return@runOnUiThread
                agencyStateLoaded = true
                agencyMine = if (result.success) result.data else null
                bindAgencyAction()
            }
        }
    }

    private fun bindAgencyAction() {
        val b = binding ?: return
        var label = R.string.my_room
        val mine = agencyMine
        if (mine != null && mine.isEligibleHost) {
            label = R.string.my_room
        } else if (mine?.application != null) {
            val status = mine.application.status
            if (status == null || status.equals("pending", ignoreCase = true)) {
                label = R.string.agency_under_review
            }
        }
        b.tabCreateRoomWrap.visibility = View.GONE
        b.labelCreateRoom.setText(label)
        b.tabCreateRoom.contentDescription = getString(label)
    }

    private fun applyNavIcons() {
        val b = binding ?: return
        RemoteNavIcons.bind(
            b.tabParty, RemoteNavIcons.TAB_PARTY, false,
            AssetIcons.TAB_PARTY_NORMAL, AssetIcons.TAB_PARTY_SELECTED,
        )
        RemoteNavIcons.bind(
            b.tabDrama, RemoteNavIcons.TAB_DRAMA, false,
            AssetIcons.TAB_DRAMA_NORMAL, AssetIcons.TAB_DRAMA_SELECTED,
        )
        RemoteNavIcons.bind(
            b.tabGames, RemoteNavIcons.TAB_GAMES, false,
            AssetIcons.TAB_GAME_NORMAL, AssetIcons.TAB_GAME_SELECTED,
        )
        RemoteNavIcons.bind(
            b.tabChat, RemoteNavIcons.TAB_CHAT, false,
            AssetIcons.TAB_CHAT_NORMAL, AssetIcons.TAB_CHAT_SELECTED,
        )
        RemoteNavIcons.bind(
            b.tabMe, RemoteNavIcons.TAB_ME, false,
            AssetIcons.TAB_ME_NORMAL, AssetIcons.TAB_ME_SELECTED,
        )
        clearNavIconTint(b.tabCreateRoom)
    }

    /**
     * Dashboard-driven bottom navigation.
     *
     * The visual chrome stays native/consistent, while the server controls
     * label, icon, order, visibility and destination. Unknown/legacy config
     * safely falls back to the three core tabs.
     */
    private fun applyDynamicBottomNavigation() {
        val b = binding ?: return
        val cfg = RemoteNavIcons.get() ?: return
        val entries = listOf(
            "party" to b.tabPartyWrap,
            "drama" to b.tabDramaWrap,
            "games" to b.tabGamesWrap,
            "chat" to b.tabChatWrap,
            "me" to b.tabMeWrap,
        )
        fun pair(key: String): MiscDtos.NavIconPairDto? = when (key) {
            "party" -> cfg.party
            "drama" -> cfg.drama
            "games" -> cfg.games
            "chat" -> cfg.chat
            "me" -> cfg.me
            else -> null
        }
        fun destination(route: String?): Int = when (route?.lowercase()) {
            "home", "party", "main" -> R.id.nav_home
            "messages", "chat" -> R.id.nav_messages
            "profile", "me", "account" -> R.id.nav_profile
            "drama" -> R.id.nav_drama
            "games", "game" -> R.id.nav_games
            else -> R.id.nav_home
        }

        val visible = entries
            .map { (key, view) -> Triple(key, view, pair(key)) }
            .filter { (_, _, p) -> p?.enabled != false }
            .sortedBy { (_, _, p) -> p?.sortOrder ?: 99 }

        // Keep only configured tabs in the bottom chrome; hidden tabs remain
        // available through their existing routes if another feature opens them.
        for ((_, view) in entries) {
            view.visibility = View.GONE
            b.customBottomBar.removeView(view)
        }
        for ((key, view, p) in visible) {
            view.visibility = View.VISIBLE
            b.customBottomBar.addView(view)
            val label = when {
                !p?.label.isNullOrBlank() -> p?.label
                key == "party" -> getString(R.string.tab_party)
                key == "chat" -> getString(R.string.nav_messages)
                key == "me" -> getString(R.string.nav_profile)
                else -> view.contentDescription
            }
            when (key) {
                "party" -> b.labelParty.text = label
                "drama" -> b.labelDrama.text = label
                "games" -> b.labelGames.text = label
                "chat" -> b.labelChat.text = label
                "me" -> b.labelMe.text = label
            }
            val dest = destination(p?.route)
            view.setOnClickListener { go(dest) }
            view.findViewById<View>(view.id)?.setOnClickListener { go(dest) }
        }
        b.customBottomBar.requestLayout()
    }

    private fun clearNavIconTint(icon: ImageView?) {
        if (icon == null) return
        icon.clearColorFilter()
        icon.imageTintList = null
    }

    /**
     * Drama tab show/hide comes only from dashboard drama config.enabled.
     */
    private fun loadDramaConfig(container: AppContainer) {
        container.ioExecutor.execute {
            val r: Result<DramaDtos.DramaConfigDto> =
                ApiCall.execute(container.dramaApi.config())
            runOnUiThread {
                val b = binding ?: return@runOnUiThread
                val show = r.success && r.data != null && r.data.enabled
                dramaEnabled = show
                b.tabDramaWrap.visibility = if (show) View.VISIBLE else View.GONE
                if (show) {
                    RemoteNavIcons.bind(
                        b.tabDrama, RemoteNavIcons.TAB_DRAMA, currentPage == 1,
                        AssetIcons.TAB_DRAMA_NORMAL, AssetIcons.TAB_DRAMA_SELECTED,
                    )
                }
                if (!show && mainPager?.currentItem == 1) {
                    mainPager?.setCurrentItem(0, false)
                }
            }
        }
    }

    private fun currentScreenKey(): String =
        pageScreenKey(mainPager?.currentItem ?: currentPage)

    override fun inferScreenKey(): String = currentScreenKey()

    override fun onResume() {
        super.onResume()
        val container = ContainerProvider.from(this)
        loadAgencyAction(container)
        container.ioExecutor.execute { AppFeatures.refresh(container) }
        bindActiveRoomMini()
        playInAppUpdateHelper?.onResume()
    }

    private fun bindActiveRoomMini() {
        val b = binding ?: return
        val mini = b.activeRoomMini ?: return
        val activeRoomId = VoiceRoomForegroundService.activeRoomId(this)
        val visible = !activeRoomId.isNullOrEmpty()
        val miniRoot = mini.root
        miniRoot.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            mini.imgMiniRoomCover?.clearAnimation()
            return
        }
        if (!activeRoomDragConfigured) {
            activeRoomDragConfigured = true
            enableFloatingDrag(miniRoot, "active_room")
        } else {
            val prefs = getSharedPreferences(FLOATING_PREFS, MODE_PRIVATE)
            miniRoot.post { restoreFloatingPosition(miniRoot, "active_room", prefs) }
        }
        miniRoot.bringToFront()
        val title = VoiceRoomForegroundService.activeRoomTitle(this)
        mini.tvMiniRoomTitle.text =
            if (!title.isNullOrEmpty()) title else getString(R.string.voice_room)
        Glide.with(this)
            .load(AssetCatalog.absoluteUrl(VoiceRoomForegroundService.activeRoomCover(this)))
            .placeholder(R.drawable.placeholder_cover)
            .error(R.drawable.placeholder_cover)
            .circleCrop()
            .into(mini.imgMiniRoomCover)
        mini.imgMiniRoomCover.clipToOutline = true
        mini.imgMiniRoomCover.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, max(1, view.width), max(1, view.height))
            }
        }
        val ring = miniRoot.findViewById<View>(R.id.miniRoomRing)
        if (ring != null) {
            ring.clipToOutline = true
            ring.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, max(1, view.width), max(1, view.height))
                }
            }
            ring.isClickable = false
            ring.isFocusable = false
            ring.setOnClickListener(null)
        }
        miniRoot.setOnClickListener {
            startActivity(
                Intent(this, VoiceRoomActivity::class.java)
                    .putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, activeRoomId)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        val spin = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f,
        ).apply {
            duration = 4200
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        mini.imgMiniRoomCover.clearAnimation()
        mini.imgMiniRoomCover.startAnimation(spin)

        mini.btnMiniRoomClose.bringToFront()
        mini.btnMiniRoomClose.setOnClickListener {
            VoiceRoomForegroundService.leaveActiveRoom(this)
            mini.imgMiniRoomCover.clearAnimation()
            miniRoot.visibility = View.GONE
            Toast.makeText(this, R.string.left_room, Toast.LENGTH_SHORT).show()
        }

        val micBtn = mini.btnMiniRoomMic
        micBtn.bringToFront()
        syncMiniRoomMicUi(micBtn)
        micBtn.setOnClickListener { toggleMiniRoomMic(activeRoomId, micBtn) }
    }

    private fun syncMiniRoomMicUi(micBtn: ImageView?) {
        if (micBtn == null) return
        val micOn = RoomRtcEngine.getInstance().isMicEnabled
        micBtn.setImageResource(
            if (micOn) R.drawable.ic_asset_mic_open else R.drawable.ic_asset_mic_close,
        )
        micBtn.setBackgroundResource(
            if (micOn) R.drawable.bg_mini_room_mic else R.drawable.bg_mini_room_mic_off,
        )
        micBtn.contentDescription = if (micOn) {
            getString(R.string.mini_mic_mute_desc)
        } else {
            getString(R.string.mini_mic_unmute_desc)
        }
    }

    private fun toggleMiniRoomMic(activeRoomId: String?, micBtn: ImageView) {
        val nextOn = !RoomRtcEngine.getInstance().isMicEnabled
        RoomRtcEngine.getInstance().setMicEnabled(nextOn)
        syncMiniRoomMicUi(micBtn)
        Toast.makeText(
            this,
            if (nextOn) R.string.mini_mic_enabled_toast else R.string.mini_mic_muted_toast,
            Toast.LENGTH_SHORT,
        ).show()
        if (activeRoomId.isNullOrEmpty()) return
        val c = ContainerProvider.from(this)
        val muted = !nextOn
        c.ioExecutor.execute { c.roomRepository.setMic(activeRoomId, muted) }
    }

    private fun enableFloatingDrag(floatingView: View, preferenceKey: String) {
        val prefs = getSharedPreferences(FLOATING_PREFS, MODE_PRIVATE)
        floatingView.isClickable = true
        floatingView.isFocusable = true
        floatingView.post { restoreFloatingPosition(floatingView, preferenceKey, prefs) }
        val downRaw = FloatArray(2)
        val startTrans = FloatArray(2)
        var moved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val dragListener = View.OnTouchListener { _, event ->
            val target = floatingView
            val parentView = target.parent as? View ?: return@OnTouchListener false
            val closeBtn = target.findViewById<View>(R.id.btnMiniRoomClose)
            val micBtn = target.findViewById<View>(R.id.btnMiniRoomMic)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (closeBtn != null && closeBtn.visibility == View.VISIBLE
                        && touchInsideChild(target, closeBtn, event)
                    ) {
                        return@OnTouchListener false
                    }
                    if (micBtn != null && micBtn.visibility == View.VISIBLE
                        && touchInsideChild(target, micBtn, event)
                    ) {
                        return@OnTouchListener false
                    }
                    downRaw[0] = event.rawX
                    downRaw[1] = event.rawY
                    startTrans[0] = target.translationX
                    startTrans[1] = target.translationY
                    moved = false
                    target.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRaw[0]
                    val dy = event.rawY - downRaw[1]
                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) moved = true
                    val bottomLimit = binding?.customBottomBar?.top?.takeIf { it > 0 }
                        ?.toFloat()
                        ?: parentView.height.toFloat()
                    val minTx = -target.left.toFloat()
                    var maxTx = (parentView.width - target.left - target.width).toFloat()
                    val minTy = -target.top.toFloat()
                    var maxTy = bottomLimit - target.top - target.height
                    if (maxTx < minTx) maxTx = minTx
                    if (maxTy < minTy) maxTy = minTy
                    val tx = max(minTx, min(maxTx, startTrans[0] + dx))
                    val ty = max(minTy, min(maxTy, startTrans[1] + dy))
                    target.translationX = tx
                    target.translationY = ty
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    target.parent?.requestDisallowInterceptTouchEvent(false)
                    saveFloatingPosition(target, preferenceKey, prefs, parentView)
                    if (event.actionMasked == MotionEvent.ACTION_UP && !moved) {
                        target.performClick()
                    }
                    true
                }
                else -> false
            }
        }
        floatingView.setOnTouchListener(dragListener)
        floatingView.findViewById<View>(R.id.miniRoomRing)?.apply {
            isClickable = false
            isFocusable = false
            setOnTouchListener(dragListener)
        }
        floatingView.findViewById<View>(R.id.imgMiniRoomCover)?.apply {
            isClickable = false
            isFocusable = false
            setOnTouchListener(dragListener)
        }
    }

    private fun touchInsideChild(parent: View?, child: View?, event: MotionEvent): Boolean {
        if (parent == null || child == null) return false
        val parentLoc = IntArray(2)
        val childLoc = IntArray(2)
        parent.getLocationOnScreen(parentLoc)
        child.getLocationOnScreen(childLoc)
        val x = event.rawX
        val y = event.rawY
        return x >= childLoc[0] && x <= childLoc[0] + child.width
            && y >= childLoc[1] && y <= childLoc[1] + child.height
    }

    private fun restoreFloatingPosition(view: View, key: String, prefs: SharedPreferences) {
        val parent = view.parent as? View
        if (parent == null || !prefs.contains("${key}_tx")) {
            if (parent != null && prefs.contains("${key}_x")) {
                val maxX = max(1f, (parent.width - view.width).toFloat())
                val bottom = binding?.customBottomBar?.top?.takeIf { it > 0 }
                    ?.toFloat()
                    ?: parent.height.toFloat()
                val maxY = max(1f, bottom - view.height)
                val absX = maxX * prefs.getFloat("${key}_x", 0f)
                val absY = maxY * prefs.getFloat("${key}_y", 1f)
                view.translationX = absX - view.left
                view.translationY = absY - view.top
            }
            return
        }
        val bottom = binding?.customBottomBar?.top?.takeIf { it > 0 }
            ?.toFloat()
            ?: parent.height.toFloat()
        val minTx = -view.left.toFloat()
        val maxTx = (parent.width - view.left - view.width).toFloat()
        val minTy = -view.top.toFloat()
        val maxTy = bottom - view.top - view.height
        val tx = minTx + (maxTx - minTx) * prefs.getFloat("${key}_tx", 0f)
        val ty = minTy + (maxTy - minTy) * prefs.getFloat("${key}_ty", 0f)
        view.translationX = tx
        view.translationY = ty
    }

    private fun saveFloatingPosition(
        view: View,
        key: String,
        prefs: SharedPreferences,
        parent: View,
    ) {
        val bottom = binding?.customBottomBar?.top?.takeIf { it > 0 }
            ?.toFloat()
            ?: parent.height.toFloat()
        val minTx = -view.left.toFloat()
        val maxTx = max(minTx + 1f, (parent.width - view.left - view.width).toFloat())
        val minTy = -view.top.toFloat()
        val maxTy = max(minTy + 1f, bottom - view.top - view.height)
        val nx = (view.translationX - minTx) / (maxTx - minTx)
        val ny = (view.translationY - minTy) / (maxTy - minTy)
        prefs.edit()
            .putFloat("${key}_tx", max(0f, min(1f, nx)))
            .putFloat("${key}_ty", max(0f, min(1f, ny)))
            .apply()
    }

    private fun highlight(on: Boolean, icon: ImageView?, label: TextView?) {
        if (icon == null || label == null) return
        icon.alpha = if (on) 1f else 0.55f
        label.alpha = 1f
        label.setTextColor(
            getColor(if (on) R.color.bottom_chrome_label else R.color.bottom_chrome_label_muted),
        )
        label.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        clearNavIconTint(icon)
        icon.scaleX = if (on) 1.05f else 1f
        icon.scaleY = if (on) 1.05f else 1f
        icon.background = null
    }

    private fun highlightTab(
        on: Boolean,
        icon: ImageView?,
        label: TextView?,
        tabKey: String,
        normalAsset: String,
        selectedAsset: String,
    ) {
        highlight(on, icon, label)
        if (icon != null) {
            icon.alpha = 1f
            RemoteNavIcons.bind(icon, tabKey, on, normalAsset, selectedAsset)
        }
    }

    private fun attachTaskInviteListener(container: AppContainer) {
        if (taskInviteListener != null) return
        val listener = object : RealtimeClient.RoomListener {
            override fun onRoomEvent(
                roomId: String?,
                event: String?,
                payload: JsonObject?,
                fromUserId: String?,
                fromUsername: String?,
            ) {
                if (event != "room:task_invited" || payload == null) return
                if (VoiceRoomActivity.isRoomUiVisible()) return
                val guestId = if (payload.has("guestId") && !payload.get("guestId").isJsonNull) {
                    payload.get("guestId").asString
                } else {
                    null
                }
                val myId = container.sessionManager.userId
                if (myId == null || guestId == null || myId != guestId) return
                val inviteRoomId = if (payload.has("roomId") && !payload.get("roomId").isJsonNull) {
                    payload.get("roomId").asString
                } else {
                    roomId
                }
                if (inviteRoomId.isNullOrEmpty()) return
                runOnUiThread { promptTaskRoomInvite(inviteRoomId) }
            }

            override fun onUserJoined(roomId: String?, userId: String?, username: String?) {}
            override fun onUserLeft(roomId: String?, userId: String?) {}
            override fun onConnected() {}
            override fun onDisconnected() {}
        }
        taskInviteListener = listener
        RealtimeClient.getInstance().addRoomListener(listener)
    }

    private fun promptTaskRoomInvite(inviteRoomId: String) {
        if (isFinishing) return
        AuraDialogHelper.confirm(
            this,
            getString(R.string.task_invite_title),
            getString(R.string.task_invite_message),
            getString(R.string.enter_room),
            {
                startActivity(
                    Intent(this, VoiceRoomActivity::class.java)
                        .putExtra(VoiceRoomActivity.EXTRA_ROOM_ID, inviteRoomId),
                )
            },
            getString(R.string.later),
            null,
        )
    }

    override fun onDestroy() {
        playInAppUpdateHelper?.onDestroy()
        playInAppUpdateHelper = null
        taskInviteListener?.let {
            RealtimeClient.getInstance().removeRoomListener(it)
            taskInviteListener = null
        }
        binding = null
        super.onDestroy()
    }
}
