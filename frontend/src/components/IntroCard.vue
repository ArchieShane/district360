<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDistrictStore } from '../stores/district'

const store = useDistrictStore()
const { t } = useI18n()
const first = computed(() => store.features.get('adm-al-reem-island') ?? store.boundaries?.features[0]?.properties ?? null)
</script>

<template>
  <div class="intro" :class="{ hide: !!store.selectedCode }">
    <h3>{{ t('intro.title') }}</h3>
    <p>{{ t('intro.text') }}</p>
    <div class="k3">
      <div><b>{{ store.meta?.districtCount ?? store.features.size }}</b>{{ t('intro.districts') }}</div>
      <div><b>{{ store.meta?.blockCount ?? 4 }}</b>{{ t('intro.blocks') }}</div>
    </div>
    <button v-if="first" type="button" class="go" @click="store.select(first.code)">{{ t('intro.go', { name: first.nameEn }) }}</button>
  </div>
</template>
