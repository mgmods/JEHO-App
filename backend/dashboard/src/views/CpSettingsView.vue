<template>
  <div class="cp-admin-page">
    <PageHeader title="إعدادات علاقات CP" subtitle="إدارة الأنواع والهدايا والأسعار والمستويات والمكافآت وسجل العمليات. لا توجد أسعار افتراضية." >
      <template #actions>
        <button class="btn btn-outline-secondary btn-sm" :disabled="loading || saving" @click="load">تحديث البيانات</button>
      </template>
    </PageHeader>

    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />
    <div class="alert alert-warning small">
      تفعيل النوع يتطلب تحديد هدية موجودة وسعر موجب. لا يتم اعتماد أي سعر أو مستوى تجريبي تلقائياً.
      نظام الصوت وإعداداته غير قابل للتعديل من هذه الصفحة.
    </div>
    <LoadingSpinner v-if="loading" />

    <template v-else>
      <section v-for="item in types" :key="item.type" class="settings-card mb-3">
        <div class="d-flex align-items-start justify-content-between gap-3 flex-wrap mb-3">
          <div>
            <h3 class="settings-card-title mb-1">{{ label(item.type) }}</h3>
            <p class="form-text mb-0">معرّف النوع: <code>{{ item.type }}</code></p>
          </div>
          <label class="form-check form-switch mb-0">
            <input v-model="item.enabled" class="form-check-input" type="checkbox" role="switch">
            <span class="form-check-label">{{ item.enabled ? 'مفعّل' : 'متوقف' }}</span>
          </label>
        </div>
        <div class="row g-3">
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">السعر بالعملات</label>
            <input v-model="item.priceInput" class="form-control" type="number" min="1" max="1000000000" step="1" placeholder="غير محدد">
            <div class="form-text">اتركه فارغاً لإبقاء السعر غير محدد.</div>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">هدية بدء العلاقة</label>
            <select v-model="item.giftId" class="form-select">
              <option value="">اختر هدية موجودة</option>
              <option v-for="gift in gifts" :key="gift.id" :value="gift.id">{{ gift.name }} · {{ gift.coinPrice ?? gift.price ?? '—' }}</option>
            </select>
            <div v-if="selectedGift(item)" class="d-flex align-items-center gap-2 mt-2 small">
              <img v-if="selectedGift(item).iconUrl" :src="selectedGift(item).iconUrl" :alt="selectedGift(item).name" width="40" height="40" style="object-fit:contain" />
              <div>الهدية: {{ selectedGift(item).name }}<div class="form-text">تُحفظ لقطة من الصورة والأنيميشن عند إرسال الطلب.</div></div>
            </div>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">نقاط هدية البدء</label>
            <input v-model.number="item.giftPoints" class="form-control" type="number" min="0" max="1000000000" step="1">
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">نقاط كل هدية متبادلة بين الشريكين</label>
            <input v-model.number="item.giftInteractionPoints" class="form-control" type="number" min="0" max="1000000000" step="1">
            <div class="form-text">تُمنح بعد تسجيل الهدية بنجاح، وتُخصم مرة واحدة فقط لكل سجل هدية.</div>
          </div>
          <div class="col-sm-6 col-xl-3">
            <label class="form-label">الحد الأقصى للمستوى</label>
            <input v-model.number="item.maxLevel" class="form-control" type="number" min="1" max="1000" step="1" placeholder="حسب الجدول">
          </div>
          <div class="col-12">
            <label class="form-label">جدول المستويات والمكافآت (JSON)</label>
            <textarea v-model="item.levelsText" class="form-control font-monospace" rows="5" dir="ltr" spellcheck="false" placeholder='[{"level":1,"pointsRequired":0,"reward":"إطار البداية"}]'></textarea>
            <div class="form-text">كل عنصر: level و pointsRequired، واختيارياً reward. يجب ترتيب النقاط تصاعدياً. لا يوجد عدد مستويات مفروض مسبقاً.</div>
          </div>
          <div class="col-12">
            <label class="form-label">قواعد النوع (JSON)</label>
            <textarea v-model="item.rulesText" class="form-control font-monospace" rows="3" dir="ltr" spellcheck="false" placeholder="{}"></textarea>
            <div class="form-text">قواعد إضافية خاصة بنوع العلاقة.</div>
          </div>
          <div class="col-12">
            <div class="border rounded-3 p-3">
              <div class="d-flex align-items-center justify-content-between gap-3 flex-wrap mb-3">
                <div>
                  <h4 class="h6 mb-1">تأثير العلاقة بين مقاعد الروم</h4>
                  <p class="form-text mb-0">يظهر فقط عندما يكون الشريكان معاً على المقاعد. إذا كان أحدهما خارج المقاعد، لا يظهر التأثير.</p>
                </div>
                <label class="form-check form-switch mb-0">
                  <input v-model="item.seatEffectEnabled" class="form-check-input" type="checkbox" role="switch">
                  <span class="form-check-label">{{ item.seatEffectEnabled ? 'مفعّل' : 'متوقف' }}</span>
                </label>
              </div>
              <div class="row g-3 align-items-end">
                <div class="col-md-6">
                  <label class="form-label">صورة التأثير المتحرك (GIF أو WebP) أو صورة ثابتة</label>
                  <input class="form-control" type="file" accept=".gif,.webp,.png,.jpg,.jpeg,image/gif,image/webp,image/png,image/jpeg" :disabled="item.uploadingSeatEffect" @change="uploadSeatEffect(item, $event)">
                  <div class="form-text">ارفع الصورة المرفوعة تتكرر تلقائياً كشريط متحرك بين الشريكين، والحجم قابل للتصغير لتجنب إزعاج الروم. لن يتم تغيير الصوت أو إعدادات الروم.</div>
                  <div v-if="item.uploadingSeatEffect" class="small text-primary mt-2">جارٍ رفع الصورة…</div>
                </div>
                <div class="col-md-6">
                  <label class="form-label">رابط الصورة</label>
                  <input v-model.trim="item.seatEffectUrl" class="form-control" type="url" dir="ltr" placeholder="https://…">
                </div>
                <div class="col-md-6">
                  <label class="form-label">حجم الرمز الواحد (dp)</label>
                  <input v-model.number="item.seatEffectSize" class="form-control" type="number" min="10" max="36" step="1">
                </div>
                <div class="col-md-6">
                  <label class="form-label">مدة الحركة (مللي ثانية)</label>
                  <input v-model.number="item.seatEffectDuration" class="form-control" type="number" min="500" max="10000" step="100">
                </div>
                <div v-if="item.seatEffectUrl" class="col-12 d-flex align-items-center gap-3">
                  <img :src="item.seatEffectUrl" :alt="'تأثير ' + label(item.type)" style="width:72px;height:72px;object-fit:contain;background:#111;border-radius:12px;padding:6px">
                  <span class="small text-secondary">معاينة التأثير الحالي · {{ label(item.type) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="d-flex justify-content-end mt-3">
          <button class="btn btn-aurora" :disabled="saving" @click="saveType(item)">
            <span v-if="savingType === item.type" class="spinner-border spinner-border-sm me-1" />
            حفظ {{ label(item.type) }}
          </button>
        </div>
      </section>

      <section class="settings-card mb-3">
        <h3 class="settings-card-title">سياسة الطلبات</h3>
        <p class="form-text">الرفض والانتهاء والإلغاء من صاحب الطلب يعيد المبلغ الأصلي كاملاً. لا يمكن تعطيل الاسترداد الكامل من لوحة التحكم.</p>
        <div class="row g-3 align-items-end">
          <div class="col-sm-6 col-lg-4">
            <label class="form-label">مهلة الرد بالدقائق</label>
            <input v-model.number="policy.requestExpiryMinutes" class="form-control" type="number" min="5" max="1440" step="1">
          </div>
          <div class="col-sm-6 col-lg-4">
            <label class="form-label">قواعد عامة (JSON)</label>
            <input v-model="policy.rulesText" class="form-control font-monospace" dir="ltr" placeholder="{}">
          </div>
          <div class="col-lg-4">
            <button class="btn btn-aurora" :disabled="saving" @click="savePolicy">حفظ سياسة الطلبات</button>
          </div>
        </div>
      </section>

      <section class="settings-card mb-3">
        <h3 class="settings-card-title">تعديل نقاط علاقة (إجراء إداري مسجّل)</h3>
        <p class="form-text">يُستخدم للتصحيح الإداري فقط. لا يمنح المستخدم صلاحية إرسال نقاط لنفسه، وكل تعديل يسجل في سجل التدقيق.</p>
        <div class="row g-3">
          <div class="col-md-6"><label class="form-label">معرّف العلاقة</label><input v-model.trim="pointForm.relationshipId" class="form-control font-monospace" dir="ltr" placeholder="UUID"></div>
          <div class="col-md-3"><label class="form-label">النقاط</label><input v-model.number="pointForm.points" class="form-control" type="number" min="0" step="1"></div>
          <div class="col-md-3"><label class="form-label">نوع الحدث</label><input v-model.trim="pointForm.eventType" class="form-control" placeholder="admin_adjustment"></div>
          <div class="col-md-6"><label class="form-label">نوع المرجع الفريد</label><input v-model.trim="pointForm.referenceType" class="form-control" dir="ltr" placeholder="admin_adjustment"></div>
          <div class="col-md-6"><label class="form-label">معرّف المرجع الفريد</label><input v-model.trim="pointForm.referenceId" class="form-control" dir="ltr" placeholder="Unique ID"></div>
        </div>
        <div class="mt-3 d-flex justify-content-end"><button class="btn btn-aurora" :disabled="savingPoints" @click="adjustPoints"><span v-if="savingPoints" class="spinner-border spinner-border-sm me-1" />تسجيل تعديل النقاط</button></div>
      </section>

      <section class="settings-card mb-3">
        <div class="d-flex justify-content-between align-items-center gap-2 flex-wrap mb-3">
          <div>
            <h3 class="settings-card-title mb-1">طلبات CP وسجل الاسترداد</h3>
            <p class="form-text mb-0">يعرض آخر الطلبات وحالاتها؛ لا تُعرض بيانات المحافظ الخاصة.</p>
          </div>
          <button class="btn btn-outline-secondary btn-sm" :disabled="loadingLogs" @click="loadLogs">تحديث السجلات</button>
        </div>
        <LoadingSpinner v-if="loadingLogs" />
        <div v-else class="table-responsive">
          <table class="table table-sm align-middle">
            <thead><tr><th>النوع</th><th>المرسل</th><th>المستلم</th><th>السعر</th><th>الحالة</th><th>الهدية</th><th>الاسترداد</th><th>تاريخ الطلب</th></tr></thead>
            <tbody>
              <tr v-for="row in requests" :key="row.id">
                <td>{{ label(row.type) }}</td><td>{{ row.fromUsername || row.fromUserId }}</td><td>{{ row.toUsername || row.toUserId }}</td>
                <td>{{ format(row.priceCoins) }}</td><td><span class="badge text-bg-light">{{ row.status }}</span></td>
                <td>{{ row.giftName || '—' }}</td><td>{{ row.refundedAt ? 'مسترد' : '—' }}</td><td>{{ date(row.createdAt) }}</td>
              </tr>
              <tr v-if="!requests.length"><td colspan="8" class="text-center text-secondary py-4">لا توجد طلبات مسجلة.</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <section class="settings-card mb-3">
        <div class="d-flex justify-content-between align-items-center gap-2 flex-wrap mb-3">
          <div><h3 class="settings-card-title mb-1">سجل تدقيق CP</h3><p class="form-text mb-0">تغييرات الإعدادات وتعديلات النقاط الإدارية.</p></div>
          <button class="btn btn-outline-secondary btn-sm" :disabled="loadingLogs" @click="loadLogs">تحديث</button>
        </div>
        <div class="table-responsive">
          <table class="table table-sm align-middle">
            <thead><tr><th>الوقت</th><th>الإجراء</th><th>العنصر</th><th>التفاصيل</th><th>المدير</th></tr></thead>
            <tbody>
              <tr v-for="row in audit" :key="row.id"><td>{{ date(row.createdAt) }}</td><td>{{ row.action }}</td><td>{{ row.entityType }} / {{ row.entityId || '—' }}</td><td><code class="small">{{ JSON.stringify(row.details || {}) }}</code></td><td>{{ row.adminUserId || '—' }}</td></tr>
              <tr v-if="!audit.length"><td colspan="5" class="text-center text-secondary py-4">لا توجد أحداث تدقيق حتى الآن.</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { cpAdminApi, giftsApi, uploadsApi } from '@/api'
import { extractList } from '@/composables/useUtils'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const types = ref([])
const gifts = ref([])
const requests = ref([])
const audit = ref([])
const loading = ref(false)
const loadingLogs = ref(false)
const saving = ref(false)
const savingType = ref('')
const savingPoints = ref(false)
const pointForm = ref({ relationshipId: '', points: 0, eventType: 'admin_adjustment', referenceType: 'admin_adjustment', referenceId: '' })
const error = ref('')
const success = ref('')
const policy = ref({ requestExpiryMinutes: 30, rulesText: '{}' })

function label(type) {
  return ({ friendship: 'الصداقة', brotherhood: 'الأخوّة', love: 'الحب', engagement: 'الخطوبة', marriage: 'الزواج' })[type] || type
}
function format(value) { return Number(value || 0).toLocaleString('ar') }
function date(value) { if (!value) return '—'; const d = new Date(value); return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString('ar') }
function selectedGift(item) { return gifts.value.find(g => String(g.id) === String(item.giftId)) || null }
function payloadOf(response) { return response?.data?.data ?? response?.data ?? response ?? {} }

async function load() {
  loading.value = true; error.value = ''; success.value = ''
  try {
    const [configResponse, giftsResponse] = await Promise.all([cpAdminApi.config(), giftsApi.list({ limit: 500 })])
    const config = payloadOf(configResponse)
    gifts.value = extractList(giftsResponse?.data?.data ?? giftsResponse?.data ?? giftsResponse).filter(gift => gift && gift.isActive !== false)
    types.value = (config.types || []).map(item => ({
      ...item,
      priceInput: item.priceCoins == null ? '' : String(item.priceCoins),
      giftId: item.giftId || item.gift?.id || '',
      giftPoints: Number(item.giftPoints || 0),
      giftInteractionPoints: Number(item.giftInteractionPoints || 0),
      maxLevel: item.maxLevel == null ? null : Number(item.maxLevel),
      levelsText: JSON.stringify(item.levels || [], null, 2),
      rulesText: JSON.stringify(item.rules || {}, null, 2),
      seatEffectEnabled: item.rules?.seatEffect?.enabled !== false,
      seatEffectUrl: item.rules?.seatEffect?.url || '',
      seatEffectSize: Math.max(10, Math.min(36, Number(item.rules?.seatEffect?.sizeDp || 18))),
      seatEffectDuration: Math.max(500, Math.min(10000, Number(item.rules?.seatEffect?.durationMs || 1800))),
      uploadingSeatEffect: false,
    }))
    policy.value = {
      requestExpiryMinutes: Number(config.policy?.requestExpiryMinutes || 30),
      rulesText: JSON.stringify(config.policy?.rules || {}, null, 2),
    }
    await loadLogs()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل إعدادات CP' }
  finally { loading.value = false }
}

async function uploadSeatEffect(item, event) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const allowed = /\.(gif|webp|png|jpe?g)$/i.test(file.name)
  if (!allowed) { error.value = 'اختر صورة GIF أو WebP أو PNG أو JPG.'; event.target.value = ''; return }
  if (file.size > 40 * 1024 * 1024) { error.value = 'حجم الصورة يجب ألا يتجاوز 40 ميغابايت.'; event.target.value = ''; return }
  item.uploadingSeatEffect = true
  error.value = ''
  try {
    const response = await uploadsApi.upload(file)
    if (response?.error) throw new Error(response.error.message || 'تعذر رفع الصورة')
    const data = payloadOf(response)
    if (!data?.url && !data?.path) throw new Error('تم استلام رد رفع غير مكتمل من الخادم.')
    const uploadedUrl = data.url || data.path
    item.seatEffectUrl = /^https?:\/\//i.test(uploadedUrl)
      ? uploadedUrl
      : new URL(uploadedUrl, window.location.origin).toString()
    success.value = 'تم رفع الصورة. اضغط حفظ ' + label(item.type) + ' لاعتمادها.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع صورة التأثير'
  } finally {
    item.uploadingSeatEffect = false
    if (event?.target) event.target.value = ''
  }
}

async function saveType(item) {
  error.value = ''; success.value = ''; saving.value = true; savingType.value = item.type
  try {
    let levels, rules
    try { levels = JSON.parse(item.levelsText || '[]'); rules = JSON.parse(item.rulesText || '{}') }
    catch { throw new Error('تحقق من صيغة JSON في المستويات أو القواعد.') }
    if (!Array.isArray(levels) || !rules || typeof rules !== 'object' || Array.isArray(rules)) throw new Error('المستويات يجب أن تكون قائمة والقواعد كائناً JSON.')
    rules.seatEffect = {
      enabled: item.seatEffectEnabled === true,
      url: String(item.seatEffectUrl || '').trim(),
      sizeDp: Math.max(10, Math.min(36, Number(item.seatEffectSize || 18))),
      durationMs: Math.max(500, Math.min(10000, Number(item.seatEffectDuration || 1800))),
    }
    const payload = {
      enabled: !!item.enabled,
      giftId: item.giftId || '',
      giftPoints: Number(item.giftPoints || 0),
      maxLevel: item.maxLevel == null || item.maxLevel === '' ? undefined : Number(item.maxLevel),
      levels, rules,
    }
    if (String(item.priceInput).trim() !== '') {
      const price = Number(item.priceInput)
      if (!Number.isSafeInteger(price) || price <= 0) throw new Error('السعر يجب أن يكون عدداً صحيحاً موجباً.')
      payload.priceCoins = price
    } else if (item.priceCoins != null) {
      payload.priceCoins = 0
    }
    await cpAdminApi.updateType(item.type, payload)
    success.value = 'تم حفظ إعدادات ' + label(item.type) + '.'
    await load()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ إعدادات النوع' }
  finally { saving.value = false; savingType.value = '' }
}

async function savePolicy() {
  error.value = ''; success.value = ''; saving.value = true
  try {
    let rules
    try { rules = JSON.parse(policy.value.rulesText || '{}') } catch { throw new Error('قواعد السياسة ليست JSON صالحاً.') }
    await cpAdminApi.updatePolicy({ requestExpiryMinutes: Number(policy.value.requestExpiryMinutes), rules })
    success.value = 'تم حفظ سياسة الطلبات.'
    await load()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ السياسة' }
  finally { saving.value = false }
}


async function adjustPoints() {
  error.value = ''; success.value = ''
  if (!pointForm.value.relationshipId || !pointForm.value.referenceId || !pointForm.value.referenceType || !pointForm.value.eventType ||
      !Number.isSafeInteger(Number(pointForm.value.points)) || Number(pointForm.value.points) < 0) {
    error.value = 'أكمل بيانات تعديل النقاط بشكل صحيح.'
    return
  }
  savingPoints.value = true
  try {
    await cpAdminApi.adjustPoints({ ...pointForm.value, points: Number(pointForm.value.points), metadata: { source: 'dashboard_manual_adjustment' } })
    success.value = 'تم تسجيل الحدث. إذا كان المرجع مستخدماً سابقاً فلن تُضاف النقاط مرتين.'
    pointForm.value.referenceId = ''
    await loadLogs()
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر تسجيل تعديل النقاط' }
  finally { savingPoints.value = false }
}

async function loadLogs() {
  loadingLogs.value = true
  try {
    const [requestResponse, auditResponse] = await Promise.all([cpAdminApi.requests(), cpAdminApi.audit()])
    requests.value = extractList(payloadOf(requestResponse))
    audit.value = extractList(payloadOf(auditResponse))
  } catch (e) { error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل السجلات' }
  finally { loadingLogs.value = false }
}

onMounted(load)
</script>
