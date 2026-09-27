package com.sca4dj.gradle.app;

import org.gradle.api.provider.Property;

/** 配置具体插件的身份、入口及所需 App API。 */
public abstract class PluginArtifactExtension {
    /** 返回稳定插件编码。 */
    public abstract Property<String> getId();
    /** 返回插件入口完整类名。 */
    public abstract Property<String> getEntryClass();
    /** 返回目标 App API 编码。 */
    public abstract Property<String> getApi();
    /** 返回插件接受的 App API 版本范围。 */
    public abstract Property<String> getRequiresApi();
}
