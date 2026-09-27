package com.sca4d.core.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 App 接受插件实现的公开接口。 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginExtension {
    /** 返回扩展契约的稳定编码。 */
    String value();
    /** 返回实现选择规则。 */
    ExtensionSelection selection() default ExtensionSelection.SINGLE;
    /** 返回启动时是否必须存在实现。 */
    boolean required() default false;
}
