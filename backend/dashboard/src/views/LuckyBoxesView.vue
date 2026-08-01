<template>
  <div>
    <PageHeader :title="t('luckyBoxes.title')" :subtitle="t('luckyBoxes.subtitle')">
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" @click="load" :disabled="loading">{{ t('common.refresh') }}</button>
        <button class="btn btn-aurora btn-sm" type="button" @click="startCreate">{{ t('luckyBoxes.new') }}</button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <LoadingSpinner v-if="loading" />

    <div v-else class="widget-grid">
      <div v-if="!boxes.length" class="glass p-4 empty-state">{{ t('app.none') }}</div>
      <article v-for="box in boxes" :key="box.id" class="widget-card">
        <div class="widget-card-body">
          <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
            <div class="min-w-0">
              <h3 class="widget-card-title text-truncate">{{ box.title }}</h3>
              <div class="widget-card-meta">{{ box.code }} · {{ box.kind }}</div>
            </div>
            <span class="badge" :class="box.isActive ? 'bg-success' : 'bg-secondary'">
              {{ box.isActive ? t('common.active') : t('common.off') }}
            </span>
          </div>
          <div class="small mb-2">{{ t('luckyBoxes.cost') }}: {{ box.costCoins || 0 }} {{ t('common.coins') }} · {{ t('luckyBoxes.dailyLimit') }}: {{ box.dailyLimitPerUser || 1 }}</div>
          <div class="widget-card-meta mb-3">{{ t('luckyBoxes.rewards') }}: {{ (box.rewardsJson || []).length }}</div>
          <div class="action-btns">
            <button class="btn btn-sm btn-outline-info" type="button" @click="edit(box)">{{ t('common.edit') }}</button>
            <button
              class="btn btn-sm"
              :class="box.isActive ? 'btn-outline-warning' : 'btn-outline-success'"
              type="button"
              :disabled="togglingId === box.id"
              @click="toggleActive(box)"
            >
              {{ box.isActive ? 'إيقاف' : 'تفعيل' }}
            </button>
            <button class="btn btn-sm btn-outline-danger" type="button" @click="askRemove(box)">{{ t('common.delete') }}</button>
          </div>
        </div>
      </article>
    </div>

    <div v-if="editing" class="glass p-4 mt-4">
      <h3 class="h6 fw-semibold mb-3">{{ form.id ? t('luckyBoxes.edit') : t('luckyBoxes.create') }}</h3>
      <div class="row g-3">
        <div class="col-md-4">
          <label class="form-label">{{ t('app.code') }}</label>
          <input v-model="form.code" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">{{ t('common.title') }}</label>
          <input v-model="form.title" class="form-control" />
        </div>
        <div class="col-md-4">
          <label class="form-label">{{ t('luckyBoxes.kind') }}</label>
          <select v-model="form.kind" class="form-select">
            <option value="free_daily">{{ t('luckyBoxes.freeDaily') }}</option>
            <option value="paid">{{ t('luckyBoxes.paid') }}</option>
            <option value="host_funded">{{ t('luckyBoxes.hostFunded') }}</option>
            <option value="agency_funded">{{ t('luckyBoxes.agencyFunded') }}</option>
          </select>
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('luckyBoxes.cost') }}</label>
          <input v-model.number="form.costCoins" type="number" min="0" class="form-control" />
        </div>
        <div class="col-md-3">
          <label class="form-label">{{ t('luckyBoxes.dailyLimit') }}</label>
          <input v-model.number="form.dailyLimitPerUser" type="number" min="1" class="form-control" />
        </div>
        <div class="col-md-3 form-check form-switch mt-4">
          <input id="boxActive" v-model="form.isActive" class="form-check-input" type="checkbox" />
          <label class="form-check-label" for="boxActive">{{ t('common.active') }}</label>
        </div>
        <div class="col-12">
          <label class="form-label">{{ t('luckyBoxes.rewardsJson') }}</label>
          <textarea v-model="rewardsText" class="form-control" rows="5" placeholder='[{"type":"coins","amount":100,"weight":70}]'></textarea>
        </div>
        <div class="col-12 d-flex gap-2">
          <button class="btn btn-aurora" type="button" :disabled="saving" @click="save">{{ t('common.save') }}</button>
          <button class="btn btn-ghost" type="button" @click="editing = false">{{ t('common.cancel') }}</button>
        </div>
      </div>
    </div>

    <ConfirmDialog
      v-model="confirmOpen"
      :title="t('common.delete')"
      :message="confirmMsg"
      @confirm="doRemove"
    />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { luckyBoxesApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

const { t } = useI18n()

const boxes = ref([])
const loading = ref(false)
const saving = ref(false)
const togglingId = ref(null)
const editing = ref(false)
const error = ref('')
const success = ref('')
const rewardsText = ref('[{"type":"coins","amount":100,"weight":70},{"type":"diamonds","amount":5,"weight":30}]')

const form = reactive({
  id: null,
  code: '',
  title: '',
  kind: 'free_daily',
  costCoins: 0,
  dailyLimitPerUser: 1,
  isActive: true,
})

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await luckyBoxesApi.adminList()
  loading.value = false
  if (err) {
    error.value = err.message
    boxes.value = []
    return
  }
  boxes.value = data?.items || data?.data || data || []
  if (!Array.isArray(boxes.value)) boxes.value = []
}

function startCreate() {
  editing.value = true
  Object.assign(form, {
    id: null,
    code: 'box_' + Date.now(),
    title: t('luckyBoxes.newTitle'),
    kind: 'free_daily',
    costCoins: 0,
    dailyLimitPerUser: 1,
    isActive: true,
  })
  rewardsText.value = '[{"type":"coins","amount":100,"weight":70},{"type":"diamonds","amount":5,"weight":30}]'
}

function edit(box) {
  editing.value = true
  Object.assign(form, {
    id: box.id,
    code: box.code,
    title: box.title,
    kind: box.kind,
    costCoins: box.costCoins || 0,
    dailyLimitPerUser: box.dailyLimitPerUser || 1,
    isActive: !!box.isActive,
  })
  rewardsText.value = JSON.stringify(box.rewardsJson || [], null, 2)
}

async function save() {
  let rewardsJson = []
  try {
    rewardsJson = JSON.parse(rewardsText.value || '[]')
  } catch {
    error.value = t('luckyBoxes.invalidJson')
    return
  }
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    id: form.id || undefined,
    code: form.code,
    title: form.title,
    kind: form.kind,
    costCoins: Number(form.costCoins || 0),
    dailyLimitPerUser: Number(form.dailyLimitPerUser || 1),
    isActive: !!form.isActive,
    rewardsJson,
  }
  const { error: err } = await luckyBoxesApi.adminUpsert(payload)
  saving.value = false
  if (err) {
    error.value = err.message
    return
  }
  success.value = t('luckyBoxes.saved')
  toast().success(t('luckyBoxes.saved'))
  editing.value = false
  await load()
}

async function toggleActive(box) {
  if (!box?.id) return
  togglingId.value = box.id
  error.value = ''
  success.value = ''
  const payload = {
    id: box.id,
    code: box.code,
    title: box.title,
    kind: box.kind,
    costCoins: Number(box.costCoins || 0),
    dailyLimitPerUser: Number(box.dailyLimitPerUser || 1),
    isActive: !box.isActive,
    rewardsJson: box.rewardsJson || [],
  }
  const { error: err } = await luckyBoxesApi.adminUpsert(payload)
  togglingId.value = null
  if (err) {
    error.value = err.message || 'تعذر تغيير حالة الصندوق'
    toast().danger(error.value)
    return
  }
  success.value = payload.isActive ? 'تم تفعيل الصندوق' : 'تم إيقاف الصندوق'
  toast().success(success.value)
  await load()
}

const confirmOpen = ref(false)
const confirmMsg = ref('')
const pendingDelete = ref(null)

function askRemove(box) {
  pendingDelete.value = box
  confirmMsg.value = t('luckyBoxes.confirmDelete')
  confirmOpen.value = true
}

async function doRemove() {
  const box = pendingDelete.value
  pendingDelete.value = null
  if (!box?.id) {
    error.value = 'معرّف الصندوق غير صالح'
    return
  }
  error.value = ''
  const { error: err } = await luckyBoxesApi.adminDelete(box.id)
  if (err) {
    error.value = err.message || 'تعذر حذف الصندوق'
    toast().danger(error.value)
    return
  }
  success.value = t('luckyBoxes.deleted')
  toast().success(success.value)
  await load()
}

onMounted(load)
</script>
