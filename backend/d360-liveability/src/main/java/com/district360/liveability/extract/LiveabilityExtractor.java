package com.district360.liveability.extract;

import com.district360.common.district.DistrictRef;
import com.district360.common.extract.StatExtractor;
import com.district360.common.extract.snapshot.LiveabilitySnapshot;
import com.district360.liveability.mapper.LiveabilityMapper;
import com.district360.liveability.row.LivDistrictRow;
import com.district360.liveability.row.LivRunRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Liveability 抽取：每个区用 {@link DistrictRef#scopeEwkbHex()}（统计范围 = makani 原始区面）在源库做空间查询，
 * <b>不用区 ID</b>，保证与 makani、ADREC 口径一致。范围内没有宜居区的区跳过 → 页面显示「没有查询到数据」；
 * 不在宜居评估范围（param_district_scope.in_scope = false）的区照常写入并标 inScope = false → 页面不展示得分，显示「不在宜居评估范围」。
 * 计算版本取当前发布版本（dim_calc_versions.current_flag = true 且 approved），没有发布版本时抽取失败、统计库保持原样。
 * 优先级批次取最近一次审批通过的 fact_prioritization_runs；没有时分档、最缺设施留空，其余照常。
 * 取数逻辑与宜居平台 dashboard（Budget、District Priority、Facility Scores、Community Voice）一致。
 * <p>
 * 不捕获异常：抛出即记为失败，统计库保持原样。写统计库、日志、定时都由框架处理。
 * 本地验证：POST /api/v1/admin/etl/liveability。
 */
public class LiveabilityExtractor implements StatExtractor<LiveabilitySnapshot> {

    private static final Logger log = LoggerFactory.getLogger(LiveabilityExtractor.class);

    /** 最缺设施保留几项（页面展示前 3） */
    static final int FACILITY_GAPS = 5;
    /** 居民需求保留几项（原型展示前 3） */
    static final int NEEDS = 3;
    /** 受访人数少于此数的区不展示调查结果（宜居平台 SMALL_CELL_MIN_RESPONDENTS） */
    static final int MIN_RESPONDENTS = 10;

    private final LiveabilityMapper mapper;

    public LiveabilityExtractor(LiveabilityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String source() {
        return "liveability";
    }

    @Override
    public LiveabilitySnapshot extract(List<DistrictRef> districts) {
        // 当前发布版本、优先级批次只取一次，整批区都用它，避免抽取过程中有人重新发布导致前后版本不一
        Long version = mapper.publishedVersion();
        if (version == null) {
            throw new IllegalStateException("Liveability 没有已发布的计算版本（dim_calc_versions.current_flag = true 且 status = 'approved'），本次不抽取");
        }
        LivRunRow run = mapper.approvedRun();
        Long runId = run == null ? null : run.runId;
        log.info("Liveability 使用当前发布版本 calc_version_id = {}，优先级批次 run_id = {}", version, runId);
        if (run == null) {
            log.warn("Liveability 没有审批通过的优先级批次，分档与最缺设施留空");
        }
        List<LiveabilitySnapshot.FacilityType> catalogue = mapper.facilityCatalogue().stream()
                .map(t -> new LiveabilitySnapshot.FacilityType(t.facilityType, t.typeLabel, t.category, t.categoryLabel))
                .toList();
        List<String> types = catalogue.stream().map(LiveabilitySnapshot.FacilityType::facilityType).toList();
        List<LiveabilitySnapshot.District> out = new ArrayList<>();
        for (DistrictRef d : districts) {
            if (d.scopeEwkbHex() == null) {
                continue;
            }
            LivDistrictRow r = mapper.districtStats(d.scopeEwkbHex(), version, runId);
            // 范围内找不到宜居区 → 不放进列表，页面显示「没有查询到数据」
            if (r == null || r.livDistrictId == null) {
                continue;
            }
            List<LiveabilitySnapshot.FacilityGap> gaps = run == null || run.assumptionSetId == null ? List.of()
                    : mapper.facilityGaps(r.livDistrictId, version, run.runId, run.assumptionSetId, FACILITY_GAPS).stream()
                    .map(g -> new LiveabilitySnapshot.FacilityGap(g.facility, g.category, g.scoreExisting, g.scorePipeline,
                            g.scoreAp50, g.targetPct, g.gap))
                    .toList();
            List<LiveabilitySnapshot.Need> needs = mapper.needs(r.livDistrictId, version, MIN_RESPONDENTS, NEEDS).stream()
                    .map(n -> new LiveabilitySnapshot.Need(n.item, n.pct, n.votes, n.respondents))
                    .toList();
            List<LiveabilitySnapshot.FacilityCount> counts = types.isEmpty() ? List.of()
                    : mapper.facilityTypeCounts(d.scopeEwkbHex(), types).stream()
                    .map(c -> new LiveabilitySnapshot.FacilityCount(c.facilityType, c.n))
                    .toList();
            // 不在评估范围的区也放进来（inScope = false）：页面不展示得分，公园指标照常展示
            out.add(new LiveabilitySnapshot.District(
                    d.code(), r.inScope, r.livDistrictId, r.scoreExisting, r.scoreCurrent, r.scoreTarget,
                    r.parks, r.parkAreaHa, r.parkGapHa, r.capexAed, r.opexAed, r.capexTierTargetAed,
                    r.tier, r.tierRule, r.tierTriggers, gaps, needs, counts));
        }
        log.info("Liveability 设施类型目录 {} 类", catalogue.size());
        log.info("Liveability 抽取 {} 个区，其中在评估范围内 {} 个，有分档 {} 个，有调查结果 {} 个",
                out.size(), out.stream().filter(x -> Boolean.TRUE.equals(x.inScope())).count(),
                out.stream().filter(x -> x.tier() != null).count(), out.stream().filter(x -> !x.needs().isEmpty()).count());
        return new LiveabilitySnapshot(mapper.dataDate(version), version, runId, catalogue, out);
    }
}
