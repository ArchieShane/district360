package com.district360.common.theme;

import java.time.LocalDate;

/**
 * 主题库抽取器：读源库（如 ODS），把记录逐行推给 Sink，不直接写主题库。
 * <p>
 * 框架（d360-app 的 EtlService）负责：找到 {@link #sinkType()} 对应的 {@link ThemeLoader}，
 * 由它开启主题库事务、清空该主题的表、把 Sink 交给本抽取器、抽完后做空间归属关联与汇总。
 * 抛异常即本次失败，事务回滚，主题库保持原样。
 *
 * @param <K> 写入端类型
 */
public interface ThemeExtractor<K extends ThemeSink> {

    /** 抽取器标识，用于手动触发（POST /api/v1/admin/etl/{source}）和抽取日志，如 ods-spatial。 */
    String source();

    /**
     * 是否参加每日定时抽取和 POST /admin/etl/all。只做局部同步的抽取器（如只同步单元的 ods-units）返回 false，
     * 只能按名称手动触发，避免每晚和全量抽取重复执行。
     */
    default boolean scheduled() {
        return true;
    }

    /** 写入端类型，框架按它找 Loader。 */
    Class<K> sinkType();

    /**
     * 读源库并逐行写入 sink。
     *
     * @return 源数据日期（可为 null）
     */
    LocalDate extract(K sink);
}
