package com.sca4d.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 验证响应分类与 HTTP 状态码、固定值之间的映射。 */
class ResponseCodeTest {

    @Test
    void responseCodeShouldResolveByCodeAndHttpStatus() {
        assertThat(ResponseCode.fromCode(ResponseCode.BAD_REQUEST.getCode())).isEqualTo(ResponseCode.BAD_REQUEST);
        assertThat(ResponseCode.fromCode(-1)).isEqualTo(ResponseCode.SERVER_ERROR);
    }

    @Test
    void responseCodeShouldExposeStatusFlags() {
        assertThat(ResponseCode.SUCCESS.isSuccess()).isTrue();
        assertThat(ResponseCode.BAD_REQUEST.isError()).isTrue();
        assertThat(ResponseCode.SERVER_ERROR.isServerError()).isTrue();
        assertThat(ResponseCode.FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
