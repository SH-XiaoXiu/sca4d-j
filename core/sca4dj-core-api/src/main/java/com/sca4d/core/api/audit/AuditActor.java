package com.sca4d.core.api.audit;

import java.util.Optional;

/** 审计事件发生时的身份快照。 */
public class AuditActor {

    private final long subjectId;

    private final Long accountId;

    private final long principalId;

    /**
     * 创建审计行为主体。
     *
     * @param subjectId Subject 标识
     * @param accountId 可选 Account 标识，SYSTEM 行为为空
     * @param principalId Principal 标识
     */
    public AuditActor(long subjectId, Long accountId, long principalId) {
        if (subjectId <= 0 || principalId <= 0 || accountId != null && accountId <= 0) {
            throw new IllegalArgumentException("审计身份标识必须为正数");
        }
        this.subjectId = subjectId;
        this.accountId = accountId;
        this.principalId = principalId;
    }

    /**
     * 返回Subject 标识。
     *
     * @return Subject 标识
     */
    public long subjectId() {
        return subjectId;
    }

    /**
     * 返回可选 Account 标识。
     *
     * @return 可选 Account 标识
     */
    public Optional<Long> accountId() {
        return Optional.ofNullable(accountId);
    }

    /**
     * 返回Principal 标识。
     *
     * @return Principal 标识
     */
    public long principalId() {
        return principalId;
    }
}
