package com.sca4d.core.spring.version;

import java.lang.annotation.*;

/** 声明接口所属能力及固定路由契约；contract 为零时按请求协商。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiCapability {
    /** @return 矩阵中的能力标识 */
    String value();

    /** @return 固定路径契约；零表示协商 */
    int contract() default 0;
}
