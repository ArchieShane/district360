package com.district360.adrec.extract;

import com.district360.adrec.mapper.AdrecMapper;
import com.district360.common.district.DistrictRef;
import com.district360.common.extract.StatExtractor;
import com.district360.common.extract.snapshot.AdrecSnapshot;

import java.util.List;

/**
 * ADREC 抽取。
 * <p>
 * TODO(房地产同事)：
 * <ol>
 *   <li>用 {@link DistrictRef#scopeEwkbHex()}（统计范围 = makani 原始区面）做空间汇总：记录位置落在范围内即计入，
 *       <b>不用区名 / 区 ID</b>，保证与 makani、宜居口径一致（code 用 {@link DistrictRef#code()}）；</li>
 *   <li>近 12 个月区级汇总放 {@link AdrecSnapshot.District}，12 个月柱放 {@link AdrecSnapshot.Monthly}；</li>
 *   <li>可选：如果只能按社区匹配，把用到的社区 → 区对应放进 {@link AdrecSnapshot.CommunityMapping} 以便核对；</li>
 *   <li>返回快照即可，写统计库、日志、定时都由框架处理。</li>
 * </ol>
 * 本地验证：配置 d360.datasource.adrec.*，然后 POST /api/v1/admin/etl/adrec。
 */
public class AdrecExtractor implements StatExtractor<AdrecSnapshot> {

    private final AdrecMapper mapper;

    public AdrecExtractor(AdrecMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String source() {
        return "adrec";
    }

    @Override
    public AdrecSnapshot extract(List<DistrictRef> districts) {
        throw new UnsupportedOperationException("AdrecExtractor 尚未实现（房地产同事负责）");
    }
}
