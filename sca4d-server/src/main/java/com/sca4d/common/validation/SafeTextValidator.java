package com.sca4d.common.validation;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 安全文本校验器。
 */
public class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    private int max;

    /**
     * 初始化校验规则。
     *
     * @param annotation 安全文本注解
     */
    @Override
    public void initialize(@NotNull SafeText annotation) {
        this.max = annotation.max();
    }

    /**
     * 校验安全文本。
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
        // 这里只拒绝控制字符和超长文本，不判断敏感词等业务内容。
        return value.length() <= max && value.matches(ValidationPatterns.SAFE_TEXT);
    }
}
