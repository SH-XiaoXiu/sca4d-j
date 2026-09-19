package com.sca4d.core.spring.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.Nullable;

/** 执行 MacAddress 的冒号分隔格式检查。 */
public final class MacAddressValidator implements ConstraintValidator<MacAddress, String> {

    /** 创建无配置参数的 MAC 地址校验器。 */
    public MacAddressValidator() {
    }

    /**
     * 检查非空白文本是否由冒号分隔的六组十六进制字节组成。
     *
     * @param value 待校验 MAC 地址
     * @param context Bean Validation 上下文
     * @return 空白值或格式正确时返回 {@code true}
     */
    @Override
    public boolean isValid(@Nullable String value, @Nullable ConstraintValidatorContext context) {
        return value == null || value.isBlank() || value.matches(ValidationPatterns.MAC_ADDRESS);
    }
}
