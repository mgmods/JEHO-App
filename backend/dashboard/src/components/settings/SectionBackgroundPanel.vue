<template>
  <section class="settings-card">
    <h3 class="settings-card-title">خلفية الأقسام الرئيسية</h3>
    <p class="form-text mb-3">صورة واحدة مشتركة لخلفية الصفحة الرئيسية خلف البنر وكروت الرومات، وقسم «أنا»، وقائمة الرسائل والمحادثات الخاصة، والصفحة الرئيسية للألعاب. لا تُطبّق داخل غرف الصوت نفسها أو داخل واجهة اللعب.</p>
    <div v-if="previewUrl" class="mb-3">
      <img :src="previewUrl" alt="معاينة خلفية الأقسام" style="display:block;width:100%;max-height:280px;object-fit:cover;border-radius:12px" />
    </div>
    <div class="d-flex flex-wrap gap-2 align-items-center">
      <input ref="fileInput" class="form-control" style="max-width:360px" type="file" accept="image/png,image/jpeg,image/webp" @change="selectImage" />
      <button class="btn btn-aurora" type="button" :disabled="!selectedFile || uploading || saving" @click="uploadAndSave">
        {{ uploading ? 'جارٍ رفع الصورة…' : saving ? 'جارٍ الحفظ…' : 'رفع وحفظ الخلفية' }}
      </button>
      <button class="btn btn-ghost" type="button" :disabled="saving || uploading || !savedUrl" @click="removeBackground">إزالة الخلفية</button>
    </div>
    <p v-if="message" class="form-text mt-3" role="status">{{ message }}</p>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '@/api/client'
import { settingsApi } from '@/api'
import { resolveAsset } from '@/utils/assets'

const fileInput = ref(null)
const selectedFile = ref(null)
const previewUrl = ref('')
const savedUrl = ref('')
const saving = ref(false)
const uploading = ref(false)
const message = ref('')
let themeConfig = {}

function makeAbsoluteUrl(value) {
  return resolveAsset(value)
}

async function loadTheme() {
  const result = await settingsApi.get()
  if (result.error) throw new Error(result.error)
  const rows = result.data || []
  const row = rows.find((item) => item.key === 'app_theme')
  try { themeConfig = row?.value ? JSON.parse(row.value) : {} } catch { themeConfig = {} }
  const bg = themeConfig.backgrounds || {}
  savedUrl.value = bg.allSections || bg.home || bg.profile || bg.chat || bg.games || ''
  previewUrl.value = makeAbsoluteUrl(savedUrl.value)
}

function selectImage(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!/^image\/(png|jpeg|webp)$/.test(file.type)) {
    message.value = 'اختر صورة PNG أو JPG أو WebP.'
    return
  }
  selectedFile.value = file
  if (previewUrl.value.startsWith('blob:')) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = URL.createObjectURL(file)
  message.value = ''
}

async function persistTheme(url) {
  const backgrounds = { ...(themeConfig.backgrounds || {}) }
  if (url) {
    backgrounds.allSections = url
    backgrounds.home = url
    backgrounds.profile = url
    backgrounds.chat = url
    backgrounds.games = url
  } else {
    delete backgrounds.allSections
    delete backgrounds.home
    delete backgrounds.profile
    delete backgrounds.chat
    delete backgrounds.games
  }
  themeConfig = { ...themeConfig, version: (Number(themeConfig.version) || 1) + 1, updatedAt: new Date().toISOString(), backgrounds }
  const result = await settingsApi.update({ app_theme: JSON.stringify(themeConfig) })
  if (result.error) throw new Error(result.error)
  savedUrl.value = url
  previewUrl.value = makeAbsoluteUrl(url)
}

async function uploadAndSave() {
  if (!selectedFile.value) return
  uploading.value = true
  message.value = ''
  try {
    const form = new FormData()
    form.append('file', selectedFile.value)
    const response = await api.post('/uploads', form)
    const payload = response.data?.data ?? response.data
    const url = payload?.url || payload?.path || payload?.publicUrl || payload?.public_url
    if (!url) throw new Error('تم الرفع لكن لم يرجع الخادم رابط الصورة المتوقع.')
    saving.value = true
    await persistTheme(url)
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    message.value = 'تم حفظ الخلفية للأقسام الرئيسية.'
  } catch (error) {
    message.value = error.message || 'تعذّر رفع الخلفية أو حفظها.'
  } finally {
    uploading.value = false
    saving.value = false
  }
}

async function removeBackground() {
  saving.value = true
  message.value = ''
  try {
    await persistTheme('')
    selectedFile.value = null
    if (fileInput.value) fileInput.value.value = ''
    message.value = 'تمت إزالة الخلفية المخصصة، وسيعود التطبيق للخلفية الأصلية.'
  } catch (error) {
    message.value = error.message || 'تعذّر حفظ الإعداد.'
  } finally {
    saving.value = false
  }
}

onMounted(() => loadTheme().catch((error) => { message.value = error.message || 'تعذّر تحميل إعداد الخلفية.' }))
</script>
