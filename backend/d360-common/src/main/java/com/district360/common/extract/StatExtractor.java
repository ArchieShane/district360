package com.district360.common.extract;

import com.district360.common.district.DistrictRef;

import java.util.List;

/**
 * 各数据源的抽取器：只负责读自己的源库并返回快照，不直接写统计库。
 * <p>
 * 写入（整批替换、单事务）、日志、定时调度都由框架完成。
 *
 * @param <S> 快照类型，定义在 {@code com.district360.common.extract.snapshot} 包里
 */
public interface StatExtractor<S extends SourceSnapshot> {

    /** 数据源标识：makani / liveability / adrec。 */
    String source();

    /**
     * 读源库并返回快照。抛异常即视为本次抽取失败，统计库保持原样。
     *
     * @param districts 要统计的区（带统计范围）。makani 抽取器收到的是空列表：区列表本身由它从 makani 产出；
     *                  其他抽取器收到 ADM 全部区，按 {@link DistrictRef#scopeEwkbHex()} 做空间查询
     */
    S extract(List<DistrictRef> districts);
}
