/**
 * 仅开发用：地址带 ?mock=biz 时，给 dev / ops / mkt / overview / watch 接口的业务视图部分填上示例数据，
 * 用来在源表还没导入时检查页面（打包时 import.meta.env.DEV 为 false，这个文件不会进入产物）。
 */
/* eslint-disable @typescript-eslint/no-explicit-any */
const months = (f: (i: number) => Record<string, number>) =>
  Array.from({ length: 12 }, (_, i) => ({ month: `20${25 + (i >= 1 ? 1 : 0)}-${String(i === 0 ? 12 : i).padStart(2, '0')}`, ...f(i) }))
const rows = (n = 1) => ({ rows: n, note: null })
const SRC = {
  dev_capital_project: rows(), dev_permit: rows(), dev_building_application: rows(), mkt_unit_status: rows(),
  ops_case: rows(), ops_accident: rows(), ops_distortion: rows(), ops_violation: rows(), ops_occupancy_case: rows(),
}
const prjSummary = { projects: 38, budgetAed: 2_840_000_000, spentAed: 1_120_000_000, spentPct: 39.4, completed: 11, ongoing: 27, delayed: 6, delayedBudgetAed: 612_000_000, mapped: 31 }
const permitSummary = { asOf: '2026-11-20', permits: 412, newLast12m: 96, newLast6m: 54, newPrev6m: 42, inProgress: 133, stalled180: 9 }
const occSummary = { units: 4210, rented: 3268, vacant: 802, occupancyPct: 80.3, blocked: 140, linkedUnits: 3920 }
const caseSummary = { asOf: '2026-11-25', cases12m: 1843, closed12m: 1611, closedPct: 87.4, slaRated: 1580, slaPct: 78.2, csat: null }
const accSummary = { asOf: '2026-11-24', accidents12m: 214 }
const disSummary = { asOf: '2026-11-25', reports12m: 652, slaRated: 610, slaFixedPct: 64.1, duplicatePct: 7.8, convertedToViolation: 41, open: 88 }
const vioSummary = { violations: 389, open: 57, awareness: 120, warning: 154, fine: 115, fineAed: 1_480_000, receivedAed: 980_000, collectionPct: 66.2, asOf: '2026-11-22' }

export function mockBiz(path: string, data: any): any {
  const plot = /\/nodes\/plot\//.test(path)
  const unitLevel = /\/nodes\/(floor|unit)\//.test(path)
  if (path.endsWith('/dev')) {
    data.sources = SRC
    data.capitalProjects = {
      summary: prjSummary,
      byStatus: [{ k: 'On Going', n: 21, budgetAed: 1.9e9, delayed: 6 }, { k: 'Completed', n: 11, budgetAed: 6.1e8, delayed: 0 }, { k: 'Design', n: 6, budgetAed: 3.3e8, delayed: 0 }],
      byCategory: [{ k: 'Roads & Infrastructure', n: 14, budgetAed: 1.4e9, delayed: 3 }, { k: 'Parks & Public Realm', n: 9, budgetAed: 4.2e8, delayed: 1 }, { k: 'Utilities', n: 8, budgetAed: 6.6e8, delayed: 2 }, { k: 'Community Facilities', n: 7, budgetAed: 3.6e8, delayed: 0 }],
      byPhase: [],
      items: [
        { projectId: 'P-1021', name: 'Al Wathba Road Upgrade Phase 2', category: 'Roads & Infrastructure', phase: 'Construction', status: 'On Going', budgetAed: 420e6, spentAed: 160e6, spentPct: 38.1, actualProgress: 41, plannedProgress: 58, plannedEnd: '2026-06-30', forecastEnd: '2027-02-28', delayed: true, completed: false, cx: 54.4726, cy: 24.2559 },
        { projectId: 'P-0877', name: 'ICAD Stormwater Network', category: 'Utilities', phase: 'Construction', status: 'On Going', budgetAed: 190e6, spentAed: 92e6, spentPct: 48.4, actualProgress: 52, plannedProgress: 55, plannedEnd: '2027-03-31', forecastEnd: '2027-03-31', delayed: false, completed: false, cx: 54.46, cy: 24.27 },
        { projectId: 'P-0640', name: 'Community Park ICAD III', category: 'Parks & Public Realm', phase: 'Completed', status: 'Completed', budgetAed: 38e6, spentAed: 37.2e6, spentPct: 97.9, actualProgress: 100, plannedProgress: 100, plannedEnd: '2025-09-30', forecastEnd: '2025-10-15', delayed: false, completed: true, cx: 54.48, cy: 24.30 },
      ],
    }
    data.permits = {
      summary: permitSummary,
      byStatus: [{ k: 'Active', n: 133 }, { k: 'Expired', n: 201 }, { k: 'Cancelled', n: 78 }],
      applications: { applications: 287, completionCertificates: 118, maintenancePermits: 102, demolitionPermits: 21, other: 46 },
      applicationTypes: [{ k: 'Completion Certificate', kind: 'completion', n: 118 }, { k: 'Building Maintenance Permit', kind: 'maintenance', n: 102 }, { k: 'Building Modification', kind: 'other', n: 46 }, { k: 'Demolition Permit', kind: 'demolition', n: 21 }],
      applicationsByMonth: months(i => ({ maintenance: [6, 8, 7, 9, 11, 8, 10, 7, 9, 12, 8, 7][i], demolition: [1, 2, 0, 3, 1, 2, 2, 1, 4, 2, 1, 2][i], completion: 9 })),
      plotRecords: plot ? [
        { type: 'permit', number: 'EM-2026-1182', name: 'Warehouse extension', status: 'Active', projectStatus: 'Under Construction', date: '2026-10-15' },
        { type: 'maintenance', number: 'MP-55120', name: 'Building Maintenance Permit', status: 'In Review', date: '2026-09-30' },
        { type: 'completion', number: 'CC-30981', name: 'Completion Certificate', status: 'Issued', date: '2024-03-12' },
      ] : [],
    }
  } else if (path.endsWith('/mkt')) {
    data.sources = SRC
    data.occupancy = {
      summary: occSummary,
      byUsage: [{ k: 'Residential', units: 3410, rented: 2790, occupancyPct: 84.3 }, { k: 'Commercial', units: 660, rented: 478, occupancyPct: 72.6 }],
      byClassification: [],
      units: unitLevel ? Array.from({ length: 14 }, (_, i) => ({ unitKey: 'ADM-' + (1101532 + i), unitNumber: String(101 + i), status: i % 5 === 3 ? 'vacant' : i === 9 ? 'blocked' : 'rented', isRented: i % 5 !== 3 })) : [],
    }
  } else if (path.endsWith('/ops')) {
    data.sources = SRC
    data.cases = { summary: caseSummary,
      byCategory: [{ k: 'Roads & Traffic', n: 612, closed: 540 }, { k: 'Waste & Cleanliness', n: 488, closed: 451 }, { k: 'Street Lighting', n: 301, closed: 280 }, { k: 'Parks', n: 214, closed: 170 }],
      byType: [], byMonth: months(i => ({ opened: 120 + (i * 37) % 70, closed: 110 + (i * 29) % 60 })) }
    data.accidents = { summary: accSummary, bySeverity: [{ k: 'Minor', n: 151 }, { k: 'Moderate', n: 46 }, { k: 'Severe', n: 14 }, { k: 'Fatal', n: 3 }],
      byKind: [{ k: 'Collision', n: 121 }, { k: 'Run-off-road', n: 44 }, { k: 'Pedestrian', n: 27 }], byMonth: months(i => ({ n: 12 + (i * 7) % 11 })) }
    data.distortions = { summary: disSummary,
      byService: [{ k: 'Visual Pollution', n: 302, slaFixedPct: 61 }, { k: 'Building Facade', n: 198, slaFixedPct: 70.2 }, { k: 'Public Behaviour', n: 152, slaFixedPct: 62.5 }],
      byEntity: [{ k: 'DMT', n: 401, slaFixedPct: 66.3 }, { k: 'ADM', n: 251, slaFixedPct: 60.6 }],
      byMonth: months(i => ({ reports: 40 + (i * 13) % 30, convertedToViolation: (i * 3) % 7 })) }
    data.violations = { summary: vioSummary,
      byService: [{ k: 'Building Violations', n: 172, open: 25, fineAed: 820000 }, { k: 'Public Health', n: 131, open: 19, fineAed: 410000 }, { k: 'Occupancy', n: 86, open: 13, fineAed: 250000 }],
      byItem: [{ k: 'Unlicensed works', n: 96, open: 14, fineAed: 610000 }, { k: 'Littering', n: 88, open: 9, fineAed: 88000 }, { k: 'Overcrowding', n: 61, open: 11, fineAed: 230000 }] }
    data.occupancyCases = { summary: { cases: 74, open: 22, urgent: 6, fineAed: 230000 }, byStage: [{ k: 'Inspection', n: 31 }, { k: 'Warning issued', n: 25 }, { k: 'Fine issued', n: 18 }] }
    data.plotRecords = plot ? [
      { type: 'distortion', number: 'D-88120', raisedOn: '2026-11-10 09:00:00', closedOn: '2026-11-30 12:00:00', status: 'Closed', item: 'Visual Pollution', withinSla: false },
      { type: 'violation', number: 'V-44102', raisedOn: '2026-10-01 10:00:00', closedOn: null, status: 'Open', item: 'Unlicensed works', fineAed: 1500 },
      { type: 'occupancy', number: 'S-1203', raisedOn: '2026-09-01 08:00:00', closedOn: null, status: 'Open', item: 'Inspection', fineAed: 1000 },
    ] : []
  } else if (path.endsWith('/overview')) {
    data.dev = { ...data.dev, capitalProjects: prjSummary, permits: permitSummary }
    data.mkt = { ...data.mkt, occupancy: occSummary }
    data.ops = { ...data.ops, cases: caseSummary, accidents: accSummary, accidentsBySeverity: [{ k: 'Minor', n: 151 }], distortions: disSummary, violations: vioSummary }
  } else if (path.endsWith('/watch')) {
    for (const i of data.indicators) if (['prj', 'occ', 'vio'].includes(i.key)) { i.available = true; i.count = 2; delete i.note }
    const d = data.districts
    const add = (k: number, item: any) => { if (d[k]) d[k].items.push(item) }
    add(0, { key: 'prj', value: 4, projects: 9, delayedBudgetAed: 2.1e8 })
    add(1, { key: 'occ', value: 72.4, units: 1830, rented: 1325 })
    add(2, { key: 'vio', value: 58.1, reports: 124, fixed: 72 })
  }
  return data
}
