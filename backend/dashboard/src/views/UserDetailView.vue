<template>

  <div>

    <PageHeader :title="user?.name || user?.username || t('userDetail.title')" :subtitle="t('userDetail.subtitle')">

      <template #actions>

        <RouterLink class="btn btn-ghost btn-sm" :to="{ name: 'users' }">

          <i class="bi bi-arrow-left me-1"></i> {{ t('app.back') }}

        </RouterLink>

        <button class="btn btn-ghost btn-sm" type="button" @click="load" :disabled="loading">{{ t('common.refresh') }}</button>

      </template>

    </PageHeader>



    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />

    <AlertMessage v-if="walletSuccess" :message="walletSuccess" type="success" @dismiss="walletSuccess = ''" />

    <LoadingSpinner v-if="loading" />



    <div v-else-if="user" class="row g-3">

      <div class="col-lg-4">

        <div class="glass p-4">

          <div class="d-flex align-items-center gap-3 mb-3">

            <div class="user-detail-wear">
              <div class="user-detail-avatar">
                <img v-if="avatarUrl" :src="avatarUrl" alt="" @error="avatarError = true" />
                <span v-else>{{ (user.name || user.username || 'U').charAt(0).toUpperCase() }}</span>
              </div>
              <div v-if="frameUrl" class="user-detail-frame" aria-hidden="true">
                <SvgaPreview v-if="isSvgaFrame" class="user-detail-frame-media" :src="frameUrl" />
                <video
                  v-else-if="isVideoFrame"
                  class="user-detail-frame-media"
                  :src="frameUrl"
                  muted
                  loop
                  autoplay
                  playsinline
                />
                <img v-else class="user-detail-frame-media" :src="frameUrl" alt="" />
              </div>
            </div>

            <div>

              <h2 class="h5 mb-1">{{ user.name || user.username || '—' }}</h2>

              <div class="d-flex flex-wrap gap-2 align-items-center">
                <StatusBadge :status="user.status || (user.isBanned ? 'banned' : 'active')" />
                <span v-if="form.staffRole === 'super'" class="badge text-bg-warning">{{ t('users.roleSuper') }}</span>
                <span v-else-if="form.staffRole === 'manager'" class="badge text-bg-info">{{ t('users.roleManager') }}</span>
              </div>

            </div>

          </div>

          <dl class="row mb-0 small">

            <dt class="col-4 text-muted">{{ t('common.id') }}</dt><dd class="col-8 text-break">{{ user.id }}</dd>

            <dt class="col-4 text-muted">{{ t('common.email') }}</dt><dd class="col-8">{{ user.email || '—' }}</dd>

            <dt class="col-4 text-muted">{{ t('common.phone') }}</dt><dd class="col-8">{{ user.phone || '—' }}</dd>

            <dt class="col-4 text-muted">{{ t('common.country') }}</dt>
            <dd class="col-8"><CountryFlag :country="user.country || user.countryCode" show-label /></dd>

            <dt class="col-4 text-muted">{{ t('common.joined') }}</dt><dd class="col-8">{{ formatDate(user.createdAt) }}</dd>

          </dl>

          <div class="d-grid gap-2 mt-3">

            <button

              v-if="!(user.isBanned || user.status === 'banned')"

              class="btn btn-outline-danger"

              type="button"

              @click="ban"

            >

              {{ t('users.ban') }}

            </button>

            <button v-else class="btn btn-outline-success" type="button" @click="unban">{{ t('users.unban') }}</button>

            <button

              v-if="user.status !== 'deleted' && !user.isAdmin"

              class="btn btn-outline-warning"

              type="button"

              :disabled="resetting"

              @click="resetBaseline"

            >

              {{ resetting ? t('userDetail.resetting') : t('userDetail.resetBaseline') }}

            </button>

            <button

              v-if="user.status !== 'deleted'"

              class="btn btn-danger"

              type="button"

              @click="removeUser"

            >

              {{ t('users.deleteUser') }}

            </button>

          </div>

        </div>

      </div>

      <div class="col-lg-8">

        <div class="row g-3">

          <div class="col-md-3"><StatCard :label="t('common.coins')" :value="user.coins ?? 0" icon="bi bi-coin" /></div>

          <div class="col-md-3"><StatCard :label="t('common.diamonds')" :value="user.diamonds ?? 0" icon="bi bi-gem" /></div>

        </div>



        <div class="glass p-3 mt-3">

          <h3 class="h6 fw-semibold mb-1">{{ t('userDetail.adjustWallet') }}</h3>

          <p class="small text-muted mb-3">

            {{ t('userDetail.adjustHint') }}

          </p>

          <form class="row g-3" @submit.prevent="applyWallet">

            <div class="col-md-6 col-lg-3">

              <label class="form-label">{{ t('common.coins') }}</label>

              <input v-model.number="walletForm.coinsDelta" type="number" class="form-control" placeholder="+1000" />

            </div>

            <div class="col-md-6 col-lg-3">

              <label class="form-label">{{ t('common.diamonds') }}</label>

              <input v-model.number="walletForm.diamondsDelta" type="number" class="form-control" placeholder="+50" />

            </div>

            <div class="col-12">

              <label class="form-label">{{ t('common.reason') }}</label>

              <input v-model="walletForm.reason" class="form-control" required :placeholder="t('userDetail.reasonPlaceholder')" />

            </div>

            <div class="col-12">

              <button class="btn btn-aurora" type="submit" :disabled="walletSaving">

                {{ t('userDetail.applyAdjustment') }}

              </button>

            </div>

          </form>

        </div>



        <div class="glass p-3 mt-3">

          <h3 class="h6 fw-semibold mb-3">{{ t('userDetail.profileFields') }}</h3>

          <form class="row g-3" @submit.prevent="save">

            <div class="col-md-6">

              <label class="form-label">{{ t('userDetail.displayName') }}</label>

              <input v-model="form.name" class="form-control" />

            </div>

            <div class="col-md-6">

              <label class="form-label">{{ t('common.email') }}</label>

              <input v-model="form.email" type="email" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('common.username') }}</label>

              <input v-model="form.username" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('common.phone') }}</label>

              <input v-model="form.phone" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('common.gender') }}</label>

              <select v-model="form.gender" class="form-select">

                <option value="unspecified">—</option>

                <option value="female">female</option>

                <option value="male">male</option>

                <option value="other">other</option>

              </select>

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('common.country') }}</label>

              <input v-model="form.country" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('common.status') }}</label>

              <select v-model="form.status" class="form-select">

                <option value="active">{{ t('common.active') }}</option>

                <option value="suspended">suspended</option>

                <option value="banned">{{ t('common.banned') }}</option>

              </select>

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('common.level') }}</label>

              <input v-model.number="form.level" type="number" min="1" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">XP / Experience</label>

              <input v-model.number="form.experience" type="number" min="0" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('userDetail.vipLevel') }}</label>

              <input v-model.number="form.vipLevel" type="number" min="0" class="form-control" placeholder="0" />

            </div>

            <div class="col-md-4">

              <label class="form-label">Wealth (totalSentCoins)</label>

              <input v-model.number="form.totalSentCoins" type="number" min="0" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">Charm (totalReceivedDiamonds)</label>

              <input v-model.number="form.totalReceivedDiamonds" type="number" min="0" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">Set coins (مطلق)</label>

              <input v-model.number="form.coins" type="number" min="0" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">Set diamonds (مطلق)</label>

              <input v-model.number="form.diamonds" type="number" min="0" class="form-control" />

            </div>

            <div class="col-md-4">

              <label class="form-label">{{ t('userDetail.staffRole') }}</label>

              <select v-model="form.staffRole" class="form-select">

                <option value="none">{{ t('userDetail.roleNone') }}</option>

                <option value="manager">{{ t('userDetail.roleManager') }}</option>

                <option value="super">{{ t('userDetail.roleSuper') }}</option>

              </select>

              <div class="form-text">{{ t('userDetail.staffRoleHint') }}</div>

            </div>

            <div class="col-md-6">

              <div class="form-check form-switch mt-4">

                <input id="genderVerified" v-model="form.genderVerified" class="form-check-input" type="checkbox" />

                <label class="form-check-label" for="genderVerified">موثّقة / genderVerified</label>

              </div>

            </div>

            <div class="col-12">

              <button class="btn btn-aurora" type="submit" :disabled="saving">{{ t('userDetail.saveChanges') }}</button>

              <span v-if="saved" class="ms-2 text-success small">{{ t('common.saved') }}</span>

            </div>

          </form>

        </div>

      </div>

    </div>

  </div>

  <ConfirmDialog
    v-model="confirmOpen"
    :title="confirmTitle"
    :message="confirmMsg"
    @confirm="runConfirm"
  />

</template>



<script setup>

import { onMounted, reactive, ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { useRoute, useRouter } from 'vue-router'

import { usersApi, walletApi } from '@/api'

import { formatDate } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import { askPrompt } from '@/composables/usePrompt'
import { resolveAsset } from '@/utils/assets'

import PageHeader from '@/components/PageHeader.vue'

import AlertMessage from '@/components/AlertMessage.vue'

import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

import StatusBadge from '@/components/StatusBadge.vue'

import StatCard from '@/components/StatCard.vue'

import CountryFlag from '@/components/CountryFlag.vue'
import SvgaPreview from '@/components/SvgaPreview.vue'

const { t } = useI18n()



const route = useRoute()

const router = useRouter()

const user = ref(null)

const loading = ref(false)

const saving = ref(false)

const saved = ref(false)

const walletSaving = ref(false)

const walletSuccess = ref('')

const error = ref('')

const resetting = ref(false)
const avatarError = ref(false)

const form = reactive({
  name: '',
  email: '',
  phone: '',
  username: '',
  gender: 'unspecified',
  country: '',
  status: 'active',
  level: 1,
  experience: 0,
  vipLevel: 0,
  totalSentCoins: 0,
  totalReceivedDiamonds: 0,
  coins: 0,
  diamonds: 0,
  genderVerified: false,
  staffRole: 'none',
})

const avatarUrl = computed(() => {
  if (avatarError.value || !user.value) return null
  const url = user.value.avatarUrl || user.value.avatar
  if (!url) return null
  if (String(url).startsWith('http')) return url
  return resolveAsset(url)
})

const frameUrl = computed(() => {
  if (!user.value) return null
  const raw =
    user.value.frameAnimUrl
    || user.value.frameUrl
    || user.value.vipBadgeUrl
    || user.value.hostBadgeUrl
    || null
  if (!raw) return null
  if (String(raw).startsWith('http')) return raw
  return resolveAsset(raw)
})

const isSvgaFrame = computed(() => /\.svga(\?|$)/i.test(String(frameUrl.value || '')))
const isVideoFrame = computed(() =>
  /\.(mp4|webm|mov)(\?|$)/i.test(String(frameUrl.value || '')),
)

const walletForm = reactive({

  coinsDelta: 0,

  diamondsDelta: 0,

  reason: '',

})



watch(user, (u) => {

  if (!u) return

  form.name = u.name || u.username || u.displayName || ''

  form.email = u.email || ''

  form.phone = u.phone || ''

  form.username = u.username || ''

  form.gender = u.gender || 'unspecified'

  form.country = u.country || u.countryCode || ''

  form.status = u.status || (u.isBanned ? 'banned' : 'active')

  form.level = Number(u.level || 1)

  form.experience = Number(u.experience || 0)

  form.vipLevel = Number(u.vipLevel || 0)

  form.totalSentCoins = Number(u.totalSentCoins || 0)

  form.totalReceivedDiamonds = Number(u.totalReceivedDiamonds || 0)

  form.coins = Number(u.coins || 0)

  form.diamonds = Number(u.diamonds || 0)

  form.genderVerified = !!u.genderVerified

  const roleRaw = String(u.staffRole || '').toLowerCase()
  if (roleRaw === 'super' || roleRaw === 'super_admin' || u.isSuperAdmin || (u.isAdmin && roleRaw !== 'manager')) {
    form.staffRole = 'super'
  } else if (roleRaw === 'manager' || u.isManager) {
    form.staffRole = 'manager'
  } else {
    form.staffRole = 'none'
  }

})



async function load() {

  loading.value = true

  error.value = ''

  const { data, error: err } = await usersApi.get(route.params.id)

  loading.value = false

  if (err) {

    error.value = err.message

    user.value = null

    return

  }

  user.value = data?.user || data?.data || data

}



async function save() {

  saving.value = true

  saved.value = false

  const { error: err } = await usersApi.update(route.params.id, { ...form })

  saving.value = false

  if (err) {

    error.value = err.message

    return

  }

  saved.value = true

  await load()

}



async function applyWallet() {

  const deltas = {

    coinsDelta: Number(walletForm.coinsDelta) || 0,

    diamondsDelta: Number(walletForm.diamondsDelta) || 0,

  }

  if (!deltas.coinsDelta && !deltas.diamondsDelta) {

    error.value = t('userDetail.valueRequired')

    return

  }

  if (!walletForm.reason?.trim()) {

    error.value = t('userDetail.reasonRequired')

    return

  }



  walletSaving.value = true

  error.value = ''

  walletSuccess.value = ''

  const { error: err } = await walletApi.adjustUser(route.params.id, {

    ...deltas,

    note: walletForm.reason.trim(),

    reason: walletForm.reason.trim(),

  })

  walletSaving.value = false

  if (err) {

    error.value = err.message

    return

  }

  walletSuccess.value = t('userDetail.walletUpdated')

  walletForm.coinsDelta = 0

  walletForm.diamondsDelta = 0


  walletForm.reason = ''

  await load()

}



async function ban() {

  const reason = await askPrompt({
    title: t('users.ban'),
    message: t('userDetail.banReason'),
    defaultValue: t('users.defaultBanReason'),
  })

  if (reason === null) return

  const { error: err } = await usersApi.ban(route.params.id, { reason })

  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    toast().success(t('app.success'))
    await load()
  }

}



async function unban() {

  const { error: err } = await usersApi.unban(route.params.id)

  if (err) error.value = err.message

  else await load()

}



async function removeUser() {

  const label = user.value?.name || user.value?.displayName || user.value?.email || route.params.id

  pendingAction.value = { type: 'delete' }
  confirmTitle.value = t('common.delete')
  confirmMsg.value = t('userDetail.confirmDelete', { name: label })
  confirmOpen.value = true

}



async function resetBaseline() {

  const label = user.value?.name || user.value?.displayName || user.value?.email || route.params.id

  pendingAction.value = { type: 'reset' }
  confirmTitle.value = t('common.confirm')
  confirmMsg.value = t('userDetail.confirmReset', { name: label })
  confirmOpen.value = true

}

const confirmOpen = ref(false)
const confirmTitle = ref('')
const confirmMsg = ref('')
const pendingAction = ref(null)

async function runConfirm() {
  const action = pendingAction.value
  pendingAction.value = null
  if (!action) return
  if (action.type === 'delete') {
    const { error: err } = await usersApi.delete(route.params.id)
    if (err) {
      error.value = err.message
      toast().danger(err.message)
      return
    }
    toast().success(t('app.success'))
    router.push({ name: 'users' })
    return
  }
  if (action.type === 'reset') {
    resetting.value = true
    error.value = ''
    const { data, error: err } = await usersApi.resetBaseline(route.params.id)
    resetting.value = false
    if (err) {
      error.value = err.message
      toast().danger(err.message)
      return
    }
    walletSuccess.value = t('userDetail.resetDone')
    toast().success(walletSuccess.value)
    if (data?.user) user.value = data.user
    else await load()
  }
}

onMounted(load)

</script>

<style scoped>
.user-detail-wear {
  position: relative;
  width: 72px;
  height: 72px;
  flex: 0 0 auto;
}
.user-detail-avatar {
  position: absolute;
  inset: 12%;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, var(--al-teal, #14b8a6), var(--al-cyan, #22d3ee));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 800;
  font-size: 1.4rem;
  z-index: 1;
}
.user-detail-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user-detail-frame {
  position: absolute;
  inset: -10%;
  z-index: 2;
  pointer-events: none;
}
.user-detail-frame-media {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
</style>

