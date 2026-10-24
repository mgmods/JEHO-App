<template>
  <div class="daily-rewards-page">
    <PageHeader title="مكافآت تسجيل الدخول اليومية" subtitle="إدارة مستقلة لمكافآت الأيام السبعة ومعاينتها قبل الحفظ." />
    <div class="daily-rewards-toolbar">
      <div class="daily-rewards-status">
        <span class="status-dot" :class="{ off: !enabled }"></span>
        <div><strong>{{ enabled ? 'النظام مفعّل' : 'النظام متوقف' }}</strong><small>تُطبّق الإعدادات بعد حفظها والتحقق منها</small></div>
      </div>
      <label class="switch-label"><input v-model="enabled" type="checkbox"> تفعيل المكافآت</label>
      <button class="save-btn" :disabled="loading || saving" @click="save">{{ saving ? 'جارٍ الحفظ…' : 'حفظ الإعدادات' }}</button>
    </div>
    <div v-if="error" class="notice error">{{ error }}</div>
    <div v-if="success" class="notice success">{{ success }}</div>
    <div v-if="loading" class="loading">جارٍ تحميل المكافآت…</div>
    <section v-else class="reward-panel">
      <header class="panel-title"><div><span class="eyebrow">DAILY REWARDS · 7 DAYS</span><h2>مكافآت الأسبوع</h2><p>اختر عملات أو عنصرًا تجميليًا من المتجر لكل يوم. تظهر المعاينة كما ستُعرض للمستخدم.</p></div><span class="week-chip"><i class="bi bi-calendar-week"></i> ٧ أيام</span></header>
      <div class="reward-grid">
        <article v-for="(day, idx) in days" :key="day.day" class="reward-card">
          <div class="reward-card-top"><span class="day-pill">اليوم {{ day.day }}</span><span class="card-number">{{ String(day.day).padStart(2,'0') }}</span></div>
          <div class="reward-art">
            <template v-if="day.type === 'cosmetic' && cosmetic(day.cosmeticCode)">
              <img :src="cosmetic(day.cosmeticCode).previewUrl || cosmetic(day.cosmeticCode).animationUrl" :alt="cosmetic(day.cosmeticCode).name">
            </template>
            <template v-else-if="day.type === 'coins'">
              <div class="coin-art"><i class="bi bi-coin"></i><strong>{{ Number(day.amount || 0).toLocaleString('en-US') }}</strong><small>كوينز</small></div>
            </template>
            <template v-else><i class="bi bi-gift empty-art"></i><small>اختر هدية للمعاينة</small></template>
          </div>
          <div class="reward-summary"><strong>{{ day.type === 'coins' ? 'عملات' : (cosmetic(day.cosmeticCode)?.name || 'عنصر تجميلي') }}</strong><small>{{ day.type === 'coins' ? 'مكافأة رصيد' : cosmeticTypeLabel(cosmetic(day.cosmeticCode)?.type) }}</small></div>
          <label class="field-label">نوع المكافأة</label>
          <select v-model="day.type" class="field" @change="clearNotices"><option value="coins">عملات</option><option value="cosmetic">إطار / دخولية / كرت اسم / عنصر متجر</option></select>
          <template v-if="day.type === 'coins'">
            <label class="field-label">عدد العملات</label><input v-model.number="day.amount" class="field" type="number" min="0" max="1000000000">
          </template>
          <template v-else>
            <button class="choose-btn" @click="openPicker(idx)"><i class="bi bi-images"></i>{{ cosmetic(day.cosmeticCode)?.name || 'اختيار هدية من المتجر' }}</button>
            <label class="field-label">مدة الصلاحية (أيام)</label><input v-model.number="day.durationDays" class="field" type="number" min="1" max="365">
          </template>
        </article>
      </div>
      <div class="panel-foot"><i class="bi bi-shield-check"></i><span>الاستلام يتم عبر خدمة المكافآت الحالية؛ لا يتم اعتبار اليوم مستلمًا إلا بعد نجاح طلب الاستلام من الخادم.</span></div>
    </section>
    <div v-if="pickerOpen" class="picker-backdrop" @click.self="pickerOpen = false">
      <section class="picker-modal">
        <header><div><h3>اختيار هدية من المتجر</h3><p>اختر من الصورة والاسم، وستظهر المعاينة على بطاقة اليوم.</p></div><button class="close-btn" @click="pickerOpen = false" aria-label="إغلاق">×</button></header>
        <input v-model="search" class="field search-field" placeholder="ابحث عن إطار أو دخولية أو كرت اسم…">
        <div v-if="cosmeticsLoading" class="loading">جارٍ تحميل عناصر المتجر…</div>
        <div v-else class="cosmetic-grid">
          <button v-for="item in filteredCosmetics" :key="item.code" class="cosmetic-option" :class="{ selected: days[target]?.cosmeticCode === item.code }" @click="choose(item)">
            <div class="cosmetic-art"><img v-if="item.previewUrl || item.animationUrl" :src="item.previewUrl || item.animationUrl" :alt="item.name"><i v-else class="bi bi-gift"></i></div>
            <strong>{{ item.name || item.code }}</strong><small>{{ cosmeticTypeLabel(item.type) }}</small>
            <span v-if="days[target]?.cosmeticCode === item.code" class="selected-tag">محدد ✓</span>
          </button>
          <div v-if="!filteredCosmetics.length" class="empty-picker">لا توجد عناصر مطابقة. تأكد من وجود عناصر نشطة في المتجر.</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { dailyLoginRewardsApi, cosmeticsApi } from '@/api'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'

const enabled = ref(true)
const loading = ref(true)
const saving = ref(false)
const cosmeticsLoading = ref(false)
const error = ref('')
const success = ref('')
const pickerOpen = ref(false)
const target = ref(0)
const search = ref('')
const cosmetics = ref([])
const days = ref(Array.from({ length: 7 }, (_, i) => ({ day: i + 1, type: 'coins', amount: [100,150,200,200,250,300,500][i], label: 'عملات ذهبية', cosmeticCode: '', durationDays: 7 })))
const filteredCosmetics = computed(() => {
  const q = search.value.trim().toLocaleLowerCase()
  return cosmetics.value.filter(x => x.isActive !== false && (!q || [x.name,x.code,x.type,x.description].filter(Boolean).join(' ').toLocaleLowerCase().includes(q)))
})
function cosmetic(code) { return cosmetics.value.find(x => x.code === code) || null }
function cosmeticTypeLabel(type) {
  const labels = { frame:'إطار', avatar_frame:'إطار صورة', profile_frame:'إطار ملف شخصي', entry_effect:'دخولية', entrance:'دخولية', entrance_effect:'دخولية', entry:'دخولية', name_card:'كرت اسم', namecard:'كرت اسم', name_plate:'كرت اسم', nameplate:'كرت اسم', title_card:'كرت اسم', badge:'شارة', vip_badge:'شارة VIP', room_background:'خلفية غرفة' }
  return labels[String(type || '').toLowerCase()] || 'عنصر تجميلي'
}
function clearNotices() { error.value = ''; success.value = '' }
async function load() {
  loading.value = true
  const [config, catalog] = await Promise.all([dailyLoginRewardsApi.get(), cosmeticsApi.adminList()])
  loading.value = false
  if (config.error) { error.value = config.error.message || 'تعذر تحميل الإعدادات'; return }
  const payload = config.data?.data ?? config.data ?? {}
  enabled.value = payload.enabled !== false
  if (Array.isArray(payload.days) && payload.days.length === 7) days.value = payload.days.map((d,i) => ({ ...days.value[i], ...d, day:i+1, amount:Math.max(0,Number(d.amount)||0), cosmeticCode:String(d.cosmeticCode||''), durationDays:Math.max(1,Math.min(365,Math.floor(Number(d.durationDays)||7))) }))
  if (!catalog.error) { const c = catalog.data?.data ?? catalog.data; cosmetics.value = Array.isArray(c) ? c : Array.isArray(c?.items) ? c.items : [] }
  else toast().danger('تم تحميل المكافآت، لكن تعذر تحميل صور المتجر: ' + catalog.error.message)
}
async function openPicker(idx) {
  target.value = idx; search.value = ''; pickerOpen.value = true
  if (!cosmetics.value.length) {
    cosmeticsLoading.value = true
    const { data, error: err } = await cosmeticsApi.adminList()
    cosmeticsLoading.value = false
    if (err) { error.value = err.message || 'تعذر تحميل عناصر المتجر'; return }
    const p = data?.data ?? data
    cosmetics.value = Array.isArray(p) ? p : Array.isArray(p?.items) ? p.items : []
  }
}
function choose(item) {
  if (!item?.code || !days.value[target.value]) return
  days.value[target.value].cosmeticCode = item.code
  days.value[target.value].label = item.name || 'عنصر تجميلي'
  pickerOpen.value = false; clearNotices()
}
async function save() {
  saving.value = true; clearNotices()
  const submitted = { enabled: enabled.value, days: days.value.map((d,i) => ({ ...d, day:i+1, amount:Math.max(0,Math.floor(Number(d.amount)||0)), durationDays:Math.max(1,Math.min(365,Math.floor(Number(d.durationDays)||7))), cosmeticCode:String(d.cosmeticCode||'').trim() })) }
  const { error: err } = await dailyLoginRewardsApi.save(submitted)
  if (err) { saving.value = false; error.value = err.message || 'تعذر حفظ المكافآت'; toast().danger(error.value); return }
  const { data, error: readErr } = await dailyLoginRewardsApi.get()
  saving.value = false
  if (readErr) { error.value = 'تم إرسال الحفظ لكن تعذر التحقق من القيم المحفوظة. أعد تحميل الصفحة للتأكد.'; return }
  const fresh = data?.data ?? data ?? {}
  const same = fresh.enabled === submitted.enabled && Array.isArray(fresh.days) && fresh.days.length === 7 && submitted.days.every((d,i) => {
    const saved = fresh.days.find(x => Number(x.day) === i+1)
    return saved && String(saved.type||'coins') === String(d.type||'coins') && Number(saved.amount||0) === Number(d.amount||0) && String(saved.cosmeticCode||'') === String(d.cosmeticCode||'') && Number(saved.durationDays||7) === Number(d.durationDays||7)
  })
  if (!same) { error.value = 'لم تتطابق القيم مع ما حُفظ على الخادم. لم نعتبر الحفظ ناجحًا؛ أعد المحاولة.'; await load(); return }
  days.value = fresh.days.map((d,i) => ({ ...days.value[i], ...d, day:i+1 })); enabled.value = fresh.enabled !== false
  success.value = 'تم حفظ مكافآت الأيام السبعة والتحقق من الإعدادات المحفوظة.'
  toast().success(success.value)
}
onMounted(load)
</script>

<style scoped>
.daily-rewards-page{color:#172a20}.daily-rewards-toolbar{display:flex;align-items:center;gap:18px;flex-wrap:wrap;background:#fff;border:1px solid #e0eee5;border-radius:18px;padding:16px 18px;margin:18px 0;box-shadow:0 8px 24px #163c2410}.daily-rewards-status{display:flex;align-items:center;gap:10px;margin-right:auto}.daily-rewards-status strong,.daily-rewards-status small{display:block}.daily-rewards-status small{color:#718078;margin-top:3px}.status-dot{width:10px;height:10px;border-radius:50%;background:#20a464;box-shadow:0 0 0 5px #20a46420}.status-dot.off{background:#aab4ae;box-shadow:0 0 0 5px #aab4ae22}.switch-label{display:flex;align-items:center;gap:8px;font-weight:700}.switch-label input{accent-color:#20864f;width:18px;height:18px}.save-btn{border:0;border-radius:12px;padding:11px 20px;background:#20864f;color:white;font-weight:800}.save-btn:disabled{opacity:.55}.reward-panel{background:#f8fbf9;border:1px solid #deebe2;border-radius:22px;padding:22px}.panel-title{display:flex;justify-content:space-between;align-items:center;gap:14px;margin-bottom:20px}.eyebrow{font-size:.7rem;letter-spacing:.15em;color:#20864f;font-weight:900}.panel-title h2{margin:5px 0;font-weight:900;color:#172a20}.panel-title p{margin:0;color:#6c7b72}.week-chip{background:#e7f6ed;color:#187847;border-radius:99px;padding:9px 13px;white-space:nowrap;font-weight:800}.reward-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.reward-card{background:#fff;border:1px solid #e1ebe4;border-radius:18px;padding:13px;box-shadow:0 7px 20px #173e2510;min-width:0}.reward-card-top{display:flex;justify-content:space-between;align-items:center}.day-pill{background:#e8f7ed;color:#187847;border-radius:8px;padding:5px 9px;font-size:.8rem;font-weight:800}.card-number{color:#d6e5da;font-size:1.25rem;font-weight:900}.reward-art{height:130px;margin:11px 0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:5px;border-radius:13px;background:#f7faf8;border:1px solid #edf3ef;overflow:hidden}.reward-art>img{width:100%;height:100%;object-fit:contain;padding:8px}.coin-art{display:flex;flex-direction:column;align-items:center;color:#c18c16}.coin-art i{font-size:2.2rem;color:#d9a92e}.coin-art strong{font-size:1.12rem}.coin-art small,.reward-art small{font-size:.76rem;color:#718078}.empty-art{font-size:2.1rem;color:#a7cbb4}.reward-summary{display:flex;flex-direction:column;gap:3px;margin-bottom:12px;min-height:40px}.reward-summary strong{font-size:.9rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.reward-summary small{color:#738177}.field-label{display:block;font-size:.78rem;font-weight:800;color:#4c5e53;margin:9px 0 5px}.field{width:100%;min-width:0;background:#fff;color:#24372b;border:1px solid #dbe7df;border-radius:10px;padding:9px 10px;outline:none}.field:focus{border-color:#43a56f;box-shadow:0 0 0 3px #20864f17}.choose-btn{display:flex;align-items:center;justify-content:center;gap:7px;width:100%;min-height:39px;border:1px solid #cde8d6;border-radius:10px;background:#f0faf3;color:#187847;font-size:.82rem;font-weight:800;padding:7px;overflow:hidden;text-overflow:ellipsis}.panel-foot{display:flex;gap:9px;align-items:flex-start;margin-top:18px;padding:12px 14px;background:#eaf7ee;border-radius:12px;color:#326547;font-size:.84rem}.panel-foot i{font-size:1.1rem}.notice{padding:12px 14px;border-radius:12px;margin:12px 0}.notice.error{background:#fff0ef;color:#9c3029}.notice.success{background:#eaf8ee;color:#176b3b}.loading,.empty-picker{padding:28px;text-align:center;color:#6b7d70}.picker-backdrop{position:fixed;inset:0;z-index:1100;background:#0d1c13a8;display:flex;align-items:center;justify-content:center;padding:18px}.picker-modal{width:min(980px,100%);max-height:90vh;overflow:auto;background:#fff;border-radius:22px;padding:20px;box-shadow:0 25px 90px #0004}.picker-modal>header{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.picker-modal h3{margin:0;font-size:1.2rem;font-weight:900}.picker-modal p{color:#718078;margin:5px 0 14px}.close-btn{border:0;background:#f0f4f1;border-radius:10px;font-size:1.5rem;width:36px;height:36px;color:#34513d}.search-field{margin-bottom:15px}.cosmetic-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}.cosmetic-option{position:relative;display:flex;flex-direction:column;align-items:center;gap:6px;padding:10px;border:1px solid #e1ebe4;border-radius:14px;background:#fff;color:#24372b;min-width:0}.cosmetic-option.selected{border-color:#20864f;background:#effaf3;box-shadow:0 0 0 2px #20864f16}.cosmetic-art{width:100%;height:96px;display:flex;align-items:center;justify-content:center;background:#f6faf7;border-radius:10px;overflow:hidden}.cosmetic-art img{width:100%;height:100%;object-fit:contain;padding:5px}.cosmetic-art i{font-size:2rem;color:#9bc8a9}.cosmetic-option strong{font-size:.82rem;max-width:100%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.cosmetic-option small{font-size:.73rem;color:#718078}.selected-tag{font-size:.7rem;color:#187847;font-weight:800}.cosmetic-option{cursor:pointer}.empty-picker{grid-column:1/-1}@media(max-width:1000px){.reward-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.cosmetic-grid{grid-template-columns:repeat(4,minmax(0,1fr))}}@media(max-width:680px){.reward-panel{padding:13px}.reward-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.reward-card{padding:10px}.reward-art{height:100px}.panel-title{align-items:flex-start}.panel-title p{font-size:.84rem}.cosmetic-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.daily-rewards-toolbar{align-items:flex-start}.daily-rewards-status{width:100%}.save-btn{flex:1}}
</style>
