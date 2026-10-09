package com.sca4d.core.api.job;

import com.sca4d.core.api.id.Id;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** 说明一次普通提交新建了运行、复用了同一输入，还是因占用而跳过。 */
public class JobSubmission {

    public enum Status {
        CREATED,
        EXISTING,
        SKIPPED
    }

    private final @Nullable Id runId;

    private final @NotNull Status status;

    private JobSubmission(@NotNull Status status, @Nullable Id runId) {
        this.status = status;
        this.runId = runId;
    }

    /**
     * @param runId 本次创建的 Run
     * @return 本次新建的持久化运行
     */
    public static @NotNull JobSubmission created(@NotNull Id runId) {
        return new JobSubmission(Status.CREATED, runId);
    }

    /**
     * @param runId 已存在的 Run
     * @return 同一工作与输入已存在的运行
     */
    public static @NotNull JobSubmission existing(@NotNull Id runId) {
        return new JobSubmission(Status.EXISTING, runId);
    }

    /** @return 同一工作键由其他输入占用，未创建运行 */
    public static @NotNull JobSubmission skipped() {
        return new JobSubmission(Status.SKIPPED, null);
    }

    /** @return 本次提交的处理结果 */
    public @NotNull Status status() {
        return status;
    }

    /** @return 新建或同一输入复用的 Run；不同工作占用导致跳过时为空 */
    public @NotNull Optional<Id> runId() {
        return Optional.ofNullable(runId);
    }
}
