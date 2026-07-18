package com.sca4d.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证 BizException 保存错误码、消息参数和安全摘要。 */
class BizExceptionTest {

    @Test
    void bizExceptionShouldKeepOriginalErrorCode() {
        BizException exception = new BizException(CommonErrorCode.BAD_REQUEST, "name");

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST);
        assertThat(exception.getCode()).isEqualTo(CommonErrorCode.BAD_REQUEST.getCode());
        assertThat(exception.getMessageKey()).isEqualTo(CommonErrorCode.BAD_REQUEST.getMessageKey());
        assertThat(exception.getMessageArgs()).containsExactly("name");
        assertThat(exception.getData()).isNull();
        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.BAD_REQUEST);
    }
}
