package com.sca4d.core.spring.identity;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Web 适配层从当前 HTTP 请求和 Controller 参数收集的业务身份选择输入。 */
public class PrincipalSelectionInput {

    private final Map<String, @Nullable Object> values;

    /**
     * 创建保持输入名称和解析顺序的不可变选择输入。
     *
     * @param values path、query、header、body 和已绑定方法参数
     */
    public PrincipalSelectionInput(Map<String, @Nullable Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * 用于转换成选择器参数类型的输入。
     *
     * @return 保持登记顺序的不可变输入
     */
    public @Unmodifiable Map<String, @Nullable Object> values() {
        return values;
    }
}
