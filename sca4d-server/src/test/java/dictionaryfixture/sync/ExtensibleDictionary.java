package dictionaryfixture.sync;

import com.sca4d.common.dictionary.CodeDictionary;
import com.sca4d.common.dictionary.CodeDictionaryItem;
import com.sca4d.common.dictionary.CodeDictionaryMode;
import com.sca4d.common.enums.BaseEnum;
import org.jetbrains.annotations.NotNull;

/** 启动同步测试使用的可扩展代码字典。 */
@CodeDictionary(code = "fixture.extensible", nameKey = "fixture.extensible", mode = CodeDictionaryMode.EXTENSIBLE)
public enum ExtensibleDictionary implements BaseEnum {
    /** 参与业务操作并形成数据库保留项。 */
    @CodeDictionaryItem(labelKey = "fixture.extensible.reserved")
    RESERVED,
    /** 只参与代码内部操作，不形成数据库投影。 */
    @CodeDictionaryItem(published = false)
    INTERNAL;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }
}
