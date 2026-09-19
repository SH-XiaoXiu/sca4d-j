package com.sca4d.core.api.validation;

/**
 * 对一个明确类型执行校验（Validator）。
 *
 * @param <T> 待校验类型
 */
@FunctionalInterface
public interface Validator<T> {

    /**
     * 校验输入。
     *
     * @param value 待校验值
     * @return 当前规则发现的问题
     */
    ValidationResult validate(T value);
}
