<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { api, type ChildItem, type Level, type NodeDetail, type NodeRef } from '../api'
import { LEVEL_NAME } from '../labels'

/**
 * 左上逐级选择（原型的 pickers）：酋长国 ▸ 市政 ▸ 区域 ▸ 社区 ▸ 地块 ▸ 建筑 ▸ 楼层 ▸ 单元。
 * 点名称跳到该级；点 ▾ 列出同级（上一级的全部下级）可搜索切换；最后一个 ▾ 列出当前节点的下一级。
 */
const props = defineProps<{ node: NodeDetail | null }>()
const emit = defineEmits<{ go: [level: Level, key: string] }>()

interface Crumb { ref: NodeRef; parent: NodeRef | null }
const crumbs = computed<Crumb[]>(() => {
  const n = props.node
  if (!n) return []
  const chain: NodeRef[] = [...n.path, { level: n.level, key: n.key, name: n.name }]
  return chain.map((r, i) => ({ ref: r, parent: i ? chain[i - 1] : null }))
})

const open = ref<{ i: number; x: number; y: number; parent: NodeRef; childLevel: Level | null } | null>(null)
const items = ref<ChildItem[]>([])
const total = ref(0)
const q = ref('')
const input = ref<HTMLInputElement>()
let timer = 0

async function fetchList() {
  const o = open.value
  if (!o) return
  try {
    const r = await api.children(o.parent.level, o.parent.key, 0, 200, q.value.trim() || undefined)
    items.value = r.items
    total.value = r.total
  } catch {
    items.value = []
  }
}

/** i = 第几个面包屑；next = 列当前节点的下一级 */
function toggle(e: MouseEvent, i: number, next = false) {
  if (open.value && open.value.i === (next ? -1 : i)) {
    open.value = null
    return
  }
  const c = crumbs.value[i]
  const parent = next ? c.ref : c.parent
  if (!parent) return
  const r = (e.currentTarget as HTMLElement).getBoundingClientRect()
  open.value = { i: next ? -1 : i, x: r.left, y: r.bottom + 6, parent, childLevel: next ? props.node?.childLevel ?? null : c.ref.level }
  q.value = ''
  items.value = []
  fetchList()
  nextTick(() => input.value?.focus())
}

function onQuery() {
  clearTimeout(timer)
  timer = window.setTimeout(fetchList, 250)
}

function choose(it: ChildItem) {
  const lvl = open.value?.childLevel
  open.value = null
  if (lvl) emit('go', lvl, it.key)
}

function outside(e: MouseEvent) {
  if (!(e.target as HTMLElement).closest('.pop, .pick')) open.value = null
}
onMounted(() => document.addEventListener('mousedown', outside))
onBeforeUnmount(() => document.removeEventListener('mousedown', outside))

const curKey = computed(() => open.value ? crumbs.value[open.value.i]?.ref.key : null)
</script>

<template>
  <nav class="pickers" aria-label="层级">
    <template v-for="(c, i) in crumbs" :key="c.ref.level">
      <i v-if="i" class="sep"><svg class="i" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" /></svg></i>
      <button class="pick" :class="{ cur: i === crumbs.length - 1, open: open?.i === i }" :title="LEVEL_NAME[c.ref.level]"
              @click="c.parent && i < crumbs.length - 1 ? emit('go', c.ref.level, c.ref.key) : c.parent ? toggle($event, i) : emit('go', c.ref.level, c.ref.key)">
        <small>{{ LEVEL_NAME[c.ref.level] }}</small><span>{{ c.ref.name }}</span>
        <svg v-if="c.parent" class="i" viewBox="0 0 24 24" @click.stop="toggle($event, i)"><path d="m6 9 6 6 6-6" /></svg>
      </button>
    </template>
    <template v-if="node?.childLevel && node.childCount">
      <i class="sep"><svg class="i" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" /></svg></i>
      <button class="pick" :class="{ open: open?.i === -1 }" @click="toggle($event, crumbs.length - 1, true)">
        <small>{{ LEVEL_NAME[node.childLevel] }}</small><span>选择（{{ node.childCount.toLocaleString() }}）</span>
        <svg class="i" viewBox="0 0 24 24"><path d="m6 9 6 6 6-6" /></svg>
      </button>
    </template>
  </nav>
  <div v-if="open" class="pop" :style="{ left: open.x + 'px', top: open.y + 'px' }">
    <input ref="input" v-model="q" :placeholder="'搜索' + (open.childLevel ? LEVEL_NAME[open.childLevel] : '')" @input="onQuery" @keydown.esc="open = null" />
    <ul>
      <li v-for="it in items" :key="it.key">
        <button :class="{ on: it.key === curKey }" @click="choose(it)">
          <span>{{ it.name }}</span><small>{{ it.sub ?? '' }}</small>
        </button>
      </li>
    </ul>
    <div v-if="total > items.length" class="more">共 {{ total.toLocaleString() }} 个，只列前 {{ items.length }} 个，请输入关键字</div>
    <div v-else-if="!items.length" class="more">没有数据</div>
  </div>
</template>
