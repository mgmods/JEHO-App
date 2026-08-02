<template>
  <div>
    <PageHeader title="العروض والترقيات" subtitle="شهرية · وكلاء · داعمين · مدد VIP">
      <template #actions>
        <button class="btn btn-outline-light btn-sm" :disabled="expiring" @click="expireVanity">
          إنهاء الآي دي المنتهي
        </button>
      </template>
    </PageHeader>
    <AlertMessage v-if="error" :message="error" type="warning" @dismiss="error = ''" />
    <AlertMessage v-if="ok" :message="ok" type="success" @dismiss="ok = ''" />
    <LoadingSpinner v-if="loading" />
    <template v-else-if="catalog">
      <div class="glass p-3 mb-3">
        <div class="fw-semibold mb-1">إصدار الكتالوج</div>
        <div class="small text-muted">{{ catalog.version }}</div>
      </div>

      <h3 class="h6 mb-2">العروض الشهرية</h3>
      <div class="widget-grid mb-4">
        <article v-for="o in catalog.monthlyOffers || []" :key="o.id" class="widget-card">
          <div class="widget-card-body">
            <h4 class="widget-card-title">{{ o.titleAr || o.titleEn }}</h4>
            <p class="small text-muted mb-2">{{ o.descriptionAr || o.descriptionEn }}</p>
            <div class="d-flex gap-2 flex-wrap">
              <span class="badge text-bg-primary">${{ o.thresholdUsd }}</span>
              <span class="badge text-bg-success">{{ o.rewardDays }} يوم</span>
            </div>
          </div>
        </article>
      </div>

      <h3 class="h6 mb-2">سياسة الوكلاء (بونص على الرصيد)</h3>
      <div class="widget-grid mb-4">
        <article v-for="t in catalog.agentTiers || []" :key="t.id" class="widget-card">
          <div class="widget-card-body">
            <h4 class="widget-card-title">{{ t.titleAr }}</h4>
            <div class="d-flex gap-2 flex-wrap">
              <span class="badge text-bg-primary">شحن ${{ t.thresholdUsd }}</span>
              <span class="badge text-bg-warning">+{{ t.bonusPercent }}%</span>
            </div>
          </div>
        </article>
      </div>

      <h3 class="h6 mb-2">عروض الداعمين</h3>
      <div class="widget-grid mb-4">
        <article v-for="p in catalog.supporterPacks || []" :key="p.id" class="widget-card">
          <div class="widget-card-body">
            <h4 class="widget-card-title">{{ p.titleAr || p.titleEn }}</h4>
            <p class="small text-muted mb-2">{{ p.descriptionAr || p.descriptionEn }}</p>
            <div class="d-flex gap-2 flex-wrap">
              <span class="badge text-bg-primary">${{ p.thresholdUsd }}</span>
              <span class="badge text-bg-success">{{ p.rewardDays }} يوم</span>
              <span class="badge text-bg-secondary">إطار + ID</span>
            </div>
          </div>
        </article>
      </div>

      <h3 class="h6 mb-2">مدد VIP (ليست دائمة)</h3>
      <div class="widget-grid">
        <article
          v-for="d in catalog.vipDurations || []"
          :key="d.id"
          class="widget-card"
          :style="{ borderTop: `3px solid ${d.color || '#888'}` }"
        >
          <div class="widget-card-body">
            <h4 class="widget-card-title">{{ d.labelAr }} / {{ d.labelEn }}</h4>
            <div class="d-flex gap-2 flex-wrap">
              <span class="badge text-bg-dark">{{ d.days }} يوم</span>
              <span class="badge text-bg-info">×{{ d.priceMultiplier }} من السعر الشهري</span>
            </div>
          </div>
        </article>
      </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '@/api/client'
import { toast } from '@/composables/useToast'
import PageHeader from '@/components/PageHeader.vue'
import AlertMessage from '@/components/AlertMessage.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const catalog = ref(null)
const loading = ref(true)
const error = ref('')
const ok = ref('')
const expiring = ref(false)

async function expireVanity() {
  expiring.value = true
  error.value = ''
  ok.value = ''
  try {
    const { data } = await api.post('/promotions/admin/expire-vanity')
    const payload = data?.data ?? data
    ok.value = `تم إنهاء ${payload?.expired ?? 0} آي دي منتهي`
    toast().success(ok.value)
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || 'تعذر الإنهاء'
    toast().danger(error.value)
  } finally {
    expiring.value = false
  }
}

onMounted(async () => {
  try {
    const { data } = await api.get('/promotions/catalog')
    catalog.value = data?.data ?? data
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || 'تعذر تحميل العروض'
  } finally {
    loading.value = false
  }
})
</script>
