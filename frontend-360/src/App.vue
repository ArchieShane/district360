<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { api, LEVELS, type Level, type Meta, type NodeDetail } from './api'
import MapView from './components/MapView.vue'
import Pickers from './components/Pickers.vue'
import Panel from './components/Panel.vue'
import SearchBox from './components/SearchBox.vue'

/**
 * 页面状态只有「当前节点」（层级 + 编码），记在地址栏 #/<level>/<key>，可分享、可前进后退。
 * 节点视图（名称、上级链、计数、分组、属性）一次从 /api/v2/space/nodes/{level}/{key} 取齐。
 */
const node = shallowRef<NodeDetail | null>(null)
const meta = shallowRef<Meta | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
/** 当前页签：主题页签打开时地图叠加对应图层（开发建设：总规与工地；城市运行：公交站与路灯） */
const tab = ref('ov')
/** 面板里点了某个总规 / 工地：地图飞过去（t 让同一点也能重复触发） */
const focus = ref<{ lon: number; lat: number; t: number } | null>(null)
let ctrl: AbortController | null = null

function parseHash(): [Level, string] {
  const m = location.hash.match(/^#\/([a-z]+)\/(.+)$/)
  if (m && (LEVELS as string[]).includes(m[1])) return [m[1] as Level, decodeURIComponent(m[2])]
  return ['emirate', 'AD']
}

function go(level: Level, key: string) {
  const h = `#/${level}/${encodeURIComponent(key)}`
  if (location.hash !== h) location.hash = h
  else load()
}

async function load() {
  const [level, key] = parseHash()
  ctrl?.abort()
  ctrl = new AbortController()
  loading.value = true
  error.value = null
  try {
    node.value = await api.node(level, key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function up() {
  const p = node.value?.path
  if (p && p.length) go(p[p.length - 1].level, p[p.length - 1].key)
}

onMounted(() => {
  window.addEventListener('hashchange', load)
  load()
  api.meta().then(m => (meta.value = m)).catch(() => undefined)
})
onBeforeUnmount(() => window.removeEventListener('hashchange', load))
</script>

<template>
  <MapView :node="node" :theme-tab="tab" :focus="focus" @pick="go" />
  <Pickers :node="node" @go="go" />
  <SearchBox @go="go" />
  <Panel :node="node" :meta="meta" :error="error" @go="go" @up="up" @tab="t => (tab = t)"
         @focus="(lon, lat) => (focus = { lon, lat, t: Date.now() })" />
  <div v-if="loading" class="loading">加载中…</div>
</template>
