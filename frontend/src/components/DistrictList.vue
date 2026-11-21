<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDistrictStore } from '../stores/district'
import { nf } from '../utils/format'

const store = useDistrictStore()
const { t } = useI18n()
const q = ref('')

const list = computed(() => {
  const k = q.value.trim().toLowerCase()
  return [...store.districts]
    .sort((a, b) => a.nameEn.localeCompare(b.nameEn))
    .filter(d => !k || d.nameEn.toLowerCase().includes(k))
})

function enter() {
  if (list.value[0]) void store.select(list.value[0].code)
}
</script>

<template>
  <nav class="dl">
    <div class="h"><span>{{ t('list.title') }}</span><span>{{ store.districts.length }} · A–Z</span></div>
    <input v-model="q" class="q" autocomplete="off" :placeholder="t('list.search')" @keydown.enter="enter" />
    <div class="dlist">
      <button
        v-for="d in list"
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
        <span v-if="!d.mappingConfirmed" class="tbc" :title="d.mappingNote ?? ''">TBC</span>
        <span class="p">{{ d.areaKm2 != null ? nf(d.areaKm2, 2) + ' km²' : '—' }}</span>
      </button>
      <div v-if="!list.length" class="empty">{{ t('list.noMatch') }}</div>
    </div>
  </nav>
</template>
