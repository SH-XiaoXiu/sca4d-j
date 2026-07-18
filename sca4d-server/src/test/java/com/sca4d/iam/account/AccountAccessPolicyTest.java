package com.sca4d.iam.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sca4d.common.access.ResourceAccess;
import com.sca4d.common.access.ResourceAccessDeniedException;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.error.IamErrorCode;
import org.junit.jupiter.api.Test;

/** 验证 Account 安全资源的 Principal 作用域。 */
class AccountAccessPolicyTest {

    private final AccountAccessPolicy policy = new AccountAccessPolicy(new ResourceAccess());

    @Test
    void shouldAllowDefaultOnlyForSameSubject() {
        IamAccountEntity account = new IamAccountEntity().setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL);

        assertThatCode(() -> policy.require(Principal.of(1L, PrincipalType.DEFAULT, 10L, null), account))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.require(Principal.of(2L, PrincipalType.DEFAULT, 11L, null), account))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void shouldAllowTenantOnlyForCurrentTenantAccount() {
        IamAccountEntity account = new IamAccountEntity()
                .setScopeType(AccountScopeType.TENANT)
                .setScopeId(20L);

        assertThatCode(() -> policy.require(Principal.of(1L, PrincipalType.TENANT, 10L, 20L), account))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.require(Principal.of(1L, PrincipalType.TENANT, 10L, 21L), account))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void shouldAllowPlatformForGlobalAccount() {
        IamAccountEntity account = new IamAccountEntity().setScopeType(AccountScopeType.GLOBAL);

        assertThatCode(() -> policy.require(Principal.of(1L, PrincipalType.PLATFORM, 10L, null), account))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTenantAccountOutsideRequestedTenantAsNotFound() {
        IamAccountEntity account = new IamAccountEntity()
                .setScopeType(AccountScopeType.TENANT)
                .setScopeId(20L);

        assertThatThrownBy(() -> policy.requireTenantAccount(
                Principal.of(1L, PrincipalType.TENANT, 10L, 20L), 21L, account))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(IamErrorCode.ACCOUNT_NOT_FOUND));
    }
}
