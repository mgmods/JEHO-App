<template>
  <div class="login-page">
    <div class="login-ambient" aria-hidden="true"></div>
    <div class="login-grid" aria-hidden="true"></div>

    <div class="login-shell">
      <aside class="login-hero glass-strong">
        <div class="login-hero-badge">{{ t('login.operations') }}</div>
        <h2 class="display-font">{{ t('login.heroTitle') }}</h2>
        <p>{{ t('login.heroText') }}</p>
        <ul class="login-stats">
          <li><strong>{{ t('login.live') }}</strong><span>{{ t('login.liveText') }}</span></li>
          <li><strong>{{ t('login.economy') }}</strong><span>{{ t('login.economyText') }}</span></li>
          <li><strong>{{ t('login.studio') }}</strong><span>{{ t('login.studioText') }}</span></li>
        </ul>
      </aside>

      <div class="glass-strong login-card">
        <div class="text-center mb-4">
          <img
            :src="logoUrl"
            alt="JEHO CHAT"
            width="72"
            height="72"
            class="mb-3 login-logo"
          />
          <h1 class="display-font h3 mb-1 text-white">{{ t('login.title') }}</h1>
          <p class="mb-0 login-sub">{{ t('login.subtitle') }}</p>
          <p class="mb-0 mt-2 small text-white-50">دخول سوبر أدمن فقط — المانجر والحسابات العادية مرفوضون.</p>
        </div>

        <AlertMessage v-if="auth.error" :message="auth.error" type="danger" @dismiss="auth.error = null" />

        <form @submit.prevent="onSubmit">
          <div class="mb-3">
            <label class="form-label text-white-50">{{ t('login.email') }}</label>
            <input
              v-model="form.email"
              type="email"
              class="form-control form-control-lg"
              required
              placeholder="admin@jeho.chat"
              autocomplete="username"
            />
          </div>
          <div class="mb-4">
            <label class="form-label text-white-50">{{ t('login.password') }}</label>
            <input
              v-model="form.password"
              type="password"
              class="form-control form-control-lg"
              required
              placeholder="••••••••"
              autocomplete="current-password"
            />
          </div>
          <button class="btn btn-aurora w-100 btn-lg" type="submit" :disabled="auth.loading">
            <span v-if="auth.loading" class="spinner-border spinner-border-sm me-2"></span>
            {{ t('login.submit') }}
          </button>
        </form>

        <div class="login-hint mt-4">
          <div>{{ t('login.api') }}: <code>api.adnova.bbs.tr</code></div>
          <div class="mt-1">{{ t('login.defaultAccount') }}: <code>admin@jeho.chat</code></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/composables/useToast'
import AlertMessage from '@/components/AlertMessage.vue'
import brandLogo from '@/assets/brand/logo.png'

const { t } = useI18n()

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const logoUrl = brandLogo

const form = reactive({
  email: 'admin@jeho.chat',
  password: '',
})

async function onSubmit() {
  const ok = await auth.login({ email: form.email, password: form.password })
  if (ok) {
    toast().success(t('login.submit'))
    const redirect = route.query.redirect
    if (typeof redirect === 'string' && redirect && redirect !== '/' && !redirect.includes('://')) {
      router.replace(redirect)
    } else {
      router.replace({ name: 'dashboard' })
    }
  } else if (auth.error) {
    toast().danger(auth.error)
  }
}
</script>

<style scoped>
.login-shell {
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  gap: 1.25rem;
  position: relative;
  z-index: 2;
}

.login-hero {
  padding: 2.25rem;
  color: #eef1f6;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 520px;
}

.login-hero-badge {
  display: inline-flex;
  width: fit-content;
  padding: 0.35rem 0.7rem;
  border-radius: 999px;
  font-size: 0.7rem;
  letter-spacing: 0.12em;
  font-weight: 700;
  color: #0b0e14;
  background: linear-gradient(135deg, #9b7cff, #7c4dff);
  margin-bottom: 1.1rem;
}

.login-hero h2 {
  font-size: clamp(1.8rem, 3vw, 2.4rem);
  margin: 0 0 0.75rem;
  line-height: 1.15;
}

.login-hero p {
  color: rgba(238, 241, 246, 0.72);
  margin-bottom: 1.5rem;
  max-width: 36ch;
}

.login-stats {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 0.75rem;
}

.login-stats li {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.85rem 1rem;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(124, 77, 255, 0.18);
}

.login-stats strong {
  color: #9b7cff;
  font-family: var(--font-display), var(--al-display);
}

.login-stats span {
  color: rgba(238, 241, 246, 0.55);
  font-size: 0.85rem;
}

.login-logo {
  border-radius: 18px;
  object-fit: cover;
  filter: drop-shadow(0 8px 24px rgba(124, 77, 255, 0.4));
}

.login-sub {
  color: rgba(238, 241, 246, 0.65);
}

.login-hint {
  text-align: center;
  font-size: 0.78rem;
  color: rgba(238, 241, 246, 0.45);
}

.login-hint code {
  color: #f5c542;
  font-size: 0.78rem;
}

.login-ambient {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 50% 45% at 18% 28%, rgba(124, 77, 255, 0.3), transparent 60%),
    radial-gradient(ellipse 45% 40% at 82% 68%, rgba(61, 139, 253, 0.14), transparent 55%),
    radial-gradient(ellipse 40% 35% at 55% 100%, rgba(124, 77, 255, 0.1), transparent 50%);
  animation: aurora-drift 12s ease-in-out infinite alternate;
  z-index: 0;
}

.login-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(124, 77, 255, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(124, 77, 255, 0.06) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(circle at center, black, transparent 75%);
  z-index: 1;
  pointer-events: none;
}

@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }
  .login-hero {
    min-height: auto;
    order: 2;
  }
}
</style>
