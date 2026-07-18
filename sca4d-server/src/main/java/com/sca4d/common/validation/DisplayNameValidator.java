package com.sca4d.common.validation;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 展示名称校验器。
 */
public class DisplayNameValidator implements ConstraintValidator<DisplayName, String> {

    private int max;

    /**
     * 初始化校验规则。
     *
     * @param annotation 展示名称注解
     */
    @Override
    public void initialize(@NotNull DisplayName annotation) {
        this.max = annotation.max();
    }

    /**
     * 校验展示名称。
     *
     * @param value 待校验值
     * @param context 校验上下文
     * @return 格式合法返回 true，否则返回 false
     */
    @Override
    public boolean isValid(@Nullable String value, @Nullable ConstraintValidatorContext context) {
        if (StrUtil.isBlank(value)) {
            return true;
        }
        // 展示名称允许普通空格，但不允许控制字符。
        return value.length() <= max && value.matches(ValidationPatterns.DISPLAY_NAME);
    }
}
