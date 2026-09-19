package com.sca4d.core.api.setting;

import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存一次数据库查询得到的设置值及其维护来源。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class SettingValue<T> {

    private final Setting<T> setting;

    private final SettingOwner owner;

    private final T value;

    private final boolean stored;

    private final boolean synchronizedFromEnvironment;

    private final Version version;

    /**
     * 创建设置值。
     *
     * @param setting 对应设置定义
     * @param owner 当前值所属 owner
     * @param value 数据库值或 Java 默认值
     * @param stored 数据库中是否存在覆盖值
     * @param synchronizedFromEnvironment 是否由初始化节点持续同步并禁止界面修改
     * @param version 当前 owner 设置行版本；没有数据库行时为 {@code 0}
     */
    public SettingValue(
            @NotNull Setting<T> setting,
            @NotNull SettingOwner owner,
            @NotNull T value,
            boolean stored,
            boolean synchronizedFromEnvironment,
            @NotNull Version version) {
        this.setting = Objects.requireNonNull(setting, "设置定义不能为空");
        this.owner = Objects.requireNonNull(owner, "设置 owner 不能为空");
        this.value = Objects.requireNonNull(value, "设置值不能为空");
        this.stored = stored;
        this.synchronizedFromEnvironment = synchronizedFromEnvironment;
        this.version = Objects.requireNonNull(version, "设置版本不能为空");
        if (!stored && synchronizedFromEnvironment) {
            throw new IllegalArgumentException("没有覆盖值的设置不能标记为 ENV 同步");
        }
    }

    /** @return 对应设置定义 */
    @Contract(pure = true)
    public @NotNull Setting<T> setting() {
        return setting;
    }

    /** @return 当前设置值所属 owner */
    @Contract(pure = true)
    public @NotNull SettingOwner owner() {
        return owner;
    }

    /** @return 数据库值或 Java 默认值 */
    @Contract(pure = true)
    public @NotNull T value() {
        return value;
    }

    /** @return 数据库中存在该设置时返回 {@code true} */
    @Contract(pure = true)
    public boolean stored() {
        return stored;
    }

    /** @return 由初始化节点持续同步并禁止界面修改时返回 {@code true} */
    @Contract(pure = true)
    public boolean synchronizedFromEnvironment() {
        return synchronizedFromEnvironment;
    }

    /** @return 当前 owner 设置行版本；没有数据库行时返回 {@code 0} */
    @Contract(pure = true)
    public @NotNull Version version() {
        return version;
    }
}
