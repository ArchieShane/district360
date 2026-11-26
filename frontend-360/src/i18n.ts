import { ref } from 'vue'

/**
 * 中 / 英文切换（原型的 中 / EN 按钮）。
 *
 * 做法与原型一致：界面源码只写中文，英文模式下把页面上渲染出来的文字（文本节点和 title / placeholder /
 * aria-label / data-tip 属性）就地换成英文；切回中文时还原。用 MutationObserver 跟上 Vue 的每次更新，
 * 组件不用逐个改。翻译顺序：
 *   1. 整句词典；
 *   2. 句式规则（排名、楼层序号、「显示全部（还有 n 个）」等需要调语序的）；
 *   3. 短语替换：按长度从长到短替换已知词，中文标点换成英文标点，再整理空格。
 * 词典里没有的中文原样保留（如数据库里的长说明文字）。地名、编号、单位、数字不翻译。
 */

export type Lang = 'zh' | 'en'
const KEY = 'd360.lang'
const saved = (() => { try { return localStorage.getItem(KEY) } catch { return null } })()
export const lang = ref<Lang>(saved === 'en' ? 'en' : 'zh')

/** 词典：中文 → 英文。整句和短语共用；短语替换按长度从长到短 */
const D: Record<string, string> = {
  // —— 层级、页签、通用
  '酋长国': 'Emirate', '市政': 'Municipality', '区域': 'District', '社区': 'Community', '地块': 'Plot', '建筑': 'Building',
  '楼层': 'Floor', '单元': 'Unit', '概况': 'Overview', '开发建设': 'Development', '市场': 'Market', '宜居': 'Liveability',
  '城市运行': 'Operations', '设施': 'Facilities', '数据': 'Data', '资产': 'Assets', '房地产市场': 'Real estate market',
  '加载中…': 'Loading…', '加载失败': 'Failed to load', '请求失败：': 'Request failed: ', '待接入': 'Pending', '暂无数据': 'No data yet',
  '没有数据': 'No data', '没有找到': 'Nothing found', '搜索': 'Search', '上一级': 'Up one level', '层级': 'Level', '面板': 'Panel',
  '空间': 'Spatial', '推导数据': 'Derived data', '基本信息': 'Details', '是': 'Yes', '否': 'No', '合计': 'Total', '占比': 'Share',
  '类别': 'Category', '类型': 'Type', '名称': 'Name', '状态': 'Status', '面积': 'Area', '高度': 'Height', '项目': 'Project',
  '阶段': 'Stage', '排名': 'Rank', '数据日期': 'Data date', '源数据日期': 'Source date', '表': 'Table', '行数': 'Rows',
  '收起': 'Collapse', '关闭': 'Close', '阈值': 'Threshold', '统计': 'Statistics', '分类': 'Categories', '其他': 'Other',
  '未知': 'Unknown', '编号': 'ID', '最近': 'Latest', '外': ' away', '分档': 'tier', '评分': 'score',
  '按所在地块统计': 'Counted by plot', '按所在社区统计': 'Counted by community', '按所在区域统计': 'Counted by district',
  'District 360 · 空间下钻': 'District 360 · Spatial drill-down',
  '按名称 / 编号筛选': 'Filter by name / ID', '搜索区域、社区、地块号 / GIS ID、建筑编号': 'Search district, community, plot no. / GIS ID, building ID',
  '选择（': 'Select (', '个，只列前': ' items, showing the first ', '个，请输入关键字': '; type to narrow down', '共': 'Total ',
  // —— 地图
  '地图工具': 'Map tools', '图层': 'Layers', '定位到当前': 'Centre on selection', '切换深色底图': 'Switch to dark basemap',
  '切换卫星影像': 'Switch to satellite imagery', '影像': 'Imagery', '深色': 'Dark', '三维视角': '3D view', '放大': 'Zoom in', '缩小': 'Zoom out',
  '底图': 'Basemap', '卫星影像': 'Satellite', '地块着色': 'Plot colours', '建设状态': 'Construction status', '土地用途': 'Land use',
  '设施点位': 'Facility points', '设施页签打开时全部显示': 'All shown while the Facilities tab is open', '指标图层': 'Indicator layer',
  '社区面': 'communities', '区域面': 'districts', '：源数据待接入': ': source data pending', '宜居页签打开时自动按宜居分着色': 'Coloured by liveability score while the Liveability tab is open',
  '宜居分': 'Liveability', '达标投资': 'CAPEX to target', '优先级': 'Priority', '入住率': 'Occupancy', '违规': 'Violations',
  '销售额': 'Sales value', '道路事故': 'Road accidents', '社区建成率': 'Community built rate', '区域建成率': 'District built rate',
  '宜居在途评分': 'Liveability score (pipeline)', '及以上': 'and above', '及以下': 'and below', '达标所需投资（AED）': 'CAPEX to target (AED)',
  '地块建设状态': 'Plot status', '地块土地用途': 'Plot land use', '公交站台': 'Bus platform', '路灯': 'Streetlights',
  '周边设施 · 1 km': 'Facilities within 1 km', '总体规划': 'Master plan', '在建工地': 'Construction site',
  // —— 节点标签与数字卡
  '个区域': 'districts', '个社区': 'communities', '个地块': 'plots', '栋建筑': 'buildings', '个单元': 'units', '个区': 'districts',
  '层': 'floors', '卧': 'bd', '卫': 'ba', '人口': 'Population', '总规': 'Master plan', '高': 'Height', '底标高': 'Base height',
  '建筑面积': 'GFA', '允许容积率': 'FAR allowed', '可建面积': 'GFA allowance', '已建建筑面积': 'Built GFA', '可建面积使用率': 'Allowance used',
  '使用面积': 'Usable area', '登记层数': 'Registered floors', '单元面积': 'Unit area', '地块面积': 'Plot area', '建成率': 'Built rate',
  '楼层（BDMS）': 'Floors (BDMS)', '单元（BDMS）': 'Units (BDMS)', '楼层与单元': 'Floors and units',
  '下一层：': 'Floor below: ', '已是最低层': 'Lowest floor', '上一层：': 'Floor above: ', '已是最高层': 'Top floor',
  '有分层数据': 'Floor data', '无分层数据': 'No floor data', '加载更多（': 'Load more (',
  // —— 数据页签
  '空间下钻主题表': 'Spatial drill-down tables', '最近一次抽取': 'Last extraction', '抽取器': 'Extractor', '开始': 'Started',
  '写入行数': 'Rows written', '数据说明': 'About the data', '主题表 / ODS 源表': 'Theme table / ODS source',
  '全部数据来自 ODS 库（ods 架构），由 d360-ods 抽取工程写入主题库 theme，接口只读主题库。': 'All data comes from the ODS database (ods schema); the d360-ods extractors write it to the theme schema, which the API reads.',
  '范围内的统计与明细。': ' statistics and details.', '。主题表已在主题库建好，抽取器尚未实现，接入后本页签展示': '. The theme tables exist; the extractor is not yet implemented. Once connected this tab shows ',
  '资本项目': 'Capital projects', '建设许可': 'Construction permits', '竣工 / 维修 / 拆除': 'Completion / maintenance / demolition',
  '居民需求调查': 'Residents survey', '总规地块指标': 'Master plan plots', '居民诉求': 'Community requests', '视觉违规上报': 'Visual distortion reports',
  'Tawtheeq 租约': 'Tawtheeq leases', '违规与处罚': 'Violations & penalties', '房产买卖': 'Property sales', '房产抵押': 'Mortgages',
  '住宅占用案件': 'Housing occupancy cases', '租赁合同': 'Lease contracts', '地块交易': 'Plot transactions', '公共设施': 'Public facilities',
  '资产与设施': 'Assets & facilities', '现状建筑台账': 'Building register', '楼层表': 'Floor table',
  '由 BDMS 楼层 / 单元推导（udm 建筑台账未覆盖）': 'Derived from BDMS floors / units (not in the udm building register)',
  '由 BDMS 单元推导（楼层表缺失）': 'Derived from BDMS units (floor table missing)',
  'ODS 无源表': 'Not in ODS', '当前 ODS（makani_1119）：区域 216、社区 1,864、地块 41 万、udm 建筑 38 万、BDMS 楼层 52 万、单元 73 万均为全量；没有 udm 台账的建筑由 BDMS 楼层 / 单元推导（标「推导数据」）。各主题缺失的源表见对应页签的「待接入」。': 'Current ODS (makani_1119): 216 districts, 1,864 communities, 410k plots, 380k udm buildings, 520k BDMS floors and 730k units, all full tables; buildings missing from the udm register are derived from BDMS floors / units (marked "Derived data"). Missing source tables per theme are listed under "Pending" in each tab.',
  // —— 首页总览
  '用途前 3': 'Top 3 uses', '前 3 类': 'Top 3 types', '成交额': 'Sales value', '成交笔数': 'Sales', '抵押笔数': 'Mortgages',
  '租约（Tawtheeq）': 'Leases (Tawtheeq)', '年租金中位数 AED': 'Median annual rent, AED', '所在区域': 'District', '路灯 · 在用': 'Streetlights · in service',
  '公交站 ·': 'Bus stops ·', '资本项目（数量、预算、延期）、建设许可：源数据待接入': 'Capital projects (count, budget, delays) and permits: source data pending',
  '成交、抵押、租赁与近 12 个月走势：源数据待接入；Tawtheeq 为样例数据（至 2021 年）': 'Sales, mortgages, leases and 12-month trends: source data pending; Tawtheeq is sample data (to 2021)',
  '道路安全（交通事故）、视觉违规、违规与罚款、居民诉求：源数据待接入': 'Road safety (accidents), visual distortion, violations and fines, community requests: source data pending',
  '不在宜居评估范围，没有评分。': ' is outside the liveability assessment; no score.', '没有宜居数据。': ' has no liveability data.',
  // —— 开发建设
  '该建筑没有对应的地块，没有总规与工地数据。': 'This building has no plot, so there is no master plan or site data.',
  '土地与建设': 'Land & construction', '在建建筑': 'Buildings under construction', '土地用途（按面积）': 'Land use (by area)',
  '社区按建成率': 'Communities by built rate', '总规项目': 'Master plans', '总规地块': 'Master plan plots', '总规实现率': 'Master plan delivery',
  '规划建设量': 'Planned development', '规划建筑面积': 'Planned GFA', '规划住宅单元': 'Planned housing units', '规划人口': 'Planned population',
  '按用途': 'By use', '地块规划管控': 'Planning control', '所属总规': 'Master plan', '总规类型 / 审批': 'Plan type / approval',
  '规划用途': 'Planned use', '规划地块号': 'Planned plot no.', '容积率 FAR': 'FAR', '层数': 'Floors', '住宅单元 / 人口': 'Housing units / population',
  '该地块共有': 'This plot has ', '条总规记录，显示规划建筑面积最大的一条。': ' master plan records; showing the one with the largest planned GFA.',
  '实现率': 'Delivery', '开发商未标注': 'Developer not recorded', '按面积列前': 'Largest ', '个。': ' shown.', '工地': 'Site', '工地面积': 'Site area',
  '竣工与拆除': 'Completion & demolition', '有竣工日期的建筑': 'Buildings with a completion date', '有拆除日期的建筑': 'Buildings with a demolition date',
  '累计口径，最近竣工': 'Cumulative; latest completion ', '，最近拆除': ', latest demolition ',
  '全酋长国只有约 1,900 栋建筑登记了竣工日期（最近一次 2021-04）；原型的竣工证书、拆除许可按月统计需等建设许可源表（MEPS）。': 'Only about 1,900 buildings in the emirate have a completion date (latest 2021-04); monthly completion certificates and demolition permits need the permit source (MEPS).',
  '详细规划': 'Detailed', '概念规划': 'Concept', '小幅修编': 'Minor amendment', '延期': 'Time extension', '咨询': 'Enquiry', '未标注': 'Not recorded',
  '住宅': 'Residential', '办公': 'Office', '零售': 'Retail', '酒店': 'Hotel', '工业': 'Industrial', '商业娱乐': 'Commercial recreation',
  '宗教': 'Religious', '教育': 'Education', '社区设施': 'Community facilities',
  // —— 市场
  '该建筑没有对应的地块，没有租约与交易数据。': 'This building has no plot, so there are no leases or transactions.', '租赁 · Tawtheeq': 'Leasing · Tawtheeq',
  '租约': 'Leases', '出租单元': 'Leased units', '生效中租约': 'Active leases', '年租金中位数（生效中，AED）': 'Median annual rent (active, AED)',
  '年租金 AED/m²': 'Annual rent AED/m²', '生效中年租金合计': 'Active annual rent total', '起租': 'Leases from ', '至 到期': ' to expiry ',
  '。样例数据截至 2021 年，「生效中」按源状态，部分已过到期日。': '. Sample data to 2021; "active" follows the source status and some have expired.',
  '没有租约记录。': 'No leases.', '按单元类型': 'By unit type', '年租金中位数': 'Median annual rent', '逐年新签租约': 'New leases by year',
  '按物业用途': 'By property use', '地块租约': 'Plot leases', '单元 / 合同': 'Unit / contract', '年租金': 'Annual rent',
  '买卖 · 抵押 · 租赁（ADREC）': 'Sales · mortgages · leases (ADREC)', '买卖 ·': 'Sales ·', '抵押 ·': 'Mortgages ·', '租赁 · 年': 'Leases · annual',
  '成交均价': 'Average price', 'AED/m²（Σ金额 ÷ Σ面积）。': 'AED/m² (Σ value ÷ Σ area).', '月份': 'Month', '买卖': 'Sales', '抵押': 'Mortgages',
  '租赁': 'Leases', '没有交易记录。': 'No transactions.', '生效中': 'Active', '已结束': 'Ended', '已到期': 'Expired', '已取消': 'Cancelled', '已终止': 'Terminated',
  // —— 宜居
  '没有单独评分，显示所在区域': ' has no score of its own; showing district', '宜居区域评分': 'Liveability district score', '平均在途评分': 'Average pipeline score',
  '平均现状评分': 'Average existing score', '平均目标评分': 'Average target score', '纳入评估的区域': 'Districts assessed', '达标所需投资合计': 'Total CAPEX to target',
  '公园缺口合计': 'Total park gap', '优先级分档': 'Priority tier',
  '。只给评估范围内的区域评分和分档，排名按在途评分；投资合计含全部区域。': '. Only assessed districts are scored and tiered; ranks follow the pipeline score; the CAPEX total covers all districts.',
  '区域排名': 'District ranking', '评分最高': 'Highest score', '评分最低': 'Lowest score', '距目标最远': 'Furthest from target', '投资最多': 'Highest CAPEX',
  '在途': 'Pipeline', '差距': 'Gap', '不在评估范围': 'Not assessed', '现状': 'Existing', '目标': 'Target',
  '个区域不在宜居评估范围，不排名。': ' districts are outside the liveability assessment and not ranked.', '另有': 'Another ',
  '没有对应的区域宜居数据。': ' has no district liveability data.', '查看区域': 'Open district', '在途评分': 'Pipeline score', '达标所需投资': 'CAPEX to target',
  '距目标': 'Gap to target', '分': 'pts', '分档规则': 'Tier rule',
  '现状 / 在途 / 目标对应宜居库的 Existing / Pipeline（原 Current）/ Target（原 LIV Action Plan）。发布版本': 'Existing / pipeline / target are the liveability Existing / Pipeline (formerly Current) / Target (formerly LIV Action Plan) stages. Published version ',
  '，数据日期': ', data date ', '该区域不在宜居评估范围，没有评分和分档。': 'This district is outside the liveability assessment, with no score or tier.',
  '最缺设施': 'Most lacking facilities', '前 3': 'Top 3', '设施类型': 'Facility type', '在途 / 分档目标': 'Pipeline / tier target', '行动计划后': 'after action plan',
  'FPP 类设施的在途供给评分都已达到本区分档目标。': 'All FPP facility types meet the tier target at the pipeline stage.', '没有分档，无法比较目标。': 'No tier, so no target to compare.',
  '供给评分 0–100；目标为本区分档（': 'Provision score 0–100; the target is the threshold for this district\'s tier (',
  '）下该类设施的达标线。只计 FPP 类项目（含公园），道路设施、可达性、学校容量等类型未计入。': '). FPP items only (parks included); infrastructure, accessibility and school capacity are not counted.',
  '居民需求': 'Residents\' needs', '理想社区调查': 'Dream Neighbourhood Survey', '得票占比 %': 'Vote share, %', '本区': 'This district: ',
  '人受访（Dream Neighbourhood Survey，可多选；占比 = 选该项人数 ÷ 全部选择次数）。': ' respondents (Dream Neighbourhood Survey, multiple choice; share = votes for the option ÷ all choices).',
  '本区没有调查结果，或受访不足 10 人（不展示）。': 'No survey results, or fewer than 10 respondents (not shown).',
  '公园供给': 'Park provision', '公园': 'Parks', '公园面积': 'Park area', '公园缺口': 'Park gap',
  '高优先': 'High priority', '优先': 'Priority', '其余': 'Remaining', '公民人口风险（严重）': 'Citizen population risk (critical)',
  '重大经济（严重）': 'Significant economic (critical)', '总人口风险（严重）': 'Total population risk (critical)', '公民人口分档高': 'High citizen tier',
  '高风险区域': 'High risk area', '社会影响高': 'High social impact', '未来经济潜力高': 'High future economic potential',
  '关键': 'Critical', '重要': 'Important', '基础': 'Basic',
  // —— 城市运行
  '没有对应的社区，没有城市运行数据。': 'No community, so no operations data.', '灯盏': 'Lamps', '在用率': 'In service', '灯型': 'Lamp type',
  '公交站': 'Bus stops', '站台': 'Platforms', '站名': 'Stop', '按站名列前 100 个。': 'First 100 stops by name.',
  '在用': 'In service', '停用': 'Out of service', '规划': 'Planned', '废弃': 'Abandoned', '高压钠灯': 'High-pressure sodium',
  '金属卤化物灯': 'Metal halide', '汞灯': 'Mercury vapour', '低压钠灯': 'Low-pressure sodium', '荧光灯': 'Fluorescent',
  // —— 设施
  '按所在': 'Counted by ', '学位': 'Seats', '床位': 'Beds', '礼拜人数': 'Worshippers', '该建筑没有对应的地块，没有设施数据。': 'This building has no plot, so there is no facility data.',
  '建筑资产': 'Building assets', '建筑 · 在建': 'Buildings · under construction', '建筑面积（楼层合计）': 'GFA (sum of floors)',
  '使用面积（单元合计）': 'Usable area (sum of units)', '可建面积（地块管控合计）': 'GFA allowance (plot controls)',
  '不少地块没有可建面积登记，所以这里不算使用率；单个地块的使用率见地块概况。': 'Many plots have no recorded GFA allowance, so no usage rate here; see the plot overview for a single plot.',
  '个单元面积超过 10,000 m²，视为源数据异常，未计入使用面积。': ' units larger than 10,000 m² are treated as source errors and left out of usable area.',
  '学校 ·': 'Schools ·', '在校生': 'Students', '学位使用率': 'Seat utilisation', '医疗 · 其中医院': 'Health · hospitals', '药房': 'Pharmacies',
  '清真寺': 'Mosques', '可容纳礼拜人数': 'Worshipper capacity', '公园 ·': 'Parks ·', '阿拉伯文名': 'Arabic name', '在校生 / 教职工': 'Students / staff',
  '没有': 'No ', '按': 'By ', '列前 30 个。': ': first 30 shown.', '学校': 'Schools', '医疗': 'Health',
  '周边设施': 'Nearby facilities', '按所在地块计算': 'Counted from the plot', '最近的设施': 'Nearest facilities',
  '从地块面内一点量直线距离；同一公交站的多个站台算一个站。地块上的设施不计入（见下方列表）。打开设施页签时地图上画出范围圈。': 'Straight-line distance from a point inside the plot; platforms of one bus stop count once. Facilities on the plot itself are not counted (see the list below). The rings show on the map while the Facilities tab is open.',
  '性别': 'Gender', '年级': 'Grades', '学段': 'Phase', '课程': 'Curriculum', '评级（Irtiqaa）': 'Rating (Irtiqaa)', '学费档': 'Fee band',
  '平均学费 AED': 'Average fee AED', '教师': 'Teachers', '男生': 'Boys', '女生': 'Girls', '学年': 'Academic year', '数据快照': 'Snapshot',
  '执照号': 'Licence no.', '专科': 'Specialities', '急诊级别': 'Emergency level', '地区': 'Region', '城市': 'City', '地址': 'Address',
  '发证日期': 'Issued', '到期日期': 'Expires', '清真寺编号': 'Mosque no.', '分支': 'Branch', '布道语言': 'Sermon language', '街区': 'Block',
  '街道': 'Street', '开放时间': 'Opening hours', '门票': 'Entry fee', '多功能游乐区': 'Multi-play area', '礼拜室': 'Prayer room', '售货亭': 'Kiosk',
  '烧烤区': 'BBQ area', '剧场': 'Amphitheatre', '餐厅': 'Restaurant', '图书馆': 'Library', '游戏区': 'Play area', '喷泉': 'Fountain', '围栏': 'Fence',
  '明细编号': 'Detail ID',
  // —— 需关注区域
  '需关注区域': 'Districts to watch', '供给 / 需求': 'Provision / need', '距目标差距': 'Gap to target', '延期资本项目': 'Delayed capital projects',
  '公园供给率': 'Park provision', '视觉违规按期整改率': 'Distortion resolved within SLA', '占全酋长国': 'Share of emirate', '占本市政': 'Share of municipality',
  '阈值为原型工作值（配置 d360.watch）；灰色指标的源数据尚未到位。数据日期': 'Thresholds are the prototype\'s working values (config d360.watch); grey indicators have no source data yet. Data date ',
  '资本项目源表 view_gsec_project_details 未入 ODS': 'Capital project source view_gsec_project_details not in ODS',
  'Tawtheeq 租约只有样例，关联表对不上单元': 'Tawtheeq leases are a sample and do not link to units',
  '视觉违规源表 view_ins_distortion 未入 ODS': 'Visual distortion source view_ins_distortion not in ODS',
  // —— 属性字段（基本信息）
  '市政编码': 'Municipality code', '市政编号': 'Municipality ID', '区域编码': 'District code', '区域编号': 'District ID', '俗称': 'Popular name',
  '面积 km²': 'Area km²', '社区编码': 'Community code', '社区编号': 'Community ID', '组合名称': 'Composite name', '地块编号': 'Plot ID',
  '地块号': 'Plot no.', 'ELMS 地块编号': 'ELMS plot ID', '面积 m²': 'Area m²', '规划面积 m²': 'Planned area m²', '配套状态': 'Service status',
  '规划状态': 'Planning status', '地块状态': 'Plot status', '主要用途': 'Primary use', '用地大类': 'Parent land use', '登记用途': 'Registered use',
  '次要用途': 'Secondary use', '权属类型': 'Ownership', '分配状态': 'Allocation status', '投资项目': 'Investment', '开发管控代码': 'Devcode',
  '管控类别': 'Devcode category', '可建面积 m²': 'GFA allowance m²', '最高高度': 'Max height', '分区': 'Zone', '扇区': 'Sector', '项目编号': 'Project ID',
  '最近许可类型': 'Last permit type', '最近许可日期': 'Last permit date', '注销日期': 'Cancelled on', '地块登记区名': 'Registered district',
  '地块登记社区': 'Registered community', '更新日期': 'Updated', '数据来源': 'Source', '建筑编号': 'Building ID', '建筑内部编号': 'Internal ID',
  '建筑状态': 'Building status', '用途类型': 'Usage type', '次要用途类型': 'Secondary usage type', '竣工日期': 'Completed', '拆除日期': 'Demolished',
  '开工日期': 'Started', '许可编号': 'Permit ID', '工地编号': 'Site ID', '有 BDMS 分层': 'BDMS floors', '楼层数（BDMS）': 'Floors (BDMS)',
  '单元数（BDMS）': 'Units (BDMS)', '基底面积 m²': 'Footprint m²', '楼层编号': 'Floor ID', '楼层号': 'Floor no.', '自下而上序号': 'Sequence (from bottom)',
  '底标高 m': 'Base height m', '层高 m': 'Floor height m', '高度 m': 'Height m', '建筑状态（BDMS）': 'Building status (BDMS)', '地块用途（BDMS）': 'Plot use (BDMS)',
  '所在建筑楼层数': 'Floors in building', '单元编号': 'Unit ID', '单元号': 'Unit no.', '单元类型': 'Unit type', '使用类型': 'Usage', '户型分类': 'Classification',
  '卧室': 'Bedrooms', '卫生间': 'Bathrooms', '阳台面积 m²': 'Balcony m²', '建筑用途': 'Building use', '单元户型': 'Unit types', '卧室数': 'Bedrooms',
  '已建': 'Built', '在建': 'Under construction', '空置': 'Vacant', '仅围墙': 'Boundary wall only',
}

/** 需要调语序的句式（整句匹配） */
const PATTERNS: [RegExp, string][] = [
  [/^第 ([\d,]+) \/ ([\d,]+)$/, 'Rank $1 / $2'],
  [/^([A-Z]{3}) 第 ([\d,]+) \/ ([\d,]+)$/, '$1 rank $2 / $3'],
  [/^第 ([\d,]+) \/ ([\d,]+) 层$/, 'Floor $1 / $2'],
  [/^全酋长国第 ([\d,]+)/, 'Emirate rank $1'],
  [/^显示全部（还有 ([\d,]+) 个）$/, 'Show all ($1 more)'],
  [/^加载更多（(.+)）$/, 'Load more ($1)'],
  [/^最近 ([\d,]+) 条$/, 'Latest $1'],
  [/^共 ([\d,]+) 个，只列前 ([\d,]+) 个，请输入关键字$/, '$1 items; showing the first $2 — type to narrow down'],
  [/^([\d,.]+) 层$/, '$1 floors'],
  [/^高 ([\d,.]+) m$/, 'Height $1 m'],
  [/^≥ ([\d,.]+) 分$/, '≥ $1 pts'],
  [/^≥ ([\d,.]+) 个$/, '≥ $1'],
  [/^(.+)概况$/, '$1 overview'],
]

const CJK = /[　-〿一-鿿＀-￯]/
const PUNCT: [RegExp, string][] = [[/，/g, ', '], [/。/g, '. '], [/；/g, '; '], [/：/g, ': '], [/（/g, ' ('], [/）/g, ') '], [/、/g, ', '], [/「|」/g, '"'], [/÷/g, ' ÷ ']]
const keys = Object.keys(D).sort((a, b) => b.length - a.length)
const PHRASE = new RegExp(keys.map(k => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|'), 'g')

function tr(src: string): string {
  if (!CJK.test(src)) return src
  const lead = src.match(/^\s*/)![0], tail = src.match(/\s*$/)![0], s = src.trim()
  if (D[s] !== undefined) return lead + D[s] + tail
  for (const [re, to] of PATTERNS) {
    if (re.test(s)) return lead + tr(s.replace(re, to)) + tail
  }
  let out = s.replace(PHRASE, m => ' ' + D[m] + ' ')
  for (const [re, to] of PUNCT) out = out.replace(re, to)
  out = out.replace(/\s+/g, ' ').replace(/\s+([,.;:)%])/g, '$1').replace(/\(\s+/g, '(').trim()
  return lead + out + tail
}

// —— 应用到页面：记住原文，切回中文时还原
const ATTRS = ['title', 'placeholder', 'aria-label', 'data-tip']
const textOrig = new WeakMap<Text, string>()
const textSet = new WeakMap<Text, string>()
const textNodes = new Set<Text>()
const attrOrig = new WeakMap<Element, Record<string, string>>()
const attrEls = new Set<Element>()
let busy = false

function doText(n: Text) {
  const cur = n.data
  if (textSet.get(n) === cur) return
  if (!CJK.test(cur)) return
  textOrig.set(n, cur)
  const t = tr(cur)
  textSet.set(n, t)
  textNodes.add(n)
  if (t !== cur) n.data = t
}
function doAttrs(el: Element) {
  for (const a of ATTRS) {
    const v = el.getAttribute(a)
    if (!v || !CJK.test(v)) continue
    const t = tr(v)
    // 译不出来（如「切换到中文」本来就该是中文）时不写回：写同样的值也会触发属性变更，观察器会自己循环
    if (t === v) continue
    const o = attrOrig.get(el) ?? {}
    o[a] = v
    attrOrig.set(el, o)
    attrEls.add(el)
    el.setAttribute(a, t)
  }
}
function walk(root: Node) {
  if (root.nodeType === Node.TEXT_NODE) { doText(root as Text); return }
  if (root.nodeType !== Node.ELEMENT_NODE) return
  const el = root as Element
  if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE') return
  doAttrs(el)
  const it = document.createTreeWalker(el, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT)
  let n: Node | null = it.nextNode()
  while (n) {
    if (n.nodeType === Node.TEXT_NODE) doText(n as Text)
    else doAttrs(n as Element)
    n = it.nextNode()
  }
}

let obs: MutationObserver | null = null
let titleOrig = ''
function start() {
  busy = true
  walk(document.body)
  if (!titleOrig) titleOrig = document.title
  document.title = tr(titleOrig)
  busy = false
  obs = new MutationObserver(list => {
    if (busy) return
    busy = true
    for (const m of list) {
      if (m.type === 'characterData') doText(m.target as Text)
      else if (m.type === 'attributes') doAttrs(m.target as Element)
      else m.addedNodes.forEach(walk)
    }
    busy = false
  })
  obs.observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS })
}
function stop() {
  obs?.disconnect()
  obs = null
  busy = true
  textNodes.forEach(n => { const o = textOrig.get(n); if (o !== undefined && n.isConnected && n.data === textSet.get(n)) n.data = o })
  textNodes.clear()
  attrEls.forEach(el => { const o = attrOrig.get(el); if (o && el.isConnected) for (const [a, v] of Object.entries(o)) el.setAttribute(a, v) })
  attrEls.clear()
  if (titleOrig) document.title = titleOrig
  busy = false
}

/** 切换语言并记住选择 */
export function setLang(l: Lang) {
  lang.value = l
  try { localStorage.setItem(KEY, l) } catch { /* 隐私模式等写不进去时只在本次有效 */ }
  document.documentElement.lang = l === 'en' ? 'en' : 'zh-CN'
  if (l === 'en') { if (!obs) start() } else stop()
}

/** 启动时按记住的语言生效（在 app 挂载后调用） */
export function initLang() {
  if (lang.value === 'en') setLang('en')
  else document.documentElement.lang = 'zh-CN'
}

/** 供测试 / 调试：把一句中文译成英文 */
export const translate = tr
