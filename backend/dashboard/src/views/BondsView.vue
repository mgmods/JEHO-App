<template>
  <div>
    <PageHeader
      title="نظام الارتباطات"
      subtitle="أيقونات وأنواع الارتباط (أخوة / معجبين / حب / أزواج) + تكلفة هدية الارتباط"
    />
    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" />
    <AlertMessage v-if="ok" :message="ok" type="success" class="mb-3" />

    <div class="glass p-3 mb-3">
      <h5 class="mb-3">أيقونات الارتباط</h5>
      <div class="row g-3">
        <div v-for="b in bonds" :key="b.key" class="col-md-6">
          <label class="form-label">{{ b.label }} — رابط الأيقونة</label>
          <input v-model.trim="icons[b.key]" class="form-control" :placeholder="`/assets/bonds/${b.key}.png`" />
        </div>
      </div>
    </div>

    <div class="glass p-3 mb-3">
      <h5 class="mb-3">تكلفة هدية الارتباط (كوينز — تُخصم من طالب الارتباط عند الموافقة)</h5>
      <div class="row g-3">
        <div v-for="b in bonds" :key="'c-' + b.key" class="col-md-4">
          <label class="form-label">{{ b.label }}</label>
          <input v-model.number="costs[b.key]" type="number" min="0" class="form-control" />
        </div>
      </div>
    </div>

    <button class="btn btn-aurora" type="button" :disabled="saving" @click="save">
      {{ saving ? 'جاري الحفظ…' : 'حفظ' }}
    </button>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { settingsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'

const bonds = [
  { key: 'sibling', label: 'أخوة' },
  { key: 'fans', label: 'معجبين' },
  { key: 'love', label: 'حب' },
  { key: 'couple', label: 'أزواج' },
  { key: 'relation', label: 'علاقة عامة' },
  { key: 'guardian', label: 'وصاية' },
  { key: 'friend', label: 'أصدقاء' },
]

const icons = reactive({})
const costs = reactive({})
bonds.forEach((b) => {
  icons[b.key] = ''
  costs[b.key] = b.key === 'couple' ? 2000 : b.key === 'love' ? 1000 : b.key === 'fans' ? 200 : 500
})

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const ok = ref('')

async function load() {
  loading.value = true
  const { data, error: err } = await settingsApi.get()
  loading.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
    return
  }
  const rows = Array.isArray(data) ? data : data?.items || []
  const iconRow = rows.find((r) => r.key === 'social.bond_icons')
  const costRow = rows.find((r) => r.key === 'social.bond_gift_coins')
  if (iconRow?.value) {
    try {
      Object.assign(icons, JSON.parse(iconRow.value))
    } catch { /* ignore */ }
  }
  if (costRow?.value) {
    try {
      Object.assign(costs, JSON.parse(costRow.value))
    } catch { /* ignore */ }
  }
}

async function save() {
  ok.value = ''
  error.value = ''
  saving.value = true
  const { error: err } = await settingsApi.update({
    'social.bond_icons': JSON.stringify({ ...icons }),
    'social.bond_gift_coins': JSON.stringify({ ...costs }),
  })
  saving.value = false
  if (err) {
    error.value = err.message
    toast().danger(err.message)
  } else {
    ok.value = 'تم الحفظ — التطبيق يقرأ الأيقونات والتكاليف مباشرة'
    toast().success(ok.value)
  }
}

onMounted(load)
</script>
