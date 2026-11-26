<script setup lang="ts">
import maplibregl, { type ExpressionSpecification, type GeoJSONSource, type Map as MlMap, type StyleSpecification } from 'maplibre-gl'
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { api, LEVELS, type Layer, type Level, type NodeDetail, type NodeRef } from '../api'
import { colorOf, LEVEL_NAME, PLOT_STATUS } from '../labels'

/**
 * 地图：按当前层级加载对应图层，点击要素下钻。
 *   酋长国 / 市政：区域面（按建成率着色）+ 区名标签
 *   区域：社区面 + 社区名标签 + 地块（建设状态色）+ 建筑 3D
 *   社区：地块 + 建筑 3D；地块：所在社区的地块（背景）+ 本地块建筑
 *   建筑：楼层半透明分层堆叠 + 单元按户型着色（3D，底标高 / 层高）
 *   楼层 / 单元：本层单元 + 整栋楼层轮廓；选中对象用白色描边
 */
const props = defineProps<{ node: NodeDetail | null; themeTab?: string; focus?: { lon: number; lat: number; t: number } | null }>()
const emit = defineEmits<{ pick: [level: Level, key: string] }>()

const el = ref<HTMLDivElement>()
const tip = ref<{ x: number; y: number; name: string; sub: string } | null>(null)
const base = ref<'sat' | 'dark'>('sat')
const three = ref(true)
const legend = shallowRef<{ title: string; items: { n: string; c: string }[] } | null>(null)
let map: MlMap | null = null
let ready = false
let ctrl: AbortController | null = null
let markers: maplibregl.Marker[] = []
let hovered: { src: string; id: number } | null = null
let resizeObs: ResizeObserver | null = null

const EMPTY: GeoJSON.FeatureCollection = { type: 'FeatureCollection', features: [] }
const SOURCES = ['area', 'plot', 'bld', 'flr', 'unit', 'sel', 'mp', 'site', 'bus', 'light'] as const
/** 主题页签叠加的数据源：页签 → [数据源, 图层] */
const THEME_LAYERS: Record<string, [Src, Layer][]> = {
  dev: [['mp', 'masterplans'], ['site', 'sites']],
  ops: [['bus', 'busstops'], ['light', 'streetlights']],
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

const PLOT_COLOR: ExpressionSpecification = ['match', ['get', 's'],
  ...Object.entries(PLOT_STATUS).flatMap(([k, v]) => [k, v.c]), '#6B7280'] as unknown as ExpressionSpecification

function addLayers(m: MlMap) {
  SOURCES.forEach(s => m.addSource(s, { type: 'geojson', data: EMPTY, generateId: true }))
  // 区域 / 社区 / 市政面：按建成率着色，悬停提亮
  m.addLayer({ id: 'area-fill', type: 'fill', source: 'area', paint: {
    'fill-color': ['interpolate', ['linear'], ['coalesce', ['get', 'built'], 0], 0, '#2B4C7E', 50, '#3D6DF2', 100, '#8FE3FF'],
    'fill-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 0.42, 0.2] } })
  m.addLayer({ id: 'area-line', type: 'line', source: 'area', paint: { 'line-color': '#9CC4FF', 'line-width': 1, 'line-opacity': 0.75 } })
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
  m.addLayer({ id: 'bus-pt', type: 'circle', source: 'bus', paint: {
    'circle-color': '#4C8DF6', 'circle-stroke-color': '#FFFFFF', 'circle-stroke-width': 1.2,
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
  legend.value = null
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
      const seen = new Set(fc.features.map(f => String(f.properties?.s)))
      legend.value = { title: '地块', items: Object.entries(PLOT_STATUS).filter(([k]) => seen.has(k)).map(([, v]) => v) }
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
  for (const [src, layer] of Object.values(THEME_LAYERS).flat()) {
    const on = active.has(layer) && n.layers.includes(layer)
    if (!on) { (m.getSource(src) as GeoJSONSource).setData(EMPTY); continue }
    api.layer(n.level, n.key, layer, sig).then(fc => { if (!sig.aborted) (m.getSource(src) as GeoJSONSource).setData(fc) }).catch(() => undefined)
  }
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
  legend.value = { title: '单元', items: cls.map(c => ({ n: c, c: colorOf(c) })) }
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

const PICKABLE = ['bus-pt', 'light-pt', 'unit-ext', 'flr-ext', 'bld-ext', 'site-fill', 'plot-fill', 'mp-fill', 'area-fill']
const SRC_OF: Record<string, Src> = { 'unit-ext': 'unit', 'flr-ext': 'flr', 'bld-ext': 'bld', 'site-fill': 'site', 'plot-fill': 'plot', 'mp-fill': 'mp', 'area-fill': 'area', 'bus-pt': 'bus', 'light-pt': 'light' }
/** 悬停提示里非下钻对象的类型名 */
const EXTRA_NAME: Record<string, string> = { masterplan: '总体规划', site: '在建工地', busstop: '公交站台', streetlight: '路灯' }

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
    if (props.node) show(props.node)
  })
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

onBeforeUnmount(() => { ctrl?.abort(); resizeObs?.disconnect(); map?.remove(); map = null })

watch(() => props.node, n => { if (n) show(n) })
watch(() => props.themeTab, () => { if (props.node) showTheme(props.node) })
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
  <div v-if="hasLegend" class="legend">
    <b>{{ legend!.title }}</b>
    <span v-for="it in legend!.items" :key="it.n"><i class="sw" :style="{ background: it.c }" />{{ it.n }}</span>
  </div>
  <nav class="tools" aria-label="地图工具">
    <div class="grp">
      <button data-tip="定位到当前" aria-label="定位到当前" @click="recentre">
        <svg viewBox="0 0 24 24"><path d="M12 21s6.5-5.6 6.5-10.6a6.5 6.5 0 0 0-13 0C5.5 15.4 12 21 12 21z" /><circle cx="12" cy="10.3" r="2.3" /></svg>
      </button>
      <button class="txt" :data-tip="base === 'sat' ? '切换深色底图' : '切换卫星影像'" @click="base = base === 'sat' ? 'dark' : 'sat'">{{ base === 'sat' ? '影像' : '深色' }}</button>
    </div>
    <div class="grp">
      <button class="txt" :class="{ on: three }" data-tip="三维视角" @click="three = !three">3D</button>
      <button aria-label="放大" @click="zoom(1)"><svg viewBox="0 0 24 24"><path d="M12 5.5v13M5.5 12h13" /></svg></button>
      <button aria-label="缩小" @click="zoom(-1)"><svg viewBox="0 0 24 24"><path d="M5.5 12h13" /></svg></button>
    </div>
  </nav>
</template>
