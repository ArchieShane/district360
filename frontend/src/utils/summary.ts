import type { DistrictOverview } from '../api/types'
import type { Lang } from '../i18n'
import { escapeHtml, nf, sg } from './format'

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
    z.push(`${name} 隶属阿布扎比市政（ADM）${d.areaKm2 != null ? `，面积 ${nf(d.areaKm2, 2)} km²` : ''}${hasLand ? `，含 ${nf(d.communities)} 个社区、${nf(d.plots)} 个地块` : ''}。`)
    if (hasLand) {
      z.push(`已建成地块占 <b>${nf(land.plotBuiltRatePct, 1)}%</b>，在建建筑 <b>${nf(land.buildingsUnderConstruction)}</b> 栋${top && top.pct ? `，土地以${top.nameZh}为主（${nf(top.pct, 1)}%）` : ''}。`)
    }
    z.push(hasLv ? `宜居得分 <b>${nf(lv.current, 2)}</b>，目标 ${nf(lv.target, 2)}。` : lv.status === 'not_assessed' ? '该区未纳入宜居评估。' : '')
    if (hasRe) {
      z.push(`近 12 个月房地产成交 <b>AED ${nf(re.salesAedBn, 1)} bn</b>、${nf(re.transactions)} 宗，均价 AED ${nf(re.avgPriceSqm)}/m²，同比 ${sg(re.salesYoyPct)}%。`)
    }
    if (hasLand) {
      z.push(`区内有学校 ${nf(f.schools)} 所、医疗设施 ${nf(f.medical)} 处${parksOk ? `、公园 ${nf(f.parks.count)} 处` : ''}。`)
    }
    return z.join('')
  }

  const pl = (n: number | null, one: string, many: string) => `${nf(n)} ${n === 1 ? one : many}`
  const e: string[] = []
  e.push(`${name} is in Abu Dhabi City Municipality (ADM)${d.areaKm2 != null ? `, covering ${nf(d.areaKm2, 2)} km²` : ''}${hasLand ? ` with ${pl(d.communities, 'community', 'communities')} and ${pl(d.plots, 'plot', 'plots')}` : ''}. `)
  if (hasLand) {
    e.push(`<b>${nf(land.plotBuiltRatePct, 1)}%</b> of plots are constructed and <b>${nf(land.buildingsUnderConstruction)}</b> ${land.buildingsUnderConstruction === 1 ? 'building is' : 'buildings are'} under construction${top && top.pct ? `; ${top.nameEn.toLowerCase()} is the main land use (${nf(top.pct, 1)}%)` : ''}. `)
  }
  e.push(hasLv ? `The liveability score is <b>${nf(lv.current, 2)}</b> against a target of ${nf(lv.target, 2)}. ` : lv.status === 'not_assessed' ? 'The district is outside the Liveability assessment. ' : '')
  if (hasRe) {
    e.push(`Real-estate sales over the last 12 months were <b>AED ${nf(re.salesAedBn, 1)} bn</b> across ${nf(re.transactions)} transactions, at AED ${nf(re.avgPriceSqm)}/m² on average, ${sg(re.salesYoyPct)}% year on year. `)
  }
  if (hasLand) {
    e.push(`The district has ${pl(f.schools, 'school', 'schools')}, ${pl(f.medical, 'health facility', 'health facilities')}${parksOk ? ` and ${pl(f.parks.count, 'park', 'parks')}` : ''}.`)
  }
  return e.join('')
}
