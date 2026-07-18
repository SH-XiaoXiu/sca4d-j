package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 未注册类型、未知历史版本或无场景载荷时使用的有序键值载荷。 */
public class RawAuditPayload implements AuditPayload {

    private Map<String, Object> values = new LinkedHashMap<>();

    /** 创建不包含业务字段的通用载荷。 */
    public RawAuditPayload() {
    }

    /**
     * 创建与调用方映射解除可变共享的通用载荷。
     *
     * @param values 按展示顺序保存的载荷字段
     */
    public RawAuditPayload(@NotNull Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }

    /** @return 新的空通用载荷 */
    public static @NotNull RawAuditPayload empty() {
        return new RawAuditPayload();
    }

    /** @return 当前载荷字段；反序列化历史空值时返回不可变空映射 */
    public @NotNull Map<String, Object> getValues() {
        return values == null ? Map.of() : Collections.unmodifiableMap(values);
    }

    /** @param values 按展示顺序保存的载荷字段，内部复制后持有 */
    public void setValues(@NotNull Map<String, Object> values) {
        this.values = new LinkedHashMap<>(values);
    }
}
