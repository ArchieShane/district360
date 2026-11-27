<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { api, type Meta, type MktView, type NodeDetail } from '../api'
import { num } from '../format'
import { colorOf, LEVEL_NAME, TENANCY_STATUS, UNIT_STATUS } from '../labels'
import SrcPending from './SrcPending.vue'

/**
 * 市场页签（ods-mkt）：Tawtheeq 租约（状态、户型、物业用途、逐年新签、租金中位数、地块租约明细），
 * ADREC 买卖 / 抵押 / 租赁与 ELMS 地块交易（源表未导入时显示原因）。建筑 / 楼层 / 单元按所在地块统计。
 * 不显示任何个人信息（主题库里也没有）。
 */
const props = defineProps<{ node: NodeDetail; meta: Meta | null }>()

const mkt = shallowRef<MktView | null>(null)
const error = shallowRef<string | null>(null)
let ctrl: AbortController | null = null

watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  mkt.value = null
  error.value = null
  try {
    mkt.value = await api.mkt(props.node.level, props.node.key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const t = computed(() => mkt.value?.tenancy)
const status = computed(() => (mkt.value?.byStatus ?? []).map(x => ({ ...x, n2: TENANCY_STATUS[x.k]?.n ?? x.k, c: TENANCY_STATUS[x.k]?.c ?? '#6B7280' })))
const years = computed(() => mkt.value?.byYear ?? [])
const maxYear = computed(() => Math.max(1, ...years.value.map(y => y.n)))
const scopeNote = computed(() => {
  const sc = mkt.value?.scope
  return sc && sc.level !== props.node.level ? `按所在${LEVEL_NAME[sc.level]}统计` : ''
})
/** ADREC / ELMS 各表的接入备注（源表未导入等） */
const adrecNotes = computed(() => (props.meta?.tables ?? []).filter(x => x.theme === '市场' && x.table !== 'mkt_tenancy' && x.note))
const a = computed(() => mkt.value?.adrec)
const hasAdrec = computed(() => !!a.value && (a.value.sales + a.value.mortgages + a.value.rents + a.value.plotTransactions) > 0)
/** 入住率（Tawtheeq 单元状态）：已租 ÷ 有状态的单元；楼层 / 单元层级另列单元状态 */
const occ = computed(() => mkt.value?.occupancy)
const os = computed(() => occ.value?.summary)
const hasOcc = computed(() => !!mkt.value?.sources?.mkt_unit_status?.rows)
const occParts = computed(() => {
  const s = os.value
  if (!s) return []
  const other = Math.max(0, s.units - s.rented - s.vacant)
  return [{ k: 'rented', n: s.rented }, { k: 'vacant', n: s.vacant }, { k: 'unknown', n: other }]
    .map(x => ({ ...x, n2: UNIT_STATUS[x.k]?.n ?? '无状态', c: UNIT_STATUS[x.k]?.c ?? '#3A3F48' })).filter(x => x.n > 0)
})
const big = (v: number | undefined) => !v ? '0' : v >= 1e9 ? num(v / 1e9, 2) + 'B' : v >= 1e6 ? num(v / 1e6, 1) + 'M' : v >= 1e3 ? num(v / 1e3, 0) + 'K' : num(v)
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <section v-else-if="!mkt" class="card muted"><p>加载中…</p></section>
  <section v-else-if="!mkt.scope" class="card muted"><p>该建筑没有对应的地块，没有租约与交易数据。</p></section>
  <template v-else>
    <section class="card" :class="{ muted: !hasOcc }" :style="{ order: hasOcc ? 0 : 1 }">
      <h2>入住率<span class="src">theme.mkt_unit_status</span></h2>
      <SrcPending :sources="mkt.sources" :tables="['mkt_unit_status']" />
      <template v-if="hasOcc && os">
        <p v-if="scopeNote" style="margin:-.2rem 0 .5rem">{{ scopeNote }}</p>
        <div class="nums">
          <div><b :class="{ bad: os.occupancyPct != null && os.occupancyPct < 80 }">{{ os.occupancyPct ?? '—' }}<small v-if="os.occupancyPct != null">%</small></b><span>入住率</span></div>
          <div><b>{{ num(os.rented) }}</b><span>已租单元</span></div>
          <div><b>{{ num(os.units) }}</b><span>单元（Tawtheeq）</span></div>
        </div>
        <div class="stack"><i v-for="x in occParts" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
        <div class="keys">
          <span v-for="x in occParts" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span>
          <span v-if="os.blocked"><i class="sw" :style="{ background: UNIT_STATUS.blocked.c }" />其中冻结<b>{{ num(os.blocked) }}</b></span>
        </div>
        <template v-if="occ?.byUsage?.length">
          <div class="sub">按用途</div>
          <div class="rows" style="--cols: minmax(0,1fr) 3.6rem 3.6rem 4.4rem; max-height: 14rem; overflow: auto">
            <div class="row hd"><span>用途</span><span>单元</span><span>已租</span><span>入住率</span></div>
            <div v-for="x in occ.byUsage" :key="x.k" class="row"><span>{{ x.k }}</span><span>{{ num(x.units) }}</span><span>{{ num(x.rented) }}</span><span>{{ x.occupancyPct != null ? x.occupancyPct + '%' : '—' }}</span></div>
          </div>
        </template>
        <template v-if="occ?.units?.length">
          <div class="sub">单元状态</div>
          <div class="ugrid">
            <span v-for="u in occ.units" :key="u.unitKey" :title="`${u.unitNumber ?? u.unitKey} · ${UNIT_STATUS[u.status ?? '']?.n ?? '无状态'}${u.usageType ? ' · ' + u.usageType : ''}`"
                  :style="{ background: UNIT_STATUS[u.status ?? '']?.c ?? '#3A3F48' }">{{ u.unitNumber }}</span>
          </div>
        </template>
        <p v-else-if="node.level === 'floor' || node.level === 'unit'" style="margin-top:.5rem">本{{ LEVEL_NAME[node.level] }}的单元对不上 Tawtheeq 单元状态（编号是否同一套待确认）。</p>
        <p style="margin-top:.5rem">入住率 = 已租 ÷ 有状态（已租 / 空置）的单元；对上 BDMS 单元：{{ num(os.linkedUnits) }}。</p>
      </template>
    </section>

    <section class="card">
      <h2>租赁 · Tawtheeq<span class="src">theme.mkt_tenancy</span></h2>
      <p v-if="scopeNote" style="margin:-.2rem 0 .5rem">{{ scopeNote }}</p>
      <template v-if="t && t.records">
        <div class="nums">
          <div><b>{{ num(t.contracts) }}</b><span>租约</span></div>
          <div><b>{{ num(t.units) }}</b><span>出租单元</span></div>
          <div><b>{{ num(t.activeContracts) }}</b><span>生效中租约</span></div>
        </div>
        <div class="nums">
          <div><b>{{ big(t.medianAnnualRent) }}</b><span>年租金中位数（生效中，AED）</span></div>
          <div><b>{{ num(t.rentPerSqm) }}</b><span>年租金 AED/m²</span></div>
          <div><b>{{ big(t.activeAnnualRent) }}</b><span>生效中年租金合计</span></div>
        </div>
        <div class="stack"><i v-for="x in status" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
        <div class="keys"><span v-for="x in status" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span></div>
        <p style="margin-top:.6rem">起租 {{ t.firstStart ?? '—' }} 至 到期 {{ t.lastEnd ?? '—' }}。样例数据截至 2021 年，「生效中」按源状态，部分已过到期日。</p>
      </template>
      <p v-else>没有租约记录。</p>
    </section>

    <section v-if="mkt.byUnitType?.length" class="card">
      <h2>按单元类型</h2>
      <div class="rows" style="--cols: minmax(0,1fr) 4rem 6rem">
        <div class="row hd"><span>类型</span><span>租约</span><span>年租金中位数</span></div>
        <div v-for="x in mkt.byUnitType" :key="x.k" class="row">
          <span><i class="sw" :style="{ background: colorOf(x.k), marginRight: '.4rem' }" />{{ x.k }}</span>
          <span>{{ num(x.n) }}</span><span>{{ num(x.medianRent) }}</span>
        </div>
      </div>
    </section>

    <section v-if="years.length" class="card">
      <h2>逐年新签租约</h2>
      <div class="hb">
        <div v-for="y in years" :key="y.y">
          <span>{{ y.y }}</span>
          <i><u :style="{ width: (y.n / maxYear * 100) + '%', background: '#3DD6D0' }" /></i>
          <b>{{ num(y.n) }}</b>
        </div>
      </div>
    </section>

    <section v-if="mkt.byPropertyUsage?.length" class="card">
      <h2>按物业用途</h2>
      <div class="hb">
        <div v-for="x in mkt.byPropertyUsage" :key="x.k">
          <span :title="x.k">{{ x.k }}</span>
          <i><u :style="{ width: (x.n / mkt.byPropertyUsage[0].n * 100) + '%', background: colorOf(x.k) }" /></i>
          <b>{{ num(x.n) }}</b>
        </div>
      </div>
    </section>

    <section v-if="mkt.tenancies?.length" class="card">
      <h2>地块租约<span class="chip">最近 {{ mkt.tenancies.length }} 条</span></h2>
      <div class="rows" style="--cols: minmax(0,1fr) 4.6rem 5.2rem">
        <div class="row hd"><span>单元 / 合同</span><span>状态</span><span>年租金</span></div>
        <div v-for="(x, i) in mkt.tenancies" :key="i" class="row">
          <span><b>{{ x.unitName ?? '—' }} · {{ x.unitType ?? '' }}</b><small>{{ x.contractNum }} · {{ x.startDate }} ~ {{ x.endDate }}{{ x.areaSqm ? ' · ' + num(x.areaSqm) + ' m²' : '' }}</small></span>
          <span>{{ TENANCY_STATUS[x.status ?? 'Unknown']?.n ?? x.status }}</span>
          <span>{{ num(x.annualRent) }}</span>
        </div>
      </div>
    </section>

    <section class="card" :class="{ muted: !hasAdrec }">
      <h2>买卖 · 抵押 · 租赁（ADREC）<span class="src">mkt_sale · mkt_mortgage · mkt_rent</span></h2>
      <template v-if="hasAdrec && a">
        <div class="nums">
          <div><b>{{ num(a.sales) }}</b><span>买卖 · {{ big(a.salesValue) }} AED</span></div>
          <div><b>{{ num(a.mortgages) }}</b><span>抵押 · {{ big(a.mortgagesValue) }} AED</span></div>
          <div><b>{{ num(a.rents) }}</b><span>租赁 · 年 {{ big(a.rentsAnnualValue) }} AED</span></div>
        </div>
        <p v-if="a.salesRatePerSqm" style="margin-top:.5rem">成交均价 {{ num(a.salesRatePerSqm) }} AED/m²（Σ金额 ÷ Σ面积）。</p>
        <div v-if="mkt.adrecByMonth?.length" class="rows" style="--cols: minmax(0,1fr) 3.6rem 3.6rem 3.6rem">
          <div class="row hd"><span>月份</span><span>买卖</span><span>抵押</span><span>租赁</span></div>
          <div v-for="m in mkt.adrecByMonth" :key="m.m" class="row"><span>{{ m.m }}</span><span>{{ m.sales }}</span><span>{{ m.mortgages }}</span><span>{{ m.rents }}</span></div>
        </div>
      </template>
      <template v-else>
        <p>没有交易记录。</p>
        <div v-if="adrecNotes.length" class="rows" style="--cols: minmax(0,1fr)">
          <div v-for="x in adrecNotes" :key="x.table" class="row"><span><b>{{ x.title }} · theme.{{ x.table }}</b><small>{{ x.note }}</small></span></div>
        </div>
      </template>
    </section>
  </template>
</template>

<style scoped>
.nums b.bad { color: #FF8A8A }
.ugrid { display: grid; grid-template-columns: repeat(auto-fill, minmax(2.6rem, 1fr)); gap: .25rem; margin-top: .3rem }
.ugrid span { padding: .2rem 0; border-radius: .2rem; text-align: center; font-size: .66rem; color: #0E1116; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap }
</style>
