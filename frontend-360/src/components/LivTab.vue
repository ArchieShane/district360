<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { api, type Level, type LivDistrict, type LivView, type NodeDetail } from '../api'
import { num } from '../format'
import { LEVEL_NAME, livColor } from '../labels'

/**
 * 宜居页签：评分只有区域粒度（v1 stat.stat_district_liveability，每日从宜居库抽取）。
 * 区域：在途评分大字 + 现状 → 在途 → 目标进度条、全酋长国 / 市政内排名、距目标差距、公园与缺口；
 * 社区及以下：显示所在区域的评分；酋长国 / 市政：各区平均与排名列表（点击下钻）。
 * 达标所需投资、优先级、最缺设施、居民需求没有数据来源，列为待接入。
 */
const props = defineProps<{ node: NodeDetail }>()
const emit = defineEmits<{ go: [level: Level, key: string] }>()

const liv = shallowRef<LivView | null>(null)
const error = shallowRef<string | null>(null)
const order = ref<'top' | 'bottom' | 'gap'>('top')
let ctrl: AbortController | null = null

watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  liv.value = null
  error.value = null
  try {
    liv.value = await api.liv(props.node.level, props.node.key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const d = computed(() => liv.value?.district ?? null)
const s = computed(() => liv.value?.summary ?? null)
const inherited = computed(() => d.value && props.node.level !== 'district' ? `${LEVEL_NAME[props.node.level]}没有单独评分，显示所在区域 ${d.value.name}` : '')

/** 进度条：现状为起点、目标为终点，在途落在中间（与原型一致） */
const track = computed(() => {
  const x = d.value
  if (!x || x.existing == null || x.pipeline == null || x.target == null) return 0
  const span = Math.max(x.target - x.existing, 0.01)
  return Math.min(Math.max((x.pipeline - x.existing) / span, 0), 1) * 100
})

const ranked = computed<LivDistrict[]>(() => {
  const list = (liv.value?.districts ?? []).filter(x => x.inScope && x.pipeline != null)
  if (order.value === 'bottom') return [...list].reverse()
  if (order.value === 'gap') return [...list].sort((a, b) => (b.gap ?? 0) - (a.gap ?? 0))
  return list
})
const outOfScope = computed(() => (liv.value?.districts ?? []).filter(x => !x.inScope).length)
const f2 = (v: number | null | undefined) => v == null ? '—' : num(v, 2)
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <section v-else-if="!liv" class="card muted"><p>加载中…</p></section>

  <!-- 酋长国 / 市政：各区排名 -->
  <template v-else-if="s">
    <section class="card">
      <h2>宜居区域评分<span class="src">stat.stat_district_liveability</span></h2>
      <div class="nums">
        <div><b>{{ f2(s.avgPipeline) }}</b><span>平均在途评分</span></div>
        <div><b>{{ f2(s.avgExisting) }}</b><span>平均现状评分</span></div>
        <div><b>{{ f2(s.avgTarget) }}</b><span>平均目标评分</span></div>
      </div>
      <div class="nums">
        <div><b>{{ num(s.inScope) }}<small>/ {{ num(s.districts) }}</small></b><span>纳入评估的区域</span></div>
        <div><b>{{ num(s.parkAreaHa, 1) }}<small>ha</small></b><span>公园面积 · {{ num(s.parks) }} 个</span></div>
        <div><b>{{ num(s.parkGapHa, 1) }}<small>ha</small></b><span>公园缺口合计</span></div>
      </div>
      <p style="margin-top:.6rem">数据日期 {{ s.asOf ?? '—' }}。只给评估范围内的区域评分，排名按在途评分。</p>
    </section>

    <section class="card">
      <h2>区域排名<span class="chip">{{ num(ranked.length) }}</span></h2>
      <div class="chips" style="margin:0 0 .4rem">
        <button class="chip" :class="{ on: order === 'top' }" @click="order = 'top'">评分最高</button>
        <button class="chip" :class="{ on: order === 'bottom' }" @click="order = 'bottom'">评分最低</button>
        <button class="chip" :class="{ on: order === 'gap' }" @click="order = 'gap'">距目标最远</button>
      </div>
      <div class="rows scroll" style="--cols: 2.2rem minmax(0,1fr) 3.6rem 3.4rem; max-height: 28rem; overflow: auto">
        <div class="row hd"><span>排名</span><span>区域</span><span>在途</span><span>差距</span></div>
        <button v-for="x in ranked" :key="x.key" class="row" @click="emit('go', 'district', x.key)">
          <span>{{ node.level === 'municipality' ? x.muniRank : x.rank }}</span>
          <span><b :title="x.name"><i class="sw" :style="{ background: livColor(x.pipeline) }" />{{ x.name }}</b><small>{{ node.level === 'municipality' ? `全酋长国第 ${x.rank}` : x.municipalityCode }} · 现状 {{ f2(x.existing) }} · 目标 {{ f2(x.target) }}</small></span>
          <span>{{ f2(x.pipeline) }}</span>
          <span>{{ f2(x.gap) }}</span>
        </button>
      </div>
      <p v-if="outOfScope" style="margin-top:.5rem">另有 {{ outOfScope }} 个区域不在宜居评估范围，不排名。</p>
    </section>
  </template>

  <!-- 区域及以下 -->
  <section v-else-if="!d" class="card muted"><p>{{ LEVEL_NAME[node.level] }}没有对应的区域宜居数据。</p></section>
  <template v-else>
    <section class="card">
      <h2>宜居区域评分
        <template v-if="d.inScope && d.rank">
          <span class="chip">第 {{ d.rank }} / {{ d.rankOf }}</span>
          <span class="chip">{{ d.municipalityCode }} 第 {{ d.muniRank }} / {{ d.muniRankOf }}</span>
        </template>
      </h2>
      <p v-if="inherited" style="margin:-.2rem 0 .5rem">
        {{ inherited }} · <button class="link" @click="emit('go', 'district', d.key)">查看区域</button>
      </p>
      <template v-if="d.inScope && d.pipeline != null">
        <div class="lv">
          <div class="big" :style="{ color: livColor(d.pipeline) }">{{ f2(d.pipeline) }}<small>在途评分</small></div>
          <div class="cx"><span>距目标</span><b>{{ f2(d.gap) }}<small>分</small></b></div>
        </div>
        <div class="trk" :title="`现状 ${f2(d.existing)} → 在途 ${f2(d.pipeline)} → 目标 ${f2(d.target)}`">
          <div class="fill" :style="{ width: track + '%' }" /><i class="cu" :style="{ left: track + '%' }" />
        </div>
        <div class="trio">
          <div><small><i class="d-ex" />现状</small><b>{{ f2(d.existing) }}</b></div>
          <div><small><i class="d-cu" />在途</small><b>{{ f2(d.pipeline) }}</b></div>
          <div><small><i class="d-ap" />目标</small><b>{{ f2(d.target) }}</b></div>
        </div>
        <p style="margin-top:.6rem">现状 / 在途 / 目标对应宜居库的 Existing / Pipeline（原 Current）/ Target（原 LIV Action Plan）。数据日期 {{ d.asOf ?? '—' }}。</p>
      </template>
      <p v-else>该区域不在宜居评估范围，没有评分。</p>
    </section>

    <section class="card">
      <h2>公园供给</h2>
      <div class="nums">
        <div><b>{{ num(d.parks) }}</b><span>公园</span></div>
        <div><b>{{ num(d.parkAreaHa, 2) }}<small>ha</small></b><span>公园面积</span></div>
        <div><b :class="{ warn: (d.parkGapHa ?? 0) > 0 }">{{ num(d.parkGapHa, 2) }}<small>ha</small></b><span>公园缺口</span></div>
      </div>
    </section>

    <section class="card muted">
      <h2>待接入<span class="badge no">没有数据来源</span></h2>
      <p>原型中的达标所需投资（CAPEX）、优先级分档、最缺设施前 3（供给评分与差距）、居民需求前 3（理想社区调查）暂无数据来源，需与宜居项目确认后接入。</p>
    </section>
  </template>
</template>

<style scoped>
.lv { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem }
.big { font-size: 2.6rem; font-weight: 800; line-height: 1; font-variant-numeric: tabular-nums }
.big small { display: block; margin-top: .3rem; font-size: .74rem; font-weight: 500; color: var(--tx3) }
.cx { text-align: right }
.cx span { display: block; font-size: .74rem; color: var(--tx3) }
.cx b { font-size: 1.3rem; font-weight: 800; font-variant-numeric: tabular-nums }
.cx b small { font-size: .76rem; color: var(--tx2); margin-left: .15rem }
.trk { position: relative; height: .5rem; margin: .9rem 0 .7rem; border-radius: 1rem; background: rgba(255, 255, 255, .1) }
.trk .fill { position: absolute; inset: 0 auto 0 0; border-radius: 1rem; background: linear-gradient(90deg, #8C929B, #4C8DF6) }
.trk .cu { position: absolute; top: 50%; width: .9rem; height: .9rem; margin: -.45rem 0 0 -.45rem; border-radius: 50%; background: #fff; border: 2px solid #4C8DF6 }
.trio { display: grid; grid-template-columns: repeat(3, 1fr); gap: .5rem }
.trio > div:nth-child(2) { text-align: center }
.trio > div:nth-child(3) { text-align: right }
.trio small { display: block; font-size: .74rem; color: var(--tx3) }
.trio small i { display: inline-block; width: .5rem; height: .5rem; border-radius: 50%; margin-right: .3rem; vertical-align: .05rem }
.trio b { font-size: 1.05rem; font-weight: 800; font-variant-numeric: tabular-nums }
.d-ex { background: #8C929B }
.d-cu { background: #4C8DF6 }
.d-ap { background: #62D36A }
.warn { color: #FFB23E }
.chips .chip.on { background: #4C8DF6; color: #fff }
.row b .sw { display: inline-block; width: .55rem; height: .55rem; border-radius: 50%; margin-right: .35rem; vertical-align: .02rem }
.link { color: #8FB8FF; text-decoration: underline; font-size: inherit }
</style>
