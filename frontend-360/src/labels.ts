import type { Level } from './api'

/** 层级名称 */
export const LEVEL_NAME: Record<Level, string> = {
  emirate: '酋长国',
  municipality: '市政',
  district: '区域',
  community: '社区',
  plot: '地块',
  building: '建筑',
  floor: '楼层',
  unit: '单元',
}

/** 面板标题上方的小字（原型：District 360 / Community 360 …） */
export const LEVEL_KICK: Record<Level, string> = {
  emirate: 'Emirate 360',
  municipality: 'Municipality 360',
  district: 'District 360',
  community: 'Community 360',
  plot: 'Plot 360',
  building: 'Building 360',
  floor: 'Floor 360',
  unit: 'Unit 360',
}

/** 属性（info）字段中文名；没列出的不显示 */
export const INFO_LABEL: Record<string, string> = {
  municipalityCode: '市政编码',
  municipalityId: '市政编号',
  districtKey: '区域编码',
  districtId: '区域编号',
  popularNameEn: '俗称',
  population: '人口',
  areaKm2: '面积 km²',
  communityKey: '社区编码',
  communityId: '社区编号',
  compositeNameEn: '组合名称',
  gisid: 'GIS ID',
  plotId: '地块编号',
  plotNumber: '地块号',
  elmsPlotId: 'ELMS 地块编号',
  areaSqm: '面积 m²',
  plannedAreaSqm: '规划面积 m²',
  constructionStatus: '建设状态',
  serviceStatus: '配套状态',
  planningStatus: '规划状态',
  plotStatus: '地块状态',
  primaryUse: '主要用途',
  parentLandUse: '用地大类',
  landUse: '登记用途',
  secondaryUse: '次要用途',
  ownershipType: '权属类型',
  allocationStatus: '分配状态',
  investmentName: '投资项目',
  devcode: '开发管控代码',
  devcodeCategory: '管控类别',
  devcodeFar: '允许容积率',
  devcodeMaxGfa: '可建面积 m²',
  maxHeights: '最高高度',
  zoneTpss: '分区',
  sectorTpss: '扇区',
  projectId: '项目编号',
  permitLastType: '最近许可类型',
  permitLastDate: '最近许可日期',
  cancelDate: '注销日期',
  srcDistrictEn: '地块登记区名',
  srcCommunityEn: '地块登记社区',
  modifiedDate: '更新日期',
  source: '数据来源',
  buildingId: '建筑编号',
  buildingInternalId: '建筑内部编号',
  bdmsBuildingId: 'BDMS 建筑编号',
  physicalStatus: '建筑状态',
  primaryUsageType: '用途类型',
  secondaryUsageType: '次要用途类型',
  floorsCount: '层数',
  heightM: '高度 m',
  completionDate: '竣工日期',
  demolitionDate: '拆除日期',
  startDate: '开工日期',
  permitId: '许可编号',
  constructionSiteId: '工地编号',
  isBdms: '有 BDMS 分层',
  bdmsFloors: '楼层数（BDMS）',
  bdmsUnits: '单元数（BDMS）',
  footprintSqm: '基底面积 m²',
  floorId: '楼层编号',
  floorNumber: '楼层号',
  floorSeq: '自下而上序号',
  baseHeightM: '底标高 m',
  floorHeightM: '层高 m',
  buildingStatus: '建筑状态（BDMS）',
  plotPrimaryUse: '地块用途（BDMS）',
  buildingFloors: '所在建筑楼层数',
  unitId: '单元编号',
  unitNumber: '单元号',
  unitType: '单元类型',
  usageType: '使用类型',
  classification: '户型分类',
  category: '类别',
  bedrooms: '卧室',
  bathrooms: '卫生间',
  balconyAreaSqm: '阳台面积 m²',
}

/** 数据来源取值说明 */
export const SOURCE_NOTE: Record<string, string> = {
  udm: 'udm 现状建筑台账',
  bdms: '由 BDMS 楼层 / 单元推导（udm 建筑台账未覆盖）',
  bdms_floors: 'BDMS 楼层表',
  bdms_units: '由 BDMS 单元推导（楼层表缺失）',
}

/** 分组卡片标题 */
export const DIM_TITLE: Record<string, string> = {
  plotStatus: '地块建设状态',
  landUse: '土地用途',
  buildingUse: '建筑用途',
  buildingStatus: '建筑状态',
  unitClass: '单元户型',
  unitType: '单元类型',
  bedrooms: '卧室数',
}

/** 地块建设状态 → 中文与颜色（与原型一致：已建蓝、在建橙、空置绿） */
export const PLOT_STATUS: Record<string, { n: string; c: string }> = {
  Constructed: { n: '已建', c: '#3D6DF2' },
  'Under Construction': { n: '在建', c: '#F0914A' },
  'Not Constructed': { n: '空置', c: '#62D36A' },
  'Only Boundary Wall': { n: '仅围墙', c: '#8C929B' },
  Unknown: { n: '未知', c: '#6B7280' },
}

export const PALETTE = ['#4C8DF6', '#F0A64A', '#B58CFF', '#3DD6D0', '#E86A6A', '#7BC86C', '#F2D45C', '#6F86A6', '#FF8FB1', '#8D99AE']

/** 稳定的分类颜色：同一取值在不同卡片、地图里颜色一致 */
const colorCache = new Map<string, string>()
export function colorOf(k: string): string {
  if (PLOT_STATUS[k]) return PLOT_STATUS[k].c
  let c = colorCache.get(k)
  if (!c) {
    let h = 0
    for (let i = 0; i < k.length; i++) h = (h * 31 + k.charCodeAt(i)) >>> 0
    c = PALETTE[h % PALETTE.length]
    colorCache.set(k, c)
  }
  return c
}

/** 总规阶段（ods-dev 归一后的取值） */
export const MP_STAGE: Record<string, { n: string; c: string }> = {
  Detailed: { n: '详细规划', c: '#4C8DF6' },
  Concept: { n: '概念规划', c: '#B58CFF' },
  'Minor Amendment': { n: '小幅修编', c: '#3DD6D0' },
  'Time Extension': { n: '延期', c: '#F0A64A' },
  Enquiry: { n: '咨询', c: '#8D99AE' },
  Other: { n: '未标注', c: '#6B7280' },
}

/** 规划建筑面积用途 */
export const GFA_USE: Record<string, string> = {
  Residential: '住宅', Office: '办公', Retail: '零售', Hotel: '酒店', Industrial: '工业',
  'Commercial recreation': '商业娱乐', Religious: '宗教', Education: '教育', Healthcare: '医疗', 'Community facilities': '社区设施',
}

/** Tawtheeq 租约状态 */
export const TENANCY_STATUS: Record<string, { n: string; c: string }> = {
  ACTIVE: { n: '生效中', c: '#62D36A' },
  CLOSED: { n: '已结束', c: '#8C929B' },
  EXPIRED: { n: '已到期', c: '#F0A64A' },
  CANCELLED: { n: '已取消', c: '#E86A6A' },
  TERMINATED: { n: '已终止', c: '#B58CFF' },
  Unknown: { n: '未标注', c: '#6B7280' },
}

/** 路灯状态（源 statusindicator） */
export const LIGHT_STATUS: Record<string, { n: string; c: string }> = {
  'In Service': { n: '在用', c: '#F2D45C' },
  'Out Of Service': { n: '停用', c: '#E86A6A' },
  UnderConst: { n: '在建', c: '#F0A64A' },
  Proposed: { n: '规划', c: '#4C8DF6' },
  Abandon: { n: '废弃', c: '#8C929B' },
  Unknown: { n: '未标注', c: '#6B7280' },
}

/** 灯型（源 lamptype） */
export const LAMP_TYPE: Record<string, string> = {
  HPS: '高压钠灯', MH: '金属卤化物灯', MV: '汞灯', LPS: '低压钠灯', FLUO: '荧光灯', Unknown: '未标注',
}

/** 尚未接入的主题页签：对应主题库 theme.meta_table.theme */
export const PENDING_TABS: { id: string; name: string; theme: string; note: string }[] = [
  { id: 'ast', name: '设施', theme: '资产与设施', note: '学校、医疗、清真寺、公园' },
]
