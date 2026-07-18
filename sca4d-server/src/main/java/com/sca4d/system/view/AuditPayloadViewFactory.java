package com.sca4d.system.view;

import com.sca4d.common.audit.AuditPayload;
import org.jetbrains.annotations.NotNull;

/**
 * 把一种指定类型的审计内容转换为管理端视图。
 *
 * @param <P> 审计载荷类型
 */
public interface AuditPayloadViewFactory<P extends AuditPayload> {

    /**
     * 载荷类型。
     *
     * @return 载荷类型
     */
    @NotNull
    String payloadType();

    /**
     * 载荷 Java 类型。
     *
     * @return 载荷 Java 类型
     */
    @NotNull
    Class<P> payloadClass();

    /**
     * 构造前端展示视图。
     *
     * @param payload 审计载荷
     * @return 前端展示视图
     */
    @NotNull
    Object toView(@NotNull P payload);
}
