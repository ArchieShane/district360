package com.district360.liveability.row;

import java.math.BigDecimal;

/** {@code LiveabilityMapper.facilityGaps} 的结果行：一个设施项目的三个阶段供给评分、分档目标与差距（0–100）。 */
public class LivFacilityGapRow {
    public String facility;
    /** critical / important / basic */
    public String category;
    public BigDecimal scoreExisting;
    public BigDecimal scorePipeline;
    public BigDecimal scoreAp50;
    public BigDecimal targetPct;
    public BigDecimal gap;
}
