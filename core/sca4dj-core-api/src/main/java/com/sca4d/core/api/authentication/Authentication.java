package com.sca4d.core.api.authentication;

import com.sca4d.core.api.code.TreeCode;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 记录一种认证方式已经成功验证账号（Authentication）。
 *
 * <p>认证方法成功后直接返回该对象。多因素认证评估会话（Session，{@link Session}）中的认证列表。
 */
@SuppressWarnings("ClassCanBeRecord")
public final class Authentication {

    private final long accountId;

    private final TreeCode methodCode;

    private final Set<AuthenticationFactorKind> factorKinds;

    private final Instant authenticatedAt;

    /**
     * 创建认证证据。
     *
     * @param accountId       已验证的 Account
     * @param methodCode      完成验证的认证方式编码
     * @param factorKinds     已验证的认证因素
     * @param authenticatedAt 验证完成时间
     */
    public Authentication(
            long accountId,
            TreeCode methodCode,
            Set<AuthenticationFactorKind> factorKinds,
            Instant authenticatedAt) {
        if (accountId <= 0) {
            throw new IllegalArgumentException("账号标识必须为正数");
        }
        this.accountId = accountId;
        this.methodCode = Objects.requireNonNull(methodCode, "认证方式编码不能为空");
        this.factorKinds = Set.copyOf(Objects.requireNonNull(factorKinds, "认证因素不能为空"));
        this.authenticatedAt = Objects.requireNonNull(authenticatedAt, "认证完成时间不能为空");
    }

    /**
     * 返回已验证的 Account 标识。
     *
     * @return 已验证的 Account 标识
     */
    public long accountId() {
        return accountId;
    }

    /**
     * 返回完成验证的认证方式编码。
     *
     * @return 完成验证的认证方式编码
     */
    public TreeCode methodCode() {
        return methodCode;
    }

    /**
     * 返回本次成功认证确认的因素类别。
     *
     * @return 创建认证记录时复制的不可修改因素集合
     */
    public Set<AuthenticationFactorKind> factorKinds() {
        return factorKinds;
    }

    /**
     * 返回验证完成时间。
     *
     * @return 验证完成时间
     */
    public Instant authenticatedAt() {
        return authenticatedAt;
    }
}
