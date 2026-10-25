package com.sca4d.auth.password.credential;

import com.sca4d.auth.password.PasswordErrorCode;
import com.sca4d.auth.password.credential.mapper.PasswordCredentialMapper;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 读取和保存 Password 方法自己拥有的密码凭据，隔离 `PasswordAuthenticationMethod` 对 MyBatis 访问细节的依赖。
 *
 * <p>底层 MyBatis 动态代理说明由 `PasswordCredentialMapper` 的类型注释统一提供。
 */
public class PasswordCredentialRepository {

    private final PasswordCredentialMapper mapper;

    /**
     * 创建密码凭据 Repository。
     *
     * @param mapper 执行密码凭据 SQL 的 Mapper
     */
    public PasswordCredentialRepository(@NotNull PasswordCredentialMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "密码凭据 Mapper 不能为空");
    }

    /**
     * 为账号写入初始密码摘要；账号已有凭据时保持原摘要不变。
     *
     * @param accountId 密码凭据所属账号
     * @param passwordHash Argon2id 不可逆摘要
     */
    public void insertIfAbsent(@NotNull Long accountId, @NotNull String passwordHash) {
        AffectedRowCounts.requireSingle(
                mapper.insertCredential(
                        Objects.requireNonNull(accountId, "密码凭据账号不能为空"),
                        Objects.requireNonNull(passwordHash, "密码摘要不能为空")),
                ZeroRowPolicy.ALLOW,
                "初始化 Password 凭据");
    }

    /**
     * 为新账号写入初始密码摘要。
     *
     * @param accountId 密码凭据所属账号
     * @param passwordHash Argon2id 不可逆摘要
     * @return 新建的密码凭据
     * @throws BusinessException 账号已有密码凭据时抛出
     */
    @Transactional
    public @NotNull PasswordCredential insert(
            @NotNull Long accountId,
            @NotNull String passwordHash) {
        Long checkedId = Objects.requireNonNull(accountId, "密码凭据账号不能为空");
        if (!AffectedRowCounts.changed(
                mapper.insertCredential(checkedId, Objects.requireNonNull(passwordHash, "密码摘要不能为空")),
                "新增 Password 凭据")) {
            throw new BusinessException(PasswordErrorCode.CREDENTIAL_EXISTS);
        }
        return Objects.requireNonNull(findByAccountId(checkedId), "新增后的密码凭据不存在");
    }

    /**
     * 查询账号当前保存的密码凭据。
     *
     * @param accountId 密码凭据所属账号
     * @return 密码凭据；账号尚未设置密码时为 {@code null}
     */
    public @Nullable PasswordCredential findByAccountId(@NotNull Long accountId) {
        PasswordCredential current = mapper.selectByAccountId(
                Objects.requireNonNull(accountId, "密码凭据账号不能为空"));
        return current == null || !current.active() ? null : current;
    }

    /** 按指定生命周期状态查询账号保存的密码凭据。 */
    public @Nullable PasswordCredential findByAccountId(
            @NotNull Long accountId,
            @NotNull PasswordCredential.Status expectedStatus) {
        PasswordCredential current = mapper.selectByAccountId(
                Objects.requireNonNull(accountId, "密码凭据账号不能为空"));
        return current != null && current.status() == Objects.requireNonNull(expectedStatus, "密码凭据状态不能为空")
                ? current
                : null;
    }

    /** 按当前凭据的版本和状态替换密码摘要及目标状态。 */
    @Transactional
    public @NotNull PasswordCredential replace(
            @NotNull PasswordCredential current,
            @NotNull String passwordHash,
            @NotNull PasswordCredential.Status status) {
        PasswordCredential checked = Objects.requireNonNull(current, "当前密码凭据不能为空");
        AffectedRowCounts.requireSingle(
                mapper.update(
                        checked.accountId(),
                        checked.version(),
                        checked.status(),
                        Patch.set(Objects.requireNonNull(passwordHash, "密码摘要不能为空")),
                        Patch.set(Objects.requireNonNull(status, "密码凭据状态不能为空"))),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "Password 凭据");
        return Objects.requireNonNull(
                mapper.selectByAccountId(checked.accountId()),
                "更新后的密码凭据不存在");
    }

    /** 按账号、摘要和预期版本替换 ACTIVE 凭据。 */
    @Transactional
    public @NotNull PasswordCredential replace(
            long accountId,
            @NotNull String passwordHash,
            @NotNull Version expectedVersion) {
        AffectedRowCounts.requireSingle(
                mapper.update(
                        accountId,
                        Objects.requireNonNull(expectedVersion, "密码凭据版本不能为空"),
                        PasswordCredential.Status.ACTIVE,
                        Patch.set(Objects.requireNonNull(passwordHash, "密码摘要不能为空")),
                        Patch.set(PasswordCredential.Status.ACTIVE)),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "Password 凭据");
        return Objects.requireNonNull(
                mapper.selectByAccountId(accountId),
                "更新后的密码凭据不存在");
    }

    /** 按乐观版本和当前状态切换密码凭据状态。 */
    @Transactional
    public @NotNull PasswordCredential changeStatus(
            @NotNull PasswordCredential current,
            @NotNull PasswordCredential.Status status) {
        PasswordCredential checked = Objects.requireNonNull(current, "当前密码凭据不能为空");
        AffectedRowCounts.requireSingle(
                mapper.update(
                        checked.accountId(),
                        checked.version(),
                        checked.status(),
                        Patch.ignore(),
                        Patch.set(Objects.requireNonNull(status, "密码凭据状态不能为空"))),
                ZeroRowPolicy.OPTIMISTIC_LOCK,
                "Password 凭据");
        return Objects.requireNonNull(
                mapper.selectByAccountId(checked.accountId()),
                "状态变更后的密码凭据不存在");
    }

    /**
     * 幂等删除账号保存的密码摘要。
     *
     * @param accountId 待清理凭据的 Account
     */
    public void erase(@NotNull Long accountId) {
        AffectedRowCounts.requireSingle(
                mapper.deleteByAccountId(Objects.requireNonNull(accountId, "密码凭据账号不能为空")),
                ZeroRowPolicy.ALLOW,
                "删除 Password 凭据");
    }
}
