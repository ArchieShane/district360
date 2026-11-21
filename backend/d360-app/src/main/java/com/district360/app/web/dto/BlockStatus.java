package com.district360.app.web.dto;

/** 区块状态，前端据此决定显示数值、「样例数值」标记、「未纳入评估」或占位。 */
public final class BlockStatus {
    public static final String OK = "ok";
    public static final String SAMPLE = "sample";
    public static final String NOT_ASSESSED = "not_assessed";
    public static final String UNAVAILABLE = "unavailable";

    private BlockStatus() {
    }
}
