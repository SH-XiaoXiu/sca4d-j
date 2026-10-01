package com.sca4d.auth.totp.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.isolation.AppName;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.security.EncryptedSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/** 验证 Redis enrollment 的 TTL 创建和独立 Lua CAS 状态迁移。 */
class RedisTotpEnrollmentStoreTest {

    static {
        AppName.publish("SCA4D_TEST");
    }

    private static final Instant NOW = Instant.parse("2026-07-26T06:00:00Z");

    @Test
    @DisplayName("按 TTL 创建 enrollment，并通过 classpath Lua 脚本消费待确认版本")
    @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
    void createsWithTtlAndConsumesPendingVersionThroughClasspathScript() {
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<Key, String> values = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<Key, String> sets = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForSet()).thenReturn(sets);
        when(values.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        when(
                redis.execute(
                        ArgumentMatchers.<RedisScript<Long>>any(),
                        anyList(),
                        eq("0"),
                        eq(TotpEnrollmentStatus.PENDING.name()),
                        any()))
                .thenReturn(1L);
        RedisTotpEnrollmentStore store = new RedisTotpEnrollmentStore(
                redis,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        TotpEnrollmentState state = new TotpEnrollmentState(
                "enrollment-1",
                10L,
                new EncryptedSecret("current", new byte[] {1, 2, 3}),
                NOW.plusSeconds(600),
                TotpEnrollmentStatus.PENDING,
                0);

        store.create(state);
        store.consume(state);

        verify(values).setIfAbsent(
                eq(Key.of(TreeCode.ofColon("AUTH:TOTP_ENROLLMENT")).part("enrollment-1")),
                any(),
                eq(Duration.ofSeconds(600)));
        verify(sets).add(
                Key.of(TreeCode.ofColon("AUTH:TOTP_ENROLLMENTS:ACCOUNT")).part(10L),
                "SCA4D_TEST:AUTH:TOTP_ENROLLMENT:V_656E726F6C6C6D656E742D31");
        verify(redis).expire(
                Key.of(TreeCode.ofColon("AUTH:TOTP_ENROLLMENTS:ACCOUNT")).part(10L),
                Duration.ofSeconds(600));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> consumedJson = ArgumentCaptor.forClass(String.class);
        verify(redis).execute(
                script.capture(),
                eq(List.of(Key.of(TreeCode.ofColon("AUTH:TOTP_ENROLLMENT")).part("enrollment-1"))),
                eq("0"),
                eq("PENDING"),
                consumedJson.capture());
        assertThat(script.getValue().getSha1()).isNotBlank();
        assertThat(script.getValue().getScriptAsString())
                .contains("stateVersion", "KEEPTTL")
                .doesNotContain("redis.call('del'");
        assertThat(consumedJson.getValue()).contains("\"state\":\"CONSUMED\"", "\"stateVersion\":1");
    }

    @Test
    @DisplayName("序列化后的 enrollment 不包含明文秘密")
    void encodedEnrollmentNeverContainsPlaintextSecret() {
        TotpEnrollmentCodec codec = new TotpEnrollmentCodec(new ObjectMapper());
        String plaintext = "JBSWY3DPEHPK3PXP";
        TotpEnrollmentState state = new TotpEnrollmentState(
                "enrollment-1",
                10L,
                new EncryptedSecret("current", plaintext.getBytes(StandardCharsets.UTF_8)),
                NOW.plusSeconds(600),
                TotpEnrollmentStatus.PENDING,
                0);

        assertThat(codec.encode(state)).doesNotContain(plaintext);
    }
}
