package com.sca4d.app.server.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.setting.definition.SystemSetting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存管理端展示一个设置所需的定义、当前编码值和维护来源。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class ManagedSetting {

    private final JsonNode value;

    private final SystemSetting<?> setting;

    private final Version version;

    private final boolean stored;

    private final boolean synchronizedFromEnvironment;

    /** 创建设置管理结果。 */
    public ManagedSetting(
            @NotNull SystemSetting<?> setting,
            @Nullable JsonNode value,
            boolean stored,
            boolean synchronizedFromEnvironment,
            @NotNull Version version) {
        this.setting = Objects.requireNonNull(setting, "设置定义不能为空");
        this.value = value;
        this.stored = stored;
        this.synchronizedFromEnvironment = synchronizedFromEnvironment;
        this.version = Objects.requireNonNull(version, "设置版本不能为空");
    }

    /** @return 设置定义 */
    public @NotNull SystemSetting<?> setting() {
        return setting;
    }

    /** @return 非敏感设置的当前编码值；敏感设置返回空 */
    public @Nullable JsonNode value() {
        return value;
    }

    /** @return 数据库中存在设置值时返回 {@code true} */
    public boolean stored() {
        return stored;
    }

    /** @return 当前值由初始化节点持续同步时返回 {@code true} */
    public boolean synchronizedFromEnvironment() {
        return synchronizedFromEnvironment;
    }

    /** @return 当前设置行版本；数据库还没有该设置行时返回 {@code 0} */
    public @NotNull Version version() {
        return version;
    }
}
