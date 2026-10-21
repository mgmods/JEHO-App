<script setup>
import { defineAsyncComponent, onErrorCaptured, ref } from 'vue'

const failed = ref('')
const attempt = ref(0)

function loadGamesView() {
  return import('./GamesView.vue')
}

const GamesView = defineAsyncComponent({
  loader: loadGamesView,
  timeout: 15000,
  onError(error, retry, fail, attempts) {
    failed.value = error?.message || String(error || 'تعذّر تحميل ملف صفحة الألعاب')
    if (attempts < 2) retry()
    else fail()
  },
})

onErrorCaptured((error) => {
  failed.value = error?.message || String(error || 'خطأ غير معروف أثناء عرض صفحة الألعاب')
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
  <section class="games-page-shell">
    <header class="glass p-3 mb-3">
      <h1 class="h4 mb-1">إدارة الألعاب</h1>
      <p class="small text-muted mb-0">إدارة قائمة الألعاب وإعداداتها ونسب الفوز ومتجر الألعاب.</p>
    </header>

    <section v-if="failed" class="glass p-4" role="alert" aria-live="assertive">
      <h2 class="h5 mb-2">تعذّر فتح إدارة الألعاب</h2>
      <p class="text-muted mb-2">ظهر خطأ أثناء تحميل صفحة الألعاب. تفاصيل الخطأ:</p>
      <pre class="small text-break text-wrap mb-3">{{ failed }}</pre>
      <div class="d-flex flex-wrap gap-2">
        <button class="btn btn-aurora btn-sm" type="button" @click="retry">إعادة محاولة فتح الألعاب</button>
        <button class="btn btn-outline-light btn-sm" type="button" @click="reloadPage">إعادة تحميل لوحة الإدارة</button>
      </div>
    </section>

    <GamesView v-else :key="attempt" />
  </section>
</template>
