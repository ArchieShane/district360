<script setup lang="ts">
import { computed } from 'vue'
import { num } from '../format'

/**
 * 近 12 个月柱状图：每月一组柱（每个系列一根），月份标签只标首、中、尾，悬停显示数值。
 * rows 的每行有 month（YYYY-MM）和各系列的数值。
 */
const props = defineProps<{
  rows: Record<string, string | number>[]
  series: { key: string; name: string; c: string }[]
}>()

const max = computed(() => Math.max(1, ...props.rows.flatMap(r => props.series.map(s => Number(r[s.key] ?? 0)))))
const label = (m: string, i: number) => {
  const last = props.rows.length - 1
  return i === 0 || i === last || i === Math.round(last / 2) ? m.slice(2).replace('-', '/') : ''
}
const tip = (r: Record<string, string | number>) => r.month + '：' + props.series.map(s => `${s.name} ${num(Number(r[s.key] ?? 0))}`).join('，')
</script>

<template>
  <div class="mb">
    <div class="plot">
      <div v-for="(r, i) in rows" :key="String(r.month)" class="m" :title="tip(r)">
        <div class="bars"><i v-for="s in series" :key="s.key" :style="{ height: (Number(r[s.key] ?? 0) / max * 100) + '%', background: s.c }" /></div>
        <small>{{ label(String(r.month), i) }}</small>
      </div>
    </div>
    <div v-if="series.length > 1" class="lg"><span v-for="s in series" :key="s.key"><i class="sw" :style="{ background: s.c }" />{{ s.name }}</span></div>
  </div>
</template>

<style scoped>
.mb { margin-top: .4rem }
.plot { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: .25rem; height: 5.2rem; align-items: end }
.m { display: flex; flex-direction: column; height: 100%; min-width: 0 }
.bars { flex: 1; display: flex; align-items: flex-end; gap: 1px; border-bottom: 1px solid var(--line) }
.bars i { flex: 1; min-height: 1px; border-radius: 2px 2px 0 0 }
.m small { height: 1rem; margin-top: .2rem; font-size: .6rem; color: var(--tx3); text-align: center; white-space: nowrap; overflow: visible }
.lg { display: flex; gap: .9rem; margin-top: .35rem; font-size: .72rem; color: var(--tx2) }
.lg span { display: inline-flex; align-items: center; gap: .35rem }
</style>
