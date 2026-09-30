package com.sca4d.core.spring.web.view;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.data.DataAssertionException;
import com.sca4d.core.api.data.DataOps;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/** 使用当前 HTTP ObjectMapper 和 DataOps 定制 BaseView 的最终 JSON。 */
public class ViewJson {

    private final ObjectMapper objectMapper;

    private final ObjectNode root;

    /**
     * 创建包装最终 JSON 对象的定制入口。
     *
     * @param objectMapper 当前 HTTP ObjectMapper
     * @param root 当前最终 JSON 对象
     */
    public ViewJson(@NotNull ObjectMapper objectMapper, @NotNull ObjectNode root) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.root = Objects.requireNonNull(root, "ViewJson 根对象不能为空");
    }

    /**
     * 按点分路径写入使用当前 ObjectMapper 序列化的值。
     *
     * @param path 不包含空字段名的点分路径
     * @param value 待序列化值；空值写为 JSON null
     * @return 已修改同一个根对象的当前 ViewJson
     * @throws DataAssertionException 值无法序列化或路径中间值不是对象时抛出
     */
    public @NotNull ViewJson put(@NotNull String path, @Nullable Object value) {
        JsonNode node;
        try {
            node = objectMapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            throw new DataAssertionException(path, "View 值无法序列化为 JSON", null, exception);
        }
        return update(pipe().putJson(path, write(node, path)));
    }

    /**
     * 仅在值非空时按点分路径写入。
     *
     * @param path 不包含空字段名的点分路径
     * @param value 待序列化值；为空时不修改根对象
     * @return 当前 ViewJson
     * @throws DataAssertionException 值无法序列化或路径中间值不是对象时抛出
     */
    public @NotNull ViewJson opt(@NotNull String path, @Nullable Object value) {
        return value == null ? this : put(path, value);
    }

    /**
     * 删除点分路径字段，目标缺失时保持不变。
     *
     * @param path 不包含空字段名的点分路径
     * @return 已同步删除结果的当前 ViewJson
     * @throws DataAssertionException 路径中间值不是对象时抛出
     */
    public @NotNull ViewJson remove(@NotNull String path) {
        return update(pipe().remove(path));
    }

    /**
     * 原子重命名字段，来源缺失时保持不变。
     *
     * @param from 待读取和删除的点分路径
     * @param to 待写入的点分路径
     * @return 已同步重命名结果的当前 ViewJson
     * @throws IllegalArgumentException 来源和目标路径互为前缀时抛出
     * @throws DataAssertionException 路径中间值不是对象时抛出
     */
    public @NotNull ViewJson rename(@NotNull String from, @NotNull String to) {
        return update(pipe().rename(from, to));
    }

    /**
     * 使用当前 ObjectMapper 序列化并递归合并对象。
     *
     * @param value 待序列化并合并的对象；为空时不修改根对象
     * @return 已同步合并结果的当前 ViewJson
     * @throws DataAssertionException 值无法序列化或序列化结果不是对象时抛出
     */
    public @NotNull ViewJson merge(@Nullable Object value) {
        if (value == null) {
            return this;
        }
        JsonNode patch;
        try {
            patch = objectMapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            throw new DataAssertionException(null, "View 合并值无法序列化为 JSON", null, exception);
        }
        return update(pipe().merge(write(patch, null)));
    }

    /**
     * 递归合并严格解析的 JSON 对象。
     *
     * @param json 待合并的 JSON 对象文本；为空或空白时不修改根对象
     * @return 已同步合并结果的当前 ViewJson
     * @throws DataAssertionException JSON 语法无效或解析结果不是对象时抛出
     */
    public @NotNull ViewJson mergeJson(@Nullable String json) {
        return update(pipe().merge(json));
    }

    /**
     * 编辑当前对象或指定点分路径子对象。
     *
     * @param path 待编辑对象的点分路径；为空或空白时把当前对象传给 action
     * @param action 直接修改目标对象的操作
     * @return action 执行完成后的当前 ViewJson
     * @throws DataAssertionException 当前值、目标值或编辑结果不是对象时抛出
     */
    public @NotNull ViewJson edit(
            @Nullable String path,
            @NotNull Consumer<ViewJson> action) {
        Objects.requireNonNull(action, "ViewJson 对象编辑操作不能为空");
        if (path == null || path.isBlank()) {
            action.accept(this);
            return this;
        }
        DataOps.JsonPipe edited = pipe().edit(path, child -> {
            ObjectNode childRoot = readObject(child.get(), path);
            action.accept(new ViewJson(objectMapper, childRoot));
            return DataOps.j(write(childRoot, path)).req(path);
        });
        return update(edited);
    }

    /**
     * 编辑数组中的对象元素，标量和 null 元素保持原值。
     *
     * @param path 待读取数组的点分路径
     * @param action 依次修改每个对象元素的操作
     * @return 路径缺失时保持不变，否则返回已写回数组的当前 ViewJson
     * @throws DataAssertionException 目标不是数组或路径中间值不是对象时抛出
     */
    public @NotNull ViewJson list(
            @NotNull String path,
            @NotNull Consumer<ViewJson> action) {
        Objects.requireNonNull(action, "ViewJson 数组编辑操作不能为空");
        DataOps.JsonPipe edited = pipe().list(path, item -> {
            ObjectNode itemRoot = readObject(item.get(), path);
            action.accept(new ViewJson(objectMapper, itemRoot));
            return DataOps.j(write(itemRoot, path)).req(path);
        });
        return update(edited);
    }

    /**
     * Jackson 最终写出的可变根对象。
     *
     * @return 创建 ViewJson 时传入、之后操作会原地修改的同一个 ObjectNode
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The public contract intentionally exposes the live final JSON object")
    public @NotNull ObjectNode root() {
        return root;
    }

    /** 把根对象当前状态转换为 DataOps JSON 处理对象。 */
    private @NotNull DataOps.JsonPipe pipe() {
        return DataOps.j(write(root, null)).req("view");
    }

    /** 把 DataOps 的新结果同步回原 ObjectNode，保持 root 对象身份不变。 */
    private @NotNull ViewJson update(@NotNull DataOps.JsonPipe result) {
        ObjectNode updated = readObject(result.get(), null);
        root.removeAll();
        root.setAll(updated);
        return this;
    }

    /** 使用当前 ObjectMapper 序列化 JSON 节点。 */
    private @NotNull String write(@NotNull JsonNode node, @Nullable String field) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new DataAssertionException(field, "View JSON 无法序列化", null, exception);
        }
    }

    /** 使用当前 ObjectMapper 读取对象结果。 */
    private @NotNull ObjectNode readObject(@Nullable String json, @Nullable String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode object) {
                return object;
            }
            throw new DataAssertionException(field, "View JSON 必须是对象", null);
        } catch (JsonProcessingException exception) {
            throw new DataAssertionException(field, "View JSON 语法无效", null, exception);
        }
    }
}
