package com.sca4d.app.server.system;

import com.sca4d.core.api.dictionary.DictionaryOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存管理端展示一个代码字典项或数据库字典项所需的数据和乐观锁版本。 */
public final class ManagedDictionaryOption {

    private final DictionaryOption option;

    private final Long version;

    /**
     * 创建字典项管理结果。
     *
     * @param option 聚合后的字典项
     * @param version 数据库项的乐观锁版本；代码项为空
     */
    public ManagedDictionaryOption(@NotNull DictionaryOption option, @Nullable Long version) {
        this.option = Objects.requireNonNull(option, "Dictionary 选项不能为空");
        this.version = version;
        if (option.codeDefined() == (version != null)) {
            throw new IllegalArgumentException("代码 Dictionary 选项不能有数据库版本，数据库选项必须有版本");
        }
    }

    /** @return 聚合后的字典项 */
    public @NotNull DictionaryOption option() {
        return option;
    }

    /** @return 数据库项的乐观锁版本；代码项为空 */
    public @Nullable Long version() {
        return version;
    }
}
