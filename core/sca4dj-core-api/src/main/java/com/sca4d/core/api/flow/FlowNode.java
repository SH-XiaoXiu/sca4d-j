package com.sca4d.core.api.flow;

import com.sca4d.core.api.utils.TextUtils;

import java.util.Objects;

/** 表示 {@link FlowGraph} 中的一个节点。 */
public class FlowNode {

    private final String id;

    private final FlowNodeType type;

    /**
     * 创建节点。
     *
     * @param id 定义内唯一的节点标识
     * @param type 节点行为分类
     */
    public FlowNode(String id, FlowNodeType type) {
        this.id = TextUtils.requireText(id, "流程节点标识");
        this.type = Objects.requireNonNull(type, "流程节点类型不能为空");
    }

    /**
     * 返回节点标识。
     *
     * @return 节点标识
     */
    public String id() {
        return id;
    }

    /**
     * 返回节点类型。
     *
     * @return 节点类型
     */
    public FlowNodeType type() {
        return type;
    }
}
