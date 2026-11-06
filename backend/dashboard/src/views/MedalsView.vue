<template>
  <div class="medals-page" dir="rtl">
    <PageHeader title="إدارة المداليات" subtitle="إهداء المداليات للحسابات بشكل فردي أو لجميع مستخدمي JEHO." />
    <div v-if="error" class="alert alert-danger">{{ error }}</div>
    <div v-if="success" class="alert alert-success">{{ success }}</div>

    
    <section class="panel room-support-panel">
      <div class="section-heading">
        <div class="heading-icon room-support-icon"><i class="bi bi-trophy-fill"></i></div>
        <div><h2>مداليات دعم الغرف</h2><p>قسم مستقل لتعريف مداليات الدعم وشروطها، دون خلطها بالمداليات العامة أو مهامها.</p></div>
      </div>
      <div class="room-support-settings">
        <label class="check-row"><input v-model="roomSupport.enabled" type="checkbox" /> تفعيل نظام مداليات دعم الغرف</label>
        <label>طريقة العرض
          <select v-model="roomSupport.displayMode" class="form-select"><option value="highest">أعلى مدالية مستحقة فقط (highest)</option></select>
        </label>
        <label>مكان الظهور
          <select v-model="roomSupport.displayLocation" class="form-select"><option value="both">داخل الغرفة وفقاعة الرسائل (both)</option></select>
        </label>
        <label>مدة المدالية
          <select v-model="roomSupport.durationMode" class="form-select"><option value="admin">حسب المدة التي يحددها المدير (admin)</option></select>
        </label>
        <label>احتساب الدعم
          <select v-model="roomSupport.scope" class="form-select"><option value="per_room">كل غرفة بشكل مستقل</option></select>
        </label>
      </div>
      <div class="room-medal-editor">
        <h3>{{ roomMedalEditIndex < 0 ? 'إضافة مدالية دعم' : 'تعديل مدالية دعم' }}</h3>
        <div class="form-grid">
          <label>اسم المدالية<input v-model.trim="roomMedalForm.name" class="form-control" maxlength="80" placeholder="مثلاً: داعم الغرفة الذهبي" /></label>
          <label>نوع شرط الدعم
            <select v-model="roomMedalForm.criteriaType" class="form-select">
              <option value="coins">إجمالي العملات المدفوعة</option><option value="diamonds">إجمالي الألماس المستحق</option><option value="gifts">عدد الهدايا</option>
            </select>
          </label>
          <label>الحد المطلوب<input v-model.number="roomMedalForm.threshold" class="form-control" type="number" min="1" max="1000000000" /></label>
          <label>مدة الاستحقاق بالأيام<input v-model.number="roomMedalForm.durationDays" class="form-control" type="number" min="0" max="3650" /><small>0 = دائمة</small></label>
          <label class="wide">رابط صورة المدالية<input v-model.trim="roomMedalForm.imageUrl" class="form-control" type="url" placeholder="ارفع الصورة أو أدخل رابطها" /></label>
          <label class="upload-box wide"><span><i class="bi bi-cloud-arrow-up"></i> {{ roomMedalUploading ? 'جارٍ رفع الصورة…' : 'رفع صورة مدالية الدعم' }}</span><input type="file" accept="image/png,image/jpeg,image/webp,image/gif" :disabled="roomMedalUploading" @change="uploadRoomMedalImage" /></label>
          <div v-if="roomMedalForm.imageUrl" class="medal-preview"><img :src="roomMedalForm.imageUrl" alt="معاينة مدالية الدعم" /></div>
          <label class="check-row"><input v-model="roomMedalForm.isActive" type="checkbox" /> المدالية مفعّلة</label>
        </div>
        <div class="room-medal-actions">
          <button class="btn btn-success" type="button" :disabled="roomSupportSaving || roomMedalUploading" @click="saveRoomMedal">{{ roomMedalEditIndex < 0 ? 'إضافة إلى القائمة' : 'حفظ التعديل' }}</button>
          <button v-if="roomMedalEditIndex >= 0" class="btn btn-outline-light" type="button" @click="resetRoomMedalForm">إلغاء التعديل</button>
        </div>
      </div>
      <div v-if="roomSupport.medals.length" class="room-medal-list">
        <article v-for="(medal, index) in roomSupport.medals" :key="index" class="room-medal-row">
          <img v-if="medal.imageUrl" :src="medal.imageUrl" :alt="medal.name" />
          <div class="room-medal-info"><strong>{{ medal.name }}</strong><small>{{ criteriaLabel(medal.criteriaType) }}: {{ Number(medal.threshold).toLocaleString() }} · {{ medal.durationDays ? medal.durationDays + ' يوم' : 'دائمة' }}</small></div>
          <span class="status" :class="medal.isActive ? 'on' : 'off'">{{ medal.isActive ? 'مفعّلة' : 'متوقفة' }}</span>
          <button class="btn btn-sm btn-outline-light" type="button" @click="editRoomMedal(index)">تعديل</button>
          <button class="btn btn-sm btn-outline-danger" type="button" @click="removeRoomMedal(index)">حذف</button>
        </article>
      </div>
      <p v-else class="empty">لم تتم إضافة مداليات دعم بعد.</p>
      <div class="room-support-footer">
        <span>الإعدادات المعتمدة: highest · both · admin</span>
        <button class="btn btn-success" type="button" :disabled="roomSupportSaving || roomMedalUploading" @click="saveRoomSupportConfig">{{ roomSupportSaving ? 'جارٍ الحفظ…' : 'حفظ إعدادات مداليات دعم الغرف' }}</button>
      </div>
      <p class="secret-note">هذا القسم مستقل عن المداليات العامة. حفظ إعداداته في لوحة الإدارة لا يعني وحده تفعيل احتساب الدعم أو إظهار الميدالية داخل التطبيق؛ يلزم ربط محرك المنح والعرض في الخادم والتطبيق.</p>
    </section>

    <section class="panel create-panel">
      <div class="section-heading">
        <div class="heading-icon"><i class="bi bi-award-fill"></i></div>
        <div><h2>إضافة مدالية</h2><p>ارفع صورة المدالية أو استخدم رابط صورة مباشر.</p></div>
      </div>
      <form class="form-grid" @submit.prevent="createMedal">
        <label>اسم المدالية<input v-model.trim="form.name" class="form-control" maxlength="80" required placeholder="مثلاً: مدالية التميز" /></label>
        <label>الوصف<input v-model.trim="form.description" class="form-control" maxlength="500" placeholder="وصف مختصر يظهر للإدارة" /></label>
        <label class="wide">رابط الصورة<input v-model.trim="form.imageUrl" class="form-control" type="url" required placeholder="https://..." /></label>
        <label class="upload-box wide">
          <span><i class="bi bi-cloud-arrow-up"></i> {{ uploading ? 'جارٍ رفع الصورة…' : 'رفع صورة المدالية' }}</span>
          <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" :disabled="uploading" @change="uploadImage" />
        </label>
        <div v-if="form.imageUrl" class="medal-preview"><img :src="form.imageUrl" alt="معاينة المدالية" @error="previewBroken = true" @load="previewBroken = false" /><span v-if="previewBroken">تعذر تحميل الصورة</span></div>
        <div class="hidden-task-config wide">
          <h3>مهمة سرّية تمنح المدالية تلقائياً</h3>
          <p>لا تظهر المهمة أو التقدم للمستخدم. السيرفر يمنح المدالية فور اكتمال الشرط.</p>
          <label class="check-row"><input v-model="form.hiddenTaskEnabled" type="checkbox" /> تفعيل المهمة السرّية</label>
          <label>نوع النشاط
            <select v-model="form.hiddenTaskEvent" class="form-select">
              <option value="gift">إرسال هدايا</option><option value="rooms">دخول غرف صوتية</option><option value="mic">الجلوس على الميكروفون</option><option value="follow">متابعة مستخدمين</option><option value="recharge">الشحن</option><option value="game">لعب الألعاب</option><option value="win">الفوز بالألعاب</option><option value="chat">المحادثات</option><option value="checkin">الحضور اليومي</option><option value="ad">مشاهدة الإعلانات</option>
            </select>
          </label>
          <label>العدد المطلوب<input v-model.number="form.hiddenTaskTarget" class="form-control" type="number" min="1" max="1000000000" /></label>
          <label>مدة المدالية بالأيام<input v-model.number="form.hiddenTaskDays" class="form-control" type="number" min="0" max="3650" /><small>0 = دائمة</small></label>
        </div>
        <div class="manual-task-card wide">
          <h3>مهمة يدوية لهذه المدالية</h3>
          <p>تظهر في مركز المهام أو تبقى مخفية. اعتماد الإنجاز ومنح المدالية يتم من الإدارة عبر الإهداء للمستخدم.</p>
          <label class="check-row"><input v-model="form.manualTaskEnabled" type="checkbox" /> تفعيل المهمة اليدوية</label>
          <label>ظهور المهمة
            <select v-model="form.manualTaskVisibility" class="form-select">
              <option value="visible">ظاهرة للمستخدم في مركز المهام</option>
              <option value="hidden">مخفية — للإدارة فقط</option>
            </select>
          </label>
          <label>عنوان المهمة<input v-model.trim="form.manualTaskTitle" class="form-control" maxlength="120" placeholder="مثلاً: شارك في فعالية JEHO" /></label>
          <label>وصف المهمة<input v-model.trim="form.manualTaskDescription" class="form-control" maxlength="500" placeholder="اشرح المطلوب من المستخدم" /></label>
          <label>مدة المدالية بالأيام<input v-model.number="form.manualTaskDays" class="form-control" type="number" min="0" max="3650" /><small>0 = دائمة</small></label>
        </div>
        <label>الترتيب<input v-model.number="form.sortOrder" class="form-control" type="number" min="0" /></label>
        <label class="check-row"><input v-model="form.isAnimated" type="checkbox" /> المدالية متحركة</label>
        <button class="btn btn-success wide submit-btn" type="submit" :disabled="saving || uploading">{{ saving ? 'جارٍ الحفظ…' : 'حفظ المدالية' }}</button>
      </form>
    </section>

    <section class="section-heading list-heading">
      <div><h2>المداليات الموجودة</h2><p>اختر أي مدالية سابقة وأهدها لمستخدم محدد أو لجميع الحسابات.</p></div>
      <button class="btn btn-outline-light" type="button" :disabled="loading" @click="load">{{ loading ? 'جارٍ التحديث…' : 'تحديث القائمة' }}</button>
    </section>

    <div v-if="loading && !medals.length" class="panel loading">جارٍ تحميل المداليات…</div>
    <div v-else-if="!medals.length" class="panel empty">لا توجد مداليات بعد. أضف أول مدالية من النموذج أعلاه.</div>
    <div v-else class="medal-grid">
      <article v-for="medal in medals" :key="medal.id" class="medal-card" :class="{ inactive: !medal.isActive }">
        <div class="card-top">
          <div class="medal-art"><img :src="medal.imageUrl" :alt="medal.name" loading="lazy" /></div>
          <span class="status" :class="medal.isActive ? 'on' : 'off'">{{ medal.isActive ? 'متاحة' : 'مخفية' }}</span>
        </div>
        <h3>{{ medal.name }}</h3>
        <p class="description">{{ medal.description || 'مدالية مميزة لحساب المستخدم' }}</p>
        <div class="hidden-task-card">
          <h4>المهمة السرّية لهذه المدالية</h4>
          <label class="check-row"><input v-model="hiddenTask[medal.id].enabled" type="checkbox" /> تفعيل المنح التلقائي</label>
          <label>النشاط
            <select v-model="hiddenTask[medal.id].event" class="form-select">
              <option value="gift">إرسال هدايا</option><option value="rooms">دخول غرف صوتية</option><option value="mic">الجلوس على الميكروفون</option><option value="follow">متابعة مستخدمين</option><option value="recharge">الشحن</option><option value="game">لعب الألعاب</option><option value="win">الفوز بالألعاب</option><option value="chat">المحادثات</option><option value="checkin">الحضور اليومي</option><option value="ad">مشاهدة الإعلانات</option>
            </select>
          </label>
          <label>العدد المطلوب<input v-model.number="hiddenTask[medal.id].target" class="form-control" type="number" min="1" /></label>
          <label>مدة المدالية بالأيام<input v-model.number="hiddenTask[medal.id].days" class="form-control" type="number" min="0" max="3650" /><small>0 = دائمة</small></label>
          <button class="btn btn-outline-success w-100" type="button" :disabled="busyId === medal.id" @click="saveHiddenTask(medal)">حفظ المهمة السرّية</button>
          <p class="secret-note">هذه المهمة مخفية تماماً عن المستخدم، والمنح تلقائي دون زر استلام.</p>
        </div>
        <div class="manual-task-card">
          <h4>المهمة اليدوية</h4>
          <label class="check-row"><input v-model="manualTask[medal.id].enabled" type="checkbox" /> تفعيل المهمة اليدوية</label>
          <label>الظهور
            <select v-model="manualTask[medal.id].visibility" class="form-select">
              <option value="visible">ظاهرة في مركز المهام</option>
              <option value="hidden">مخفية عن المستخدم</option>
            </select>
          </label>
          <label>عنوان المهمة<input v-model.trim="manualTask[medal.id].title" class="form-control" maxlength="120" /></label>
          <label>وصف المهمة<input v-model.trim="manualTask[medal.id].description" class="form-control" maxlength="500" /></label>
          <label>مدة المدالية بالأيام<input v-model.number="manualTask[medal.id].days" class="form-control" type="number" min="0" max="3650" /><small>0 = دائمة</small></label>
          <button class="btn btn-outline-success w-100" type="button" :disabled="busyId === medal.id" @click="saveManualTask(medal)">حفظ المهمة اليدوية</button>
          <p class="secret-note">المهمة الظاهرة تظهر بمركز المهام وتنتظر اعتماد الإدارة. المخفية لا تظهر للمستخدم.</p>
        </div>
        <div class="gift-box">
          <label>رقم المستخدم أو ID العام<input v-model.trim="recipient[medal.id]" class="form-control" placeholder="أدخل ID المستخدم" /></label>
          <label>مدة الإهداء
            <select v-model.number="duration[medal.id]" class="form-select">
              <option :value="0">دائمة</option><option :value="1">يوم واحد</option><option :value="7">7 أيام</option><option :value="30">30 يوماً</option><option :value="90">90 يوماً</option><option :value="365">سنة</option>
            </select>
          </label>
          <button class="btn btn-success w-100" type="button" :disabled="busyId === medal.id" @click="giftOne(medal)"><i class="bi bi-gift"></i> {{ busyId === medal.id ? 'جارٍ الإهداء…' : 'إهداء لمستخدم' }}</button>
          <button class="btn btn-outline-success w-100" type="button" :disabled="busyId === medal.id" @click="giftEveryone(medal)"><i class="bi bi-people-fill"></i> إهداء لجميع الحسابات</button>
        </div>
        <div class="card-actions">
          <button class="btn btn-sm btn-outline-light" type="button" :disabled="busyId === medal.id" @click="toggleActive(medal)">{{ medal.isActive ? 'إخفاء من القائمة' : 'إظهار في القائمة' }}</button>
          <button class="btn btn-sm btn-outline-danger" type="button" :disabled="busyId === medal.id" @click="deleteMedal(medal)">حذف</button>
        </div>
      </article>
    </div>
    <p class="footnote"><i class="bi bi-shield-check"></i> الإهداء يُحفظ في قاعدة البيانات، ويصل للمستخدم إشعار داخل التطبيق. الإهداء الدائم لا ينتهي تلقائياً.</p>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { medalsApi, uploadsApi, settingsApi } from '@/api'
import PageHeader from '@/components/PageHeader.vue'
import { resolveAsset } from '@/utils/assets'

const medals = ref([])
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const error = ref('')
const success = ref('')
const busyId = ref('')
const previewBroken = ref(false)
const roomSupportSaving = ref(false)
const roomMedalUploading = ref(false)
const roomMedalEditIndex = ref(-1)
const roomSupport = reactive({ enabled: true, displayMode: 'highest', displayLocation: 'both', durationMode: 'admin', scope: 'per_room', medals: [] })
const roomMedalForm = reactive({ name: '', criteriaType: 'coins', threshold: 1000, durationDays: 0, imageUrl: '', isActive: true })
function resetRoomMedalForm() {
  Object.assign(roomMedalForm, { name: '', criteriaType: 'coins', threshold: 1000, durationDays: 0, imageUrl: '', isActive: true })
  roomMedalEditIndex.value = -1
}
function criteriaLabel(type) { return ({ coins: 'عملات', diamonds: 'ألماس', gifts: 'هدايا' })[type] || 'عملات' }
async function loadRoomSupportConfig() {
  try {
    const { data, error: err } = await settingsApi.get()
    if (err) throw err
    const rows = unwrap(data)
    const row = Array.isArray(rows) ? rows.find(item => item?.key === 'room_support_medals_config') : null
    if (!row?.value) return
    const saved = JSON.parse(row.value)
    roomSupport.enabled = saved.enabled !== false
    roomSupport.displayMode = 'highest'
    roomSupport.displayLocation = 'both'
    roomSupport.durationMode = 'admin'
    roomSupport.scope = 'per_room'
    roomSupport.medals = Array.isArray(saved.medals) ? saved.medals : []
  } catch (e) { error.value = e?.message || 'تعذر تحميل إعدادات مداليات دعم الغرف' }
}
async function saveRoomSupportConfig() {
  roomSupportSaving.value = true; error.value = ''; success.value = ''
  try {
    const payload = {
      enabled: !!roomSupport.enabled, displayMode: 'highest', displayLocation: 'both',
      durationMode: 'admin', scope: 'per_room',
      medals: roomSupport.medals.map(m => ({
        name: String(m.name || '').trim(), criteriaType: ['coins','diamonds','gifts'].includes(m.criteriaType) ? m.criteriaType : 'coins',
        threshold: Math.max(1, Math.floor(Number(m.threshold) || 1)),
        durationDays: Math.max(0, Math.min(3650, Math.floor(Number(m.durationDays) || 0))),
        imageUrl: String(m.imageUrl || '').trim(), isActive: m.isActive !== false,
      })),
    }
    const { error: err } = await settingsApi.update({ room_support_medals_config: JSON.stringify(payload) })
    if (err) throw new Error(err.message || 'تعذر حفظ إعدادات مداليات دعم الغرف')
    success.value = 'تم حفظ إعدادات وقائمة مداليات دعم الغرف في الداشبورد.'
  } catch (e) { error.value = e?.message || 'تعذر حفظ إعدادات مداليات دعم الغرف' }
  finally { roomSupportSaving.value = false }
}
function saveRoomMedal() {
  const name = String(roomMedalForm.name || '').trim()
  const imageUrl = String(roomMedalForm.imageUrl || '').trim()
  if (!name || !imageUrl) { error.value = 'أدخل اسم المدالية وارفع صورتها أولاً.'; return }
  const item = { ...roomMedalForm, name, imageUrl, threshold: Math.max(1, Math.floor(Number(roomMedalForm.threshold) || 1)), durationDays: Math.max(0, Math.min(3650, Math.floor(Number(roomMedalForm.durationDays) || 0))) }
  if (roomMedalEditIndex.value >= 0) roomSupport.medals.splice(roomMedalEditIndex.value, 1, item)
  else roomSupport.medals.push(item)
  resetRoomMedalForm()
}
function editRoomMedal(index) {
  const item = roomSupport.medals[index]
  Object.assign(roomMedalForm, { ...item })
  roomMedalEditIndex.value = index
}
function removeRoomMedal(index) { roomSupport.medals.splice(index, 1); if (roomMedalEditIndex.value === index) resetRoomMedalForm() }
async function uploadRoomMedalImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  roomMedalUploading.value = true; error.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw new Error(err.message || 'فشل رفع صورة المدالية')
    const payload = unwrap(data)
    const url = payload.url || payload.path
    if (!url) throw new Error('تم الرفع دون رابط للصورة')
    roomMedalForm.imageUrl = resolveAsset(url)
  } catch (e) { error.value = e?.message || 'فشل رفع صورة المدالية' }
  finally { roomMedalUploading.value = false; event.target.value = '' }
}
const recipient = reactive({})
const duration = reactive({})
const hiddenTask = reactive({})
const manualTask = reactive({})
const form = reactive({ name: '', description: '', imageUrl: '', sortOrder: 0, isAnimated: false, hiddenTaskEnabled: false, hiddenTaskEvent: 'gift', hiddenTaskTarget: 1, hiddenTaskDays: 7, manualTaskEnabled: false, manualTaskVisibility: 'hidden', manualTaskTitle: '', manualTaskDescription: '', manualTaskDays: 0 })

function unwrap(data) { return data?.data?.data ?? data?.data ?? data ?? {} }
async function load() {
  loading.value = true
  error.value = ''
  try {
    const { data, error: err } = await medalsApi.list()
    if (err) throw new Error(err.message || 'تعذر تحميل المداليات')
    const value = unwrap(data)
    medals.value = Array.isArray(value) ? value : (Array.isArray(value.items) ? value.items : [])
    for (const item of medals.value) {
      if (duration[item.id] === undefined) duration[item.id] = Number(item.manualTaskDays) || 0
      if (recipient[item.id] === undefined) recipient[item.id] = ''
      hiddenTask[item.id] = {
        enabled: !!item.hiddenTaskEnabled, event: item.hiddenTaskEvent || 'gift',
        target: Number(item.hiddenTaskTarget) || 1, days: Number(item.hiddenTaskDays) || 0,
      }
      manualTask[item.id] = {
        enabled: !!item.manualTaskEnabled,
        visibility: item.manualTaskVisibility === 'visible' ? 'visible' : 'hidden',
        title: item.manualTaskTitle || '', description: item.manualTaskDescription || '',
        days: Number(item.manualTaskDays) || 0,
      }
    }
  } catch (e) { error.value = e?.message || 'تعذر تحميل المداليات' }
  finally { loading.value = false }
}
async function uploadImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  uploading.value = true; error.value = ''
  try {
    const { data, error: err } = await uploadsApi.upload(file)
    if (err) throw new Error(err.message || 'فشل رفع الصورة')
    const payload = unwrap(data)
    const url = payload.url || payload.path
    if (!url) throw new Error('تم الرفع دون رابط للصورة')
    form.imageUrl = resolveAsset(url)
    previewBroken.value = false
  } catch (e) { error.value = e?.message || 'فشل رفع الصورة' }
  finally { uploading.value = false; event.target.value = '' }
}
async function createMedal() {
  saving.value = true; error.value = ''; success.value = ''
  try {
    const { data, error: err } = await medalsApi.create({
      name: form.name, description: form.description, imageUrl: form.imageUrl,
      category: 'general', awardType: 'manual', isActive: true,
      isAnimated: form.isAnimated, sortOrder: Number(form.sortOrder) || 0,
      hiddenTaskEnabled: form.hiddenTaskEnabled, hiddenTaskEvent: form.hiddenTaskEvent,
      hiddenTaskTarget: Math.max(1, Number(form.hiddenTaskTarget) || 1),
      hiddenTaskDays: Math.max(0, Math.min(3650, Number(form.hiddenTaskDays) || 0)),
      manualTaskEnabled: !!form.manualTaskEnabled,
      manualTaskVisibility: form.manualTaskVisibility,
      manualTaskTitle: form.manualTaskTitle,
      manualTaskDescription: form.manualTaskDescription,
      manualTaskDays: Math.max(0, Math.min(3650, Number(form.manualTaskDays) || 0)),
    })
    if (err) throw new Error(err.message || 'تعذر حفظ المدالية')
    success.value = 'تم حفظ المدالية بنجاح.'
    Object.assign(form, { name: '', description: '', imageUrl: '', sortOrder: 0, isAnimated: false, hiddenTaskEnabled: false, hiddenTaskEvent: 'gift', hiddenTaskTarget: 1, hiddenTaskDays: 7, manualTaskEnabled: false, manualTaskVisibility: 'hidden', manualTaskTitle: '', manualTaskDescription: '', manualTaskDays: 0 })
    await load()
  } catch (e) { error.value = e?.message || 'تعذر حفظ المدالية' }
  finally { saving.value = false }
}
async function saveHiddenTask(medal) {
  const config = hiddenTask[medal.id]
  if (!config) return
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { error: err } = await medalsApi.update(medal.id, {
      hiddenTaskEnabled: !!config.enabled,
      hiddenTaskEvent: config.event,
      hiddenTaskTarget: Math.max(1, Math.floor(Number(config.target) || 1)),
      hiddenTaskDays: Math.max(0, Math.min(3650, Math.floor(Number(config.days) || 0))),
    })
    if (err) throw new Error(err.message || 'تعذر حفظ المهمة السرّية')
    success.value = config.enabled ? 'تم حفظ المهمة السرّية وتفعيل المنح التلقائي.' : 'تم إيقاف المهمة السرّية.'
    await load()
  } catch (e) { error.value = e?.message || 'تعذر حفظ المهمة السرّية' }
  finally { busyId.value = '' }
}
async function saveManualTask(medal) {
  const config = manualTask[medal.id]
  if (!config) return
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { error: err } = await medalsApi.update(medal.id, {
      manualTaskEnabled: !!config.enabled,
      manualTaskVisibility: config.visibility === 'visible' ? 'visible' : 'hidden',
      manualTaskTitle: String(config.title || '').trim(),
      manualTaskDescription: String(config.description || '').trim(),
      manualTaskDays: Math.max(0, Math.min(3650, Math.floor(Number(config.days) || 0))),
    })
    if (err) throw new Error(err.message || 'تعذر حفظ المهمة اليدوية')
    success.value = config.enabled
      ? (config.visibility === 'visible' ? 'تم حفظ المهمة اليدوية وإظهارها في مركز المهام.' : 'تم حفظ المهمة اليدوية وإخفاؤها عن المستخدم.')
      : 'تم إيقاف المهمة اليدوية.'
    await load()
  } catch (e) { error.value = e?.message || 'تعذر حفظ المهمة اليدوية' }
  finally { busyId.value = '' }
}
async function giftOne(medal) {
  const userId = String(recipient[medal.id] || '').trim()
  if (!userId) { error.value = 'أدخل ID المستخدم أولاً.'; return }
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { data, error: err } = await medalsApi.grant(medal.id, { userId, durationDays: Number(duration[medal.id]) || 0 })
    if (err) throw new Error(err.message || 'تعذر إهداء المدالية')
    const payload = unwrap(data)
    success.value = `تم إهداء «${medal.name}» إلى ${payload.user?.displayName || payload.user?.username || userId}.`
    recipient[medal.id] = ''
  } catch (e) { error.value = e?.message || 'تعذر إهداء المدالية' }
  finally { busyId.value = '' }
}
async function giftEveryone(medal) {
  if (!window.confirm(`تأكيد إهداء «${medal.name}» لجميع الحسابات؟ الحساب الذي يملك المدالية الفعالة لن يحصل على نسخة مكررة.`)) return
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { data, error: err } = await medalsApi.grantAll(medal.id, { durationDays: Number(duration[medal.id]) || 0 })
    if (err) throw new Error(err.message || 'تعذر إهداء المدالية لجميع الحسابات')
    const payload = unwrap(data)
    success.value = `اكتملت العملية: أُهديت المدالية إلى ${payload.granted ?? 0} حساب، وتُخطّي ${payload.skipped ?? 0} حساباً يملكها بالفعل.`
  } catch (e) { error.value = e?.message || 'تعذر إهداء المدالية لجميع الحسابات' }
  finally { busyId.value = '' }
}
async function toggleActive(medal) {
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { error: err } = await medalsApi.update(medal.id, { isActive: !medal.isActive })
    if (err) throw new Error(err.message || 'تعذر تحديث حالة المدالية')
    success.value = 'تم تحديث حالة المدالية.'
    await load()
  } catch (e) { error.value = e?.message || 'تعذر تحديث حالة المدالية' }
  finally { busyId.value = '' }
}
async function deleteMedal(medal) {
  if (!window.confirm(`حذف المدالية «${medal.name}»؟ سيتم حذف ارتباطها بحسابات المستخدمين أيضاً.`)) return
  busyId.value = medal.id; error.value = ''; success.value = ''
  try {
    const { error: err } = await medalsApi.remove(medal.id)
    if (err) throw new Error(err.message || 'تعذر حذف المدالية')
    success.value = 'تم حذف المدالية.'
    await load()
  } catch (e) { error.value = e?.message || 'تعذر حذف المدالية' }
  finally { busyId.value = '' }
}
onMounted(() => { load(); loadRoomSupportConfig() })
</script>

<style scoped>
.medals-page { color: #f7f8fc; }
.room-support-icon { background:linear-gradient(135deg,#d7a83d,#8b5a12); color:#fff; }
.room-support-settings { display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:14px;padding:16px;border:1px solid rgba(215,168,61,.28);border-radius:14px;background:rgba(150,103,25,.10); }
.room-support-settings label,.room-medal-editor label { display:flex;flex-direction:column;gap:7px;font-size:.88rem;font-weight:700;color:#dce4f2; }
.room-support-settings .check-row,.room-medal-editor .check-row { flex-direction:row;align-items:center; }
.room-medal-editor { margin-top:18px;padding:16px;border:1px solid rgba(255,255,255,.1);border-radius:15px;background:rgba(10,16,28,.35); }
.room-medal-editor h3 { font-size:1rem;font-weight:800;margin:0 0 14px;color:#f4d58b; }
.room-medal-actions,.room-support-footer { display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px;flex-wrap:wrap; }
.room-medal-list { display:grid;gap:10px;margin-top:16px; }
.room-medal-row { display:flex;align-items:center;gap:12px;padding:10px;border:1px solid rgba(255,255,255,.1);border-radius:12px;background:rgba(255,255,255,.035); }
.room-medal-row img { width:48px;height:48px;object-fit:contain;border-radius:10px;background:#101827; }
.room-medal-info { display:grid;gap:4px;flex:1;min-width:0; }
.room-medal-info small,.room-support-footer span { color:#a8b1c5;font-size:.8rem; }
.room-support-footer { padding-top:14px;border-top:1px solid rgba(255,255,255,.1); }
.room-support-panel .secret-note { margin-top:12px; }
@media(max-width:650px) { .room-medal-row { flex-wrap:wrap; } .room-medal-info { min-width:calc(100% - 70px); } }
.panel { background: linear-gradient(145deg, rgba(30,36,52,.96), rgba(18,23,36,.96)); border: 1px solid rgba(255,255,255,.09); border-radius: 20px; padding: 22px; margin-bottom: 26px; }
.section-heading { display:flex; align-items:center; gap:14px; margin: 22px 0 16px; }
.section-heading h2 { font-size: 1.15rem; font-weight: 800; margin:0 0 5px; }
.section-heading p { color: #a8b1c5; margin:0; font-size:.9rem; }
.heading-icon { width:48px;height:48px;border-radius:15px;display:grid;place-items:center;background:linear-gradient(135deg,#20b26b,#117b4b);font-size:1.35rem; }
.hidden-task-config,.hidden-task-card,.manual-task-card { grid-column:1/-1;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;padding:15px;border:1px solid rgba(124,226,173,.25);border-radius:14px;background:rgba(18,95,61,.12); }
.hidden-task-config h3,.hidden-task-config p,.hidden-task-card h4,.manual-task-card h3,.manual-task-card h4,.manual-task-card p,.secret-note { grid-column:1/-1;margin:0; }
.hidden-task-config h3,.hidden-task-card h4,.manual-task-card h3,.manual-task-card h4 { color:#7ce2ad;font-size:.98rem;font-weight:800; }
.hidden-task-config p,.manual-task-card p,.secret-note,.hidden-task-config small,.hidden-task-card small,.manual-task-card small { color:#a8b1c5;font-size:.8rem; }
.hidden-task-card { margin-top:12px; }
.form-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;align-items:end; }
.form-grid label,.gift-box label { display:flex;flex-direction:column;gap:7px;font-size:.88rem;font-weight:700;color:#dce4f2; }
.form-grid .wide { grid-column:1/-1; }
.form-control,.form-select { background:#111827!important;color:#f8fafc!important;border-color:#3b465a!important; }
.upload-box { border:1px dashed #438e6c;border-radius:12px;padding:14px;cursor:pointer;display:flex;align-items:center;justify-content:center;color:#7ce2ad!important; }
.upload-box input { max-width:100%;margin-top:8px; }
.medal-preview { width:100px;height:100px;border-radius:22px;background:#0e1421;border:1px solid #3c4a60;display:grid;place-items:center;overflow:hidden;padding:7px; }
.medal-preview img { width:100%;height:100%;object-fit:contain; }
.check-row { flex-direction:row!important;align-items:center; }
.submit-btn { min-height:44px;font-weight:800; }
.list-heading { justify-content:space-between;align-items:center; }
.medal-grid { display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:18px; }
.medal-card { border-radius:20px;padding:17px;background:linear-gradient(155deg,#222b40,#141b2b);border:1px solid rgba(255,255,255,.1);box-shadow:0 12px 28px rgba(0,0,0,.15); }
.medal-card.inactive { opacity:.72; }
.card-top { display:flex;justify-content:space-between;align-items:start; }
.medal-art { width:92px;height:92px;border-radius:25px;padding:9px;background:radial-gradient(circle at 30% 20%,#485b73,#111827);box-shadow:0 0 0 1px #60738b; }
.medal-art img { width:100%;height:100%;object-fit:contain; }
.status { border-radius:999px;padding:5px 10px;font-size:.75rem;font-weight:800; }
.status.on { color:#7ce2ad;background:#12462f; }.status.off { color:#f9a8a8;background:#51252b; }
.medal-card h3 { font-size:1.05rem;font-weight:800;margin:14px 0 6px; }
.description { min-height:38px;color:#aeb8ca;font-size:.84rem; }
.gift-box { border-top:1px solid rgba(255,255,255,.1);margin-top:14px;padding-top:14px;display:grid;gap:11px; }
.card-actions { display:flex;justify-content:space-between;gap:8px;margin-top:14px; }
.empty,.loading { text-align:center;color:#bdc7d8; }
.footnote { color:#9ba8bd;font-size:.85rem;margin:20px 0; }
.footnote i { color:#69d49b; }
@media(max-width:650px) { .form-grid { grid-template-columns:1fr; }.form-grid .wide { grid-column:auto; }.list-heading { align-items:flex-start;gap:12px;flex-direction:column; } }
</style>
