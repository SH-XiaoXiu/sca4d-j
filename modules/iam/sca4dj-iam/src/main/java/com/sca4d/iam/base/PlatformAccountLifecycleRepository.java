package com.sca4d.iam.base;

import com.sca4d.core.api.persistence.AffectedRowCounts;
import com.sca4d.core.api.persistence.AffectedRows;
import com.sca4d.core.api.persistence.ZeroRowPolicy;
import com.sca4d.iam.base.mapper.PlatformAccountMapper;
import com.sca4d.iam.identity.PrincipalType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/** 隔离 Platform Account 生命周期服务对 Mapper 和影响行数的依赖。 */
@ApiStatus.Internal
public class PlatformAccountLifecycleRepository {

    private final @NotNull PlatformAccountMapper mapper;

    private final @NotNull PrincipalType principalType;

    /** 创建固定处理 PlatformPrincipal 的生命周期 Repository。 */
    public PlatformAccountLifecycleRepository(
            @NotNull PlatformAccountMapper mapper,
            @NotNull PrincipalType principalType) {
        this.mapper = Objects.requireNonNull(mapper, "Platform Account Mapper 不能为空");
        this.principalType = Objects.requireNonNull(principalType, "PlatformPrincipal 类型不能为空");
    }

    /** 查询一个未删除的 Platform Account。 */
    @NotNull
    Optional<PlatformAccountEntry> find(@NotNull Long accountId) {
        return Optional.ofNullable(
                mapper.selectPlatformAccount(
                        Objects.requireNonNull(accountId, "Account 标识不能为空"),
                        principalType));
    }

    /** 判断 Subject 是否仍有目标以外的未删除 Account。 */
    boolean hasOtherSubjectAccount(@NotNull Long subjectId, @NotNull Long accountId) {
        return mapper.existsOtherAccountForSubject(subjectId, accountId);
    }

    /** 判断 Principal 是否仍有目标以外的未删除 Account。 */
    boolean hasOtherPrincipalAccount(@NotNull Long principalId, @NotNull Long accountId) {
        return mapper.existsOtherAccountForPrincipal(principalId, accountId);
    }

    /** 停用并逻辑删除 Account 及其登录标识，再物理删除 Principal 关系。 */
    void isolateAccount(@NotNull Long accountId) {
        AffectedRowCounts.requireSingle(
                mapper.markPlatformAccountDeleted(accountId),
                ZeroRowPolicy.ERROR,
                "隔离 Platform Account");
        AffectedRowCounts.requireAtLeast(
                mapper.markAccountIdentifiersDeleted(accountId),
                1,
                "隔离 Platform Account 登录标识");
        AffectedRowCounts.requireAtLeast(
                mapper.deleteAccountPrincipalLinks(accountId),
                1,
                "删除 Platform Account Principal 关系");
    }

    /** 停用并逻辑删除不再被其他 Account 使用的 Principal。 */
    @AffectedRows(target = "PlatformPrincipal")
    void isolatePrincipal(@NotNull Long principalId) {
        mapper.markPrincipalDeleted(principalId);
    }

    /** 停用并逻辑删除不再拥有其他 Account 的 Subject。 */
    @AffectedRows(target = "Platform Account 的 Subject")
    void isolateSubject(@NotNull Long subjectId) {
        mapper.markSubjectDeleted(subjectId);
    }
}
