package com.district360.app.web.dto;

/** 区块状态，前端据此决定显示数值、「样例数值」标记、「不在宜居评估范围」或占位。 */
public final class BlockStatus {
    public static final String OK = "ok";
    public static final String SAMPLE = "sample";
    /** 不在宜居评估范围（宜居库 param_district_scope.in_scope = false），不展示宜居得分 */
    public static final String NOT_ASSESSED = "not_assessed";
    public static final String UNAVAILABLE = "unavailable";

    private BlockStatus() {
    }
}
