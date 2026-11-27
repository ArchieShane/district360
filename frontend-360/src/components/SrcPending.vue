<script setup lang="ts">
import { computed } from 'vue'
import type { Sources } from '../api'

/**
 * 业务视图主题表没有数据时的提示（接口 sources 里该表 rows 为 0）：显示「待接入」与原因（如「ODS 源表无数据：…」）。
 * 有数据时什么都不渲染。tables 为该卡片依赖的主题表；任一有数据就算有数据。
 */
const props = defineProps<{ sources?: Sources; tables: string[] }>()
const empty = computed(() => !!props.sources && props.tables.every(t => !(props.sources![t]?.rows)))
const notes = computed(() => [...new Set(props.tables.map(t => props.sources?.[t]?.note).filter(Boolean))] as string[])
defineExpose({ empty })
</script>

<template>
  <div v-if="empty" class="pend">
    <span class="badge no">待接入</span>
    <span>{{ notes.join('；') || '源数据尚未到位' }}</span>
  </div>
</template>

<style scoped>
.pend { display: flex; align-items: baseline; gap: .5rem; font-size: .74rem; color: var(--tx3); line-height: 1.5; margin-top: .2rem }
.pend .badge { flex: none }
.pend span + span { word-break: break-all }
</style>
