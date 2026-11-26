package com.district360.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 需关注区域的阈值（原型 Districts to watch）。原型注明这些是工作值，待业主确认后替换，所以放配置不写死。
 *
 * @param gapMin             距目标差距（目标 − 在途）≥ 此值（分）
 * @param capexMinAed        达标所需投资 ≥ 此值（AED）
 * @param parkProvisionMax   公园供给率（现有面积 ÷ 需求面积）&lt; 此值
 * @param parkNeedMinHa      只看公园需求面积 ≥ 此值（公顷）的区；对应原型「人口 ≥ 1 万 × 5 m²/人」，避免小区域误报
 * @param maxCards           首屏显示几个区（其余点「显示全部」）
 */
@ConfigurationProperties(prefix = "d360.watch")
public record WatchProperties(
        @DefaultValue("8") double gapMin,
        @DefaultValue("400000000") double capexMinAed,
        @DefaultValue("0.5") double parkProvisionMax,
        @DefaultValue("5") double parkNeedMinHa,
        @DefaultValue("6") int maxCards) {
}
