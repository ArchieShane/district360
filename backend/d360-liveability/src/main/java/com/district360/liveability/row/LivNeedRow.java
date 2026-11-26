package com.district360.liveability.row;

import java.math.BigDecimal;

/** {@code LiveabilityMapper.needs} 的结果行：理想社区调查一个选项的得票占比。 */
public class LivNeedRow {
    public String item;
    /** 得票占比 %（选该项的受访人数 ÷ 本区全部选择次数） */
    public BigDecimal pct;
    public Integer votes;
    /** 本区受访人数 */
    public Integer respondents;
}
