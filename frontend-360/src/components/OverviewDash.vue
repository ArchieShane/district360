<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { api, type AccidentSummary, type AstView, type CaseSummary, type DevSummary, type DistortionSummary, type FacilityTypes, type KN, type Level, type LivDistrict,
  type LivSummary, type MktView, type NodeDetail, type OccupancySummary, type OpsView, type PermitSummary, type ProjectSummary, type ViolationSummary } from '../api'
import { num, pct } from '../format'
import { AST_CATEGORY, FACILITY_DOMAIN, LEVEL_NAME, livColor, PLOT_STATUS, TIER } from '../labels'

/**
 * 首页五主题总览（原型 Overview 的 home view）：开发建设、资产、房地产市场、宜居、城市运行各一块，
 * 每块的标题可点，跳到对应页签。只在酋长国 / 市政 / 区域 / 社区层级显示（地块及以下是地块卡、建筑卡）。
 * 地块建设状态、建筑用途取自节点视图；其余取 /nodes/{level}/{key}/overview（各主题汇总，一次取齐）。
 * 业务视图（资本项目、许可、诉求、事故、视觉违规、违规、入住率）没有数据时写明待接入；ADREC 成交同。
 */
interface Overview {
  dev?: { summary?: DevSummary; stages?: { k: string; n: number }[]; capitalProjects?: ProjectSummary; permits?: PermitSummary }
  ast?: AstView['summary'] & { facilityTypes?: FacilityTypes | null }
  mkt?: { tenancy?: MktView['tenancy']; adrec?: MktView['adrec']; occupancy?: OccupancySummary }
  ops?: OpsView['summary'] & { cases?: CaseSummary; accidents?: AccidentSummary; accidentsBySeverity?: KN[]; distortions?: DistortionSummary; violations?: ViolationSummary }
  liv?: { district?: LivDistrict | null; summary?: LivSummary }
}

const props = defineProps<{ node: NodeDetail }>()
const emit = defineEmits<{ tab: [id: string]; go: [level: Level, key: string] }>()

const ov = shallowRef<Overview | null>(null)
const error = shallowRef<string | null>(null)
let ctrl: AbortController | null = null
watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  ov.value = null
  error.value = null
  try {
    ov.value = await api.overview(props.node.level, props.node.key, ctrl.signal) as Overview
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const st = computed(() => props.node.stat)
/** 地块建设状态（与原型一致：已建、在建、空置） */
const plots = computed(() => {
  const s = st.value
  return [
    { k: 'Constructed', n: s.plotsConstructed ?? 0 },
    { k: 'Under Construction', n: s.plotsUnderConstruction ?? 0 },
    { k: 'Not Constructed', n: s.plotsNotConstructed ?? 0 },
  ].map(x => ({ ...x, n2: PLOT_STATUS[x.k]?.n ?? x.k, c: PLOT_STATUS[x.k]?.c ?? '#6B7280' }))
})
const dev = computed(() => ov.value?.dev?.summary)
const delivery = computed(() => dev.value && dev.value.mpPlotsLinked ? pct(dev.value.mpPlotsBuilt, dev.value.mpPlotsLinked, 0) : null)

/** 建筑用途前 3（节点分组统计） */
const bldTop = computed(() => (props.node.breakdowns.buildingUse ?? []).filter(x => x.k && x.k !== 'Unknown').slice(0, 3))
const a = computed(() => ov.value?.ast)
const facilities = computed(() => {
  const x = a.value
  if (!x) return []
  const v: Record<string, number> = { school: x.schools, medical: x.medical, pharmacy: x.pharmacies, mosque: x.mosques, park: x.parks }
  return Object.entries(v).map(([k, n]) => ({ k, n: n ?? 0, name: AST_CATEGORY[k as keyof typeof AST_CATEGORY]?.n ?? k, c: AST_CATEGORY[k as keyof typeof AST_CATEGORY]?.c ?? '#3DD6D0' }))
    .filter(f => f.n > 0).sort((p, q) => q.n - p.n)
})
const facTotal = computed(() => facilities.value.reduce((s, f) => s + f.n, 0))
/** 宜居 35 类设施目录（有时优先展示：合计、覆盖类型数、前 3 类） */
const ft = computed(() => a.value?.facilityTypes ?? null)
const ftTop = computed(() => (ft.value?.top ?? []).slice(0, 3).map(x => ({ ...x, c: FACILITY_DOMAIN[x.category]?.c ?? '#3DD6D0' })))

const t = computed(() => ov.value?.mkt?.tenancy)
const adrec = computed(() => ov.value?.mkt?.adrec)
const hasAdrec = computed(() => !!adrec.value && adrec.value.sales + adrec.value.mortgages + adrec.value.rents > 0)

const ld = computed(() => ov.value?.liv?.district ?? null)
const ls = computed(() => ov.value?.liv?.summary ?? null)
const tiers = computed(() => Object.entries(TIER).map(([k, v]) => ({ k, ...v, v: ls.value?.tiers?.[k] ?? 0 })).filter(x => x.v > 0))
const track = computed(() => {
  const x = ld.value
  if (!x || x.existing == null || x.pipeline == null || x.target == null) return 0
  return Math.min(Math.max((x.pipeline - x.existing) / Math.max(x.target - x.existing, 0.01), 0), 1) * 100
})

const o = computed(() => ov.value?.ops)
/** 业务视图汇总：有数据才显示（总览不带 sources，按计数判断） */
const prj = computed(() => ov.value?.dev?.capitalProjects)
const pmt = computed(() => ov.value?.dev?.permits)
const occ = computed(() => ov.value?.mkt?.occupancy)
const hasPrj = computed(() => (prj.value?.projects ?? 0) > 0)
const hasPmt = computed(() => (pmt.value?.permits ?? 0) > 0)
const hasOcc = computed(() => (occ.value?.units ?? 0) > 0)
const hasCase = computed(() => (o.value?.cases?.cases12m ?? 0) > 0)
const hasAcc = computed(() => (o.value?.accidents?.accidents12m ?? 0) > 0)
const hasDis = computed(() => (o.value?.distortions?.reports12m ?? 0) > 0)
const hasVio = computed(() => (o.value?.violations?.violations ?? 0) > 0)
const opsPending = computed(() => [['居民诉求', hasCase.value], ['道路安全', hasAcc.value], ['视觉违规', hasDis.value], ['违规与罚款', hasVio.value]].filter(x => !x[1]).map(x => x[0]))
const f2 = (v: number | null | undefined) => v == null ? '—' : num(v, 2)
const aed = (v: number | null | undefined) => v == null ? '—' : v >= 1e9 ? num(v / 1e9, 2) + 'B' : v >= 1e6 ? num(v / 1e6, 1) + 'M' : num(v)
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <div class="dash">
    <!-- 1 开发建设 -->
    <section class="card tc">
      <button class="hd" @click="emit('tab', 'dev')"><b>开发建设</b><svg class="i" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" /></svg></button>
      <div class="kp">
        <div class="lead"><b>{{ num(st.plots) }}</b><span>地块</span></div>
        <div v-for="p in plots" :key="p.k"><b>{{ num(p.n) }}<small>{{ pct(p.n, st.plots, 0) }}%</small></b><span><i class="dot" :style="{ background: p.c }" />{{ p.n2 }}</span></div>
      </div>
      <div class="stack"><i v-for="p in plots" :key="p.k" :style="{ flex: p.n, background: p.c }" /></div>
      <div v-if="dev" class="kp" style="margin-top:.7rem">
        <div><b>{{ num(dev.masterPlans) }}</b><span>总体规划</span></div>
        <div><b>{{ delivery ?? '—' }}<small v-if="delivery">%</small></b><span>总规实现率</span></div>
        <div><b>{{ num(dev.sites) }}</b><span>在建工地</span></div>
        <div><b>{{ num(st.buildingsUnderConstruction) }}</b><span>在建建筑</span></div>
      </div>
      <div v-if="hasPrj && prj" class="kp" style="margin-top:.7rem">
        <div><b>{{ num(prj.projects) }}</b><span>资本项目</span></div>
        <div><b><small class="pre">AED</small>{{ aed(prj.budgetAed) }}</b><span>项目预算</span></div>
        <div><b>{{ prj.spentPct ?? '—' }}<small v-if="prj.spentPct != null">%</small></b><span>支出率</span></div>
        <div><b :class="{ bad: prj.delayed > 0 }">{{ num(prj.delayed) }}</b><span>延期项目</span></div>
      </div>
      <div v-if="hasPmt && pmt" class="kp" style="margin-top:.7rem">
        <div><b>{{ num(pmt.newLast12m) }}</b><span>近 12 个月新签许可</span></div>
        <div><b>{{ num(pmt.inProgress) }}</b><span>在办许可</span></div>
        <div><b :class="{ bad: pmt.stalled180 > 0 }">{{ num(pmt.stalled180) }}</b><span>停滞 &gt; 180 天</span></div>
      </div>
      <p v-if="ov && (!hasPrj || !hasPmt)" class="pend">{{ [!hasPrj && '资本项目（数量、预算、延期）', !hasPmt && '建设许可'].filter(Boolean).join('、') }}：源数据待接入</p>
    </section>

    <!-- 2 资产 -->
    <section class="card tc">
      <button class="hd" @click="emit('tab', 'ast')"><b>资产</b><svg class="i" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" /></svg></button>
      <div class="two">
        <div>
          <div class="bh"><span>建筑</span><em>用途前 3</em></div>
          <div class="lead1"><b>{{ num(st.buildings) }}</b><span>合计</span></div>
          <div class="hb">
            <div v-for="x in bldTop" :key="x.k"><span :title="x.k">{{ x.k }}</span><i><u :style="{ width: (x.n / bldTop[0].n * 100) + '%', background: '#4C8DF6' }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </div>
        <div v-if="ft && ftTop.length">
          <div class="bh"><span>公共设施</span><em>{{ ft.typesInCatalogue }} 类中有 {{ ft.typesPresent }} 类</em></div>
          <div class="lead1"><b>{{ num(ft.total) }}</b><span>合计</span></div>
          <div class="hb">
            <div v-for="x in ftTop" :key="x.type"><span :title="x.label">{{ x.label }}</span><i><u :style="{ width: (x.n / ftTop[0].n * 100) + '%', background: x.c }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </div>
        <div v-else>
          <div class="bh"><span>公共设施</span><em>前 3 类</em></div>
          <div class="lead1"><b>{{ ov ? num(facTotal) : '…' }}</b><span>合计</span></div>
          <div class="hb">
            <div v-for="x in facilities.slice(0, 3)" :key="x.k"><span>{{ x.name }}</span><i><u :style="{ width: (x.n / facilities[0].n * 100) + '%', background: x.c }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </div>
      </div>
    </section>

    <!-- 3 房地产市场 -->
    <section class="card tc">
      <button class="hd" @click="emit('tab', 'mkt')"><b>房地产市场</b><svg class="i" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" /></svg></button>
      <div v-if="hasAdrec && adrec" class="kp">
        <div class="lead"><b><small class="pre">AED</small>{{ aed(adrec.salesValue) }}</b><span>成交额</span></div>
        <div><b>{{ num(adrec.sales) }}</b><span>成交笔数</span></div>
        <div><b>{{ num(adrec.mortgages) }}</b><span>抵押笔数</span></div>
        <div><b>{{ num(adrec.rents) }}</b><span>租约</span></div>
      </div>
      <template v-else>
        <div class="kp">
          <div><b>{{ num(t?.contracts) }}</b><span>租约（Tawtheeq）</span></div>
          <div><b>{{ num(t?.units) }}</b><span>出租单元</span></div>
          <div><b>{{ t?.medianAnnualRent ? aed(t.medianAnnualRent) : '—' }}</b><span>年租金中位数 AED</span></div>
        </div>
        <p class="pend">ADREC 成交、抵押、租赁与近 12 个月走势：源数据待接入；Tawtheeq 为样例数据（至 2021 年）</p>
      </template>
      <div v-if="hasOcc && occ" class="kp" style="margin-top:.7rem">
        <div><b :class="{ bad: occ.occupancyPct != null && occ.occupancyPct < 80 }">{{ occ.occupancyPct ?? '—' }}<small v-if="occ.occupancyPct != null">%</small></b><span>入住率</span></div>
        <div><b>{{ num(occ.rented) }}</b><span>已租单元</span></div>
        <div><b>{{ num(occ.units) }}</b><span>单元</span></div>
      </div>
      <p v-else-if="ov" class="pend">入住率（Tawtheeq 单元状态）：源数据待接入</p>
    </section>

    <!-- 4 宜居 -->
    <section class="card tc">
      <button class="hd" @click="emit('tab', 'liv')">
        <b>宜居区域评分</b>
        <span v-if="ld?.rank" class="chip">第 {{ ld.rank }} / {{ ld.rankOf }}</span>
        <span v-if="ld?.tier" class="chip"><i class="dot" :style="{ background: TIER[ld.tier]?.c }" />{{ TIER[ld.tier]?.n }}</span>
        <svg class="i" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" /></svg>
      </button>
      <template v-if="ld && ld.inScope && ld.pipeline != null">
        <p v-if="node.level === 'community'" class="pend" style="margin:0 0 .4rem">所在区域 {{ ld.name }}</p>
        <div class="lv">
          <div class="big" :style="{ color: livColor(ld.pipeline) }">{{ f2(ld.pipeline) }}<small>在途评分</small></div>
          <div class="cx"><span>达标所需投资</span><b><small class="pre">AED</small>{{ aed(ld.capexAed) }}</b></div>
        </div>
        <div class="trk"><div class="fill" :style="{ width: track + '%' }" /><i class="cu" :style="{ left: track + '%' }" /></div>
        <div class="trio">
          <div><small><i class="dot" style="background:#8C929B" />现状</small><b>{{ f2(ld.existing) }}</b></div>
          <div><small><i class="dot" style="background:#4C8DF6" />在途</small><b>{{ f2(ld.pipeline) }}</b></div>
          <div><small><i class="dot" style="background:#62D36A" />目标</small><b>{{ f2(ld.target) }}</b></div>
        </div>
      </template>
      <p v-else-if="ld">{{ ld.name }} 不在宜居评估范围，没有评分。</p>
      <template v-else-if="ls">
        <div class="kp">
          <div class="lead"><b :style="{ color: livColor(ls.avgPipeline) }">{{ f2(ls.avgPipeline) }}</b><span>平均在途评分</span></div>
          <div><b>{{ num(ls.inScope) }}<small>/ {{ num(ls.districts) }}</small></b><span>纳入评估的区域</span></div>
          <div><b><small class="pre">AED</small>{{ aed(ls.capexAed) }}</b><span>达标所需投资合计</span></div>
        </div>
        <template v-if="tiers.length">
          <div class="stack" style="margin-top:.6rem"><i v-for="x in tiers" :key="x.k" :style="{ flex: x.v, background: x.c }" /></div>
          <div class="keys"><span v-for="x in tiers" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n }}<b>{{ num(x.v) }}</b></span></div>
        </template>
      </template>
      <p v-else-if="ov">{{ LEVEL_NAME[node.level] }}没有宜居数据。</p>
    </section>

    <!-- 5 城市运行 -->
    <section class="card tc">
      <button class="hd" @click="emit('tab', 'ops')"><b>城市运行</b><svg class="i" viewBox="0 0 24 24"><path d="m9 6 6 6-6 6" /></svg></button>
      <div class="kp">
        <div><b>{{ num(o?.lights) }}</b><span>路灯 · 在用 {{ o?.lights ? pct(o.lightsInService, o.lights, 0) : '—' }}%</span></div>
        <div><b>{{ num(o?.busStops) }}</b><span>公交站 · {{ num(o?.busPlatforms) }} 站台</span></div>
      </div>
      <div v-if="hasCase || hasAcc || hasDis || hasVio" class="kp" style="margin-top:.7rem">
        <div v-if="hasCase"><b>{{ num(o!.cases!.cases12m) }}</b><span>诉求 · 办结 {{ o!.cases!.closedPct ?? '—' }}%</span></div>
        <div v-if="hasAcc"><b>{{ num(o!.accidents!.accidents12m) }}</b><span>事故{{ o!.accidentsBySeverity?.[0] ? ' · ' + o!.accidentsBySeverity[0].k + ' ' + num(o!.accidentsBySeverity[0].n) : '' }}</span></div>
        <div v-if="hasDis"><b :class="{ bad: o!.distortions!.slaFixedPct != null && o!.distortions!.slaFixedPct < 68 }">{{ o!.distortions!.slaFixedPct ?? '—' }}<small v-if="o!.distortions!.slaFixedPct != null">%</small></b><span>视觉违规按期整改 · 上报 {{ num(o!.distortions!.reports12m) }}</span></div>
        <div v-if="hasVio"><b>{{ num(o!.violations!.open) }}</b><span>未结违规 · 收缴 {{ o!.violations!.collectionPct ?? '—' }}%</span></div>
      </div>
      <p v-if="ov && opsPending.length" class="pend">{{ opsPending.join('、') }}：源数据待接入</p>
    </section>
  </div>
</template>

<style scoped>
.dash { display: flex; flex-direction: column; gap: inherit }
.tc .hd { display: flex; align-items: center; gap: .45rem; width: 100%; margin: -.1rem 0 .6rem; padding: 0; text-align: left; color: var(--tx); font-size: .92rem }
.tc .hd b { font-weight: 700; white-space: nowrap }
.tc .hd .i { margin-left: auto; width: .95rem; height: .95rem; fill: none; stroke: var(--tx3); stroke-width: 2 }
.tc .hd:hover .i { stroke: #fff }
.kp { display: grid; grid-template-columns: repeat(auto-fit, minmax(4.6rem, 1fr)); gap: .5rem }
.kp > div { min-width: 0 }
.kp b { display: block; font-size: 1.15rem; font-weight: 800; font-variant-numeric: tabular-nums; line-height: 1.3; white-space: nowrap }
.kp b small { font-size: .68rem; font-weight: 600; color: var(--tx2); margin-left: .2rem }
.kp span { display: block; font-size: .7rem; color: var(--tx3); white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.kp .lead b { font-size: 1.35rem }
.stack { margin-top: .55rem }
.dot { display: inline-block; width: .5rem; height: .5rem; border-radius: 50%; margin-right: .3rem; vertical-align: .05rem }
.pre { font-size: .64rem !important; font-weight: 600; color: var(--tx2); margin: 0 .2rem 0 0 !important }
.pend { margin-top: .6rem; font-size: .7rem; color: var(--tx3) }
.kp b.bad { color: #FF8A8A }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem }
.two > div { min-width: 0 }
.bh { display: flex; align-items: baseline; justify-content: space-between; font-size: .74rem; color: var(--tx2) }
.bh em { font-style: normal; font-size: .66rem; color: var(--tx3) }
.lead1 { display: flex; align-items: baseline; gap: .35rem; margin: .15rem 0 .35rem }
.lead1 b { font-size: 1.35rem; font-weight: 800; font-variant-numeric: tabular-nums }
.lead1 span { font-size: .7rem; color: var(--tx3) }
.two .hb > div { grid-template-columns: minmax(0, 1fr) 2.2rem 2.4rem; gap: .35rem; font-size: .72rem }
.lv { display: flex; align-items: flex-end; justify-content: space-between; gap: 1rem }
.big { font-size: 2.2rem; font-weight: 800; line-height: 1; font-variant-numeric: tabular-nums }
.big small { display: block; margin-top: .3rem; font-size: .7rem; font-weight: 500; color: var(--tx3) }
.cx { text-align: right }
.cx span { display: block; font-size: .7rem; color: var(--tx3) }
.cx b { font-size: 1.15rem; font-weight: 800; font-variant-numeric: tabular-nums }
.trk { position: relative; height: .45rem; margin: .8rem 0 .6rem; border-radius: 1rem; background: rgba(255, 255, 255, .1) }
.trk .fill { position: absolute; inset: 0 auto 0 0; border-radius: 1rem; background: linear-gradient(90deg, #8C929B, #4C8DF6) }
.trk .cu { position: absolute; top: 50%; width: .8rem; height: .8rem; margin: -.4rem 0 0 -.4rem; border-radius: 50%; background: #fff; border: 2px solid #4C8DF6 }
.trio { display: grid; grid-template-columns: repeat(3, 1fr); gap: .5rem }
.trio > div:nth-child(2) { text-align: center }
.trio > div:nth-child(3) { text-align: right }
.trio small { display: block; font-size: .7rem; color: var(--tx3) }
.trio b { font-size: 1rem; font-weight: 800; font-variant-numeric: tabular-nums }
</style>
