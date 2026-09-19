package com.sca4d.core.spring.dictionary.fixture;

import com.sca4d.core.api.dictionary.Dictionary;
import org.jetbrains.annotations.NotNull;

/** 供 Dictionary classpath 扫描测试发现的枚举。 */
public enum ScannedDictionary implements Dictionary {
    /** 第一个测试选项。 */
    FIRST,
    /** 第二个测试选项。 */
    SECOND;

    @Override
    public @NotNull String value() {
        return name();
    }

    @Override
    public @NotNull String description() {
        return name();
    }

    @Override
    public @NotNull String dictionaryKey() {
        return "fixture.scanned";
    }
}
