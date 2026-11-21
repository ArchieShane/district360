package com.district360.liveability.extract;

import com.district360.common.district.DistrictRef;
import com.district360.common.extract.StatExtractor;
import com.district360.common.extract.snapshot.LiveabilitySnapshot;
import com.district360.liveability.mapper.LiveabilityMapper;
import com.district360.liveability.row.LivDistrictRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Liveability 抽取：每个区用 {@link DistrictRef#scopeEwkbHex()}（统计范围 = makani 原始区面）在源库做空间查询，
 * <b>不用区 ID</b>，保证与 makani、ADREC 口径一致。范围内没有宜居区的区跳过 → 页面显示「没有查询到数据」；
 * 不在宜居评估范围（param_district_scope.in_scope = false）的区照常写入并标 inScope = false → 页面不展示得分，显示「不在宜居评估范围」。
 * 计算版本取当前发布版本（dim_calc_versions.current_flag = true），没有发布版本时抽取失败、统计库保持原样。
 * <p>
 * 不捕获异常：抛出即记为失败，统计库保持原样。写统计库、日志、定时都由框架处理。
 * 本地验证：POST /api/v1/admin/etl/liveability。
 */
public class LiveabilityExtractor implements StatExtractor<LiveabilitySnapshot> {

    private static final Logger log = LoggerFactory.getLogger(LiveabilityExtractor.class);

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
        // 当前发布版本只取一次，整批区都用它，避免抽取过程中有人重新发布导致前后版本不一
        Long version = mapper.publishedVersion();
        if (version == null) {
            throw new IllegalStateException("Liveability 没有已发布的计算版本（dim_calc_versions.current_flag = true），本次不抽取");
        }
        log.info("Liveability 使用当前发布版本 calc_version_id = {}", version);
        List<LiveabilitySnapshot.District> out = new ArrayList<>();
        for (DistrictRef d : districts) {
            if (d.scopeEwkbHex() == null) {
                continue;
            }
            LivDistrictRow r = mapper.districtStats(d.scopeEwkbHex(), version);
            // 范围内找不到宜居区 → 不放进列表，页面显示「没有查询到数据」
            if (r == null || r.livDistrictId == null) {
                continue;
            }
            // 不在评估范围的区也放进来（inScope = false）：页面不展示得分，公园指标照常展示
            out.add(new LiveabilitySnapshot.District(
                    d.code(), r.inScope, r.scoreExisting, r.scoreCurrent, r.scoreTarget, r.parks, r.parkAreaHa, r.parkGapHa));
        }
        log.info("Liveability 抽取 {} 个区，其中在评估范围内 {} 个",
                out.size(), out.stream().filter(x -> Boolean.TRUE.equals(x.inScope())).count());
        return new LiveabilitySnapshot(mapper.dataDate(version), out);
    }
}
