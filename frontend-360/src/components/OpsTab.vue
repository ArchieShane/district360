<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { api, type Meta, type NodeDetail, type OpsView } from '../api'
import { num } from '../format'
import { LAMP_TYPE, LEVEL_NAME, LIGHT_STATUS } from '../labels'

/**
 * 城市运行页签（ods-ops）：路灯（点数、灯盏、状态、灯型）与公交站（站、站台、列表）；
 * 居民诉求、道路事故、视觉违规、违规处罚、住宅占用的源数据尚未入 ODS，显示原因。地块及以下按所在社区统计。
 */
const props = defineProps<{ node: NodeDetail; meta: Meta | null }>()
const emit = defineEmits<{ focus: [lon: number, lat: number] }>()

const ops = shallowRef<OpsView | null>(null)
const error = shallowRef<string | null>(null)
let ctrl: AbortController | null = null

watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  ops.value = null
  error.value = null
  try {
    ops.value = await api.ops(props.node.level, props.node.key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const s = computed(() => ops.value?.summary)
const status = computed(() => (ops.value?.lightsByStatus ?? []).map(x => ({ ...x, n2: LIGHT_STATUS[x.k]?.n ?? x.k, c: LIGHT_STATUS[x.k]?.c ?? '#6B7280' })))
const lamps = computed(() => ops.value?.lightsByLampType ?? [])
const inServiceRate = computed(() => s.value && s.value.lights ? (s.value.lightsInService / s.value.lights * 100).toFixed(1) : null)
const scopeNote = computed(() => {
  const sc = ops.value?.scope
  return sc && sc.level !== props.node.level ? `按所在${LEVEL_NAME[sc.level]}统计` : ''
})
const pending = computed(() => (props.meta?.tables ?? []).filter(x => x.theme === '城市运行' && x.note))
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <section v-else-if="!ops" class="card muted"><p>加载中…</p></section>
  <section v-else-if="!ops.scope" class="card muted"><p>没有对应的社区，没有城市运行数据。</p></section>
  <template v-else-if="s">
    <section class="card">
      <h2>路灯<span class="src">theme.ops_streetlight</span></h2>
      <p v-if="scopeNote" style="margin:-.2rem 0 .5rem">{{ scopeNote }}</p>
      <div class="nums">
        <div><b>{{ num(s.lights) }}</b><span>路灯</span></div>
        <div><b>{{ num(s.lamps) }}</b><span>灯盏</span></div>
        <div><b>{{ inServiceRate ?? '—' }}<small v-if="inServiceRate">%</small></b><span>在用率</span></div>
      </div>
      <template v-if="status.length">
        <div class="stack"><i v-for="x in status" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
        <div class="keys"><span v-for="x in status" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span></div>
      </template>
      <template v-if="lamps.length">
        <div class="sub">灯型</div>
        <div class="hb">
          <div v-for="x in lamps" :key="x.k">
            <span>{{ LAMP_TYPE[x.k] ?? x.k }}</span>
            <i><u :style="{ width: (x.n / lamps[0].n * 100) + '%', background: '#F2D45C' }" /></i>
            <b>{{ num(x.n) }}</b>
          </div>
        </div>
      </template>
    </section>

    <section class="card">
      <h2>公交站<span class="src">theme.ops_bus_stop</span></h2>
      <div class="nums">
        <div><b>{{ num(s.busStops) }}</b><span>公交站</span></div>
        <div><b>{{ num(s.busPlatforms) }}</b><span>站台</span></div>
        <div />
      </div>
      <div v-if="ops.busStops?.length" class="rows" style="--cols: minmax(0,1fr) 3.4rem">
        <div class="row hd"><span>站名</span><span>站台</span></div>
        <button v-for="b in ops.busStops" :key="b.stopId" class="row" @click="b.cx != null && emit('focus', b.cx, b.cy!)">
          <span><b :title="b.name">{{ b.name ?? b.stopId }}</b><small>{{ b.codes }}</small></span>
          <span>{{ b.platforms }}</span>
        </button>
      </div>
      <p v-if="(ops.busStops?.length ?? 0) >= 100" style="margin-top:.5rem">按站名列前 100 个。</p>
    </section>

    <section v-if="pending.length" class="card muted">
      <h2>待接入<span class="badge no">ODS 暂无数据</span></h2>
      <div class="rows" style="--cols: minmax(0,1fr)">
        <div v-for="x in pending" :key="x.table" class="row"><span><b>{{ x.title }} · theme.{{ x.table }}</b><small>{{ x.note }}</small></span></div>
      </div>
    </section>
  </template>
</template>
