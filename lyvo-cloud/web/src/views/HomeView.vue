<template>
  <div v-if="data">
    <div class="page-head">
      <h2>{{ isAdmin ? 'Platform overview' : 'Developer workspace' }}</h2>
      <p>
        أنشئ تطبيقات بث صوت، اربط الـ SDK عبر <strong>wss://</strong>، واشترِ باقات الدقائق.
        <span v-if="!isAdmin">إعدادات الدفع والمنصة للمالك فقط — لا تظهر لحسابات المطورين.</span>
      </p>
    </div>

    <div class="wss-banner">
      <div>
        <h3>Primary signaling URL (استخدمه أولاً)</h3>
        <p>Live voice requires this secure WebSocket endpoint in your app</p>
      </div>
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <div class="wss-value">{{ livekitUrl }}</div>
        <button class="btn btn-ghost btn-sm" type="button" style="background:rgba(255,255,255,0.08);color:#fff;border-color:rgba(255,255,255,0.12)" @click="copy(livekitUrl)">
          Copy
        </button>
      </div>
    </div>

    <div class="grid-stats">
      <div class="stat-card">
        <div class="label">Minutes left</div>
        <div class="value">{{ (data.totalMinutesRemaining || 0).toLocaleString() }}</div>
        <div class="hint">Free + paid wallet</div>
      </div>
      <div class="stat-card">
        <div class="label">Live apps</div>
        <div class="value">{{ (data.projects || []).length }}</div>
        <div class="hint">Streaming projects</div>
      </div>
      <div class="stat-card">
        <div class="label">Paid minutes</div>
        <div class="value">{{ (data.paidMinutes?.remaining || 0).toLocaleString() }}</div>
        <div class="hint">From packages you bought</div>
      </div>
    </div>

    <div class="two-col">
      <section>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <h2 class="section-title" style="margin:0">Your live apps</h2>
          <button class="btn btn-primary btn-sm" type="button" @click="$router.push({ name: 'apps' })">Manage apps</button>
        </div>
        <div class="apps-grid">
          <button class="app-card create" type="button" @click="showCreate = true">
            <div class="plus">+</div>
            Create live app
          </button>
          <button
            v-for="p in (data.projects || []).slice(0, 5)"
            :key="p.id"
            class="app-card"
            type="button"
            @click="$router.push({ name: 'app', params: { id: p.id } })"
          >
            <div class="app-card-top"><span class="orb" /></div>
            <div class="app-card-body">
              <div class="title">{{ p.name }}</div>
              <div class="meta">AppID {{ p.appId }}</div>
              <div class="chips">
                <span class="chip live">wss ready</span>
                <span class="chip" :class="p.status === 'suspended' ? 'warn' : 'ok'">{{ p.status }}</span>
              </div>
            </div>
          </button>
        </div>
      </section>

      <aside class="card">
        <h3>Quick start</h3>
        <div class="steps" style="margin-top:14px">
          <div class="step">
            <div class="step-num">1</div>
            <div>
              <h4>Create app</h4>
              <p>Get AppID + API Key + Secret</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">2</div>
            <div>
              <h4>Bind wss://</h4>
              <p>Use the Live URL above in client SDK</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">3</div>
            <div>
              <h4>Fetch token</h4>
              <p>POST /api/v1/token with your credentials</p>
            </div>
          </div>
          <div class="step">
            <div class="step-num">4</div>
            <div>
              <h4>Buy minutes</h4>
              <p>Card pay via Fourthwall packages</p>
            </div>
          </div>
        </div>
        <button class="btn btn-primary btn-block" type="button" style="margin-top:8px" @click="$router.push({ name: 'sdk' })">
          Open full SDK guide
        </button>
      </aside>
    </div>

    <div v-if="(data.packages || []).length" style="margin-bottom:8px">
      <h2 class="section-title">Minutes packages</h2>
      <div class="pkg-grid">
        <div
          v-for="p in data.packages.slice(0, 4)"
          :key="p.id"
          class="pkg-card"
          :class="{ featured: !!p.badge }"
        >
          <div v-if="p.badge" class="pkg-badge">{{ p.badge }}</div>
          <h3>{{ p.name }}</h3>
          <div class="pkg-mins">{{ p.minutes.toLocaleString() }} <small>min</small></div>
          <div class="pkg-price">${{ Number(p.priceUsd).toFixed(2) }}</div>
          <p class="pkg-desc">{{ p.description }}</p>
          <button class="btn btn-primary btn-block" type="button" @click="$router.push({ name: 'packages' })">
            View &amp; buy
          </button>
        </div>
      </div>
    </div>

    <div v-if="showCreate" class="modal-back" @click.self="showCreate = false">
      <div class="modal">
        <h3 style="margin:0 0 6px;font-family:var(--display)">Create live app</h3>
        <p style="color:var(--muted);font-size:13px;margin:0 0 14px">اسم مشروع البث — تُنشأ المفاتيح تلقائياً</p>
        <div v-if="createError" class="error">{{ createError }}</div>
        <div class="field">
          <label>App name</label>
          <input v-model="newName" placeholder="e.g. Studio Voice" />
        </div>
        <div class="field">
          <label>Description</label>
          <input v-model="newDesc" placeholder="Optional" />
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <button class="btn btn-ghost" type="button" @click="showCreate = false">Cancel</button>
          <button class="btn btn-primary" type="button" :disabled="creating" @click="create">Create</button>
        </div>
      </div>
    </div>

    <div v-if="created" class="modal-back" @click.self="created = null">
      <div class="modal">
        <h3 style="margin:0 0 8px;font-family:var(--display)">App ready</h3>
        <p style="color:var(--muted);font-size:13px">انسخ App Secret الآن — يظهر مرة واحدة</p>
        <div class="kv"><b>Name</b><span>{{ created.name }}</span></div>
        <div class="kv"><b>AppID</b><span>{{ created.appId }}</span></div>
        <div class="kv"><b>API Key</b><span>{{ created.apiKey }}</span></div>
        <div class="secret-box">App Secret: {{ created.apiSecret }}</div>
        <div class="wss-value" style="margin-bottom:12px;font-size:13px">{{ livekitUrl }}</div>
        <button class="btn btn-primary btn-block" type="button" @click="openSdk(created.id)">Open SDK binding</button>
      </div>
    </div>
  </div>
  <p v-else style="color:var(--muted)">Loading…</p>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const dash = inject('dash')
const refreshDash = inject('refreshDash')
const isAdmin = inject('isAdmin')
const data = computed(() => dash?.value)
const livekitUrl = computed(
  () => data.value?.resources?.livekitUrl || 'wss://voice.adastra.bbs.tr',
)

const showCreate = ref(false)
const newName = ref('')
const newDesc = ref('')
const creating = ref(false)
const createError = ref('')
const created = ref(null)

function copy(t) {
  navigator.clipboard?.writeText(t).catch(() => {})
}

async function create() {
  createError.value = ''
  creating.value = true
  try {
    const res = await api.createProject({ name: newName.value, description: newDesc.value })
    created.value = res.project
    showCreate.value = false
    newName.value = ''
    newDesc.value = ''
    await refreshDash()
  } catch (e) {
    createError.value = e.message || 'Failed'
  } finally {
    creating.value = false
  }
}

function openSdk(id) {
  created.value = null
  router.push({ name: 'app', params: { id } })
}
</script>
