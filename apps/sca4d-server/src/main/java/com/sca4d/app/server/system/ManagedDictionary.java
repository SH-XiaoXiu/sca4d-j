package com.sca4d.app.server.system;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 保存管理端展示一个代码字典或动态字典所需的数据、选项和乐观锁版本。 */
public class ManagedDictionary {

    private final String key;

    private final String label;

    private final boolean codeDefined;

    private final boolean optionsEditable;

    private final boolean enabled;

    private final int sortOrder;

    private final Long version;

    private final List<ManagedDictionaryOption> options;

    /**
     * 创建字典管理结果。
     *
     * @param key 字典稳定键
     * @param label 动态字典名称；代码字典使用稳定键
     * @param codeDefined 是否由 Java 枚举声明
     * @param optionsEditable 是否允许管理数据库选项
     * @param enabled 动态字典是否可供业务查询；代码字典始终启用
     * @param sortOrder 动态字典展示顺序；代码字典为 {@code 0}
     * @param version 动态字典乐观锁版本；代码字典为空
     * @param options 代码项与数据库项
     */
    public ManagedDictionary(
            @NotNull String key,
            @NotNull String label,
            boolean codeDefined,
            boolean optionsEditable,
            boolean enabled,
            int sortOrder,
            @Nullable Long version,
            @NotNull List<@NotNull ManagedDictionaryOption> options) {
        this.key = Objects.requireNonNull(key, "Dictionary 键不能为空");
        this.label = Objects.requireNonNull(label, "Dictionary 名称不能为空");
        this.codeDefined = codeDefined;
        this.optionsEditable = optionsEditable;
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.version = version;
        this.options = List.copyOf(options);
        if (codeDefined == (version != null)) {
            throw new IllegalArgumentException("代码 Dictionary 不能有数据库版本，动态 Dictionary 必须有版本");
        }
    }

    /** @return 字典稳定键 */
    public @NotNull String key() {
        return key;
    }

    /** @return 动态字典名称；代码字典返回稳定键 */
    public @NotNull String label() {
        return label;
    }

    /** @return 字典由 Java 枚举声明时返回 {@code true} */
    public boolean codeDefined() {
        return codeDefined;
    }

    /** @return 管理端可以新增、修改和删除数据库选项时返回 {@code true} */
    public boolean optionsEditable() {
        return optionsEditable;
    }

    /** @return 动态字典可供业务查询时返回 {@code true} */
    public boolean enabled() {
        return enabled;
    }

    /** @return 动态字典展示顺序；代码字典返回 {@code 0} */
    public int sortOrder() {
        return sortOrder;
    }

    /** @return 动态字典乐观锁版本；代码字典返回空 */
    public @Nullable Long version() {
        return version;
    }

    /** @return 代码项与数据库项组成的不可修改列表 */
    public @NotNull @Unmodifiable List<@NotNull ManagedDictionaryOption> options() {
        return options;
    }
}
