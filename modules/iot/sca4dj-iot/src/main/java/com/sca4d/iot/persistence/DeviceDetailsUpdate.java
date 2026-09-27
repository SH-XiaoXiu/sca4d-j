package com.sca4d.iot.persistence;

import com.sca4d.core.api.persistence.Patch;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存 Device 展示名称和备注交给 Mapper 执行的字段更新意图。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class DeviceDetailsUpdate {

    private final @NotNull Patch<String> displayName;

    private final @NotNull Patch<String> remark;

    /**
     * 创建 Device 展示资料字段更新。
     *
     * @param displayName 展示名称的字段更新意图
     * @param remark 备注的字段更新意图
     */
    public DeviceDetailsUpdate(
            @NotNull Patch<String> displayName,
            @NotNull Patch<String> remark) {
        this.displayName = Objects.requireNonNull(displayName, "Device 展示名称 Patch 不能为空");
        this.remark = Objects.requireNonNull(remark, "Device 备注 Patch 不能为空");
    }

    /**
     * 读取展示名称的字段更新意图。
     *
     * @return 展示名称 Patch
     */
    @Contract(pure = true)
    public @NotNull Patch<String> displayName() {
        return displayName;
    }

    /**
     * 通过 JavaBean 属性读取展示名称的字段更新意图，供 MyBatis 解析 Mapper 参数。
     *
     * @return 展示名称 Patch
     */
    @Contract(pure = true)
    public @NotNull Patch<String> getDisplayName() {
        return displayName;
    }

    /**
     * 读取备注的字段更新意图。
     *
     * @return 备注 Patch
     */
    @Contract(pure = true)
    public @NotNull Patch<String> remark() {
        return remark;
    }

    /**
     * 通过 JavaBean 属性读取备注的字段更新意图，供 MyBatis 解析 Mapper 参数。
     *
     * @return 备注 Patch
     */
    @Contract(pure = true)
    public @NotNull Patch<String> getRemark() {
        return remark;
    }

    /**
     * 判断是否至少有一个字段需要写入数据库。
     *
     * @return 展示名称或备注不是 IGNORE 时为 {@code true}
     */
    @CheckReturnValue
    @Contract(pure = true)
    public boolean hasUpdates() {
        return !displayName.isIgnore() || !remark.isIgnore();
    }
}
