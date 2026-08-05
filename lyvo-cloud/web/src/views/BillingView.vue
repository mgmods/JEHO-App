<template>
  <div>
    <div class="page-head">
      <h2>My balance</h2>
      <p>رصيد الدقائق لحسابك كمطور — ليس إعدادات منصة.</p>
    </div>
    <div class="grid-stats">
      <div class="stat-card">
        <div class="label">Total minutes</div>
        <div class="value">{{ (dev?.totalMinutesRemaining || 0).toLocaleString() }}</div>
        <div class="hint">Available for live sessions</div>
      </div>
      <div class="stat-card">
        <div class="label">Free trial left</div>
        <div class="value">{{ freeRem.toLocaleString() }}</div>
        <div class="hint">Included with new accounts</div>
      </div>
      <div class="stat-card">
        <div class="label">Paid remaining</div>
        <div class="value">{{ paidRem.toLocaleString() }}</div>
        <div class="hint">From purchased packages</div>
      </div>
    </div>
    <div class="card" style="max-width:480px">
      <h3>Need more minutes?</h3>
      <p style="margin-bottom:14px">اشترِ باقة ببطاقة Fourthwall — تظهر في قائمة المطور فقط.</p>
      <button class="btn btn-primary" type="button" @click="$router.push({ name: 'packages' })">Buy packages</button>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted } from 'vue'

const dash = inject('dash')
const refreshDash = inject('refreshDash')
const dev = computed(() => dash?.value?.developer)
const freeRem = computed(() => dash?.value?.freeMinutes?.remaining ?? 0)
const paidRem = computed(() => dash?.value?.paidMinutes?.remaining ?? 0)

onMounted(async () => {
  if (!dash?.value) await refreshDash?.()
})
</script>
