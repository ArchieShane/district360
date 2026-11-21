<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import DistrictList from './components/DistrictList.vue'
import DistrictPanel from './components/DistrictPanel.vue'
import IntroCard from './components/IntroCard.vue'
import LineageTip from './components/LineageTip.vue'
import MapView from './components/MapView.vue'
import NotesModal from './components/NotesModal.vue'
import { useDistrictStore } from './stores/district'
import { dmy } from './utils/format'

const store = useDistrictStore()
const { t } = useI18n()

// 与 CSS 变量 --pw / --nw 保持一致，用于地图飞行时留出面板位置
const vw = ref(window.innerWidth)
const panelWidth = computed(() => (vw.value <= 1400 ? 440 : vw.value <= 1600 ? 480 : 590))
const listWidth = computed(() => (vw.value <= 1400 ? 214 : vw.value <= 1600 ? 232 : 250))
const onResize = () => (vw.value = window.innerWidth)

const footSources = computed(() =>
  (store.meta?.sources ?? []).map(s => `${t('src.' + s.source)} ${s.sample ? t('foot.sample') : dmy(s.sourceAsOf)}`).join(' · '),
)

const onHash = () => store.readHash()

onMounted(() => {
  window.addEventListener('resize', onResize)
  window.addEventListener('hashchange', onHash)
  void store.load()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('hashchange', onHash)
})
</script>

<template>
  <div :class="{ open: !!store.selectedCode }" class="root">
    <MapView :panel-width="panelWidth" :list-width="listWidth" />
    <div class="vig"></div>

    <header class="hd">
      <div class="k">{{ t('header.kicker') }}</div>
      <h1><b>District 360</b> · {{ t('header.title') }}</h1>
      <div class="sub">{{ t('header.sub') }}</div>
    </header>

    <DistrictList />

    <div class="tb">
      <button type="button" class="tbb lng" @click="store.setLang(store.lang === 'zh' ? 'en' : 'zh')">
        <span :class="{ on: store.lang === 'zh' }">中</span> / <span :class="{ on: store.lang === 'en' }">EN</span>
      </button>
      <button type="button" class="tbb" @click="store.notesOpen = true">{{ t('toolbar.notes') }}</button>
      <button type="button" class="tbb" @click="store.goHome()">{{ t('toolbar.reset') }}</button>
    </div>

    <IntroCard v-if="store.boundaries" />

    <div class="lgw">
      <div class="lg">
        <template v-if="!store.selectedCode">
          <span>{{ t('legend.idle') }}</span><span class="sep"></span><span>{{ t('legend.controls') }}</span>
        </template>
        <template v-else>
          <span><i style="background: #a9d0f7"></i>{{ t('legend.selected') }}</span>
          <span><i style="background: #ffb547"></i>{{ t('legend.uc') }}</span>
          <span><i style="background: #2b3643"></i>{{ t('legend.outside') }}</span>
          <span class="sep"></span><span>{{ t('legend.esc') }}</span>
        </template>
      </div>
    </div>

    <div class="foot">
      <template v-if="store.error">{{ t('foot.error', { msg: store.error }) }}</template>
      <template v-else-if="store.meta">{{ t('foot.dataDate') }}：{{ footSources }}</template>
    </div>

    <DistrictPanel />
    <NotesModal />
    <LineageTip />
  </div>
</template>
