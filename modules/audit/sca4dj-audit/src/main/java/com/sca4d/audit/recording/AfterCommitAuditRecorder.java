package com.sca4d.audit.recording;

import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.transaction.TransactionCallbacks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/** 提交后尽力把冻结审计记录交给有界进程内执行器，不提供恢复和重试。 */
public class AfterCommitAuditRecorder implements AuditRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(AfterCommitAuditRecorder.class);

    private final AuditRepository repository;

    private final Executor executor;

    public AfterCommitAuditRecorder(@NotNull AuditRepository repository, @NotNull Executor executor) {
        this.repository = Objects.requireNonNull(repository, "审计 Repository 不能为空");
        this.executor = Objects.requireNonNull(executor, "尽力审计执行器不能为空");
    }

    @Override
    public @NotNull Audit.Mode mode() {
        return Audit.Mode.AFTER_COMMIT;
    }

    /** 在调用线程冻结和脱敏，提交后仅等待有界执行器接受任务。 */
    @Override
    public void record(@NotNull AuditRecord record) {
        AuditRecord checked = Objects.requireNonNull(record, "审计记录不能为空");
        FrozenAuditEntry frozen = repository.freeze(checked);
        Call parent = CallHolder.currentOrNull();
        Call workerCall = parent == null
                ? new Call(UUID.randomUUID().toString(), Instant.now())
                : parent.fork();
        try {
            TransactionCallbacks.afterCommitOrNow(() -> {
                try {
                    executor.execute(() -> {
                        try {
                            CallHolder.run(workerCall, () -> {
                                repository.appendFrozen(frozen);
                                return null;
                            });
                        } catch (Throwable failure) {
                            LOG.error(
                                    "尽力审计写入失败且不会重试 | requestId={} operation={}",
                                    checked.requestId(),
                                    checked.operation().id(),
                                    failure);
                        }
                    });
                } catch (RuntimeException rejected) {
                    LOG.warn(
                            "尽力审计任务未被进程队列接受且不会重试 | requestId={} operation={}",
                            checked.requestId(),
                            checked.operation().id(),
                            rejected);
                }
            });
        } catch (RuntimeException unavailable) {
            LOG.warn(
                    "尽力审计任务未登记且不会重试 | requestId={} operation={}",
                    checked.requestId(),
                    checked.operation().id(),
                    unavailable);
        }
    }
}
