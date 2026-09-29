package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存交付物声明的插件、App API 身份及入口类。 */
public final class PluginDescriptor {
    private final @NotNull TreeCode id;

    private final @NotNull String version;

    private final @NotNull TreeCode api;

    private final @NotNull String requiresApi;

    private final @NotNull String entry;

    public PluginDescriptor(
            @NotNull TreeCode id,
            @NotNull String version,
            @NotNull TreeCode api,
            @NotNull String requiresApi,
            @NotNull String entry) {
        this.id = Objects.requireNonNull(id);
        this.version = Objects.requireNonNull(version);
        this.api = Objects.requireNonNull(api);
        this.requiresApi = Objects.requireNonNull(requiresApi);
        this.entry = Objects.requireNonNull(entry);
        if (version.isBlank() || requiresApi.isBlank() || entry.isBlank()) {
            throw new IllegalArgumentException("插件版本与入口不能为空");
        }
    }

    /** 返回稳定插件编码。 */
    public @NotNull TreeCode id() {
        return id;
    }

    /** 返回交付版本。 */
    public @NotNull String version() {
        return version;
    }

    /** 返回所需 App API 编码。 */
    public @NotNull TreeCode api() {
        return api;
    }

    /** 返回 API 版本范围。 */
    public @NotNull String requiresApi() {
        return requiresApi;
    }

    /** 返回入口完整类名。 */
    public @NotNull String entry() {
        return entry;
    }
}
