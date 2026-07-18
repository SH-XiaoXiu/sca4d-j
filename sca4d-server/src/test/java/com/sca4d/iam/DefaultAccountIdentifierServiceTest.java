package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.iam.identifier.repository.AccountIdentifierRepository;
import com.sca4d.iam.identifier.service.DefaultAccountIdentifierService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/** 验证 AccountIdentifier 的规范值、作用域和用户名限制。 */
class DefaultAccountIdentifierServiceTest {

    @Test
    void shouldNormalizeUsernameBeforeSavingInsideAccountScope() {
        AccountService accountService = mock(AccountService.class);
        AccountIdentifierRepository repository = mock(AccountIdentifierRepository.class);
        IamAccountEntity account = new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.TENANT).setScopeId(20L)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(0L);
        when(accountService.require(30L)).thenReturn(account);
        DefaultAccountIdentifierService service = new DefaultAccountIdentifierService(accountService, repository);

        service.createUsername(30L, "  Ａlice  ");

        verify(repository).save(
                account, AccountIdentifierType.USERNAME, "Ａlice", "alice", null);
    }

    @Test
    void shouldRejectUsernameContainingScopeSeparator() {
        AccountService accountService = mock(AccountService.class);
        when(accountService.require(30L)).thenReturn(new IamAccountEntity().setId(30L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(false).setVersion(0L));
        DefaultAccountIdentifierService service = new DefaultAccountIdentifierService(
                accountService, mock(AccountIdentifierRepository.class));

        assertThatThrownBy(() -> service.createUsername(30L, "tenant:alice"))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.IDENTIFIER_INVALID));
    }

    @Test
    void shouldResolveSameEmailInsideRequestedTenantScope() {
        AccountIdentifierRepository repository = mock(AccountIdentifierRepository.class);
        IamAccountIdentifierEntity expected = mock(IamAccountIdentifierEntity.class);
        when(repository.findActive(
                AccountScopeType.TENANT,
                20L,
                AccountIdentifierType.EMAIL,
                "alice@example.com"))
                .thenReturn(expected);
        DefaultAccountIdentifierService service = new DefaultAccountIdentifierService(
                mock(AccountService.class), repository);

        assertThat(service.findActive(
                AccountScopeType.TENANT,
                20L,
                AccountIdentifierType.EMAIL,
                " Alice@Example.com "))
                .isSameAs(expected);
    }

    @Test
    void shouldReadAllIdentifiersWithOneRepositoryCallAndPreserveInputAccounts() {
        AccountIdentifierRepository repository = mock(AccountIdentifierRepository.class);
        IamAccountIdentifierEntity first = new IamAccountIdentifierEntity().setId(101L).setAccountId(30L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setIdentifierType(AccountIdentifierType.USERNAME).setValue("alice").setContactId(null).setStatus(AccountIdentifierStatus.ACTIVE)
                .setVersion(0L);
        IamAccountIdentifierEntity second = new IamAccountIdentifierEntity().setId(102L).setAccountId(30L).setScopeType(AccountScopeType.GLOBAL)
                .setScopeId(null).setIdentifierType(AccountIdentifierType.EMAIL).setValue("alice@example.com").setContactId(50L)
                .setStatus(AccountIdentifierStatus.ACTIVE).setVersion(0L);
        when(repository.findByAccounts(List.of(30L, 31L))).thenReturn(List.of(first, second));
        DefaultAccountIdentifierService service = new DefaultAccountIdentifierService(
                mock(AccountService.class), repository);

        Map<Long, List<IamAccountIdentifierEntity>> result = service.findByAccounts(List.of(30L, 31L, 30L));

        assertThat(result).containsOnlyKeys(30L, 31L);
        assertThat(result.get(30L)).containsExactly(first, second);
        assertThat(result.get(31L)).isEmpty();
        verify(repository).findByAccounts(List.of(30L, 31L));
        verifyNoMoreInteractions(repository);
    }
}
