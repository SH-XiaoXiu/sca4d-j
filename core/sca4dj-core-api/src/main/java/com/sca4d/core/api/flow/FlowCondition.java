package com.sca4d.core.api.flow;

/** 判断转换是否适用于当前实例和事件。 */
@FunctionalInterface
public interface FlowCondition {

    /**
     * 判断转换条件。
     *
     * @param instance 推进前的实例
     * @param event 已确认事件
     * @return 当前转换可用时返回 {@code true}
     */
    boolean matches(FlowInstance instance, FlowEvent event);
}
