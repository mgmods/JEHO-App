<template>
  <div>
    <div class="page-head">
      <h2>Buy voice minutes</h2>
      <p>
        باقات احترافية بالبطاقة عبر Fourthwall. الدقائق تُضاف لحسابك فوراً بعد الدفع
        (أو بعد webhook).
      </p>
    </div>

    <div v-if="msg" :class="ok ? 'success' : 'error'">{{ msg }}</div>

    <div class="pkg-grid">
      <div v-for="p in packages" :key="p.id" class="pkg-card" :class="{ featured: p.badge }">
        <div v-if="p.badge" class="pkg-badge">{{ p.badge }}</div>
        <h3>{{ p.name }}</h3>
        <div class="pkg-mins">{{ p.minutes.toLocaleString() }} <small>min</small></div>
        <div class="pkg-price">${{ Number(p.priceUsd).toFixed(2) }}</div>
        <p class="pkg-desc">{{ p.description }}</p>
        <p v-if="p.perThousandUsd != null" class="pkg-unit">≈ ${{ p.perThousandUsd }} / 1,000 min</p>
        <button class="btn btn-primary btn-block" type="button" :disabled="busyId === p.id" @click="buy(p)">
          {{ busyId === p.id ? '...' : 'Pay by card' }}
        </button>
      </div>
    </div>

    <div v-if="orders?.length" class="card" style="margin-top:24px">
      <h3>History</h3>
      <table class="table">
        <thead>
          <tr><th>Package</th><th>Minutes</th><th>Price</th><th>Status</th><th>Date</th></tr>
        </thead>
        <tbody>
          <tr v-for="o in orders" :key="o.id">
            <td>{{ o.packageName || '—' }}</td>
            <td>{{ (o.minutes || 0).toLocaleString() }}</td>
            <td>${{ Number(o.amountUsd).toFixed(2) }}</td>
            <td>{{ o.status }}</td>
            <td>{{ (o.paidAt || o.createdAt || '').slice(0, 10) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { api } from '../api'

const dash = inject('dash')
const refreshDash = inject('refreshDash')
const packages = computed(() => dash?.value?.packages || [])
const orders = computed(() => dash?.value?.orders || [])
const busyId = ref('')
const msg = ref('')
const ok = ref(false)

onMounted(async () => {
  if (!dash?.value) await refreshDash?.()
  const q = new URLSearchParams(location.search)
  if (q.get('billing') === 'success') {
    ok.value = true
    msg.value = 'رجعت من الدفع — الرصيد يتحدّث بعد webhook إن لزم.'
    await refreshDash()
  }
})

async function buy(p) {
  busyId.value = p.id
  msg.value = ''
  ok.value = false
  try {
    const r = await api.buyPackage(p.id)
    if (r.checkoutUrl) {
      location.href = r.checkoutUrl
      return
    }
    ok.value = true
    msg.value = r.message || 'Package purchased'
    await refreshDash()
  } catch (e) {
    msg.value = e.message || 'Purchase failed'
  } finally {
    busyId.value = ''
  }
}
</script>
