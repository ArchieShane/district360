package com.district360.liveability.extract;

import com.district360.common.district.DistrictRef;
import com.district360.common.extract.StatExtractor;
import com.district360.common.extract.snapshot.LiveabilitySnapshot;
import com.district360.liveability.mapper.LiveabilityMapper;

import java.util.List;

/**
 * Liveability 抽取。
 * <p>
 * TODO(宜居同事)：
 * <ol>
 *   <li>用 {@link DistrictRef#scopeEwkbHex()}（统计范围 = makani 原始区面）在源库做空间查询，<b>不用区 ID</b>，
 *       保证与 makani、ADREC 口径一致；范围内没有宜居数据的区跳过 → 页面显示「未纳入评估」；</li>
 *   <li>组装成 {@link LiveabilitySnapshot.District}（code 用 {@link DistrictRef#code()}）；</li>
 *   <li>返回快照即可，写统计库、日志、定时都由框架处理。</li>
 * </ol>
 * 本地验证：配置 d360.datasource.liveability.*，然后 POST /api/v1/admin/etl/liveability。
 */
public class LiveabilityExtractor implements StatExtractor<LiveabilitySnapshot> {

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
        throw new UnsupportedOperationException("LiveabilityExtractor 尚未实现（宜居同事负责）");
    }
}
