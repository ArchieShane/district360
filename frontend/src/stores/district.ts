import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { api } from '../api/client'
import type { DistrictListItem, DistrictOverview, LineageInfo, MetaInfo } from '../api/types'
import { i18n, type Lang } from '../i18n'

/** 页面状态：区列表、选中 / 悬停的区、概览缓存、语言；与 URL hash（#d=reem&l=en）同步。 */
export const useDistrictStore = defineStore('district', () => {
  const districts = ref<DistrictListItem[]>([])
  const meta = ref<MetaInfo | null>(null)
  const lineage = ref<LineageInfo | null>(null)
  const selectedCode = ref<string | null>(null)
  const hoveredCode = ref<string | null>(null)
  const overviews = ref<Record<string, DistrictOverview>>({})
  const loadingOverview = ref(false)
  const error = ref<string | null>(null)
  const lang = ref<Lang>('zh')
  const notesOpen = ref(false)
  /** 递增一次 = 请求地图回到全景 */
  const homeTick = ref(0)

  const selected = computed(() => districts.value.find(d => d.code === selectedCode.value) ?? null)
  const overview = computed(() => (selectedCode.value ? overviews.value[selectedCode.value] ?? null : null))

  async function load() {
    try {
      const [list, m, lin] = await Promise.all([api.districts(), api.meta(), api.lineage()])
      districts.value = list
      meta.value = m
      lineage.value = lin
      readHash()
    } catch (e) {
      error.value = (e as Error).message
    }
  }

  async function select(code: string) {
    if (!districts.value.some(d => d.code === code)) return
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
    districts, meta, lineage, selectedCode, hoveredCode, overviews, loadingOverview, error, lang, notesOpen, homeTick,
    selected, overview, load, select, deselect, goHome, setLang, readHash,
  }
})
