<template>
  <div>
    <PageHeader
      title="تارجيت المضيف الشهري"
      subtitle="مراحل منفصلة عن باقات الشحن. عند عبور العتبة تُمنح العملات/الألماس ويمكن إضافة إطار مؤقت أو VIP لعدد أيام."
    >
      <template #actions>
        <button class="btn btn-aurora btn-sm" type="button" :disabled="saving" @click="save">
          حفظ التارجيت
        </button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <LoadingSpinner v-if="loading" />

    <div v-else class="glass p-3 mb-3">
      <div class="form-check form-switch mb-3">
        <input id="hostTargetEnabled" v-model="hostTarget.enabled" class="form-check-input" type="checkbox" />
        <label class="form-check-label" for="hostTargetEnabled">تفعيل النظام</label>
      </div>

      <div class="row g-2 mb-3">
        <div class="col-md-4">
          <label class="form-label">عملة التقدّم</label>
          <select v-model="hostTarget.currency" class="form-select">
            <option value="diamonds">ألماس الهدايا</option>
            <option value="gift_coins">عملات الهدايا</option>
          </select>
        </div>
      </div>

      <div class="small text-muted mb-3">
        كود الإطار من صفحة المستحضرات (مثلاً frame_mikoo_…) · أيام الإطار/VIP افتراضياً 7.
      </div>

      <div v-for="(stage, idx) in hostTarget.stages" :key="stage.id || idx" class="border rounded p-2 mb-2">
        <div class="row g-2 align-items-end">
          <div class="col-md-2">
            <label class="form-label">العنوان</label>
            <input v-model="stage.title" class="form-control form-control-sm" />
          </div>
          <div class="col-md-1">
            <label class="form-label">العتبة</label>
            <input v-model.number="stage.threshold" type="number" min="1" class="form-control form-control-sm" />
          </div>
          <div class="col-md-1">
            <label class="form-label">عملات</label>
            <input v-model.number="stage.rewardCoins" type="number" min="0" class="form-control form-control-sm" />
          </div>
          <div class="col-md-1">
            <label class="form-label">ألماس</label>
            <input v-model.number="stage.rewardDiamonds" type="number" min="0" class="form-control form-control-sm" />
          </div>
          <div class="col-md-2">
            <label class="form-label">كود إطار/هدية</label>
            <input v-model="stage.rewardCosmeticCode" class="form-control form-control-sm" placeholder="frame_mikoo_…" />
          </div>
          <div class="col-md-1">
            <label class="form-label">أيام إطار</label>
            <input v-model.number="stage.rewardCosmeticDays" type="number" min="0" class="form-control form-control-sm" />
          </div>
          <div class="col-md-1">
            <label class="form-label">VIP</label>
            <input v-model.number="stage.rewardVipLevel" type="number" min="0" class="form-control form-control-sm" />
          </div>
          <div class="col-md-1">
            <label class="form-label">أيام VIP</label>
            <input v-model.number="stage.rewardVipDays" type="number" min="0" class="form-control form-control-sm" />
          </div>
          <div class="col-md-2">
            <button class="btn btn-sm btn-outline-danger" type="button" @click="hostTarget.stages.splice(idx, 1)">
              حذف
            </button>
          </div>
        </div>
      </div>

      <button class="btn btn-ghost btn-sm" type="button" @click="addStage">+ مرحلة</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { settingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const hostTarget = reactive({
  enabled: false,
  currency: 'diamonds',
  stages: [],
})

function addStage() {
  const n = hostTarget.stages.length + 1
  hostTarget.stages.push({
    id: `stage_${n}`,
    title: `مرحلة ${n}`,
    threshold: n * 1000,
    rewardCoins: 0,
    rewardDiamonds: 0,
    rewardCosmeticCode: '',
    rewardCosmeticDays: 7,
    rewardVipLevel: 0,
    rewardVipDays: 7,
  })
}

async function load() {
  loading.value = true
  error.value = ''
  const result = await settingsApi.get()
  loading.value = false
  if (result.error) {
    error.value = result.error.message
    return
  }
  const data = result.data?.data ?? result.data
  const rows = Array.isArray(data) ? data : []
  const map = Object.fromEntries(rows.map((row) => [row.key, row.value]))
  try {
    const raw = map.host_monthly_target
    if (!raw) return
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    hostTarget.enabled = !!parsed.enabled
    hostTarget.currency = parsed.currency === 'gift_coins' ? 'gift_coins' : 'diamonds'
    hostTarget.stages = Array.isArray(parsed.stages)
      ? parsed.stages.map((s, i) => ({
          id: String(s?.id || `stage_${i + 1}`),
          title: s?.title ? String(s.title) : `مرحلة ${i + 1}`,
          threshold: Math.max(0, Number(s?.threshold) || 0),
          rewardCoins: Math.max(0, Math.floor(Number(s?.rewardCoins) || 0)),
          rewardDiamonds: Math.max(0, Math.floor(Number(s?.rewardDiamonds) || 0)),
          rewardCosmeticCode: s?.rewardCosmeticCode ? String(s.rewardCosmeticCode) : '',
          rewardCosmeticDays: Math.max(0, Math.floor(Number(s?.rewardCosmeticDays) || 7)),
          rewardVipLevel: Math.max(0, Math.floor(Number(s?.rewardVipLevel) || 0)),
          rewardVipDays: Math.max(0, Math.floor(Number(s?.rewardVipDays) || 7)),
        }))
      : []
  } catch {
    error.value = 'تعذر قراءة إعدادات التارجيت'
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const payload = {
    enabled: !!hostTarget.enabled,
    currency: hostTarget.currency,
    stages: hostTarget.stages,
  }
  const result = await settingsApi.update({
    host_monthly_target: JSON.stringify(payload),
  })
  saving.value = false
  if (result.error) {
    error.value = result.error.message
    toast().danger(result.error.message)
  } else {
    success.value = 'تم حفظ تارجيت المضيف الشهري'
    toast().success(success.value)
  }
}

onMounted(load)
</script>
