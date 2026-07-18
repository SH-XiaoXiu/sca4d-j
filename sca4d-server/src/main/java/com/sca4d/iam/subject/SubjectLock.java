package com.sca4d.iam.subject;

import com.sca4d.common.concurrent.lock.DistributedLockTemplate;
import com.sca4d.common.concurrent.lock.LockedOperation;
import com.sca4d.config.properties.Sca4djConcurrentProperties;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Subject 登录、认证材料修改、状态修改和删除共用的集群锁。
 *
 * <p>登录会话创建、Credential 变更以及 Subject 更新、停用和删除使用相同锁键，避免认证材料与 Subject 状态同时变化。
 */
@Component
@RequiredArgsConstructor
public class SubjectLock {

    private final DistributedLockTemplate lockTemplate;
    private final Sca4djConcurrentProperties properties;

    /**
     * 在指定 Subject 的集群锁内执行一个完整操作。
     *
     * @param subjectId 需要串行执行登录、认证材料或状态修改的 Subject 主键
     * @param operation 必须在锁租约内完成的操作
     * @param <T> 操作返回值类型
     * @return 操作结果
     */
    public <T> T execute(@NotNull Long subjectId, @NotNull LockedOperation<T> operation) {
        try {
            return lockTemplate.execute(properties.getLock().getKeyPrefix() + ":subject:" + subjectId, operation);
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Subject 锁内操作失败", throwable);
        }
    }
}
