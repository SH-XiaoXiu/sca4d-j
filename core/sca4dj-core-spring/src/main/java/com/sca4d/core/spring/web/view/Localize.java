package com.sca4d.core.spring.web.view;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 String View 字段在 HTTP 序列化时由静态文本来源或动态字段键生成。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Localize {

    /**
     * 指定 BaseViewSerializer 在 HTTP 序列化时调用的文本来源方法。
     *
     * @return 当前 View 中返回 LocalizableText 的 protected 零参数方法名
     */
    String source() default "";

    /**
     * 指定 BaseViewSerializer 从当前 View 的动态国际化数据读取的字段键。
     *
     * @return `LocalizedFields` 中的字段键；与 {@link #source()} 必须且只能填写一个
     */
    String key() default "";
}
