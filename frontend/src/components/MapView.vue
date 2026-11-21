<script setup lang="ts">
import maplibregl, { type ExpressionSpecification, type GeoJSONSource, type LngLatBoundsLike, type Map as MlMap } from 'maplibre-gl'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../api/client'
import type { DistrictFeatureCollection, DistrictFeatureProps } from '../api/types'
import { useDistrictStore } from '../stores/district'
import { escapeHtml, nf } from '../utils/format'

/**
 * 在线底图（MapLibre，默认 CARTO dark-matter）+ ADM 全部区的边界与名称标签 + 建筑 MVT 3D 拉伸。
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

const STYLE = import.meta.env.VITE_BASEMAP_STYLE || 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json'
/** 「全景」视角：默认阿布扎比岛及周边；可用 VITE_HOME_BOUNDS=minLon,minLat,maxLon,maxLat 覆盖 */
const HOME: LngLatBoundsLike = (() => {
  const v = (import.meta.env.VITE_HOME_BOUNDS || '54.30,24.28,54.66,24.62').split(',').map(Number)
  return [[v[0], v[1]], [v[2], v[3]]]
})()
const LABEL_FONT = ['Montserrat SemiBold', 'Open Sans Bold', 'Noto Sans Regular', 'Noto Sans CJK JP Regular']
const NONE = '__none__'

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
  return ['case', ['==', ['get', 'code'], code], ['case', ['get', 'uc'], '#FFB547', '#A9D0F7'], '#3A4553']
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
  renderSelectedMarker()
}

function applyHover(code: string | null) {
  if (!map || !map.getSource('districts')) return
  if (hoverId !== null) map.setFeatureState({ source: 'districts', id: hoverId }, { hover: false })
  hoverId = idOf(code)
  if (hoverId !== null) map.setFeatureState({ source: 'districts', id: hoverId }, { hover: true })
  map.setPaintProperty('district-labels', 'text-color', ['case', ['==', ['get', 'code'], code ?? NONE], '#CFE5FA', 'rgba(255,255,255,0.86)'])
}

function tipHtml(d: DistrictFeatureProps): string {
  const lv = d.liveabilityScore != null ? `${t('tip.liveability')} ${nf(d.liveabilityScore, 2)}` : t('tip.notAssessed')
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
  const lv = d.liveabilityScore != null
    ? `<span data-m="liv_current" data-code="${code}">${t('tip.liveability')} <b>${nf(d.liveabilityScore, 2)}</b></span>`
    : t('tip.notAssessed')
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
  map.addSource('buildings', {
    type: 'vector', tiles: [api.buildingTilesUrl()], minzoom: 12, maxzoom: 16, attribution: 'Buildings © makani',
  })

  map.addLayer({
    id: 'districts-hover', type: 'fill', source: 'districts',
    paint: { 'fill-color': '#7FB7F0', 'fill-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 0.12, 0.02] },
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

  const onHover = (code: string | undefined, point: maplibregl.Point) => {
    store.hoveredCode = code ?? null
    const d = code ? store.features.get(code) : undefined
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
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  map?.remove()
})

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && store.selectedCode) store.deselect()
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
watch(() => store.homeTick, () => flyHome())
watch(() => store.lang, () => renderSelectedMarker())
</script>

<template>
  <div ref="el" class="map"></div>
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
.tip :deep(b) {
  display: block;
  color: #fff;
  font-size: 13.5px;
  font-weight: 700;
  margin-bottom: 2px;
}
</style>
