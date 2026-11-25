<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, type ChildItem, type Level, type NodeDetail } from '../api'
import { area, num } from '../format'
import { LEVEL_NAME, PLOT_STATUS } from '../labels'

/** 下一级列表：分页、关键字过滤，点击下钻。列随下一级的层级变化。 */
const props = defineProps<{ node: NodeDetail; limit?: number; compact?: boolean }>()
const emit = defineEmits<{ go: [level: Level, key: string] }>()

const items = ref<ChildItem[]>([])
const total = ref(0)
const page = ref(0)
const q = ref('')
const loading = ref(false)
const size = computed(() => props.limit ?? 50)
let timer = 0

async function load(reset: boolean) {
  const n = props.node
  if (!n.childLevel) return
  loading.value = true
  try {
    const p = reset ? 0 : page.value + 1
    const r = await api.children(n.level, n.key, p, size.value, q.value.trim() || undefined)
    items.value = reset ? r.items : [...items.value, ...r.items]
    total.value = r.total
    page.value = p
  } catch {
    if (reset) items.value = []
  } finally {
    loading.value = false
  }
}

watch(() => [props.node.level, props.node.key], () => { q.value = ''; load(true) }, { immediate: true })
watch(q, () => { clearTimeout(timer); timer = window.setTimeout(() => load(true), 250) })

interface Col { h: string; v: (it: ChildItem) => string }
const cols = computed<Col[]>(() => {
  switch (props.node.childLevel) {
    case 'municipality':
    case 'district':
    case 'community':
      return [
        { h: '地块', v: it => num(it.plots) },
        { h: '建筑', v: it => num(it.buildings) },
        { h: '单元', v: it => num(it.units) },
      ]
    case 'plot':
      return [
        { h: '面积', v: it => area(Number(it.areaSqm)) },
        { h: '状态', v: it => PLOT_STATUS[String(it.status)]?.n ?? String(it.status ?? '—') },
        { h: '建筑', v: it => num(it.buildings) },
      ]
    case 'building':
      return [
        { h: '高度 m', v: it => num(it.heightM, 1) },
        { h: '楼层', v: it => num(it.floors ?? it.floorsCount) },
        { h: '单元', v: it => num(it.units) },
      ]
    case 'floor':
      return [
        { h: '底标高', v: it => num(it.baseHeightM, 1) },
        { h: '面积 m²', v: it => num(it.areaSqm) },
        { h: '单元', v: it => num(it.units) },
      ]
    case 'unit':
      return [
        { h: '面积 m²', v: it => num(it.areaSqm, 1) },
        { h: '卧室', v: it => num(it.bedrooms) },
        { h: '卫生间', v: it => num(it.bathrooms) },
      ]
    default:
      return []
  }
})
const grid = computed(() => `minmax(0, 1fr) ${cols.value.map(() => '4.2rem').join(' ')}`)
</script>

<template>
  <section v-if="node.childLevel" class="card">
    <h2>{{ LEVEL_NAME[node.childLevel] }}<span class="chip">{{ num(total) }}</span></h2>
    <input v-if="!compact" v-model="q" class="find" :placeholder="'按名称 / 编号筛选' + LEVEL_NAME[node.childLevel]" />
    <div class="rows" :style="{ '--cols': grid }">
      <div class="row hd"><span>{{ LEVEL_NAME[node.childLevel] }}</span><span v-for="c in cols" :key="c.h">{{ c.h }}</span></div>
      <button v-for="it in items" :key="it.key" class="row" @click="emit('go', node.childLevel!, it.key)">
        <span><b>{{ it.name }}</b><small v-if="it.sub && it.sub !== it.name">{{ it.sub }}</small></span>
        <span v-for="c in cols" :key="c.h">{{ c.v(it) }}</span>
      </button>
    </div>
    <p v-if="!loading && !items.length" style="margin-top:.6rem">没有{{ LEVEL_NAME[node.childLevel] }}数据</p>
    <button v-if="!compact && items.length < total" class="more" :disabled="loading" @click="load(false)">
      {{ loading ? '加载中…' : `加载更多（${items.length} / ${num(total)}）` }}
    </button>
  </section>
</template>
