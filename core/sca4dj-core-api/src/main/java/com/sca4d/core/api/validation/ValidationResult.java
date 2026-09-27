package com.sca4d.core.api.validation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 表示一次校验得到的不可变问题集合（Validation Result）。 */
public class ValidationResult {

    private final List<Violation> violations;

    /**
     * 创建校验结果。
     *
     * @param violations 不可变校验问题集合
     */
    public ValidationResult(List<Violation> violations) {
        this.violations = List.copyOf(Objects.requireNonNull(violations, "校验问题不能为空"));
    }

    /**
     * 返回校验通过的结果。
     *
     * @return 校验通过的结果
     */
    @Contract(pure = true)
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /**
     * 返回不可变校验问题集合。
     *
     * @return 不可变校验问题集合
     */
    @Contract(pure = true)
    public @Unmodifiable List<Violation> violations() {
        return violations;
    }

    /**
     * 返回没有校验问题时返回 {@code true}。
     *
     * @return 没有校验问题时返回 {@code true}
     */
    @Contract(pure = true)
    public boolean isValid() {
        return violations.isEmpty();
    }
}
