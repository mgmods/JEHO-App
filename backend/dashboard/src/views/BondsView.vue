<template>
  <div>
    <PageHeader
      title="نظام الارتباطات"
      subtitle="أيقونات وأنواع الارتباط (أخوة / معجبين / حب / أزواج) + تكلفة هدية الارتباط"
    />
    <AlertMessage v-if="error" :message="error" type="danger" class="mb-3" />
    <AlertMessage v-if="ok" :message="ok" type="success" class="mb-3" />

    <div class="widget-grid mb-3">
      <article v-for="b in bonds" :key="b.key" class="widget-card">
        <div class="widget-card-body">
          <h4 class="widget-card-title mb-2">{{ b.label }}</h4>
          <div class="bond-preview mb-2">
            <img v-if="icons[b.key]" :src="absUrl(icons[b.key])" alt="" />
            <span v-else class="text-muted small">بدون أيقونة</span>
          </div>
          <label class="form-label small">أيقونة</label>
          <input
            type="file"
            accept="image/*,.webp,.gif"
            class="form-control form-control-sm"
            @change="(e) => onIconFile(e, b.key)"
          />
          <div class="form-text">اختر ملف — بدون رابط</div>
          <button
            v-if="icons[b.key]"
            class="btn btn-sm btn-outline-danger mt-2"
            type="button"
            @click="icons[b.key] = ''"
          >
            مسح
          </button>
          <hr class="my-3 opacity-25" />
          <label class="form-label small">تكلفة (كوينز)</label>
          <input v-model.number="costs[b.key]" type="number" min="0" class="form-control" />
        </div>
      </article>
    </div>

    <button class="btn btn-aurora" type="button" :disabled="saving" @click="save">
      {{ saving ? 'جاري الحفظ…' : 'حفظ' }}
    </button>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { settingsApi, uploadsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'

const ORIGIN = 'https://api.adnova.bbs.tr'

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

const saving = ref(false)
const error = ref('')
const ok = ref('')

function absUrl(u) {
  if (!u) return ''
  if (String(u).startsWith('http')) return u
  return `${ORIGIN}${String(u).startsWith('/') ? '' : '/'}${u}`
}

async function onIconFile(e, key) {
  const file = e.target.files?.[0]
  if (!file) return
  const { data, error: err } = await uploadsApi.upload(file)
  if (err) {
    toast().danger(err.message)
    return
  }
  icons[key] = absUrl(data?.url || data?.data?.url || '')
  toast().success('تم رفع الأيقونة')
  e.target.value = ''
}

async function load() {
  const { data, error: err } = await settingsApi.get()
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

<style scoped>
.bond-preview {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(139, 92, 246, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.bond-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
</style>
