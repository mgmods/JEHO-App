<template>
  <div>
    <div class="page-head" style="display:flex;justify-content:space-between;align-items:flex-end;gap:16px;flex-wrap:wrap">
      <div>
        <h2>Live apps</h2>
        <p>كل تطبيق = AppID + مفاتيح + room prefix للبث المباشر</p>
      </div>
      <button class="btn btn-primary" type="button" @click="showCreate = true">+ New app</button>
    </div>

    <div class="wss-banner" style="margin-bottom:20px">
      <div>
        <h3>Connect every app with</h3>
        <p>LiveKit signaling (priority #1)</p>
      </div>
      <div class="wss-value">{{ livekitUrl }}</div>
    </div>

    <div class="apps-grid">
      <button class="app-card create" type="button" @click="showCreate = true">
        <div class="plus">+</div>
        Create live app
      </button>
      <button
        v-for="p in projects"
        :key="p.id"
        class="app-card"
        type="button"
        @click="$router.push({ name: 'app', params: { id: p.id } })"
      >
        <div class="app-card-top"><span class="orb" /></div>
        <div class="app-card-body">
          <div class="title">{{ p.name }}</div>
          <div class="meta">AppID {{ p.appId }}</div>
          <div class="meta">{{ p.roomPrefix }}_*</div>
          <div class="chips">
            <span class="chip live">{{ livekitUrl.startsWith('wss') ? 'wss://' : 'ws' }}</span>
            <span class="chip ok">{{ p.status }}</span>
          </div>
        </div>
      </button>
    </div>

    <div v-if="showCreate" class="modal-back" @click.self="showCreate = false">
      <div class="modal">
        <h3 style="margin:0 0 12px;font-family:var(--display)">New live app</h3>
        <div v-if="err" class="error">{{ err }}</div>
        <div class="field">
          <label>Name</label>
          <input v-model="name" placeholder="Broadcast room app" />
        </div>
        <div class="field">
          <label>Description</label>
          <input v-model="desc" />
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end">
          <button class="btn btn-ghost" type="button" @click="showCreate = false">Cancel</button>
          <button class="btn btn-primary" type="button" :disabled="busy" @click="create">Create</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const dash = inject('dash')
const refreshDash = inject('refreshDash')
const projects = computed(() => dash?.value?.projects || [])
const livekitUrl = computed(() => dash?.value?.resources?.livekitUrl || 'wss://voice.adastra.bbs.tr')

const showCreate = ref(false)
const name = ref('')
const desc = ref('')
const busy = ref(false)
const err = ref('')

async function create() {
  busy.value = true
  err.value = ''
  try {
    const res = await api.createProject({ name: name.value, description: desc.value })
    await refreshDash()
    showCreate.value = false
    router.push({ name: 'app', params: { id: res.project.id } })
  } catch (e) {
    err.value = e.message || 'Failed'
  } finally {
    busy.value = false
  }
}
</script>
