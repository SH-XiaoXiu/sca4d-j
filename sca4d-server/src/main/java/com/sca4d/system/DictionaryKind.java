package com.sca4d.system;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 字典类型的三种固定业务含义。 */
@CodeDictionary(code = "system.dictionary-kind", nameKey = "dictionary.system.dictionary-kind")
public enum DictionaryKind implements BaseEnum {
    /** 由 Java 枚举定义并在启动时同步的不可变字典。 */
    CODE_ENUM("代码枚举"),
    /** 作为业务表和接口关系编码使用的可配置字典。 */
    BUSINESS_CODE("业务编码"),
    /** 只为自由输入提供快捷建议的可配置字典。 */
    INPUT_SUGGESTION("输入建议");

    private final String value;
    private final String description;

    DictionaryKind(@NotNull String description) {
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
