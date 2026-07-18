package com.sca4d.common.validation;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.Nullable;

/**
 * MAC 地址校验器。
 */
public class MacAddressValidator implements ConstraintValidator<MacAddress, String> {

    /**
     * 校验 MAC 地址格式。
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
        return value.matches(ValidationPatterns.MAC_ADDRESS);
    }
}
