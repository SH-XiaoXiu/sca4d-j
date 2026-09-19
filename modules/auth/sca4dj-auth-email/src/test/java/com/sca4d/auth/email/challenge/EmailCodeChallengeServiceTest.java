package com.sca4d.auth.email.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.email.EmailCodeAuthenticationMethod;
import com.sca4d.auth.email.EmailCodeChallenge;
import com.sca4d.auth.email.EmailCodeChallengeId;
import com.sca4d.auth.email.EmailCodeErrorCode;
import com.sca4d.auth.email.config.EmailCodeSettings;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.messaging.MessageId;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.security.SecretHasher;
import com.sca4d.core.api.setting.Settings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/** 验证 Email Code Challenge 对账号冷却和匿名结果使用稳定边界。 */
class EmailCodeChallengeServiceTest {

    @Test
    void sharesCooldownAcrossEmailVariantsResolvedToSameAccount() {
        RealmKey realm = new RealmKey("platform");
        Account account = new Account(42, 52, realm);
        AccountDirectory accounts = mock(AccountDirectory.class);
        when(
                accounts.resolve(
                        eq(realm),
                        eq(EmailCodeAuthenticationMethod.EMAIL_IDENTIFIER_TYPE),
                        anyString()))
                .thenReturn(Optional.of(account));
        Notifications notifications = mock(Notifications.class);
        when(notifications.isAvailable(any(), any())).thenReturn(true);
        when(notifications.enqueue(any(), any())).thenReturn(new MessageId(1L));
        RedisEmailCodeChallengeStore store = mock(RedisEmailCodeChallengeStore.class);
        when(store.reserveCooldown(anyString(), any())).thenReturn(true, false);
        SecretHasher hasher = mock(SecretHasher.class);
        when(hasher.hash(anyString())).thenReturn("secret-hash");
        Settings settings = mock(Settings.class);
        when(settings.get(EmailCodeSettings.CHALLENGE_TTL)).thenReturn(Duration.ofMinutes(5));
        when(settings.get(EmailCodeSettings.RESEND_INTERVAL)).thenReturn(Duration.ofMinutes(1));
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(123456);
        EmailCodeChallengeService service = new EmailCodeChallengeService(
                accounts,
                realm,
                notifications,
                store,
                hasher,
                settings,
                Clock.systemUTC(),
                random);

        EmailCodeChallenge challenge = service.issue("User@example.com");

        assertThat(challenge).isNotNull();
        assertThatThrownBy(() -> service.issue(" user@example.com "))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(EmailCodeErrorCode.RESEND_TOO_SOON));
        ArgumentCaptor<String> cooldownTargets = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).reserveCooldown(
                cooldownTargets.capture(),
                eq(Duration.ofMinutes(1)));
        assertThat(cooldownTargets.getAllValues()).containsOnly(cooldownTargets.getAllValues().getFirst());
    }

    @Test
    void rejectsConsumedChallengeAfterAccountBecomesUnavailable() {
        long accountId = 42L;
        EmailCodeChallengeId challengeId = new EmailCodeChallengeId("00000000-0000-0000-0000-000000000001");
        Version version = new Version(1);
        AccountDirectory accounts = mock(AccountDirectory.class);
        when(accounts.activeById(accountId))
                .thenThrow(new BusinessException(EmailCodeErrorCode.CHALLENGE_UNAVAILABLE));
        RedisEmailCodeChallengeStore store = mock(RedisEmailCodeChallengeStore.class);
        when(store.find(challengeId))
                .thenReturn(
                        new EmailCodeChallengeState(
                                challengeId,
                                accountId,
                                "secret-hash",
                                "u***@e***.com",
                                Instant.parse("2026-08-11T01:05:00Z"),
                                Instant.parse("2026-08-11T01:01:00Z"),
                                0,
                                EmailCodeChallengeStatus.CONSUMED,
                                version));
        SecretHasher hasher = mock(SecretHasher.class);
        when(hasher.hash(anyString())).thenReturn("dummy-hash");
        EmailCodeChallengeService service = new EmailCodeChallengeService(
                accounts,
                new RealmKey("platform"),
                mock(Notifications.class),
                store,
                hasher,
                mock(Settings.class),
                Clock.fixed(Instant.parse("2026-08-11T01:02:00Z"), ZoneOffset.UTC),
                mock(SecureRandom.class));

        assertThatThrownBy(() -> service.verifyConsumedChallenge(challengeId, accountId, version))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Email Code Challenge 已变化或不可用")
                .hasCauseInstanceOf(BusinessException.class);
        verify(accounts).activeById(accountId);
    }
}
