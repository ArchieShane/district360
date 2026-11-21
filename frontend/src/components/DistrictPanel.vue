<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BlockStatus } from '../api/types'
import { useDistrictStore } from '../stores/district'
import { dmy, nf, sg, ym } from '../utils/format'
import { buildSummary } from '../utils/summary'

const store = useDistrictStore()
const { t, locale } = useI18n()

const o = computed(() => store.overview)
const summary = computed(() => (o.value ? buildSummary(o.value, locale.value as 'zh' | 'en') : ''))

const has = (s: BlockStatus) => s === 'ok' || s === 'sample'

const PS = [
  { key: 'constructed', color: '#C9D3DE' },
  { key: 'underConstruction', color: '#FFB547' },
  { key: 'vacantServiced', color: '#7FB7F0' },
  { key: 'vacantUnserviced', color: '#6B7785' },
] as const
const LU_COLORS: Record<string, string> = {
  residential: '#E8C77A', commercial: '#6FB2FF', mixed: '#C792EA', community: '#5FE0A0', open_space: '#4FBF85', other: '#9AA6B2',
}

const plotParts = computed(() => {
  const ps = o.value?.land.plotStatus
  if (!ps) return []
  return PS.map(p => ({ ...p, value: ps[p.key] ?? 0 }))
})
const plotTotal = computed(() => plotParts.value.reduce((a, p) => a + p.value, 0) || 1)

const bars = computed(() => {
  const m = o.value?.realEstate.monthly ?? []
  const max = Math.max(1, ...m.map(x => x.transactions))
  return m.map((x, i) => ({ x: i * 30 + 4, h: (x.transactions / max) * 30, last: i === m.length - 1, label: x.month }))
})

function dateLabel(src: 'makani' | 'liveability' | 'adrec', asOf: string | null) {
  return asOf ? `${t('src.' + src)} · ${dmy(asOf)}` : t('src.' + src)
}
</script>

<template>
  <aside class="pn">
    <div class="pnh">
      <div class="pk"><span>{{ t('panel.kicker') }}</span></div>
      <div class="ptl">
        <h2>{{ store.selected?.nameEn }}</h2>
        <button type="button" :aria-label="t('panel.close')" @click="store.deselect()">×</button>
      </div>
      <div v-if="o" class="pch">
        <span v-if="o.district.areaKm2 != null" class="chip"><b>{{ nf(o.district.areaKm2, 2) }}</b> km²</span>
        <span v-if="o.district.communities != null" class="chip"><b>{{ nf(o.district.communities) }}</b> {{ t('chips.communities') }}</span>
        <span v-if="o.district.plots != null" class="chip"><b>{{ nf(o.district.plots) }}</b> {{ t('chips.plots') }}</span>
        <span class="chip">{{ t('chips.muni') }}</span>
        <span v-if="!o.district.mappingConfirmed" class="chip smp" :title="o.district.mappingNote ?? ''">{{ t('chips.unconfirmed') }}</span>
        <span v-if="!store.selected?.centroid" class="chip smp">{{ t('chips.noBoundary') }}</span>
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
        <div class="blk">
          <div class="bh">
            <b>{{ t('block.liveability') }}</b>
            <span><i v-if="o.liveability.status === 'sample'" class="tag smp">{{ t('status.sample') }}</i>{{ dateLabel('liveability', o.liveability.asOf) }}</span>
          </div>
          <div v-if="has(o.liveability.status)" class="lvr">
            <div>
              <div class="bigv"><b>{{ nf(o.liveability.current, 2) }}</b></div>
              <div class="subv">{{ t('liv.target') }} <b>{{ nf(o.liveability.target, 2) }}</b> · {{ t('liv.gap') }} {{ nf(o.liveability.gap, 2) }}</div>
            </div>
            <div class="st3">
              <div class="sv">{{ t('liv.existing') }}<b>{{ nf(o.liveability.existing, 2) }}</b></div>
              <span class="ar">→</span>
              <div class="sv">{{ t('liv.current') }}<b>{{ nf(o.liveability.current, 2) }}</b></div>
              <span class="ar">→</span>
              <div class="sv tg">{{ t('liv.target') }}<b>{{ nf(o.liveability.target, 2) }}</b></div>
            </div>
          </div>
          <div v-else class="bigv na2">{{ o.liveability.status === 'not_assessed' ? t('status.notAssessed') : t('status.unavailable') }}</div>
        </div>

        <!-- 土地与建设 -->
        <div class="blk">
          <div class="bh"><b>{{ t('block.land') }}</b><span>{{ dateLabel('makani', o.land.asOf) }}</span></div>
          <template v-if="has(o.land.status)">
            <div class="k3x">
              <div><b>{{ nf(o.land.plots) }}</b>{{ t('land.plots') }}</div>
              <div><b>{{ nf(o.land.buildings) }}</b>{{ t('land.buildings') }}</div>
              <div><b>{{ nf(o.land.buildingsUnderConstruction) }}</b>{{ t('land.uc') }}</div>
            </div>
            <div class="pbar" style="margin-top: 9px; height: 9px">
              <i v-for="p in plotParts" :key="p.key" :style="{ width: (p.value / plotTotal) * 100 + '%', background: p.color }"></i>
            </div>
            <div class="lgd">
              <span v-for="p in plotParts" :key="p.key"><i :style="{ background: p.color }"></i>{{ t('land.ps.' + p.key) }} <b>{{ nf(p.value) }}</b></span>
            </div>
            <div class="lur">
              {{ t('land.landUse') }}：
              <span v-for="u in o.land.landUse.slice(0, 3)" :key="u.key">
                <i :style="{ background: LU_COLORS[u.key] ?? '#9AA6B2' }"></i>{{ locale === 'zh' ? u.nameZh : u.nameEn }} <b>{{ nf(u.pct, 1) }}%</b>
              </span>
            </div>
          </template>
          <div v-else class="bigv na2">{{ t('status.unavailable') }}</div>
        </div>

        <!-- 房地产 -->
        <div class="blk">
          <div class="bh">
            <b>{{ t('block.realEstate') }}</b>
            <span>
              <i v-if="o.realEstate.status === 'sample'" class="tag smp">{{ t('status.sample') }}</i>
              <template v-if="o.realEstate.periodStart">{{ ym(o.realEstate.periodStart) }} – {{ ym(o.realEstate.periodEnd) }}</template>
              <template v-else>{{ t('src.adrec') }}</template>
            </span>
          </div>
          <template v-if="has(o.realEstate.status)">
            <div class="k3x">
              <div>
                <b>AED {{ nf(o.realEstate.salesAedBn, 1) }}<small class="unit"> bn</small></b>{{ t('re.sales') }}
                <small>{{ t('re.yoy') }} {{ sg(o.realEstate.salesYoyPct) }}%</small>
              </div>
              <div><b>{{ nf(o.realEstate.transactions) }}</b>{{ t('re.tx') }}<small>{{ t('re.mort') }} {{ nf(o.realEstate.mortgages) }}</small></div>
              <div><b>{{ nf(o.realEstate.avgPriceSqm) }}</b>{{ t('re.price') }}<small>{{ t('re.lease') }} {{ nf(o.realEstate.activeLeases) }}</small></div>
            </div>
            <template v-if="bars.length">
              <svg width="100%" height="34" viewBox="0 0 360 34" preserveAspectRatio="none" class="bars">
                <rect v-for="b in bars" :key="b.label" :x="b.x" :y="32 - b.h" width="22" :height="b.h" rx="2" fill="#7FB7F0" :opacity="b.last ? 1 : 0.5">
                  <title>{{ ym(b.label) }}</title>
                </rect>
              </svg>
              <div class="barsl"><span>{{ ym(bars[0].label) }}</span><span>{{ t('re.monthly') }}</span><span>{{ ym(bars[bars.length - 1].label) }}</span></div>
            </template>
          </template>
          <div v-else class="bigv na2">{{ t('status.unavailable') }}</div>
        </div>

        <!-- 公共设施 -->
        <div class="blk">
          <div class="bh">
            <b>{{ t('block.facilities') }}</b>
            <span><i v-if="o.facilities.parks.status === 'sample'" class="tag smp">{{ t('fac.parks') }} · {{ t('status.sample') }}</i>{{ dateLabel('makani', o.facilities.asOf) }}</span>
          </div>
          <div class="fac6">
            <div><b>{{ has(o.facilities.status) ? nf(o.facilities.schools) : '—' }}</b>{{ t('fac.schools') }}</div>
            <div><b>{{ has(o.facilities.status) ? nf(o.facilities.medical) : '—' }}</b>{{ t('fac.health') }}</div>
            <div><b>{{ has(o.facilities.status) ? nf(o.facilities.mosques) : '—' }}</b>{{ t('fac.mosques') }}</div>
            <div>
              <b>{{ has(o.facilities.parks.status) ? nf(o.facilities.parks.count) : '—' }}</b>{{ t('fac.parks') }}
              <small>{{ has(o.facilities.parks.status) ? nf(o.facilities.parks.areaHa, 1) + ' ha' : ' ' }}</small>
            </div>
            <div>
              <b>{{ has(o.facilities.parks.status) ? nf(o.facilities.parks.gapHa, 1) : '—' }}</b>{{ t('fac.gap') }}
              <small>{{ o.facilities.parks.status === 'not_assessed' ? t('status.notAssessed') : ' ' }}</small>
            </div>
          </div>
        </div>
      </template>
    </div>
  </aside>
</template>
