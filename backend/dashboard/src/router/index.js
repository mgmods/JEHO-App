import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { i18n } from '@/i18n'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { guest: true, titleKey: 'routes.login' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { titleKey: 'routes.dashboard' },
      },
      {
        path: 'conversations',
        name: 'conversations',
        component: () => import('@/views/UserConversationsView.vue'),
        meta: { title: 'جميع المحادثات' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: { titleKey: 'routes.users' },
      },
      {
        path: 'users/:id',
        name: 'user-detail',
        component: () => import('@/views/UserDetailView.vue'),
        meta: { titleKey: 'routes.userDetail' },
      },
      {
        path: 'rooms',
        name: 'rooms',
        component: () => import('@/views/RoomsView.vue'),
        meta: { titleKey: 'routes.rooms' },
      },
      {
        path: 'streams',
        name: 'streams',
        redirect: { name: 'rooms' },
        meta: { titleKey: 'routes.rooms' },
      },
      {
        path: 'gifts',
        name: 'gifts',
        component: () => import('@/views/GiftsView.vue'),
        meta: { titleKey: 'routes.gifts' },
      },
      {
        path: 'banners',
        name: 'banners',
        component: () => import('@/views/BannersView.vue'),
        meta: { titleKey: 'routes.banners' },
      },
      {
        path: 'nav-icons',
        name: 'nav-icons',
        component: () => import('@/views/NavIconsView.vue'),
        meta: { titleKey: 'nav.navIcons' },
      },
      {
        path: 'seat-stickers',
        name: 'seat-stickers',
        component: () => import('@/views/SeatStickersView.vue'),
        meta: { titleKey: 'nav.seatStickers' },
      },
      {
        path: 'coins',
        name: 'coins',
        component: () => import('@/views/CoinPackagesView.vue'),
        meta: { titleKey: 'routes.coins' },
      },
      {
        path: 'offers',
        name: 'offers',
        component: () => import('@/views/OffersView.vue'),
        meta: { titleKey: 'routes.offers' },
      },
      {
        path: 'promos',
        name: 'promos',
        component: () => import('@/views/PromosView.vue'),
        meta: { titleKey: 'routes.promos', title: 'العروض والترقيات' },
      },
      {
        path: 'wallet',
        name: 'wallet',
        component: () => import('@/views/WalletView.vue'),
        meta: { titleKey: 'routes.wallet', walletMode: 'wallet' },
      },
      {
        path: 'withdrawals',
        name: 'withdrawals',
        component: () => import('@/views/WalletView.vue'),
        meta: { titleKey: 'routes.withdrawals', walletMode: 'withdrawals' },
      },
      {
        path: 'host-target',
        name: 'host-target',
        component: () => import('@/views/HostTargetView.vue'),
        meta: { titleKey: 'routes.hostTarget', title: 'تارجيت المضيف' },
      },
      {
        path: 'payment-settings',
        redirect: { name: 'settings', query: { tab: 'payment' } },
      },
      {
        path: 'zego-settings',
        redirect: { name: 'settings', query: { tab: 'zego' } },
      },
      {
        path: 'vip',
        name: 'vip',
        component: () => import('@/views/VipView.vue'),
        meta: { titleKey: 'routes.vip' },
      },
      {
        path: 'vanity-ids',
        name: 'vanity-ids',
        component: () => import('@/views/VanityIdsView.vue'),
        meta: { titleKey: 'routes.vanityIds', title: 'الآي دي المميز' },
      },
      {
        path: 'cosmetics',
        name: 'cosmetics',
        component: () => import('@/views/CosmeticsView.vue'),
        meta: { titleKey: 'routes.cosmetics' },
      },
      {
        path: 'contests',
        name: 'contests',
        component: () => import('@/views/ContestsView.vue'),
        meta: { titleKey: 'routes.contests' },
      },
      {
        path: 'games',
        name: 'games',
        component: () => import('@/views/GamesView.vue'),
        meta: { titleKey: 'routes.games' },
      },
      {
        path: 'game-ads',
        name: 'game-ads',
        component: () => import('@/views/GameAdsView.vue'),
        meta: { titleKey: 'routes.gameAds' },
      },
      {
        path: 'drama',
        name: 'drama',
        component: () => import('@/views/DramaView.vue'),
        meta: { titleKey: 'routes.drama' },
      },
      {
        path: 'lucky-boxes',
        name: 'lucky-boxes',
        component: () => import('@/views/LuckyBoxesView.vue'),
        meta: { titleKey: 'routes.luckyBoxes' },
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('@/views/TasksView.vue'),
        meta: { titleKey: 'routes.tasks' },
      },
      {
        path: 'ranking',
        name: 'ranking',
        component: () => import('@/views/RankingView.vue'),
        meta: { titleKey: 'routes.ranking' },
      },
      {
        path: 'room-cup',
        name: 'room-cup',
        component: () => import('@/views/RoomCupView.vue'),
        meta: { titleKey: 'routes.roomCup', title: 'كأس الروم' },
      },
      {
        path: 'agencies',
        name: 'agencies',
        component: () => import('@/views/AgenciesView.vue'),
        meta: { titleKey: 'routes.agencies' },
      },
      {
        path: 'recharge-agents',
        name: 'recharge-agents',
        component: () => import('@/views/RechargeAgentsView.vue'),
        meta: { titleKey: 'routes.rechargeAgents' },
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/views/ReportsView.vue'),
        meta: { titleKey: 'routes.reports' },
      },
      {
        path: 'gender-verifications',
        name: 'gender-verifications',
        component: () => import('@/views/GenderVerificationsView.vue'),
        meta: { titleKey: 'routes.genderVerifications' },
      },
      {
        path: 'notifications',
        name: 'notifications',
        component: () => import('@/views/NotificationsView.vue'),
        meta: { titleKey: 'routes.notifications' },
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/SettingsView.vue'),
        meta: { titleKey: 'routes.settings' },
      },
      {
        path: 'logs',
        name: 'logs',
        component: () => import('@/views/LogsView.vue'),
        meta: { titleKey: 'routes.logs' },
      },
      {
        path: 'policy-brochure',
        name: 'policy-brochure',
        component: () => import('@/views/PolicyBrochureView.vue'),
        meta: { titleKey: 'routes.policyBrochure', title: 'سياسة المنصة PDF' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  document.body.classList.add('route-loading')
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guest && auth.isAuthenticated) {
    return { name: 'dashboard' }
  }
  if (to.meta.requiresAuth && auth.isAuthenticated && to.name) {
    if (!auth.canRoute(String(to.name), 'read')) {
      return { name: 'dashboard' }
    }
  }
  const title = to.meta.titleKey ? `${i18n.global.t(to.meta.titleKey)} · JEHO CHAT Admin` : 'JEHO CHAT Admin'
  document.title = title
  return true
})

router.afterEach(() => {
  requestAnimationFrame(() => {
    document.body.classList.remove('route-loading')
  })
})

export default router
