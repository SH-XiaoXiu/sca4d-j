package com.sca4d.app.server.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.auth.password.PasswordAuthenticationMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class AccountAuthenticationSettingsCodecTest {

    private final AccountAuthenticationSettingsCodec codec = new AccountAuthenticationSettingsCodec();

    @Test
    @DisplayName("账号认证设置可以通过 JSON 完整往返且不保存 Account 标识")
    void roundTripsWithoutAccountIdentifier() {
        AccountAuthenticationSettings original = new AccountAuthenticationSettings(
                2,
                1,
                Duration.ofSeconds(600),
                List.of(
                        new AuthenticationMethodSettings(
                                PasswordAuthenticationMethod.CODE,
                                true,
                                false,
                                true)));

        JsonNode json = codec.encodeJson(original);
        AccountAuthenticationSettings decoded = codec.decodeJson(json);

        assertThat(json.has("accountId")).isFalse();
        assertThat(decoded.loginFactorCount()).isEqualTo(2);
        assertThat(decoded.sensitiveActionFactorCount()).isOne();
        assertThat(decoded.reusableAuthenticationMaximumAge()).isEqualTo(Duration.ofSeconds(600));
        assertThat(decoded.methods()).containsOnlyKeys(PasswordAuthenticationMethod.CODE);
        assertThat(decoded.methods().get(PasswordAuthenticationMethod.CODE)).satisfies(method -> {
            assertThat(method.loginStartEnabled()).isTrue();
            assertThat(method.loginAdditionalEnabled()).isFalse();
            assertThat(method.sensitiveActionEnabled()).isTrue();
        });
    }

    @Test
    @DisplayName("账号认证设置拒绝未知字段和错误字段类型")
    void rejectsUnknownFieldsAndWrongTypes() {
        assertThatThrownBy(() -> codec.decode("""
                {
                  "loginFactorCount": 1,
                  "sensitiveActionFactorCount": 1,
                  "reusableAuthenticationMaximumAgeSeconds": 300,
                  "methods": [{
                    "methodCode": "AUTHENTICATION/PASSWORD",
                    "loginStartEnabled": true,
                    "loginAdditionalEnabled": true,
                    "sensitiveActionEnabled": true
                  }],
                  "accountId": 7
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知字段");
        assertThatThrownBy(() -> codec.decode("""
                {
                  "loginFactorCount": "1",
                  "sensitiveActionFactorCount": 1,
                  "reusableAuthenticationMaximumAgeSeconds": 300,
                  "methods": [{
                    "methodCode": "AUTHENTICATION/PASSWORD",
                    "loginStartEnabled": true,
                    "loginAdditionalEnabled": true,
                    "sensitiveActionEnabled": true
                  }]
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loginFactorCount");
    }

    @Test
    @DisplayName("账号认证设置拒绝重复方法")
    void rejectsDuplicateMethods() {
        assertThatThrownBy(() -> codec.decode("""
                {
                  "loginFactorCount": 1,
                  "sensitiveActionFactorCount": 1,
                  "reusableAuthenticationMaximumAgeSeconds": 300,
                  "methods": [
                    {
                      "methodCode": "AUTHENTICATION/PASSWORD",
                      "loginStartEnabled": true,
                      "loginAdditionalEnabled": true,
                      "sensitiveActionEnabled": true
                    },
                    {
                      "methodCode": "AUTHENTICATION/PASSWORD",
                      "loginStartEnabled": false,
                      "loginAdditionalEnabled": true,
                      "sensitiveActionEnabled": true
                    }
                  ]
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }
}
