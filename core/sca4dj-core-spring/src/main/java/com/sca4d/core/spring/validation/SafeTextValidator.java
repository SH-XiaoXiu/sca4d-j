package com.sca4d.core.spring.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 执行 SafeText 声明的长度和控制字符检查。 */
public final class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    private int max;

    /** 创建等待 Bean Validation Provider 初始化最大长度的校验器。 */
    public SafeTextValidator() {
    }

    /**
     * 读取普通文本允许的最大字符数。
     *
     * @param annotation 普通文本约束
     * @throws IllegalArgumentException 最大字符数为负数时抛出
     */
    @Override
    public void initialize(@NotNull SafeText annotation) {
        if (annotation.max() < 0) {
            throw new IllegalArgumentException("@SafeText max 不能小于 0");
        }
        max = annotation.max();
    }

    /**
     * 检查非空白文本的字符数和控制字符。
     *
     * @param value 待校验普通文本
     * @param context Bean Validation 上下文
     * @return 空白值或文本符合约束时返回 {@code true}
     */
    @Override
    public boolean isValid(@Nullable String value, @Nullable ConstraintValidatorContext context) {
        return value == null
                || value.isBlank()
                || value.length() <= max && value.matches(ValidationPatterns.SAFE_TEXT);
    }
}
