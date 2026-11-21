<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useDistrictStore } from '../stores/district'
import { dmy } from '../utils/format'

const store = useDistrictStore()
const { locale } = useI18n()
const zh = () => locale.value === 'zh'
</script>

<template>
  <div class="modal" :class="{ on: store.notesOpen }" @click.self="store.notesOpen = false">
    <div class="mcard">
      <h3>
        {{ zh() ? '设计说明 · 一天实现方案' : 'Design notes · one-day build' }}
        <button type="button" aria-label="Close" @click="store.notesOpen = false">×</button>
      </h3>

      <h4>{{ zh() ? '为什么是单页' : 'Why a single page' }}</h4>
      <ul v-if="zh()">
        <li>只用已接入且按区可汇总的三类数据：makani、宜居评估、ADREC。一页 5 个区块，不设页签，不做专题 3D 图层，不做生成式简报。</li>
        <li>本页对应客户 A 区情概况、B 土地与建设、C 房地产、G 公共空间四个专题的概览类问题；其余专题数据接通后再加，本页不放占位数字。</li>
        <li>人口数据较敏感，本页不显示，也不写入页面数据。</li>
      </ul>
      <ul v-else>
        <li>Uses only the three sources that are connected and can be summarised by district: makani, the Liveability assessment and ADREC. One page, five blocks, no tabs, no topic 3D layers, no generated brief.</li>
        <li>Covers the overview questions of topics A (profile), B (land &amp; construction), C (real estate) and G (public realm); other topics are added once their data is connected.</li>
        <li>Population is sensitive and is neither shown nor stored for this page.</li>
      </ul>

      <h4>{{ zh() ? '数据流' : 'Data flow' }}</h4>
      <p v-if="zh()">三个源库每日抽取一次到统计库（<code>district360.stat</code>，视图 <code>v_district_360</code>），接口只读统计库；底图为在线地图，建筑体块来自 makani，以矢量瓦片按高度拉伸。</p>
      <p v-else>Each source is extracted daily into the summary database (<code>district360.stat</code>, view <code>v_district_360</code>); the API reads only that database. The base map is an online map; building blocks come from makani as vector tiles extruded by height.</p>

      <table>
        <tr><th>{{ zh() ? '来源' : 'Source' }}</th><th>{{ zh() ? '字段' : 'Fields' }}</th><th>{{ zh() ? '数据日期' : 'Data date' }}</th></tr>
        <tr>
          <td><b>makani</b></td>
          <td>{{ zh() ? '面积、社区数、地块数、地块建设状态 4 类、建筑数、在建建筑数、土地利用占比、学校 / 医疗 / 清真寺（按区面空间汇总）' : 'Area, communities, plots, four plot-status counts, buildings, buildings under construction, land-use shares, schools / health / mosques (spatial summary by district polygon)' }}</td>
          <td>{{ dmy(store.meta?.sources.find(s => s.source === 'makani')?.sourceAsOf) }}</td>
        </tr>
        <tr>
          <td><b>{{ zh() ? '宜居评估' : 'Liveability' }}</b></td>
          <td>{{ zh() ? '宜居得分（现状 / 目前 / 目标）、公园数、公园面积与缺口' : 'Liveability score (existing / current / target), parks, park area and gap' }}</td>
          <td>{{ dmy(store.meta?.sources.find(s => s.source === 'liveability')?.sourceAsOf) }}</td>
        </tr>
        <tr>
          <td><b>ADREC</b></td>
          <td>{{ zh() ? '近 12 个月成交额、宗数、按揭数、均价、在册租约、同比、月度宗数（社区 → 区映射后汇总）' : 'Last-12-month sales value, transactions, mortgages, average price, leases, YoY, monthly counts (summed after community → district mapping)' }}</td>
          <td>{{ dmy(store.meta?.sources.find(s => s.source === 'adrec')?.sourceAsOf) }}</td>
        </tr>
      </table>

      <h4>{{ zh() ? '标记说明' : 'Markers' }}</h4>
      <ul v-if="zh()">
        <li><b>没有查询到数据</b>：该数据源还没接入（或该区在源库里没有数据），页面不显示数值。</li>
        <li><b>未纳入评估</b>：宜居评估不覆盖该区，不做估算。</li>
      </ul>
      <ul v-else>
        <li><b>No data found</b>: the source is not connected yet (or has no data for this district); no value is shown.</li>
        <li><b>Not assessed</b>: the district is outside the Liveability assessment; no estimate is made.</li>
      </ul>

      <h4>{{ zh() ? '留到下一阶段' : 'Next phase' }}</h4>
      <p>{{ zh() ? '许可、稽查、现场、资产、内涝、诉求、项目 7 个专题；专题 3D 图层；AI 简报。' : 'Seven topics (permits, inspections, field, assets, flooding, requests, projects); topic 3D layers; AI brief.' }}</p>
    </div>
  </div>
</template>
