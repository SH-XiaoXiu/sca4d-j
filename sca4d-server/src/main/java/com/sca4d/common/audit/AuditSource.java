package com.sca4d.common.audit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 从当前 HTTP、消息或系统任务中读取审计操作者和请求信息。 */
public interface AuditSource {

    /**
     * 采集当前执行身份与 Principal 快照。
     *
     * @return 未认证调用也返回空字段对象，调用方无需区分 HTTP、消息或系统任务
     */
    @NotNull
    AuditActor actor();

    /**
     * 采集当前请求的追踪、协议和客户端摘要。
     *
     * @return 没有请求上下文时返回空字段对象，不生成新的请求数据
     */
    @NotNull
    AuditRequest request();

    /**
     * 在业务资源仍存在时采集当前请求可识别的目标名称。
     *
     * @param targetType 固定资源类型
     * @param targetId 固定资源标识
     * @return 业务名称；资源缺失或类型不支持时为 null
     */
    @Nullable
    String targetLabel(@NotNull String targetType, @NotNull String targetId);
}
