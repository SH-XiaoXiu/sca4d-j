package com.sca4d.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

/** 验证错误码目录按模块生成且包含全部业务错误码。 */
class ErrorCodeCatalogTest {

    @Test
    void shouldBuildCommonCatalog() {
        ErrorCodeCatalog catalog = ErrorCodeCatalog.common();

        assertThat(catalog.items()).hasSize(CommonErrorCode.values().length);
        assertThat(catalog.items()).extracting(ErrorCodeItem::getCode).contains(CommonErrorCode.SUCCESS.getCode());
        assertThat(catalog.ranges()).singleElement().satisfies(range -> {
            assertThat(range.getSource()).isEqualTo(CommonErrorCode.class.getSimpleName());
            assertThat(range.getStart()).isEqualTo(0);
            assertThat(range.getEnd()).isEqualTo(1999);
        });
    }

    @Test
    void shouldRegisterBusinessErrorCodeByEnumType() {
        ErrorCodeCatalog catalog = ErrorCodeCatalog.builder().register(TestErrorCode.class).build();

        assertThat(catalog.items()).singleElement().satisfies(item -> {
            assertThat(item.getCode()).isEqualTo(900001);
            assertThat(item.getCategory()).isEqualTo(ResponseCode.BAD_REQUEST);
            assertThat(item.getHttpStatus()).isEqualTo(ResponseCode.BAD_REQUEST.getHttpCode());
            assertThat(item.getSource()).isEqualTo(TestErrorCode.class.getSimpleName());
        });
        assertThat(catalog.ranges()).singleElement().satisfies(range -> {
            assertThat(range.getStart()).isEqualTo(900000);
            assertThat(range.getEnd()).isEqualTo(900999);
            assertThat(range.getSize()).isEqualTo(1);
        });
    }

    @Test
    void shouldRejectMissingCodeRange() {
        assertThatThrownBy(() -> ErrorCodeCatalog.builder().register(MissingRangeErrorCode.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@CodeRange");
    }

    @Test
    void shouldRejectCodeOutOfRange() {
        assertThatThrownBy(() -> ErrorCodeCatalog.builder().register(OutOfRangeErrorCode.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超出声明号段");
    }

    @Test
    void shouldRejectRangeConflict() {
        assertThatThrownBy(() -> ErrorCodeCatalog.builder().register(TestErrorCode.class).register(ConflictRangeErrorCode.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("号段冲突");
    }

    @Test
    void shouldRejectDuplicatedCode() {
        assertThatThrownBy(() -> ErrorCodeCatalog.builder().register(DuplicateCodeErrorCode.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复错误码");
    }

    /** 提供合法号段和错误码的目录注册样例。 */
    @CodeRange(start = 900000, end = 900999)
    private enum TestErrorCode implements BizErrorCode {
        /** 位于声明号段内的合法错误码。 */
        TEST_ERROR(900001);

        private final int code;

        TestErrorCode(int code) {
            this.code = code;
        }

        @Override
        public int getCode() {
            return code;
        }

        @Override
        public @NotNull String getMessageKey() {
            return "test.error.catalog";
        }

        @Override
        public @NotNull ResponseCode getCategory() {
            return ResponseCode.BAD_REQUEST;
        }
    }

    /** 缺少 CodeRange 注解的无效错误码类型。 */
    private enum MissingRangeErrorCode implements BizErrorCode {
        /** 用于触发缺少号段错误的枚举值。 */
        TEST_ERROR;

        @Override
        public int getCode() {
            return 910001;
        }

        @Override
        public @NotNull String getMessageKey() {
            return "test.error.missing-range";
        }

        @Override
        public @NotNull ResponseCode getCategory() {
            return ResponseCode.BAD_REQUEST;
        }
    }

    /** 返回超出自身 CodeRange 的错误码类型。 */
    @CodeRange(start = 920000, end = 920999)
    private enum OutOfRangeErrorCode implements BizErrorCode {
        /** 返回 921000 以触发超出号段检查。 */
        TEST_ERROR;

        @Override
        public int getCode() {
            return 921000;
        }

        @Override
        public @NotNull String getMessageKey() {
            return "test.error.out-of-range";
        }

        @Override
        public @NotNull ResponseCode getCategory() {
            return ResponseCode.BAD_REQUEST;
        }
    }

    /** 声明与 TestErrorCode 重叠号段的错误码类型。 */
    @CodeRange(start = 900500, end = 901000)
    private enum ConflictRangeErrorCode implements BizErrorCode {
        /** 位于重叠区间内的测试错误码。 */
        TEST_ERROR;

        @Override
        public int getCode() {
            return 900600;
        }

        @Override
        public @NotNull String getMessageKey() {
            return "test.error.conflicting-range";
        }

        @Override
        public @NotNull ResponseCode getCategory() {
            return ResponseCode.BAD_REQUEST;
        }
    }

    /** 两个枚举值返回同一数字以触发重复错误码检查。 */
    @CodeRange(start = 901000, end = 901999)
    private enum DuplicateCodeErrorCode implements BizErrorCode {
        /** 第一个返回 901001 的错误码。 */
        FIRST,
        /** 第二个返回 901001 的错误码。 */
        SECOND;

        @Override
        public int getCode() {
            return 901001;
        }

        @Override
        public @NotNull String getMessageKey() {
            return "test.error.duplicate-code";
        }

        @Override
        public @NotNull ResponseCode getCategory() {
            return ResponseCode.BAD_REQUEST;
        }
    }
}
