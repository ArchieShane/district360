<script setup lang="ts">
import { computed, shallowRef, watch } from 'vue'
import { api, type Meta, type NodeDetail, type OpsView } from '../api'
import { num } from '../format'
import { LAMP_TYPE, LEVEL_NAME, LIGHT_STATUS, OPS_RECORD, VIOLATION_STAGE } from '../labels'
import MonthBars from './MonthBars.vue'
import SrcPending from './SrcPending.vue'

/**
 * 城市运行页签（ods-ops）：居民诉求、出行与道路安全、视觉违规上报、违规与处罚、住宅占用（业务视图），
 * 路灯（点数、灯盏、状态、灯型）与公交站（站、站台、列表）。地块及以下按所在社区统计，另列本地块的执法记录。
 * 业务视图没有数据时卡片显示「待接入」与原因，并排在路灯、公交站之后。
 */
const props = defineProps<{ node: NodeDetail; meta: Meta | null }>()
const emit = defineEmits<{ focus: [lon: number, lat: number] }>()

const ops = shallowRef<OpsView | null>(null)
const error = shallowRef<string | null>(null)
let ctrl: AbortController | null = null

watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  ops.value = null
  error.value = null
  try {
    ops.value = await api.ops(props.node.level, props.node.key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const s = computed(() => ops.value?.summary)
const status = computed(() => (ops.value?.lightsByStatus ?? []).map(x => ({ ...x, n2: LIGHT_STATUS[x.k]?.n ?? x.k, c: LIGHT_STATUS[x.k]?.c ?? '#6B7280' })))
const lamps = computed(() => ops.value?.lightsByLampType ?? [])
const inServiceRate = computed(() => s.value && s.value.lights ? (s.value.lightsInService / s.value.lights * 100).toFixed(1) : null)
const scopeNote = computed(() => {
  const sc = ops.value?.scope
  return sc && sc.level !== props.node.level ? `按所在${LEVEL_NAME[sc.level]}统计` : ''
})

const src = computed(() => ops.value?.sources)
const has = (t: string) => !!src.value?.[t]?.rows
/** 业务视图任一有数据时排在路灯、公交站前面（原型的顺序），都没有时放后面 */
const bizOrder = computed(() => ['ops_case', 'ops_accident', 'ops_distortion', 'ops_violation', 'ops_occupancy_case'].some(has) ? 0 : 2)
const cs = computed(() => ops.value?.cases)
const ac = computed(() => ops.value?.accidents)
const ds = computed(() => ops.value?.distortions)
const vs = computed(() => ops.value?.violations)
const oc = computed(() => ops.value?.occupancyCases)
const stages = computed(() => {
  const v = vs.value?.summary
  return v ? (['awareness', 'warning', 'fine'] as const).map(k => ({ k, n: v[k], n2: VIOLATION_STAGE[k].n, c: VIOLATION_STAGE[k].c })) : []
})
/** 事故严重程度配色：按原值关键词（死亡最深、轻微最浅），认不出的灰色 */
const sevColor = (k: string) => /fatal|death|死亡/i.test(k) ? '#C93A3A' : /sever|serious|major|重/i.test(k) ? '#E86A6A'
  : /moderate|medium|中/i.test(k) ? '#F0A64A' : /minor|slight|light|轻/i.test(k) ? '#F2D45C' : '#8C929B'
const rows = (x: unknown) => (x ?? []) as Record<string, string | number>[]
const pct = (v: number | null | undefined) => v == null ? '—' : num(v, 1)
const big = (v: number | undefined) => !v ? '0' : v >= 1e6 ? num(v / 1e6, 2) + 'M' : v >= 1e3 ? num(v / 1e3, 1) + 'K' : num(v)
const maxN = (xs: { n: number }[] | undefined) => Math.max(1, ...(xs ?? []).map(x => x.n))
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <section v-else-if="!ops" class="card muted"><p>加载中…</p></section>
  <section v-else-if="!ops.scope" class="card muted"><p>没有对应的社区，没有城市运行数据。</p></section>
  <template v-else-if="s">
    <section class="card" style="order:1">
      <h2>路灯<span class="src">theme.ops_streetlight</span></h2>
      <p v-if="scopeNote" style="margin:-.2rem 0 .5rem">{{ scopeNote }}</p>
      <div class="nums">
        <div><b>{{ num(s.lights) }}</b><span>路灯</span></div>
        <div><b>{{ num(s.lamps) }}</b><span>灯盏</span></div>
        <div><b>{{ inServiceRate ?? '—' }}<small v-if="inServiceRate">%</small></b><span>在用率</span></div>
      </div>
      <template v-if="status.length">
        <div class="stack"><i v-for="x in status" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
        <div class="keys"><span v-for="x in status" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span></div>
      </template>
      <template v-if="lamps.length">
        <div class="sub">灯型</div>
        <div class="hb">
          <div v-for="x in lamps" :key="x.k">
            <span>{{ LAMP_TYPE[x.k] ?? x.k }}</span>
            <i><u :style="{ width: (x.n / lamps[0].n * 100) + '%', background: '#F2D45C' }" /></i>
            <b>{{ num(x.n) }}</b>
          </div>
        </div>
      </template>
    </section>

    <section class="card" style="order:1">
      <h2>公交站<span class="src">theme.ops_bus_stop</span></h2>
      <div class="nums">
        <div><b>{{ num(s.busStops) }}</b><span>公交站</span></div>
        <div><b>{{ num(s.busPlatforms) }}</b><span>站台</span></div>
        <div />
      </div>
      <div v-if="ops.busStops?.length" class="rows" style="--cols: minmax(0,1fr) 3.4rem">
        <div class="row hd"><span>站名</span><span>站台</span></div>
        <button v-for="b in ops.busStops" :key="b.stopId" class="row" @click="b.cx != null && emit('focus', b.cx, b.cy!)">
          <span><b :title="b.name">{{ b.name ?? b.stopId }}</b><small>{{ b.codes }}</small></span>
          <span>{{ b.platforms }}</span>
        </button>
      </div>
      <p v-if="(ops.busStops?.length ?? 0) >= 100" style="margin-top:.5rem">按站名列前 100 个。</p>
    </section>

    <!-- 本地块的执法记录（地块及以下） -->
    <section v-if="ops.plotRecords?.length" class="card" style="order:-1">
      <h2>本地块的上报、违规与占用案件<span class="chip">{{ ops.plotRecords.length }}</span></h2>
      <div class="rows" style="--cols: minmax(0,1fr) 4.4rem 5.2rem; max-height: 16rem; overflow: auto">
        <div class="row hd"><span>事项</span><span>类型</span><span>上报</span></div>
        <div v-for="(x, i) in ops.plotRecords" :key="i" class="row">
          <span><b :title="x.item">{{ x.item ?? x.number }}</b><small>{{ x.number }}{{ x.status ? ' · ' + x.status : '' }}{{ x.closedOn ? ' · 关闭 ' + x.closedOn.slice(0, 10) : '' }}{{ x.fineAed ? ' · 罚款 ' + num(x.fineAed) + ' AED' : '' }}{{ x.withinSla === false ? ' · 超时' : '' }}</small></span>
          <span><i class="sw" :style="{ background: OPS_RECORD[x.type]?.c, marginRight: '.3rem' }" />{{ OPS_RECORD[x.type]?.n ?? x.type }}</span>
          <span>{{ x.raisedOn?.slice(0, 10) ?? '—' }}</span>
        </div>
      </div>
    </section>

    <!-- 居民诉求（ADDA） -->
    <section class="card" :class="{ muted: !has('ops_case') }" :style="{ order: bizOrder }">
      <h2>居民诉求<span class="src">theme.ops_case</span></h2>
      <p v-if="scopeNote && bizOrder === 0" style="margin:-.2rem 0 .5rem">{{ scopeNote }}（诉求、事故、视觉违规、违规、占用）</p>
      <SrcPending :sources="src" :tables="['ops_case']" />
      <template v-if="has('ops_case') && cs?.summary">
        <div class="nums">
          <div><b>{{ num(cs.summary.cases12m) }}</b><span>近 12 个月工单</span></div>
          <div><b>{{ pct(cs.summary.closedPct) }}<small v-if="cs.summary.closedPct != null">%</small></b><span>办结率</span></div>
          <div><b>{{ pct(cs.summary.slaPct) }}<small v-if="cs.summary.slaPct != null">%</small></b><span>SLA 达标率*</span></div>
        </div>
        <template v-if="cs.byMonth?.length">
          <div class="sub">每月开立与办结</div>
          <MonthBars :rows="rows(cs.byMonth)" :series="[{ key: 'opened', name: '开立', c: '#4C8DF6' }, { key: 'closed', name: '办结', c: '#62D36A' }]" />
        </template>
        <template v-if="cs.byCategory?.length">
          <div class="sub">按类别</div>
          <div class="hb">
            <div v-for="x in cs.byCategory" :key="x.k"><span :title="x.k">{{ x.k }}</span><i><u :style="{ width: (x.n / maxN(cs.byCategory) * 100) + '%', background: '#3DD6D0' }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </template>
        <p style="margin-top:.5rem">* SLA 暂按工单的 SLA 颜色为 Green 计（已办结工单中），待 SLA 合规表到位后改口径；满意度源表未提供。以最晚开立日 {{ cs.summary.asOf ?? '—' }} 为准。</p>
      </template>
    </section>

    <!-- 出行与道路安全 -->
    <section class="card" :class="{ muted: !has('ops_accident') }" :style="{ order: bizOrder }">
      <h2>出行与道路安全<span class="src">theme.ops_accident</span></h2>
      <SrcPending :sources="src" :tables="['ops_accident']" />
      <template v-if="has('ops_accident') && ac?.summary">
        <div class="nums">
          <div><b>{{ num(ac.summary.accidents12m) }}</b><span>近 12 个月事故</span></div>
          <div v-for="x in (ac.bySeverity ?? []).slice(0, 2)" :key="x.k"><b :style="{ color: sevColor(x.k) }">{{ num(x.n) }}</b><span>{{ x.k }}</span></div>
        </div>
        <template v-if="ac.bySeverity?.length">
          <div class="stack"><i v-for="x in ac.bySeverity" :key="x.k" :style="{ flex: x.n, background: sevColor(x.k) }" /></div>
          <div class="keys"><span v-for="x in ac.bySeverity" :key="x.k"><i class="sw" :style="{ background: sevColor(x.k) }" />{{ x.k }}<b>{{ num(x.n) }}</b></span></div>
        </template>
        <template v-if="ac.byMonth?.length">
          <div class="sub">每月事故</div>
          <MonthBars :rows="rows(ac.byMonth)" :series="[{ key: 'n', name: '事故', c: '#E86A6A' }]" />
        </template>
        <template v-if="ac.byKind?.length">
          <div class="sub">事故类型</div>
          <div class="hb">
            <div v-for="x in ac.byKind.slice(0, 6)" :key="x.k"><span :title="x.k">{{ x.k }}</span><i><u :style="{ width: (x.n / maxN(ac.byKind) * 100) + '%', background: '#F0A64A' }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </template>
      </template>
    </section>

    <!-- 视觉违规上报 -->
    <section class="card" :class="{ muted: !has('ops_distortion') }" :style="{ order: bizOrder }">
      <h2>视觉违规上报<span class="src">theme.ops_distortion</span></h2>
      <SrcPending :sources="src" :tables="['ops_distortion']" />
      <template v-if="has('ops_distortion') && ds?.summary">
        <div class="nums">
          <div><b>{{ num(ds.summary.reports12m) }}</b><span>近 12 个月上报</span></div>
          <div><b :class="{ bad: ds.summary.slaFixedPct != null && ds.summary.slaFixedPct < 68 }">{{ pct(ds.summary.slaFixedPct) }}<small v-if="ds.summary.slaFixedPct != null">%</small></b><span>按期整改率</span></div>
          <div><b>{{ pct(ds.summary.duplicatePct) }}<small v-if="ds.summary.duplicatePct != null">%</small></b><span>重复上报</span></div>
        </div>
        <div class="nums">
          <div><b>{{ num(ds.summary.open) }}</b><span>未关闭</span></div>
          <div><b>{{ num(ds.summary.convertedToViolation) }}</b><span>升级为违规</span></div>
          <div />
        </div>
        <template v-if="ds.byMonth?.length">
          <div class="sub">每月上报与升级为违规</div>
          <MonthBars :rows="rows(ds.byMonth)" :series="[{ key: 'reports', name: '上报', c: '#FF7AB6' }, { key: 'convertedToViolation', name: '升级为违规', c: '#E86A6A' }]" />
        </template>
        <template v-if="ds.byService?.length">
          <div class="sub">按类别</div>
          <div class="hb">
            <div v-for="x in ds.byService.slice(0, 6)" :key="x.k"><span :title="x.k">{{ x.k }}</span><i><u :style="{ width: (x.n / maxN(ds.byService) * 100) + '%', background: '#FF7AB6' }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </template>
        <template v-if="ds.byEntity?.length">
          <div class="sub">责任实体</div>
          <div class="rows" style="--cols: minmax(0,1fr) 3.6rem 5rem; max-height: 14rem; overflow: auto">
            <div class="row hd"><span>实体</span><span>上报</span><span>按期整改</span></div>
            <div v-for="x in ds.byEntity" :key="x.k" class="row"><span>{{ x.k }}</span><span>{{ num(x.n) }}</span><span>{{ pct(x.slaFixedPct) }}{{ x.slaFixedPct != null ? '%' : '' }}</span></div>
          </div>
        </template>
        <p style="margin-top:.5rem">按期整改 = 在时限内关闭的上报 ÷ 有时限的上报。原型的三类归类（基础设施 / 建筑外观 / 个人行为）待业务给出条目映射，先按服务类别分组。</p>
      </template>
    </section>

    <!-- 违规与处罚 -->
    <section class="card" :class="{ muted: !has('ops_violation') }" :style="{ order: bizOrder }">
      <h2>违规与处罚<span class="src">theme.ops_violation</span></h2>
      <SrcPending :sources="src" :tables="['ops_violation']" />
      <template v-if="has('ops_violation') && vs?.summary">
        <div class="nums">
          <div><b>{{ num(vs.summary.violations) }}</b><span>违规（不含重复）</span></div>
          <div><b>{{ num(vs.summary.open) }}</b><span>未结</span></div>
          <div><b>{{ pct(vs.summary.collectionPct) }}<small v-if="vs.summary.collectionPct != null">%</small></b><span>罚款收缴率</span></div>
        </div>
        <div class="stack"><i v-for="x in stages" :key="x.k" :style="{ flex: x.n, background: x.c }" /></div>
        <div class="keys"><span v-for="x in stages" :key="x.k"><i class="sw" :style="{ background: x.c }" />{{ x.n2 }}<b>{{ num(x.n) }}</b></span></div>
        <p style="margin-top:.4rem">罚款 {{ big(vs.summary.fineAed) }} AED，已收 {{ big(vs.summary.receivedAed) }} AED。</p>
        <template v-if="vs.byService?.length">
          <div class="sub">按服务类别</div>
          <div class="hb">
            <div v-for="x in vs.byService.slice(0, 6)" :key="x.k"><span :title="x.k">{{ x.k }}</span><i><u :style="{ width: (x.n / maxN(vs.byService) * 100) + '%', background: '#E86A6A' }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </template>
        <template v-if="vs.byItem?.length">
          <div class="sub">违规条目</div>
          <div class="rows" style="--cols: minmax(0,1fr) 3.2rem 3.2rem 4.6rem; max-height: 14rem; overflow: auto">
            <div class="row hd"><span>条目</span><span>数量</span><span>未结</span><span>罚款</span></div>
            <div v-for="x in vs.byItem" :key="x.k" class="row"><span :title="x.k">{{ x.k }}</span><span>{{ num(x.n) }}</span><span>{{ num(x.open) }}</span><span>{{ big(x.fineAed) }}</span></div>
          </div>
        </template>
        <p style="margin-top:.5rem">原型按三部法律（城市外观法 / 建筑处罚 / 占用法）分组，待业务给出条目映射，先按服务类别分组。</p>
      </template>
    </section>

    <!-- 住宅占用 -->
    <section class="card" :class="{ muted: !has('ops_occupancy_case') }" :style="{ order: bizOrder }">
      <h2>住宅占用（超员）<span class="src">theme.ops_occupancy_case</span></h2>
      <SrcPending :sources="src" :tables="['ops_occupancy_case']" />
      <template v-if="has('ops_occupancy_case') && oc?.summary">
        <div class="nums">
          <div><b>{{ num(oc.summary.cases) }}</b><span>案件</span></div>
          <div><b>{{ num(oc.summary.open) }}</b><span>未结</span></div>
          <div><b>{{ num(oc.summary.urgent) }}</b><span>紧急</span></div>
        </div>
        <template v-if="oc.byStage?.length">
          <div class="sub">按所处环节</div>
          <div class="hb">
            <div v-for="x in oc.byStage" :key="x.k"><span :title="x.k">{{ x.k }}</span><i><u :style="{ width: (x.n / maxN(oc.byStage) * 100) + '%', background: '#B58CFF' }" /></i><b>{{ num(x.n) }}</b></div>
          </div>
        </template>
      </template>
    </section>
  </template>
</template>

<style scoped>
.nums b.bad { color: #FF8A8A }
</style>
