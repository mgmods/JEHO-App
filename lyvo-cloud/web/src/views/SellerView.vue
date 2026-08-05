<template>
  <div>
    <h2 class="section-title">Seller — باقاتك للبيع</h2>
    <p style="color:var(--muted);margin:-6px 0 18px;font-size:14px">
      أنت تبيع الدقائق. حدّد عدد الدقائق والسعر لكل باقة. المشترون يدفعون ويُضاف لرصيدهم.
    </p>

    <div v-if="sales" class="grid-mid" style="margin-bottom:20px">
      <div class="card">
        <h3>Revenue</h3>
        <div class="balance-num">${{ Number(sales.summary?.revenueUsd || 0).toFixed(2) }}</div>
      </div>
      <div class="card">
        <h3>Minutes sold</h3>
        <div class="balance-num" style="font-size:28px">{{ (sales.summary?.minutesSold || 0).toLocaleString() }}</div>
      </div>
      <div class="card">
        <h3>Paid orders</h3>
        <div class="balance-num" style="font-size:28px">{{ sales.summary?.ordersPaid || 0 }}</div>
      </div>
    </div>

    <div class="card" style="margin-bottom:18px">
      <h3>Fourthwall — الدفع بالبطاقة</h3>
      <p style="color:var(--muted);font-size:13px;margin:0 0 12px">
        نفس خدمة <strong>fourthwall.com</strong> المستخدمة في تطبيق JEHO.
        من لوحة Fourthwall: Open API user/password + Storefront token + Shop domain.
      </p>
      <div v-if="payMsg" class="error" :style="payOk ? { background: '#ecfdf5', color: '#047857' } : {}">
        {{ payMsg }}
      </div>
      <div class="field">
        <label>Webhook (انسخه في Fourthwall → Webhooks)</label>
        <input :value="payments?.webhookUrl || ''" readonly dir="ltr" class="mono" @focus="$event.target.select()" />
      </div>
      <div class="form-grid">
        <div class="field">
          <label>API User (email)</label>
          <input v-model="pay.apiUser" dir="ltr" placeholder="fw_api_…@fourthwall.com" />
        </div>
        <div class="field">
          <label>Shop domain</label>
          <input v-model="pay.shopDomain" dir="ltr" placeholder="your-shop.fourthwall.com" />
        </div>
        <div class="field">
          <label>API Password</label>
          <input v-model="pay.apiPassword" type="password" dir="ltr" :placeholder="payments?.fourthwall?.apiPasswordConfigured ? '•••• leave blank to keep' : ''" />
        </div>
        <div class="field">
          <label>Storefront Token (ptkn_…)</label>
          <input v-model="pay.storefrontToken" type="password" dir="ltr" :placeholder="payments?.fourthwall?.storefrontTokenConfigured ? '•••• leave blank to keep' : ''" />
        </div>
        <div class="field">
          <label>Webhook secret</label>
          <input v-model="pay.webhookSecret" type="password" dir="ltr" :placeholder="payments?.fourthwall?.webhookSecretConfigured ? '•••• leave blank to keep' : ''" />
        </div>
      </div>
      <p style="font-size:13px;margin:0 0 12px">
        الحالة:
        <strong :style="{ color: payments?.configured ? '#047857' : '#b91c1c' }">
          {{ payments?.configured ? 'جاهز للدفع بالبطاقة' : 'غير مكتمل' }}
        </strong>
      </p>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="btn btn-primary" :disabled="payBusy" @click="savePayments">Save Fourthwall</button>
        <button class="btn btn-ghost" :disabled="payBusy" @click="testPayments">Test connection</button>
      </div>
    </div>

    <div class="card" style="margin-bottom:18px">
      <h3>{{ editId ? 'Edit package' : 'New package' }}</h3>
      <div v-if="err" class="error">{{ err }}</div>
      <div class="form-grid">
        <div class="field">
          <label>Name</label>
          <input v-model="form.name" placeholder="e.g. Pro 50k" />
        </div>
        <div class="field">
          <label>Minutes (كم دقيقة)</label>
          <input v-model.number="form.minutes" type="number" min="100" step="100" />
        </div>
        <div class="field">
          <label>Price USD</label>
          <input v-model.number="form.priceUsd" type="number" min="0.5" step="0.5" />
        </div>
        <div class="field">
          <label>Badge</label>
          <input v-model="form.badge" placeholder="Best value" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Description</label>
          <input v-model="form.description" placeholder="What buyer gets" />
        </div>
      </div>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="btn btn-primary" :disabled="busy" @click="save">
          {{ busy ? '...' : editId ? 'Save' : 'Create package' }}
        </button>
        <button v-if="editId" class="btn btn-ghost" @click="resetForm">Cancel</button>
      </div>
    </div>

    <div class="card">
      <h3>Catalog</h3>
      <table class="table">
        <thead>
          <tr>
            <th>Name</th><th>Minutes</th><th>Price</th><th>Active</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in list" :key="p.id">
            <td>
              <strong>{{ p.name }}</strong>
              <div style="font-size:12px;color:var(--muted)">{{ p.badge || p.description }}</div>
            </td>
            <td>{{ p.minutes.toLocaleString() }}</td>
            <td>${{ Number(p.priceUsd).toFixed(2) }}</td>
            <td>{{ p.active ? 'yes' : 'no' }}</td>
            <td style="white-space:nowrap">
              <button class="btn btn-ghost" style="padding:6px 10px" @click="startEdit(p)">Edit</button>
              <button class="btn btn-ghost" style="padding:6px 10px" @click="toggle(p)">
                {{ p.active ? 'Hide' : 'Show' }}
              </button>
              <button class="btn btn-ghost" style="padding:6px 10px;color:var(--red)" @click="remove(p)">Del</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { inject, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const dash = inject('dash')
const refreshDash = inject('refreshDash')
const list = ref([])
const sales = ref(null)
const busy = ref(false)
const err = ref('')
const editId = ref('')
const form = reactive({
  name: '',
  minutes: 10000,
  priceUsd: 19,
  badge: '',
  description: '',
})

function resetForm() {
  editId.value = ''
  form.name = ''
  form.minutes = 10000
  form.priceUsd = 19
  form.badge = ''
  form.description = ''
}

function startEdit(p) {
  editId.value = p.id
  form.name = p.name
  form.minutes = p.minutes
  form.priceUsd = p.priceUsd
  form.badge = p.badge || ''
  form.description = p.description || ''
}

const pay = reactive({
  apiUser: '',
  shopDomain: '',
  apiPassword: '',
  storefrontToken: '',
  webhookSecret: '',
})
const payments = ref(null)
const payBusy = ref(false)
const payMsg = ref('')
const payOk = ref(false)

async function load() {
  if (!dash?.value?.developer?.isSeller) {
    router.replace({ name: 'home' })
    return
  }
  const [pkgs, s, payCfg] = await Promise.all([
    api.sellerPackages(),
    api.sellerSales(),
    api.sellerPayments(),
  ])
  list.value = pkgs.packages || []
  sales.value = s
  payments.value = payCfg
  pay.apiUser = payCfg?.fourthwall?.apiUser || ''
  pay.shopDomain = payCfg?.fourthwall?.shopDomain || ''
}

async function savePayments() {
  payBusy.value = true
  payMsg.value = ''
  payOk.value = false
  try {
    const body = {
      apiUser: pay.apiUser,
      shopDomain: pay.shopDomain,
    }
    if (pay.apiPassword) body.apiPassword = pay.apiPassword
    if (pay.storefrontToken) body.storefrontToken = pay.storefrontToken
    if (pay.webhookSecret) body.webhookSecret = pay.webhookSecret
    await api.sellerSavePayments(body)
    pay.apiPassword = ''
    pay.storefrontToken = ''
    pay.webhookSecret = ''
    await load()
    payOk.value = true
    payMsg.value = 'Fourthwall saved'
  } catch (e) {
    payMsg.value = e.message || 'Save failed'
  } finally {
    payBusy.value = false
  }
}

async function testPayments() {
  payBusy.value = true
  payMsg.value = ''
  payOk.value = false
  try {
    const r = await api.sellerTestPayments()
    payOk.value = !!r.ok
    payMsg.value = r.ok ? `Connected: ${r.shop || 'ok'}` : (r.error || 'failed')
  } catch (e) {
    payMsg.value = e.message || 'Test failed'
  } finally {
    payBusy.value = false
  }
}

onMounted(load)

async function save() {
  busy.value = true
  err.value = ''
  try {
    const body = {
      name: form.name,
      minutes: form.minutes,
      priceUsd: form.priceUsd,
      badge: form.badge,
      description: form.description,
    }
    if (editId.value) await api.sellerUpdatePackage(editId.value, body)
    else await api.sellerCreatePackage(body)
    resetForm()
    await load()
    await refreshDash?.()
  } catch (e) {
    err.value = e.message || 'Failed'
  } finally {
    busy.value = false
  }
}

async function toggle(p) {
  await api.sellerUpdatePackage(p.id, { active: !p.active })
  await load()
  await refreshDash?.()
}

async function remove(p) {
  if (!confirm(`Delete ${p.name}?`)) return
  await api.sellerDeletePackage(p.id)
  await load()
  await refreshDash?.()
}
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 14px;
  margin-bottom: 12px;
}
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin-top: 10px;
}
.table th,
.table td {
  text-align: left;
  padding: 10px 6px;
  border-bottom: 1px solid var(--line);
  vertical-align: top;
}
.table th {
  color: var(--muted);
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
@media (max-width: 700px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
