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
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.setting.access.SettingReader;
import com.sca4d.core.api.setting.access.SettingWriter;
import com.sca4d.core.api.setting.value.SettingOwner;
import com.sca4d.core.api.setting.value.SettingValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/** 验证 Passkey ceremony 使用 Redis GETDEL 单次消费。 */
class PasskeyCeremonyStoreTest {

    @Test
    @DisplayName("assertion ceremony 消费一次后不再可用")
    @SuppressWarnings("unchecked") // Mockito 的 Class 参数不能表达 RedisTemplate 泛型。
    void consumesAssertionCeremonyOnce() {
        RedisTemplate<Key, String> redis = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<Key, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Key key = Key.of(TreeCode.ofColon("PASSKEY:ASSERTION")).part("ceremony-1");
        when(values.getAndDelete(key))
                .thenReturn("{\"version\":1,\"request\":\"request-json\"}", (String) null);
        TestAccess source = mock(TestAccess.class);
        PasskeySettings settings = new PasskeySettings(source, source);
        when(source.read(settings.CEREMONY_TTL.code(), SettingOwner.system())).thenReturn(
                SettingValue.read(
                        TreeCode.of("SETTING:TEST"),
                        settings.CEREMONY_TTL.code(),
                        SettingOwner.system(),
                        settings.CEREMONY_TTL.codec().encode(Duration.ofMinutes(5)),
                        1,
                        new Version(0),
                        false));
        PasskeyCeremonyStore store = new PasskeyCeremonyStore(
                redis,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-31T00:00:00Z"), ZoneOffset.UTC),
                settings);
        PasskeyCeremonyId id = new PasskeyCeremonyId("ceremony-1");

        assertThat(store.consumeAssertion(id)).isEqualTo("request-json");
        assertThatThrownBy(() -> store.consumeAssertion(id))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(PasskeyErrorCode.CEREMONY_UNAVAILABLE));
        verify(values, Mockito.times(2)).getAndDelete(key);
    }

    /** 测试替身同时实现读取和写入契约，避免引入生产组合接口。 */
    private interface TestAccess extends SettingReader<String>, SettingWriter<String> {
    }
}
