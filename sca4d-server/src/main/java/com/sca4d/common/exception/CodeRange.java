package com.sca4d.common.exception;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 错误码号段声明。
 *
 * <p>每个实现 {@link BizErrorCode} 的枚举类都必须声明自己的号段。号段使用闭区间，
 * 注册目录时会校验错误码是否落在号段内，并校验不同枚举类之间的号段冲突。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CodeRange {

    /**
     * 号段起始值。
     *
     * @return 起始错误码，包含该值
     */
    int start();

    /**
     * 号段结束值。
     *
     * @return 结束错误码，包含该值
     */
    int end();
}
