<script setup lang="ts">
import { ref, watch } from 'vue'
import { api, type Level, type SearchHit } from '../api'
import { LEVEL_NAME } from '../labels'

/** 右上搜索：区域、社区名称；地块 gisid / 地块号；建筑编号 */
const emit = defineEmits<{ go: [level: Level, key: string] }>()
const q = ref('')
const hits = ref<SearchHit[]>([])
const open = ref(false)
let timer = 0

watch(q, v => {
  clearTimeout(timer)
  if (v.trim().length < 2) {
    hits.value = []
    return
  }
  timer = window.setTimeout(async () => {
    try {
      hits.value = await api.search(v.trim())
      open.value = true
    } catch {
      hits.value = []
    }
  }, 250)
})

function pick(h: SearchHit) {
  open.value = false
  q.value = ''
  emit('go', h.level, h.key)
}
</script>

<template>
  <div class="search">
    <input v-model="q" placeholder="搜索区域、社区、地块号 / GIS ID、建筑编号" @focus="open = true" @keydown.esc="open = false" />
    <div v-if="open && q.trim().length >= 2" class="pop">
      <ul>
        <li v-for="h in hits" :key="h.level + h.key">
          <button @click="pick(h)"><span>{{ h.name }}</span><small>{{ LEVEL_NAME[h.level] }}{{ h.sub ? ' · ' + h.sub : '' }}</small></button>
        </li>
      </ul>
      <div v-if="!hits.length" class="more">没有找到</div>
    </div>
  </div>
</template>
