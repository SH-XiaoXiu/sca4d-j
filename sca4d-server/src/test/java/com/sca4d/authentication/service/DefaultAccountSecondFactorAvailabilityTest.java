package com.sca4d.authentication.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AccountSecondFactorMethod;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

/** 验证 Account 第二因素判断只接受当前已注册方式的可用结果。 */
class DefaultAccountSecondFactorAvailabilityTest {

    @Test
    void shouldRejectAccountWhenNoSecondFactorMethodIsRegistered() {
        IamAccountEntity account = account();

        assertThat(new DefaultAccountSecondFactorAvailability(List.of()).available(account))
                .isFalse();
    }

    @Test
    void shouldAcceptAccountWhenAnyRegisteredMethodIsAvailable() {
        IamAccountEntity account = account();
        AccountSecondFactorMethod unavailable = mock(AccountSecondFactorMethod.class);
        AccountSecondFactorMethod available = mock(AccountSecondFactorMethod.class);
        when(available.available(account, null)).thenReturn(true);

        assertThat(new DefaultAccountSecondFactorAvailability(List.of(unavailable, available))
                .available(account))
                .isTrue();
    }

    @Test
    void shouldExcludeCredentialBeingRemovedFromAvailableMethods() {
        IamAccountEntity account = account();
        AccountSecondFactorMethod method = mock(AccountSecondFactorMethod.class);
        when(method.available(account, 40L)).thenReturn(false);

        assertThat(new DefaultAccountSecondFactorAvailability(List.of(method))
                .availableAfterRemovingCredential(account, 40L))
                .isFalse();
    }

    /** @return 未启用第二因素的 GLOBAL Account 测试对象 */
    private IamAccountEntity account() {
        return new IamAccountEntity().setId(20L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null).setStatus(AccountStatus.ACTIVE)
                .setSecondFactorRequired(false).setVersion(3L);
    }
}
