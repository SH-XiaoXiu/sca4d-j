package com.sca4d.system;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.dictionary.CodeDictionaryItem;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 可配置字典类型和字典项当前是否允许用于新输入。 */
@CodeDictionary(code = "system.dictionary-status", nameKey = "dictionary.system.dictionary-status")
public enum DictionaryStatus implements BaseEnum {
    /** 可以查询并用于新的业务输入。 */
    @CodeDictionaryItem(tone = DictionaryTone.SUCCESS)
    ACTIVE("启用"),
    /** 保留历史读取，但不能再用于新的业务输入。 */
    @CodeDictionaryItem(tone = DictionaryTone.WARNING)
    DISABLED("停用");

    private final String value;
    private final String description;

    DictionaryStatus(@NotNull String description) {
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
