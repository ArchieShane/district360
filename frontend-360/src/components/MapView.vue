<script setup lang="ts">
import maplibregl, { type ExpressionSpecification, type GeoJSONSource, type Map as MlMap, type StyleSpecification } from 'maplibre-gl'
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { api, LEVELS, type Layer, type Level, type LivDistrict, type NearbyView, type NodeDetail, type NodeRef, type WatchView } from '../api'
import { num } from '../format'
import { lang, setLang } from '../i18n'
import { AST_CATEGORY, colorOf, LEVEL_NAME, livColor, PLOT_STATUS, POI_BY, POI_TYPES, TIER, WATCH_STYLE } from '../labels'

/**
 * 地图：按当前层级加载对应图层，点击要素下钻。
 *   酋长国 / 市政：区域面（按建成率着色；宜居页签时按在途评分着色）+ 区名标签
 *   区域：社区面 + 社区名标签 + 地块（建设状态色）+ 建筑 3D
 *   社区：地块 + 建筑 3D；地块：所在社区的地块（背景）+ 本地块建筑
 *   建筑：楼层半透明分层堆叠 + 单元按户型着色（3D，底标高 / 层高）
 *   楼层 / 单元：本层单元 + 整栋楼层轮廓；选中对象用白色描边
 * 图层菜单（原型 Layers）：底图；地块按建设状态 / 土地用途着色；设施点位开关（学校、医疗、药房、清真寺、公园、公交站）；
 *   指标图层 —— 酋长国 / 市政的区域面按建成率 / 宜居分 / 达标投资 / 优先级着色，区域内的社区面按建成率着色
 *   （入住率、违规、销售额、事故缺数据，菜单里灰显）。
 * 地块及以下：设施页签打开或勾选了设施点位时，画 500 / 1000 m 范围圈和圈内设施（原型选中地块后的范围圈）。
 * 需关注区域：卡片列里显示的区画脉冲标记，并从卡片连线到标记（原型 Districts to watch 的连线）。
 */
const props = defineProps<{ node: NodeDetail | null; themeTab?: string; focus?: { lon: number; lat: number; t: number } | null;
  watch?: WatchView | null; hot?: string | null; watchShown?: string[] }>()
const emit = defineEmits<{ pick: [level: Level, key: string] }>()

const el = ref<HTMLDivElement>()
const tip = ref<{ x: number; y: number; name: string; sub: string } | null>(null)
const base = ref<'sat' | 'dark'>('sat')
const three = ref(true)
type Legend = { title: string; items: { n: string; c: string }[] } | null
/** 图例分三层：单元 > 地块 > 区域面，取最具体的一层显示 */
const areaLegend = shallowRef<Legend>(null)
const plotLegend = shallowRef<Legend>(null)
const unitLegend = shallowRef<Legend>(null)
const legend = computed<Legend>(() => unitLegend.value ?? plotLegend.value ?? areaLegend.value)
/** 第二组图例：周边设施（1 km 内个数）或勾选的设施点位 */
const poiLegend = computed<Legend>(() => {
  const shownCats = props.themeTab === 'ast' ? POI_TYPES.map(x => x.k) : poi.value
  const nb = near.value
  if (nearOn.value && nb?.counts) {
    const items = POI_TYPES.filter(x => shownCats.includes(x.k)).map(x => ({ n: `${x.n} ${nb.counts?.[x.k]?.r1000 ?? 0}`, c: x.c }))
    return items.length ? { title: '周边设施 · 1 km', items } : null
  }
  const items = POI_TYPES.filter(x => poi.value.includes(x.k)).map(x => ({ n: x.n, c: x.c }))
  return items.length ? { title: '设施点位', items } : null
})
/** 周边设施（地块及以下） */
const near = shallowRef<NearbyView | null>(null)
const nearLevel = computed(() => ['plot', 'building', 'floor', 'unit'].includes(props.node?.level ?? ''))
const nearOn = computed(() => nearLevel.value && (props.themeTab === 'ast' || poi.value.length > 0))

/** 图层菜单 */
type Kpi = 'built' | 'liv' | 'capex' | 'tier'
const menu = ref(false)
const plotMode = ref<'status' | 'use'>('status')
/** 区域 / 社区面的指标图层；null = 不着色 */
const kpi = ref<Kpi | null>('built')
/** 手动打开的设施点位（设施页签、城市运行页签打开时另外全显示） */
const poi = ref<string[]>([])
const POI = POI_TYPES
const KPI_AREA: { k: Kpi; n: string }[] = [{ k: 'built', n: '建成率' }, { k: 'liv', n: '宜居分' }, { k: 'capex', n: '达标投资' }, { k: 'tier', n: '优先级' }]
const KPI_PENDING = ['入住率', '违规', '销售额', '道路事故']
const lv = computed(() => props.node?.level)
const areaLevel = computed(() => lv.value === 'emirate' || lv.value === 'municipality' || lv.value === 'district')
const kpiOptions = computed(() => lv.value === 'district' ? KPI_AREA.slice(0, 1) : areaLevel.value ? KPI_AREA : [])
const hasPlots = computed(() => !!props.node?.layers.includes('plots') || lv.value === 'community' || lv.value === 'plot')
const hasPoi = computed(() => !!props.node?.layers.includes('facilities'))
let map: MlMap | null = null
let ready = false
let ctrl: AbortController | null = null
let markers: maplibregl.Marker[] = []
let hovered: { src: string; id: number } | null = null
let resizeObs: ResizeObserver | null = null

const EMPTY: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] }
const SOURCES = ['area', 'plot', 'bld', 'flr', 'unit', 'sel', 'mp', 'site', 'bus', 'light', 'fac', 'ring', 'near'] as const
/** 主题页签叠加的数据源：页签 → [数据源, 图层] */
const THEME_LAYERS: Record<string, [Src, Layer][]> = {
  dev: [['mp', 'masterplans'], ['site', 'sites']],
  ops: [['bus', 'busstops'], ['light', 'streetlights']],
  ast: [['fac', 'facilities']],
}
const THEME_SRC = new Set<string>(Object.values(THEME_LAYERS).flat().map(x => x[0]))
type Src = typeof SOURCES[number]

const STYLE: StyleSpecification = {
  version: 8,
  sources: {
    sat: { type: 'raster', tiles: ['https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'], tileSize: 256, maxzoom: 19, attribution: 'Imagery © Esri, Maxar, Earthstar Geographics' },
    dark: { type: 'raster', tiles: ['a', 'b', 'c', 'd'].map(s => `https://${s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png`), tileSize: 256, maxzoom: 20, attribution: '© OpenStreetMap contributors © CARTO' },
  },
  layers: [
    { id: 'bg', type: 'background', paint: { 'background-color': '#0B0E13' } },
    { id: 'sat', type: 'raster', source: 'sat', paint: { 'raster-saturation': -0.25, 'raster-brightness-max': 0.82 } },
    { id: 'dark', type: 'raster', source: 'dark', layout: { visibility: 'none' } },
  ],
}

const AREA_COLOR = ['interpolate', ['linear'], ['coalesce', ['get', 'built'], 0], 0, '#2B4C7E', 50, '#3D6DF2', 100, '#8FE3FF'] as unknown as ExpressionSpecification
const AREA_OPACITY = (base: number) => ['case', ['boolean', ['feature-state', 'hover'], false], base + 0.22, base] as unknown as ExpressionSpecification

const PLOT_COLOR: ExpressionSpecification = ['match', ['get', 's'],
  ...Object.entries(PLOT_STATUS).flatMap(([k, v]) => [k, v.c]), '#6B7280'] as unknown as ExpressionSpecification
/** 当前地块数据（换着色方式时重算图例） */
let plotFc: GeoJSON.FeatureCollection = EMPTY

function addLayers(m: MlMap) {
  SOURCES.forEach(s => m.addSource(s, { type: 'geojson', data: EMPTY, generateId: true }))
  // 区域 / 社区 / 市政面：按建成率着色，悬停提亮
  m.addLayer({ id: 'area-fill', type: 'fill', source: 'area', paint: {
    'fill-color': AREA_COLOR, 'fill-opacity': AREA_OPACITY(0.2) } })
  m.addLayer({ id: 'area-line', type: 'line', source: 'area', paint: { 'line-color': '#9CC4FF', 'line-width': 1, 'line-opacity': 0.75 } })
  // 需关注区域：按第一项越过的指标着色的光晕与描边（只画名单里的区）；悬停卡片的区填充提亮
  m.addLayer({ id: 'watch-glow', type: 'line', source: 'area', filter: ['in', ['get', 'key'], ['literal', []]],
    paint: { 'line-color': '#FF5A5A', 'line-width': 8, 'line-blur': 7, 'line-opacity': 0.7 }, layout: { 'line-join': 'round' } })
  m.addLayer({ id: 'watch-line', type: 'line', source: 'area', filter: ['in', ['get', 'key'], ['literal', []]],
    paint: { 'line-color': '#FF5A5A', 'line-width': 1.6 }, layout: { 'line-join': 'round' } })
  m.addLayer({ id: 'watch-hot', type: 'fill', source: 'area', filter: ['==', ['get', 'key'], '__none__'],
    paint: { 'fill-color': '#FFFFFF', 'fill-opacity': 0.28 } })
  m.addLayer({ id: 'plot-fill', type: 'fill', source: 'plot', paint: {
    'fill-color': PLOT_COLOR, 'fill-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 0.7, 0.42] } })
  m.addLayer({ id: 'plot-line', type: 'line', source: 'plot', minzoom: 13, paint: { 'line-color': 'rgba(255,255,255,0.45)', 'line-width': 0.6 } })
  m.addLayer({ id: 'bld-ext', type: 'fill-extrusion', source: 'bld', paint: {
    'fill-extrusion-color': ['case', ['boolean', ['feature-state', 'hover'], false], '#FFFFFF', ['get', 'fd'], '#62E0FF', '#7FA2FF'],
    'fill-extrusion-height': ['get', 'h'], 'fill-extrusion-base': 0, 'fill-extrusion-opacity': 0.95,
    'fill-extrusion-vertical-gradient': true } })
  // 建筑基底轮廓：体块半透明或俯视时也能看清每栋楼的边界
  m.addLayer({ id: 'bld-line', type: 'line', source: 'bld', minzoom: 14, paint: {
    'line-color': '#E6F4FF', 'line-width': ['interpolate', ['linear'], ['zoom'], 14, 0.4, 18, 1.4], 'line-opacity': 0.9 } })
  m.addLayer({ id: 'flr-ext', type: 'fill-extrusion', source: 'flr', paint: {
    'fill-extrusion-color': ['case', ['boolean', ['feature-state', 'hover'], false], '#FFFFFF', '#C9D3DE'],
    'fill-extrusion-base': ['get', 'b'], 'fill-extrusion-height': ['-', ['get', 't'], 0.25], 'fill-extrusion-opacity': 0.45 } })
  m.addLayer({ id: 'flr-line', type: 'line', source: 'flr', paint: { 'line-color': '#FFFFFF', 'line-width': 1, 'line-opacity': 0.6 } })
  m.addLayer({ id: 'unit-ext', type: 'fill-extrusion', source: 'unit', paint: {
    'fill-extrusion-color': '#4C8DF6', 'fill-extrusion-base': ['get', 'b'], 'fill-extrusion-height': ['get', 't'], 'fill-extrusion-opacity': 0.9 } })
  // 开发建设页签：总规项目面（橙色虚线）、在建工地（红色）
  m.addLayer({ id: 'mp-fill', type: 'fill', source: 'mp', paint: { 'fill-color': '#F0A64A', 'fill-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 0.28, 0.1] } })
  m.addLayer({ id: 'mp-line', type: 'line', source: 'mp', paint: { 'line-color': '#F0A64A', 'line-width': 1.6, 'line-dasharray': [3, 2] } })
  m.addLayer({ id: 'site-fill', type: 'fill', source: 'site', paint: { 'fill-color': '#E86A6A', 'fill-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 0.55, 0.32] } })
  m.addLayer({ id: 'site-line', type: 'line', source: 'site', paint: { 'line-color': '#FF8A8A', 'line-width': 1.2 } })
  // 城市运行页签：路灯（黄色小点）、公交站台（蓝色圆点）
  m.addLayer({ id: 'light-pt', type: 'circle', source: 'light', paint: {
    'circle-color': ['match', ['get', 'st'], 'In Service', '#F2D45C', 'Out Of Service', '#E86A6A', '#8C929B'],
    'circle-radius': ['interpolate', ['linear'], ['zoom'], 12, 1.2, 17, 3.5], 'circle-opacity': 0.9 } })
  // 设施页签：按类别着色的位置点
  m.addLayer({ id: 'fac-pt', type: 'circle', source: 'fac', paint: {
    'circle-color': ['match', ['get', 'c'], ...Object.entries(AST_CATEGORY).flatMap(([k, v]) => [k, v.c]), '#8C929B'] as unknown as ExpressionSpecification,
    'circle-stroke-color': '#0E1116', 'circle-stroke-width': 1,
    'circle-radius': ['case', ['boolean', ['feature-state', 'hover'], false], 8, 5] } })
  m.addLayer({ id: 'bus-pt', type: 'circle', source: 'bus', paint: {
    'circle-color': '#4C8DF6', 'circle-stroke-color': '#FFFFFF', 'circle-stroke-width': 1.2,
    'circle-radius': ['case', ['boolean', ['feature-state', 'hover'], false], 7, 4.5] } })
  // 地块周边：500 / 1000 m 范围圈（虚线）与圈内设施
  m.addLayer({ id: 'ring-line', type: 'line', source: 'ring', paint: { 'line-color': '#FFFFFF', 'line-width': 1.4, 'line-opacity': 0.8, 'line-dasharray': [2, 2] } })
  m.addLayer({ id: 'near-pt', type: 'circle', source: 'near', paint: {
    'circle-color': ['match', ['get', 'c'], ...POI_TYPES.flatMap(x => [x.k, x.c]), '#8C929B'] as unknown as ExpressionSpecification,
    'circle-stroke-color': '#0E1116', 'circle-stroke-width': 1,
    'circle-radius': ['case', ['boolean', ['feature-state', 'hover'], false], 7, 4.5] } })
  m.addLayer({ id: 'sel-line', type: 'line', source: 'sel', paint: { 'line-color': '#FFFFFF', 'line-width': 2.4 } })
}

/** 当前层级要显示的图层：[数据源, 取哪个节点的哪个图层] */
function plan(n: NodeDetail): { src: Src; at: NodeRef; layer: Layer }[] {
  const self: NodeRef = { level: n.level, key: n.key, name: n.name }
  const up = (lv: Level) => n.path.find(p => p.level === lv)
  const out: { src: Src; at: NodeRef; layer: Layer }[] = []
  const add = (src: Src, at: NodeRef | undefined, layer: Layer) => { if (at) out.push({ src, at, layer }) }
  switch (n.level) {
    case 'emirate': add('area', self, 'districts'); break
    case 'municipality': add('area', self, 'districts'); break
    case 'district': add('area', self, 'communities'); add('plot', self, 'plots'); add('bld', self, 'buildings'); break
    case 'community': add('plot', self, 'plots'); add('bld', self, 'buildings'); break
    case 'plot': add('plot', up('community'), 'plots'); add('bld', self, 'buildings'); break
    case 'building': add('bld', up('plot') ?? up('community'), 'buildings'); add('flr', self, 'floors'); add('unit', self, 'units'); break
    case 'floor': add('flr', up('building'), 'floors'); add('unit', self, 'units'); break
    case 'unit': add('flr', up('building'), 'floors'); add('unit', up('floor'), 'units'); break
  }
  return out
}

const panelPx = () => parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--pw')) || 0

async function show(n: NodeDetail) {
  if (!map || !ready) return
  ctrl?.abort()
  ctrl = new AbortController()
  const signal = ctrl.signal
  const m = map
  const layers = plan(n)
  const want = new Set(layers.map(l => l.src))
  showTheme(n)
  SOURCES.filter(s => s !== 'sel' && !THEME_SRC.has(s) && !want.has(s)).forEach(s => (m.getSource(s) as GeoJSONSource).setData(EMPTY))
  ;(m.getSource('sel') as GeoJSONSource).setData(n.geometry ? { type: 'Feature', geometry: n.geometry, properties: {} } : EMPTY)
  // 建筑层级：本楼不画体块（看楼层和单元），周边建筑压暗
  m.setPaintProperty('bld-ext', 'fill-extrusion-opacity', n.level === 'building' || n.level === 'floor' || n.level === 'unit' ? 0.55 : 0.95)
  m.setPaintProperty('plot-fill', 'fill-opacity', ['case', ['boolean', ['feature-state', 'hover'], false], 0.7, n.level === 'plot' ? 0.22 : 0.42])
  fit(n)
  markers.forEach(mk => mk.remove())
  markers = []
  plotLegend.value = null
  unitLegend.value = null
  plotFc = EMPTY
  paintArea(n)
  showNear()
  const results = await Promise.all(layers.map(l => api.layer(l.at.level, l.at.key, l.layer, signal).then(fc => ({ l, fc })).catch(() => null)))
  if (signal.aborted) return
  for (const r of results) {
    if (!r) continue
    let fc = r.fc
    if (r.l.src === 'bld' && (n.level === 'building' || n.level === 'floor' || n.level === 'unit')) {
      const self = n.level === 'building' ? n.key : n.path.find(p => p.level === 'building')?.key
      fc = { ...fc, features: fc.features.filter(f => f.properties?.key !== self) }
    }
    if (r.l.src === 'unit') {
      paintUnits(fc, n)
    }
    ;(m.getSource(r.l.src) as GeoJSONSource).setData(fc)
    if (r.l.src === 'area') labels(fc)
    if (r.l.src === 'plot') {
      plotFc = fc
      paintPlots()
    }
  }
}

/** 开发建设图层：页签打开且本层级支持时加载总规面、在建工地，否则清空 */
let devCtrl: AbortController | null = null
async function showTheme(n: NodeDetail) {
  if (!map || !ready) return
  devCtrl?.abort()
  devCtrl = new AbortController()
  const m = map, sig = devCtrl.signal
  const active = new Set((THEME_LAYERS[props.themeTab ?? ''] ?? []).map(x => x[1]))
  // 图层菜单里勾选的设施 / 公交站：不在对应页签时也加载
  if (poi.value.some(k => k !== 'bus')) active.add('facilities')
  if (poi.value.includes('bus')) active.add('busstops')
  paintPoi()
  for (const [src, layer] of Object.values(THEME_LAYERS).flat()) {
    const on = active.has(layer) && n.layers.includes(layer)
    if (!on) { (m.getSource(src) as GeoJSONSource).setData(EMPTY); continue }
    api.layer(n.level, n.key, layer, sig).then(fc => { if (!sig.aborted) (m.getSource(src) as GeoJSONSource).setData(fc) }).catch(() => undefined)
  }
}

/** 区域 / 社区面的指标着色。宜居页签打开且仍是默认的建成率时，酋长国 / 市政自动换成宜居分（原型 Liveability 指标图层） */
let areaCtrl: AbortController | null = null
const livCache = new Map<string, LivDistrict[]>()
const mix = (a: string, b: string, t: number) => '#' + [1, 3, 5].map(i => Math.round(parseInt(a.slice(i, i + 2), 16) * (1 - t) + parseInt(b.slice(i, i + 2), 16) * t).toString(16).padStart(2, '0')).join('')
const effKpi = (n: NodeDetail): Kpi | null => {
  const top = n.level === 'emirate' || n.level === 'municipality'
  if (!top && n.level !== 'district') return null
  if (!top && kpi.value !== 'built') return null
  return top && kpi.value === 'built' && props.themeTab === 'liv' ? 'liv' : kpi.value
}
async function paintArea(n: NodeDetail) {
  if (!map) return
  areaCtrl?.abort()
  const m = map
  const k = effKpi(n)
  const set = (color: ExpressionSpecification | string, op: number, lg: Legend) => {
    m.setPaintProperty('area-fill', 'fill-color', color as ExpressionSpecification)
    m.setPaintProperty('area-fill', 'fill-opacity', AREA_OPACITY(op))
    areaLegend.value = lg
  }
  if (k === null) { set('#3D6DF2', 0.1, null); return }
  if (k === 'built') {
    set(AREA_COLOR, 0.2, { title: n.level === 'district' ? '社区建成率' : '区域建成率', items: [{ n: '0%', c: '#2B4C7E' }, { n: '50%', c: '#3D6DF2' }, { n: '100%', c: '#8FE3FF' }] })
    return
  }
  areaCtrl = new AbortController()
  const sig = areaCtrl.signal
  try {
    const ck = n.level + '/' + n.key
    let list = livCache.get(ck)
    if (!list) {
      list = (await api.liv(n.level, n.key, sig)).districts ?? []
      livCache.set(ck, list)
    }
    if (sig.aborted || !map) return
    let pairs: (string)[] = []
    let lg: Legend = null
    const grey = livColor(null)
    if (k === 'liv') {
      pairs = list.flatMap(d => [d.key, livColor(d.inScope ? d.pipeline : null)])
      lg = { title: '宜居在途评分', items: [{ n: '90 及以上', c: livColor(90) }, { n: '67.5', c: livColor(67.5) }, { n: '45 及以下', c: livColor(45) }, { n: '不在评估范围', c: grey }] }
    } else if (k === 'capex') {
      const mx = Math.max(1, ...list.map(d => d.capexAed ?? 0))
      pairs = list.flatMap(d => [d.key, d.capexAed ? mix('#22304A', '#FFB23E', Math.sqrt(d.capexAed / mx)) : '#22304A'])
      const m1 = mx / 1e6
      lg = { title: '达标所需投资（AED）', items: [{ n: '0', c: '#22304A' }, { n: num(m1 / 4, 0) + 'M', c: mix('#22304A', '#FFB23E', 0.5) }, { n: num(m1, 0) + 'M', c: '#FFB23E' }] }
    } else {
      pairs = list.flatMap(d => [d.key, d.tier ? TIER[d.tier]?.c ?? grey : grey])
      lg = { title: '优先级分档', items: [...Object.values(TIER).map(t => ({ n: t.n, c: t.c })), { n: '不在评估范围', c: grey }] }
    }
    set((pairs.length ? ['match', ['get', 'key'], ...pairs, grey] : grey) as unknown as ExpressionSpecification, 0.55, lg)
  } catch { if (!sig.aborted) set(AREA_COLOR, 0.2, null) }
}

/** 地块按建设状态 / 土地用途着色，图例只列当前数据里出现的类别 */
function paintPlots() {
  if (!map) return
  const fs = plotFc.features
  if (plotMode.value === 'status') {
    map.setPaintProperty('plot-fill', 'fill-color', PLOT_COLOR)
    const seen = new Set(fs.map(f => String(f.properties?.s)))
    plotLegend.value = fs.length ? { title: '地块建设状态', items: Object.entries(PLOT_STATUS).filter(([k]) => seen.has(k)).map(([, v]) => v) } : null
  } else {
    const cnt = new Map<string, number>()
    fs.forEach(f => { const u = String(f.properties?.u ?? 'Undefined'); cnt.set(u, (cnt.get(u) ?? 0) + 1) })
    const uses = [...cnt.entries()].sort((a, b) => b[1] - a[1]).map(x => x[0])
    map.setPaintProperty('plot-fill', 'fill-color', (uses.length
      ? ['match', ['get', 'u'], ...uses.flatMap(u => [u, colorOf(u)]), '#6B7280'] : '#6B7280') as unknown as ExpressionSpecification)
    plotLegend.value = fs.length ? { title: '地块土地用途', items: uses.slice(0, 8).map(u => ({ n: u, c: colorOf(u) })) } : null
  }
}

/** 设施 / 公交站点位：所在页签打开时全显示，否则只显示菜单里勾选的类别 */
function paintPoi() {
  if (!map) return
  const fac = props.themeTab === 'ast' ? null : poi.value.filter(k => k !== 'bus')
  map.setFilter('fac-pt', fac === null ? null : ['in', ['get', 'c'], ['literal', fac]])
}

function togglePoi(k: string) {
  const first = poi.value.length === 0
  poi.value = poi.value.includes(k) ? poi.value.filter(x => x !== k) : [...poi.value, k]
  if (props.node) { showTheme(props.node); showNear(first && poi.value.length > 0) }
}

/** 范围圈：以 (lon, lat) 为圆心、r 米的近似圆（64 边形） */
function circle(lon: number, lat: number, r: number): GeoJSON.Feature {
  const dy = r / 111320, dx = r / (111320 * Math.cos(lat * Math.PI / 180))
  const ring = Array.from({ length: 65 }, (_, i) => { const a = i / 64 * 2 * Math.PI; return [lon + dx * Math.cos(a), lat + dy * Math.sin(a)] })
  return { type: 'Feature', properties: { r }, geometry: { type: 'LineString', coordinates: ring } }
}
let ringMarkers: maplibregl.Marker[] = []
let nearCtrl: AbortController | null = null
/** 地块及以下：设施页签打开或勾选设施时画范围圈与圈内设施；fly = 把镜头拉到 1 km 圈 */
async function showNear(fly = false) {
  if (!map || !ready) return
  const m = map, n = props.node
  ringMarkers.forEach(x => x.remove())
  ringMarkers = []
  const clear = () => { (m.getSource('ring') as GeoJSONSource).setData(EMPTY); (m.getSource('near') as GeoJSONSource).setData(EMPTY) }
  if (!n || !nearOn.value) { clear(); return }
  let v = near.value
  const want = n.level + '/' + n.key
  if (!v || (v as NearbyView & { _k?: string })._k !== want) {
    nearCtrl?.abort()
    nearCtrl = new AbortController()
    try {
      v = await api.nearby(n.level, n.key, nearCtrl.signal)
      ;(v as NearbyView & { _k?: string })._k = want
      near.value = v
    } catch { return }
    if (props.node !== n || !nearOn.value) return
  }
  if (!v?.center) { clear(); return }
  const [lon, lat] = v.center
  const radii = v.radii ?? [500, 1000]
  ;(m.getSource('ring') as GeoJSONSource).setData({ type: 'FeatureCollection', features: radii.map(r => circle(lon, lat, r)) })
  const cats = props.themeTab === 'ast' ? null : poi.value
  ;(m.getSource('near') as GeoJSONSource).setData({ type: 'FeatureCollection', features: (v.items ?? [])
    .filter(it => !cats || cats.includes(it.category))
    .map(it => ({ type: 'Feature', properties: { c: it.category, name: `${it.name ?? POI_BY[it.category]?.n ?? ''} · ${it.dist} m`, level: 'facility' },
      geometry: { type: 'Point', coordinates: [it.cx, it.cy] } })) })
  for (const r of radii) {
    const div = document.createElement('div')
    div.className = 'rl'
    div.textContent = r >= 1000 ? r / 1000 + ' km' : r + ' m'
    ringMarkers.push(new maplibregl.Marker({ element: div, anchor: 'bottom' }).setLngLat([lon, lat + r / 111320]).addTo(m))
  }
  if (fly) {
    const d = 1100 / 111320, dx = d / Math.cos(lat * Math.PI / 180)
    m.fitBounds([[lon - dx, lat - d], [lon + dx, lat + d]], { padding: { top: 60, bottom: 60, left: 60, right: panelPx() + 60 }, pitch: 40, duration: 900 })
  }
}

/** 需关注区域：卡片列里显示的区画脉冲标记，并从卡片连线到标记 */
let watchMarkers: maplibregl.Marker[] = []
const wlines = ref<{ d: string; c: string }[]>([])
function paintWatchMarkers() {
  watchMarkers.forEach(x => x.remove())
  watchMarkers = []
  if (!map || !ready) return
  const n = props.node, w = props.watch
  if (!w || !n || (n.level !== 'emirate' && n.level !== 'municipality')) { wlines.value = []; return }
  const by = new Map(w.districts.map(d => [d.key, d]))
  for (const k of props.watchShown ?? []) {
    const d = by.get(k)
    if (!d || d.cx == null || d.cy == null) continue
    const div = document.createElement('div')
    div.className = 'wb'
    div.style.setProperty('--c', WATCH_STYLE[d.items[0].key]?.c ?? '#FF5A5A')
    div.innerHTML = '<u></u><u></u><b></b>'
    div.title = d.name
    div.onclick = e => { e.stopPropagation(); emit('pick', 'district', d.key) }
    watchMarkers.push(new maplibregl.Marker({ element: div, anchor: 'center' }).setLngLat([d.cx, d.cy]).addTo(map))
  }
  drawWatchLines()
}
let lineRaf = 0
function drawWatchLines() {
  cancelAnimationFrame(lineRaf)
  lineRaf = requestAnimationFrame(() => {
    const n = props.node, w = props.watch
    if (!map || !el.value || !w || !n || (n.level !== 'emirate' && n.level !== 'municipality')) { wlines.value = []; return }
    const box = el.value.getBoundingClientRect()
    const list = document.querySelector('.watch .list')?.getBoundingClientRect()
    const by = new Map(w.districts.map(d => [d.key, d]))
    const out: { d: string; c: string }[] = []
    for (const k of props.watchShown ?? []) {
      const d = by.get(k), card = document.querySelector(`.watch .wc[data-key="${CSS.escape(k)}"]`)
      if (!d || d.cx == null || d.cy == null || !card) continue
      const r = card.getBoundingClientRect()
      const y0 = r.top + Math.min(r.height / 2, 18)
      if (list && (y0 < list.top || y0 > list.bottom)) continue
      const x0 = r.right - box.left, y = y0 - box.top
      const p = map.project([d.cx, d.cy])
      if (p.x < x0) continue
      const mx = (x0 + p.x) / 2
      out.push({ d: `M${x0},${y} C${mx},${y} ${mx},${p.y} ${p.x},${p.y}`, c: WATCH_STYLE[d.items[0].key]?.c ?? '#FF5A5A' })
    }
    wlines.value = out
  })
}
function pickKpi(k: Kpi) {
  kpi.value = kpi.value === k ? null : k
  if (props.node) paintArea(props.node)
}

/** 需关注区域在地图上的标注：只在酋长国 / 市政层级、且区面图层是这些区时画 */
function paintWatch() {
  if (!map || !ready) return
  const n = props.node, w = props.watch
  const on = !!w && !!n && (n.level === 'emirate' || n.level === 'municipality')
  const list = on ? w!.districts : []
  const keys = list.map(d => d.key)
  const color = (list.length
    ? ['match', ['get', 'key'], ...list.flatMap(d => [d.key, WATCH_STYLE[d.items[0].key]?.c ?? '#FF5A5A']), '#FF5A5A']
    : '#FF5A5A') as unknown as ExpressionSpecification
  for (const id of ['watch-glow', 'watch-line']) {
    map.setFilter(id, ['in', ['get', 'key'], ['literal', keys]])
    map.setPaintProperty(id, 'line-color', color)
  }
  map.setFilter('watch-hot', ['==', ['get', 'key'], on && props.hot ? props.hot : '__none__'])
}

/** 单元按户型着色；楼层 / 单元层级时选中单元提亮 */
function paintUnits(fc: GeoJSON.FeatureCollection, n: NodeDetail) {
  if (!map) return
  const cls = [...new Set(fc.features.map(f => String(f.properties?.c)))]
  const color: ExpressionSpecification = cls.length
    ? ['match', ['get', 'c'], ...cls.flatMap(c => [c, colorOf(c)]), '#8C929B'] as unknown as ExpressionSpecification
    : ['literal', '#8C929B'] as unknown as ExpressionSpecification
  const sel = n.level === 'unit' ? n.key : null
  map.setPaintProperty('unit-ext', 'fill-extrusion-color', ['case',
    ['boolean', ['feature-state', 'hover'], false], '#FFFFFF',
    ['==', ['get', 'key'], sel ?? '__none__'], '#FFFFFF', color])
  unitLegend.value = { title: '单元', items: cls.map(c => ({ n: c, c: colorOf(c) })) }
}

/** 区域 / 社区名称标签（HTML，点击下钻）；过多时不画 */
function labels(fc: GeoJSON.FeatureCollection) {
  if (!map || fc.features.length > 260) return
  for (const f of fc.features) {
    const p = f.properties as { key: string; name: string; level: Level; cx: number; cy: number } | null
    if (!p || p.cx == null) continue
    const div = document.createElement('div')
    div.className = 'dl'
    const span = document.createElement('span')
    span.textContent = p.name
    div.append(span, document.createElement('i'))
    div.onclick = e => { e.stopPropagation(); emit('pick', p.level, p.key) }
    markers.push(new maplibregl.Marker({ element: div, anchor: 'bottom' }).setLngLat([p.cx, p.cy]).addTo(map))
  }
}

function fit(n: NodeDetail) {
  if (!map || !n.bbox) return
  const deep = ['community', 'plot', 'building', 'floor', 'unit'].includes(n.level)
  const pitch = three.value && deep ? (n.level === 'community' ? 45 : 55) : 0
  const maxZoom = n.level === 'unit' || n.level === 'floor' ? 20 : n.level === 'building' ? 19.5 : n.level === 'plot' ? 18 : 17
  map.fitBounds([[n.bbox[0], n.bbox[1]], [n.bbox[2], n.bbox[3]]], {
    padding: { top: 90, bottom: 60, left: 60, right: panelPx() + 60 }, pitch, maxZoom, duration: 900,
  })
}

const PICKABLE = ['near-pt', 'fac-pt', 'bus-pt', 'light-pt', 'unit-ext', 'flr-ext', 'bld-ext', 'site-fill', 'plot-fill', 'mp-fill', 'area-fill']
const SRC_OF: Record<string, Src> = { 'unit-ext': 'unit', 'flr-ext': 'flr', 'bld-ext': 'bld', 'site-fill': 'site', 'plot-fill': 'plot', 'mp-fill': 'mp', 'area-fill': 'area', 'bus-pt': 'bus', 'light-pt': 'light', 'fac-pt': 'fac', 'near-pt': 'near' }
/** 悬停提示里非下钻对象的类型名 */
const EXTRA_NAME: Record<string, string> = { masterplan: '总体规划', site: '在建工地', busstop: '公交站台', streetlight: '路灯', facility: '设施' }

function topFeature(e: maplibregl.MapMouseEvent) {
  if (!map) return null
  const fs = map.queryRenderedFeatures(e.point, { layers: PICKABLE.filter(l => map!.getLayer(l)) })
  // 楼层半透明体块只在没有单元时才可点
  return fs.find(f => f.layer.id !== 'flr-ext') ?? fs[0] ?? null
}

function setHover(f: maplibregl.MapGeoJSONFeature | null) {
  if (!map) return
  if (hovered) map.setFeatureState({ source: hovered.src, id: hovered.id }, { hover: false })
  hovered = f && f.id != null ? { src: SRC_OF[f.layer.id], id: f.id as number } : null
  if (hovered) map.setFeatureState({ source: hovered.src, id: hovered.id }, { hover: true })
}

onMounted(() => {
  map = new maplibregl.Map({
    container: el.value!, style: STYLE, center: [54.37, 24.45], zoom: 8, maxPitch: 75, attributionControl: { compact: true },
  })
  if (import.meta.env.DEV) (window as unknown as { __map: MlMap }).__map = map
  // 容器尺寸变化（含样式晚于地图初始化加载）时重算画布
  resizeObs = new ResizeObserver(() => map?.resize())
  resizeObs.observe(el.value!)
  map.on('load', () => {
    map!.resize()
    addLayers(map!)
    ready = true
    paintWatch()
    if (props.node) show(props.node)
  })
  map.on('move', drawWatchLines)
  document.addEventListener('scroll', drawWatchLines, true)
  window.addEventListener('resize', drawWatchLines)
  map.on('mousemove', e => {
    const f = topFeature(e)
    setHover(f)
    map!.getCanvas().style.cursor = f ? 'pointer' : ''
    const p = f?.properties as { name?: string; level?: Level } | undefined
    tip.value = f && p?.name ? { x: e.point.x, y: e.point.y, name: p.name, sub: p.level ? (LEVEL_NAME[p.level] ?? EXTRA_NAME[p.level] ?? '') : '' } : null
  })
  map.on('mouseout', () => { setHover(null); tip.value = null })
  map.on('click', e => {
    const f = topFeature(e)
    const p = f?.properties as { key?: string; level?: Level } | undefined
    // 总规、工地不是下钻层级，只做提示不跳转
    if (p?.key && p.level && LEVELS.includes(p.level) && !(p.level === props.node?.level && p.key === props.node?.key)) emit('pick', p.level, p.key)
  })
})

onBeforeUnmount(() => {
  ctrl?.abort(); resizeObs?.disconnect()
  document.removeEventListener('scroll', drawWatchLines, true)
  window.removeEventListener('resize', drawWatchLines)
  map?.remove(); map = null
})

watch(() => props.node, n => { if (n) show(n) })
watch(() => [props.watch, props.hot, props.node], () => paintWatch())
watch(() => [props.watch, props.watchShown, props.node], () => paintWatchMarkers())
// 地块及以下打开设施页签时把镜头拉到 1 km 范围圈（原型勾选设施后的做法）
watch(() => props.themeTab, (t, old) => { if (props.node) { showTheme(props.node); paintArea(props.node); showNear(t === 'ast' && old !== 'ast' && nearLevel.value) } })
watch(plotMode, () => paintPlots())
watch(() => props.focus, f => { if (f && map) map.flyTo({ center: [f.lon, f.lat], zoom: Math.max(map.getZoom(), 15), duration: 900 }) })
watch(base, b => {
  map?.setLayoutProperty('sat', 'visibility', b === 'sat' ? 'visible' : 'none')
  map?.setLayoutProperty('dark', 'visibility', b === 'dark' ? 'visible' : 'none')
})
watch(three, () => { if (props.node) fit(props.node) })

const zoom = (d: number) => map?.easeTo({ zoom: map.getZoom() + d, duration: 300 })
const recentre = () => props.node && fit(props.node)
const hasLegend = computed(() => legend.value && legend.value.items.length)
</script>

<template>
  <div ref="el" class="map" />
  <div v-if="tip" class="tip" :style="{ left: tip.x + 'px', top: tip.y + 'px' }">{{ tip.name }}<small>{{ tip.sub }}</small></div>
  <svg v-if="wlines.length" class="wlines" aria-hidden="true">
    <path v-for="(l, i) in wlines" :key="i" :d="l.d" :style="{ stroke: l.c }" />
  </svg>
  <div v-if="hasLegend || poiLegend" class="legend">
    <template v-if="hasLegend">
      <b>{{ legend!.title }}</b>
      <span v-for="it in legend!.items" :key="it.n"><i class="sw" :style="{ background: it.c }" />{{ it.n }}</span>
    </template>
    <template v-if="poiLegend">
      <b>{{ poiLegend.title }}</b>
      <span v-for="it in poiLegend.items" :key="it.n"><i class="sw rd" :style="{ background: it.c }" />{{ it.n }}</span>
    </template>
  </div>
  <nav class="tools" aria-label="地图工具">
    <div class="grp">
      <button :class="{ on: menu }" data-tip="图层" aria-label="图层" @click="menu = !menu">
        <svg viewBox="0 0 24 24"><path d="M12 3.5 21 8l-9 4.5L3 8z" /><path d="M3.6 12 12 16.2 20.4 12M3.6 16 12 20.2 20.4 16" /></svg>
      </button>
      <button data-tip="定位到当前" aria-label="定位到当前" @click="recentre">
        <svg viewBox="0 0 24 24"><path d="M12 21s6.5-5.6 6.5-10.6a6.5 6.5 0 0 0-13 0C5.5 15.4 12 21 12 21z" /><circle cx="12" cy="10.3" r="2.3" /></svg>
      </button>
      <button class="txt" :data-tip="base === 'sat' ? '切换深色底图' : '切换卫星影像'" @click="base = base === 'sat' ? 'dark' : 'sat'">{{ base === 'sat' ? '影像' : '深色' }}</button>
    </div>
    <div class="grp">
      <button class="txt" :data-tip="lang === 'en' ? '切换到中文' : 'Switch to English'" :aria-label="lang === 'en' ? '中文' : 'English'" @click="setLang(lang === 'en' ? 'zh' : 'en')">{{ lang === 'en' ? '中' : 'EN' }}</button>
      <button class="txt" :class="{ on: three }" data-tip="三维视角" @click="three = !three">3D</button>
      <button aria-label="放大" @click="zoom(1)"><svg viewBox="0 0 24 24"><path d="M12 5.5v13M5.5 12h13" /></svg></button>
      <button aria-label="缩小" @click="zoom(-1)"><svg viewBox="0 0 24 24"><path d="M5.5 12h13" /></svg></button>
    </div>
  </nav>
  <div v-if="menu" class="lmenu" role="dialog" aria-label="图层">
    <header><b>图层</b><button aria-label="关闭" @click="menu = false"><svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6 6 18" /></svg></button></header>
    <div class="sec">底图</div>
    <div class="opts">
      <button :class="{ on: base === 'sat' }" @click="base = 'sat'">卫星影像</button>
      <button :class="{ on: base === 'dark' }" @click="base = 'dark'">深色</button>
    </div>
    <template v-if="hasPlots">
      <div class="sec">地块着色</div>
      <div class="opts">
        <button :class="{ on: plotMode === 'status' }" @click="plotMode = 'status'">建设状态</button>
        <button :class="{ on: plotMode === 'use' }" @click="plotMode = 'use'">土地用途</button>
      </div>
    </template>
    <template v-if="hasPoi">
      <div class="sec">设施点位<small v-if="themeTab === 'ast'">设施页签打开时全部显示</small></div>
      <div class="opts wrap">
        <button v-for="x in POI" :key="x.k" :class="{ on: poi.includes(x.k) }" @click="togglePoi(x.k)"><i class="sw rd" :style="{ background: x.c }" />{{ x.n }}</button>
      </div>
    </template>
    <template v-if="kpiOptions.length">
      <div class="sec">指标图层<small>{{ lv === 'district' ? '社区面' : '区域面' }}</small></div>
      <div class="opts wrap">
        <button v-for="x in kpiOptions" :key="x.k" :class="{ on: kpi === x.k }" @click="pickKpi(x.k)">{{ x.n }}</button>
        <button v-for="x in KPI_PENDING" :key="x" disabled :title="x + '：源数据待接入'">{{ x }}</button>
      </div>
      <p v-if="kpi === 'built' && themeTab === 'liv' && lv !== 'district'" class="hint">宜居页签打开时自动按宜居分着色</p>
    </template>
  </div>
</template>

<style scoped>
.lmenu { position: absolute; right: calc(var(--pw) + 4.4rem); bottom: 1.2rem; z-index: 7; width: 17.5rem; padding: .2rem .9rem .9rem; border-radius: var(--r);
  background: rgba(30, 31, 35, .97); border: 1px solid var(--line2); box-shadow: 0 .6rem 2rem rgba(0, 0, 0, .45); font-size: .8rem }
.lmenu header { display: flex; align-items: center; justify-content: space-between; padding: .5rem 0 .2rem }
.lmenu header b { font-size: .9rem }
.lmenu header button { width: 1.8rem; height: 1.8rem; display: grid; place-items: center; border-radius: .3rem; color: var(--tx2) }
.lmenu header button:hover { background: rgba(255, 255, 255, .1); color: #fff }
.lmenu header svg { width: 1rem; height: 1rem; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round }
.sec { margin: .7rem 0 .35rem; font-size: .7rem; font-weight: 700; color: var(--tx3); letter-spacing: .04em; display: flex; justify-content: space-between; gap: .5rem }
.sec small { font-weight: 500; letter-spacing: 0 }
.opts { display: flex; gap: .3rem }
.opts.wrap { flex-wrap: wrap }
.opts button { display: inline-flex; align-items: center; gap: .35rem; padding: .32rem .6rem; border-radius: .3rem; border: 1px solid var(--line2); font-size: .76rem; font-weight: 600; color: var(--tx); white-space: nowrap }
.opts button:hover:not(:disabled) { background: rgba(255, 255, 255, .08) }
.opts button.on { background: #4C8DF6; border-color: #4C8DF6; color: #fff }
.opts button:disabled { opacity: .4; cursor: not-allowed }
.sw.rd { border-radius: 50% }
.hint { margin-top: .45rem; font-size: .7rem; color: var(--tx3) }
.legend .sw.rd { border-radius: 50% }
.wlines { position: absolute; inset: 0; z-index: 4; width: 100%; height: 100%; pointer-events: none }
.wlines path { fill: none; stroke-width: 1.4; stroke-opacity: .75; stroke-dasharray: 4 3 }
</style>
