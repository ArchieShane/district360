<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { api, type ChildItem, type DevView, type Level, type Meta, type NodeDetail } from '../api'
import { area, num } from '../format'
import { colorOf, GFA_USE, LEVEL_NAME, MP_STAGE, PLOT_STATUS } from '../labels'

/**
 * 开发建设页签（ods-dev）：总体规划（阶段、实现率、项目列表）、规划建筑面积（按用途）、在建工地、地块规划管控。
 * 建筑 / 楼层 / 单元按所在地块统计。资本项目、建设许可、竣工 / 维修 / 拆除的源数据尚未入 ODS，显示待接入。
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
const scopeNote = computed(() => {
  const sc = dev.value?.scope
  return sc && sc.level !== props.node.level ? `按所在${LEVEL_NAME[sc.level]}统计` : ''
})
const big = (v: number | undefined) => !v ? '0' : v >= 1e6 ? num(v / 1e6, 2) + 'M' : v >= 1e3 ? num(v / 1e3, 1) + 'K' : num(v)
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

    <section v-if="showCc" class="card">
      <h2>竣工与拆除<span class="src">theme.sp_building · udm_building</span></h2>
      <div class="nums">
        <div><b>{{ num(st.buildingsCompleted) }}</b><span>有竣工日期的建筑</span></div>
        <div><b>{{ num(st.buildingsDemolished) }}</b><span>有拆除日期的建筑</span></div>
        <div><b>{{ num(st.buildingsUnderConstruction) }}</b><span>在建建筑</span></div>
      </div>
      <p style="margin-top:.6rem">累计口径，最近竣工 {{ st.lastCompletionDate ?? '—' }}，最近拆除 {{ st.lastDemolitionDate ?? '—' }}。udm_building 全酋长国只有约 1,900 栋建筑登记了竣工日期（最近一次 2021-04）；原型的竣工证书、拆除许可按月统计需等建设许可源表（MEPS）。</p>
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
</style>
