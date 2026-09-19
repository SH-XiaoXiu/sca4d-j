package com.sca4d.iot.state;

import java.util.Objects;

/** 返回 reported 更新是否改变 latest 投影以及当前 version。 */
public final class ReportedStateResult {

    /** reported 顺序和摘要比较结果。 */
    public enum Outcome {
        ACCEPTED,
        DUPLICATE,
        OLDER
    }

    private final Outcome outcome;

    private final long version;

    /** @param outcome 处理结果 @param version 当前 latest version */
    public ReportedStateResult(Outcome outcome, long version) {
        if (version < 0)
            throw new IllegalArgumentException("reported version 不能为负数");
        this.outcome = Objects.requireNonNull(outcome, "reported outcome 不能为空");
        this.version = version;
    }

    /** @return 接受、重复或较旧 */
    public Outcome outcome() {
        return outcome;
    }

    /** @return 当前 latest version */
    public long version() {
        return version;
    }
}
