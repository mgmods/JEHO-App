<template>
  <div class="auth-page">
    <div class="auth-card">
      <RouterLink class="brand" style="padding:0 0 14px;text-decoration:none;color:inherit" :to="{ name: 'landing' }">
        <div class="brand-mark">LV</div>
        <div>
          <h1 style="font-size:20px">LYVO Cloud</h1>
          <span>Live voice for apps</span>
        </div>
      </RouterLink>
      <h2>{{ mode === 'login' ? 'تسجيل الدخول' : 'إنشاء حساب مطور' }}</h2>
      <p class="sub">
        {{ mode === 'login'
          ? 'لوحة المطور — تطبيقات، SDK، دقائق.'
          : 'حساب مطور مجاني. إعدادات دفع المنصة للأدمن فقط.' }}
      </p>

      <div class="tabs">
        <button type="button" :class="{ on: mode === 'login' }" @click="mode = 'login'">دخول</button>
        <button type="button" :class="{ on: mode === 'register' }" @click="mode = 'register'">تسجيل</button>
      </div>

      <div v-if="error" class="error">{{ error }}</div>

      <div v-if="mode === 'register'" class="field">
        <label>الاسم المعروض</label>
        <input v-model="displayName" placeholder="استوديو أو علامة" />
      </div>
      <div class="field">
        <label>البريد</label>
        <input v-model="email" type="email" autocomplete="username" placeholder="you@studio.com" dir="ltr" />
      </div>
      <div class="field">
        <label>كلمة المرور</label>
        <input v-model="password" type="password" autocomplete="current-password" placeholder="8 أحرف على الأقل" dir="ltr" />
      </div>

      <button class="btn btn-primary btn-block" type="button" :disabled="busy" @click="submit">
        {{ busy ? '...' : mode === 'login' ? 'دخول' : 'إنشاء الحساب' }}
      </button>

      <p style="margin:16px 0 0;text-align:center;font-size:12px;color:var(--muted)">
        بالمتابعة أنت توافق على
        <RouterLink :to="{ name: 'terms' }">الشروط</RouterLink>
        و
        <RouterLink :to="{ name: 'privacy' }">الخصوصية</RouterLink>
      </p>
      <p style="margin:10px 0 0;text-align:center">
        <RouterLink :to="{ name: 'landing' }" style="font-size:13px">← العودة للموقع</RouterLink>
      </p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, setToken } from '../api'

const router = useRouter()
const route = useRoute()
const mode = ref('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const error = ref('')
const busy = ref(false)

onMounted(() => {
  if (route.meta.register || route.name === 'register') mode.value = 'register'
})

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const body = { email: email.value, password: password.value, displayName: displayName.value }
    const data = mode.value === 'login' ? await api.login(body) : await api.register(body)
    setToken(data.token)
    const next = typeof route.query.next === 'string' ? route.query.next : '/console'
    router.replace(next.startsWith('/console') ? next : { name: 'home' })
  } catch (e) {
    error.value = e.message === 'invalid_credentials'
      ? 'بريد أو كلمة مرور خاطئة'
      : e.message === 'email_taken'
        ? 'البريد مسجّل مسبقاً'
        : e.message || 'فشل'
  } finally {
    busy.value = false
  }
}
</script>
