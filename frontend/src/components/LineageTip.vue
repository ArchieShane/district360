<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BlockStatus, SourceKey } from '../api/types'
import { useDistrictStore } from '../stores/district'

/**
 * 指标血缘悬浮提示：页面上任何带 data-m="<指标键>" 的元素，鼠标悬浮时显示
 * 该值读的是哪个库 / 表、源头是哪个库 / 表、怎么算的、对应 SQL（已代入当前区的 ID）。
 * 可选 data-code="<区编码>"，默认取当前选中的区。鼠标停留 3 秒后才显示。
 */
const store = useDistrictStore()
const { t, locale } = useI18n()

const active = ref<{ key: string; code: string | null; rect: DOMRect } | null>(null)
const pos = ref({ left: 0, top: 0 })
const box = ref<HTMLDivElement>()
const copied = ref(false)
let hideTimer: number | undefined

const metric = computed(() => (active.value ? store.lineage?.metrics[active.value.key] ?? null : null))
const district = computed(() => {
  const code = active.value?.code ?? store.selectedCode
  return code ? store.features.get(code) ?? null : null
})
const source = computed(() => (metric.value ? store.lineage?.sources[metric.value.source] ?? null : null))

const PARK_KEYS = new Set(['parks', 'park_area_ha', 'park_gap_ha'])

/** 当前值的状态：实测 / 不在宜居评估范围 / 没有查询到数据 */
const state = computed<BlockStatus | 'planned'>(() => {
  const m = metric.value
  if (!m) return 'unavailable'
  const o = store.overview && store.overview.district.code === district.value?.code ? store.overview : null
  const block: Record<SourceKey, BlockStatus | undefined> = {
    makani: o?.land.status,
    liveability: o?.liveability.status,
    adrec: o?.realEstate.status,
  }
  // 公园指标虽然来自宜居库，但不受「是否在宜居评估范围」影响，按公园区块自己的状态
  const s = PARK_KEYS.has(active.value!.key) ? o?.facilities.parks.status : block[m.source]
  if (s) return s
  return source.value?.implemented ? 'ok' : 'unavailable'
})

const sql = computed(() => {
  const m = metric.value
  if (!m?.sql) return null
  const d = district.value
  return m.sql
    .replaceAll(':municipality', d?.municipality ?? ':municipality')
    .replaceAll(':makani_district_id', d?.makaniDistrictId != null ? String(d.makaniDistrictId) : ':makani_district_id')
    .replaceAll(':district_code', d?.code ?? ':district_code')
    .replaceAll(':district_scope', d ? scopeLabel(d.nameEn, d.makaniDistrictId) : ':district_scope')
})

const zh = computed(() => locale.value === 'zh')

/** SQL 里的统计范围参数：说明它是哪个区的 makani 区面（实际执行时传入该区面的 EWKB） */
function scopeLabel(name: string, makaniId: number | null) {
  const id = makaniId != null ? ` districtid=${makaniId}` : ''
  return zh.value ? `<${name} 的统计范围：makani 区面${id}>` : `<scope of ${name}: makani polygon${id}>`
}

/** 鼠标在数值上停留多久才显示（毫秒） */
const SHOW_DELAY_MS = 3000
let showTimer: number | undefined
/** 正在等待显示 / 已经显示的那个数值元素 */
let hoverEl: HTMLElement | null = null

function onOver(e: MouseEvent) {
  const target = (e.target as HTMLElement | null)?.closest<HTMLElement>('[data-m]')
  if (!target || target === hoverEl) return
  const key = target.dataset.m!
  if (!store.lineage?.metrics[key]) return
  // 换到了另一个数值：重新计时，停留满 SHOW_DELAY_MS 才显示
  hoverEl = target
  window.clearTimeout(showTimer)
  if (active.value) scheduleHide()
  showTimer = window.setTimeout(() => {
    if (hoverEl !== target || !target.isConnected) return
    window.clearTimeout(hideTimer)
    active.value = { key, code: target.dataset.code ?? null, rect: target.getBoundingClientRect() }
    copied.value = false
    void nextTick(place)
  }, SHOW_DELAY_MS)
}

function onOut(e: MouseEvent) {
  const from = (e.target as HTMLElement | null)?.closest<HTMLElement>('[data-m]')
  if (!from) return
  const to = e.relatedTarget as Node | null
  if (to && (from.contains(to) || box.value?.contains(to))) return
  // 没停够就移开：取消计时；已显示：稍后收起（允许把鼠标移进提示框里复制 SQL）
  if (from === hoverEl) {
    hoverEl = null
    window.clearTimeout(showTimer)
  }
  scheduleHide()
}

function onBoxLeave() {
  hoverEl = null
  window.clearTimeout(showTimer)
  scheduleHide()
}

function keepOpen() {
  window.clearTimeout(hideTimer)
}

function scheduleHide() {
  window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => (active.value = null), 180)
}

function place() {
  if (!active.value || !box.value) return
  const r = active.value.rect
  const w = box.value.offsetWidth
  const h = box.value.offsetHeight
  const gap = 8
  let left = r.left + r.width / 2 - w / 2
  left = Math.max(12, Math.min(left, window.innerWidth - w - 12))
  let top = r.bottom + gap
  if (top + h > window.innerHeight - 12) top = Math.max(12, r.top - h - gap)
  pos.value = { left, top }
}

async function copySql() {
  if (!sql.value) return
  try {
    await navigator.clipboard.writeText(sql.value)
    copied.value = true
  } catch {
    copied.value = false
  }
}

onMounted(() => {
  document.addEventListener('mouseover', onOver)
  document.addEventListener('mouseout', onOut)
})
onBeforeUnmount(() => {
  window.clearTimeout(showTimer)
  window.clearTimeout(hideTimer)
  document.removeEventListener('mouseover', onOver)
  document.removeEventListener('mouseout', onOut)
})
</script>

<template>
  <div
    v-if="active && metric && source"
    ref="box"
    class="ltip"
    :style="{ left: pos.left + 'px', top: pos.top + 'px' }"
    @mouseenter="keepOpen"
    @mouseleave="onBoxLeave"
  >
    <div class="lt-h">
      <b>{{ zh ? metric.nameZh : metric.nameEn }}</b>
      <span v-if="district" class="lt-d">{{ district.nameEn }}</span>
      <span class="lt-s" :class="state">{{ t('lineage.state.' + state) }}</span>
    </div>

    <dl>
      <dt>{{ t('lineage.page') }}</dt>
      <dd><code>{{ store.lineage!.statDb }}</code> · <code>stat.{{ metric.statColumn }}</code></dd>
      <dt>{{ t('lineage.source') }}</dt>
      <dd>
        {{ zh ? source.nameZh : source.nameEn }} · <code>{{ source.database }}</code>
        <div class="lt-tables"><code v-for="tb in metric.sourceTables" :key="tb">{{ tb }}</code></div>
      </dd>
      <dt>{{ t('lineage.logic') }}</dt>
      <dd>{{ zh ? metric.logicZh : metric.logicEn }}</dd>
    </dl>

    <div v-if="!source.implemented" class="lt-note">{{ t('lineage.plannedNote') }}</div>

    <div v-if="sql" class="lt-sql">
      <div class="lt-sqlh">
        <span>{{ source.implemented ? t('lineage.sql') : t('lineage.sqlPlanned') }}</span>
        <button type="button" @click="copySql">{{ copied ? t('lineage.copied') : t('lineage.copy') }}</button>
      </div>
      <pre>{{ sql }}</pre>
    </div>
  </div>
</template>

<style scoped>
.ltip {
  position: fixed;
  z-index: 20;
  width: min(520px, calc(100vw - 24px));
  max-height: min(70vh, 560px);
  overflow-y: auto;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(13, 21, 31, 0.98);
  box-shadow: inset 0 0 0 1px rgba(127, 183, 240, 0.35), 0 12px 40px rgba(0, 0, 0, 0.55);
  font-size: 12.3px;
  line-height: 18px;
  color: var(--tm);
}
.lt-h { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.lt-h b { color: #fff; font-size: 13.5px; }
.lt-d { color: var(--tq); font-size: 11.5px; }
.lt-s { margin-left: auto; font-size: 10.5px; font-weight: 700; padding: 1px 7px; border-radius: 7px; white-space: nowrap; }
.lt-s.ok { background: rgba(95, 224, 160, 0.14); color: #9ff0c6; }
.lt-s.sample, .lt-s.planned { background: rgba(255, 181, 71, 0.14); color: #ffd08a; }
.lt-s.not_assessed, .lt-s.unavailable { background: rgba(154, 166, 178, 0.18); color: #c9d2dc; }
dl { display: grid; grid-template-columns: 64px 1fr; gap: 5px 10px; }
dt { color: var(--tq); font-weight: 600; }
dd { color: #e3e9ef; min-width: 0; overflow-wrap: anywhere; }
code { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 11.5px; color: #cfe5fa; }
.lt-tables { display: flex; flex-wrap: wrap; gap: 4px 10px; margin-top: 2px; }
.lt-note { margin-top: 8px; padding: 6px 9px; border-radius: 8px; background: rgba(255, 181, 71, 0.08); color: #ffd08a; font-size: 11.5px; }
.lt-sql { margin-top: 9px; border-radius: 9px; background: rgba(0, 0, 0, 0.3); box-shadow: inset 0 0 0 1px var(--line); }
.lt-sqlh { display: flex; justify-content: space-between; align-items: center; padding: 5px 9px; color: var(--tq); font-size: 11px; font-weight: 600; border-bottom: 1px solid var(--line); }
.lt-sqlh button { border: 0; background: rgba(127, 183, 240, 0.14); color: #cfe5fa; font-size: 11px; padding: 2px 8px; border-radius: 6px; cursor: pointer; }
pre { margin: 0; padding: 8px 10px; font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 11.3px; line-height: 16px; color: #dbe7f3; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
