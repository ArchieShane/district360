<script setup lang="ts">
import maplibregl, { type ExpressionSpecification, type GeoJSONSource, type LngLatBoundsLike, type Map as MlMap } from 'maplibre-gl'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { api } from '../api/client'
import type { DistrictListItem } from '../api/types'
import { useDistrictStore } from '../stores/district'
import { escapeHtml, nf } from '../utils/format'

/**
 * 在线底图（MapLibre，默认 CARTO dark-matter）+ 后端提供的 17 区边界 GeoJSON + 建筑 MVT 3D 拉伸。
 * 选中区：边界光墙、建筑提亮、区外压暗、飞行定位；悬停：提示框。
 */
const props = defineProps<{ panelWidth: number; listWidth: number }>()

const store = useDistrictStore()
const { t } = useI18n()
const el = ref<HTMLDivElement>()
const tip = ref<{ x: number; y: number; html: string } | null>(null)
let map: MlMap | null = null
const markers = new Map<string, { marker: maplibregl.Marker; el: HTMLDivElement }>()
let hoverId: number | null = null
let selectedId: number | null = null
let ready = false

const STYLE = import.meta.env.VITE_BASEMAP_STYLE || 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json'
const ABU_DHABI: [number, number] = [54.42, 24.48]

function allBounds(list: DistrictListItem[]): LngLatBoundsLike | null {
  const b = list.filter(d => d.bbox).map(d => d.bbox!)
  if (!b.length) return null
  return [
    [Math.min(...b.map(x => x[0])), Math.min(...b.map(x => x[1]))],
    [Math.max(...b.map(x => x[2])), Math.max(...b.map(x => x[3]))],
  ]
}

function padding() {
  const open = !!store.selectedCode
  return { top: 120, bottom: 80, left: props.listWidth + 60, right: open ? props.panelWidth + 40 : 40 }
}

function flyHome(animate = true) {
  if (!map) return
  const b = allBounds(store.districts)
  if (b) map.fitBounds(b, { padding: padding(), pitch: 45, bearing: -12, duration: animate ? 1200 : 0 })
  else map.jumpTo({ center: ABU_DHABI, zoom: 11, pitch: 45, bearing: -12 })
}

function flyTo(d: DistrictListItem) {
  if (!map || !d.bbox) return
  map.fitBounds([[d.bbox[0], d.bbox[1]], [d.bbox[2], d.bbox[3]]], {
    padding: padding(), pitch: 58, bearing: -16, duration: 1300, maxZoom: 15.5,
  })
}

function buildingColor(code: string | null): ExpressionSpecification {
  if (!code) {
    return ['case', ['get', 'uc'], '#8A6A3A', '#5B6878']
  }
  return ['case',
    ['==', ['get', 'code'], code], ['case', ['get', 'uc'], '#FFB547', '#A9D0F7'],
    '#3A4553']
}

function idOf(code: string | null): number | null {
  if (!code) return null
  return store.districts.find(d => d.code === code)?.sortNo ?? null
}

function applySelection() {
  if (!map || !map.getSource('districts')) return
  const code = store.selectedCode
  if (selectedId !== null) map.setFeatureState({ source: 'districts', id: selectedId }, { selected: false })
  selectedId = idOf(code)
  if (selectedId !== null) map.setFeatureState({ source: 'districts', id: selectedId }, { selected: true })
  map.setPaintProperty('districts-dim', 'fill-opacity', code ? ['case', ['boolean', ['feature-state', 'selected'], false], 0, 0.55] : 0)
  map.setFilter('districts-glow', ['==', ['get', 'code'], code ?? '__none__'])
  map.setFilter('districts-edge-selected', ['==', ['get', 'code'], code ?? '__none__'])
  if (map.getLayer('buildings-3d')) map.setPaintProperty('buildings-3d', 'fill-extrusion-color', buildingColor(code))
  renderMarkers()
}

function applyHover(code: string | null) {
  if (!map || !map.getSource('districts')) return
  if (hoverId !== null) map.setFeatureState({ source: 'districts', id: hoverId }, { hover: false })
  hoverId = idOf(code)
  if (hoverId !== null) map.setFeatureState({ source: 'districts', id: hoverId }, { hover: true })
  renderMarkers()
}

function tipHtml(d: DistrictListItem): string {
  const lv = d.liveabilityScore != null ? `${t('tip.liveability')} ${nf(d.liveabilityScore, 2)}` : t('tip.notAssessed')
  const area = d.areaKm2 != null ? `${nf(d.areaKm2, 2)} km² · ` : ''
  return `<b>${escapeHtml(d.nameEn)}</b>${area}${lv}<br>${d.code === store.selectedCode ? t('tip.selected') : t('tip.click')}`
}

function renderMarkers() {
  for (const d of store.districts) {
    const m = markers.get(d.code)
    if (!m) continue
    const on = d.code === store.selectedCode
    // 只切换自己的类名，不能覆盖 MapLibre 加在 Marker 元素上的 maplibregl-marker 等类
    m.el.classList.toggle('on', on)
    m.el.classList.toggle('dim', !on && !!store.selectedCode)
    m.el.classList.toggle('hv', !on && d.code === store.hoveredCode)
    if (on) {
      const lv = d.liveabilityScore != null ? `${t('tip.liveability')} <b>${nf(d.liveabilityScore, 2)}</b>` : t('tip.notAssessed')
      const area = d.areaKm2 != null ? `<b>${nf(d.areaKm2, 2)}</b> km² · ` : ''
      m.el.innerHTML = `${escapeHtml(d.nameEn)}<div class="m">${area}${lv}</div>`
    } else {
      m.el.textContent = d.nameEn
    }
  }
}

function createMarkers() {
  if (!map) return
  markers.forEach(m => m.marker.remove())
  markers.clear()
  for (const d of store.districts) {
    if (!d.centroid) continue
    const div = document.createElement('div')
    div.className = 'dlb'
    div.textContent = d.nameEn
    div.addEventListener('click', ev => { ev.stopPropagation(); void store.select(d.code) })
    div.addEventListener('mouseenter', () => { store.hoveredCode = d.code })
    div.addEventListener('mouseleave', () => { store.hoveredCode = null })
    const marker = new maplibregl.Marker({ element: div, anchor: 'bottom' }).setLngLat(d.centroid).addTo(map)
    markers.set(d.code, { marker, el: div })
  }
  renderMarkers()
}

function addLayers() {
  if (!map) return
  map.addSource('districts', { type: 'geojson', data: api.boundariesUrl() })
  map.addSource('buildings', {
    type: 'vector', tiles: [api.buildingTilesUrl()], minzoom: 12, maxzoom: 16,
    attribution: 'Buildings © makani',
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
  map.addLayer({
    id: 'districts-dim', type: 'fill', source: 'districts',
    paint: { 'fill-color': '#060A10', 'fill-opacity': 0 },
  })
  map.addLayer({
    id: 'districts-edge', type: 'line', source: 'districts',
    paint: { 'line-color': 'rgba(169,208,247,0.45)', 'line-width': 1.2 },
  })
  map.addLayer({
    id: 'districts-glow', type: 'line', source: 'districts', filter: ['==', ['get', 'code'], '__none__'],
    paint: { 'line-color': '#7FB7F0', 'line-width': 10, 'line-blur': 8, 'line-opacity': 0.75 },
  })
  map.addLayer({
    id: 'districts-edge-selected', type: 'line', source: 'districts', filter: ['==', ['get', 'code'], '__none__'],
    paint: { 'line-color': '#CFE5FA', 'line-width': 2.2 },
  })

  map.on('mousemove', 'districts-hover', e => {
    const code = e.features?.[0]?.properties?.code as string | undefined
    store.hoveredCode = code ?? null
    const d = store.districts.find(x => x.code === code)
    tip.value = d ? { x: e.point.x, y: e.point.y, html: tipHtml(d) } : null
    map!.getCanvas().style.cursor = 'pointer'
  })
  map.on('mouseleave', 'districts-hover', () => {
    store.hoveredCode = null
    tip.value = null
    map!.getCanvas().style.cursor = ''
  })
  map.on('click', 'districts-hover', e => {
    const code = e.features?.[0]?.properties?.code as string | undefined
    if (code) void store.select(code)
  })

  // GeoJSON 加载后才能设置 feature-state
  map.on('sourcedata', e => {
    if (e.sourceId === 'districts' && e.isSourceLoaded) {
      applySelection()
      applyHover(store.hoveredCode)
    }
  })
}

onMounted(() => {
  map = new maplibregl.Map({
    container: el.value!,
    style: STYLE,
    center: ABU_DHABI,
    zoom: 11,
    pitch: 45,
    bearing: -12,
    maxPitch: 70,
    attributionControl: { compact: true },
  })
  map.on('load', () => {
    ready = true
    addLayers()
    if (store.districts.length) {
      createMarkers()
      if (store.selected) flyTo(store.selected)
      else flyHome(false)
    }
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

watch(() => store.districts, list => {
  if (!map || !ready || !list.length) return
  createMarkers()
  ;(map.getSource('districts') as GeoJSONSource | undefined)?.setData(api.boundariesUrl())
  if (store.selected) flyTo(store.selected)
  else flyHome(false)
})

watch(() => store.selectedCode, () => {
  applySelection()
  tip.value = null
  if (store.selected) flyTo(store.selected)
})

watch(() => store.hoveredCode, code => applyHover(code))
watch(() => store.homeTick, () => flyHome())
watch(() => store.lang, () => renderMarkers())
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
