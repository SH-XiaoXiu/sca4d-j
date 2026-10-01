package com.sca4d.auth.engine.attempt;

import com.sca4d.core.api.authentication.Authentication;
import com.sca4d.core.api.authentication.AuthenticationActionCode;
import com.sca4d.core.api.authentication.AuthenticationAttemptId;
import com.sca4d.core.api.authentication.VerifiedAuthentication;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Redis 中动作认证尝试的内部状态；只保存认证记录和非秘密证据引用。
 */
@ApiStatus.Internal
public class AuthenticationAttemptState {

    private final Account account;

    private final AuthenticationActionCode actionCode;

    private final AuthenticationAttemptId id;

    private final Instant expiresAt;

    private final List<Authentication> reusableAuthentications;

    private final List<VerifiedAuthentication> verifications;

    private boolean satisfied;

    /**
     * 创建 Redis 保存的认证尝试。
     *
     * @param id                      尝试标识
     * @param account                 要执行动作的账号
     * @param actionCode              具体应用定义的动作编码
     * @param reusableAuthentications 调用方提供且可能被策略接受的已有认证
     * @param verifications           当前尝试已经完成的认证和非秘密证据
     * @param expiresAt               尝试过期时间
     * @param satisfied               最近一次策略评估是否已经满足
     */
    public AuthenticationAttemptState(
            @NotNull AuthenticationAttemptId id,
            @NotNull Account account,
            @NotNull AuthenticationActionCode actionCode,
            @NotNull List<Authentication> reusableAuthentications,
            @NotNull List<VerifiedAuthentication> verifications,
            @NotNull Instant expiresAt,
            boolean satisfied) {
        this.id = Objects.requireNonNull(id, "认证尝试标识不能为空");
        this.account = Objects.requireNonNull(account, "认证账号不能为空");
        this.actionCode = Objects.requireNonNull(actionCode, "认证动作编码不能为空");
        this.reusableAuthentications = List.copyOf(
                Objects.requireNonNull(reusableAuthentications, "可复用认证记录不能为空"));
        if (this.reusableAuthentications.stream()
                .anyMatch(authentication -> authentication.accountId() != account.accountId())) {
            throw new IllegalArgumentException("可复用认证记录不属于尝试账号");
        }
        this.verifications = new ArrayList<>(Objects.requireNonNull(verifications, "认证结果不能为空"));
        if (this.verifications.stream()
                .anyMatch(verification -> verification.account().accountId() != account.accountId())) {
            throw new IllegalArgumentException("认证结果不属于尝试账号");
        }
        this.expiresAt = Objects.requireNonNull(expiresAt, "认证尝试过期时间不能为空");
        this.satisfied = satisfied;
    }

    /**
     * 认证尝试标识。
     *
     * @return 认证尝试标识
     */
    public @NotNull AuthenticationAttemptId id() {
        return id;
    }

    /**
     * 要执行动作的账号。
     *
     * @return 要执行动作的账号
     */
    public @NotNull Account account() {
        return account;
    }

    /**
     * 认证完成后允许执行的动作。
     *
     * @return 认证完成后允许执行的动作
     */
    public @NotNull AuthenticationActionCode actionCode() {
        return actionCode;
    }

    /**
     * 调用方提供的不可修改已有认证列表。
     *
     * @return 调用方提供的不可修改已有认证列表
     */
    public @NotNull @Unmodifiable List<Authentication> reusableAuthentications() {
        return reusableAuthentications;
    }

    /**
     * 返回当前尝试已经完成的不可修改验证结果列表。
     */
    public @NotNull @Unmodifiable List<VerifiedAuthentication> verifications() {
        return List.copyOf(verifications);
    }

    /**
     * 认证尝试的过期时间。
     *
     * @return 认证尝试的过期时间
     */
    public @NotNull Instant expiresAt() {
        return expiresAt;
    }

    /**
     * 最近一次策略评估是否已经满足。
     *
     * @return 最近一次策略评估是否已经满足
     */
    public boolean satisfied() {
        return satisfied;
    }

    /**
     * 设置当前尝试是否已经满足策略；调用方必须在同一尝试租约内保存结果。
     */
    public void satisfied(boolean value) {
        satisfied = value;
    }

    /**
     * 按认证方法编码替换已有结果，保证同一种认证方法只保留最新一次验证。
     */
    public void put(@NotNull VerifiedAuthentication verification) {
        VerifiedAuthentication checked = Objects.requireNonNull(verification, "认证结果不能为空");
        if (checked.account().accountId() != account.accountId()) {
            throw new IllegalArgumentException("认证结果不属于尝试账号");
        }
        TreeCode code = checked.authentication().methodCode();
        verifications.removeIf(existing -> existing.authentication().methodCode().equals(code));
        verifications.add(checked);
    }
}
