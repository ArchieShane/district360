<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FacilityItem, MapFeatureDetail } from '../api/types'
import { useDistrictStore } from '../stores/district'
import { dmy, nf } from '../utils/format'

/**
 * 地图上点开的详情卡片：地块、建筑、学校、医疗、清真寺。
 * 位置由 MapView 按锚点经纬度投影后传入（随地图移动）；below = 锚点离顶部太近时卡片放到锚点下方；
 * shift = 卡片水平平移量（避开列表和面板），连接线和定位点不动。
 */
const props = defineProps<{ x: number; y: number; below: boolean; shift: number }>()

const store = useDistrictStore()
const { t, te, locale } = useI18n()

type Kind = 'plot' | 'building' | 'school' | 'medical' | 'mosque'
interface Row { key: string; label: string; value: string; list?: string[] }

/** 清真寺类型（源数据为阿拉伯文） */
const MOSQUE_TYPE: Record<string, { zh: string; en: string }> = {
  'جامع': { zh: '聚礼清真寺', en: 'Friday mosque' },
  'مسجد': { zh: '清真寺', en: 'Mosque' },
}

/** 每类展示的字段及顺序；单位 / 格式见 fmt */
const FIELDS: Record<Kind, string[]> = {
  plot: ['status', 'serviceStatus', 'community', 'areaM2', 'useDesc', 'landUse', 'secondaryUse', 'planningStatus', 'allocationStatus',
    'allocation', 'ownership', 'devCode', 'far', 'maxGfaM2', 'maxHeight', 'investmentZone', 'permitType', 'permitDate', 'buildings', 'refId'],
  building: ['height', 'floors', 'physicalStatus', 'usageType', 'primaryUse', 'secondaryUsage', 'community', 'completionDate', 'popularName', 'refId'],
  school: ['subtype', 'curriculum', 'gender', 'grades', 'cycle', 'students', 'capacity', 'teachingStaff', 'totalStaff', 'rating',
    'feeCategory', 'avgFeeAed', 'academicYear', 'community', 'esisId'],
  medical: ['subtype', 'subType', 'isPublic', 'specialities', 'beds', 'emergency', 'address', 'licenseNo', 'licenseStatus', 'licenseExpiry'],
  mosque: ['subtype'],
}
/** 源表枚举值的中英文 */
const BUILT = { zh: '已建成', en: 'Existing' }
const UC = { zh: '在建', en: 'Under construction' }
const VALUES: Record<string, Record<string, { zh: string; en: string }>> = {
  serviceStatus: {
    available: { zh: '已通市政', en: 'Serviced' },
    notAvailable: { zh: '未通市政', en: 'Not serviced' },
    notAvailableHaveConsNOC: { zh: '未通市政（已有施工 NOC）', en: 'Not serviced (construction NOC issued)' },
  },
  physicalStatus: { existing: BUILT, Existing: BUILT, Built: BUILT, BUILT, underConstruction: UC, 'Under Construction': UC },
}
const UNIT: Record<string, string> = { areaM2: 'm²', maxGfaM2: 'm²', height: 'm', avgFeeAed: 'AED' }
const BLANK = new Set(['', 'n/a', 'na', 'undefined', 'null', '-'])

const poi = computed<FacilityItem | null>(() =>
  store.focusedPoiId ? store.facilityList.find(f => f.id === store.focusedPoiId) ?? null : null)

const kind = computed<Kind | null>(() => store.featureDetail?.kind ?? poi.value?.category ?? null)
const feature = computed<MapFeatureDetail | null>(() => store.featureDetail?.data ?? null)
const loading = computed(() => !!store.featureDetail && !store.featureDetail.data && !store.featureDetail.error)

/** camelCase / snake_case 的源值转成可读文字：finalApproval → Final approval */
function humanize(s: string): string {
  if (!/^[a-z]+([A-Z][a-z]*)+$/.test(s) && !/^[a-z]+(_[a-z]+)+$/.test(s)) return s
  const w = s.replace(/_/g, ' ').replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase()
  return w.charAt(0).toUpperCase() + w.slice(1)
}

function fmt(key: string, v: unknown): string | string[] | null {
  if (v === null || v === undefined) return null
  if (Array.isArray(v)) {
    const list = v.map(x => String(x).trim()).filter(x => !BLANK.has(x.toLowerCase()))
    return list.length ? list : null
  }
  if (typeof v === 'number') return `${nf(v, Number.isInteger(v) ? 0 : 1)}${UNIT[key] ? ' ' + UNIT[key] : ''}`
  if (typeof v === 'boolean') return t(v ? 'detail.yes' : 'detail.no')
  const s = String(v).trim()
  if (BLANK.has(s.toLowerCase())) return null
  const mapped = VALUES[key]?.[s]
  if (mapped) return locale.value === 'en' ? mapped.en : mapped.zh
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return dmy(s)
  if (key === 'isPublic') return s.toLowerCase() === 'yes' ? t('detail.public') : s.toLowerCase() === 'no' ? t('detail.private') : s
  return humanize(s)
}

/** 某字段的原始值：卡片专用字段（状态、建筑数等）+ 源表 attrs */
function raw(key: string): unknown {
  const f = feature.value
  const p = poi.value
  if (f) {
    switch (key) {
      case 'status': return f.status ? t('land.ps.' + ({ under_construction: 'underConstruction', vacant_serviced: 'vacantServiced', vacant_unserviced: 'vacantUnserviced' }[f.status] ?? f.status)) : null
      case 'useDesc': return f.useDesc
      case 'height': return f.height
      case 'buildings': return f.buildings
      case 'refId': return f.refId
      case 'physicalStatus': return f.underConstruction ? 'underConstruction' : f.attrs.physicalStatus
      default: return f.attrs[key]
    }
  }
  if (p) {
    if (key === 'subtype') {
      const m = p.subtype ? MOSQUE_TYPE[p.subtype] : undefined
      return m ? (locale.value === 'en' ? m.en : m.zh) : p.subtype
    }
    return p.attrs?.[key]
  }
  return null
}

const rows = computed<Row[]>(() => {
  const k = kind.value
  if (!k || (!feature.value && !poi.value)) return []
  const out: Row[] = []
  for (const key of FIELDS[k]) {
    const v = fmt(key, raw(key))
    if (v === null) continue
    const lk = `detail.f.${k}.${key}`
    const label = te(lk) ? t(lk) : t(`detail.f.${key}`)
    out.push(Array.isArray(v) ? { key, label, value: '', list: v } : { key, label, value: v })
  }
  return out
})

const title = computed(() => {
  const f = feature.value
  const p = poi.value
  if (p) return p.nameEn || p.nameAr || p.id
  const a = f?.attrs ?? {}
  if (kind.value === 'plot') return `${t('detail.kind.plot')} ${String(a.plotNo ?? f?.refId ?? store.featureDetail?.id ?? '')}`.trim()
  const name = (a.name ?? a.popularName) as string | undefined
  if (name) return name
  return `${t('detail.kind.building')} ${f?.refId ?? ''}`.trim()
})

const sub = computed(() => {
  const p = poi.value
  if (p) return [p.nameEn && p.nameAr ? p.nameAr : null, t('fac.cat.' + p.category)].filter(Boolean).join(' · ')
  const f = feature.value
  const parts: string[] = [t('detail.kind.' + (kind.value ?? 'plot'))]
  if (f?.kind === 'building' && f.attrs.nameAr) parts.unshift(String(f.attrs.nameAr))
  const d = f ? store.features.get(f.code) : undefined
  if (d) parts.push(d.nameEn)
  return parts.join(' · ')
})

const plotLink = computed(() => feature.value?.kind === 'building' ? feature.value.plot ?? null : null)

function openPlot() {
  const p = plotLink.value
  const f = feature.value
  if (p && f) void store.openFeature('plot', p.id, f.location)
}
</script>

<template>
  <div v-if="kind" class="dc" :class="[kind, { below: props.below }]" :style="{ left: props.x + 'px', top: props.y + 'px' }">
    <div class="dc-card" role="dialog" :style="{ transform: `translateX(${props.shift}px)` }" :aria-label="title" @wheel.stop @mousedown.stop @click.stop>
      <div class="dc-h">
        <i :class="'dc-k ' + kind"></i>
        <div class="dc-tt">
          <b dir="auto">{{ loading ? t('detail.kind.' + kind) : title }}</b>
          <small v-if="!loading" dir="auto">{{ sub }}</small>
        </div>
        <button type="button" class="dc-x" :aria-label="t('detail.close')" @click="store.closeDetail()">×</button>
      </div>
      <div v-if="loading" class="dc-msg">{{ t('foot.loading') }}</div>
      <div v-else-if="store.featureDetail?.error" class="dc-msg err">{{ store.featureDetail.error }}</div>
      <dl v-else class="dc-body">
        <template v-for="r in rows" :key="r.key">
          <dt>{{ r.label }}</dt>
          <dd v-if="r.list" class="dc-list"><span v-for="(s, i) in r.list" :key="i" dir="auto">{{ s }}</span></dd>
          <dd v-else dir="auto">{{ r.value }}</dd>
        </template>
        <template v-if="plotLink">
          <dt>{{ t('detail.f.building.plot') }}</dt>
          <dd><button type="button" class="dc-link" @click="openPlot">{{ plotLink.plotNo || plotLink.plotId || plotLink.id }} ›</button></dd>
        </template>
      </dl>
      <div v-if="!loading && !store.featureDetail?.error && !rows.length && !plotLink" class="dc-msg">{{ t('detail.empty') }}</div>
    </div>
    <div class="dc-stem"></div>
    <div class="dc-dot"></div>
  </div>
</template>

<style scoped>
/* 锚点在定位点中心：默认卡片在上方（卡片 + 连接线 + 定位点自下而上），below 时翻到下方 */
.dc {
  position: fixed;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  transform: translate(-50%, calc(-100% + 7px));
  pointer-events: none;
}
.dc.below {
  flex-direction: column-reverse;
  transform: translate(-50%, -7px);
}
.dc-card {
  pointer-events: auto;
  width: 300px;
  max-height: min(380px, 48vh);
  display: flex;
  flex-direction: column;
  border-radius: 12px;
  background: rgba(10, 18, 28, 0.96);
  box-shadow: inset 0 0 0 1px rgba(127, 183, 240, 0.55), 0 10px 30px rgba(0, 0, 0, 0.5);
  color: var(--tx);
  font-size: 12.5px;
  overflow: hidden;
}
.dc-h {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px 10px 9px 12px;
  border-bottom: 1px solid var(--line);
}
.dc-k { width: 10px; height: 10px; border-radius: 3px; flex: none; margin-top: 4px; }
.dc-k.plot { background: #7fb7f0; }
.dc-k.building { background: #a9d0f7; border-radius: 2px; }
.dc-k.school { background: #e8c77a; border-radius: 50%; }
.dc-k.medical { background: #ff8a7a; border-radius: 50%; }
.dc-k.mosque { background: #5fe0a0; border-radius: 50%; }
.dc-tt { flex: 1; min-width: 0; }
.dc-tt b { display: block; font-size: 13.5px; font-weight: 700; color: #fff; line-height: 1.3; overflow-wrap: anywhere; }
.dc-tt small { display: block; margin-top: 2px; font-size: 11.5px; color: var(--tm); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dc-x {
  flex: none;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--tm);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
.dc-x:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.dc-body {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 6px 12px;
  padding: 10px 12px 12px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.dc-body dt { color: var(--tq); white-space: nowrap; }
.dc-body dd { color: var(--tx); font-weight: 600; overflow-wrap: anywhere; }
.dc-list { display: flex; flex-direction: column; gap: 3px; font-weight: 500 !important; }
.dc-link {
  border: 0;
  padding: 0;
  background: none;
  color: var(--acc2);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.dc-link:hover { text-decoration: underline; }
.dc-msg { padding: 12px; color: var(--tm); }
.dc-msg.err { color: #ff8a7a; }
.dc-stem { width: 2px; height: 22px; background: linear-gradient(rgba(169, 208, 247, 0.95), rgba(169, 208, 247, 0.5)); }
.dc.below .dc-stem { background: linear-gradient(rgba(169, 208, 247, 0.5), rgba(169, 208, 247, 0.95)); }
.dc-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 3px solid #0b1118;
  background: #a9d0f7;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.85), 0 0 12px rgba(127, 183, 240, 0.9);
}
.dc.school .dc-dot { background: #e8c77a; }
.dc.medical .dc-dot { background: #ff8a7a; }
.dc.mosque .dc-dot { background: #5fe0a0; }
</style>
