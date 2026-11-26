<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { api, type AstCategory, type AstItem, type AstView, type NodeDetail } from '../api'
import { num } from '../format'
import { AST_ATTR, AST_CATEGORY, LEVEL_NAME } from '../labels'

/**
 * 设施页签（ods-ast）：学校（学位、在校生、使用率）、医疗（床位、医院）、药房、清真寺（礼拜人数）、公园（个数、面积），
 * 每类的分类与列表；点一项展开详情、地图定位。建筑 / 楼层 / 单元按所在地块统计。
 */
const props = defineProps<{ node: NodeDetail }>()
const emit = defineEmits<{ focus: [lon: number, lat: number] }>()

const ast = shallowRef<AstView | null>(null)
const error = shallowRef<string | null>(null)
const cat = ref<AstCategory>('school')
const open = ref<string | null>(null)
let ctrl: AbortController | null = null

watch(() => [props.node.level, props.node.key], async () => {
  ctrl?.abort()
  ctrl = new AbortController()
  ast.value = null
  error.value = null
  open.value = null
  try {
    ast.value = await api.ast(props.node.level, props.node.key, ctrl.signal)
  } catch (e) {
    if ((e as Error).name !== 'AbortError') error.value = (e as Error).message
  }
}, { immediate: true })

const s = computed(() => ast.value?.summary)
const scopeNote = computed(() => {
  const sc = ast.value?.scope
  return sc && sc.level !== props.node.level ? `按所在${LEVEL_NAME[sc.level]}统计` : ''
})
const counts = computed<Record<AstCategory, number>>(() => ({
  school: s.value?.schools ?? 0, medical: s.value?.medical ?? 0, pharmacy: s.value?.pharmacies ?? 0,
  mosque: s.value?.mosques ?? 0, park: s.value?.parks ?? 0,
}))
const subtypes = computed(() => ast.value?.bySubtype?.[cat.value] ?? [])
const items = computed(() => ast.value?.items?.[cat.value] ?? [])

function attrs(it: AstItem): [string, string][] {
  if (!it.attrs) return []
  try {
    const o = JSON.parse(it.attrs) as Record<string, unknown>
    return Object.entries(o)
      .filter(([, v]) => v !== null && v !== '' && v !== 'N/A')
      .map(([k, v]) => [AST_ATTR[k] ?? k, typeof v === 'number' && Number.isInteger(v) ? num(v) : String(v)])
  } catch {
    return []
  }
}

function pick(it: AstItem) {
  const id = it.category + it.id
  open.value = open.value === id ? null : id
  if (it.cx != null && it.cy != null) emit('focus', it.cx, it.cy)
}

/** 建筑资产（原型 Assets 页签的 Building assets）：用节点自身汇总，不按设施的地块口径 */
const bst = computed(() => props.node.stat)
const showAssets = computed(() => ['emirate', 'municipality', 'district', 'community', 'plot'].includes(props.node.level) && (bst.value.buildings ?? 0) > 0)
const ha = (v: number | undefined) => num((v ?? 0) / 1e4, 1)

const capLabel: Record<AstCategory, string> = { school: '学位', medical: '床位', pharmacy: '', mosque: '礼拜人数', park: '面积' }
</script>

<template>
  <section v-if="error" class="card"><p class="err">{{ error }}</p></section>
  <section v-else-if="!ast" class="card muted"><p>加载中…</p></section>
  <section v-else-if="!ast.scope" class="card muted"><p>该建筑没有对应的地块，没有设施数据。</p></section>
  <template v-else-if="s">
    <section v-if="showAssets" class="card">
      <h2>建筑资产<span class="src">theme.sp_building · sp_floor · sp_unit</span></h2>
      <div class="nums">
        <div><b>{{ num(bst.buildings) }}</b><span>建筑 · 在建 {{ num(bst.buildingsUnderConstruction) }}</span></div>
        <div><b>{{ num(bst.floors) }}</b><span>楼层（BDMS）</span></div>
        <div><b>{{ num(bst.units) }}</b><span>单元（BDMS）</span></div>
      </div>
      <div class="nums">
        <div><b>{{ ha(bst.floorsAreaSqm) }}<small>ha</small></b><span>建筑面积（楼层合计）</span></div>
        <div><b>{{ ha(bst.unitsAreaSqm) }}<small>ha</small></b><span>使用面积（单元合计）</span></div>
        <div><b>{{ ha(bst.plotsMaxGfaSqm) }}<small>ha</small></b><span>可建面积（地块管控合计）</span></div>
      </div>
      <p v-if="node.level !== 'plot'" style="margin-top:.6rem">不少地块没有可建面积登记，所以这里不算使用率；单个地块的使用率见地块概况。</p>
      <p v-if="bst.unitsAreaOutliers" style="margin-top:.3rem">另有 {{ num(bst.unitsAreaOutliers) }} 个单元面积超过 10,000 m²，视为源数据异常，未计入使用面积。</p>
    </section>

    <section class="card">
      <h2>公共设施<span class="src">theme.ast_facility</span></h2>
      <p v-if="scopeNote" style="margin:-.2rem 0 .5rem">{{ scopeNote }}</p>
      <div class="nums">
        <div><b>{{ num(s.schools) }}</b><span>学校 · {{ num(s.schoolCapacity) }} 学位</span></div>
        <div><b>{{ num(s.students) }}</b><span>在校生</span></div>
        <div><b>{{ s.seatUtilization ?? '—' }}<small v-if="s.seatUtilization != null">%</small></b><span>学位使用率</span></div>
      </div>
      <div class="nums">
        <div><b>{{ num(s.medical) }}</b><span>医疗 · 其中医院 {{ num(s.hospitals) }}</span></div>
        <div><b>{{ num(s.beds) }}</b><span>床位</span></div>
        <div><b>{{ num(s.pharmacies) }}</b><span>药房</span></div>
      </div>
      <div class="nums">
        <div><b>{{ num(s.mosques) }}</b><span>清真寺</span></div>
        <div><b>{{ num(s.worshippers) }}</b><span>可容纳礼拜人数</span></div>
        <div><b>{{ num(s.parks) }}</b><span>公园 · {{ num(s.parkAreaHa, 1) }} ha</span></div>
      </div>
    </section>

    <section class="card">
      <div class="chips" style="margin:0 0 .6rem">
        <button v-for="(v, k) in AST_CATEGORY" :key="k" class="chip" :style="cat === k ? { background: v.c, color: '#10141A' } : {}"
                @click="cat = k as AstCategory; open = null">
          {{ v.n }} {{ num(counts[k as AstCategory]) }}
        </button>
      </div>
      <template v-if="subtypes.length">
        <div class="sub" style="margin-top:0">分类</div>
        <div class="hb">
          <div v-for="x in subtypes" :key="x.k">
            <span :title="x.k">{{ x.k }}</span>
            <i><u :style="{ width: (x.n / subtypes[0].n * 100) + '%', background: AST_CATEGORY[cat].c }" /></i>
            <b>{{ num(x.n) }}</b>
          </div>
        </div>
      </template>
      <div v-if="items.length" class="rows" style="--cols: minmax(0,1fr) 5.4rem">
        <div class="row hd"><span>{{ AST_CATEGORY[cat].n }}</span><span>{{ capLabel[cat] }}</span></div>
        <template v-for="it in items" :key="it.category + it.id">
          <button class="row" :class="{ on: open === it.category + it.id }" @click="pick(it)">
            <span><b :title="it.name">{{ it.name ?? it.id }}</b><small>{{ [it.subtype, it.subCategory].filter(x => x && x !== 'N/A').join(' · ') }}</small></span>
            <span>{{ cat === 'park' ? (it.areaSqm ? num(it.areaSqm / 10000, 1) + ' ha' : '—') : cat === 'pharmacy' ? '' : num(it.capacity) }}</span>
          </button>
          <dl v-if="open === it.category + it.id" class="kv" style="padding:.3rem .4rem .6rem 1.2rem">
            <template v-if="it.nameAr && it.nameAr !== it.name"><dt>阿拉伯文名</dt><dd>{{ it.nameAr }}</dd></template>
            <template v-if="it.students != null"><dt>在校生 / 教职工</dt><dd>{{ num(it.students) }} / {{ num(it.staff) }}</dd></template>
            <template v-for="[k, v] in attrs(it)" :key="k"><dt>{{ k }}</dt><dd :title="v">{{ v }}</dd></template>
          </dl>
        </template>
      </div>
      <p v-else style="margin-top:.4rem">没有{{ AST_CATEGORY[cat].n }}。</p>
      <p v-if="items.length >= 30" style="margin-top:.5rem">按{{ capLabel[cat] || '名称' }}列前 30 个。</p>
    </section>
  </template>
</template>
