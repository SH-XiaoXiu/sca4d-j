package com.sca4d.common.i18n;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** 使用共用语言目录校验接口、字典和其他业务多语言键。 */
public class SupportedLanguageValidator implements ConstraintValidator<SupportedLanguage, String> {

    /**
     * 判断语言标签是否属于项目共用支持集合。
     *
     * <p>空值交给 {@code @NotNull} 或容器约束处理，本约束只负责非空语言标签的目录归属。
     *
     * @param value 待校验的 BCP 47 语言标签
     * @param context 当前 Jakarta Validation 上下文
     * @return 空值或受支持语言返回 {@code true}
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || SupportedLocale.supports(value);
    }
}
