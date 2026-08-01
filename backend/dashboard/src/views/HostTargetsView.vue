<template>
  <div>
    <PageHeader
      title="التارجيت الشهري"
      subtitle="مراحل تراكمية لألماس الهدايا المستلمة كل شهر — تُحدَّث مباشرة في التطبيق"
    />
    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" />
    <AlertMessage v-if="ok" :message="ok" type="success" class="mb-3" />

    <div class="alert alert-info border-0 mb-4">
      التقدّم يُحسب من مجموع <strong>ألماس الهدايا المستلمة</strong> خلال الشهر الحالي (UTC).
      عند الوصول لمرحلة، تُفتح مكافآت كل المراحل السابقة معها (تراكمي). المضيف يستلم المكافأة من زر «استلام» داخل التطبيق.
    </div>

    <div class="glass p-3 mb-3">
      <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
        <h5 class="mb-0">المراحل</h5>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-ghost" type="button" @click="addStage">
            <i class="bi bi-plus-lg me-1"></i> إضافة مرحلة
          </button>
          <button class="btn btn-sm btn-aurora" type="button" :disabled="saving" @click="save">
            {{ saving ? 'جاري الحفظ…' : 'حفظ' }}
          </button>
        </div>
      </div>

      <LoadingSpinner v-if="loading" />
      <div v-else class="table-responsive">
        <table class="table align-middle mb-0">
          <thead>
            <tr>
              <th>#</th>
              <th>الاسم</th>
              <th>ألماس مطلوب</th>
              <th>مكافأة كوينز</th>
              <th>مكافأة ألماس</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(s, i) in stages" :key="i">
              <td>{{ i + 1 }}</td>
              <td><input v-model.trim="s.label" class="form-control form-control-sm" /></td>
              <td>
                <input v-model.number="s.diamonds" type="number" min="1" class="form-control form-control-sm" />
              </td>
              <td>
                <input v-model.number="s.rewardCoins" type="number" min="0" class="form-control form-control-sm" />
              </td>
              <td>
                <input v-model.number="s.rewardDiamonds" type="number" min="0" class="form-control form-control-sm" />
              </td>
              <td>
                <button class="btn btn-sm btn-outline-danger" type="button" @click="removeStage(i)">
                  <i class="bi bi-trash"></i>
                </button>
              </td>
            </tr>
            <tr v-if="!stages.length">
              <td colspan="6" class="empty-state">لا توجد مراحل — أضف مرحلة ثم احفظ</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { settingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const KEY = 'host.monthly_target_stages'
const DEFAULTS = [
  { diamonds: 100000, rewardCoins: 5000, rewardDiamonds: 0, label: 'المرحلة 1' },
  { diamonds: 500000, rewardCoins: 25000, rewardDiamonds: 0, label: 'المرحلة 2' },
  { diamonds: 1000000, rewardCoins: 60000, rewardDiamonds: 0, label: 'المرحلة 3' },
  { diamonds: 5000000, rewardCoins: 350000, rewardDiamonds: 0, label: 'المرحلة 4' },
]

const stages = ref([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const ok = ref('')

function normalize(list) {
  return (Array.isArray(list) ? list : [])
    .map((s, i) => ({
      diamonds: Math.max(1, Math.floor(Number(s?.diamonds) || 0)),
      rewardCoins: Math.max(0, Math.floor(Number(s?.rewardCoins) || 0)),
      rewardDiamonds: Math.max(0, Math.floor(Number(s?.rewardDiamonds) || 0)),
      label: String(s?.label || `المرحلة ${i + 1}`).slice(0, 64),
    }))
    .filter((s) => s.diamonds > 0)
    .sort((a, b) => a.diamonds - b.diamonds)
}

async function load() {
  loading.value = true
  error.value = ''
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    stages.value = normalize(DEFAULTS)
    return
  }
  const rows = Array.isArray(data) ? data : data?.items || []
  const row = rows.find((r) => r.key === KEY)
  if (row?.value) {
    try {
      stages.value = normalize(JSON.parse(row.value))
      if (!stages.value.length) stages.value = normalize(DEFAULTS)
      return
    } catch {
      /* fall through */
    }
  }
  stages.value = normalize(DEFAULTS)
}

function addStage() {
  const last = stages.value[stages.value.length - 1]
  const nextDiamonds = last ? Math.floor(last.diamonds * 2) : 100000
  stages.value.push({
    diamonds: nextDiamonds,
    rewardCoins: 0,
    rewardDiamonds: 0,
    label: `المرحلة ${stages.value.length + 1}`,
  })
}

function removeStage(i) {
  stages.value.splice(i, 1)
}

async function save() {
  ok.value = ''
  error.value = ''
  const clean = normalize(stages.value)
  if (!clean.length) {
    error.value = 'أضف مرحلة واحدة على الأقل'
    toast().warning(error.value)
    return
  }
  stages.value = clean
  saving.value = true
  const { error: err } = await settingsApi.update({
    [KEY]: JSON.stringify(clean),
  })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    ok.value = 'تم حفظ مراحل التارجيت — التطبيق يقرأها مباشرة'
    toast().success(ok.value)
  }
}

onMounted(load)
</script>
