package com.sca4d.core.api.validation;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 合并多个同类型校验器的确定性执行结果。 */
public final class Validation {

    private Validation() {
    }

    /**
     * 依次执行全部校验器并合并问题。
     *
     * @param value 待校验值
     * @param validators 按执行顺序排列的校验器
     * @param <T> 待校验类型
     * @return 合并后的校验结果
     */
    @CheckReturnValue
    public static <T> ValidationResult validate(
            T value,
            @Unmodifiable List<Validator<T>> validators) {
        Objects.requireNonNull(value, "待校验值不能为空");
        Objects.requireNonNull(validators, "校验器不能为空");
        List<Violation> violations = new ArrayList<>();
        for (Validator<T> validator : validators) {
            ValidationResult result = Objects.requireNonNull(
                    validator.validate(value),
                    "校验器不能返回空结果");
            violations.addAll(result.violations());
        }
        return violations.isEmpty() ? ValidationResult.valid() : new ValidationResult(violations);
    }
}
