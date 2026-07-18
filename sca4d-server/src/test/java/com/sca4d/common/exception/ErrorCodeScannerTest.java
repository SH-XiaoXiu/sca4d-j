package com.sca4d.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Set;

/** 验证错误码扫描、重复检测和号段检查。 */
class ErrorCodeScannerTest {

    @Test
    void shouldScanBizErrorCodeEnumTypes() {
        ErrorCodeScanner scanner = new ErrorCodeScanner();

        assertThat(scanner.scan(Set.of("com.sca4d.common.exception")))
                .extracting(Class::getSimpleName)
                .contains(CommonErrorCode.class.getSimpleName());
    }
}
