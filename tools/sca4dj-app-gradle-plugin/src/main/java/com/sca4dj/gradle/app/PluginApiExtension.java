package com.sca4dj.gradle.app;

import org.gradle.api.provider.Property;

/** 配置 App 自动生成的插件 API 身份及入口类。 */
public abstract class PluginApiExtension {
    /** 返回 App API 编码。 */
    public abstract Property<String> getId();

    /** 返回独立的 API 版本。 */
    public abstract Property<String> getVersion();

    /** 返回生成 App 插件入口的包名。 */
    public abstract Property<String> getGeneratedPackage();

    /** 返回生成的插件基类名称。 */
    public abstract Property<String> getEntryName();

    /** 返回 App 向插件公开的配置完整类名，类型必须声明 PluginApiType。 */
    public abstract Property<String> getConfigurationType();
}
