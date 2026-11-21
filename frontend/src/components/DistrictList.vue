<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDistrictStore } from '../stores/district'
import { nf } from '../utils/format'

/** 左侧区列表：分页加载，滚动到底自动取下一页；搜索交给后端（输入停顿 250ms 后查询）。 */
const store = useDistrictStore()
const { t } = useI18n()
const q = ref('')
const scroller = ref<HTMLDivElement>()
const sentinel = ref<HTMLDivElement>()
let observer: IntersectionObserver | null = null
let searchTimer: number | undefined

watch(q, v => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => void store.resetList(v.trim()), 250)
})

function enter() {
  if (store.list[0]) void store.select(store.list[0].code)
}

onMounted(() => {
  // 哨兵元素进入列表可视区（提前 120px）就加载下一页
  observer = new IntersectionObserver(
    entries => {
      if (entries.some(e => e.isIntersecting)) void store.loadMore()
    },
    { root: scroller.value, rootMargin: '0px 0px 120px 0px' },
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

// 一页加载完哨兵仍在可视区内（列表不够长）时，继续加载
watch(
  () => store.list.length,
  () => {
    requestAnimationFrame(() => {
      const root = scroller.value
      const el = sentinel.value
      if (!root || !el || !store.listHasMore) return
      if (el.getBoundingClientRect().top <= root.getBoundingClientRect().bottom + 120) void store.loadMore()
    })
  },
)

onBeforeUnmount(() => {
  observer?.disconnect()
  window.clearTimeout(searchTimer)
})
</script>

<template>
  <nav class="dl">
    <div class="h">
      <span>{{ t('list.title') }}</span>
      <span>{{ t('list.count', { loaded: store.list.length, total: store.listTotal }) }}</span>
    </div>
    <input v-model="q" class="q" autocomplete="off" :placeholder="t('list.search')" @keydown.enter="enter" />
    <div ref="scroller" class="dlist">
      <button
        v-for="d in store.list"
        :key="d.code"
        type="button"
        class="dr"
        :class="{ on: d.code === store.selectedCode, hv: d.code === store.hoveredCode }"
        @click="store.select(d.code)"
        @mouseenter="store.hoveredCode = d.code"
        @mouseleave="store.hoveredCode = null"
      >
        <i></i>
        <span class="n">{{ d.nameEn }}</span>
        <span class="mu" data-m="municipality" :data-code="d.code">{{ d.municipality }}</span>
        <span class="p" data-m="area_km2" :data-code="d.code">{{ d.areaKm2 != null ? nf(d.areaKm2, 2) + ' km²' : '—' }}</span>
      </button>
      <div ref="sentinel" class="sentinel"></div>
      <div v-if="store.listLoading" class="more">{{ t('foot.loading') }}</div>
      <div v-else-if="!store.list.length" class="empty">{{ t('list.noMatch') }}</div>
    </div>
  </nav>
</template>
