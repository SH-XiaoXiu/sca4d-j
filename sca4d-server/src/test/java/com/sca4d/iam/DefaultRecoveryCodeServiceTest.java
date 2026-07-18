package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.AccountScopeType;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.auth.UnauthenticatedException;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamAccountEntity;
import com.sca4d.entity.IamSubjectEntity;
import com.sca4d.iam.account.AccountService;
import com.sca4d.iam.account.AccountStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.recovery.RecoveryCodeCandidate;
import com.sca4d.iam.recovery.RecoveryCodeSummary;
import com.sca4d.iam.recovery.RecoveryCodeVerification;
import com.sca4d.iam.recovery.repository.RecoveryCodeRepository;
import com.sca4d.iam.recovery.service.DefaultRecoveryCodeService;
import com.sca4d.iam.subject.SubjectService;
import com.sca4d.iam.subject.SubjectStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 验证恢复码只以高熵一次性明文返回，并以 Argon2id 摘要进入 Repository。 */
class DefaultRecoveryCodeServiceTest {

    @Test
    void shouldIssueTenDistinctCodesAndPersistOnlyArgon2idHashes() {
        RecoveryCodeRepository repository = mock(RecoveryCodeRepository.class);
        RecoveryCodeSummary summary = new RecoveryCodeSummary(
                100L, 20L, OffsetDateTime.parse("2026-07-17T10:00:00+08:00"), 10, 0L);
        when(repository.replace(eq(20L), anyList())).thenReturn(summary);
        DefaultRecoveryCodeService service = new DefaultRecoveryCodeService(
                repository, mock(AccountService.class), mock(SubjectService.class));

        var issued = service.issue(20L);

        assertThat(issued.getSummary()).isSameAs(summary);
        assertThat(issued.getCodes())
                .hasSize(10)
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[0-9A-F]{5}(-[0-9A-F]{5}){3}"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> hashes = ArgumentCaptor.forClass(List.class);
        verify(repository).replace(eq(20L), hashes.capture());
        assertThat(hashes.getValue())
                .hasSize(10)
                .allMatch(hash -> hash.startsWith("$argon2id$"));
        assertThat(hashes.getValue())
                .allSatisfy(hash -> assertThat(issued.getCodes()).noneMatch(hash::contains));
    }

    @Test
    void shouldDelegateRevocationAndBatchStatusReads() {
        RecoveryCodeRepository repository = mock(RecoveryCodeRepository.class);
        RecoveryCodeSummary summary = new RecoveryCodeSummary(
                100L, 20L, OffsetDateTime.parse("2026-07-17T10:00:00+08:00"), 7, 0L);
        when(repository.findCurrent(List.of(20L))).thenReturn(Map.of(20L, summary));
        DefaultRecoveryCodeService service = new DefaultRecoveryCodeService(
                repository, mock(AccountService.class), mock(SubjectService.class));

        assertThat(service.findByAccounts(List.of(20L))).containsEntry(20L, summary);
        service.revoke(20L);

        verify(repository).findCurrent(List.of(20L));
        verify(repository).revoke(20L);
    }

    @Test
    void shouldConsumeMatchingCodeAndReturnCurrentAccount() {
        RecoveryCodeRepository repository = mock(RecoveryCodeRepository.class);
        AccountService accountService = mock(AccountService.class);
        SubjectService subjectService = mock(SubjectService.class);
        String hash = new Argon2PasswordEncoder(16, 32, 1, 19456, 2)
                .encode("aaaaabbbbbcccccddddd");
        RecoveryCodeCandidate candidate = new RecoveryCodeCandidate(101L, 100L, 20L, hash, 3L);
        when(repository.findUnused(20L)).thenReturn(List.of(candidate));
        when(repository.use(candidate)).thenReturn(true);
        when(accountService.require(20L)).thenReturn(new IamAccountEntity().setId(20L).setSubjectId(10L).setScopeType(AccountScopeType.GLOBAL).setScopeId(null)
                .setStatus(AccountStatus.ACTIVE).setSecondFactorRequired(true).setVersion(4L));
        when(subjectService.require(10L)).thenReturn(new IamSubjectEntity().setId(10L).setDisplayName("管理员").setStatus(SubjectStatus.ACTIVE).setVersion(2L));
        DefaultRecoveryCodeService service = new DefaultRecoveryCodeService(
                repository, accountService, subjectService);

        var verification = service.consume(20L, "AAAAA-BBBBB-CCCCC-DDDDD");

        assertThat(verification.getCodeId()).isEqualTo(101L);
        assertThat(verification.getSetVersion()).isEqualTo(3L);
        assertThat(verification.getAccount().getAccountId()).isEqualTo(20L);
        assertThat(verification.getSubject().getSubjectId()).isEqualTo(10L);
        verify(repository).use(candidate);
    }

    @Test
    void shouldRejectCodeWhenAnotherRequestConsumedCandidateFirst() {
        RecoveryCodeRepository repository = mock(RecoveryCodeRepository.class);
        String hash = new Argon2PasswordEncoder(16, 32, 1, 19456, 2)
                .encode("aaaaabbbbbcccccddddd");
        RecoveryCodeCandidate candidate = new RecoveryCodeCandidate(101L, 100L, 20L, hash, 3L);
        when(repository.findUnused(20L)).thenReturn(List.of(candidate));
        when(repository.use(candidate)).thenReturn(false);
        DefaultRecoveryCodeService service = new DefaultRecoveryCodeService(
                repository, mock(AccountService.class), mock(SubjectService.class));

        assertThatThrownBy(() -> service.consume(20L, "AAAAA-BBBBB-CCCCC-DDDDD"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void shouldConvertDeletedAccountDuringProofRecheckToAuthenticationFailure() {
        RecoveryCodeRepository repository = mock(RecoveryCodeRepository.class);
        AccountService accountService = mock(AccountService.class);
        when(accountService.require(20L)).thenThrow(new BizException(IamErrorCode.ACCOUNT_NOT_FOUND));
        DefaultRecoveryCodeService service = new DefaultRecoveryCodeService(
                repository, accountService, mock(SubjectService.class));
        var verification = new RecoveryCodeVerification(
                new Subject(10L, "管理员"),
                new Account(
                        20L,
                        10L,
                        AccountScopeType.GLOBAL,
                        null,
                        true),
                101L,
                3L);

        assertThatThrownBy(() -> service.requireCurrent(verification))
                .isInstanceOf(UnauthenticatedException.class);
    }
}
