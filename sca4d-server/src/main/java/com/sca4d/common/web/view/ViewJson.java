package com.sca4d.common.web.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 视图 JSON 修改对象。
 *
 * <p>该对象包装 Jackson {@link ObjectNode}，用于在响应输出前对最终 JSON 做展示层修改。
 * path 使用点号分隔，例如 {@code profile.avatarUrl}。该类不负责业务校验，只负责 JSON 结构编辑。
 */
public final class ViewJson {

    private final ObjectMapper objectMapper;
    private final ObjectNode root;

    /**
     * 创建视图 JSON 修改对象。
     *
     * @param objectMapper Jackson ObjectMapper
     * @param root 根 JSON 对象
     */
    public ViewJson(@NotNull ObjectMapper objectMapper, @NotNull ObjectNode root) {
        this.objectMapper = objectMapper;
        this.root = root;
    }

    /**
     * 写入字段值。
     *
     * @param path 字段路径，不能为空
     * @param value 字段值
     * @return 当前对象
     */
    public @NotNull ViewJson put(@NotNull String path, @Nullable Object value) {
        PathParts parts = PathParts.of(path);
        if (parts.empty()) {
            return this;
        }
        ObjectNode parent = parent(parts, true);
        parent.set(parts.last(), value == null ? NullNode.getInstance() : objectMapper.valueToTree(value));
        return this;
    }

    /**
     * 非 null 时写入字段值。
     *
     * @param path 字段路径，不能为空
     * @param value 字段值，为 null 时忽略
     * @return 当前对象
     */
    public @NotNull ViewJson opt(@NotNull String path, @Nullable Object value) {
        if (value != null) {
            put(path, value);
        }
        return this;
    }

    /**
     * 删除字段。
     *
     * @param path 字段路径，不能为空
     * @return 当前对象
     */
    public @NotNull ViewJson remove(@NotNull String path) {
        PathParts parts = PathParts.of(path);
        if (parts.empty()) {
            return this;
        }
        ObjectNode parent = parent(parts, false);
        if (parent != null) {
            parent.remove(parts.last());
        }
        return this;
    }

    /**
     * 重命名字段。
     *
     * @param from 原字段路径，不能为空
     * @param to 目标字段路径，不能为空
     * @return 当前对象
     */
    public @NotNull ViewJson rename(@NotNull String from, @NotNull String to) {
        JsonNode value = get(from);
        if (value == null || value.isMissingNode()) {
            return this;
        }
        remove(from);
        put(to, value);
        return this;
    }

    /**
     * 合并对象。
     *
     * @param value 待合并对象
     * @return 当前对象
     */
    public @NotNull ViewJson merge(@Nullable Object value) {
        if (value == null) {
            return this;
        }
        JsonNode patch = objectMapper.valueToTree(value);
        if (patch instanceof ObjectNode objectNode) {
            mergeObject(root, objectNode);
        }
        return this;
    }

    /**
     * 合并 JSON 字符串。
     *
     * @param json 待合并 JSON 字符串
     * @return 当前对象
     */
    public @NotNull ViewJson mergeJson(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return this;
        }
        try {
            JsonNode patch = objectMapper.readTree(json);
            if (patch instanceof ObjectNode objectNode) {
                mergeObject(root, objectNode);
            }
        } catch (RuntimeException ignored) {
            // 展示层 JSON patch 非法时直接忽略，避免因为附加展示字段影响主响应。
        } catch (Exception ignored) {
            // Jackson 解析异常属于展示层附加输入异常，同样忽略。
        }
        return this;
    }

    /**
     * 编辑子对象。
     *
     * @param path 子对象路径，不能为空
     * @param action 编辑动作
     * @return 当前对象
     */
    public @NotNull ViewJson edit(@NotNull String path, @NotNull Consumer<ViewJson> action) {
        PathParts parts = PathParts.of(path);
        if (parts.empty()) {
            action.accept(this);
            return this;
        }
        ObjectNode parent = parent(parts, true);
        JsonNode current = parent.get(parts.last());
        ObjectNode objectNode;
        if (current instanceof ObjectNode existing) {
            objectNode = existing;
        } else {
            objectNode = JsonNodeFactory.instance.objectNode();
            parent.set(parts.last(), objectNode);
        }
        action.accept(new ViewJson(objectMapper, objectNode));
        return this;
    }

    /**
     * 编辑数组元素。
     *
     * @param path 数组路径，不能为空
     * @param action 元素编辑动作
     * @return 当前对象
     */
    public @NotNull ViewJson list(@NotNull String path, @NotNull Consumer<ViewJson> action) {
        JsonNode current = get(path);
        if (!(current instanceof ArrayNode arrayNode)) {
            return this;
        }
        // 数组元素只在内存中线性遍历一次，不执行数据库或外部调用；非对象元素保持原值。
        for (JsonNode item : arrayNode) {
            if (item instanceof ObjectNode objectNode) {
                action.accept(new ViewJson(objectMapper, objectNode));
            }
        }
        return this;
    }

    /**
     * 获取根 JSON 对象。
     *
     * @return 根 JSON 对象
     */
    public @NotNull ObjectNode root() {
        return root;
    }

    /**
     * 按点分路径读取当前 JSON 节点。
     *
     * @param path 相对当前根对象的点分字段路径
     * @return 命中的节点；中间路径不存在或不是对象时返回 {@code null}
     */
    private @Nullable JsonNode get(@NotNull String path) {
        PathParts parts = PathParts.of(path);
        if (parts.empty()) {
            return root;
        }
        JsonNode current = root;
        for (String part : parts.values()) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    /**
     * 定位目标字段的父对象，并按调用方要求选择是否创建缺失路径。
     *
     * @param parts 已解析的目标字段路径
     * @param create {@code true} 时将缺失或非对象中间节点替换为对象
     * @return 目标字段父对象；不允许创建且路径缺失时返回 {@code null}
     */
    private @Nullable ObjectNode parent(@NotNull PathParts parts, boolean create) {
        ObjectNode current = root;
        for (int index = 0; index < parts.parentSize(); index++) {
            String part = parts.get(index);
            JsonNode next = current.get(part);
            if (next instanceof ObjectNode objectNode) {
                current = objectNode;
                continue;
            }
            if (!create) {
                return null;
            }
            // 路径不存在或不是对象时，按目标展示结构重建子对象。
            ObjectNode child = JsonNodeFactory.instance.objectNode();
            current.set(part, child);
            current = child;
        }
        return current;
    }

    /**
     * 将 patch 对象递归合并到目标对象，嵌套对象继续合并，其他节点直接覆盖。
     *
     * @param target 接收最终展示字段的目标对象
     * @param patch 需要合并的 JSON 对象
     */
    private void mergeObject(@NotNull ObjectNode target, @NotNull ObjectNode patch) {
        for (var field : patch.properties()) {
            JsonNode current = target.get(field.getKey());
            JsonNode value = field.getValue();
            if (current instanceof ObjectNode currentObject && value instanceof ObjectNode valueObject) {
                mergeObject(currentObject, valueObject);
                continue;
            }
            target.set(field.getKey(), value);
        }
    }

    /** 点分 JSON 路径的不可变分段结果。 */
    private static final class PathParts {

        private final String[] values;

        /**
         * 创建已经按点号拆分的路径分段。
         *
         * @param values 保持原顺序的字段名数组
         */
        private PathParts(@NotNull String[] values) {
            this.values = values;
        }

        /**
         * 解析点分字段路径；空白路径表示当前根对象。
         *
         * @param path ViewJson 调用方提供的相对路径
         * @return 去除首尾空白后的路径分段
         */
        private static @NotNull PathParts of(@NotNull String path) {
            String cleanPath = path.trim();
            if (cleanPath.isEmpty()) {
                return new PathParts(new String[0]);
            }
            return new PathParts(cleanPath.split("\\."));
        }

        /**
         * 判断路径是否直接指向当前根对象。
         *
         * @return 没有字段分段时返回 {@code true}
         */
        private boolean empty() {
            return values.length == 0;
        }

        /**
         * 返回内部只读操作使用的字段分段数组。
         *
         * @return 创建当前对象时保存的字段分段
         */
        private @NotNull String[] values() {
            return values;
        }

        /**
         * 返回指定位置的字段名。
         *
         * @param index 从零开始的路径分段位置
         * @return 对应位置的字段名
         */
        private @NotNull String get(int index) {
            return values[index];
        }

        /**
         * 返回定位最终字段父对象所需遍历的分段数量。
         *
         * @return 总分段数减一，空路径返回零
         */
        private int parentSize() {
            return Math.max(0, values.length - 1);
        }

        /**
         * 返回最终字段名。
         *
         * @return 非空路径的最后一个分段
         */
        private @NotNull String last() {
            return values[values.length - 1];
        }
    }
}
