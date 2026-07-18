package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.iam.account.AccountSecondFactorAvailability;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.account.service.DefaultAccountService;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.repository.IamRepository;
import org.junit.jupiter.api.Test;

/** 验证 Account 第二因子策略不会进入缺少可用认证因素的状态。 */
class DefaultAccountServiceTest {

    @Test
    void shouldRequireAvailableSecondFactorWhenEnablingSecondFactor() {
        IamRepository repository = mock(IamRepository.class);
        AccountSecondFactorAvailability secondFactors = mock(AccountSecondFactorAvailability.class);
        IamAccountEntity account = new IamAccountEntity().setId(20L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(3L);
        when(repository.findAccount(20L)).thenReturn(account);
        DefaultAccountService service = new DefaultAccountService(repository, secondFactors);

        assertThatThrownBy(() -> service.changeSecondFactorRequirement(20L, true, 3L))
                .isInstanceOf(BizException.class)
                .satisfies(exception -> assertThat(((BizException) exception).getErrorCode())
                        .isEqualTo(IamErrorCode.ACCOUNT_SECOND_FACTOR_REQUIRED));
        verify(secondFactors).available(account);
        verify(repository, never()).changeSecondFactorRequirement(20L, true, 3L);
    }

    @Test
    void shouldEnableSecondFactorWhenRegisteredMethodIsAvailable() {
        IamRepository repository = mock(IamRepository.class);
        AccountSecondFactorAvailability secondFactors = mock(AccountSecondFactorAvailability.class);
        IamAccountEntity before = new IamAccountEntity().setId(20L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(3L);
        IamAccountEntity after = new IamAccountEntity().setId(20L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(4L);
        when(repository.findAccount(20L)).thenReturn(before, after);
        when(secondFactors.available(before)).thenReturn(true);
        DefaultAccountService service = new DefaultAccountService(repository, secondFactors);

        assertThat(service.changeSecondFactorRequirement(20L, true, 3L))
                .satisfies(account -> {
                    assertThat(account.getId()).isEqualTo(20L);
                    assertThat(Boolean.TRUE.equals(account.getSecondFactorRequired())).isTrue();
                    assertThat(account.getVersion()).isEqualTo(4L);
                });
        verify(secondFactors).available(before);
        verify(repository).changeSecondFactorRequirement(20L, true, 3L);
    }

    @Test
    void shouldAllowDisablingWithoutCheckingAvailableMethods() {
        IamRepository repository = mock(IamRepository.class);
        AccountSecondFactorAvailability secondFactors = mock(AccountSecondFactorAvailability.class);
        IamAccountEntity account = new IamAccountEntity().setId(20L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(3L);
        when(repository.findAccount(20L)).thenReturn(account);
        DefaultAccountService service = new DefaultAccountService(repository, secondFactors);

        service.changeSecondFactorRequirement(20L, false, 3L);

        verify(secondFactors, never()).available(account);
        verify(repository).changeSecondFactorRequirement(20L, false, 3L);
    }
}
