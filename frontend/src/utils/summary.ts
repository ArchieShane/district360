import type { DistrictOverview } from '../api/types'
import type { Lang } from '../i18n'
import { escapeHtml, nf, sg } from './format'

/** 给数值包一层 data-m，鼠标悬浮可看指标来源（见 LineageTip.vue）。 */
const m = (key: string, html: string) => `<span data-m="${key}">${html}</span>`

/** 摘要：按原型模板用统计数拼句（不调用大模型）。缺数据的那一句直接略过。 */
export function buildSummary(o: DistrictOverview, lang: Lang): string {
  const d = o.district
  const name = `<b>${escapeHtml(d.nameEn)}</b>`
  const land = o.land
  const lv = o.liveability
  const re = o.realEstate
  const f = o.facilities
  const hasLand = land.status === 'ok' || land.status === 'sample'
  const hasLv = lv.status === 'ok' || lv.status === 'sample'
  const hasRe = re.status === 'ok' || re.status === 'sample'
  const parksOk = f.parks.status === 'ok' || f.parks.status === 'sample'

  // 主导用地：前 5 类里占比最大的
  const top = hasLand ? land.landUse.slice(0, 5).reduce<typeof land.landUse[number] | null>((a, b) => (!a || (b.pct ?? 0) > (a.pct ?? 0) ? b : a), null) : null

  if (lang === 'zh') {
    const z: string[] = []
    z.push(`${name} 隶属阿布扎比市政（ADM）${d.areaKm2 != null ? `，面积 ${m('area_km2', `${nf(d.areaKm2, 2)} km²`)}` : ''}${hasLand ? `，含 ${m('communities', `${nf(d.communities)} 个社区`)}、${m('plots', `${nf(d.plots)} 个地块`)}` : ''}。`)
    if (hasLand) {
      z.push(`已建成地块占 ${m('plot_built_rate', `<b>${nf(land.plotBuiltRatePct, 1)}%</b>`)}，在建建筑 ${m('buildings_under_construction', `<b>${nf(land.buildingsUnderConstruction)}</b> 栋`)}${top && top.pct ? `，${m('land_use', `土地以${top.nameZh}为主（${nf(top.pct, 1)}%）`)}` : ''}。`)
    }
    z.push(hasLv ? `宜居得分 ${m('liv_current', `<b>${nf(lv.current, 2)}</b>`)}，目标 ${m('liv_target', nf(lv.target, 2))}。` : lv.status === 'not_assessed' ? '该区未纳入宜居评估。' : '')
    if (hasRe) {
      z.push(`近 12 个月房地产成交 ${m('re_sales', `<b>AED ${nf(re.salesAedBn, 1)} bn</b>`)}、${m('re_transactions', `${nf(re.transactions)} 宗`)}，均价 ${m('re_price', `AED ${nf(re.avgPriceSqm)}/m²`)}，同比 ${m('re_yoy', `${sg(re.salesYoyPct)}%`)}。`)
    }
    if (hasLand) {
      z.push(`区内有学校 ${m('schools', `${nf(f.schools)} 所`)}、医疗设施 ${m('medical', `${nf(f.medical)} 处`)}${parksOk ? `、公园 ${m('parks', `${nf(f.parks.count)} 处`)}` : ''}。`)
    }
    return z.join('')
  }

  const pl = (n: number | null, one: string, many: string) => `${nf(n)} ${n === 1 ? one : many}`
  const e: string[] = []
  e.push(`${name} is in Abu Dhabi City Municipality (ADM)${d.areaKm2 != null ? `, covering ${m('area_km2', `${nf(d.areaKm2, 2)} km²`)}` : ''}${hasLand ? ` with ${m('communities', pl(d.communities, 'community', 'communities'))} and ${m('plots', pl(d.plots, 'plot', 'plots'))}` : ''}. `)
  if (hasLand) {
    e.push(`${m('plot_built_rate', `<b>${nf(land.plotBuiltRatePct, 1)}%</b>`)} of plots are constructed and ${m('buildings_under_construction', `<b>${nf(land.buildingsUnderConstruction)}</b> ${land.buildingsUnderConstruction === 1 ? 'building is' : 'buildings are'}`)} under construction${top && top.pct ? `; ${m('land_use', `${top.nameEn.toLowerCase()} is the main land use (${nf(top.pct, 1)}%)`)}` : ''}. `)
  }
  e.push(hasLv ? `The liveability score is ${m('liv_current', `<b>${nf(lv.current, 2)}</b>`)} against a target of ${m('liv_target', nf(lv.target, 2))}. ` : lv.status === 'not_assessed' ? 'The district is outside the Liveability assessment. ' : '')
  if (hasRe) {
    e.push(`Real-estate sales over the last 12 months were ${m('re_sales', `<b>AED ${nf(re.salesAedBn, 1)} bn</b>`)} across ${m('re_transactions', `${nf(re.transactions)} transactions`)}, at ${m('re_price', `AED ${nf(re.avgPriceSqm)}/m²`)} on average, ${m('re_yoy', `${sg(re.salesYoyPct)}%`)} year on year. `)
  }
  if (hasLand) {
    e.push(`The district has ${m('schools', pl(f.schools, 'school', 'schools'))}, ${m('medical', pl(f.medical, 'health facility', 'health facilities'))}${parksOk ? ` and ${m('parks', pl(f.parks.count, 'park', 'parks'))}` : ''}.`)
  }
  return e.join('')
}
