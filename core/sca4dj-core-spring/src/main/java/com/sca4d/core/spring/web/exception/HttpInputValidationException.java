package com.sca4d.core.spring.web.exception;

import com.sca4d.core.spring.web.FieldViolation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/** 保存 Controller 执行前发现的 HTTP 默认校验失败结果。 */
public class HttpInputValidationException extends RuntimeException {

    private final @NotNull List<@NotNull FieldViolation> violations;

    /**
     * 创建包含全部默认校验失败字段的异常。
     *
     * @param violations 非空的字段失败结果
     * @throws IllegalArgumentException violations 为空时抛出
     */
    public HttpInputValidationException(@NotNull List<@NotNull FieldViolation> violations) {
        super("HTTP 输入不满足默认校验规则");
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("HTTP 输入校验失败结果不能为空");
        }
        this.violations = List.copyOf(violations);
    }

    /**
     * 客户端能够定位的全部字段失败结果。
     *
     * @return 不可修改的字段失败结果
     */
    public @NotNull @Unmodifiable List<@NotNull FieldViolation> violations() {
        return violations;
    }
}
