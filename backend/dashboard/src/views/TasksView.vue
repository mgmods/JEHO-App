<template>
  <div>
    <PageHeader :title="t('tasks.title')" :subtitle="t('tasks.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="!tasksEnabled" @click="addRow">
          {{ t('tasks.add') }}
        </button>
        <button
          class="btn btn-aurora btn-sm"
          type="button"
          :disabled="saving || !tasksEnabled"
          @click="save"
        >
          {{ t('tasks.saveToApp') }}
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3 d-flex flex-wrap align-items-center justify-content-between gap-3">
      <div>
        <div class="fw-semibold">{{ t('tasks.masterSwitch') }}</div>
        <div class="small text-muted">{{ t('tasks.masterSwitchHint') }}</div>
      </div>
      <div class="form-check form-switch m-0">
        <input
          id="tasksEnabledSwitch"
          v-model="tasksEnabled"
          class="form-check-input"
          type="checkbox"
          role="switch"
          :disabled="toggling"
          @change="toggleEnabled"
        />
        <label class="form-check-label" for="tasksEnabledSwitch">
          {{ tasksEnabled ? t('tasks.enabledOn') : t('tasks.enabledOff') }}
        </label>
      </div>
    </div>

    <div class="glass p-3 mb-3 small text-muted">
      {{ t('tasks.hint') }}
    </div>

    <section class="glass p-3 mb-3 daily-login-admin">
      <div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
        <div>
          <h5 class="mb-1">مكافآت تسجيل الدخول اليومي</h5>
          <div class="small text-muted">دورة ثابتة من 7 أيام. اختر المكافآت من صور المتجر وحدد الكميات والمدد دون كتابة أكواد.</div>
        </div>
        <div class="d-flex align-items-center gap-2">
          <label class="form-check-label small" for="dailyLoginEnabled">تفعيل النظام</label>
          <input id="dailyLoginEnabled" v-model="dailyLoginEnabled" class="form-check-input" type="checkbox" />
          <button class="btn btn-aurora btn-sm" type="button" :disabled="dailyLoginSaving" @click="saveDailyLogin">
            <i class="bi bi-save me-1"></i>{{ dailyLoginSaving ? 'جارٍ الحفظ…' : 'حفظ مكافآت الأيام السبعة' }}
          </button>
        </div>
      </div>
      <div v-if="dailyLoginError" class="alert alert-warning py-2">{{ dailyLoginError }}</div>
      <div v-if="dailyLoginSuccess" class="alert alert-success py-2">{{ dailyLoginSuccess }}</div>
      <LoadingSpinner v-if="dailyLoginLoading" />
      <div v-else class="daily-login-grid">
        <article v-for="(reward, idx) in dailyLoginDays" :key="reward.day" class="daily-login-card">
          <div class="d-flex align-items-center justify-content-between mb-2">
            <span class="daily-login-day">اليوم {{ reward.day }}</span>
            <i class="bi bi-calendar-check text-warning"></i>
          </div>
          <div class="daily-login-preview">
            <template v-if="reward.type === 'coins'">
              <i class="bi bi-coin daily-login-coin"></i>
              <strong>{{ Number(reward.amount || 0).toLocaleString() }}</strong>
              <span>كوينز</span>
            </template>
            <template v-else-if="cosmeticByCode(reward.cosmeticCode)?.previewUrl || cosmeticByCode(reward.cosmeticCode)?.animationUrl">
              <img :src="cosmeticByCode(reward.cosmeticCode).previewUrl || cosmeticByCode(reward.cosmeticCode).animationUrl" :alt="cosmeticByCode(reward.cosmeticCode).name" />
            </template>
            <template v-else>
              <i class="bi bi-gift daily-login-placeholder"></i>
              <span>اختر صورة المكافأة</span>
            </template>
          </div>
          <label class="form-label small mt-2">نوع المكافأة</label>
          <select v-model="reward.type" class="form-select form-select-sm">
            <option value="coins">كوينزات</option>
            <option value="cosmetic">عنصر تجميلي من المتجر</option>
          </select>
          <template v-if="reward.type === 'coins'">
            <label class="form-label small mt-2">كمية الكوينزات</label>
            <input v-model.number="reward.amount" class="form-control form-control-sm" type="number" min="1" max="1000000000" />
          </template>
          <template v-else>
            <div class="small fw-semibold mt-2 text-truncate">{{ cosmeticByCode(reward.cosmeticCode)?.name || 'لم يتم اختيار عنصر' }}</div>
            <button class="btn btn-outline-info btn-sm w-100 mt-1" type="button" @click="openCosmeticPicker(idx, 'daily')">
              <i class="bi bi-images me-1"></i>اختيار ومعاينة العنصر
            </button>
            <label class="form-label small mt-2">مدة الصلاحية بالأيام</label>
            <input v-model.number="reward.durationDays" class="form-control form-control-sm" type="number" min="1" max="365" />
          </template>
        </article>
      </div>
    </section>

    <LoadingSpinner v-if="loading" />
    <div v-else class="glass p-0 overflow-hidden" :class="{ 'opacity-50': !tasksEnabled }">
      <div class="table-responsive">
        <table class="table table-glass table-hover align-middle mb-0">
          <thead>
            <tr>
              <th>{{ t('common.id') }}</th>
              <th>{{ t('common.title') }}</th>
              <th>{{ t('tasks.points') }}</th>
              <th>{{ t('common.coins') }}</th>
              <th>المكافأة التجميلية</th>
              <th>أيام</th>
              <th>VIP</th>
              <th>أيام VIP</th>
              <th>{{ t('common.type') }}</th>
              <th>{{ t('common.scope') }}</th>
              <th>{{ t('tasks.scopeId') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in tasks" :key="row.id || idx">
              <td><input v-model="row.id" class="form-control form-control-sm" :disabled="!tasksEnabled" /></td>
              <td><input v-model="row.title" class="form-control form-control-sm" :disabled="!tasksEnabled" /></td>
              <td style="width:90px">
                <input
                  v-model.number="row.rewardPoints"
                  type="number"
                  min="0"
                  class="form-control form-control-sm"
                  :disabled="!tasksEnabled"
                />
              </td>
              <td style="width:90px">
                <input
                  v-model.number="row.rewardSilver"
                  type="number"
                  min="0"
                  class="form-control form-control-sm"
                  :disabled="!tasksEnabled"
                />
              </td>
              <td style="min-width:210px">
                <div class="d-flex align-items-center gap-2">
                  <img
                    v-if="cosmeticByCode(row.rewardCosmeticCode)?.previewUrl"
                    :src="cosmeticByCode(row.rewardCosmeticCode).previewUrl"
                    :alt="cosmeticByCode(row.rewardCosmeticCode).name"
                    class="task-cosmetic-thumb"
                  />
                  <div v-else class="task-cosmetic-thumb task-cosmetic-empty">
                    <i class="bi bi-gift"></i>
                  </div>
                  <div class="min-w-0 flex-grow-1">
                    <div class="small fw-semibold text-truncate">
                      {{ cosmeticByCode(row.rewardCosmeticCode)?.name || 'بدون عنصر تجميلي' }}
                    </div>
                    <button
                      type="button"
                      class="btn btn-sm btn-outline-info mt-1"
                      :disabled="!tasksEnabled || cosmeticsLoading"
                      @click="openCosmeticPicker(idx)"
                    >
                      <i class="bi bi-images me-1"></i>
                      {{ row.rewardCosmeticCode ? 'تغيير العنصر' : 'اختيار من المتجر' }}
                    </button>
                    <button
                      v-if="row.rewardCosmeticCode"
                      type="button"
                      class="btn btn-sm btn-link text-danger mt-1"
                      :disabled="!tasksEnabled"
                      @click="row.rewardCosmeticCode = ''"
                    >إزالة</button>
                  </div>
                </div>
              </td>
              <td style="width:70px">
                <input
                  v-model.number="row.rewardCosmeticDays"
                  type="number"
                  min="0"
                  class="form-control form-control-sm"
                  :disabled="!tasksEnabled"
                />
              </td>
              <td style="width:70px">
                <input
                  v-model.number="row.rewardVipLevel"
                  type="number"
                  min="0"
                  class="form-control form-control-sm"
                  :disabled="!tasksEnabled"
                />
              </td>
              <td style="width:70px">
                <input
                  v-model.number="row.rewardVipDays"
                  type="number"
                  min="0"
                  class="form-control form-control-sm"
                  :disabled="!tasksEnabled"
                />
              </td>
              <td><input v-model="row.type" class="form-control form-control-sm" :disabled="!tasksEnabled" /></td>
              <td style="width:130px">
                <select v-model="row.scope" class="form-select form-select-sm" :disabled="!tasksEnabled">
                  <option value="global">{{ t('common.global') }}</option>
                  <option value="room">{{ t('common.room') }}</option>
                  <option value="agency">{{ t('common.agency') }}</option>
                </select>
              </td>
              <td>
                <input
                  v-if="row.scope === 'room'"
                  v-model="row.roomId"
                  class="form-control form-control-sm"
                  :placeholder="t('tasks.roomPlaceholder')"
                  :disabled="!tasksEnabled"
                />
                <input
                  v-else-if="row.scope === 'agency'"
                  v-model="row.agencyId"
                  class="form-control form-control-sm"
                  :placeholder="t('tasks.agencyPlaceholder')"
                  :disabled="!tasksEnabled"
                />
                <span v-else class="text-muted">—</span>
              </td>
              <td>
                <button
                  class="btn btn-sm btn-ghost"
                  type="button"
                  :disabled="!tasksEnabled"
                  @click="askRemoveTask(idx)"
                >
                  <i class="bi bi-trash"></i>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="cosmeticPickerOpen" class="modal fade show d-block task-cosmetic-modal" tabindex="-1" role="dialog" aria-modal="true">
      <div class="modal-dialog modal-dialog-centered modal-xl">
        <div class="modal-content glass-modal">
          <div class="modal-header">
            <div>
              <h5 class="modal-title">اختيار مكافأة من المتجر</h5>
              <div class="small text-muted mt-1">اختر العنصر من صورته ومعاينته؛ لا حاجة لكتابة أي كود.</div>
            </div>
            <button type="button" class="btn-close" aria-label="إغلاق" @click="cosmeticPickerOpen = false"></button>
          </div>
          <div class="modal-body">
            <div class="d-flex flex-wrap align-items-center gap-2 mb-3">
              <div class="input-group" style="max-width:420px">
                <span class="input-group-text"><i class="bi bi-search"></i></span>
                <input v-model="cosmeticSearch" class="form-control" placeholder="ابحث باسم الإطار أو الدخولية…" />
              </div>
              <span class="small text-muted">{{ filteredCosmetics.length }} عنصر</span>
            </div>
            <LoadingSpinner v-if="cosmeticsLoading" />
            <div v-else-if="!filteredCosmetics.length" class="text-center text-muted py-5">
              لا توجد عناصر مطابقة. تأكد من إضافة العناصر في إدارة المتجر.
            </div>
            <div v-else class="task-cosmetic-grid">
              <button
                v-for="item in filteredCosmetics"
                :key="item.id || item.code"
                type="button"
                class="task-cosmetic-option"
                :class="{ 'is-current': selectedCosmeticCode === item.code }"
                @click="selectCosmetic(item)"
              >
                <span class="task-cosmetic-preview">
                  <img v-if="item.previewUrl || item.animationUrl" :src="item.previewUrl || item.animationUrl" :alt="item.name" loading="lazy" />
                  <i v-else class="bi bi-image text-muted"></i>
                </span>
                <span class="task-cosmetic-name">{{ item.name || item.code }}</span>
                <span class="task-cosmetic-type">{{ cosmeticTypeLabel(item.type) }}</span>
                <span v-if="selectedCosmeticCode === item.code" class="task-cosmetic-selected"><i class="bi bi-check-circle-fill"></i> محدد</span>
              </button>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-ghost" @click="cosmeticPickerOpen = false">إلغاء</button>
          </div>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemoveTask"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { tasksApi, cosmeticsApi, dailyLoginRewardsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import './tasks-cosmetic-picker.css'

const { t } = useI18n()

const tasks = ref([])
const tasksEnabled = ref(true)
const loading = ref(false)
const saving = ref(false)
const toggling = ref(false)
const error = ref('')
const success = ref('')
const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingRemoveIdx = ref(-1)
const cosmetics = ref([])
const cosmeticsLoading = ref(false)
const dailyLoginEnabled = ref(true)
const dailyLoginDays = ref([
  { day: 1, type: 'coins', amount: 100, label: 'عملات ذهبية' },
  { day: 2, type: 'coins', amount: 150, label: 'مكافأة يومية' },
  { day: 3, type: 'coins', amount: 200, label: 'مكافأة يومية' },
  { day: 4, type: 'coins', amount: 200, label: 'عملات ذهبية' },
  { day: 5, type: 'coins', amount: 250, label: 'مكافأة يومية' },
  { day: 6, type: 'coins', amount: 300, label: 'عملات ذهبية' },
  { day: 7, type: 'coins', amount: 500, label: 'الجائزة الكبرى' },
])
const dailyLoginLoading = ref(false)
const dailyLoginSaving = ref(false)
const dailyLoginError = ref('')
const dailyLoginSuccess = ref('')
const cosmeticTargetMode = ref('task')
const cosmeticPickerOpen = ref(false)
const cosmeticTargetIdx = ref(-1)
const selectedCosmeticCode = ref('')
const cosmeticSearch = ref('')

const filteredCosmetics = computed(() => {
  const q = cosmeticSearch.value.trim().toLocaleLowerCase()
  return cosmetics.value.filter((item) => {
    if (item.isActive === false) return false
    const searchable = [item.name, item.code, item.type, item.description].filter(Boolean).join(' ').toLocaleLowerCase()
    return !q || searchable.includes(q)
  })
})

function cosmeticByCode(code) {
  if (!code) return null
  return cosmetics.value.find((item) => item.code === code) || null
}

function cosmeticTypeLabel(type) {
  const labels = {
    frame: 'إطار',
    entry_effect: 'دخوليّة',
    entrance: 'دخوليّة',
    avatar_frame: 'إطار صورة',
    profile_frame: 'إطار ملف شخصي',
    badge: 'شارة',
    vip_badge: 'شارة VIP',
    room_background: 'خلفية غرفة',
  }
  return labels[String(type || '').toLowerCase()] || 'عنصر تجميلي'
}

async function loadCosmetics() {
  cosmeticsLoading.value = true
  const { data, error: err } = await cosmeticsApi.adminList()
  cosmeticsLoading.value = false
  if (err) {
    toast().danger('تعذر تحميل عناصر المتجر: ' + err.message)
    return
  }
  const payload = data?.data ?? data
  cosmetics.value = Array.isArray(payload) ? payload : Array.isArray(payload?.items) ? payload.items : []
}

function openCosmeticPicker(idx, mode = 'task') {
  cosmeticTargetMode.value = mode
  cosmeticTargetIdx.value = idx
  selectedCosmeticCode.value = mode === 'daily'
    ? dailyLoginDays.value[idx]?.cosmeticCode || ''
    : tasks.value[idx]?.rewardCosmeticCode || ''
  cosmeticSearch.value = ''
  cosmeticPickerOpen.value = true
  if (!cosmetics.value.length) loadCosmetics()
}

function selectCosmetic(item) {
  if (cosmeticTargetIdx.value < 0 || !item?.code) return
  if (cosmeticTargetMode.value === 'daily') {
    const reward = dailyLoginDays.value[cosmeticTargetIdx.value]
    if (reward) {
      reward.cosmeticCode = item.code
      reward.label = item.name || reward.label
    }
  } else {
    const row = tasks.value[cosmeticTargetIdx.value]
    if (row) row.rewardCosmeticCode = item.code
  }
  selectedCosmeticCode.value = item.code
  cosmeticPickerOpen.value = false
}

async function loadDailyLogin() {
  dailyLoginLoading.value = true
  dailyLoginError.value = ''
  const { data, error: err } = await dailyLoginRewardsApi.get()
  dailyLoginLoading.value = false
  if (err) {
    dailyLoginError.value = err.message || 'تعذر تحميل إعدادات تسجيل الدخول اليومي'
    return
  }
  const payload = data?.data ?? data ?? {}
  dailyLoginEnabled.value = payload.enabled !== false
  if (Array.isArray(payload.days) && payload.days.length === 7) {
    dailyLoginDays.value = payload.days.map((day, i) => ({ ...dailyLoginDays.value[i], ...day, day: i + 1 }))
  }
}

async function saveDailyLogin() {
  dailyLoginSaving.value = true
  dailyLoginError.value = ''
  dailyLoginSuccess.value = ''
  const { error: err } = await dailyLoginRewardsApi.save({
    enabled: dailyLoginEnabled.value,
    days: dailyLoginDays.value,
  })
  dailyLoginSaving.value = false
  if (err) {
    dailyLoginError.value = err.message || 'تعذر حفظ مكافآت تسجيل الدخول'
    toast().danger(dailyLoginError.value)
    return
  }
  dailyLoginSuccess.value = 'تم حفظ مكافآت تسجيل الدخول اليومية بنجاح'
  toast().success(dailyLoginSuccess.value)
}

async function load() {
  loading.value = true
  const { data, error: err } = await tasksApi.list()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const payload = data?.data ?? data ?? {}
  tasks.value = Array.isArray(payload.items)
    ? payload.items.map((t) => ({ scope: 'global', roomId: '', agencyId: '', ...t }))
    : []
  if (typeof payload.enabled === 'boolean') {
    tasksEnabled.value = payload.enabled
  }
}

async function toggleEnabled() {
  const next = !!tasksEnabled.value
  toggling.value = true
  error.value = ''
  const { error: err } = await tasksApi.setEnabled(next)
  toggling.value = false
  if (err) {
    tasksEnabled.value = !next
    error.value = err.message
    toast().danger(err.message)
    return
  }
  success.value = next ? t('tasks.enabledSavedOn') : t('tasks.enabledSavedOff')
  toast().success(success.value)
}

function addRow() {
  if (!tasksEnabled.value) return
  tasks.value.push({
    id: `t${tasks.value.length + 1}`,
    title: t('tasks.newTask'),
    rewardPoints: 50,
    rewardSilver: 0,
    rewardCosmeticCode: '',
    rewardCosmeticDays: 2,
    rewardVipLevel: 0,
    rewardVipDays: 2,
    type: 'custom',
    scope: 'global',
    roomId: '',
    agencyId: '',
  })
}

function askRemoveTask(idx) {
  if (!tasksEnabled.value) return
  pendingRemoveIdx.value = idx
  const title = tasks.value[idx]?.title || `#${idx + 1}`
  confirmMsg.value = `${t('common.delete')} — ${title}؟`
  confirmOpen.value = true
}

function doRemoveTask() {
  const idx = pendingRemoveIdx.value
  if (idx < 0) return
  tasks.value.splice(idx, 1)
  pendingRemoveIdx.value = -1
  toast().success(t('common.delete'))
}

async function save() {
  if (!tasksEnabled.value) return
  saving.value = true
  error.value = ''
  const { error: err } = await tasksApi.save(tasks.value, tasksEnabled.value)
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    success.value = t('tasks.saved')
    toast().success(success.value)
  }
}

onMounted(() => { load(); loadDailyLogin() })
</script>
