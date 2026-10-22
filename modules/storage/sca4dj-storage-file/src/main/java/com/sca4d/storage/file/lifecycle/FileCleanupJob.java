package com.sca4d.storage.file.lifecycle;

import com.sca4d.core.api.job.DirectJob;
import com.sca4d.core.api.job.JobExecution;
import com.sca4d.core.api.job.JobKey;
import com.sca4d.core.api.retry.RetryRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 由分布式 Job runtime 触发一次文件过期与待删除对象扫描。
 *
 * <p>FileCleanupService 已按文件保存删除租约、成功和下次重试时间，因此该 Job 不再增加外层重试；
 * 本次扫描未完成的记录由下一次代码计划继续处理。
 */
public class FileCleanupJob extends DirectJob<Void, Void> {

    private static final JobKey KEY = new JobKey("STORAGE/FILE/CLEANUP");

    private final @NotNull FileCleanupService cleanupService;

    /**
     * 创建执行一次文件清理扫描的 Job。
     *
     * @param cleanupService 负责文件删除租约、对象幂等删除和结果写回的服务
     * @throws NullPointerException 文件清理服务为空时抛出
     */
    public FileCleanupJob(@NotNull FileCleanupService cleanupService) {
        this.cleanupService = Objects.requireNonNull(cleanupService, "文件清理服务不能为空");
    }

    @Override
    public @NotNull JobKey key() {
        return KEY;
    }

    @Override
    public int definitionVersion() {
        return 1;
    }

    @Override
    public @NotNull Class<Void> inputType() {
        return Void.class;
    }

    @Override
    public @NotNull Class<Void> outputType() {
        return Void.class;
    }

    @Override
    public @NotNull RetryRule retryRule() {
        return RetryRule.once();
    }

    @Override
    protected @Nullable Void execute(@NotNull JobExecution execution, @Nullable Void input) {
        cleanupService.cleanup();
        return null;
    }
}
