package com.sca4d.core.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 将宿主对象绑定到生成的服务契约。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginBinding {
    /** 返回声明服务的接口或类型。 */
    Class<?> value();
}
