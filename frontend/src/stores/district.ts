import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../api/client'
import type { DistrictFeatureCollection, DistrictFeatureProps, DistrictListItem, DistrictOverview, LineageInfo, MetaInfo } from '../api/types'
import { i18n, type Lang } from '../i18n'

const PAGE_SIZE = 30

/**
 * 页面状态。
 * - 地图数据（makani 全部区的边界 + 属性）一次性加载，用于地图、悬停提示、选中区；
 * - 左侧列表分页加载：滚动到底再取下一页，搜索交给后端；
 * - 选中区、语言与 URL hash（#d=adm-al-reem-island&l=en）同步。
 */
export const useDistrictStore = defineStore('district', () => {
  const boundaries = ref<DistrictFeatureCollection | null>(null)
  const meta = ref<MetaInfo | null>(null)
  const lineage = ref<LineageInfo | null>(null)
  const selectedCode = ref<string | null>(null)
  const hoveredCode = ref<string | null>(null)
  /** 面板「地块状态」图例悬停时高亮的状态：constructed / under_construction / vacant_serviced / vacant_unserviced */
  const highlightPlotStatus = ref<string | null>(null)
  const overviews = ref<Record<string, DistrictOverview>>({})
  const loadingOverview = ref(false)
  const error = ref<string | null>(null)
  const lang = ref<Lang>('zh')
  const notesOpen = ref(false)
  /** 递增一次 = 请求地图回到全景 */
  const homeTick = ref(0)

  // 左侧列表（分页）
  const list = ref<DistrictListItem[]>([])
  const listTotal = ref(0)
  const listHasMore = ref(true)
  const listLoading = ref(false)
  const listQuery = ref('')
  let listPage = 0
  let listToken = 0
  let listStarted = false

  /** code → 地图属性（全部区） */
  const features = computed(() => {
    const m = new Map<string, DistrictFeatureProps>()
    boundaries.value?.features.forEach(f => m.set(f.properties.code, f.properties))
    return m
  })
  const selected = computed(() => (selectedCode.value ? features.value.get(selectedCode.value) ?? null : null))
  const overview = computed(() => (selectedCode.value ? overviews.value[selectedCode.value] ?? null : null))
  /** 数据版本：最近一次 makani 抽取完成时间，用于地图数据和建筑瓦片的缓存失效 */
  const dataVersion = computed(() => meta.value?.sources.find(s => s.source === 'makani')?.lastFinishedAt ?? 'init')

  async function load() {
    const listReady = resetList('') // 列表第一页与地图数据并行加载
    try {
      const [m, lin] = await Promise.all([api.meta(), api.lineage()])
      meta.value = m
      lineage.value = lin
      boundaries.value = await api.boundaries(dataVersion.value)
      readHash()
    } catch (e) {
      error.value = (e as Error).message
    }
    await listReady
  }

  /** 换搜索词：清空列表，从第一页重新加载 */
  async function resetList(q: string) {
    listStarted = true
    listQuery.value = q
    list.value = []
    listPage = 0
    listHasMore.value = true
    listToken++ // 让还在路上的旧请求作废
    listLoading.value = false
    await loadMore()
  }

  /** 加载下一页（滚动到底时调用）；并发请求、过期请求都会被忽略 */
  async function loadMore() {
    // 首次加载由 load() 发起；在那之前（例如列表组件刚挂载时滚动监听触发）不加载
    if (!listStarted || listLoading.value || !listHasMore.value) return
    listLoading.value = true
    const token = listToken
    try {
      const page = await api.districts(listPage, PAGE_SIZE, listQuery.value || undefined)
      if (token !== listToken) return
      list.value.push(...page.items)
      listTotal.value = page.total
      listHasMore.value = page.hasMore
      listPage++
    } catch (e) {
      error.value = (e as Error).message
    } finally {
      if (token === listToken) listLoading.value = false
    }
  }

  async function select(code: string) {
    if (!features.value.has(code)) return
    selectedCode.value = code
    writeHash()
    if (!overviews.value[code]) {
      loadingOverview.value = true
      try {
        overviews.value[code] = await api.overview(code)
      } catch (e) {
        error.value = (e as Error).message
      } finally {
        loadingOverview.value = false
      }
    }
  }

  function deselect() {
    selectedCode.value = null
    highlightPlotStatus.value = null
    writeHash()
    homeTick.value++
  }

  function goHome() {
    if (selectedCode.value) deselect()
    else homeTick.value++
  }

  function setLang(l: Lang) {
    lang.value = l
    i18n.global.locale.value = l
    document.documentElement.lang = l
    writeHash()
  }

  function readHash() {
    const p = new URLSearchParams(location.hash.slice(1))
    const l = p.get('l')
    if (l === 'en' || l === 'zh') setLang(l)
    const d = p.get('d')
    if (d && d !== selectedCode.value) void select(d)
    else if (!d && selectedCode.value) deselect()
  }

  function writeHash() {
    const p = new URLSearchParams()
    if (selectedCode.value) p.set('d', selectedCode.value)
    if (lang.value !== 'zh') p.set('l', lang.value)
    const h = p.toString()
    history.replaceState(null, '', h ? `#${h}` : location.pathname + location.search)
  }

  return {
    boundaries, features, meta, lineage, selectedCode, hoveredCode, highlightPlotStatus, overviews, loadingOverview, error, lang, notesOpen, homeTick,
    list, listTotal, listHasMore, listLoading, listQuery,
    selected, overview, dataVersion, load, resetList, loadMore, select, deselect, goHome, setLang, readHash,
  }
})
