<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FacilityItem } from '../api/types'
import { useDistrictStore } from '../stores/district'

/** 面板里展开的公共设施列表：搜索、悬停联动地图、点击定位。 */
const store = useDistrictStore()
const { t, locale } = useI18n()
const q = ref('')
const listEl = ref<HTMLDivElement>()

/** 清真寺类型（源数据为阿拉伯文） */
const MOSQUE_TYPE: Record<string, { zh: string; en: string }> = {
  'جامع': { zh: '聚礼清真寺', en: 'Friday mosque' },
  'مسجد': { zh: '清真寺', en: 'Mosque' },
}

const items = computed(() => {
  const k = q.value.trim().toLowerCase()
  if (!k) return store.facilityList
  return store.facilityList.filter(f => (f.nameEn ?? '').toLowerCase().includes(k) || (f.nameAr ?? '').includes(k) || (f.subtype ?? '').toLowerCase().includes(k))
})

function name(f: FacilityItem) {
  return (locale.value === 'en' ? f.nameEn || f.nameAr : f.nameEn || f.nameAr) ?? f.id
}
function sub(f: FacilityItem) {
  const parts: string[] = []
  if (f.nameEn && f.nameAr) parts.push(f.nameAr)
  if (f.subtype) {
    const m = MOSQUE_TYPE[f.subtype]
    parts.push(m ? (locale.value === 'en' ? m.en : m.zh) : f.subtype)
  }
  return parts.join(' · ')
}

watch(() => store.facilityCategory, () => (q.value = ''))
// 地图上点击了某个设施：列表滚动到这一项
watch(() => store.focusedPoiId, async id => {
  if (!id) return
  await nextTick()
  listEl.value?.querySelector<HTMLElement>(`[data-poi="${CSS.escape(id)}"]`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
})
</script>

<template>
  <div v-if="store.facilityCategory" class="facl">
    <div class="facl-h">
      <b>{{ t('fac.cat.' + store.facilityCategory) }} · {{ store.facilityLoading ? '…' : store.facilityList.length }}</b>
      <input v-if="store.facilityList.length > 8" v-model="q" class="facl-q" :placeholder="t('fac.search')" />
      <button type="button" class="facl-x" :aria-label="t('fac.close')" @click="store.clearFacilities()">×</button>
    </div>
    <div ref="listEl" class="facl-list">
      <button
        v-for="f in items"
        :key="f.id"
        type="button"
        class="facl-i"
        :class="{ on: store.focusedPoiId === f.id }"
        :data-poi="f.id"
        :title="t('fac.locate')"
        @click="store.focusPoi(f.id)"
        @mouseenter="store.hoveredPoiId = f.id"
        @mouseleave="store.hoveredPoiId = null"
      >
        <i :class="'dot ' + f.category"></i>
        <span class="facl-t">
          <span class="facl-n" :dir="f.nameEn ? 'ltr' : 'auto'">{{ name(f) }}</span>
          <span v-if="sub(f)" class="facl-s" dir="auto">{{ sub(f) }}</span>
        </span>
        <span class="facl-go">⌖</span>
      </button>
      <div v-if="!store.facilityLoading && !items.length" class="facl-empty">{{ t('fac.empty') }}</div>
    </div>
  </div>
</template>
