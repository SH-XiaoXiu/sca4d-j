package com.sca4d.core.spring.dictionary;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 保存数据库动态字典的管理配置。 */
@SuppressWarnings("ClassCanBeRecord") // 项目禁止声明 record，这里使用普通 class。
public class DynamicDictionary {

    private final String key;

    private final String label;

    private final boolean enabled;

    private final int sortOrder;

    private final long version;

    public DynamicDictionary(
            @NotNull String key,
            @NotNull String label,
            boolean enabled,
            int sortOrder,
            long version) {
        this.key = Objects.requireNonNull(key, "动态字典键不能为空");
        this.label = Objects.requireNonNull(label, "动态字典名称不能为空");
        this.enabled = enabled;
        this.sortOrder = sortOrder;
        this.version = version;
    }

    /** @return 动态字典键 */
    public @NotNull String key() {
        return key;
    }

    /** @return 动态字典展示名称 */
    public @NotNull String label() {
        return label;
    }

    /** @return 字典允许查询和选择时返回 {@code true} */
    public boolean enabled() {
        return enabled;
    }

    /** @return 管理端展示顺序 */
    public int sortOrder() {
        return sortOrder;
    }

    /** @return 当前乐观锁版本 */
    public long version() {
        return version;
    }
}
