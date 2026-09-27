package com.sca4d.core.api.flow;

import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 保存由 Java 节点和转换组成的不可变状态图。
 *
 * <p>构造时校验节点唯一性、起点存在、连线端点、终点出边和从起点可达性，使错误定义在启动阶段失败。
 */
public class FlowGraph {

    private final String initialNodeId;

    private final Map<String, FlowNode> nodes;

    private final List<FlowTransition> transitions;

    /**
     * 创建并校验状态图。
     *
     * @param initialNodeId 起始节点标识
     * @param nodes 全部节点
     * @param transitions 全部转换
     */
    public FlowGraph(
            String initialNodeId,
            List<FlowNode> nodes,
            List<FlowTransition> transitions) {
        this.initialNodeId = Objects.requireNonNull(initialNodeId, "流程起始节点不能为空");
        Objects.requireNonNull(nodes, "流程节点不能为空");
        this.transitions = List.copyOf(Objects.requireNonNull(transitions, "流程转换不能为空"));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("流程图必须包含节点");
        }
        Map<String, FlowNode> indexed = new HashMap<>();
        for (FlowNode node : nodes) {
            FlowNode previous = indexed.putIfAbsent(node.id(), node);
            if (previous != null) {
                throw new IllegalArgumentException("流程节点标识重复: " + node.id());
            }
        }
        if (!indexed.containsKey(initialNodeId)) {
            throw new IllegalArgumentException("流程起始节点不存在: " + initialNodeId);
        }
        validateTransitions(indexed, this.transitions);
        validateReachability(initialNodeId, indexed, this.transitions);
        this.nodes = Map.copyOf(indexed);
    }

    /**
     * 返回起始节点标识。
     *
     * @return 起始节点标识
     */
    public String initialNodeId() {
        return initialNodeId;
    }

    /**
     * 按标识取得节点。
     *
     * @param nodeId 节点标识
     * @return 对应节点
     * @throws IllegalArgumentException 节点不存在时抛出
     */
    public FlowNode node(String nodeId) {
        FlowNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("流程节点不存在: " + nodeId);
        }
        return node;
    }

    /**
     * 返回不可变节点映射。
     *
     * @return 不可变节点映射
     */
    public @Unmodifiable Map<String, FlowNode> nodes() {
        return nodes;
    }

    /**
     * 返回不可变转换列表。
     *
     * @return 不可变转换列表
     */
    public @Unmodifiable List<FlowTransition> transitions() {
        return transitions;
    }

    /**
     * 查找当前事件能够触发的唯一转换。
     *
     * @param instance 当前实例
     * @param event 已确认事件
     * @return 唯一匹配转换
     * @throws IllegalStateException 没有转换或多个转换同时匹配时抛出
     */
    public FlowTransition selectTransition(FlowInstance instance, FlowEvent event) {
        List<FlowTransition> matches = transitions.stream()
                .filter(item -> item.from().equals(instance.currentNodeId()))
                .filter(item -> item.eventType().equals(event.type()))
                .filter(item -> item.condition().matches(instance, event))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException(
                    "当前流程节点不接受事件: " + instance.currentNodeId() + " -> " + event.type());
        }
        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "流程事件同时匹配多个转换: " + instance.currentNodeId() + " -> " + event.type());
        }
        return matches.getFirst();
    }

    private static void validateTransitions(
            Map<String, FlowNode> nodes,
            List<FlowTransition> transitions) {
        for (FlowTransition transition : transitions) {
            if (!nodes.containsKey(transition.from()) || !nodes.containsKey(transition.to())) {
                throw new IllegalArgumentException(
                        "流程转换引用不存在的节点: " + transition.from() + " -> " + transition.to());
            }
            if (nodes.get(transition.from()).type() == FlowNodeType.TERMINAL) {
                throw new IllegalArgumentException("流程终点不能存在出向转换: " + transition.from());
            }
        }
        boolean hasTerminal = nodes.values()
                .stream()
                .anyMatch(node -> node.type() == FlowNodeType.TERMINAL);
        if (!hasTerminal) {
            throw new IllegalArgumentException("流程图必须包含至少一个终点");
        }
    }

    private static void validateReachability(
            String initialNodeId,
            Map<String, FlowNode> nodes,
            List<FlowTransition> transitions) {
        Set<String> reached = new HashSet<>();
        reached.add(initialNodeId);
        boolean changed;
        do {
            changed = false;
            for (FlowTransition transition : transitions) {
                if (reached.contains(transition.from()) && reached.add(transition.to())) {
                    changed = true;
                }
            }
        } while (changed);
        if (reached.size() != nodes.size()) {
            Set<String> unreachable = new HashSet<>(nodes.keySet());
            unreachable.removeAll(reached);
            throw new IllegalArgumentException("流程包含不可达节点: " + unreachable);
        }
    }
}
