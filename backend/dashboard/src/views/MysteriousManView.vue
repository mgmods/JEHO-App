<template>
  <div class="container-fluid">
    <PageHeader title="إعدادات الرجل الغامض" subtitle="إدارة الصورة الموحدة وبطاقة الدخول لجميع المستخدمين المفعّلين للميزة" />
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="success" :message="success" type="success" @dismiss="success = ''" />

    <div class="glass p-3 mb-3">
      <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
        <div>
          <h5 class="mb-1">الصورة الشخصية الموحدة</h5>
          <p class="text-muted mb-0">تظهر بدل الصورة الأصلية أثناء تفعيل الرجل الغامض فقط. يجب أن يحتفظ التطبيق بالصورة الأصلية لاستعادتها عند انتهاء التفعيل أو إلغائه.</p>
        </div>
        <span class="badge text-bg-dark">Avatar</span>
      </div>
      <div class="row g-3 align-items-center">
        <div class="col-md-3 text-center">
          <div class="preview-avatar mx-auto">
            <img v-if="form.avatarUrl" :src="form.avatarUrl" alt="معاينة صورة الرجل الغامض" />
            <i v-else class="bi bi-incognito"></i>
          </div>
          <small class="text-muted d-block mt-2">معاينة الصورة</small>
        </div>
        <div class="col-md-9">
          <label class="form-label" for="mysterious-avatar">رفع صورة أو استبدالها</label>
          <input id="mysterious-avatar" class="form-control" type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml" :disabled="uploading.avatar" @change="uploadFile($event, 'avatarUrl', 'avatar')" />
          <div v-if="uploading.avatar" class="form-text">جارٍ رفع الصورة…</div>
          <label class="form-label mt-3" for="mysterious-avatar-url">رابط الصورة</label>
          <input id="mysterious-avatar-url" v-model.trim="form.avatarUrl" class="form-control" type="url" maxlength="2048" placeholder="https://..." />
          <button v-if="form.avatarUrl" type="button" class="btn btn-sm btn-outline-danger mt-2" @click="form.avatarUrl = ''">إزالة الصورة من الإعدادات</button>
        </div>
      </div>
    </div>

    <div class="glass p-3 mb-3">
      <div class="d-flex align-items-center justify-content-between flex-wrap gap-2 mb-3">
        <div>
          <h5 class="mb-1">بطاقة الدخول الموحدة</h5>
          <p class="text-muted mb-0">تُستخدم عند دخول المستخدم المفعّل إلى أي روم. يمكن استبدال ملف البطاقة في أي وقت.</p>
        </div>
        <span class="badge text-bg-dark">Room Entry</span>
      </div>
      <div class="row g-3">
        <div class="col-lg-5">
          <label class="form-label" for="entry-mode">نوع بطاقة الدخول</label>
          <select id="entry-mode" v-model="form.entryMode" class="form-select">
            <option value="video">فيديو / تأثير متحرك</option>
            <option value="text">بطاقة نصية</option>
          </select>
          <label class="form-label mt-3" for="entry-text">النص الظاهر على البطاقة</label>
          <input id="entry-text" v-model.trim="form.entryText" class="form-control" maxlength="160" placeholder="دخل الرجل الغامض" />
          <label class="form-label mt-3" for="entry-effect">رفع أو استبدال ملف البطاقة (صورة أو فيديو أو SVGA)</label>
          <input id="entry-effect" class="form-control" type="file" accept="image/*,video/mp4,video/webm,video/quicktime,.svga" :disabled="uploading.entry" @change="uploadFile($event, 'entryEffectUrl', 'entry')" />
          <div v-if="uploading.entry" class="form-text">جارٍ رفع ملف بطاقة الدخول…</div>
          <label class="form-label mt-3" for="entry-url">رابط ملف البطاقة</label>
          <input id="entry-url" v-model.trim="form.entryEffectUrl" class="form-control" type="url" maxlength="2048" placeholder="https://..." />
          <button v-if="form.entryEffectUrl" type="button" class="btn btn-sm btn-outline-danger mt-2" @click="form.entryEffectUrl = ''">إزالة ملف البطاقة من الإعدادات</button>
        </div>
        <div class="col-lg-7">
          <label class="form-label">معاينة البطاقة</label>
          <div class="entry-preview">
            <video v-if="isVideo(form.entryEffectUrl)" :key="form.entryEffectUrl" :src="form.entryEffectUrl" controls muted loop playsinline />
            <img v-else-if="form.entryEffectUrl && isImage(form.entryEffectUrl)" :src="form.entryEffectUrl" alt="معاينة بطاقة الدخول" />
            <div v-else class="entry-placeholder"><i class="bi bi-stars"></i><strong>{{ form.entryText || 'دخل الرجل الغامض' }}</strong><small>ستظهر البطاقة للمستخدمين المفعّلين عند دخول الروم</small></div>
          </div>
          <label class="form-label mt-3" for="message-effect">رابط تأثير الرسائل (اختياري)</label>
          <input id="message-effect" v-model.trim="form.messageEffectUrl" class="form-control" type="url" maxlength="2048" placeholder="https://..." />
          <small class="text-muted">هذا الحقل يحفظ رابط التأثير فقط؛ ظهوره داخل التطبيق يعتمد على دعم إصدار التطبيق الحالي له.</small>
        </div>
      </div>
    </div>

    <div class="glass p-3 d-flex align-items-center justify-content-between flex-wrap gap-3">
      <div>
        <strong>تطبيق الإعدادات المشتركة</strong>
        <div class="text-muted small">الحفظ يحدّث إعدادات الخادم المشتركة، ولا يغيّر صور المستخدمين الأصلية في قاعدة البيانات.</div>
      </div>
      <div class="d-flex gap-2">
        <button type="button" class="btn btn-outline-secondary" :disabled="loading || saving" @click="load">إعادة تحميل</button>
        <button type="button" class="btn btn-aurora" :disabled="loading || saving || uploading.avatar || uploading.entry" @click="save">
          <i class="bi bi-check2-circle me-1"></i>{{ saving ? 'جارٍ الحفظ…' : 'حفظ وتطبيق الإعدادات' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { mysteriousManApi, uploadsApi } from '@/api'

const blank = () => ({
  avatarUrl: '',
  entryMode: 'video',
  entryText: 'دخل الرجل الغامض',
  entryEffectUrl: '',
  messageEffectUrl: '',
})
const form = reactive(blank())
const uploading = reactive({ avatar: false, entry: false })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

function payloadOf(result) {
  return result?.data?.data ?? result?.data ?? {}
}
function isVideo(url) {
  return /\.(mp4|webm|mov)(\?.*)?$/i.test(String(url || ''))
}
function isImage(url) {
  return /\.(png|jpe?g|gif|webp|svg)(\?.*)?$/i.test(String(url || ''))
}
async function load() {
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await mysteriousManApi.get()
    if (result?.error) throw new Error(result.error.message || 'تعذر تحميل إعدادات الرجل الغامض.')
    Object.assign(form, blank(), payloadOf(result))
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر تحميل إعدادات الرجل الغامض.'
  } finally {
    loading.value = false
  }
}
async function uploadFile(event, field, kind) {
  const file = event?.target?.files?.[0]
  if (!file) return
  const allowed = kind === 'avatar'
    ? /^image\//i.test(file.type)
    : (/^image\//i.test(file.type) || /^video\//i.test(file.type) || /\.svga$/i.test(file.name))
  if (!allowed) {
    error.value = kind === 'avatar' ? 'اختر ملف صورة صالحًا.' : 'اختر صورة أو فيديو أو ملف SVGA صالحًا.'
    event.target.value = ''
    return
  }
  if (file.size > 40 * 1024 * 1024) {
    error.value = 'حجم الملف يتجاوز الحد المسموح به وهو 40 ميغابايت.'
    event.target.value = ''
    return
  }
  uploading[kind] = true
  error.value = ''
  success.value = ''
  try {
    const result = await uploadsApi.upload(file)
    if (result?.error) throw new Error(result.error.message || 'رفض الخادم رفع الملف.')
    const payload = payloadOf(result)
    if (!payload.url) throw new Error('لم يُرجع الخادم رابط الملف بعد الرفع.')
    form[field] = payload.url
    success.value = kind === 'avatar' ? 'تم رفع الصورة. اضغط حفظ وتطبيق الإعدادات لاعتمادها.' : 'تم رفع ملف البطاقة. اضغط حفظ وتطبيق الإعدادات لاعتماده.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر رفع الملف.'
  } finally {
    uploading[kind] = false
    event.target.value = ''
  }
}
async function save() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await mysteriousManApi.update({
      avatarUrl: form.avatarUrl,
      entryMode: form.entryMode,
      entryText: form.entryText || 'دخل الرجل الغامض',
      entryEffectUrl: form.entryEffectUrl,
      messageEffectUrl: form.messageEffectUrl,
    })
    if (result?.error) throw new Error(result.error.message || 'رفض الخادم حفظ الإعدادات.')
    Object.assign(form, blank(), payloadOf(result))
    success.value = 'تم حفظ إعدادات الرجل الغامض المشتركة. ستستخدمها أجزاء التطبيق التي تقرأ إعدادات هذه الميزة من الخادم.'
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || 'تعذر حفظ إعدادات الرجل الغامض.'
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.preview-avatar {
  width: 132px;
  height: 132px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 2px solid rgba(210, 177, 95, .8);
  background: radial-gradient(circle at 30% 20%, #343434, #090909 75%);
  color: #d7b765;
  font-size: 2.5rem;
}
.preview-avatar img { width: 100%; height: 100%; object-fit: cover; }
.entry-preview {
  min-height: 260px;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(210, 177, 95, .45);
  background: radial-gradient(ellipse at center, #2a2416 0%, #101010 72%);
}
.entry-preview img, .entry-preview video { display: block; width: 100%; max-height: 420px; object-fit: contain; }
.entry-placeholder { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 24px; text-align: center; color: #e5ca82; }
.entry-placeholder i { font-size: 2.4rem; }
.entry-placeholder strong { font-size: 1.35rem; letter-spacing: .03em; }
.entry-placeholder small { color: #d1d1d1; }
</style>
