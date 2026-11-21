import type { ApiResponse, DistrictListItem, DistrictOverview, MetaInfo } from './types'

const BASE = '/api/v1'

async function get<T>(path: string): Promise<T> {
  const res = await fetch(BASE + path, { headers: { Accept: 'application/json' } })
  const body = (await res.json().catch(() => null)) as ApiResponse<T> | null
  if (!res.ok || !body || body.code !== 0) {
    throw new Error(body?.message || `请求失败：${res.status}`)
  }
  return body.data
}

export const api = {
  districts: () => get<DistrictListItem[]>('/districts'),
  overview: (code: string) => get<DistrictOverview>(`/districts/${encodeURIComponent(code)}/overview`),
  meta: () => get<MetaInfo>('/meta'),
  boundariesUrl: () => `${location.origin}${BASE}/map/districts.geojson`,
  buildingTilesUrl: () => `${location.origin}${BASE}/map/tiles/buildings/{z}/{x}/{y}.pbf`,
}
