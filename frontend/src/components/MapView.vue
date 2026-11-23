<script setup lang="ts">
import maplibregl, { type ExpressionSpecification, type GeoJSONSource, type LngLatBoundsLike, type Map as MlMap } from 'maplibre-gl'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../api/client'
import type { DistrictFeatureCollection, DistrictFeatureProps, FacilityItem } from '../api/types'
import { useDistrictStore } from '../stores/district'
import { escapeHtml, nf } from '../utils/format'
import DetailCard from './DetailCard.vue'

/**
 * 在线底图（MapLibre，默认 CARTO dark-matter）+ makani 全部区的边界与名称标签 + 建筑 MVT 3D 拉伸。
 * 区名用文字图层（自动避让重叠）；选中区：边界光墙、建筑提亮、区外压暗、卡片式标签、飞行定位。
 */
const props = defineProps<{ panelWidth: number; listWidth: number }>()

const store = useDistrictStore()
const { t } = useI18n()
const el = ref<HTMLDivElement>()
const tip = ref<{ x: number; y: number; html: string } | null>(null)
let map: MlMap | null = null
let ready = false
let hoverId: number | null = null
let selectedId: number | null = null
let selectedMarker: maplibregl.Marker | null = null
/** 详情卡片的屏幕位置（锚点经纬度投影），随地图移动更新 */
const card = ref<{ x: number; y: number; below: boolean; shift: number } | null>(null)
/** 卡片半宽 + 边距（卡片宽 300px） */
const CARD_HALF = 162

const STYLE = import.meta.env.VITE_BASEMAP_STYLE || 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json'
/** 「全景」视角：默认阿布扎比岛及周边；可用 VITE_HOME_BOUNDS=minLon,minLat,maxLon,maxLat 覆盖 */
const HOME: LngLatBoundsLike = (() => {
  const v = (import.meta.env.VITE_HOME_BOUNDS || '54.30,24.28,54.66,24.62').split(',').map(Number)
  return [[v[0], v[1]], [v[2], v[3]]]
})()
const LABEL_FONT = ['Montserrat SemiBold', 'Open Sans Bold', 'Noto Sans Regular', 'Noto Sans CJK JP Regular']
const NONE = '__none__'
/** 地块按建设状态着色，与面板「地块状态」图例颜色一致 */
const PLOT_COLOR: ExpressionSpecification = ['match', ['get', 'status'],
  'constructed', '#C9D3DE',
  'under_construction', '#FFB547',
  'vacant_serviced', '#7FB7F0',
  'vacant_unserviced', '#6B7785',
  '#9AA6B2']
const POI_COLOR: ExpressionSpecification = ['match', ['get', 'category'], 'school', '#E8C77A', 'medical', '#FF8A7A', 'mosque', '#5FE0A0', '#CFE5FA']

function poiCollection(list: FacilityItem[]): GeoJSON.FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: list.map(f => ({
      type: 'Feature',
      properties: { id: f.id, category: f.category, name: f.nameEn || f.nameAr || f.id },
      geometry: { type: 'Point', coordinates: f.location },
    })),
  }
}

/** 列表悬停 / 定位的设施放大显示 */
function applyPoiEmphasis() {
  if (!map || !map.getLayer('pois')) return
  const hot = ['any', ['==', ['get', 'id'], store.focusedPoiId ?? NONE], ['==', ['get', 'id'], store.hoveredPoiId ?? NONE]]
  map.setPaintProperty('pois', 'circle-radius', ['case', hot, 9, 5.5])
  map.setPaintProperty('pois', 'circle-stroke-width', ['case', hot, 3, 1.5])
}

/** 定位到某个设施：飞过去；名称和详情由详情卡片显示（卡片在 DOM 上层，不会被 3D 建筑挡住） */
function focusPoi(id: string | null) {
  const f = id ? store.facilityList.find(x => x.id === id) : undefined
  if (!map || !f) return
  map.flyTo({ center: f.location, zoom: Math.max(map.getZoom(), 16.5), pitch: 55, duration: 1100 })
}

/** 详情卡片锚点：点开的地块 / 建筑的点击位置，或定位到的设施 */
const cardAnchor = computed<[number, number] | null>(() => {
  if (store.featureDetail) return store.featureDetail.lngLat
  const f = store.focusedPoiId ? store.facilityList.find(x => x.id === store.focusedPoiId) : undefined
  return f ? f.location : null
})

function updateCard() {
  const a = cardAnchor.value
  if (!map || !a) {
    card.value = null
    return
  }
  const p = map.project(a)
  const w = map.getCanvas().clientWidth
  const h = map.getCanvas().clientHeight
  // 锚点移出视野就先不显示
  if (p.x < -20 || p.y < -20 || p.x > w + 20 || p.y > h + 20) {
    card.value = null
    return
  }
  // 卡片左右不压到区列表和右侧面板：卡片整体平移，连接线和定位点仍在锚点
  const minX = props.listWidth + 24 + CARD_HALF
  const maxX = w - (store.selectedCode ? props.panelWidth : 0) - 12 - CARD_HALF
  const cx = maxX > minX ? Math.min(Math.max(p.x, minX), maxX) : p.x
  card.value = { x: p.x, y: p.y, below: p.y < Math.min(430, h * 0.55), shift: cx - p.x }
}

/** 点开详情的地块 / 建筑高亮 */
function applyFeatureHighlight() {
  if (!map || !map.getLayer('plots-selected')) return
  const d = store.featureDetail
  map.setFilter('plots-selected', ['==', ['get', 'id'], d?.kind === 'plot' ? d.id : -1])
  map.setPaintProperty('buildings-3d', 'fill-extrusion-color', buildingColor(store.selectedCode))
}

const PLOT_STATUS_KEY: Record<string, string> = {
  constructed: 'constructed', under_construction: 'underConstruction',
  vacant_serviced: 'vacantServiced', vacant_unserviced: 'vacantUnserviced', other: 'other',
}

function padding() {
  const open = !!store.selectedCode
  return { top: 120, bottom: 80, left: props.listWidth + 60, right: open ? props.panelWidth + 40 : 40 }
}

function flyHome(animate = true) {
  map?.fitBounds(HOME, { padding: padding(), pitch: 45, bearing: -12, duration: animate ? 1200 : 0 })
}

function flyTo(d: DistrictFeatureProps) {
  map?.fitBounds([[d.bbox[0], d.bbox[1]], [d.bbox[2], d.bbox[3]]], {
    padding: padding(), pitch: 58, bearing: -16, duration: 1300, maxZoom: 15.5,
  })
}

function buildingColor(code: string | null): ExpressionSpecification {
  if (!code) return ['case', ['get', 'uc'], '#8A6A3A', '#5B6878']
  const d = store.featureDetail
  const sel = d?.kind === 'building' ? d.id : -1
  return ['case',
    ['==', ['get', 'id'], sel], '#7CF3D0',
    ['==', ['get', 'code'], code], ['case', ['get', 'uc'], '#FFB547', '#A9D0F7'],
    '#3A4553']
}

function points(fc: DistrictFeatureCollection): GeoJSON.FeatureCollection {
  return {
    type: 'FeatureCollection',
    features: fc.features.map(f => ({
      type: 'Feature',
      id: f.id,
      properties: { code: f.properties.code, name: f.properties.nameEn, area: f.properties.areaKm2 ?? 0 },
      geometry: { type: 'Point', coordinates: f.properties.centroid },
    })),
  }
}

function idOf(code: string | null): number | null {
  return code ? store.features.get(code)?.sortNo ?? null : null
}

function applySelection() {
  if (!map || !map.getSource('districts')) return
  const code = store.selectedCode
  if (selectedId !== null) map.setFeatureState({ source: 'districts', id: selectedId }, { selected: false })
  selectedId = idOf(code)
  if (selectedId !== null) map.setFeatureState({ source: 'districts', id: selectedId }, { selected: true })
  map.setPaintProperty('districts-dim', 'fill-opacity', code ? ['case', ['boolean', ['feature-state', 'selected'], false], 0, 0.55] : 0)
  map.setFilter('districts-glow', ['==', ['get', 'code'], code ?? NONE])
  map.setFilter('districts-edge-selected', ['==', ['get', 'code'], code ?? NONE])
  // 选中区的名字由卡片标签显示，文字图层里隐藏它；其他区在选中状态下变暗
  map.setFilter('district-labels', ['!=', ['get', 'code'], code ?? NONE])
  map.setPaintProperty('district-labels', 'text-opacity', code ? 0.45 : 0.9)
  if (map.getLayer('buildings-3d')) map.setPaintProperty('buildings-3d', 'fill-extrusion-color', buildingColor(code))
  // 只显示选中区的地块
  map.setFilter('plots-fill', ['==', ['get', 'code'], code ?? NONE])
  map.setFilter('plots-line', ['==', ['get', 'code'], code ?? NONE])
  applyPlotHighlight()
  renderSelectedMarker()
}

/** 面板图例悬停：该状态的地块加亮，其他地块变淡 */
function applyPlotHighlight() {
  if (!map || !map.getLayer('plots-fill')) return
  const hl = store.highlightPlotStatus
  map.setPaintProperty('plots-fill', 'fill-opacity', hl ? ['case', ['==', ['get', 'status'], hl], 0.85, 0.06] : 0.42)
  map.setPaintProperty('plots-line', 'line-opacity', hl ? ['case', ['==', ['get', 'status'], hl], 1, 0.15] : 0.7)
  map.setPaintProperty('plots-line', 'line-width', hl ? ['case', ['==', ['get', 'status'], hl], 2, 0.6] : 0.8)
}

function plotTipHtml(p: Record<string, unknown>): string {
  const key = PLOT_STATUS_KEY[String(p.status)] ?? 'other'
  return `<b>${t('land.plot')} ${escapeHtml(String(p.plot_id ?? ''))}</b>${t('land.ps.' + key)} · ${escapeHtml(String(p.use_desc ?? ''))}`
    + `<br><span class="hint">${t('detail.hint')}</span>`
}

function buildingTipHtml(p: Record<string, unknown>): string {
  const h = Number(p.height)
  const uc = p.uc === true || p.uc === 'true'
  return `<b>${t('detail.buildingTip')}</b>${Number.isFinite(h) ? nf(h, 0) + ' m' : ''}${uc ? ' · ' + t('land.uc') : ''}`
    + `<br><span class="hint">${t('detail.hint')}</span>`
}

/** 选中区内点击：设施点 > 建筑 > 地块（返回是否命中，命中时由详情卡片接管） */
function onMapClick(e: maplibregl.MapMouseEvent) {
  if (!map || !store.selectedCode) return
  const code = store.selectedCode
  const layers = ['pois', 'buildings-3d', 'plots-fill'].filter(l => map!.getLayer(l))
  const hits = map.queryRenderedFeatures(e.point, { layers })
  const poi = hits.find(f => f.layer.id === 'pois')
  if (poi) {
    const id = poi.properties?.id
    if (id != null) store.focusPoi(String(id))
    return
  }
  const lngLat: [number, number] = [e.lngLat.lng, e.lngLat.lat]
  const building = hits.find(f => f.layer.id === 'buildings-3d' && f.properties?.code === code)
  if (building && building.properties?.id != null) {
    void store.openFeature('building', Number(building.properties.id), lngLat)
    return
  }
  const plot = hits.find(f => f.layer.id === 'plots-fill')
  if (plot && plot.properties?.id != null) {
    void store.openFeature('plot', Number(plot.properties.id), lngLat)
    return
  }
  // 点在选中区内的空白处：关闭详情；点到别的区由区图层的点击处理切换
  if (hits.length === 0) store.closeDetail()
}

function applyHover(code: string | null) {
  if (!map || !map.getSource('districts')) return
  if (hoverId !== null) map.setFeatureState({ source: 'districts', id: hoverId }, { hover: false })
  hoverId = idOf(code)
  if (hoverId !== null) map.setFeatureState({ source: 'districts', id: hoverId }, { hover: true })
  map.setPaintProperty('district-labels', 'text-color', ['case', ['==', ['get', 'code'], code ?? NONE], '#CFE5FA', 'rgba(255,255,255,0.86)'])
}

function tipHtml(d: DistrictFeatureProps): string {
  const lv = d.livInScope === false
    ? `${t('tip.liveability')} ${t('tip.outOfScope')}`
    : `${t('tip.liveability')} ${d.liveabilityScore != null ? nf(d.liveabilityScore, 2) : 'NaN'}`
  const area = d.areaKm2 != null ? `${nf(d.areaKm2, 2)} km² · ` : ''
  return `<b>${escapeHtml(d.nameEn)}</b>${area}${lv}<br>${d.code === store.selectedCode ? t('tip.selected') : t('tip.click')}`
}

/** 选中区的卡片式标签（带面积、宜居得分，可悬浮查看指标来源） */
function renderSelectedMarker() {
  selectedMarker?.remove()
  selectedMarker = null
  const d = store.selected
  if (!map || !d) return
  const code = escapeHtml(d.code)
  const lv = d.livInScope === false
    ? `<span data-m="liv_in_scope" data-code="${code}">${t('tip.liveability')} ${t('tip.outOfScope')}</span>`
    : d.liveabilityScore != null
    ? `<span data-m="liv_current" data-code="${code}">${t('tip.liveability')} <b>${nf(d.liveabilityScore, 2)}</b></span>`
    : `<span data-m="liv_current" data-code="${code}">${t('tip.liveability')} <b>NaN</b></span>`
  const area = d.areaKm2 != null ? `<span data-m="area_km2" data-code="${code}"><b>${nf(d.areaKm2, 2)}</b> km²</span> · ` : ''
  const div = document.createElement('div')
  div.className = 'dlb on'
  div.innerHTML = `${escapeHtml(d.nameEn)}<div class="m">${area}${lv}</div>`
  selectedMarker = new maplibregl.Marker({ element: div, anchor: 'bottom' }).setLngLat(d.centroid).addTo(map)
}

function addLayers(fc: DistrictFeatureCollection) {
  if (!map) return
  map.addSource('districts', { type: 'geojson', data: fc as unknown as GeoJSON.FeatureCollection })
  map.addSource('district-points', { type: 'geojson', data: points(fc) })
  map.addSource('plots', {
    type: 'vector', tiles: [api.plotTilesUrl(store.dataVersion)], minzoom: 10, maxzoom: 16, attribution: 'Plots © makani',
  })
  map.addSource('buildings', {
    type: 'vector', tiles: [api.buildingTilesUrl(store.dataVersion)], minzoom: 12, maxzoom: 16, attribution: 'Buildings © makani',
  })

  map.addLayer({
    id: 'districts-hover', type: 'fill', source: 'districts',
    paint: { 'fill-color': '#7FB7F0', 'fill-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 0.12, 0.02] },
  })
  // 地块：放在建筑下面，只显示选中区，按建设状态着色
  map.addLayer({
    id: 'plots-fill', type: 'fill', source: 'plots', 'source-layer': 'plots', filter: ['==', ['get', 'code'], NONE],
    paint: { 'fill-color': PLOT_COLOR, 'fill-opacity': 0.42 },
  })
  map.addLayer({
    id: 'plots-line', type: 'line', source: 'plots', 'source-layer': 'plots', filter: ['==', ['get', 'code'], NONE],
    paint: { 'line-color': PLOT_COLOR, 'line-width': 0.8, 'line-opacity': 0.7 },
  })
  map.addLayer({
    id: 'plots-selected', type: 'line', source: 'plots', 'source-layer': 'plots', filter: ['==', ['get', 'id'], -1],
    paint: { 'line-color': '#FFFFFF', 'line-width': 3, 'line-opacity': 0.95 },
  })
  map.addLayer({
    id: 'buildings-3d', type: 'fill-extrusion', source: 'buildings', 'source-layer': 'buildings', minzoom: 12,
    paint: {
      'fill-extrusion-color': buildingColor(null),
      'fill-extrusion-height': ['get', 'height'],
      'fill-extrusion-base': 0,
      'fill-extrusion-opacity': 0.9,
    },
  })
  map.addLayer({ id: 'districts-dim', type: 'fill', source: 'districts', paint: { 'fill-color': '#060A10', 'fill-opacity': 0 } })
  map.addLayer({
    id: 'districts-edge', type: 'line', source: 'districts',
    paint: { 'line-color': 'rgba(169,208,247,0.45)', 'line-width': 1.2 },
  })
  map.addLayer({
    id: 'districts-glow', type: 'line', source: 'districts', filter: ['==', ['get', 'code'], NONE],
    paint: { 'line-color': '#7FB7F0', 'line-width': 10, 'line-blur': 8, 'line-opacity': 0.75 },
  })
  map.addLayer({
    id: 'districts-edge-selected', type: 'line', source: 'districts', filter: ['==', ['get', 'code'], NONE],
    paint: { 'line-color': '#CFE5FA', 'line-width': 2.2 },
  })
  map.addLayer({
    id: 'district-labels', type: 'symbol', source: 'district-points',
    layout: {
      'text-field': ['get', 'name'],
      'text-font': LABEL_FONT,
      'text-size': ['interpolate', ['linear'], ['zoom'], 8, 10, 12, 12.5, 15, 14],
      // 面积大的区优先显示，重叠时小区的名字先隐藏
      'symbol-sort-key': ['-', 0, ['get', 'area']],
      'text-padding': 4,
      'text-max-width': 9,
    },
    paint: {
      'text-color': 'rgba(255,255,255,0.86)',
      'text-halo-color': 'rgba(0,0,0,0.85)',
      'text-halo-width': 1.4,
      'text-opacity': 0.9,
    },
  })

  // 公共设施点：面板展开某类设施列表时显示（放在最上层，避免被区名、建筑遮挡）
  map.addSource('pois', { type: 'geojson', data: poiCollection([]) })
  map.addLayer({
    id: 'pois', type: 'circle', source: 'pois',
    paint: {
      'circle-color': POI_COLOR,
      'circle-radius': 5.5,
      'circle-stroke-color': '#0B1118',
      'circle-stroke-width': 1.5,
      'circle-pitch-alignment': 'viewport',
    },
  })
  map.on('click', onMapClick)
  map.on('mouseenter', 'pois', e => {
    const id = e.features?.[0]?.properties?.id
    store.hoveredPoiId = id != null ? String(id) : null
    map!.getCanvas().style.cursor = 'pointer'
  })
  map.on('mouseleave', 'pois', () => {
    store.hoveredPoiId = null
    map!.getCanvas().style.cursor = ''
  })

  const onHover = (code: string | undefined, point: maplibregl.Point) => {
    store.hoveredCode = code ?? null
    const d = code ? store.features.get(code) : undefined
    // 选中区内悬停到建筑 / 地块上：建筑显示高度，地块显示编号、建设状态、用途
    const inner = store.selectedCode ? map!.queryRenderedFeatures(point, { layers: ['buildings-3d', 'plots-fill'] }) : []
    const bld = inner.find(f => f.layer.id === 'buildings-3d' && f.properties?.code === store.selectedCode)
    if (bld) {
      tip.value = { x: point.x, y: point.y, html: buildingTipHtml(bld.properties ?? {}) }
      map!.getCanvas().style.cursor = 'pointer'
      return
    }
    const plot = inner.find(f => f.layer.id === 'plots-fill')
    if (plot) {
      tip.value = { x: point.x, y: point.y, html: plotTipHtml(plot.properties ?? {}) }
      map!.getCanvas().style.cursor = 'pointer'
      return
    }
    tip.value = d ? { x: point.x, y: point.y, html: tipHtml(d) } : null
    map!.getCanvas().style.cursor = d ? 'pointer' : ''
  }
  const onLeave = () => {
    store.hoveredCode = null
    tip.value = null
    map!.getCanvas().style.cursor = ''
  }
  for (const layer of ['districts-hover', 'district-labels']) {
    map.on('mousemove', layer, e => onHover(e.features?.[0]?.properties?.code as string | undefined, e.point))
    map.on('mouseleave', layer, onLeave)
    map.on('click', layer, e => {
      const code = e.features?.[0]?.properties?.code as string | undefined
      if (code) void store.select(code)
    })
  }

  // GeoJSON 解析完成后才能设置 feature-state
  map.on('sourcedata', e => {
    if (e.sourceId === 'districts' && e.isSourceLoaded) {
      applySelection()
      applyHover(store.hoveredCode)
    }
  })
}

function init() {
  if (!map || !ready || !store.boundaries || map.getSource('districts')) return
  addLayers(store.boundaries)
  if (store.selected) flyTo(store.selected)
  else flyHome(false)
}

onMounted(() => {
  map = new maplibregl.Map({
    container: el.value!,
    style: STYLE,
    bounds: HOME,
    pitch: 45,
    bearing: -12,
    maxPitch: 70,
    attributionControl: { compact: true },
  })
  map.on('load', () => {
    ready = true
    init()
  })
  map.on('move', updateCard)
  map.on('resize', updateCard)
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  map?.remove()
})

/** Esc：先关详情卡片，再取消选中区 */
function onKey(e: KeyboardEvent) {
  if (e.key !== 'Escape') return
  if (store.closeDetail()) return
  if (store.selectedCode) store.deselect()
}

watch(() => store.boundaries, fc => {
  if (!fc || !map || !ready) return
  if (!map.getSource('districts')) init()
  else {
    ;(map.getSource('districts') as GeoJSONSource).setData(fc as unknown as GeoJSON.FeatureCollection)
    ;(map.getSource('district-points') as GeoJSONSource).setData(points(fc))
  }
})

watch(() => store.selectedCode, () => {
  applySelection()
  tip.value = null
  if (store.selected) flyTo(store.selected)
})

watch(() => store.hoveredCode, code => applyHover(code))
watch(() => store.highlightPlotStatus, () => applyPlotHighlight())
watch(() => store.facilityList, list => {
  ;(map?.getSource('pois') as GeoJSONSource | undefined)?.setData(poiCollection(list))
})
watch(() => store.focusedPoiId, id => {
  focusPoi(id)
  applyPoiEmphasis()
})
watch(cardAnchor, () => updateCard())
watch(() => [store.featureDetail?.kind, store.featureDetail?.id], () => applyFeatureHighlight())
watch(() => store.hoveredPoiId, () => applyPoiEmphasis())
watch(() => store.homeTick, () => flyHome())
watch(() => [props.panelWidth, props.listWidth, store.selectedCode], () => updateCard())
watch(() => store.lang, () => renderSelectedMarker())
</script>

<template>
  <div ref="el" class="map"></div>
  <DetailCard v-if="card" :x="card.x" :y="card.y" :below="card.below" :shift="card.shift" />
  <div v-if="tip" class="tip" :style="{ left: tip.x + 14 + 'px', top: tip.y + 14 + 'px' }" v-html="tip.html"></div>
</template>

<style scoped>
.map {
  position: fixed;
  inset: 0;
}
.tip {
  position: fixed;
  pointer-events: none;
  padding: 8px 11px;
  border-radius: 9px;
  background: rgba(10, 16, 24, 0.94);
  box-shadow: inset 0 0 0 1px var(--line), 0 6px 20px rgba(0, 0, 0, 0.4);
  font-size: 12.5px;
  color: var(--tm);
  z-index: 6;
  white-space: nowrap;
}
.tip :deep(.hint) {
  font-size: 11.5px;
  color: var(--tq);
}
.tip :deep(b) {
  display: block;
  color: #fff;
  font-size: 13.5px;
  font-weight: 700;
  margin-bottom: 2px;
}
</style>
