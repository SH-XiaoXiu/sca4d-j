package com.sca4d.core.api.flow;

import com.sca4d.core.api.utils.TextUtils;

import java.util.Objects;

/** 记录 {@link FlowGraph} 在事件满足条件时从一个节点转到另一个节点的规则。 */
public class FlowTransition {

    private final FlowCondition condition;

    private final String eventType;

    private final String from;

    private final String to;

    /**
     * 创建无附加条件的转换。
     *
     * @param from 起始节点
     * @param to 目标节点
     * @param eventType 触发事件类型
     */
    public FlowTransition(String from, String to, String eventType) {
        this(from, to, eventType, (instance, event) -> true);
    }

    /**
     * 创建带确定性条件的转换。
     *
     * @param from 起始节点
     * @param to 目标节点
     * @param eventType 触发事件类型
     * @param condition 转换条件
     */
    public FlowTransition(String from, String to, String eventType, FlowCondition condition) {
        this.from = TextUtils.requireText(from, "转换起始节点");
        this.to = TextUtils.requireText(to, "转换目标节点");
        this.eventType = TextUtils.requireText(eventType, "转换事件类型");
        this.condition = Objects.requireNonNull(condition, "转换条件不能为空");
    }

    /**
     * 起始节点标识。
     *
     * @return 起始节点标识
     */
    public String from() {
        return from;
    }

    /**
     * 目标节点标识。
     *
     * @return 目标节点标识
     */
    public String to() {
        return to;
    }

    /**
     * 触发事件类型。
     *
     * @return 触发事件类型
     */
    public String eventType() {
        return eventType;
    }

    /**
     * 转换条件。
     *
     * @return 转换条件
     */
    public FlowCondition condition() {
        return condition;
    }

}
