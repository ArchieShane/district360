<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BlockStatus } from '../api/types'
import { useDistrictStore } from '../stores/district'
import { dmy, nf, sg, ym } from '../utils/format'
import { buildSummary } from '../utils/summary'
import FacilityList from './FacilityList.vue'

const store = useDistrictStore()
const { t, locale } = useI18n()

const o = computed(() => store.overview)
const summary = computed(() => (o.value ? buildSummary(o.value, locale.value as 'zh' | 'en') : ''))

const has = (s: BlockStatus) => s === 'ok' || s === 'sample'
/** 没有数据时也渲染完整布局，数值显示 NaN（区块标题会标「没有查询到数据」） */
const NA = 'NaN'
const fv = (ok: boolean, v: number | null | undefined, digits = 0) => (ok ? nf(v, digits) : NA)
const fs = (ok: boolean, v: number | null | undefined) => (ok ? sg(v) : NA)

// status 与地图地块图层（map_plot.status）一致，悬停图例时地图上高亮对应地块
const PS = [
  { key: 'constructed', status: 'constructed', color: '#C9D3DE', metric: 'plots_constructed' },
  { key: 'underConstruction', status: 'under_construction', color: '#FFB547', metric: 'plots_under_construction' },
  { key: 'vacantServiced', status: 'vacant_serviced', color: '#7FB7F0', metric: 'plots_vacant_serviced' },
  { key: 'vacantUnserviced', status: 'vacant_unserviced', color: '#6B7785', metric: 'plots_vacant_unserviced' },
] as const
const LU_COLORS: Record<string, string> = {
  residential: '#E8C77A', commercial: '#6FB2FF', community: '#5FE0A0', open_space: '#4FBF85', other: '#9AA6B2',
}

const plotParts = computed(() => {
  const ps = o.value?.land.plotStatus
  if (!ps) return []
  return PS.map(p => ({ ...p, value: ps[p.key] ?? 0 }))
})
/** 面板显示的土地利用类别；没有数据时也按居住、商业两类占位 */
const landUseShown = computed(() => {
  const list = o.value?.land.landUse.filter(x => x.highlight) ?? []
  if (list.length) return list
  return [
    { key: 'residential', nameZh: '居住', nameEn: 'Residential', pct: null },
    { key: 'commercial', nameZh: '商业', nameEn: 'Commercial', pct: null },
  ]
})
/** 「其他」= 居住、商业以外的全部地块用途合计（让三项占比加起来是 100%） */
const landUseOther = computed(() => {
  const all = o.value?.land.landUse ?? []
  const rest = all.filter(x => !x.highlight)
  const total = all.reduce((a, x) => a + x.plotCount, 0)
  const n = rest.reduce((a, x) => a + x.plotCount, 0)
  return { pct: total ? Math.round((n / total) * 1000) / 10 : null, parts: rest }
})
const plotTotal = computed(() => plotParts.value.reduce((a, p) => a + p.value, 0) || 1)

const bars = computed(() => {
  const m = o.value?.realEstate.monthly ?? []
  if (!m.length) {
    // 没有数据：画 12 根灰色占位柱
    return Array.from({ length: 12 }, (_, i) => ({ x: i * 30 + 4, h: 4, last: false, label: '', empty: true }))
  }
  const max = Math.max(1, ...m.map(x => x.transactions))
  return m.map((x, i) => ({ x: i * 30 + 4, h: (x.transactions / max) * 30, last: i === m.length - 1, label: x.month, empty: false }))
})

function dateLabel(src: 'makani' | 'liveability' | 'adrec', asOf: string | null) {
  return asOf ? `${t('src.' + src)} · ${dmy(asOf)}` : t('src.' + src)
}
</script>

<template>
  <aside class="pn">
    <div class="pnh">
      <div class="pk"><span>{{ t('panel.kicker') }}{{ o ? ' · ' + o.district.municipality : '' }}</span></div>
      <div class="ptl">
        <h2>{{ store.selected?.nameEn ?? o?.district.nameEn }}</h2>
        <button type="button" :aria-label="t('panel.close')" @click="store.deselect()">×</button>
      </div>
      <div v-if="o" class="pch">
        <span v-if="o.district.areaKm2 != null" class="chip" data-m="area_km2"><b>{{ nf(o.district.areaKm2, 2) }}</b> km²</span>
        <span v-if="o.district.communities != null" class="chip" data-m="communities"><b>{{ nf(o.district.communities) }}</b> {{ t('chips.communities') }}</span>
        <span v-if="o.district.plots != null" class="chip" data-m="plots"><b>{{ nf(o.district.plots) }}</b> {{ t('chips.plots') }}</span>
        <span class="chip" data-m="municipality">{{ t('muni.' + o.district.municipality) }} {{ o.district.municipality }}</span>
      </div>
    </div>

    <div class="pnb">
      <div v-if="!o" class="empty">{{ store.loadingOverview ? t('foot.loading') : '' }}</div>
      <template v-else>
        <!-- 摘要 -->
        <div class="blk">
          <div class="bh"><b>{{ t('block.summary') }}</b><span></span></div>
          <div class="sum" v-html="summary"></div>
        </div>

        <!-- 宜居得分 -->
        <div class="blk" :class="{ na: !has(o.liveability.status) }">
          <div class="bh">
            <b>{{ t('block.liveability') }}</b>
            <span>
              <i v-if="!has(o.liveability.status)" class="tag nd">{{ o.liveability.status === 'not_assessed' ? t('status.notAssessed') : t('status.unavailable') }}</i>
              {{ dateLabel('liveability', o.liveability.asOf) }}
            </span>
          </div>
          <div class="lvr">
            <div>
              <div class="bigv"><b data-m="liv_current">{{ fv(has(o.liveability.status), o.liveability.current, 2) }}</b></div>
              <div class="subv">
                <span data-m="liv_target">{{ t('liv.target') }} <b>{{ fv(has(o.liveability.status), o.liveability.target, 2) }}</b></span> ·
                <span data-m="liv_gap">{{ t('liv.gap') }} {{ fv(has(o.liveability.status), o.liveability.gap, 2) }}</span>
              </div>
            </div>
            <div class="st3">
              <div class="sv" data-m="liv_existing">{{ t('liv.existing') }}<b>{{ fv(has(o.liveability.status), o.liveability.existing, 2) }}</b></div>
              <span class="ar">→</span>
              <div class="sv" data-m="liv_current">{{ t('liv.current') }}<b>{{ fv(has(o.liveability.status), o.liveability.current, 2) }}</b></div>
              <span class="ar">→</span>
              <div class="sv tg" data-m="liv_target">{{ t('liv.target') }}<b>{{ fv(has(o.liveability.status), o.liveability.target, 2) }}</b></div>
            </div>
          </div>
        </div>

        <!-- 土地与建设 -->
        <div class="blk" :class="{ na: !has(o.land.status) }">
          <div class="bh">
            <b>{{ t('block.land') }}</b>
            <span><i v-if="!has(o.land.status)" class="tag nd">{{ t('status.unavailable') }}</i>{{ dateLabel('makani', o.land.asOf) }}</span>
          </div>
          <div class="k3x">
            <div data-m="plots"><b>{{ fv(has(o.land.status), o.land.plots) }}</b>{{ t('land.plots') }}</div>
            <div data-m="buildings"><b>{{ fv(has(o.land.status), o.land.buildings) }}</b>{{ t('land.buildings') }}</div>
            <div data-m="buildings_under_construction"><b>{{ fv(has(o.land.status), o.land.buildingsUnderConstruction) }}</b>{{ t('land.uc') }}</div>
          </div>
          <div class="pbar" style="margin-top: 9px; height: 9px">
            <template v-if="has(o.land.status)">
              <i v-for="p in plotParts" :key="p.key" :data-m="p.metric" :style="{ width: (p.value / plotTotal) * 100 + '%', background: p.color }"
                 @mouseenter="store.highlightPlotStatus = p.status" @mouseleave="store.highlightPlotStatus = null"></i>
            </template>
          </div>
          <div class="lgd">
            <span v-for="p in plotParts" :key="p.key" :data-m="p.metric" class="hl"
                  :class="{ on: store.highlightPlotStatus === p.status }"
                  @mouseenter="store.highlightPlotStatus = p.status" @mouseleave="store.highlightPlotStatus = null"><i :style="{ background: p.color }"></i>{{ t('land.ps.' + p.key) }} <b>{{ fv(has(o.land.status), p.value) }}</b></span>
          </div>
          <div class="lur">
            {{ t('land.landUse') }}：
            <span v-for="u in landUseShown" :key="u.key" data-m="land_use">
              <i :style="{ background: LU_COLORS[u.key] ?? '#9AA6B2' }"></i>{{ locale === 'zh' ? u.nameZh : u.nameEn }} <b>{{ fv(has(o.land.status), u.pct, 1) }}%</b>
            </span>
            <span class="lu-other" data-m="land_use">
              <i style="background: #9AA6B2"></i>{{ t('land.other') }} <b>{{ fv(has(o.land.status), landUseOther.pct, 1) }}%</b>
              <em class="info" tabindex="0">i
                <span class="info-pop">
                  <b>{{ t('land.otherTitle') }}</b>
                  {{ t('land.otherDesc') }}
                  <template v-if="has(o.land.status) && landUseOther.parts.length">
                    <br />
                    <template v-for="(p, i) in landUseOther.parts" :key="p.key">{{ i ? '；' : '' }}{{ locale === 'zh' ? p.nameZh : p.nameEn }} {{ nf(p.pct ?? 0, 1) }}%（{{ nf(p.plotCount) }}）</template>
                  </template>
                </span>
              </em>
            </span>
          </div>
        </div>

        <!-- 房地产 -->
        <div class="blk" :class="{ na: !has(o.realEstate.status) }">
          <div class="bh">
            <b>{{ t('block.realEstate') }}</b>
            <span>
              <i v-if="!has(o.realEstate.status)" class="tag nd">{{ t('status.unavailable') }}</i>
              <template v-if="o.realEstate.periodStart">{{ ym(o.realEstate.periodStart) }} – {{ ym(o.realEstate.periodEnd) }}</template>
              <template v-else>{{ t('src.adrec') }}</template>
            </span>
          </div>
          <div class="k3x">
            <div>
              <span data-m="re_sales"><b>AED {{ fv(has(o.realEstate.status), o.realEstate.salesAedBn, 1) }}<small class="unit"> bn</small></b>{{ t('re.sales') }}</span>
              <small data-m="re_yoy">{{ t('re.yoy') }} {{ fs(has(o.realEstate.status), o.realEstate.salesYoyPct) }}%</small>
            </div>
            <div>
              <span data-m="re_transactions"><b>{{ fv(has(o.realEstate.status), o.realEstate.transactions) }}</b>{{ t('re.tx') }}</span>
              <small data-m="re_mortgages">{{ t('re.mort') }} {{ fv(has(o.realEstate.status), o.realEstate.mortgages) }}</small>
            </div>
            <div>
              <span data-m="re_price"><b>{{ fv(has(o.realEstate.status), o.realEstate.avgPriceSqm) }}</b>{{ t('re.price') }}</span>
              <small data-m="re_leases">{{ t('re.lease') }} {{ fv(has(o.realEstate.status), o.realEstate.activeLeases) }}</small>
            </div>
          </div>
          <svg width="100%" height="34" viewBox="0 0 360 34" preserveAspectRatio="none" class="bars" data-m="re_monthly">
            <rect v-for="(b, i) in bars" :key="i" :x="b.x" :y="32 - b.h" width="22" :height="b.h" rx="2"
                  :fill="b.empty ? '#3A4553' : '#7FB7F0'" :opacity="b.empty ? 1 : b.last ? 1 : 0.5">
              <title>{{ b.empty ? 'NaN' : ym(b.label) }}</title>
            </rect>
          </svg>
          <div class="barsl">
            <span>{{ bars[0].empty ? '—' : ym(bars[0].label) }}</span>
            <span>{{ t('re.monthly') }}</span>
            <span>{{ bars[bars.length - 1].empty ? '—' : ym(bars[bars.length - 1].label) }}</span>
          </div>
        </div>

        <!-- 公共设施 -->
        <div class="blk">
          <div class="bh">
            <b>{{ t('block.facilities') }}</b>
            <span>
              <i v-if="!has(o.facilities.parks.status)" class="tag nd">{{ t('fac.parks') }} · {{ o.facilities.parks.status === 'not_assessed' ? t('status.notAssessed') : t('status.unavailable') }}</i>
              {{ dateLabel('makani', o.facilities.asOf) }}
            </span>
          </div>
          <div class="fac6">
            <!-- 学校 / 医疗 / 清真寺：点击展开列表，点击条目在地图上定位 -->
            <div data-m="schools" class="fac-btn" :class="{ on: store.facilityCategory === 'school' }" :title="t('fac.open')"
                 role="button" tabindex="0" @click="has(o.facilities.status) && store.toggleFacilities('school')"
                 @keydown.enter="has(o.facilities.status) && store.toggleFacilities('school')">
              <b>{{ fv(has(o.facilities.status), o.facilities.schools) }}</b>{{ t('fac.schools') }}<em>›</em>
            </div>
            <div data-m="medical" class="fac-btn" :class="{ on: store.facilityCategory === 'medical' }" :title="t('fac.open')"
                 role="button" tabindex="0" @click="has(o.facilities.status) && store.toggleFacilities('medical')"
                 @keydown.enter="has(o.facilities.status) && store.toggleFacilities('medical')">
              <b>{{ fv(has(o.facilities.status), o.facilities.medical) }}</b>{{ t('fac.health') }}<em>›</em>
            </div>
            <div data-m="mosques" class="fac-btn" :class="{ on: store.facilityCategory === 'mosque' }" :title="t('fac.open')"
                 role="button" tabindex="0" @click="has(o.facilities.status) && store.toggleFacilities('mosque')"
                 @keydown.enter="has(o.facilities.status) && store.toggleFacilities('mosque')">
              <b>{{ fv(has(o.facilities.status), o.facilities.mosques) }}</b>{{ t('fac.mosques') }}<em>›</em>
            </div>
            <div :class="{ na: !has(o.facilities.parks.status) }">
              <span data-m="parks"><b>{{ fv(has(o.facilities.parks.status), o.facilities.parks.count) }}</b>{{ t('fac.parks') }}</span>
              <small data-m="park_area_ha">{{ fv(has(o.facilities.parks.status), o.facilities.parks.areaHa, 1) }} ha</small>
            </div>
            <div data-m="park_gap_ha" :class="{ na: !has(o.facilities.parks.status) }">
              <b>{{ fv(has(o.facilities.parks.status), o.facilities.parks.gapHa, 1) }}</b>{{ t('fac.gap') }}
            </div>
          </div>
          <FacilityList />
        </div>
      </template>
    </div>
  </aside>
</template>
