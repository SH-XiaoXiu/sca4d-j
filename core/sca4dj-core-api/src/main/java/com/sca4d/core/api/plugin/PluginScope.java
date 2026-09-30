package com.sca4d.core.api.plugin;

import com.sca4d.core.api.call.CallLifecycle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 为同步操作固定所有插件绑定代次；嵌套操作复用同一快照。 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@CallLifecycle.Intercepted
public @interface PluginScope {
}
