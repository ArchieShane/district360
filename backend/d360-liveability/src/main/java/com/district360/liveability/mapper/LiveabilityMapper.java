package com.district360.liveability.mapper;

import com.district360.liveability.row.LivDistrictRow;
import com.district360.liveability.row.LivFacilityCountRow;
import com.district360.liveability.row.LivFacilityGapRow;
import com.district360.liveability.row.LivFacilityTypeRow;
import com.district360.liveability.row.LivNeedRow;
import com.district360.liveability.row.LivRunRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Liveability 源库查询，SQL 在 resources/mapper/liveability/LiveabilityMapper.xml。
 * <p>
 * 口径要求：<b>按统计范围做空间查询，不用区 ID</b>。参数 scope 是该区 makani 原始区面（EWKB hex，
 * 来自 {@code DistrictRef#scopeEwkbHex()}），已验证与 dim_districts.geom 基本重合（IoU ≥ 0.997）。
 * 计算版本取当前发布版本（{@link #publishedVersion()}），不写死版本号。
 * 取数逻辑与宜居平台 dashboard 接口一致，实际取数方式同步写在 metric-lineage.yml（liv_*、park*），页面悬停展示。
 */
public interface LiveabilityMapper {

    /** 当前发布的计算版本：dim_calc_versions 里 current_flag = true 且 status = 'approved' 的那一条；没有时返回 null。 */
    Long publishedVersion();

    /** 最近一次审批通过的优先级批次；没有时返回 null（分档、最缺设施不抽取）。 */
    LivRunRow approvedRun();

    /**
     * 一个区在指定计算版本下的宜居指标（评分、公园、CAPEX / OPEX、优先级分档）；范围内没有宜居区时 livDistrictId 为 null。
     * runId 为 null 时分档为 null。
     */
    LivDistrictRow districtStats(@Param("scope") String scopeEwkbHex, @Param("version") long calcVersionId,
                                 @Param("runId") Long runId);

    /** 区域最缺设施：FPP 类项目（含公园）在途评分低于分档目标的，按差距降序前 limit 个。 */
    List<LivFacilityGapRow> facilityGaps(@Param("livDistrictId") int livDistrictId, @Param("version") long calcVersionId,
                                         @Param("runId") long runId, @Param("assumptionSetId") long assumptionSetId,
                                         @Param("limit") int limit);

    /** 居民需求：理想社区调查本区得票占比前 limit 项；受访人数不足 minRespondents 时为空。 */
    List<LivNeedRow> needs(@Param("livDistrictId") int livDistrictId, @Param("version") long calcVersionId,
                           @Param("minRespondents") int minRespondents, @Param("limit") int limit);

    /** 设施类型目录：原型 8 个领域内、fact_facilities 有数据的类型（当前 35 类）。 */
    List<LivFacilityTypeRow> facilityCatalogue();

    /** 一个区（范围）内各设施类型的现状数量，只含 types 内、数量大于 0 的。 */
    List<LivFacilityCountRow> facilityTypeCounts(@Param("scope") String scopeEwkbHex, @Param("types") List<String> types);

    /** 数据日期：指定计算版本下 fact_district_scores.updated_at 的最大值。 */
    LocalDate dataDate(@Param("version") long calcVersionId);
}
