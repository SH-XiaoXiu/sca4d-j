package com.sca4d.core.api.flow;

import com.sca4d.core.api.utils.TextUtils;

/** 保存一个 {@link FlowInstance} 的不透明标识。 */
public class FlowId {

    private final String value;

    /**
     * 创建实例标识。
     *
     * @param value 非空白标识
     */
    public FlowId(String value) {
        this.value = TextUtils.requireText(value, "流程标识");
    }

    /**
     * 原始标识。
     *
     * @return 原始标识
     */
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof FlowId flowId && value.equals(flowId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
