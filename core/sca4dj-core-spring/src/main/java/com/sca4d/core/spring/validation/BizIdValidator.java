package com.sca4d.core.spring.validation;

import static com.sca4d.core.spring.validation.BizIdMode.CODE;
import static com.sca4d.core.spring.validation.BizIdMode.CUSTOM;
import static com.sca4d.core.spring.validation.BizIdMode.NUM;
import static com.sca4d.core.spring.validation.ValidationPatterns.BIZ_CODE;
import static com.sca4d.core.spring.validation.ValidationPatterns.NAMESPACED_CODE;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** 执行 BizId 声明的业务标识格式检查。 */
public class BizIdValidator implements ConstraintValidator<BizId, Object> {

    private BizIdMode mode;

    private int min;

    private int max;

    private Pattern pattern;

    /** 创建等待 Bean Validation Provider 初始化约束参数的校验器。 */
    public BizIdValidator() {
    }

    /**
     * 读取 BizId 参数并编译当前校验器使用的完整匹配表达式。
     *
     * @param annotation 业务标识格式声明
     * @throws IllegalArgumentException 长度范围无效或 CUSTOM 模式缺少表达式时抛出
     */
    @Override
    public void initialize(@NotNull BizId annotation) {
        mode = annotation.mode();
        min = annotation.min();
        max = annotation.max();
        if (min < 0 || max < min) {
            throw new IllegalArgumentException("@BizId 长度范围不正确");
        }
        if (mode == CUSTOM && annotation.regexp().isBlank()) {
            throw new IllegalArgumentException("@BizId CUSTOM 需要提供 regexp");
        }
        pattern = switch (mode) {
            case NUM -> Pattern.compile("^\\d+$");
            case CODE -> Pattern.compile(BIZ_CODE);
            case NAMESPACED_CODE -> Pattern.compile(NAMESPACED_CODE);
            case CUSTOM -> Pattern.compile(annotation.regexp());
        };
    }

    /**
     * 按声明模式、长度和完整匹配表达式检查数字或文本。
     *
     * @param value 待校验的数字或文本
     * @param context Bean Validation 上下文
     * @return 空值或格式符合声明时返回 {@code true}
     */
    @Override
    public boolean isValid(@Nullable Object value, @Nullable ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number number) {
            return mode == NUM && isPositiveInteger(number);
        }
        if (!(value instanceof CharSequence chars)) {
            return false;
        }
        String text = chars.toString();
        if (!text.equals(text.strip()) || text.length() < min || text.length() > max) {
            return false;
        }
        return pattern.matcher(text).matches()
                && (mode != NUM || new BigInteger(text).signum() > 0);
    }

    /**
     * 判断 Number 是否为 Java 整数类型或正 BigInteger。
     *
     * @param number 待检查数字
     * @return 数字属于受支持整数类型且大于零时为 {@code true}
     */
    private boolean isPositiveInteger(Number number) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return number.longValue() > 0;
        }
        return number instanceof BigInteger value && value.signum() > 0;
    }
}
