<template>
  <section class="admin-home-menu" aria-label="أقسام الإدارة">
    <div class="admin-home-profile">
      <div class="admin-home-avatar">{{ initials }}</div>
      <div class="admin-home-identity">
        <strong>{{ auth.displayName }}</strong>
        <span>{{ roleLabel }}</span>
      </div>
      <span class="admin-home-role">{{ roleLabel }}</span>
    </div>

    <form class="admin-home-search" @submit.prevent="openUser">
      <i class="bi bi-search" aria-hidden="true"></i>
      <input v-model.trim="userId" inputmode="numeric" autocomplete="off" placeholder="ابحث عن المستخدم بالمعرّف" aria-label="ابحث عن المستخدم بالمعرّف" />
      <button type="submit" :disabled="!userId">بحث</button>
    </form>

    <div class="admin-home-grid">
      <RouterLink
        v-for="item in visibleItems"
        :key="item.name"
        class="admin-home-tile"
        :to="{ name: item.name }"
      >
        <span class="admin-home-icon" :class="'tone-' + item.tone">
          <i :class="['bi', item.icon]" aria-hidden="true"></i>
        </span>
        <strong>{{ item.label }}</strong>
        <span class="admin-home-chevron"><i class="bi bi-chevron-left"></i></span>
      </RouterLink>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const userId = ref('')

const roleLabel = computed(() => {
  const role = String(auth.user?.staffRole || auth.user?.role || '').toLowerCase()
  if (auth.isSuperAdmin) return 'Super Admin'
  if (role.includes('manager')) return 'Manager'
  return 'Admin'
})
const initials = computed(() =>
  String(auth.displayName || 'A').trim().split(/\s+/).map((part) => part[0]).join('').slice(0, 2).toUpperCase(),
)

const items = [
  { name: 'users', label: 'المستخدمون', icon: 'bi-people-fill', tone: 'purple', module: 'users' },
  { name: 'agencies', label: 'الوكالات وطلبات الوكالات', icon: 'bi-people-fill', tone: 'green', module: 'agencies' },
  { name: 'rooms', label: 'الغرف', icon: 'bi-card-text', tone: 'blue', module: 'rooms' },
  { name: 'conversations', label: 'المحادثات', icon: 'bi-chat-dots-fill', tone: 'blue', module: 'conversations' },
  { name: 'notifications', label: 'الرسائل والإعلانات', icon: 'bi-envelope-paper-fill', tone: 'green', module: 'notifications' },
  { name: 'gifts', label: 'الهدايا', icon: 'bi-gift-fill', tone: 'pink', module: 'gifts' },
  { name: 'wallet', label: 'المحفظة والمعاملات', icon: 'bi-wallet-fill', tone: 'gold', module: 'wallet' },
  { name: 'withdrawals', label: 'طلبات السحب', icon: 'bi-cash-coin', tone: 'green', module: 'withdrawals' },
  { name: 'coins', label: 'باقات العملات', icon: 'bi-coin', tone: 'blue', module: 'coins' },
  { name: 'vip', label: 'VIP', icon: 'bi-gem', tone: 'gold', module: 'vip' },
  { name: 'cosmetics', label: 'المتجر والمظاهر', icon: 'bi-shop-window', tone: 'purple', module: 'cosmetics' },
  { name: 'reports', label: 'البلاغات', icon: 'bi-flag-fill', tone: 'pink', module: 'reports' },
  { name: 'cp-settings', label: 'نظام CP', icon: 'bi-heart-fill', tone: 'pink', module: 'cpSettings' },
  { name: 'tasks', label: 'المهام والمكافآت', icon: 'bi-award-fill', tone: 'purple', module: 'tasks' },
  { name: 'settings', label: 'إعدادات التطبيق', icon: 'bi-gear-fill', tone: 'blue', module: 'settings' },
]

const visibleItems = computed(() => items.filter((item) => auth.can(item.module, 'read') && auth.canRoute(item.name, 'read')))

function openUser() {
  if (!userId.value) return
  router.push({ name: 'users', query: { search: userId.value } })
}
</script>

<style scoped>
.admin-home-menu { margin: 0 auto 1.25rem; max-width: 1180px; color: #202536; }
.admin-home-profile { display:flex; align-items:center; gap:.8rem; padding:.25rem .15rem 1rem; }
.admin-home-avatar { width:58px;height:58px;border-radius:50%;display:grid;place-items:center;background:#eef0fa;color:#6151c9;font-weight:800;font-size:1.1rem;flex:none; }
.admin-home-identity { min-width:0;display:flex;flex-direction:column;gap:.18rem; }
.admin-home-identity strong { font-size:1rem;color:#202536; }
.admin-home-identity span { font-size:.78rem;color:#8a90a0; }
.admin-home-role { margin-inline-start:auto;background:#25b8e5;color:#08354a;border-radius:13px;padding:.48rem .85rem;font-weight:800;font-size:.78rem; }
.admin-home-search { display:flex;align-items:center;gap:.65rem;background:#f2f2f7;border:1px solid #e8e8ef;border-radius:17px;padding:.55rem .8rem;margin-bottom:1rem;min-height:54px; }
.admin-home-search i { color:#9298a8;font-size:1.05rem; }
.admin-home-search input { min-width:0;flex:1;border:0;outline:0;background:transparent;color:#202536;font:inherit;font-size:.88rem; }
.admin-home-search button { border:0;background:transparent;color:#2485ca;font-weight:800;padding:.35rem .25rem; }
.admin-home-search button:disabled { opacity:.4; }
.admin-home-grid { display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:.8rem; }
.admin-home-tile { min-height:142px;position:relative;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:.7rem;padding:1rem .5rem .9rem;background:#fff;border:1px solid #ececf1;border-radius:22px;box-shadow:0 2px 5px rgba(20,25,40,.10);color:#202536;text-align:center;text-decoration:none;transition:transform .16s ease,box-shadow .16s ease,border-color .16s ease; }
.admin-home-tile:hover,.admin-home-tile:focus-visible { color:#202536;transform:translateY(-2px);border-color:#d8d8e6;box-shadow:0 7px 18px rgba(20,25,40,.12); }
.admin-home-icon { width:48px;height:48px;display:grid;place-items:center;border-radius:13px;font-size:1.55rem; }
.tone-purple { color:#8050df;background:#f1eaff; }.tone-blue { color:#2875d9;background:#e8f1ff; }.tone-green { color:#16a765;background:#e6f8ee; }.tone-pink { color:#d94d93;background:#ffedf5; }.tone-gold { color:#d99a0b;background:#fff6df; }
.admin-home-tile strong { font-size:.86rem;line-height:1.35;font-weight:800; }
.admin-home-chevron { position:absolute;inset-inline-end:.55rem;top:.55rem;color:#c3c6cf;font-size:.65rem;opacity:0; }
@media(max-width:380px) { .admin-home-grid { gap:.55rem; }.admin-home-tile { min-height:126px;border-radius:18px;padding-inline:.3rem; }.admin-home-tile strong { font-size:.76rem; }.admin-home-icon { width:42px;height:42px;font-size:1.35rem; } }
</style>
