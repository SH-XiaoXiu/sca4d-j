package com.sca4d.system.view;

import com.sca4d.common.audit.AuditPayload;
import com.sca4d.common.audit.RawAuditPayload;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计载荷视图构造注册表。
 */
public class AuditPayloadViewRegistry {

    private final Map<String, AuditPayloadViewFactory<?>> factories = new LinkedHashMap<>();

    /**
     * 注册全部审计载荷 View 工厂，并拒绝重复的持久化载荷类型。
     *
     * <p>启动阶段按工厂数量线性扫描一次，不执行数据库或外部调用。
     *
     * @param factories 当前应用提供的全部审计载荷 View 工厂
     */
    public AuditPayloadViewRegistry(@NotNull Collection<AuditPayloadViewFactory<?>> factories) {
        for (AuditPayloadViewFactory<?> factory : factories) {
            if (this.factories.containsKey(factory.payloadType())) {
                throw new IllegalStateException("重复的审计载荷 View Factory: " + factory.payloadType());
            }
            this.factories.put(factory.payloadType(), factory);
        }
    }

    /**
     * 构造载荷视图。
     *
     * @param payloadType 载荷类型
     * @param payload 审计载荷
     * @return 载荷视图
     */
    public @NotNull Object toView(@Nullable String payloadType, @NotNull AuditPayload payload) {
        AuditPayloadViewFactory<?> factory = payloadType == null ? null : factories.get(payloadType);
        if (factory == null) {
            if (payload instanceof RawAuditPayload raw) {
                return RawAuditPayloadView.from(raw);
            }
            return payload;
        }
        return toView(factory, payload);
    }

    /**
     * 工厂的运行时类型检查通过后，调用该工厂把审计内容转换为 View。
     *
     * @param factory 与 payloadType 匹配的审计载荷 View 工厂
     * @param payload 已解码的审计载荷
     * @return 工厂转换结果；载荷 Java 类型不匹配时保留原载荷
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @NotNull Object toView(@NotNull AuditPayloadViewFactory factory, @NotNull AuditPayload payload) {
        if (!factory.payloadClass().isInstance(payload)) {
            return payload;
        }
        return factory.toView(payload);
    }
}
