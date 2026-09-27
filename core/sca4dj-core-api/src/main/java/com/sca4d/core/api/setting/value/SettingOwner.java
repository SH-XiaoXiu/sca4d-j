package com.sca4d.core.api.setting.value;

import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 保存设置归属者使用的稳定类型和类型内唯一标识。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止使用 Java record。
public final class SettingOwner {

    private final TreeCode type;

    private final String id;

    /**
     * 创建设置 owner。
     *
     * @param type 全局稳定的 owner 类型，不使用 Java 类名
     * @param id   当前类型内唯一的 owner 标识
     */
    public SettingOwner(@NotNull TreeCode type, @NotNull String id) {
        this.type = Objects.requireNonNull(type, "设置 owner 类型不能为空");
        this.id = TextUtils.requireText(id, "设置 owner 标识");
        if (this.type.format(NameFormat.SLASH).length() > 128 || this.id.length() > 160) {
            throw new IllegalArgumentException("设置 owner 类型或标识超过数据库长度限制");
        }
    }

    /**
     * @return 固定归属 `Subject.SYSTEM` 的设置所有者
     */
    @Contract(pure = true)
    public static @NotNull SettingOwner system() {
        Subject system = Subject.SYSTEM;
        return new SettingOwner(system.type().code(), Long.toString(system.subjectId()));
    }

    /**
     * @return 指定 Account 对应的设置 owner
     */
    @Contract("_ -> new")
    public static @NotNull SettingOwner account(@NotNull Account account) {
        Account checked = Objects.requireNonNull(account, "设置所属 Account 不能为空");
        return new SettingOwner(Account.RESOURCE_TYPE, Long.toString(checked.accountId()));
    }

    /**
     * @return 指定资源对应的设置 owner
     */
    @Contract("_ -> new")
    public static @NotNull SettingOwner resource(@NotNull Resource resource) {
        Resource checked = Objects.requireNonNull(resource, "设置所属 Resource 不能为空");
        return new SettingOwner(checked.type(), checked.id());
    }

    /**
     * @return owner 的稳定类型
     */
    @Contract(pure = true)
    public @NotNull TreeCode type() {
        return type;
    }

    /**
     * @return owner 在当前类型内的稳定标识
     */
    @Contract(pure = true)
    public @NotNull String id() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SettingOwner owner && type.equals(owner.type) && id.equals(owner.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }

    @Override
    public @NotNull String toString() {
        return type + ":" + id;
    }
}
