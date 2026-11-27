<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { api, type ChildItem, type DevView, type Level, type Meta, type NodeDetail } from '../api'
import { area, num } from '../format'
import { colorOf, GFA_USE, LEVEL_NAME, MP_STAGE, PERMIT_KIND, PLOT_STATUS } from '../labels'
import MonthBars from './MonthBars.vue'
import SrcPending from './SrcPending.vue'

/**
 * 开发建设页签（ods-dev）：总体规划（阶段、实现率、项目列表）、规划建筑面积（按用途）、在建工地、地块规划管控；
 * 资本项目（GSEC）、建设许可（EMECS）与竣工 / 维修 / 拆除申请（MEPS）。建筑 / 楼层 / 单元按所在地块统计
 * （资本项目没有地块属性，地块层级按所在社区）。业务视图没有数据时卡片显示「待接入」与原因。
 */
const props = defineProps<{ node: NodeDetail; meta: Meta | null }>()
const emit = defineEmits<{ focus: [lon: number, lat: number] ; go: [level: Level, key: string] }>()

const dev = shallowRef<DevView | null>(null)
const error = shallowRef<string | null>(null)
let ctrl: AbortController | null = null

watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  dev.value = null
  error.value = null
  try {
    dev.value = await api.dev(props.node.level, props.node.key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const s = computed(() => dev.value?.summary)

/** 土地与建设（原型 Development 页签第一张卡）：酋长国 ~ 社区，地块建设状态与土地用途，取自节点视图 */
const areaLevel = computed(() => ['emirate', 'municipality', 'district', 'community'].includes(props.node.level))
const land = computed(() => {
  const st = props.node.stat, bd = props.node.breakdowns
  const status = [['Constructed', st.plotsConstructed], ['Under Construction', st.plotsUnderConstruction], ['Not Constructed', st.plotsNotConstructed]]
    .map(([k, n]) => ({ k: k as string, n: Number(n ?? 0), n2: PLOT_STATUS[k as string]?.n ?? k, c: PLOT_STATUS[k as string]?.c ?? '#6B7280' }))
  const lu = bd.landUse ?? []
  const tot = lu.reduce((a, x) => a + Number(x.area), 0)
  const uses = lu.map(x => ({ k: x.k, p: tot ? Number(x.area) / tot * 100 : 0, c: colorOf(x.k) })).filter(x => x.p >= 0.5).slice(0, 8)
  return { plots: st.plots ?? 0, buildings: st.buildings ?? 0, uc: st.buildingsUnderConstruction ?? 0, status, uses }
})

/** 社区按建成率排名（原型区域层级的 Communities by built rate） */
const coms = shallowRef<(ChildItem & { built: number })[]>([])
watch(() => [props.node.level, props.node.key], async () => {
  coms.value = []
  if (props.node.level !== 'district') return
  const key = props.node.key
  try {
    const r = await api.children('district', key, 0, 500)
    if (props.node.key !== key) return
    coms.value = r.items.filter(x => Number(x.plots) > 0)
      .map(x => ({ ...x, built: Number(x.plotsConstructed ?? 0) / Number(x.plots) * 100 }))
      .sort((a, b) => b.built - a.built)
  } catch { /* 列表取不到时不显示该卡 */ }
}, { immediate: true })
/** 总规实现率：所在地块已建 / 能对上地块的总规地块 */
const delivery = computed(() => s.value && s.value.mpPlotsLinked ? (s.value.mpPlotsBuilt / s.value.mpPlotsLinked * 100).toFixed(1) : null)
const stages = computed(() => (dev.value?.stages ?? []).map(x => ({ ...x, n2: MP_STAGE[x.k]?.n ?? x.k, c: MP_STAGE[x.k]?.c ?? '#6B7280' })))
const gfa = computed(() => dev.value?.gfaByUse ?? [])
const ctrl0 = computed(() => dev.value?.plotControls?.[0])
/** 竣工 / 拆除：udm_building 的 completion_date / demolition_date 累计（建设许可源表到位前的替代口径） */
const st = computed(() => props.node.stat)
const showCc = computed(() => ['emirate', 'municipality', 'district', 'community', 'plot'].includes(props.node.level) && (st.value.buildings ?? 0) > 0)
const pending = computed(() => (props.meta?.tables ?? []).filter(t => t.theme === '开发建设' && !t.extractor))

/** 资本项目（原型 Projects 卡）：项目数、预算、支出率、状态与延期，列表延期在前 */
const cp = computed(() => dev.value?.capitalProjects)
const ps = computed(() => cp.value?.summary)
const hasPrj = computed(() => !!ps.value && ps.value.projects > 0)
const prjStatus = computed(() => (cp.value?.byStatus ?? []).map((x, i) => ({ ...x, c: ['#4C8DF6', '#62D36A', '#F0A64A', '#B58CFF', '#8C929B'][i % 5] })))
const prjScope = computed(() => props.node.level === 'plot' || props.node.level === 'building' || props.node.level === 'floor' || props.node.level === 'unit')
/** 建设许可（原型 Construction permits 卡）：近 12 个月新签、较前 6 个月变化、在办、停滞；竣工 / 维修 / 拆除；按月 */
const pm = computed(() => dev.value?.permits)
const pmS = computed(() => pm.value?.summary)
const pmA = computed(() => pm.value?.applications)
const hasPermits = computed(() => !!pmS.value && pmS.value.permits > 0)
const hasApps = computed(() => !!pmA.value && pmA.value.applications > 0)
const change = computed(() => {
  const x = pmS.value
  return x && x.newPrev6m ? (x.newLast6m / x.newPrev6m - 1) * 100 : null
})
const appMonths = computed(() => (pm.value?.applicationsByMonth ?? []) as unknown as Record<string, string | number>[])
const scopeNote = computed(() => {
  const sc = dev.value?.scope
  return sc && sc.level !== props.node.level ? `按所在${LEVEL_NAME[sc.level]}统计` : ''
})
const big = (v: number | undefined | null) => !v ? '0' : v >= 1e9 ? num(v / 1e9, 2) + 'B' : v >= 1e6 ? num(v / 1e6, 2) + 'M' : v >= 1e3 ? num(v / 1e3, 1) + 'K' : num(v)
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <section v-else-if="!dev" class="card muted"><p>加载中…</p></section>
  <section v-else-if="!dev.scope" class="card muted"><p>该建筑没有对应的地块，没有总规与工地数据。</p></section>
  <template v-else-if="s">
    <section v-if="areaLevel && land.plots" class="card">
      <h2>土地与建设<span class="src">theme.sp_plot · sp_building</span></h2>
      <div class="nums">
        <div><b>{{ num(land.plots) }}</b><span>地块</span></div>
        <div><b>{{ num(land.buildings) }}</b><span>建筑</span></div>
        <div><b>{{ num(land.uc) }}</b><span>在建建筑</span></div>
      </div>
      <div class="stack"><i v-for="x in land.status" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
      <div class="keys"><span v-for="x in land.status" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span></div>
      <template v-if="land.uses.length">
        <div class="sub">土地用途（按面积）</div>
        <div class="keys"><span v-for="x in land.uses" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.k }}<b>{{ num(x.p, 1) }}%</b></span></div>
      </template>
    </section>

    <section v-if="coms.length > 1" class="card">
      <h2>社区按建成率<span class="chip">{{ coms.length }}</span></h2>
      <div class="rows" style="--cols: minmax(0,1fr) 3.6rem 5.4rem; max-height: 16rem; overflow: auto">
        <div class="row hd"><span>社区</span><span>地块</span><span>建成率</span></div>
        <button v-for="c in coms" :key="c.key" class="row" @click="emit('go', 'community', c.key)">
          <span><b :title="c.name">{{ c.name }}</b></span>
          <span>{{ num(Number(c.plots)) }}</span>
          <span class="bar"><i><u :style="{ width: c.built + '%' }" /></i>{{ num(c.built, 1) }}%</span>
        </button>
      </div>
    </section>

    <section class="card">
      <h2>总体规划<span class="src">theme.dev_masterplan · dev_masterplan_plot</span></h2>
      <p v-if="scopeNote" style="margin:-.2rem 0 .5rem">{{ scopeNote }}</p>
      <div class="nums">
        <div><b>{{ num(s.masterPlans) }}</b><span>总规项目</span></div>
        <div><b>{{ num(s.mpPlots) }}</b><span>总规地块</span></div>
        <div><b>{{ delivery ?? '—' }}<small v-if="delivery">%</small></b><span>总规实现率</span></div>
      </div>
      <template v-if="stages.length">
        <div class="stack"><i v-for="x in stages" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
        <div class="keys"><span v-for="x in stages" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span></div>
      </template>
    </section>

    <section v-if="s.plannedGfa" class="card">
      <h2>规划建设量</h2>
      <div class="nums">
        <div><b>{{ big(s.plannedGfa) }}<small>m²</small></b><span>规划建筑面积</span></div>
        <div><b>{{ num(s.plannedUnits) }}</b><span>规划住宅单元</span></div>
        <div><b>{{ num(s.plannedPopulation) }}</b><span>规划人口</span></div>
      </div>
      <div v-if="gfa.length" class="sub">按用途</div>
      <div class="hb">
        <div v-for="x in gfa" :key="x.k">
          <span>{{ GFA_USE[x.k] ?? x.k }}</span>
          <i><u :style="{ width: (x.gfa / gfa[0].gfa * 100) + '%', background: '#4C8DF6' }" /></i>
          <b>{{ big(x.gfa) }}</b>
        </div>
      </div>
    </section>

    <section v-if="ctrl0" class="card">
      <h2>地块规划管控<span class="src">dev_masterplan_plot</span></h2>
      <dl class="kv">
        <dt>所属总规</dt><dd :title="ctrl0.projectName">{{ ctrl0.projectName ?? ctrl0.projectId }}</dd>
        <dt>总规类型 / 审批</dt><dd>{{ ctrl0.masterplanType ?? '—' }} · {{ ctrl0.approvalStatus ?? '—' }}</dd>
        <dt>规划用途</dt><dd :title="ctrl0.landUseL2 ?? ctrl0.registeredLandUse">{{ ctrl0.landUseL1 ?? '—' }}{{ ctrl0.landUseL2 ? ' · ' + ctrl0.landUseL2 : '' }}</dd>
        <dt>规划地块号</dt><dd>{{ ctrl0.plotNumber ?? '—' }}</dd>
        <dt>地块面积</dt><dd>{{ area(ctrl0.plotAreaSqm) }}</dd>
        <dt>容积率 FAR</dt><dd>{{ ctrl0.far ?? '—' }}</dd>
        <dt>层数</dt><dd>{{ ctrl0.storeys ?? '—' }}</dd>
        <dt>规划建筑面积</dt><dd>{{ num(ctrl0.totalGfa) }} m²</dd>
        <dt>住宅单元 / 人口</dt><dd>{{ num(ctrl0.units) }} / {{ num(ctrl0.population) }}</dd>
      </dl>
      <p v-if="(dev.plotControls?.length ?? 0) > 1" style="margin-top:.5rem">该地块共有 {{ dev.plotControls!.length }} 条总规记录，显示规划建筑面积最大的一条。</p>
    </section>

    <section v-if="dev.masterPlans?.length" class="card">
      <h2>总规项目<span class="chip">{{ num(s.masterPlans) }}</span></h2>
      <div class="rows" style="--cols: minmax(0,1fr) 4.6rem 3.2rem 3.6rem">
        <div class="row hd"><span>项目</span><span>阶段</span><span>地块</span><span>实现率</span></div>
        <button v-for="m in dev.masterPlans" :key="m.projectId" class="row" @click="m.cx != null && emit('focus', m.cx, m.cy!)">
          <span><b :title="m.name">{{ m.name ?? m.projectId }}</b><small>{{ m.approvalYear ?? '—' }} · {{ m.developer ?? '开发商未标注' }}</small></span>
          <span>{{ MP_STAGE[m.stage ?? 'Other']?.n ?? m.stage }}</span>
          <span>{{ num(m.plots) }}</span>
          <span>{{ m.plots ? (m.plotsBuilt! / m.plots * 100).toFixed(0) + '%' : '—' }}</span>
        </button>
      </div>
      <p v-if="s.masterPlans > dev.masterPlans.length" style="margin-top:.5rem">按面积列前 {{ dev.masterPlans.length }} 个。</p>
    </section>

    <section class="card">
      <h2>在建工地<span class="src">theme.dev_construction_site</span></h2>
      <div class="nums">
        <div><b>{{ num(s.sites) }}</b><span>工地</span></div>
        <div><b>{{ num(s.siteBuildings) }}</b><span>在建建筑</span></div>
        <div><b>{{ area(s.siteAreaSqm) }}</b><span>工地面积</span></div>
      </div>
      <div v-if="dev.sites?.length" class="rows" style="--cols: minmax(0,1fr) 5rem 3.6rem">
        <div class="row hd"><span>工地</span><span>面积</span><span>建筑</span></div>
        <button v-for="x in dev.sites.slice(0, 10)" :key="x.siteId" class="row" @click="x.cx != null && emit('focus', x.cx, x.cy!)">
          <span><b>Site {{ x.siteId }}</b><small>{{ x.description ?? '' }}</small></span>
          <span>{{ area(x.areaSqm) }}</span><span>{{ num(x.buildings ?? 0) }}</span>
        </button>
      </div>
    </section>

    <section class="card" :class="{ muted: !hasPrj }" :style="{ order: hasPrj ? 0 : 1 }">
      <h2>资本项目<span class="src">theme.dev_capital_project</span></h2>
      <p v-if="prjScope && hasPrj" style="margin:-.2rem 0 .5rem">资本项目没有地块属性，按所在社区统计</p>
      <SrcPending :sources="dev.sources" :tables="['dev_capital_project']" />
      <template v-if="hasPrj && ps">
        <div class="nums">
          <div><b>{{ num(ps.projects) }}</b><span>项目</span></div>
          <div><b>{{ big(ps.budgetAed) }}</b><span>批准预算 AED</span></div>
          <div><b>{{ ps.spentPct ?? '—' }}<small v-if="ps.spentPct != null">%</small></b><span>支出率</span></div>
        </div>
        <div class="nums">
          <div><b>{{ num(ps.ongoing) }}</b><span>在建</span></div>
          <div><b>{{ num(ps.completed) }}</b><span>已完工</span></div>
          <div><b :class="{ bad: ps.delayed > 0 }">{{ num(ps.delayed) }}</b><span>已延期 · 预算 {{ big(ps.delayedBudgetAed) }}</span></div>
        </div>
        <template v-if="prjStatus.length">
          <div class="stack"><i v-for="x in prjStatus" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
          <div class="keys"><span v-for="x in prjStatus" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.k }}<b>{{ num(x.n) }}</b></span></div>
        </template>
        <template v-if="cp?.byCategory?.length">
          <div class="sub">按类别（预算）</div>
          <div class="hb">
            <div v-for="x in cp.byCategory.slice(0, 6)" :key="x.k">
              <span :title="x.k">{{ x.k }}</span>
              <i><u :style="{ width: (x.budgetAed / Math.max(1, ...cp.byCategory.map(y => y.budgetAed)) * 100) + '%', background: '#B58CFF' }" /></i>
              <b>{{ big(x.budgetAed) }}</b>
            </div>
          </div>
        </template>
        <div v-if="cp?.items?.length" class="rows" style="--cols: minmax(0,1fr) 5.4rem 4.4rem; max-height: 18rem; overflow: auto">
          <div class="row hd"><span>项目</span><span>进度 实际 / 计划</span><span>预算</span></div>
          <button v-for="x in cp.items" :key="x.projectId" class="row" @click="x.cx != null && emit('focus', x.cx, x.cy!)">
            <span>
              <b :title="x.name">{{ x.name ?? x.projectId }}<em v-if="x.delayed" class="tag bad">已延期</em><em v-else-if="x.completed" class="tag ok">完工</em></b>
              <small>{{ [x.category, x.phase, x.status].filter(Boolean).join(' · ') }}{{ x.forecastEnd ? ' · 预测完工 ' + x.forecastEnd : x.plannedEnd ? ' · 计划完工 ' + x.plannedEnd : '' }}</small>
            </span>
            <span class="prog"><i><u :style="{ width: Math.min(100, x.plannedProgress ?? 0) + '%' }" class="pl" /><u :style="{ width: Math.min(100, x.actualProgress ?? 0) + '%' }" /></i>{{ x.actualProgress != null ? num(x.actualProgress, 0) : '—' }} / {{ x.plannedProgress != null ? num(x.plannedProgress, 0) : '—' }}%</span>
            <span>{{ big(x.budgetAed) }}</span>
          </button>
        </div>
        <p style="margin-top:.5rem">延期：未完工，且预测完工晚于原计划，或实际进度落后修订计划 10 个百分点以上。{{ ps.mapped < ps.projects ? `有位置的 ${num(ps.mapped)} 个项目可在地图「资本项目」图层查看。` : '地图上显示项目位置。' }}</p>
      </template>
    </section>

    <section class="card" :class="{ muted: !hasPermits && !hasApps }" :style="{ order: hasPermits || hasApps || pm?.plotRecords?.length ? 0 : 1 }">
      <h2>建设许可<span class="src">theme.dev_permit · dev_building_application</span></h2>
      <SrcPending :sources="dev.sources" :tables="['dev_permit', 'dev_building_application']" />
      <template v-if="hasPermits && pmS">
        <div class="nums">
          <div><b>{{ num(pmS.newLast12m) }}</b><span>近 12 个月新签<template v-if="change != null"> · <em :class="change >= 0 ? 'up' : 'down'">{{ change >= 0 ? '+' : '' }}{{ num(change, 0) }}%</em></template></span></div>
          <div><b>{{ num(pmS.inProgress) }}</b><span>在办许可</span></div>
          <div><b :class="{ bad: pmS.stalled180 > 0 }">{{ num(pmS.stalled180) }}</b><span>停滞超过 180 天</span></div>
        </div>
        <p style="margin-top:.4rem">变化 = 近 6 个月 {{ num(pmS.newLast6m) }} 件对比之前 6 个月 {{ num(pmS.newPrev6m) }} 件；以最晚签发日 {{ pmS.asOf ?? '—' }} 为准。</p>
      </template>
      <template v-if="hasApps && pmA">
        <div class="nums">
          <div><b>{{ num(pmA.completionCertificates) }}</b><span>竣工证书</span></div>
          <div><b>{{ num(pmA.maintenancePermits) }}</b><span>维修许可</span></div>
          <div><b>{{ num(pmA.demolitionPermits) }}</b><span>拆除许可</span></div>
        </div>
        <template v-if="appMonths.length">
          <div class="sub">每月维修与拆除</div>
          <MonthBars :rows="appMonths" :series="[{ key: 'maintenance', name: '维修许可', c: PERMIT_KIND.maintenance.c }, { key: 'demolition', name: '拆除许可', c: PERMIT_KIND.demolition.c }]" />
        </template>
        <template v-if="pm?.applicationTypes?.length">
          <div class="sub">申请类型</div>
          <div class="hb">
            <div v-for="x in pm.applicationTypes.slice(0, 8)" :key="x.k">
              <span :title="x.k">{{ x.k }}</span>
              <i><u :style="{ width: (x.n / pm.applicationTypes[0].n * 100) + '%', background: PERMIT_KIND[x.kind]?.c ?? '#8C929B' }" /></i>
              <b>{{ num(x.n) }}</b>
            </div>
          </div>
          <p style="margin-top:.4rem">竣工证书 / 维修 / 拆除按申请类型名称归类，口径待业务确认。</p>
        </template>
      </template>
      <template v-if="pm?.plotRecords?.length">
        <div class="sub">本地块的许可与证书</div>
        <div class="rows" style="--cols: minmax(0,1fr) 4.6rem 5.2rem; max-height: 14rem; overflow: auto">
          <div class="row hd"><span>编号 / 名称</span><span>类型</span><span>日期</span></div>
          <div v-for="(x, i) in pm.plotRecords" :key="i" class="row">
            <span><b :title="x.name">{{ x.name ?? x.number }}</b><small>{{ x.number }}{{ x.status ? ' · ' + x.status : '' }}{{ x.projectStatus ? ' · ' + x.projectStatus : '' }}</small></span>
            <span><i class="sw" :style="{ background: PERMIT_KIND[x.type]?.c, marginRight: '.3rem' }" />{{ PERMIT_KIND[x.type]?.n ?? x.type }}</span>
            <span>{{ x.date ?? '—' }}</span>
          </div>
        </div>
      </template>
    </section>

    <section v-if="showCc" class="card">
      <h2>竣工与拆除<span class="src">theme.sp_building · udm_building</span></h2>
      <div class="nums">
        <div><b>{{ num(st.buildingsCompleted) }}</b><span>有竣工日期的建筑</span></div>
        <div><b>{{ num(st.buildingsDemolished) }}</b><span>有拆除日期的建筑</span></div>
        <div><b>{{ num(st.buildingsUnderConstruction) }}</b><span>在建建筑</span></div>
      </div>
      <p style="margin-top:.6rem">累计口径，最近竣工 {{ st.lastCompletionDate ?? '—' }}，最近拆除 {{ st.lastDemolitionDate ?? '—' }}。udm_building 全酋长国只有约 1,900 栋建筑登记了竣工日期（最近一次 2021-04）；竣工证书、拆除许可的按月统计见「建设许可」卡片。</p>
    </section>

    <section v-if="pending.length" class="card muted">
      <h2>待接入<span class="badge no">ODS 暂无数据</span></h2>
      <div class="rows" style="--cols: minmax(0,1fr)">
        <div v-for="t in pending" :key="t.table" class="row"><span><b>{{ t.title }} · theme.{{ t.table }}</b><small>{{ t.sources }}</small></span></div>
      </div>
    </section>
  </template>
</template>

<style scoped>
.bar { display: flex; align-items: center; gap: .35rem; justify-content: flex-end }
.bar i { flex: 1; height: .3rem; border-radius: 1rem; background: rgba(255, 255, 255, .1); overflow: hidden; display: block }
.bar u { display: block; height: 100%; background: #3D6DF2 }
.nums b.bad { color: #FF8A8A }
.nums em { font-style: normal; font-weight: 700 }
.nums em.up { color: #9BE7A0 }
.nums em.down { color: #FFB3B3 }
.tag { display: inline-block; margin-left: .35rem; padding: 0 .3rem; border-radius: .2rem; font-size: .62rem; font-style: normal; font-weight: 700; vertical-align: .08rem }
.tag.bad { background: rgba(232, 106, 106, .22); color: #FFB3B3 }
.tag.ok { background: rgba(98, 211, 106, .18); color: #9BE7A0 }
.prog { display: flex; flex-direction: column; align-items: flex-end; gap: .15rem; font-size: .7rem }
.prog i { position: relative; width: 100%; height: .3rem; border-radius: 1rem; background: rgba(255, 255, 255, .1); overflow: hidden; display: block }
.prog u { position: absolute; left: 0; top: 0; bottom: 0; background: #4C8DF6; border-radius: 1rem }
.prog u.pl { background: rgba(255, 255, 255, .28) }
</style>
