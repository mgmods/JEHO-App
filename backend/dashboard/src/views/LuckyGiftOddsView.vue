<template>
  <div dir="rtl">
    <PageHeader title="تحكّم هدايا الحظ" subtitle="إدارة احتمالات الفوز والخسارة والمردود من الخادم مباشرةً." />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <section class="glass p-3 mb-3">
      <div class="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
        <div>
          <h5 class="mb-1">الإعدادات العامة</h5>
          <div class="small text-secondary">تُطبّق على عمليات الإرسال الجديدة فور الحفظ. الخسارة X0 هي النسبة المتبقية تلقائياً.</div>
        </div>
        <div class="form-check form-switch">
          <input id="lucky-odds-enabled" v-model="config.enabled" class="form-check-input" type="checkbox" role="switch">
          <label for="lucky-odds-enabled" class="form-check-label fw-semibold">تفعيل مردود هدايا الحظ</label>
        </div>
      </div>
      <div class="row g-3 mb-3">
        <div class="col-sm-4 col-md-3">
          <label class="form-label" for="lucky-max-multiplier">أعلى مضاعف متاح (X)</label>
          <input id="lucky-max-multiplier" v-model.number="config.maxMultiplier" class="form-control" type="number" min="1" max="1000000">
          <div class="small text-secondary mt-1">حد حماية تقني: X1,000,000.</div>
        </div>
        <div class="col-sm-8 col-md-9 d-flex align-items-end">
          <div class="alert alert-info py-2 mb-0 w-100">X5 لهدية قيمتها 1,000 = مردود إجمالي 5,000 عملة، شاملاً قيمة الهدية. لا تُضاف القيمة الأصلية مرة ثانية.</div>
        </div>
      </div>

      <div class="d-flex justify-content-between align-items-center mb-2">
        <div>
          <h6 class="mb-0">نطاقات المضاعفات واحتمالاتها</h6>
          <div class="small text-secondary">احتمال كل نطاق يُوزّع بالتساوي على المضاعفات الصحيحة داخله.</div>
        </div>
        <button class="btn btn-outline-primary btn-sm" type="button" @click="addBand">إضافة نطاق</button>
      </div>
      <div class="table-responsive">
        <table class="table align-middle">
          <thead><tr><th>من X</th><th>إلى X</th><th>الاحتمال %</th><th>إجراء</th></tr></thead>
          <tbody>
            <tr v-for="(band, index) in activeBands" :key="index">
              <td><input v-model.number="band.minMultiplier" class="form-control" type="number" min="1" :max="config.maxMultiplier"></td>
              <td><input v-model.number="band.maxMultiplier" class="form-control" type="number" min="1" :max="config.maxMultiplier"></td>
              <td><input v-model.number="band.probability" class="form-control" type="number" min="0" max="100" step="0.01"></td>
              <td><button class="btn btn-sm btn-outline-danger" type="button" :disabled="activeBands.length <= 1" @click="removeBand(index)">حذف</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="d-flex flex-wrap gap-3 align-items-center mb-3">
        <span class="badge text-bg-success">مجموع احتمالات الفوز: {{ winProbability.toFixed(2) }}%</span>
        <span class="badge text-bg-secondary">احتمال الخسارة X0: {{ Math.max(0, 100 - winProbability).toFixed(2) }}%</span>
        <span v-if="winProbability > 100" class="text-danger small">مجموع النسب يتجاوز 100%؛ صحّحه قبل الحفظ.</span>
      </div>

      <div class="border rounded-3 p-3 mb-3">
        <h6 class="mb-2">تخصيص هدية بعينها (اختياري)</h6>
        <div class="row g-2 align-items-end">
          <div class="col-md-8">
            <label class="form-label">هدية الحظ</label>
            <select v-model="selectedGiftId" class="form-select">
              <option value="">اختر هدية لتخصيصها</option>
              <option v-for="gift in gifts" :key="gift.id" :value="gift.id">{{ gift.name }} — {{ Number(gift.coinPrice || 0).toLocaleString() }} عملة</option>
            </select>
          </div>
          <div class="col-md-4">
            <button class="btn btn-outline-primary w-100" type="button" :disabled="!selectedGiftId" @click="toggleGiftOverride">
              {{ selectedHasOverride ? 'إزالة التخصيص واستخدام العام' : 'تخصيص هذه الهدية' }}
            </button>
          </div>
        </div>
        <div v-if="selectedHasOverride" class="small text-secondary mt-2">النطاقات أعلاه أصبحت خاصة بهذه الهدية. يمكنك تعديلها بشكل مستقل عن الإعدادات العامة.</div>
      </div>

      <div class="d-flex flex-wrap gap-2 justify-content-end">
        <button class="btn btn-outline-secondary" type="button" :disabled="loading" @click="load">إعادة تحميل</button>
        <button class="btn btn-outline-primary" type="button" :disabled="saving" @click="runSimulation">محاكاة قبل التطبيق</button>
        <button class="btn btn-aurora" type="button" :disabled="saving || winProbability > 100" @click="save"> {{ saving ? 'جارٍ الحفظ…' : 'حفظ وتطبيق فوراً' }} </button>
      </div>
      <div v-if="simulation" class="mt-3 p-3 border rounded-3">
        <h6>نتيجة المحاكاة — {{ Number(simulation.iterations || 0).toLocaleString() }} عملية افتراضية</h6>
        <div class="row g-2">
          <div class="col-6 col-lg-3"><div class="small text-secondary">خسارة X0</div><strong>{{ simulation.lossPercent }}% ({{ Number(simulation.lossCount).toLocaleString() }})</strong></div>
          <div class="col-6 col-lg-3"><div class="small text-secondary">فوز</div><strong>{{ simulation.winPercent }}% ({{ Number(simulation.winCount).toLocaleString() }})</strong></div>
          <div class="col-6 col-lg-3"><div class="small text-secondary">متوسط المردود لكل إرسال</div><strong>{{ Number(simulation.averagePayoutPerSend).toLocaleString() }}</strong></div>
          <div class="col-6 col-lg-3"><div class="small text-secondary">العائد المتوقع من قيمة الهدايا</div><strong>{{ simulation.expectedReturnPercent }}%</strong></div>
        </div>
        <div class="small text-secondary mt-2">المحاكاة تقديرية وليست ضماناً للنتائج الفعلية.</div>
      </div>
    </section>

    <section class="glass p-3 mb-3">
      <h5>الإحصائيات المالية الفعلية</h5>
      <div class="row g-3">
        <div class="col-6 col-lg"><div class="border rounded-3 p-3 h-100"><div class="small text-secondary">عمليات الإرسال</div><strong>{{ Number(stats.totalSends || 0).toLocaleString() }}</strong></div></div>
        <div class="col-6 col-lg"><div class="border rounded-3 p-3 h-100"><div class="small text-secondary">قيمة الهدايا المرسلة</div><strong>{{ Number(stats.totalGiftCoins || 0).toLocaleString() }}</strong></div></div>
        <div class="col-6 col-lg"><div class="border rounded-3 p-3 h-100"><div class="small text-secondary">مرات الفوز</div><strong>{{ Number(stats.winCount || 0).toLocaleString() }}</strong></div></div>
        <div class="col-6 col-lg"><div class="border rounded-3 p-3 h-100"><div class="small text-secondary">مرات الخسارة</div><strong>{{ Number(stats.lossCount || 0).toLocaleString() }}</strong></div></div>
        <div class="col-6 col-lg"><div class="border rounded-3 p-3 h-100"><div class="small text-secondary">المردود الإجمالي المحسوب</div><strong>{{ Number(stats.totalPayout || 0).toLocaleString() }}</strong></div></div>
      </div>
      <div class="small text-secondary mt-2">الإحصائيات مستخرجة من سجل الإرسال؛ المردود يُحسب وفق المضاعف المسجّل لكل عملية.</div>
    </section>

    <section class="glass p-3 mb-3">
      <h5>سجل النتائج الفعلية الأخيرة</h5>
      <div class="table-responsive">
        <table class="table table-sm align-middle">
          <thead><tr><th>التاريخ</th><th>الهدية</th><th>قيمة الإرسال</th><th>المضاعف</th><th>المردود الإجمالي</th><th>النتيجة</th></tr></thead>
          <tbody>
            <tr v-for="item in recentResults" :key="item.id">
              <td>{{ formatDate(item.createdAt) }}</td><td>{{ item.giftName }}</td>
              <td>{{ Number(item.totalCoins || 0).toLocaleString() }}</td>
              <td>{{ item.luckyMultiplier ? 'X' + item.luckyMultiplier : 'X0' }}</td>
              <td>{{ item.luckyMultiplier ? (Number(item.totalCoins || 0) * Number(item.luckyMultiplier)).toLocaleString() : '0' }}</td>
              <td><span class="badge" :class="item.luckyMultiplier ? 'text-bg-success' : 'text-bg-secondary'">{{ item.luckyMultiplier ? 'فوز' : 'خسارة' }}</span></td>
            </tr>
            <tr v-if="!recentResults.length"><td colspan="6" class="text-center text-secondary py-3">لا توجد نتائج مسجلة بعد.</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="glass p-3">
      <h5>سجل تغييرات الإعدادات</h5>
      <div class="small text-secondary mb-2">يُحفظ آخر 500 تغيير مع وقت التعديل وهوية مشرف الداشبورد.</div>
      <div v-for="(entry, index) in audit" :key="entry.changedAt + '-' + index" class="border-bottom py-2">
        <div class="d-flex flex-wrap justify-content-between gap-2">
          <strong>{{ formatDate(entry.changedAt) }}</strong>
          <span class="small text-secondary">معرّف المشرف: {{ entry.actorId }}</span>
        </div>
        <div class="small text-secondary">أعلى مضاعف: X{{ entry.next?.maxMultiplier }} · مجموع احتمالات الفوز: {{ probabilitySum(entry.next?.bands).toFixed(2) }}% · الخسارة: {{ Math.max(0, 100 - probabilitySum(entry.next?.bands)).toFixed(2) }}%</div>
      </div>
      <div v-if="!audit.length" class="text-secondary small">لم تُسجّل تغييرات بعد.</div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { giftsApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'

const defaultConfig = () => ({
  enabled: true,
  maxMultiplier: 100,
  bands: [
    { minMultiplier: 1, maxMultiplier: 5, probability: 5 },
    { minMultiplier: 6, maxMultiplier: 14, probability: 10 },
    { minMultiplier: 15, maxMultiplier: 25, probability: 15 },
    { minMultiplier: 26, maxMultiplier: 100, probability: 10 },
  ],
  giftOverrides: {},
})
const config = ref(defaultConfig())
const gifts = ref([])
const audit = ref([])
const recentResults = ref([])
const stats = ref({ totalSends: 0, totalGiftCoins: 0, winCount: 0, lossCount: 0, totalPayout: 0 })
const selectedGiftId = ref('')
const simulation = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const selectedHasOverride = computed(() => Boolean(selectedGiftId.value && config.value.giftOverrides?.[selectedGiftId.value]))
const activeBands = computed(() => {
  if (selectedHasOverride.value) return config.value.giftOverrides[selectedGiftId.value].bands
  return config.value.bands
})
const winProbability = computed(() => probabilitySum(activeBands.value))
function probabilitySum(bands) {
  return (Array.isArray(bands) ? bands : []).reduce((sum, band) => sum + (Number(band.probability) || 0), 0)
}
function clone(value) {
  return JSON.parse(JSON.stringify(value))
}
function formatDate(value) {
  if (!value) return '—'
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString()
}
async function load() {
  loading.value = true
  error.value = ''
  const result = await giftsApi.luckyOddsConfig()
  loading.value = false
  if (result.error) {
    error.value = result.error.message || 'تعذّر تحميل إعدادات هدايا الحظ'
    return
  }
  const data = result.data || {}
  config.value = { ...defaultConfig(), ...(data.config || {}), bands: clone(data.config?.bands || defaultConfig().bands), giftOverrides: clone(data.config?.giftOverrides || {}) }
  gifts.value = Array.isArray(data.gifts) ? data.gifts : []
  audit.value = Array.isArray(data.audit) ? data.audit : []
  recentResults.value = Array.isArray(data.recentResults) ? data.recentResults : []
  stats.value = data.stats || { totalSends: 0, totalGiftCoins: 0, winCount: 0, lossCount: 0, totalPayout: 0 }
  if (selectedGiftId.value && !gifts.value.some(g => g.id === selectedGiftId.value)) selectedGiftId.value = ''
}
function addBand() {
  const bands = activeBands.value
  const min = Math.max(1, Number(bands[bands.length - 1]?.maxMultiplier || 0) + 1)
  if (min > Number(config.value.maxMultiplier)) {
    error.value = 'ارفع الحد الأعلى للمضاعف أولاً لإضافة نطاق جديد.'
    return
  }
  bands.push({ minMultiplier: min, maxMultiplier: min, probability: 0 })
}
function removeBand(index) {
  if (activeBands.value.length > 1) activeBands.value.splice(index, 1)
}
function toggleGiftOverride() {
  if (!selectedGiftId.value) return
  if (selectedHasOverride.value) delete config.value.giftOverrides[selectedGiftId.value]
  else config.value.giftOverrides[selectedGiftId.value] = { enabled: true, bands: clone(config.value.bands) }
}
async function runSimulation() {
  error.value = ''
  const gift = gifts.value.find(g => g.id === selectedGiftId.value)
  const result = await giftsApi.simulateLuckyOdds({
    config: config.value,
    giftId: selectedGiftId.value || undefined,
    giftPrice: Number(gift?.coinPrice || 1000),
    iterations: 10000,
  })
  if (result.error) {
    error.value = result.error.message || 'تعذّرت محاكاة النتائج'
    return
  }
  simulation.value = result.data || null
}
async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  const result = await giftsApi.saveLuckyOddsConfig(clone(config.value))
  saving.value = false
  if (result.error) {
    error.value = result.error.message || 'تعذّر حفظ إعدادات هدايا الحظ'
    return
  }
  success.value = 'تم حفظ الإعدادات وتطبيقها على عمليات الإرسال الجديدة.'
  await load()
}
onMounted(load)
</script>
