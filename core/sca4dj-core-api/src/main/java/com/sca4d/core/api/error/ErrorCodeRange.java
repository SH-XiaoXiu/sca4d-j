package com.sca4d.core.api.error;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个错误码枚举使用的闭区间号段（Error Code Range）。
 *
 * <p>{@link ErrorCodeCatalog} 会校验枚举常量是否落在号段内，并拒绝不同枚举之间的号段重叠。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ErrorCodeRange {

    /**
     * 返回包含在号段中的起始数值。
     *
     * @return 包含在号段中的起始数值
     */
    int start();

    /**
     * 返回包含在号段中的结束数值。
     *
     * @return 包含在号段中的结束数值
     */
    int end();
}
