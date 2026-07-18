package com.sca4d.common.i18n;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验语言标签必须来自项目共用 {@link SupportedLocale} 目录。 */
@Documented
@Constraint(validatedBy = SupportedLanguageValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedLanguage {

    /** @return Jakarta Validation 消息键 */
    String message() default "{validation.i18n.language-unsupported}";

    /** @return Jakarta Validation 分组 */
    Class<?>[] groups() default {};

    /** @return Jakarta Validation 附加载荷 */
    Class<? extends Payload>[] payload() default {};
}
