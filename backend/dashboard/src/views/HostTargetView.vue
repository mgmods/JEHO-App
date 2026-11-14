<template>
  <div>
    <PageHeader
      title="تارجيت المضيف (أسبوعي / شهري)"
      subtitle="الحد الأدنى لراتب المضيف $10 — عمولة الوكالة + السحب يعتمدان على المراحل. ١ كوين تارجت = ١ ألماسة. يظهر للمضيف وإدارة الوكالة فقط."
    >
      <template #actions>
        <button class="btn btn-ghost btn-sm" type="button" :disabled="saving" @click="loadOfficialLadder">
          تحميل الجدول الرسمي (من $10 · 28 مرحلة)
        </button>
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
            <option value="diamonds">ألماس الهدايا (جدول الرواتب: ١ كوين = ١ ألماسة)</option>
            <option value="gift_coins">عملات الهدايا</option>
          </select>
        </div>
        <div class="col-md-4">
          <label class="form-label">فترة التارجت</label>
          <select v-model="hostTarget.period" class="form-select">
            <option value="monthly">شهري (تقويم UTC)</option>
            <option value="weekly">أسبوعي (أسبوع ISO UTC)</option>
          </select>
          <div class="form-text">
            التقدّم يُصفَّر تلقائياً عند بداية فترة جديدة. التارجت يظهر للمضيف + إدارة الوكالة فقط.
          </div>
        </div>
      </div>

      <div class="table-responsive mb-3">
        <table class="table table-sm align-middle mb-0">
          <thead>
            <tr>
              <th>#</th>
              <th>العنوان</th>
              <th>التارجت (كوينز)</th>
              <th>١ كوين = ١ ألماسة</th>
              <th>راتب المضيف $</th>
              <th>راتب الوكيل $</th>
              <th>الإجمالي $</th>
              <th>عملات مكافأة</th>
              <th>ألماس مكافأة</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(stage, idx) in hostTarget.stages" :key="stage.id || idx">
              <td>{{ idx + 1 }}</td>
              <td><input v-model="stage.title" class="form-control form-control-sm" style="min-width:6rem" /></td>
              <td>
                <input
                  v-model.number="stage.threshold"
                  type="number"
                  min="1"
                  class="form-control form-control-sm"
                  style="min-width:7rem"
                />
              </td>
              <td class="small text-muted">{{ formatNumber(stage.threshold) }}</td>
              <td>
                <input
                  v-model.number="stage.hostSalaryUsd"
                  type="number"
                  min="10"
                  step="1"
                  class="form-control form-control-sm"
                  style="min-width:5rem"
                  @change="recalcTotal(stage)"
                />
              </td>
              <td>
                <input
                  v-model.number="stage.agentSalaryUsd"
                  type="number"
                  min="0"
                  step="1"
                  class="form-control form-control-sm"
                  style="min-width:5rem"
                  @change="recalcTotal(stage)"
                />
              </td>
              <td class="fw-semibold">${{ Number(stage.totalUsd || 0) }}</td>
              <td>
                <input v-model.number="stage.rewardCoins" type="number" min="0" class="form-control form-control-sm" style="min-width:4.5rem" />
              </td>
              <td>
                <input v-model.number="stage.rewardDiamonds" type="number" min="0" class="form-control form-control-sm" style="min-width:4.5rem" />
              </td>
              <td>
                <button class="btn btn-sm btn-outline-danger" type="button" @click="hostTarget.stages.splice(idx, 1)">
                  حذف
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <button class="btn btn-ghost btn-sm" type="button" @click="addStage">+ مرحلة</button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { settingsApi } from '@/api'
import { formatNumber } from '@/composables/useUtils'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { OFFICIAL_SALARY_LADDER, HOST_TARGET_MIN_USD } from '@/data/hostSalaryLadder'

const loading = ref(true)
const saving = ref(false)
const error = ref('')
const success = ref('')
const hostTarget = reactive({
  enabled: true,
  currency: 'diamonds',
  period: 'monthly',
  stages: [],
})

function recalcTotal(stage) {
  stage.totalUsd =
    Math.max(0, Number(stage.hostSalaryUsd) || 0) + Math.max(0, Number(stage.agentSalaryUsd) || 0)
}

function mapStage(s, i) {
  const host = Math.max(0, Number(s?.hostSalaryUsd) || 0)
  const agent = Math.max(0, Number(s?.agentSalaryUsd) || 0)
  return {
    id: String(s?.id || `stage_${i + 1}`),
    title: s?.title ? String(s.title) : `مرحلة ${i + 1}`,
    threshold: Math.max(0, Number(s?.threshold) || 0),
    rewardCoins: Math.max(0, Math.floor(Number(s?.rewardCoins) || 0)),
    rewardDiamonds: Math.max(0, Math.floor(Number(s?.rewardDiamonds) || 0)),
    hostSalaryUsd: host,
    agentSalaryUsd: agent,
    totalUsd: Math.max(0, Number(s?.totalUsd) || host + agent),
    coinEqualsDiamond: s?.coinEqualsDiamond !== false,
    rewardCosmeticCode: s?.rewardCosmeticCode ? String(s.rewardCosmeticCode) : '',
    rewardCosmeticDays: Math.max(0, Math.floor(Number(s?.rewardCosmeticDays) || 7)),
    rewardVipLevel: Math.max(0, Math.floor(Number(s?.rewardVipLevel) || 0)),
    rewardVipDays: Math.max(0, Math.floor(Number(s?.rewardVipDays) || 7)),
  }
}

function addStage() {
  const n = hostTarget.stages.length + 1
  const last = hostTarget.stages[hostTarget.stages.length - 1]
  const nextTh = last ? Math.max(150000, Number(last.threshold) || 0) + 50000 : 150000
  const nextHost = Math.max(HOST_TARGET_MIN_USD, Number(last?.hostSalaryUsd) || HOST_TARGET_MIN_USD)
  hostTarget.stages.push(
    mapStage(
      {
        id: `stage_${n}`,
        title: `مرحلة ${n}`,
        threshold: nextTh,
        hostSalaryUsd: nextHost,
        agentSalaryUsd: Math.max(0, Number(last?.agentSalaryUsd) || 2),
        totalUsd: 0,
      },
      n - 1,
    ),
  )
  recalcTotal(hostTarget.stages[hostTarget.stages.length - 1])
}

function loadOfficialLadder() {
  hostTarget.enabled = true
  hostTarget.currency = 'diamonds'
  hostTarget.stages = OFFICIAL_SALARY_LADDER.map((row, i) =>
    mapStage(
      {
        id: `salary_v2_${row.stage}`,
        title: `مرحلة ${row.stage}`,
        threshold: row.targetCoins,
        hostSalaryUsd: row.hostSalaryUsd,
        agentSalaryUsd: row.agentSalaryUsd,
        totalUsd: row.totalUsd,
        coinEqualsDiamond: true,
        rewardCoins: 0,
        rewardDiamonds: 0,
      },
      i,
    ),
  )
  success.value = `تم تحميل الجدول الرسمي (أقل تارجت مضيف $${HOST_TARGET_MIN_USD}) — اضغط حفظ`
  toast().success(success.value)
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
    if (!raw) {
      loadOfficialLadder()
      return
    }
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    hostTarget.enabled = parsed.enabled !== false
    hostTarget.currency = parsed.currency === 'gift_coins' ? 'gift_coins' : 'diamonds'
    hostTarget.period = parsed.period === 'weekly' ? 'weekly' : 'monthly'
    hostTarget.stages = Array.isArray(parsed.stages) ? parsed.stages.map(mapStage) : []
    const minHost = hostTarget.stages
      .map((s) => Number(s.hostSalaryUsd) || 0)
      .filter((n) => n > 0)
    const hasSubMin = minHost.length > 0 && Math.min(...minHost) < HOST_TARGET_MIN_USD
    const hasSalary = hostTarget.stages.some(
      (s) => Number(s.hostSalaryUsd) > 0 || Number(s.agentSalaryUsd) > 0,
    )
    if (!hostTarget.stages.length || !hasSalary || hasSubMin || hostTarget.stages.length < 20) {
      loadOfficialLadder()
    }
  } catch {
    error.value = 'تعذر قراءة إعدادات التارجيت'
  }
}

async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  hostTarget.stages.forEach(recalcTotal)
  const bad = hostTarget.stages.filter((s) => {
    const h = Number(s.hostSalaryUsd) || 0
    return h > 0 && h < HOST_TARGET_MIN_USD
  })
  if (bad.length) {
    saving.value = false
    error.value = `رفض الحفظ: مراحل برواتب مضيف أقل من $${HOST_TARGET_MIN_USD} (مثل $1/$2). حمّل الجدول الرسمي أو ارفع القيم.`
    toast().danger(error.value)
    return
  }
  if (!hostTarget.stages.length) {
    saving.value = false
    error.value = 'لا توجد مراحل — حمّل الجدول الرسمي أولاً'
    toast().danger(error.value)
    return
  }
  const payload = {
    enabled: !!hostTarget.enabled,
    currency: hostTarget.currency,
    period: hostTarget.period === 'weekly' ? 'weekly' : 'monthly',
    stages: hostTarget.stages,
    ladderVersion: '20260807-min10usd-v2',
    minHostTargetUsd: HOST_TARGET_MIN_USD,
  }
  const result = await settingsApi.update({
    host_monthly_target: JSON.stringify(payload),
  })
  saving.value = false
  if (result.error) {
    error.value = result.error.message
    toast().danger(result.error.message)
  } else {
    success.value = 'تم حفظ تارجيت المضيف (حد أدنى $10) — يعتمد عليه سحب المضيف وعمولة الوكالة'
    toast().success(success.value)
  }
}

onMounted(load)
</script>
