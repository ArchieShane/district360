<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { api, type NearbyView, type NodeDetail } from '../api'
import { num } from '../format'
import { POI_BY, POI_TYPES } from '../labels'

/**
 * 地块周边设施（原型选中地块后的 500 / 1000 m 范围圈）：各类设施与公交站在 500 m、1 km 内的个数和最近距离，
 * 以及最近的几处（点击在地图上定位）。建筑 / 楼层 / 单元按所在地块。地图上的范围圈在设施页签打开时显示。
 */
const props = defineProps<{ node: NodeDetail }>()
const emit = defineEmits<{ focus: [lon: number, lat: number] }>()

const v = shallowRef<NearbyView | null>(null)
const error = shallowRef<string | null>(null)
let ctrl: AbortController | null = null
watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  v.value = null
  error.value = null
  try {
    v.value = await api.nearby(props.node.level, props.node.key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const rows = computed(() => POI_TYPES.map(t => ({ ...t, c2: v.value?.counts?.[t.k] })))
const nearest = computed(() => (v.value?.items ?? []).slice(0, 8))
const dist = (m: number | null | undefined) => m == null ? '—' : m >= 1000 ? num(m / 1000, 1) + ' km' : num(m) + ' m'
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <section v-else-if="v && v.scope" class="card">
    <h2>周边设施<span class="chip">500 m · 1 km</span><span class="src">ast_facility · ops_bus_stop</span></h2>
    <p v-if="node.level !== 'plot'" style="margin:-.2rem 0 .5rem">按所在地块计算</p>
    <div class="rows" style="--cols: minmax(0,1fr) 3.4rem 3.4rem 4.2rem">
      <div class="row hd"><span>类别</span><span>500 m</span><span>1 km</span><span>最近</span></div>
      <div v-for="r in rows" :key="r.k" class="row">
        <span><i class="sw rd" :style="{ background: r.c }" />{{ r.n }}</span>
        <span>{{ num(r.c2?.r500 ?? 0) }}</span>
        <span>{{ num(r.c2?.r1000 ?? 0) }}</span>
        <span>{{ r.c2 ? dist(r.c2.nearest) : '1 km 外' }}</span>
      </div>
    </div>
    <template v-if="nearest.length">
      <div class="sub">最近的设施</div>
      <div class="rows" style="--cols: minmax(0,1fr) 4.2rem">
        <button v-for="it in nearest" :key="it.category + it.id" class="row" @click="emit('focus', it.cx, it.cy)">
          <span><b :title="it.name"><i class="sw rd" :style="{ background: POI_BY[it.category]?.c }" />{{ it.name ?? it.id }}</b><small>{{ POI_BY[it.category]?.n }}{{ it.subtype ? ' · ' + it.subtype : '' }}</small></span>
          <span>{{ dist(it.dist) }}</span>
        </button>
      </div>
    </template>
    <p style="margin-top:.5rem">从地块面内一点量直线距离；同一公交站的多个站台算一个站。地块上的设施不计入（见下方列表）。打开设施页签时地图上画出范围圈。</p>
  </section>
</template>

<style scoped>
.sw.rd { border-radius: 50%; margin-right: .35rem; display: inline-block; width: .55rem; height: .55rem; vertical-align: .02rem }
</style>
