<template>
  <div v-if="project">
    <button class="btn btn-ghost btn-sm" type="button" style="margin-bottom:16px" @click="$router.push({ name: 'apps' })">
      ← Apps
    </button>

    <div class="page-head">
      <h2>{{ project.name }}</h2>
      <p>{{ project.description || 'Live streaming application credentials & binding' }}</p>
    </div>

    <div class="wss-banner">
      <div>
        <h3>① Bind this URL first</h3>
        <p>Client LiveKit connect · must be wss:// in production</p>
      </div>
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <div class="wss-value">{{ livekitUrl }}</div>
        <button class="btn btn-ghost btn-sm" type="button" style="background:rgba(255,255,255,0.08);color:#fff;border:0" @click="copy(livekitUrl)">Copy</button>
      </div>
    </div>

    <div class="two-col">
      <div class="card">
        <h3>Credentials</h3>
        <div class="kv">
          <b>AppID</b>
          <span>{{ project.appId }}</span>
          <button class="btn btn-ghost btn-sm" type="button" @click="copy(project.appId)">Copy</button>
        </div>
        <div class="kv">
          <b>API Key</b>
          <span>{{ project.apiKey }}</span>
          <button class="btn btn-ghost btn-sm" type="button" @click="copy(project.apiKey)">Copy</button>
        </div>
        <div class="kv">
          <b>API Secret</b>
          <span>{{ secret || '•••••••• (reveal)' }}</span>
          <button class="btn btn-ghost btn-sm" type="button" @click="reveal">{{ secret ? 'Hide' : 'Reveal' }}</button>
        </div>
        <div class="kv">
          <b>Room prefix</b>
          <span>{{ project.roomPrefix }}_&lt;your_room&gt;</span>
          <button class="btn btn-ghost btn-sm" type="button" @click="copy(project.roomPrefix)">Copy</button>
        </div>
        <div class="kv" style="border:0">
          <b>Status</b>
          <span>{{ project.status }}</span>
          <span />
        </div>
        <div style="display:flex;gap:8px;margin-top:12px;flex-wrap:wrap">
          <button class="btn btn-primary" type="button" @click="$router.push({ name: 'sdk' })">SDK guide</button>
          <button class="btn btn-ghost" type="button" style="color:var(--red);border-color:#fecaca" @click="remove">Delete app</button>
        </div>
      </div>

      <div class="card">
        <h3>② Issue token then join</h3>
        <p style="margin-bottom:10px">Server-side only — never put API Secret in mobile binary if you can proxy tokens.</p>
        <div class="code-box">{{ tokenSnippet }}</div>
        <button class="btn btn-ghost btn-block" type="button" @click="copy(tokenSnippet)">Copy request</button>
      </div>
    </div>

    <div class="card" style="margin-top:16px">
      <h3>③ Client connect (pseudo)</h3>
      <div class="code-box">{{ clientSnippet }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'

const route = useRoute()
const router = useRouter()
const dash = inject('dash')
const refreshDash = inject('refreshDash')
const project = ref(null)
const secret = ref('')
const livekitUrl = computed(() => dash?.value?.resources?.livekitUrl || 'wss://voice.adastra.bbs.tr')
const apiBase = computed(() => {
  if (typeof location !== 'undefined') return location.origin
  return 'https://cloud.adastra.bbs.tr'
})

const tokenSnippet = computed(() => {
  const p = project.value
  if (!p) return ''
  return `POST ${apiBase.value}/api/v1/token
Content-Type: application/json

{
  "appId": "${p.appId}",
  "apiKey": "${p.apiKey}",
  "apiSecret": "${secret.value || 'YOUR_APP_SECRET'}",
  "room": "lobby",
  "identity": "user-123"
}`
})

const clientSnippet = computed(() => {
  const p = project.value
  return `// 1) Prefer this URL first
const url = "${livekitUrl.value}";   // MUST start with wss://

// 2) token from your backend (or LYVO /api/v1/token)
const { token, room } = await fetchToken();

// 3) LiveKit Android/iOS/Web SDK
room.connect(url, token);
// publish mic for live voice`
})

onMounted(async () => {
  if (!dash?.value) await refreshDash?.()
  project.value = dash.value?.projects?.find((p) => p.id === route.params.id) || null
  if (!project.value) router.replace({ name: 'apps' })
})

function copy(t) {
  navigator.clipboard?.writeText(String(t)).catch(() => {})
}

async function reveal() {
  if (secret.value) {
    secret.value = ''
    return
  }
  const r = await api.revealSecret(route.params.id)
  secret.value = r.apiSecret
}

async function remove() {
  if (!confirm('Delete this app?')) return
  await api.deleteProject(route.params.id)
  await refreshDash()
  router.replace({ name: 'apps' })
}
</script>
