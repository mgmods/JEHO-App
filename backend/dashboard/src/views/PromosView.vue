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
      <div class="neo-card promo-hero mb-3">
        <div class="fw-semibold mb-1">إصدار الكتالوج</div>
        <div class="small text-muted">{{ catalog.version }}</div>
      </div>

      <h3 class="h6 mb-2">العروض الشهرية</h3>
      <div class="widget-grid mb-4">
        <article v-for="o in catalog.monthlyOffers || []" :key="o.id" class="widget-card promo-card">
          <div class="widget-card-body">
            <div class="promo-ic"><i class="bi bi-calendar-heart"></i></div>
            <h4 class="widget-card-title">{{ o.titleAr || o.titleEn }}</h4>
            <p class="small text-muted mb-2">{{ o.descriptionAr || o.descriptionEn }}</p>
            <div class="d-flex gap-2 flex-wrap">
              <span class="neo-price">${{ o.thresholdUsd }}</span>
              <span class="neo-pill neo-pill--talk">{{ o.rewardDays }} يوم</span>
            </div>
          </div>
        </article>
      </div>

      <h3 class="h6 mb-2">سياسة الوكلاء (بونص على الرصيد)</h3>
      <div class="widget-grid mb-4">
        <article v-for="t in catalog.agentTiers || []" :key="t.id" class="widget-card promo-card">
          <div class="widget-card-body">
            <div class="promo-ic is-gold"><i class="bi bi-percent"></i></div>
            <h4 class="widget-card-title">{{ t.titleAr }}</h4>
            <div class="d-flex gap-2 flex-wrap">
              <span class="neo-price">شحن ${{ t.thresholdUsd }}</span>
              <span class="neo-pill neo-pill--heat">+{{ t.bonusPercent }}%</span>
            </div>
          </div>
        </article>
      </div>

      <h3 class="h6 mb-2">عروض الداعمين</h3>
      <div class="widget-grid mb-4">
        <article v-for="p in catalog.supporterPacks || []" :key="p.id" class="widget-card promo-card">
          <div class="widget-card-body">
            <div class="promo-ic is-cyan"><i class="bi bi-award"></i></div>
            <h4 class="widget-card-title">{{ p.titleAr || p.titleEn }}</h4>
            <p class="small text-muted mb-2">{{ p.descriptionAr || p.descriptionEn }}</p>
            <div class="d-flex gap-2 flex-wrap">
              <span class="neo-price">${{ p.thresholdUsd }}</span>
              <span class="neo-pill neo-pill--talk">{{ p.rewardDays }} يوم</span>
              <span class="neo-pill neo-pill--heat">إطار + ID</span>
            </div>
          </div>
        </article>
      </div>

      <h3 class="h6 mb-2">مدد VIP (ليست دائمة)</h3>
      <div class="widget-grid">
        <article
          v-for="d in catalog.vipDurations || []"
          :key="d.id"
          class="widget-card promo-card"
          :style="{ borderTop: `3px solid ${d.color || '#8b5cf6'}` }"
        >
          <div class="widget-card-body">
            <h4 class="widget-card-title">{{ d.labelAr }} / {{ d.labelEn }}</h4>
            <div class="d-flex gap-2 flex-wrap">
              <span class="neo-pill neo-pill--talk">{{ d.days }} يوم</span>
              <span class="neo-price">×{{ d.priceMultiplier }}</span>
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

<style scoped>
.promo-hero {
  padding: 1rem 1.15rem;
}
.promo-card .promo-ic {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.65rem;
  color: #c4b5fd;
  background: rgba(139, 92, 246, 0.16);
  box-shadow: 0 0 16px rgba(139, 92, 246, 0.25);
}
.promo-card .promo-ic.is-gold {
  color: #fbbf24;
  background: rgba(251, 191, 36, 0.14);
}
.promo-card .promo-ic.is-cyan {
  color: #67e8f9;
  background: rgba(34, 211, 238, 0.14);
}
</style>
