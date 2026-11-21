import { createI18n } from 'vue-i18n'

const zh = {
  header: {
    kicker: 'Platform Fabric · 360 District View',
    title: '区情概览',
    sub: '点击任意一个区：3D 场景高亮该区，并弹出一页概览：宜居、土地与建设、房地产和公共设施。',
  },
  list: { title: '区 · Districts', search: '搜索区名…', noMatch: '无匹配', count: '{loaded} / {total} · A–Z' },
  toolbar: { notes: '设计说明 · 一天实现', reset: '全景' },
  intro: {
    title: '每个区，一页看清',
    text: '点击地图上的任意一个区，或从左侧列表选择，查看该区的宜居、土地与建设、房地产和公共设施。',
    districts: '个区（阿布扎比市政 ADM）',
    blocks: '类区情信息',
    go: '从 {name} 开始',
  },
  legend: {
    idle: '点击任意区查看概览',
    controls: '左键拖动平移 · 右键旋转 · 滚轮缩放',
    selected: '选中区（光墙 = 边界）',
    uc: '在建建筑',
    outside: '区外压暗',
    esc: 'Esc 关闭 · 点击其他区切换',
  },
  panel: { kicker: 'District 360 · ADM', close: '关闭' },
  chips: {
    communities: '个社区',
    plots: '地块',
    muni: '阿布扎比市政 ADM',
  },
  block: { summary: '摘要', liveability: '宜居得分', land: '土地与建设', realEstate: '房地产 · 近 12 个月', facilities: '公共设施' },
  status: { sample: '样例数值', notAssessed: '未纳入评估', unavailable: '暂无数据' },
  src: { makani: 'makani', liveability: '宜居评估', adrec: 'ADREC' },
  liv: { target: '目标', gap: '差距', existing: '现状', current: '目前' },
  land: {
    plots: '地块',
    buildings: '建筑',
    uc: '在建建筑',
    landUse: '土地利用',
    ps: { constructed: '已建成', underConstruction: '在建', vacantServiced: '空置·已通市政', vacantUnserviced: '空置·未通市政' },
  },
  re: { sales: '成交额', yoy: '同比', tx: '成交宗数', mort: '按揭', price: '均价 AED/m²', lease: '在册租约', monthly: '月度成交宗数' },
  fac: { schools: '学校', health: '医疗', mosques: '清真寺', parks: '公园', gap: '公园缺口 ha' },
  tip: { liveability: '宜居', notAssessed: '宜居未评估', click: '点击查看区情概览', selected: '已选中' },
  lineage: {
    page: '页面读取',
    source: '数据来源',
    logic: '计算方式',
    sql: 'SQL（已代入当前区）',
    sqlPlanned: '计划 SQL（尚未接入）',
    copy: '复制',
    copied: '已复制',
    plannedNote: '该数据源尚未接入：当前显示的是原型样例值，下面是接入后的计划口径。',
    state: { ok: '实测', sample: '样例数值', planned: '计划口径', not_assessed: '未纳入评估', unavailable: '暂无数据' },
  },
  foot: { dataDate: '数据日期', sample: '样例', loading: '加载中…', error: '数据加载失败：{msg}' },
}

const en: typeof zh = {
  header: {
    kicker: 'Platform Fabric · 360 District View',
    title: 'district overview',
    sub: 'Click any district: the 3D scene highlights it and opens a one-page overview of liveability, land and construction, real estate and public facilities.',
  },
  list: { title: 'Districts', search: 'Search districts…', noMatch: 'No match', count: '{loaded} / {total} · A–Z' },
  toolbar: { notes: 'Design notes · one-day build', reset: 'Overview' },
  intro: {
    title: 'Every district on one page',
    text: 'Click any district on the map, or pick one from the list, to see its liveability, land and construction, real estate and public facilities.',
    districts: 'districts in Abu Dhabi City Municipality',
    blocks: 'information blocks',
    go: 'Start with {name}',
  },
  legend: {
    idle: 'Click any district for its overview',
    controls: 'Drag to pan · right-drag to rotate · scroll to zoom',
    selected: 'Selected district (light wall = boundary)',
    uc: 'Under construction',
    outside: 'Outside dimmed',
    esc: 'Esc to close · click another district to switch',
  },
  panel: { kicker: 'District 360 · ADM', close: 'Close' },
  chips: {
    communities: 'communities',
    plots: 'plots',
    muni: 'Abu Dhabi City Municipality',
  },
  block: { summary: 'Summary', liveability: 'Liveability', land: 'Land & construction', realEstate: 'Real estate · last 12 months', facilities: 'Public facilities' },
  status: { sample: 'Sample values', notAssessed: 'Not assessed', unavailable: 'No data yet' },
  src: { makani: 'makani', liveability: 'Liveability', adrec: 'ADREC' },
  liv: { target: 'Target', gap: 'gap', existing: 'Existing', current: 'Current' },
  land: {
    plots: 'Plots',
    buildings: 'Buildings',
    uc: 'Under construction',
    landUse: 'Land use',
    ps: { constructed: 'Built', underConstruction: 'Under construction', vacantServiced: 'Vacant, serviced', vacantUnserviced: 'Vacant, unserviced' },
  },
  re: { sales: 'Sales value', yoy: 'YoY', tx: 'Transactions', mort: 'Mortgages', price: 'AED/m² average', lease: 'Leases', monthly: 'Monthly transactions' },
  fac: { schools: 'Schools', health: 'Health', mosques: 'Mosques', parks: 'Parks', gap: 'Park gap, ha' },
  tip: { liveability: 'Liveability', notAssessed: 'Liveability not assessed', click: 'Click for the overview', selected: 'Selected' },
  lineage: {
    page: 'Read from',
    source: 'Source',
    logic: 'Method',
    sql: 'SQL (for this district)',
    sqlPlanned: 'Planned SQL (not connected yet)',
    copy: 'Copy',
    copied: 'Copied',
    plannedNote: 'This source is not connected yet: the value shown is a prototype sample; below is the planned method.',
    state: { ok: 'Live', sample: 'Sample', planned: 'Planned', not_assessed: 'Not assessed', unavailable: 'No data' },
  },
  foot: { dataDate: 'Data dates', sample: 'sample', loading: 'Loading…', error: 'Failed to load data: {msg}' },
}

export type Lang = 'zh' | 'en'

export const i18n = createI18n({
  legacy: false,
  locale: 'zh',
  fallbackLocale: 'zh',
  messages: { zh, en },
})
