package com.sca4d.core.api.flow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按名称和版本索引不可变的 {@link FlowDefinition}。 */
public class FlowCatalog {

    private final Map<String, FlowDefinition> definitions;

    /**
     * 创建定义目录。
     *
     * @param definitions 全部可用定义
     */
    public FlowCatalog(List<FlowDefinition> definitions) {
        Objects.requireNonNull(definitions, "流程定义不能为空");
        Map<String, FlowDefinition> indexed = new HashMap<>();
        for (FlowDefinition definition : definitions) {
            validate(definition);
            String key = key(definition.name(), definition.version());
            if (indexed.putIfAbsent(key, definition) != null) {
                throw new IllegalArgumentException("流程定义名称和版本重复: " + key);
            }
        }
        this.definitions = Map.copyOf(indexed);
    }

    /**
     * 查找指定名称和版本的定义。
     *
     * @param name 定义名称
     * @param version 定义版本
     * @return 对应定义
     * @throws IllegalArgumentException 定义不存在时抛出
     */
    public FlowDefinition loadByNameAndVersion(String name, int version) {
        FlowDefinition definition = definitions.get(key(name, version));
        if (definition == null) {
            throw new IllegalArgumentException("流程定义不存在: " + name + ":" + version);
        }
        return definition;
    }

    private static void validate(FlowDefinition definition) {
        Objects.requireNonNull(definition, "流程定义不能包含空值");
        if (definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("流程定义名称不能为空白");
        }
        if (definition.version() < 1) {
            throw new IllegalArgumentException("流程定义版本必须为正数");
        }
        Objects.requireNonNull(definition.graph(), "流程定义图不能为空");
        if (definition.timeToLive() == null
                || definition.timeToLive().isZero()
                || definition.timeToLive().isNegative()) {
            throw new IllegalArgumentException("流程有效时间必须为正数");
        }
    }

    private static String key(String name, int version) {
        return name + ":" + version;
    }
}
