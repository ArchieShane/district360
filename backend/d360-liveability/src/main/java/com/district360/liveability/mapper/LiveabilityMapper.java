package com.district360.liveability.mapper;

import com.district360.liveability.row.LivDistrictRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * Liveability 源库查询，SQL 在 resources/mapper/liveability/LiveabilityMapper.xml。
 * <p>
 * 口径要求：<b>按统计范围做空间查询，不用区 ID</b>。参数 scope 是该区 makani 原始区面（EWKB hex，
 * 来自 {@code DistrictRef#scopeEwkbHex()}），已验证与 dim_districts.geom 基本重合（IoU ≥ 0.997）。
 * 计算版本取当前发布版本（{@link #publishedVersion()}），不写死版本号。
 * 实际取数方式同步写在 metric-lineage.yml（liv_*、park*），页面悬停展示。
 */
public interface LiveabilityMapper {

    /** 当前发布的计算版本：dim_calc_versions 里 current_flag = true 的那一条（唯一索引保证至多一条）；没有时返回 null。 */
    Long publishedVersion();

    /** 一个区在指定计算版本下的全部宜居指标；范围内没有宜居区时 livDistrictId 为 null。 */
    LivDistrictRow districtStats(@Param("scope") String scopeEwkbHex, @Param("version") long calcVersionId);

    /** 数据日期：指定计算版本下 fact_district_scores.updated_at 的最大值。 */
    LocalDate dataDate(@Param("version") long calcVersionId);
}
