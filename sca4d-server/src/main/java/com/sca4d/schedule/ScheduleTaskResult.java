package com.sca4d.schedule;

import org.jetbrains.annotations.NotNull;

/** 一次 ScheduleTask 成功完成后允许持久化的安全摘要。 */
public final class ScheduleTaskResult {

    private final String summary;

    /** @param summary 不包含凭据、Token 或完整敏感业务内容的执行摘要 */
    public ScheduleTaskResult(@NotNull String summary) {
        this.summary = summary.length() <= 1000 ? summary : summary.substring(0, 1000);
    }

    /** @return 最多 1000 字符的安全执行摘要 */
    public @NotNull String getSummary() {
        return summary;
    }
}
