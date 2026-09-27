package com.sca4d.core.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 将 App 的无业务实现依赖类型导出到生成 API 构件。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface PluginApiType {
}
