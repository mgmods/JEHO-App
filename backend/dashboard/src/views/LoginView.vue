<template>
  <div class="login-page">
    <div class="login-bg" aria-hidden="true">
      <span class="login-orb login-orb-a" />
      <span class="login-orb login-orb-b" />
      <span class="login-grid" />
    </div>

    <div class="login-frame">
      <!-- Left: brand rail — mirrors dashboard sidebar language -->
      <aside class="login-brand">
        <div class="login-brand-top">
          <div class="login-brand-mark">
            <img :src="logoUrl" alt="JEHO CHAT" width="48" height="48" />
          </div>
          <div>
            <div class="login-brand-name">{{ t('app.brand') }}</div>
            <div class="login-brand-tag">{{ t('dashboard.brandTag') }}</div>
          </div>
        </div>

        <div class="login-brand-body">
          <span class="login-eyebrow">{{ t('login.operations') }}</span>
          <h1 class="login-brand-title display-font">{{ t('login.heroTitle') }}</h1>
          <p class="login-brand-copy">{{ t('login.heroText') }}</p>

          <ul class="login-kpis">
            <li>
              <i class="bi bi-broadcast-pin" aria-hidden="true" />
              <div>
                <strong>{{ t('login.live') }}</strong>
                <span>{{ t('login.liveText') }}</span>
              </div>
            </li>
            <li>
              <i class="bi bi-coin" aria-hidden="true" />
              <div>
                <strong>{{ t('login.economy') }}</strong>
                <span>{{ t('login.economyText') }}</span>
              </div>
            </li>
            <li>
              <i class="bi bi-stars" aria-hidden="true" />
              <div>
                <strong>{{ t('login.studio') }}</strong>
                <span>{{ t('login.studioText') }}</span>
              </div>
            </li>
          </ul>
        </div>

        <div class="login-brand-foot">
          <i class="bi bi-shield-lock" aria-hidden="true" />
          <span>{{ t('login.superOnly') }}</span>
        </div>
      </aside>

      <!-- Right: sign-in card — same panel language as settings / stats -->
      <section class="login-panel">
        <header class="login-panel-head">
          <div class="login-panel-logo">
            <img :src="logoUrl" alt="" width="44" height="44" />
          </div>
          <div>
            <span class="login-eyebrow">{{ t('login.title') }}</span>
            <h2 class="login-panel-title display-font">{{ t('login.subtitle') }}</h2>
          </div>
        </header>

        <AlertMessage
          v-if="auth.error"
          :message="auth.error"
          type="danger"
          @dismiss="auth.error = null"
        />

        <form class="login-form" @submit.prevent="onSubmit">
          <div class="login-field">
            <label class="login-label" for="admin-email">{{ t('login.email') }}</label>
            <div class="login-input-wrap">
              <i class="bi bi-envelope" aria-hidden="true" />
              <input
                id="admin-email"
                v-model="form.email"
                type="email"
                class="login-input"
                required
                :placeholder="PRIMARY_ADMIN_EMAIL"
                autocomplete="username"
                dir="ltr"
              />
            </div>
          </div>

          <div class="login-field">
            <label class="login-label" for="admin-password">{{ t('login.password') }}</label>
            <div class="login-input-wrap">
              <i class="bi bi-key" aria-hidden="true" />
              <input
                id="admin-password"
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                class="login-input"
                required
                placeholder="••••••••"
                autocomplete="current-password"
                dir="ltr"
              />
              <button
                class="login-eye"
                type="button"
                :aria-label="showPassword ? 'Hide' : 'Show'"
                @click="showPassword = !showPassword"
              >
                <i :class="showPassword ? 'bi bi-eye-slash' : 'bi bi-eye'" />
              </button>
            </div>
          </div>

          <button class="btn btn-aurora login-submit" type="submit" :disabled="auth.loading">
            <span v-if="auth.loading" class="spinner-border spinner-border-sm" />
            <template v-else>
              <i class="bi bi-box-arrow-in-right" aria-hidden="true" />
              {{ t('login.submit') }}
            </template>
          </button>
        </form>

        <footer class="login-meta">
          <div>
            <span class="login-meta-k">{{ t('login.api') }}</span>
            <code dir="ltr">api.adnova.bbs.tr</code>
          </div>
          <div>
            <span class="login-meta-k">{{ t('login.defaultAccount') }}</span>
            <code dir="ltr">{{ PRIMARY_ADMIN_EMAIL }}</code>
          </div>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/composables/useToast'
import AlertMessage from '@/components/AlertMessage.vue'
import brandLogo from '@/assets/brand/logo.png'

const { t } = useI18n()

/** Primary platform admin (seed account). */
const PRIMARY_ADMIN_EMAIL = 'admin@auralive.com'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const logoUrl = brandLogo
const showPassword = ref(false)

const form = reactive({
  email: PRIMARY_ADMIN_EMAIL,
  password: '',
})

async function onSubmit() {
  form.email = String(form.email || '').trim().toLowerCase()
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
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(1rem, 3vw, 2rem);
  position: relative;
  overflow: hidden;
  background: #0f111a;
  color: #f4f6fb;
}

.login-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.login-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(8px);
}

.login-orb-a {
  width: min(52vw, 520px);
  height: min(52vw, 520px);
  left: -8%;
  top: -12%;
  background: radial-gradient(circle, rgba(108, 92, 231, 0.32), transparent 70%);
  animation: login-float 14s ease-in-out infinite alternate;
}

.login-orb-b {
  width: min(48vw, 460px);
  height: min(48vw, 460px);
  right: -10%;
  bottom: -16%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.16), transparent 70%);
  animation: login-float 16s ease-in-out infinite alternate-reverse;
}

.login-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(108, 92, 231, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(108, 92, 231, 0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 45%, #000 20%, transparent 75%);
  opacity: 0.7;
}

@keyframes login-float {
  from {
    transform: translate(0, 0) scale(1);
  }
  to {
    transform: translate(2%, 3%) scale(1.06);
  }
}

.login-frame {
  position: relative;
  z-index: 1;
  width: min(1040px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.07);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 0.04) inset,
    0 28px 64px rgba(0, 0, 0, 0.55);
  background: linear-gradient(165deg, #16182a 0%, #12141f 48%, #0f111a 100%);
  animation: login-rise 0.55s ease both;
}

@keyframes login-rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── Brand rail ── */
.login-brand {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: clamp(1.4rem, 3vw, 2.1rem);
  background:
    linear-gradient(180deg, rgba(108, 92, 231, 0.14), transparent 42%),
    linear-gradient(160deg, #151722 0%, #12141f 100%);
  border-inline-end: 1px solid rgba(255, 255, 255, 0.06);
}

.login-brand-top {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.login-brand-mark {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  overflow: hidden;
  flex-shrink: 0;
  background: #1c1e2d;
  border: 1px solid rgba(108, 92, 231, 0.35);
  box-shadow: 0 8px 22px rgba(108, 92, 231, 0.25);
}

.login-brand-mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.login-brand-name {
  font-weight: 700;
  font-size: 1.05rem;
  letter-spacing: -0.02em;
  color: #f4f6fb;
}

.login-brand-tag {
  font-size: 0.72rem;
  color: #7a829c;
  margin-top: 0.1rem;
}

.login-brand-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-eyebrow {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #8b7ff0;
  background: rgba(108, 92, 231, 0.14);
  border: 1px solid rgba(108, 92, 231, 0.32);
  padding: 0.28rem 0.7rem;
  border-radius: 999px;
  margin-bottom: 0.85rem;
}

.login-brand-title {
  margin: 0 0 0.75rem;
  font-size: clamp(1.55rem, 2.6vw, 2.15rem);
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.03em;
  color: #f4f6fb;
}

.login-brand-copy {
  margin: 0 0 1.35rem;
  color: #a0a8c0;
  font-size: 0.95rem;
  line-height: 1.55;
  max-width: 34ch;
}

.login-kpis {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.65rem;
}

.login-kpis li {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.85rem 0.95rem;
  border-radius: 14px;
  background: #1c1e2d;
  border: 1px solid rgba(255, 255, 255, 0.06);
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.login-kpis li:hover {
  border-color: rgba(108, 92, 231, 0.4);
  transform: translateY(-1px);
}

.login-kpis i {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: rgba(108, 92, 231, 0.16);
  color: #8b7ff0;
  font-size: 1rem;
  border: 1px solid rgba(108, 92, 231, 0.28);
}

.login-kpis strong {
  display: block;
  font-size: 0.92rem;
  color: #f4f6fb;
  font-weight: 700;
}

.login-kpis span {
  display: block;
  font-size: 0.78rem;
  color: #7a829c;
  margin-top: 0.12rem;
}

.login-brand-foot {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.75rem 0.9rem;
  border-radius: 12px;
  background: rgba(108, 92, 231, 0.08);
  border: 1px solid rgba(108, 92, 231, 0.2);
  color: #a0a8c0;
  font-size: 0.8rem;
}

.login-brand-foot i {
  color: #8b7ff0;
  font-size: 1rem;
}

/* ── Sign-in panel ── */
.login-panel {
  padding: clamp(1.5rem, 3.2vw, 2.35rem);
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: #12141f;
}

.login-panel-head {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  margin-bottom: 1.35rem;
}

.login-panel-logo {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(108, 92, 231, 0.35);
  box-shadow: 0 8px 20px rgba(108, 92, 231, 0.22);
  flex-shrink: 0;
}

.login-panel-logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.login-panel-head .login-eyebrow {
  margin-bottom: 0.25rem;
}

.login-panel-title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #f4f6fb;
}

.login-form {
  display: grid;
  gap: 1rem;
}

.login-field {
  display: grid;
  gap: 0.4rem;
}

.login-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: #a0a8c0;
}

.login-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.login-input-wrap > i {
  position: absolute;
  inset-inline-start: 0.95rem;
  color: #7a829c;
  font-size: 0.95rem;
  pointer-events: none;
}

.login-input {
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: #151722;
  color: #f4f6fb;
  font-size: 0.98rem;
  padding: 0.78rem 2.6rem 0.78rem 2.55rem;
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.login-input::placeholder {
  color: #5c6478;
}

.login-input:focus {
  border-color: rgba(108, 92, 231, 0.55);
  box-shadow: 0 0 0 3px rgba(108, 92, 231, 0.22);
}

.login-eye {
  position: absolute;
  inset-inline-end: 0.45rem;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #7a829c;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.login-eye:hover {
  color: #8b7ff0;
  background: rgba(108, 92, 231, 0.12);
}

.login-submit {
  width: 100%;
  margin-top: 0.35rem;
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  font-size: 0.98rem;
  border-radius: 12px;
  background: linear-gradient(135deg, #7c4dff 0%, #6c5ce7 55%, #5a2fd9 100%) !important;
  box-shadow: 0 10px 28px rgba(108, 92, 231, 0.42) !important;
}

.login-submit:hover:not(:disabled) {
  filter: brightness(1.06);
  transform: translateY(-1px);
}

.login-submit:disabled {
  opacity: 0.7;
}

.login-meta {
  margin-top: 1.35rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: grid;
  gap: 0.45rem;
  font-size: 0.78rem;
  color: #7a829c;
}

.login-meta-k {
  margin-inline-end: 0.4rem;
}

.login-meta code {
  color: #f5b942;
  font-size: 0.78rem;
  background: rgba(245, 185, 66, 0.08);
  border: 1px solid rgba(245, 185, 66, 0.18);
  border-radius: 6px;
  padding: 0.1rem 0.4rem;
}

@media (max-width: 900px) {
  .login-frame {
    grid-template-columns: 1fr;
  }

  .login-brand {
    border-inline-end: 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  }

  .login-brand-body {
    display: none;
  }

  .login-brand-foot {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-orb-a,
  .login-orb-b,
  .login-frame {
    animation: none;
  }
}
</style>
