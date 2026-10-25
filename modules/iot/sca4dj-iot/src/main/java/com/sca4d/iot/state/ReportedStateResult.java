package com.sca4d.iot.state;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回 reported 更新是否改变 latest 投影以及当前 version。 */
public class ReportedStateResult {

    /** reported 顺序和摘要比较结果。 */
    public enum Outcome {
        /** 已写入 latest 投影；首次写入时 version 为 1，之后每次写入递增。 */
        ACCEPTED,
        /** 顺序值与内容摘要都与当前投影相同，未改变投影。 */
        DUPLICATE,
        /** 顺序值早于当前投影，未改变投影。 */
        OLDER
    }

    private final @NotNull Outcome outcome;

    private final long version;

    /**
     * 创建 reported 处理结果。
     *
     * @param outcome 处理结果，不能为空
     * @param version 处理后的 latest version，不能为负数
     * @throws IllegalArgumentException version 为负数时抛出
     */
    public ReportedStateResult(@NotNull Outcome outcome, long version) {
        if (version < 0) {
            throw new IllegalArgumentException("reported version 不能为负数");
        }
        this.outcome = Objects.requireNonNull(outcome, "reported outcome 不能为空");
        this.version = version;
    }

    /** @return 接受、重复或较旧 */
    public @NotNull Outcome outcome() {
        return outcome;
    }

    /** @return 当前 latest version */
    public long version() {
        return version;
    }
}
