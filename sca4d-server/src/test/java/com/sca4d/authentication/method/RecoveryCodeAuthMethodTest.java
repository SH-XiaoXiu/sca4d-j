package com.sca4d.authentication.method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.authentication.AuthMethodCapability;
import com.sca4d.authentication.AuthMethodType;
import com.sca4d.authentication.AuthMethodVerification;
import com.sca4d.authentication.AuthenticationProcess;
import com.sca4d.authentication.recovery.RecoveryCodeAuthMethod;
import com.sca4d.authentication.recovery.RecoveryCodeAuthenticationProof;
import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.entity.IamAccountIdentifierEntity;
import com.sca4d.iam.identifier.AccountIdentifierService;
import com.sca4d.iam.identifier.AccountIdentifierStatus;
import com.sca4d.iam.identifier.AccountIdentifierType;
import com.sca4d.iam.recovery.RecoveryCodeService;
import com.sca4d.iam.recovery.RecoveryCodeVerification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/** 验证恢复码认证方式只负责作用域账号解析和 RecoveryCodeService 结果转换。 */
class RecoveryCodeAuthMethodTest {

    @Test
    void shouldResolveScopedAccountAndConsumeRecoveryCode() {
        AccountIdentifierService identifiers = mock(AccountIdentifierService.class);
        RecoveryCodeService recoveryCodes = mock(RecoveryCodeService.class);
        IamAccountIdentifierEntity identifier = new IamAccountIdentifierEntity().setId(40L).setAccountId(30L).setScopeType(AccountScopeType.TENANT)
                .setScopeId(20L).setIdentifierType(AccountIdentifierType.USERNAME).setValue("admin").setContactId(null)
                .setStatus(AccountIdentifierStatus.ACTIVE).setVersion(1L);
        Subject subject = new Subject(10L, "管理员");
        Account account = new Account(30L, 10L, AccountScopeType.TENANT, 20L, true);
        when(identifiers.findActive(
                AccountScopeType.TENANT,
                20L,
                AccountIdentifierType.USERNAME,
                "admin"))
                .thenReturn(identifier);
        when(recoveryCodes.consume(30L, "AAAAA-BBBBB-CCCCC-DDDDD"))
                .thenReturn(new RecoveryCodeVerification(subject, account, 101L, 3L));
        RecoveryCodeAuthMethod method = new RecoveryCodeAuthMethod(identifiers, recoveryCodes);

        AuthMethodVerification verification = method.verify(
                new AuthenticationProcess()
                        .setScopeType(AccountScopeType.TENANT)
                        .setScopeId(20L),
                new RecoveryCodeAuthenticationProof(
                        AccountIdentifierType.USERNAME,
                        "admin",
                        "AAAAA-BBBBB-CCCCC-DDDDD"));

        assertThat(method.type()).isEqualTo(AuthMethodType.RECOVERY_CODE);
        assertThat(method.capabilities()).containsExactly(AuthMethodCapability.RECOVERY);
        assertThat(verification.getSubject()).isSameAs(subject);
        assertThat(verification.getAccount()).isSameAs(account);
        assertThat(verification.getProofId()).isEqualTo("101");
        assertThat(verification.getProofVersion()).isEqualTo(3L);
    }

    @Test
    void shouldUseEquivalentMissingAccountPathForUnknownIdentifier() {
        AccountIdentifierService identifiers = mock(AccountIdentifierService.class);
        RecoveryCodeService recoveryCodes = mock(RecoveryCodeService.class);
        RecoveryCodeAuthMethod method = new RecoveryCodeAuthMethod(identifiers, recoveryCodes);
        RecoveryCodeAuthenticationProof proof = new RecoveryCodeAuthenticationProof(
                AccountIdentifierType.EMAIL,
                "missing@example.com",
                "AAAAA-BBBBB-CCCCC-DDDDD");
        when(recoveryCodes.consume(-1L, "AAAAA-BBBBB-CCCCC-DDDDD"))
                .thenThrow(new UnauthenticatedException());

        assertThatThrownBy(() -> method.verify(
                new AuthenticationProcess().setScopeType(AccountScopeType.GLOBAL), proof))
                .isInstanceOf(UnauthenticatedException.class);

        verify(recoveryCodes).consume(-1L, "AAAAA-BBBBB-CCCCC-DDDDD");
    }

    @Test
    void shouldRecheckConsumedCodeWithoutPlaintext() {
        RecoveryCodeService recoveryCodes = mock(RecoveryCodeService.class);
        Subject subject = new Subject(10L, "管理员");
        Account account = new Account(30L, 10L, AccountScopeType.GLOBAL, null, true);
        RecoveryCodeVerification current = new RecoveryCodeVerification(subject, account, 101L, 3L);
        when(recoveryCodes.requireCurrent(ArgumentMatchers.any(RecoveryCodeVerification.class)))
                .thenReturn(current);
        RecoveryCodeAuthMethod method = new RecoveryCodeAuthMethod(
                mock(AccountIdentifierService.class), recoveryCodes);

        AuthMethodVerification result = method.requireCurrent(new AuthMethodVerification(
                AuthMethodType.RECOVERY_CODE, subject, account, "101", 3L));

        assertThat(result.getProofId()).isEqualTo("101");
        assertThat(result.getProofVersion()).isEqualTo(3L);
        verify(recoveryCodes).requireCurrent(ArgumentMatchers.any(RecoveryCodeVerification.class));
    }
}
