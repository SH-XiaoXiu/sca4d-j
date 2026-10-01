package com.sca4d.auth.sms.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.auth.sms.SmsCodeAuthenticationMethod;
import com.sca4d.auth.sms.SmsCodeChallengeId;
import com.sca4d.auth.sms.SmsCodeNotice;
import com.sca4d.auth.sms.SmsCodeProof;
import com.sca4d.auth.sms.config.SmsCodeSettings;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.AccountDirectory;
import com.sca4d.core.api.identity.RealmKey;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.notification.Notifications;
import com.sca4d.core.api.security.SecretHasher;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/** 验证 SMS Code Challenge 的账号解析、通知入队和一次性消费边界。 */
class SmsCodeChallengeServiceTest {

    @Test
    @DisplayName("已知 PHONE Account 签发 Challenge 并只把验证码放入类型化通知")
    void issuesTypedNotificationForKnownAccount() {
        RealmKey realm = new RealmKey("platform");
        Account account = new Account(42, 52, realm);
        AccountDirectory accounts = mock(AccountDirectory.class);
        when(accounts.resolve(realm, SmsCodeAuthenticationMethod.PHONE_IDENTIFIER_TYPE, "+8613800138000"))
                .thenReturn(Optional.of(account));
        Notifications notifications = mock(Notifications.class);
        when(notifications.isAvailable(any(), any())).thenReturn(true);
        when(notifications.enqueue(any(), any())).thenReturn(Id.of(1));
        RedisSmsCodeChallengeStore store = mock(RedisSmsCodeChallengeStore.class);
        when(store.reserveCooldown(anyString(), any())).thenReturn(true);
        SecretHasher hasher = mock(SecretHasher.class);
        when(hasher.hash(anyString())).thenReturn("hash");
        TestAccess source = mock(TestAccess.class);
        SmsCodeSettings settings = new SmsCodeSettings(source, source);
        when(source.read(settings.CHALLENGE_TTL.code(), SettingOwner.system())).thenReturn(
                SettingValue.read(
                        TreeCode.of("SETTING:TEST"),
                        settings.CHALLENGE_TTL.code(),
                        SettingOwner.system(),
                        settings.CHALLENGE_TTL.codec().encode(Duration.ofMinutes(5)),
                        1,
                        new Version(0),
                        false));
        when(source.read(settings.RESEND_INTERVAL.code(), SettingOwner.system())).thenReturn(
                SettingValue.read(
                        TreeCode.of("SETTING:TEST"),
                        settings.RESEND_INTERVAL.code(),
                        SettingOwner.system(),
                        settings.RESEND_INTERVAL.codec().encode(Duration.ofMinutes(1)),
                        1,
                        new Version(0),
                        false));
        SecureRandom random = mock(SecureRandom.class);
        when(random.nextInt(1_000_000)).thenReturn(123456);
        SmsCodeChallengeService service = new SmsCodeChallengeService(
                accounts,
                realm,
                notifications,
                store,
                hasher,
                settings,
                Clock.fixed(Instant.parse("2026-09-17T06:00:00Z"), ZoneOffset.UTC),
                random);

        var challenge = service.issue("+8613800138000");

        assertThat(challenge.maskedRecipient()).isEqualTo("+861***8000");
        ArgumentCaptor<SmsCodeNotice> notice = ArgumentCaptor.forClass(SmsCodeNotice.class);
        verify(notifications).enqueue(eq(SmsCodeNotice.TYPE), notice.capture());
        assertThat(notice.getValue().accountId()).isEqualTo(account.accountId());
        assertThat(notice.getValue().code()).isEqualTo("123456");
        assertThat(notice.getValue().expiresInMinutes()).isEqualTo(5);
        verify(store).create(any(SmsCodeChallengeState.class));
    }

    @Test
    @DisplayName("验证码正确时原子消费并返回当前启用 Account")
    void consumesValidChallengeOnce() {
        RealmKey realm = new RealmKey("platform");
        Account account = new Account(42, 52, realm);
        var challengeId = new SmsCodeChallengeId("00000000-0000-0000-0000-000000000001");
        var state = new SmsCodeChallengeState(
                challengeId,
                account.accountId(),
                "hash",
                "+861***8000",
                Instant.parse("2026-09-17T06:05:00Z"),
                Instant.parse("2026-09-17T06:01:00Z"),
                0,
                SmsCodeChallengeStatus.PENDING,
                new Version(0));
        AccountDirectory accounts = mock(AccountDirectory.class);
        when(accounts.activeById(account.accountId())).thenReturn(account);
        RedisSmsCodeChallengeStore store = mock(RedisSmsCodeChallengeStore.class);
        when(store.find(challengeId)).thenReturn(state);
        when(store.verify(state, true, 5)).thenReturn(new Version(1));
        SecretHasher hasher = mock(SecretHasher.class);
        when(hasher.hash(anyString())).thenReturn("dummy");
        when(hasher.matches("123456", "hash")).thenReturn(true);
        TestAccess source = mock(TestAccess.class);
        SmsCodeSettings settings = new SmsCodeSettings(source, source);
        when(source.read(settings.MAXIMUM_ATTEMPTS.code(), SettingOwner.system())).thenReturn(
                SettingValue.read(
                        TreeCode.of("SETTING:TEST"),
                        settings.MAXIMUM_ATTEMPTS.code(),
                        SettingOwner.system(),
                        settings.MAXIMUM_ATTEMPTS.codec().encode(5),
                        1,
                        new Version(0),
                        false));
        SmsCodeChallengeService service = new SmsCodeChallengeService(
                accounts,
                realm,
                mock(Notifications.class),
                store,
                hasher,
                settings,
                Clock.fixed(Instant.parse("2026-09-17T06:02:00Z"), ZoneOffset.UTC),
                mock(SecureRandom.class));

        ConsumedSmsCodeChallenge consumed = service.consume(new SmsCodeProof(challengeId, "123456"));

        assertThat(consumed.account()).isEqualTo(account);
        assertThat(consumed.version()).isEqualTo(new Version(1));
    }

    /** 测试替身同时实现读取和写入契约，避免引入生产组合接口。 */
    private interface TestAccess extends SettingReader<String>, SettingWriter<String> {
    }
}
