package com.sca4d.auth.federation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 验证 claims 的文本、布尔值和 subject 读取规则。 */
class FederatedClaimsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("文本 claim 去除首尾空白，缺失、空白和非标量返回 null")
    void readsTextOnlyFromNonBlankScalars() throws Exception {
        FederatedClaims claims = new FederatedClaims(
                MAPPER.readTree("{\"name\":\" Ada \",\"blank\":\"  \",\"empty\":null,\"nested\":{\"a\":1}}"));

        assertThat(claims.text("name")).isEqualTo("Ada");
        assertThat(claims.text("blank")).isNull();
        assertThat(claims.text("empty")).isNull();
        assertThat(claims.text("nested")).isNull();
        assertThat(claims.text("missing")).isNull();
    }

    @Test
    @DisplayName("布尔 claim 接受 JSON true 和文本 true，其他取值为 false")
    void readsFlagFromBooleanAndText() throws Exception {
        FederatedClaims claims = new FederatedClaims(
                MAPPER.readTree("{\"a\":true,\"b\":\"true\",\"c\":\"TRUE\",\"d\":\"false\",\"e\":1}"));

        assertThat(claims.flag("a")).isTrue();
        assertThat(claims.flag("b")).isTrue();
        assertThat(claims.flag("c")).isTrue();
        assertThat(claims.flag("d")).isFalse();
        assertThat(claims.flag("e")).isFalse();
        assertThat(claims.flag("missing")).isFalse();
    }

    @Test
    @DisplayName("缺少 sub 时抛出资料无效错误码")
    void rejectsMissingSubject() throws Exception {
        FederatedClaims claims = new FederatedClaims(MAPPER.readTree("{\"sub\":\" \"}"));

        assertThatThrownBy(claims::subject)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(FederatedErrorCode.PROFILE_INVALID));
    }
}
