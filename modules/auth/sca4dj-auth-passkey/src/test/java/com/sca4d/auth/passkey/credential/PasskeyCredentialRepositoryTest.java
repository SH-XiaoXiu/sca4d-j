package com.sca4d.auth.passkey.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.passkey.credential.mapper.PasskeyAccountState;
import com.sca4d.auth.passkey.credential.mapper.PasskeyCredentialMapper;
import com.sca4d.core.api.concurrent.Version;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

/** 验证 Passkey Credential 数量限制和签名计数器更新的持久化边界。 */
class PasskeyCredentialRepositoryTest {

    @Test
    @DisplayName("并发首次写入后返回数据库保存的稳定 user handle")
    void returnsStoredUserHandleAfterInsertIfAbsent() {
        long accountId = 20;
        byte[] generated = new byte[32];
        java.util.Arrays.fill(generated, (byte) 7);
        PasskeyCredentialMapper mapper = mock(PasskeyCredentialMapper.class);
        when(mapper.selectAccount(accountId, null))
                .thenReturn(new PasskeyAccountState(accountId, generated, new Version(0)));
        PasskeyCredentialRepository repository = new PasskeyCredentialRepository(mapper);

        byte[] userHandle = repository.createUserHandleIfAbsent(accountId, generated);

        assertThat(userHandle).containsExactly(generated).isNotSameAs(generated);
        verify(mapper).insertAccountIfAbsent(accountId, generated);
    }

    @Test
    @DisplayName("乐观抢占失败且数量达到上限时返回上限结果")
    void reportsCredentialLimitAfterFailedReservation() {
        PasskeyCredentialMapper mapper = mock(PasskeyCredentialMapper.class);
        PasskeyCredential credential = credential(0, 0, new Version(0));
        when(mapper.selectAccount(credential.accountId(), null))
                .thenReturn(
                        new PasskeyAccountState(
                                credential.accountId(),
                                credential.userHandle(),
                                new Version(4)));
        when(mapper.updateAccountVersionIfBelowCredentialLimit(credential.accountId(), 4L, 5)).thenReturn(0);
        when(mapper.countByAccountId(credential.accountId())).thenReturn(5);
        PasskeyCredentialRepository repository = new PasskeyCredentialRepository(mapper);

        PasskeyCredentialRepository.CredentialSlotReservation result = repository
                .reserveCredentialSlot(credential.accountId(), 5);

        assertThat(result).isEqualTo(PasskeyCredentialRepository.CredentialSlotReservation.LIMIT_REACHED);
    }

    @Test
    @DisplayName("允许零对零签名计数器并按版本原子更新")
    void acceptsZeroToZeroSignatureCounter() {
        PasskeyCredentialMapper mapper = mock(PasskeyCredentialMapper.class);
        PasskeyCredential stored = credential(1, 0, new Version(4));
        Instant usedAt = Instant.parse("2026-07-31T00:00:00Z");
        when(
                mapper.updateSignatureCountIfVersionAndCountMatch(
                        stored.id(),
                        stored.version(),
                        stored.signatureCount(),
                        0,
                        true,
                        usedAt))
                .thenReturn(1);
        when(mapper.selectCredentials(null, stored.credentialId(), null))
                .thenReturn(
                        java.util.List.of(
                                new PasskeyCredential(
                                        1,
                                        20,
                                        new byte[] {1},
                                        new byte[] {2},
                                        new byte[] {3},
                                        0,
                                        Set.of("internal"),
                                        true,
                                        true,
                                        "Laptop",
                                        Instant.parse("2026-07-30T00:00:00Z"),
                                        usedAt,
                                        new Version(5))));
        PasskeyCredentialRepository repository = new PasskeyCredentialRepository(mapper);

        PasskeyCredential updated = repository.advanceSignatureCount(stored, 0, true, usedAt);

        assertThat(updated).isNotNull();
        assertThat(updated.signatureCount()).isZero();
        assertThat(updated.version()).isEqualTo(new Version(5));
        verify(mapper).updateSignatureCountIfVersionAndCountMatch(
                stored.id(),
                stored.version(),
                stored.signatureCount(),
                0,
                true,
                usedAt);
    }

    @Test
    @DisplayName("数据库拒绝未递增计数器时返回空更新结果")
    void reportsRejectedSignatureCounterUpdate() {
        PasskeyCredentialMapper mapper = mock(PasskeyCredentialMapper.class);
        PasskeyCredential stored = credential(1, 8, new Version(4));
        Instant usedAt = Instant.parse("2026-07-31T00:00:00Z");
        when(
                mapper.updateSignatureCountIfVersionAndCountMatch(
                        stored.id(),
                        stored.version(),
                        stored.signatureCount(),
                        8,
                        false,
                        usedAt))
                .thenReturn(0);
        PasskeyCredentialRepository repository = new PasskeyCredentialRepository(mapper);

        assertThat(repository.advanceSignatureCount(stored, 8, false, usedAt)).isNull();
    }

    @Test
    @DisplayName("删除 Credential 时通过账号锚点版本原子保证最小保留数量")
    void deletesWithAccountVersionAndMinimumRemainingCount() {
        PasskeyCredentialMapper mapper = mock(PasskeyCredentialMapper.class);
        PasskeyCredential stored = credential(7, 0, new Version(4));
        when(mapper.selectAccount(stored.accountId(), null))
                .thenReturn(
                        new PasskeyAccountState(
                                stored.accountId(),
                                stored.userHandle(),
                                new Version(9)));
        when(
                mapper.updateDeletedIfAccountAndCredentialVersionMatch(
                        stored.accountId(),
                        stored.id(),
                        stored.version(),
                        9L,
                        1))
                .thenReturn(1);
        PasskeyCredentialRepository repository = new PasskeyCredentialRepository(mapper);

        repository.delete(stored.accountId(), stored.id(), stored.version(), 1);

        verify(mapper).updateDeletedIfAccountAndCredentialVersionMatch(
                stored.accountId(),
                stored.id(),
                stored.version(),
                9L,
                1);
    }

    private static PasskeyCredential credential(long id, long signatureCount, Version version) {
        return new PasskeyCredential(
                id,
                20,
                new byte[] {1},
                new byte[] {2},
                new byte[] {3},
                signatureCount,
                Set.of("internal"),
                true,
                false,
                "Laptop",
                Instant.parse("2026-07-30T00:00:00Z"),
                null,
                version);
    }

}
