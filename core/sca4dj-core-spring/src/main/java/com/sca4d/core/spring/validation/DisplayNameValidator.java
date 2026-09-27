package com.sca4d.core.spring.validation;

import static com.sca4d.core.spring.validation.ValidationPatterns.DISPLAY_NAME;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 执行 DisplayName 声明的长度和控制字符检查。 */
public class DisplayNameValidator implements ConstraintValidator<DisplayName, String> {

    private int max;

    /** 创建等待 Bean Validation Provider 初始化最大长度的校验器。 */
    public DisplayNameValidator() {
    }

    /**
     * 读取展示名称允许的最大字符数。
     *
     * @param annotation 展示名称约束
     * @throws IllegalArgumentException 最大字符数小于一时抛出
     */
    @Override
    public void initialize(@NotNull DisplayName annotation) {
        if (annotation.max() < 1) {
            throw new IllegalArgumentException("@DisplayName max 必须大于 0");
        }
        max = annotation.max();
    }

    /**
     * 检查非空白名称的字符数和控制字符。
     *
     * @param value 待校验展示名称
     * @param context Bean Validation 上下文
     * @return 空白值或名称符合约束时返回 {@code true}
     */
    @Override
    public boolean isValid(@Nullable String value, @Nullable ConstraintValidatorContext context) {
        return value == null
                || value.isBlank()
                || value.length() <= max && value.matches(DISPLAY_NAME);
    }
}
