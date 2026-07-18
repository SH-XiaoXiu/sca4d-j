package com.sca4d.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.common.concurrent.ConcurrencyConflictException;
import com.sca4d.common.exception.BizException;
import com.sca4d.entity.IamPasskeyUserHandleEntity;
import com.sca4d.iam.credential.CredentialStatus;
import com.sca4d.iam.error.IamErrorCode;
import com.sca4d.iam.passkey.PasskeySecurityDetails;
import com.sca4d.iam.passkey.repository.PasskeyRepository;
import com.sca4d.iam.passkey.repository.PasskeySecurityRow;
import com.sca4d.mapper.PasskeyMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 验证 Passkey Repository 隐藏影响行数，并由原子 SQL 保护认证器状态。 */
class PasskeyRepositoryTest {

    @Test
    void shouldPersistUserHandleBeforeCredentialInSameRepositoryOperation() {
        PasskeyMapper mapper = mock(PasskeyMapper.class);
        byte[] userHandle = new byte[] {1};
        when(mapper.selectUserHandle(30L)).thenReturn(new IamPasskeyUserHandleEntity()
                .setAccountId(30L)
                .setUserHandle(userHandle));
        PasskeyRepository repository = new PasskeyRepository(mapper);

        repository.save(
                40L,
                30L,
                userHandle,
                new byte[] {2},
                new byte[] {3},
                0,
                new byte[16],
                false,
                false,
                "工作电脑");

        var order = inOrder(mapper);
        order.verify(mapper).insertUserHandle(any(IamPasskeyUserHandleEntity.class));
        order.verify(mapper).selectUserHandle(30L);
        order.verify(mapper).insertCredential(any());
        order.verify(mapper).insertPasskey(any());
    }

    @Test
    void shouldRejectCredentialFromConcurrentRegistrationWithDifferentUserHandle() {
        PasskeyMapper mapper = mock(PasskeyMapper.class);
        when(mapper.selectUserHandle(30L)).thenReturn(new IamPasskeyUserHandleEntity()
                .setAccountId(30L)
                .setUserHandle(new byte[] {2}));
        PasskeyRepository repository = new PasskeyRepository(mapper);

        assertThatThrownBy(() -> repository.save(
                40L,
                30L,
                new byte[] {1},
                new byte[] {2},
                new byte[] {3},
                0,
                new byte[16],
                false,
                false,
                "工作电脑"))
                .isInstanceOfSatisfying(
                        BizException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.PASSKEY_USER_HANDLE_CONFLICT));
        verify(mapper, never()).insertCredential(any());
        verify(mapper, never()).insertPasskey(any());
    }

    @Test
    void shouldReturnNewVersionOnlyForSuccessfulAtomicAssertion() {
        PasskeyMapper mapper = mock(PasskeyMapper.class);
        when(mapper.advanceAssertion(40L, 30L, 2L, 3L, true, false, 5L))
                .thenReturn(1);
        PasskeyRepository repository = new PasskeyRepository(mapper);

        assertThat(repository.advanceAssertion(40L, 30L, 2L, 3L, true, false, 5L))
                .isEqualTo(6L);
        assertThat(repository.advanceAssertion(41L, 30L, 2L, 3L, true, false, 5L))
                .isNull();
    }

    @Test
    void shouldConvertMissingDeleteToPasskeyPreconditionFailure() {
        PasskeyRepository repository = new PasskeyRepository(mock(PasskeyMapper.class));

        assertThatThrownBy(() -> repository.delete(40L, 30L, 3L))
                .isInstanceOfSatisfying(
                        ConcurrencyConflictException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(IamErrorCode.PASSKEY_CREDENTIAL_CHANGED));
    }

    @Test
    void shouldCheckAvailabilityWithoutReadingPublicKey() {
        PasskeyMapper mapper = mock(PasskeyMapper.class);
        when(mapper.countActivePasskeys(30L, 40L)).thenReturn(1);

        assertThat(new PasskeyRepository(mapper).hasActiveCredential(30L, 40L)).isTrue();

        verify(mapper).countActivePasskeys(30L, 40L);
    }

    @Test
    void shouldBatchAccountSecurityDetailsWithoutProtocolSecrets() {
        PasskeyMapper mapper = mock(PasskeyMapper.class);
        OffsetDateTime registeredAt = OffsetDateTime.parse("2026-07-17T08:00:00+08:00");
        OffsetDateTime lastUsedAt = OffsetDateTime.parse("2026-07-17T09:00:00+08:00");
        when(mapper.selectSecurityByAccounts(List.of(30L, 31L)))
                .thenReturn(List.of(new PasskeySecurityRow()
                        .setCredentialId(40L)
                        .setAccountId(31L)
                        .setCredentialStatus(CredentialStatus.ACTIVE)
                        .setCredentialCreatedAt(registeredAt)
                        .setCredentialUpdatedAt(lastUsedAt)
                        .setCredentialVersion(2L)
                        .setPasskeyDisplayName("工作电脑")
                        .setBackupEligible(true)
                        .setBackupState(true)
                        .setLastUsedAt(lastUsedAt)));

        Map<Long, List<PasskeySecurityDetails>> result = new PasskeyRepository(mapper).findSecurityByAccounts(List.of(30L, 31L));

        assertThat(result).containsOnlyKeys(31L);
        assertThat(result.get(31L).getFirst().getDisplayName()).isEqualTo("工作电脑");
        assertThat(result.get(31L).getFirst().isBackupState()).isTrue();
        assertThat(result.get(31L).getFirst().getLastUsedAt()).isEqualTo(lastUsedAt);
        verify(mapper).selectSecurityByAccounts(List.of(30L, 31L));
    }

    @Test
    void shouldSkipBatchQueryForEmptyAccountCollection() {
        PasskeyMapper mapper = mock(PasskeyMapper.class);

        assertThat(new PasskeyRepository(mapper).findSecurityByAccounts(List.of())).isEmpty();

        verifyNoInteractions(mapper);
    }

}
