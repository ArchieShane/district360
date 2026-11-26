<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WatchDistrict, WatchItem, WatchView } from '../api'
import { num } from '../format'
import { WATCH_STYLE } from '../labels'

/**
 * 需关注区域（原型 Districts to watch）：酋长国 / 市政层级地图左侧的一列卡片。
 * 每张卡片一个区，列出它越过的每项指标（值、阈值、参照）；点卡片进入该区并打开对应页签，悬停时地图高亮该区。
 * 顶部列出 6 项指标与越过的区数，源数据未到位的指标灰显。默认显示前 maxCards 个，其余点「显示全部」。
 */
const props = defineProps<{ view: WatchView }>()
const emit = defineEmits<{ go: [key: string, tab: string]; hot: [key: string | null] }>()

const all = ref(false)
const open = ref(window.innerWidth >= 1200)
const ind = computed(() => Object.fromEntries(props.view.indicators.map(i => [i.key, i])))
const list = computed<WatchDistrict[]>(() => all.value ? props.view.districts : props.view.districts.slice(0, props.view.maxCards))
const rest = computed(() => props.view.districts.length - props.view.maxCards)

/** 指标值的显示：差距（分）、投资（AED）、供给率（%） */
function value(it: WatchItem): [string, string] {
  if (it.key === 'gap') return [num(it.value, 2), '分']
  if (it.key === 'capex') return [it.value >= 1e9 ? num(it.value / 1e9, 2) + 'B' : num(it.value / 1e6, 0) + 'M', 'AED']
  return [num(it.value, 1), '%']
}
function refs(it: WatchItem): [string, string][] {
  if (it.key === 'gap') return [['在途', num(it.pipeline, 2)], ['目标', num(it.target, 2)]]
  if (it.key === 'capex') return [[it.shareOf ?? '占比', num(it.sharePct, 1) + '%']]
  if (it.key === 'park') return [['供给 / 需求', `${num(it.areaHa, 1)} / ${num(it.needHa, 1)} ha`]]
  return []
}
const tabOf = (w: WatchDistrict) => ind.value[w.items[0]?.key]?.tab ?? 'liv'
</script>

<template>
  <aside class="watch" :class="{ closed: !open }" aria-label="需关注区域">
    <header>
      <button class="hd" @click="open = !open">
        <b>需关注区域</b><span class="chip">{{ num(view.districts.length) }}</span>
        <svg class="i" viewBox="0 0 24 24" :style="{ transform: open ? 'rotate(180deg)' : '' }"><path d="m6 9 6 6 6-6" /></svg>
      </button>
      <div v-if="open" class="ind">
        <span v-for="i in view.indicators" :key="i.key" :class="{ off: !i.available }"
              :title="i.available ? `${i.name} ${i.threshold}：${i.count} 个区` : `${i.name} ${i.threshold}：${i.note ?? '暂无数据'}`">
          <svg viewBox="0 0 24 24" :style="{ stroke: i.available ? WATCH_STYLE[i.key].c : undefined }" v-html="WATCH_STYLE[i.key].icon" />
          {{ i.name }}<b>{{ i.available ? num(i.count) : '—' }}</b>
        </span>
      </div>
    </header>
    <div v-if="open" class="list">
      <button v-for="w in list" :key="w.key" class="wc" :style="{ '--c': WATCH_STYLE[w.items[0].key].c }"
              @click="emit('go', w.key, tabOf(w))" @mouseenter="emit('hot', w.key)" @mouseleave="emit('hot', null)">
        <div class="wh"><b :title="w.name">{{ w.name }}</b><small>{{ w.municipalityCode }}</small></div>
        <div v-for="it in w.items" :key="it.key" class="wi" :style="{ '--c': WATCH_STYLE[it.key].c }">
          <i><svg viewBox="0 0 24 24" v-html="WATCH_STYLE[it.key].icon" /></i>
          <div>
            <span>{{ ind[it.key]?.name }}</span>
            <em><span>阈值</span><u>{{ ind[it.key]?.threshold }}</u></em>
            <em v-for="[k, v] in refs(it)" :key="k"><span>{{ k }}</span><u>{{ v }}</u></em>
          </div>
          <b><small v-if="value(it)[1] === 'AED'">AED</small>{{ value(it)[0] }}<small v-if="value(it)[1] !== 'AED'">{{ value(it)[1] }}</small></b>
        </div>
      </button>
      <button v-if="rest > 0" class="more" @click="all = !all">{{ all ? '收起' : `显示全部（还有 ${rest} 个）` }}</button>
      <p class="foot">阈值为原型工作值（配置 d360.watch）；灰色指标的源数据尚未到位。数据日期 {{ view.asOf ?? '—' }}。</p>
    </div>
  </aside>
</template>

<style scoped>
.watch { position: absolute; left: 1.5rem; top: 4.9rem; bottom: 4.4rem; width: 19.5rem; z-index: 5; display: flex; flex-direction: column; pointer-events: none }
.watch > * { pointer-events: auto }
.watch.closed { bottom: auto }
header { background: rgba(30, 31, 35, .94); border: 1px solid var(--line); border-radius: var(--r); box-shadow: 0 .3rem 1rem rgba(0, 0, 0, .35) }
.hd { display: flex; align-items: center; gap: .5rem; width: 100%; padding: .6rem .8rem; font-size: .9rem; color: var(--tx) }
.hd b { font-weight: 700 }
.hd .i { margin-left: auto; width: 1rem; height: 1rem; fill: none; stroke: var(--tx2); stroke-width: 2; transition: transform .15s }
.ind { display: flex; flex-wrap: wrap; gap: .3rem .7rem; padding: 0 .8rem .6rem; font-size: .7rem; color: var(--tx2) }
.ind span { display: inline-flex; align-items: center; gap: .25rem; white-space: nowrap }
.ind span.off { color: var(--tx3); opacity: .6 }
.ind svg { width: .8rem; height: .8rem; fill: none; stroke: var(--tx3); stroke-width: 2; stroke-linecap: round; stroke-linejoin: round }
.ind b { color: #fff; font-weight: 700; margin-left: .1rem }
.ind .off b { color: var(--tx3) }
.list { flex: 1; min-height: 0; overflow: auto; display: flex; flex-direction: column; gap: .45rem; margin-top: .45rem; padding-right: .15rem }
.wc { display: block; width: 100%; text-align: left; padding: .55rem .7rem .6rem; border-radius: var(--r); background: rgba(30, 31, 35, .94); border: 1px solid var(--line);
  border-left: 3px solid var(--c); box-shadow: 0 .3rem 1rem rgba(0, 0, 0, .35) }
.wc:hover { background: rgba(48, 49, 54, .98) }
.wh { display: flex; align-items: baseline; gap: .5rem; margin-bottom: .3rem }
.wh b { font-size: .86rem; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
.wh small { margin-left: auto; font-size: .68rem; color: var(--tx3) }
.wi { display: grid; grid-template-columns: 1.3rem minmax(0, 1fr) auto; gap: .45rem; align-items: start; padding: .3rem 0; border-top: 1px solid rgba(255, 255, 255, .05) }
.wi i { display: grid; place-items: center; width: 1.3rem; height: 1.3rem; border-radius: .25rem; background: color-mix(in srgb, var(--c) 20%, transparent) }
.wi i svg { width: .85rem; height: .85rem; fill: none; stroke: var(--c); stroke-width: 2; stroke-linecap: round; stroke-linejoin: round }
.wi > div > span { display: block; font-size: .74rem; color: var(--tx); font-weight: 600 }
.wi em { display: flex; justify-content: space-between; gap: .4rem; font-style: normal; font-size: .66rem; color: var(--tx3) }
.wi em u { text-decoration: none; color: var(--tx2); font-variant-numeric: tabular-nums }
.wi > b { font-size: .95rem; font-weight: 800; color: var(--c); font-variant-numeric: tabular-nums; white-space: nowrap }
.wi > b small { font-size: .62rem; font-weight: 600; color: var(--tx2); margin: 0 .15rem }
.more { padding: .5rem; border-radius: var(--r); background: rgba(30, 31, 35, .94); border: 1px dashed var(--line2); font-size: .76rem; color: var(--tx2) }
.more:hover { color: #fff }
.foot { font-size: .66rem; color: var(--tx3); padding: .2rem .2rem .4rem; text-shadow: 0 0 .3rem #000 }
@media (max-width: 760px) { .watch { display: none } }
</style>
