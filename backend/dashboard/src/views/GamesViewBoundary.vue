<script setup>
import { defineAsyncComponent, onErrorCaptured, ref } from 'vue'

const failed = ref('')
const attempt = ref(0)

const GamesView = defineAsyncComponent(() => import('./GamesView.vue'))

onErrorCaptured((error) => {
  failed.value = error?.message || String(error || 'خطأ غير معروف')
  return false
})

function retry() {
  failed.value = ''
  attempt.value += 1
}

function reloadPage() {
  window.location.reload()
}
</script>

<template>
  <section v-if="failed" class="glass p-4" role="alert" aria-live="assertive">
    <h1 class="h4 mb-2">تعذّر فتح إدارة الألعاب</h1>
    <p class="text-muted mb-3">تم إيقاف عرض صفحة الألعاب بسبب خطأ برمجي بدل ترك القسم فارغًا. تفاصيل الخطأ:</p>
    <pre class="small text-break text-wrap mb-3">{{ failed }}</pre>
    <div class="d-flex flex-wrap gap-2">
      <button class="btn btn-aurora btn-sm" type="button" @click="retry">إعادة محاولة فتح الألعاب</button>
      <button class="btn btn-outline-light btn-sm" type="button" @click="reloadPage">إعادة تحميل لوحة الإدارة</button>
    </div>
  </section>
  <GamesView v-else :key="attempt" />
</template>
