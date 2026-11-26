<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { api, type BreakdownItem, type ChildItem, type Level, type Meta, type NodeDetail } from '../api'
import { area, display, num, pct } from '../format'
import { colorOf, DIM_TITLE, INFO_LABEL, LEVEL_KICK, LEVEL_NAME, PENDING_TABS, PLOT_STATUS, SOURCE_NOTE } from '../labels'
import ChildList from './ChildList.vue'
import DevTab from './DevTab.vue'
import MktTab from './MktTab.vue'

/**
 * 右侧 360 面板（原型的 #panel）：标题、层级条、标签、页签。
 * 已接入：空间下钻（概况、下级列表、属性）；开发建设 / 市场 / 城市运行 / 设施页签显示对应主题表的接入状态。
 */
const props = defineProps<{ node: NodeDetail | null; meta: Meta | null; error: string | null }>()
const emit = defineEmits<{ go: [level: Level, key: string]; up: []; tab: [id: string]; focus: [lon: number, lat: number] }>()

const tab = ref('ov')
watch(() => props.node?.level, () => { if (tab.value === 'list' && !props.node?.childLevel) tab.value = 'ov' })
watch(tab, t => emit('tab', t), { immediate: true })

const BAR: Level[] = ['district', 'community', 'plot', 'building', 'floor', 'unit']
const bar = computed(() => {
  const n = props.node
  if (!n) return []
  const chain = [...n.path, { level: n.level, key: n.key, name: n.name }]
  const li = BAR.indexOf(n.level)
  return BAR.map((lv, i) => {
    const r = chain.find(c => c.level === lv)
    return { lv, r, cls: i === li ? 'on' : r ? 'done' : 'todo' }
  })
})

const s = computed(() => props.node?.stat ?? {})
const info = computed(() => props.node?.info ?? {})
const bd = computed<Record<string, BreakdownItem[]>>(() => props.node?.breakdowns ?? {})

const chips = computed<string[]>(() => {
  const n = props.node
  if (!n) return []
  const st = s.value, i = info.value
  const c: string[] = []
  const add = (cond: unknown, t: string) => { if (cond) c.push(t) }
  switch (n.level) {
    case 'emirate':
    case 'municipality':
      add(st.districts, `${num(st.districts)} 个区域`); add(st.communities, `${num(st.communities)} 个社区`); add(i.areaKm2, `${num(i.areaKm2, 1)} km²`); break
    case 'district':
      add(i.areaKm2, `${num(i.areaKm2, 2)} km²`); add(st.communities, `${num(st.communities)} 个社区`); add(st.plots, `${num(st.plots)} 个地块`); add(i.population, `人口 ${num(i.population)}`); break
    case 'community':
      add(st.plots, `${num(st.plots)} 个地块`); add(i.areaKm2, `${num(i.areaKm2, 2)} km²`); add(st.buildings, `${num(st.buildings)} 栋建筑`); break
    case 'plot':
      add(i.areaSqm, area(Number(i.areaSqm))); add(i.primaryUse, String(i.primaryUse)); add(i.constructionStatus, PLOT_STATUS[String(i.constructionStatus)]?.n ?? String(i.constructionStatus)); add(true, `${num(st.buildings)} 栋建筑`); break
    case 'building':
      add(st.floors, `${num(st.floors)} 层`); add(st.units, `${num(st.units)} 个单元`); add(i.heightM, `高 ${num(i.heightM, 1)} m`); add(i.physicalStatus, String(i.physicalStatus)); break
    case 'floor':
      add(true, `${num(st.units)} 个单元`); add(i.areaSqm, `${num(i.areaSqm)} m²`); add(i.baseHeightM != null, `底标高 ${num(i.baseHeightM, 1)} m`); break
    case 'unit':
      add(i.classification, String(i.classification)); add(i.areaSqm, `${num(i.areaSqm, 1)} m²`); add(i.bedrooms != null, `${num(i.bedrooms)} 卧`); add(i.bathrooms != null, `${num(i.bathrooms)} 卫`); break
  }
  return c
})

/** 数据质量提示：推导出来的对象标出来 */
const derived = computed(() => {
  const src = String(info.value.source ?? '')
  return src === 'bdms' || src === 'bdms_units' ? SOURCE_NOTE[src] : null
})

interface Kpi { v: string; u?: string; l: string }
const kpis = computed<Kpi[]>(() => {
  const n = props.node
  if (!n) return []
  const st = s.value, i = info.value
  switch (n.level) {
    case 'emirate':
    case 'municipality':
    case 'district':
    case 'community':
      return [
        { v: num(st.plots), l: '地块' }, { v: pct(st.plotsConstructed, st.plots), u: '%', l: '建成率' }, { v: num(st.buildings), l: '建筑' },
        { v: num(st.floors), l: '楼层（BDMS）' }, { v: num(st.units), l: '单元（BDMS）' }, { v: num((st.unitsAreaSqm ?? 0) / 1e4, 1), u: 'ha', l: '单元面积' },
      ]
    case 'plot':
      return [
        { v: area(Number(i.areaSqm)), l: '地块面积' }, { v: i.devcodeFar != null ? String(i.devcodeFar) : '—', l: '允许容积率' }, { v: num(i.devcodeMaxGfa), u: 'm²', l: '可建面积' },
        { v: num(st.buildings), l: '建筑' }, { v: num(st.floors), l: '楼层' }, { v: num(st.units), l: '单元' },
      ]
    case 'building':
      return [
        { v: num(st.floors), l: '楼层' }, { v: num(st.units), l: '单元' }, { v: num(st.unitsAreaSqm), u: 'm²', l: '单元面积' },
        { v: num(i.heightM, 1), u: 'm', l: '高度' }, { v: num(i.footprintSqm), u: 'm²', l: '基底面积' }, { v: num(i.floorsCount), l: '登记层数' },
      ]
    case 'floor':
      return [{ v: num(st.units), l: '单元' }, { v: num(st.unitsAreaSqm, 1), u: 'm²', l: '单元面积' }, { v: num(st.bedrooms), l: '卧室' }]
    case 'unit':
      return [{ v: num(i.areaSqm, 1), u: 'm²', l: '面积' }, { v: num(i.bedrooms), l: '卧室' }, { v: num(i.bathrooms), l: '卫生间' }]
  }
  return []
})

const plotStatus = computed(() => (bd.value.plotStatus ?? []).map(x => ({ ...x, n2: PLOT_STATUS[x.k]?.n ?? x.k, c: colorOf(x.k) })))
const landUse = computed(() => {
  const list = bd.value.landUse ?? []
  const tot = list.reduce((a, x) => a + Number(x.area), 0)
  return list.map(x => ({ ...x, p: tot ? (Number(x.area) / tot * 100).toFixed(1) : '0', c: colorOf(x.k) }))
})
const otherDims = computed(() => Object.entries(bd.value).filter(([k, v]) => k !== 'plotStatus' && k !== 'landUse' && v.length))

const infoRows = computed(() => Object.entries(info.value)
  .filter(([k]) => INFO_LABEL[k])
  .map(([k, v]) => ({
    k, l: INFO_LABEL[k],
    // 编号类字段原样显示，不加千分位
    v: k === 'source' ? (SOURCE_NOTE[String(v)] ?? String(v)) : /(Id|Key|Code|gisid)$/.test(k) ? String(v) : display(v),
  })))

/* 建筑：楼层自上而下堆叠，每层按单元户型分色 */
const floors = shallowRef<ChildItem[]>([])
const floorUnits = shallowRef<Map<string, string[]>>(new Map())
watch(() => props.node, async n => {
  floors.value = []
  floorUnits.value = new Map()
  const b = n?.level === 'building' ? n.key : n?.path.find(p => p.level === 'building')?.key
  if (!n || !b || !['building', 'floor', 'unit'].includes(n.level)) return
  try {
    const [fl, units] = await Promise.all([api.children('building', b, 0, 200), api.layer('building', b, 'units')])
    const m = new Map<string, string[]>()
    for (const f of units.features) {
      const p = f.properties as { floor: string; c: string }
      ;(m.get(p.floor) ?? m.set(p.floor, []).get(p.floor)!).push(p.c)
    }
    m.forEach(v => v.sort())
    floors.value = [...fl.items].reverse()
    floorUnits.value = m
  } catch { /* 面板其余部分照常显示 */ }
}, { immediate: true })
const curFloor = computed(() => props.node?.level === 'floor' ? props.node.key : props.node?.path.find(p => p.level === 'floor')?.key)

const spatialTables = computed(() => props.meta?.tables.filter(t => t.theme === '空间') ?? [])
const run = computed(() => props.meta?.runs.find(r => r.source === 'ods-spatial'))
const pendingOf = (theme: string) => props.meta?.tables.filter(t => t.theme === theme) ?? []
</script>

<template>
  <aside class="panel" aria-label="360 面板">
    <header v-if="node" class="phead">
      <div class="top">
        <div>
          <div class="kick">{{ LEVEL_KICK[node.level] }}{{ info.municipalityCode && node.level !== 'municipality' ? ' · ' + info.municipalityCode : '' }}</div>
          <h1>{{ node.name }}<small v-if="node.nameAr">{{ node.nameAr }}</small></h1>
        </div>
        <button v-if="node.path.length" class="up" title="上一级" @click="emit('up')">
          <svg class="i" viewBox="0 0 24 24"><path d="M12 19V5M5.5 11.5 12 5l6.5 6.5" /></svg>
        </button>
      </div>
      <div class="lvl" aria-label="层级">
        <button v-for="b in bar" :key="b.lv" :class="b.cls" :disabled="!b.r || b.cls === 'on'" @click="b.r && emit('go', b.lv, b.r.key)">
          <span>{{ LEVEL_NAME[b.lv] }}</span><b>{{ b.r?.name ?? '—' }}</b>
        </button>
      </div>
      <div class="chips">
        <span v-for="c in chips" :key="c" class="chip">{{ c }}</span>
        <span v-if="derived" class="chip warn" :title="derived">推导数据</span>
      </div>
      <div class="tabs" role="tablist">
        <button :class="{ on: tab === 'ov' }" @click="tab = 'ov'">概况</button>
        <button v-if="node.childLevel" :class="{ on: tab === 'list' }" @click="tab = 'list'">{{ LEVEL_NAME[node.childLevel] }}<i>{{ num(node.childCount) }}</i></button>
        <button :class="{ on: tab === 'dev' }" @click="tab = 'dev'">开发建设</button>
        <button :class="{ on: tab === 'mkt' }" @click="tab = 'mkt'">市场</button>
        <button v-for="t in PENDING_TABS" :key="t.id" :class="{ on: tab === t.id }" @click="tab = t.id">{{ t.name }}<i>待接入</i></button>
        <button :class="{ on: tab === 'data' }" @click="tab = 'data'">数据</button>
      </div>
    </header>
    <header v-else class="phead"><div class="kick">District 360</div><h1>{{ error ? '加载失败' : '加载中…' }}</h1></header>

    <div v-if="error" class="pbody"><section class="card"><p class="err">{{ error }}</p></section></div>

    <div v-else-if="node" class="pbody">
      <template v-if="tab === 'ov'">
        <section v-if="derived" class="card muted"><p>{{ derived }}。</p></section>
        <section v-if="kpis.length" class="card">
          <h2>{{ LEVEL_NAME[node.level] }}概况</h2>
          <div v-for="r in Math.ceil(kpis.length / 3)" :key="r" class="nums">
            <div v-for="k in kpis.slice((r - 1) * 3, r * 3)" :key="k.l"><b>{{ k.v }}<small v-if="k.u">{{ k.u }}</small></b><span>{{ k.l }}</span></div>
          </div>
        </section>

        <section v-if="plotStatus.length" class="card">
          <h2>土地与建设<span class="src">theme.sp_plot</span></h2>
          <div class="stack"><i v-for="x in plotStatus" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
          <div class="keys"><span v-for="x in plotStatus" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span></div>
          <template v-if="landUse.length">
            <div class="sub">土地用途（按面积）</div>
            <div class="keys"><span v-for="x in landUse" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.k }}<b>{{ x.p }}%</b></span></div>
          </template>
        </section>

        <section v-if="floors.length" class="card">
          <h2>楼层与单元<span class="src">theme.sp_floor · sp_unit</span></h2>
          <div class="fstack">
            <button v-for="f in floors" :key="f.key" :class="{ on: f.key === curFloor }" @click="emit('go', 'floor', f.key)">
              <b>{{ f.name }}</b>
              <span class="bar"><i v-for="(c, j) in floorUnits.get(f.key) ?? []" :key="j" :style="{ background: colorOf(c) }" :title="c" /></span>
              <em>{{ num(f.units) }} 单元</em>
            </button>
          </div>
        </section>

        <section v-for="[k, list] in otherDims" :key="k" class="card">
          <h2>{{ DIM_TITLE[k] ?? k }}</h2>
          <div class="hb">
            <div v-for="x in list" :key="x.k">
              <span :title="x.k">{{ x.k }}</span>
              <i><u :style="{ width: (x.n / list[0].n * 100) + '%', background: colorOf(x.k) }" /></i>
              <b>{{ num(x.n) }}</b>
            </div>
          </div>
        </section>

        <ChildList v-if="node.childLevel && node.level !== 'emirate'" :node="node" :limit="8" compact @go="(l, k) => emit('go', l, k)" />
        <ChildList v-else-if="node.level === 'emirate'" :node="node" compact @go="(l, k) => emit('go', l, k)" />

        <section v-if="infoRows.length" class="card">
          <h2>基本信息</h2>
          <dl class="kv"><template v-for="r in infoRows" :key="r.k"><dt>{{ r.l }}</dt><dd :title="String(r.v)">{{ r.v }}</dd></template></dl>
        </section>
      </template>

      <ChildList v-else-if="tab === 'list'" :node="node" @go="(l, k) => emit('go', l, k)" />

      <MktTab v-else-if="tab === 'mkt'" :node="node" :meta="meta" />

      <DevTab v-else-if="tab === 'dev'" :node="node" :meta="meta" @focus="(x, y) => emit('focus', x, y)" @go="(l, k) => emit('go', l, k)" />

      <template v-else-if="tab === 'data'">
        <section class="card">
          <h2>空间下钻主题表<span class="src">theme.sp_*</span></h2>
          <div class="rows" style="--cols: minmax(0,1fr) 5rem 5.5rem">
            <div class="row hd"><span>表</span><span>行数</span><span>源数据日期</span></div>
            <div v-for="t in spatialTables" :key="t.table" class="row">
              <span><b>{{ t.title }} · {{ t.table }}</b><small>{{ t.sources }}</small></span>
              <span>{{ num(t.rows) }}</span><span>{{ t.asOf ?? '—' }}</span>
            </div>
          </div>
        </section>
        <section v-if="run" class="card">
          <h2>最近一次抽取<span class="badge" :class="run.status === 'success' ? 'ok' : 'bad'">{{ run.status }}</span></h2>
          <dl class="kv">
            <dt>抽取器</dt><dd>{{ run.source }}</dd>
            <dt>开始</dt><dd>{{ new Date(run.startedAt).toLocaleString() }}</dd>
            <dt>写入行数</dt><dd>{{ num(run.rows) }}</dd>
          </dl>
          <p v-if="run.message" style="margin-top:.5rem;font-family:ui-monospace,Consolas,monospace;font-size:.72rem;word-break:break-all">{{ run.message }}</p>
        </section>
        <section class="card muted">
          <h2>数据说明</h2>
          <p>全部数据来自 ODS 库（ods 架构），由 d360-ods 抽取工程写入主题库 theme，接口只读主题库。</p>
          <p>当前 ODS：区域 216、社区 1,864 齐全；地块、BDMS 单元各只有 1 万行样例；udm 建筑台账、BDMS 楼层表为空，建筑与楼层由 BDMS 单元推导（标「推导数据」）。ODS 全量入库后重新抽取即可，无需改代码。</p>
        </section>
      </template>

      <template v-else>
        <template v-for="t in PENDING_TABS" :key="t.id">
          <section v-if="tab === t.id" class="card">
            <h2>{{ t.name }}<span class="badge no">待接入</span></h2>
            <p>{{ t.note }}。主题表已在主题库建好，抽取器尚未实现，接入后本页签展示{{ LEVEL_NAME[node.level] }}范围内的统计与明细。</p>
            <div class="rows" style="--cols: minmax(0,1fr) 4.5rem">
              <div class="row hd"><span>主题表 / ODS 源表</span><span>行数</span></div>
              <div v-for="x in pendingOf(t.theme)" :key="x.table" class="row">
                <span><b>{{ x.title }} · theme.{{ x.table }}</b><small>{{ x.sources }}</small></span>
                <span>{{ x.rows == null ? '—' : num(x.rows) }}</span>
              </div>
            </div>
          </section>
        </template>
      </template>
    </div>
  </aside>
</template>
