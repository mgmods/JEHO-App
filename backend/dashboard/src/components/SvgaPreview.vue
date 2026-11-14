<template>
  <canvas ref="canvasEl" class="svga-canvas" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Parser, Player } from 'svga'
import { resolveAsset } from '@/utils/assets'

const props = defineProps({
  src: { type: String, default: '' },
})

const canvasEl = ref(null)
let player = null
let loadToken = 0

async function play(url) {
  const token = ++loadToken
  stop()
  const abs = resolveAsset(url)
  if (!abs || !canvasEl.value) return
  try {
    const parser = new Parser()
    const videoItem = await parser.load(abs)
    if (token !== loadToken || !canvasEl.value) return
    player = new Player({
      container: canvasEl.value,
      loop: 0,
    })
    await player.mount(videoItem)
    if (token !== loadToken) return
    player.start()
  } catch (err) {
    console.warn('SVGA preview failed', abs, err)
  }
}

function stop() {
  try {
    player?.stop?.()
    player?.destroy?.()
  } catch {
    /* ignore */
  }
  player = null
}

watch(
  () => props.src,
  (next) => {
    if (next) play(next)
    else stop()
  },
)

onMounted(() => {
  if (props.src) play(props.src)
})

onBeforeUnmount(() => {
  loadToken += 1
  stop()
})
</script>

<style scoped>
.svga-canvas {
  display: block;
  width: 100%;
  height: 100%;
  background: transparent;
}
</style>
