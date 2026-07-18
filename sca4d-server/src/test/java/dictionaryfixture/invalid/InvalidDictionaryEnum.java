package dictionaryfixture.invalid;

import com.sca4d.common.dictionary.CodeDictionary;

/** 用于验证显式字典声明不能绕过 BaseEnum 契约的测试夹具。 */
@CodeDictionary(code = "fixture.invalid", nameKey = "fixture.invalid")
public enum InvalidDictionaryEnum {
    /** 用于触发未实现 BaseEnum 检查的唯一枚举值。 */
    VALUE
}
