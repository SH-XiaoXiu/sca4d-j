package com.sca4d.common.dictionary;

import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 字典项在共用管理端组件中的有限含义色调。 */
@CodeDictionary(code = "system.dictionary-tone", nameKey = "dictionary.system.dictionary-tone")
public enum DictionaryTone implements BaseEnum {
    /** 普通中性内容。 */
    DEFAULT("默认"),
    /** 说明或一般信息。 */
    INFO("信息"),
    /** 成功、启用或正常状态。 */
    SUCCESS("成功"),
    /** 需要注意但尚未失败的状态。 */
    WARNING("警告"),
    /** 失败、危险或不可用状态。 */
    DANGER("危险");

    private final String value;
    private final String description;

    DictionaryTone(@NotNull String description) {
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
