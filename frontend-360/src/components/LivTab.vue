<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { api, type Level, type LivDistrict, type LivView, type NodeDetail } from '../api'
import { num } from '../format'
import { FACILITY_CATEGORY, LEVEL_NAME, livColor, TIER, TIER_TRIGGER } from '../labels'

/**
 * 宜居页签：评分只有区域粒度（v1 stat.stat_district_liveability，每日从宜居库抽取，口径与宜居平台 dashboard 一致）。
 * 区域：在途评分大字 + 现状 → 在途 → 目标进度条、排名、优先级分档、达标所需投资（CAPEX）、
 *       最缺设施前 3（在途供给评分 vs 分档目标）、居民需求前 3（理想社区调查）、公园与缺口；
 * 社区及以下：显示所在区域；酋长国 / 市政：各区平均、CAPEX 合计、分档分布与排名列表（点击下钻）。
 */
const props = defineProps<{ node: NodeDetail }>()
const emit = defineEmits<{ go: [level: Level, key: string] }>()

const liv = shallowRef<LivView | null>(null)
const error = shallowRef<string | null>(null)
const order = ref<'top' | 'bottom' | 'gap' | 'capex'>('top')
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
const gaps = computed(() => liv.value?.facilityGaps ?? [])
const needs = computed(() => liv.value?.needs ?? [])
const inherited = computed(() => d.value && props.node.level !== 'district' ? `${LEVEL_NAME[props.node.level]}没有单独评分，显示所在区域 ${d.value.name}` : '')
const triggers = computed(() => (d.value?.tierTriggers ?? '').split(',').filter(Boolean).map(t => TIER_TRIGGER[t] ?? t))

/** 进度条：现状为起点、目标为终点，在途落在中间（与原型一致） */
const track = computed(() => {
  const x = d.value
  if (!x || x.existing == null || x.pipeline == null || x.target == null) return 0
  const span = Math.max(x.target - x.existing, 0.01)
  return Math.min(Math.max((x.pipeline - x.existing) / span, 0), 1) * 100
})

const ranked = computed<LivDistrict[]>(() => {
  const all = liv.value?.districts ?? []
  if (order.value === 'capex') return [...all].filter(x => (x.capexAed ?? 0) > 0).sort((a, b) => (b.capexAed ?? 0) - (a.capexAed ?? 0))
  const list = all.filter(x => x.inScope && x.pipeline != null)
  if (order.value === 'bottom') return [...list].reverse()
  if (order.value === 'gap') return [...list].sort((a, b) => (b.gap ?? 0) - (a.gap ?? 0))
  return list
})
const outOfScope = computed(() => (liv.value?.districts ?? []).filter(x => !x.inScope).length)
const tierBar = computed(() => {
  const t = s.value?.tiers ?? {}
  return Object.entries(TIER).map(([k, v]) => ({ k, ...v, v: t[k] ?? 0 })).filter(x => x.v > 0)
})
const f2 = (v: number | null | undefined) => v == null ? '—' : num(v, 2)
/** AED 金额：≥ 10 亿显示 B，≥ 100 万显示 M */
const aed = (v: number | null | undefined) => v == null ? '—' : v >= 1e9 ? num(v / 1e9, 2) + 'B' : v >= 1e6 ? num(v / 1e6, 1) + 'M' : v >= 1e3 ? num(v / 1e3, 0) + 'K' : num(v)
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
        <div><b><small class="pre">AED</small>{{ aed(s.capexAed) }}</b><span>达标所需投资合计</span></div>
        <div><b>{{ num(s.parkGapHa, 1) }}<small>ha</small></b><span>公园缺口合计</span></div>
      </div>
      <template v-if="tierBar.length">
        <div class="sub">优先级分档</div>
        <div class="stack"><i v-for="x in tierBar" :key="x.k" :style="{ flex: x.v, background: x.c }" /></div>
        <div class="keys"><span v-for="x in tierBar" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n }}<b>{{ num(x.v) }}</b></span></div>
      </template>
      <p style="margin-top:.6rem">数据日期 {{ s.asOf ?? '—' }}。只给评估范围内的区域评分和分档，排名按在途评分；投资合计含全部区域。</p>
    </section>

    <section class="card">
      <h2>区域排名<span class="chip">{{ num(ranked.length) }}</span></h2>
      <div class="chips" style="margin:0 0 .4rem">
        <button class="chip" :class="{ on: order === 'top' }" @click="order = 'top'">评分最高</button>
        <button class="chip" :class="{ on: order === 'bottom' }" @click="order = 'bottom'">评分最低</button>
        <button class="chip" :class="{ on: order === 'gap' }" @click="order = 'gap'">距目标最远</button>
        <button class="chip" :class="{ on: order === 'capex' }" @click="order = 'capex'">投资最多</button>
      </div>
      <div class="rows scroll" style="--cols: 2.2rem minmax(0,1fr) 3.6rem 3.6rem; max-height: 28rem; overflow: auto">
        <div class="row hd"><span>排名</span><span>区域</span><span>在途</span><span>{{ order === 'capex' ? 'CAPEX' : '差距' }}</span></div>
        <button v-for="x in ranked" :key="x.key" class="row" @click="emit('go', 'district', x.key)">
          <span>{{ node.level === 'municipality' ? (x.muniRank ?? '—') : (x.rank ?? '—') }}</span>
          <span>
            <b :title="x.name"><i class="sw" :style="{ background: livColor(x.pipeline) }" />{{ x.name }}</b>
            <small>{{ node.level === 'municipality' ? (x.rank ? `全酋长国第 ${x.rank}` : '不在评估范围') : x.municipalityCode }}<template v-if="x.tier"> · {{ TIER[x.tier]?.n ?? x.tier }}</template> · 现状 {{ f2(x.existing) }} · 目标 {{ f2(x.target) }}</small>
          </span>
          <span>{{ f2(x.pipeline) }}</span>
          <span>{{ order === 'capex' ? aed(x.capexAed) : f2(x.gap) }}</span>
        </button>
      </div>
      <p v-if="outOfScope && order !== 'capex'" style="margin-top:.5rem">另有 {{ outOfScope }} 个区域不在宜居评估范围，不排名。</p>
    </section>
  </template>

  <!-- 区域及以下 -->
  <section v-else-if="!d" class="card muted"><p>{{ LEVEL_NAME[node.level] }}没有对应的区域宜居数据。</p></section>
  <template v-else>
    <section class="card">
      <h2 class="hw"><span class="t">宜居区域评分</span>
        <template v-if="d.inScope && d.rank">
          <span class="chip">第 {{ d.rank }} / {{ d.rankOf }}</span>
          <span class="chip">{{ d.municipalityCode }} 第 {{ d.muniRank }} / {{ d.muniRankOf }}</span>
        </template>
        <span v-if="d.tier" class="chip"><i class="sw dot" :style="{ background: TIER[d.tier]?.c }" />{{ TIER[d.tier]?.n ?? d.tier }}</span>
      </h2>
      <p v-if="inherited" style="margin:-.2rem 0 .5rem">
        {{ inherited }} · <button class="link" @click="emit('go', 'district', d.key)">查看区域</button>
      </p>
      <template v-if="d.inScope && d.pipeline != null">
        <div class="lv">
          <div class="big" :style="{ color: livColor(d.pipeline) }">{{ f2(d.pipeline) }}<small>在途评分</small></div>
          <div class="cx"><span>达标所需投资</span><b><small class="pre">AED</small>{{ aed(d.capexAed) }}</b></div>
        </div>
        <div class="trk" :title="`现状 ${f2(d.existing)} → 在途 ${f2(d.pipeline)} → 目标 ${f2(d.target)}`">
          <div class="fill" :style="{ width: track + '%' }" /><i class="cu" :style="{ left: track + '%' }" />
        </div>
        <div class="trio">
          <div><small><i class="d-ex" />现状</small><b>{{ f2(d.existing) }}</b></div>
          <div><small><i class="d-cu" />在途</small><b>{{ f2(d.pipeline) }}</b></div>
          <div><small><i class="d-ap" />目标</small><b>{{ f2(d.target) }}</b></div>
        </div>
        <dl class="kv" style="margin-top:.8rem">
          <dt>距目标</dt><dd>{{ f2(d.gap) }} 分</dd>
          <template v-if="d.tierRule"><dt>分档规则</dt><dd :title="triggers.join('、')">{{ d.tierRule }}<template v-if="triggers.length"> · {{ triggers.join('、') }}</template></dd></template>
          <dt>OPEX</dt><dd>AED {{ aed(d.opexAed) }}</dd>
        </dl>
        <p style="margin-top:.6rem">现状 / 在途 / 目标对应宜居库的 Existing / Pipeline（原 Current）/ Target（原 LIV Action Plan）。发布版本 {{ d.calcVersionId ?? '—' }}，数据日期 {{ d.asOf ?? '—' }}。</p>
      </template>
      <template v-else>
        <p>该区域不在宜居评估范围，没有评分和分档。</p>
        <dl v-if="d.capexAed" class="kv" style="margin-top:.6rem"><dt>CAPEX</dt><dd>AED {{ aed(d.capexAed) }}</dd></dl>
      </template>
    </section>

    <section v-if="d.inScope" class="card">
      <h2>最缺设施<span class="chip">前 3</span><span class="src">Facility Scores · FPP</span></h2>
      <div v-if="gaps.length" class="rows" style="--cols: minmax(0,1fr) 5.2rem 2.6rem">
        <div class="row hd"><span>设施类型</span><span>在途 / 分档目标</span><span>差距</span></div>
        <div v-for="g in gaps" :key="g.rank" class="row">
          <span><b :title="g.facility">{{ g.facility }}</b><small>{{ FACILITY_CATEGORY[g.category ?? ''] ?? g.category }} · 现状 {{ f2(g.existing) }} · 行动计划后 {{ f2(g.ap50) }}</small></span>
          <span class="pg"><i><u :style="{ width: Math.min(100, (g.pipeline ?? 0) / Math.max(g.target ?? 1, 1) * 100) + '%' }" /></i><small>{{ num(g.pipeline, 1) }} / {{ num(g.target, 0) }}</small></span>
          <span><b class="warn">{{ num(g.gap, 1) }}</b></span>
        </div>
      </div>
      <p v-else-if="d.tier">FPP 类设施的在途供给评分都已达到本区分档目标。</p>
      <p v-else>没有分档，无法比较目标。</p>
      <p style="margin-top:.5rem">供给评分 0–100；目标为本区分档（{{ TIER[d.tier ?? '']?.n ?? '—' }}）下该类设施的达标线。只计 FPP 类项目（含公园），道路设施、可达性、学校容量等类型未计入。</p>
    </section>

    <section class="card">
      <h2>居民需求<span class="chip">前 3</span><span class="src">理想社区调查</span></h2>
      <template v-if="needs.length">
        <div class="sub" style="margin-top:0">得票占比 %</div>
        <div class="hb">
          <div v-for="n in needs" :key="n.rank">
            <span :title="n.item">{{ n.item }}</span>
            <i><u :style="{ width: (n.pct / needs[0].pct * 100) + '%', background: '#B58CFF' }" /></i>
            <b>{{ num(n.pct, 1) }}%</b>
          </div>
        </div>
        <p style="margin-top:.5rem">本区 {{ num(needs[0].respondents) }} 人受访（Dream Neighbourhood Survey，可多选；占比 = 选该项人数 ÷ 全部选择次数）。</p>
      </template>
      <p v-else>本区没有调查结果，或受访不足 10 人（不展示）。</p>
    </section>

    <section class="card">
      <h2>公园供给</h2>
      <div class="nums">
        <div><b>{{ num(d.parks) }}</b><span>公园</span></div>
        <div><b>{{ num(d.parkAreaHa, 2) }}<small>ha</small></b><span>公园面积</span></div>
        <div><b :class="{ warn: (d.parkGapHa ?? 0) > 0 }">{{ num(d.parkGapHa, 2) }}<small>ha</small></b><span>公园缺口</span></div>
      </div>
    </section>
  </template>
</template>

<style scoped>
.hw { flex-wrap: wrap; row-gap: .35rem }
.hw .t { white-space: nowrap; margin-right: auto }
.lv { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem }
.big { font-size: 2.6rem; font-weight: 800; line-height: 1; font-variant-numeric: tabular-nums }
.big small { display: block; margin-top: .3rem; font-size: .74rem; font-weight: 500; color: var(--tx3) }
.cx { text-align: right }
.cx span { display: block; font-size: .74rem; color: var(--tx3) }
.cx b { font-size: 1.3rem; font-weight: 800; font-variant-numeric: tabular-nums }
.pre { font-size: .7rem; font-weight: 600; color: var(--tx2); margin-right: .2rem }
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
.row b .sw, .chip .dot { display: inline-block; width: .55rem; height: .55rem; border-radius: 50%; margin-right: .35rem; vertical-align: .02rem }
.pg { display: flex; flex-direction: column; gap: .2rem; align-items: stretch }
.pg i { display: block; height: .34rem; border-radius: 1rem; background: rgba(255, 255, 255, .1); overflow: hidden }
.pg u { display: block; height: 100%; background: #4C8DF6; border-radius: 1rem }
.link { color: #8FB8FF; text-decoration: underline; font-size: inherit }
</style>
