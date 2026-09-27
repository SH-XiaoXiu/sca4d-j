package com.sca4d.iot.api.state;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 返回 reported 更新是否改变 latest 投影以及更新后的版本。 */
public class ReportedStateWriteResult {

    /** reported 顺序和内容比较结果。 */
    public enum Outcome {
        /** 新报文已经更新当前投影。 */
        ACCEPTED,
        /** 新报文与当前投影使用相同顺序和内容。 */
        DUPLICATE,
        /** 新报文顺序早于当前投影。 */
        OLDER
    }

    private final @NotNull Outcome outcome;

    private final long version;

    /** 创建包含当前投影版本的写入结果。 */
    public ReportedStateWriteResult(@NotNull Outcome outcome, long version) {
        if (version <= 0) {
            throw new IllegalArgumentException("reported version 必须为正数");
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
