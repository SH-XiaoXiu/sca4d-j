package com.sca4d.core.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 在宿主注入点挂载生成的扩展客户端。 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginMount {
    /** 返回扩展契约编码。 */
    String value();

    /** 返回 KEYED 扩展的实现编码；SINGLE 使用空字符串。 */
    String key() default "";
}
