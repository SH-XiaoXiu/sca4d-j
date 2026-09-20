package com.sca4d.auth.passkey.ceremony;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.auth.passkey.PasskeyCeremonyId;
import com.sca4d.auth.passkey.PasskeyErrorCode;
import com.sca4d.auth.passkey.config.PasskeySettings;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.runtime.RuntimeNamespace;
import com.sca4d.core.api.setting.Settings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/** 验证 Passkey ceremony 使用 Redis GETDEL 单次消费。 */
class PasskeyCeremonyStoreTest {

    @Test
    @DisplayName("assertion ceremony 消费一次后不再可用")
    void consumesAssertionCeremonyOnce() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        String key = "example-app:sca4dj:passkey:assertion:ceremony-1";
        when(values.getAndDelete(key))
                .thenReturn("{\"version\":1,\"request\":\"request-json\"}", (String) null);
        Settings settings = mock(Settings.class);
        when(settings.get(PasskeySettings.CEREMONY_TTL)).thenReturn(Duration.ofMinutes(5));
        PasskeyCeremonyStore store = new PasskeyCeremonyStore(
                redis,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC),
                settings,
                new RuntimeNamespace("example-app"));
        PasskeyCeremonyId id = new PasskeyCeremonyId("ceremony-1");

        assertThat(store.consumeAssertion(id)).isEqualTo("request-json");
        assertThatThrownBy(() -> store.consumeAssertion(id))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(PasskeyErrorCode.CEREMONY_UNAVAILABLE));
        verify(values, Mockito.times(2)).getAndDelete(key);
    }
}
