package com.sca4d.common.dictionary;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 标记字典类型或字典项由代码同步还是由管理端维护。 */
public enum DictionarySource implements BaseEnum {
    /** 由应用代码声明并在启动阶段同步。 */
    CODE("代码"),
    /** 由平台或租户管理操作维护。 */
    CONFIGURATION("配置");

    private final String value;
    private final String description;

    DictionarySource(@NotNull String description) {
        this.value = BaseEnum.normalize(name());
        this.description = description;
    }

    @Override
    public @NotNull String value() {
        return value;
    }

    @Override
    public @NotNull String description() {
        return description;
    }
}
