package com.sca4d.app.server.plugin;

import com.sca4d.core.api.plugin.PluginApiType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Sca4d Server 向一个插件代次公开的不可变运行配置，不暴露 Spring 或部署凭据。 */
@PluginApiType
public final class Sca4dServerPluginSettings {
    private final @NotNull String applicationName;

    /** 保存宿主应用名称；空白名称不能用于插件的运行诊断。 */
    public Sca4dServerPluginSettings(@NotNull String applicationName) {
        this.applicationName = Objects.requireNonNull(applicationName);
        if (applicationName.isBlank()) {
            throw new IllegalArgumentException("插件公开的应用名称不能为空");
        }
    }

    /** 返回当前代次加载时读取的宿主应用名称。 */
    @Contract(pure = true)
    public @NotNull String applicationName() {
        return applicationName;
    }
}
