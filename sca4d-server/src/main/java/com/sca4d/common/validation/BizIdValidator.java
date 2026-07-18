package com.sca4d.common.validation;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * 业务标识校验器。
 */
public class BizIdValidator implements ConstraintValidator<BizId, Object> {

    private BizIdMode mode;
    private int min;
    private int max;
    private Pattern pattern;

    /**
     * 初始化校验规则。
     *
     * @param annotation 业务标识注解
     */
    @Override
    public void initialize(@NotNull BizId annotation) {
        this.mode = annotation.mode();
        this.min = annotation.min();
        this.max = annotation.max();
        if (mode == BizIdMode.CUSTOM && StrUtil.isBlank(annotation.regexp())) {
            throw new IllegalArgumentException("@BizId CUSTOM 需要提供 regexp");
        }
        this.pattern = switch (mode) {
            case NUM -> Pattern.compile("^\\d+$");
            case CODE -> Pattern.compile(ValidationPatterns.BIZ_CODE);
            case NAMESPACED_CODE -> Pattern.compile(ValidationPatterns.NAMESPACED_CODE);
            case CUSTOM -> Pattern.compile(annotation.regexp());
        };
    }

    /**
     * 校验业务标识值。
     *
     * @param value 待校验值
     * @param context 校验上下文
     * @return 格式合法返回 true，否则返回 false
     */
    @Override
    public boolean isValid(@Nullable Object value, @Nullable ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number number) {
            return mode == BizIdMode.NUM && isPositiveInteger(number);
        }
        if (!(value instanceof CharSequence chars)) {
            return false;
        }
        String text = chars.toString();
        // 业务 ID 不自动 trim，带首尾空格的请求参数必须校验失败，不能静默修正。
        if (!text.equals(StrUtil.trim(text))) {
            return false;
        }
        int length = text.length();
        if (length < min || length > max) {
            return false;
        }
        if (!pattern.matcher(text).matches()) {
            return false;
        }
        return mode != BizIdMode.NUM || new BigInteger(text).signum() > 0;
    }

    /**
     * 判断通用 Number 是否为无精度损失的正整数。
     *
     * <p>浮点数和任意精度小数不属于业务数字 ID，即使当前值表面上没有小数部分也拒绝接受。
     *
     * @param number 待判断的数值
     * @return Java 整数类型或 BigInteger 且数值大于零时返回 true
     */
    private boolean isPositiveInteger(@NotNull Number number) {
        if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
            return number.longValue() > 0;
        }
        if (number instanceof BigInteger value) {
            return value.signum() > 0;
        }
        return false;
    }
}
