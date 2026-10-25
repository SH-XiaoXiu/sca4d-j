package com.sca4d.core.api.flow;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 保存推动 {@link FlowInstance} 状态变化的已确认事件。 */
public class FlowEvent {

    private final String type;

    private final Map<String, Object> attributes;

    /**
     * 创建事件。
     *
     * @param type 事件类型
     * @param attributes 已由业务代码确认的不可变事件属性
     */
    public FlowEvent(String type, Map<String, ?> attributes) {
        String normalized = TextUtils.requireText(type, "流程事件类型");
        Objects.requireNonNull(attributes, "流程事件属性不能为空");
        if (attributes.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                || attributes.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("流程事件属性不能包含空键或空值");
        }
        this.type = normalized;
        this.attributes = Map.copyOf(attributes);
    }

    /**
     * 事件类型。
     *
     * <p>取值由接入的 {@link FlowGraph} 转换规则声明，流程核心不预设集合，因此保留为字符串。
     * 没有转换接受该类型时，{@link FlowGraph#selectTransition} 抛出 {@link IllegalStateException}。
     *
     * @return 非空白事件类型
     */
    public String type() {
        return type;
    }

    /**
     * 不可变事件属性。
     *
     * @return 不可变事件属性
     */
    public @Unmodifiable Map<String, Object> attributes() {
        return attributes;
    }

    /**
     * 按类型读取事件属性。
     *
     * @param key 属性键
     * @param valueType 期望类型
     * @param <T> 属性类型
     * @return 属性不存在时为空
     */
    public <T> Optional<T> find(String key, Class<T> valueType) {
        Object value = attributes.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!valueType.isInstance(value)) {
            throw new IllegalArgumentException("流程事件属性类型不匹配: " + key);
        }
        return Optional.of(valueType.cast(value));
    }
}
