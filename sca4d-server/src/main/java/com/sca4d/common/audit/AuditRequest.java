package com.sca4d.common.audit;

import org.jetbrains.annotations.Nullable;

/** 操作发生时采集的协议无关追踪与客户端请求摘要。 */
public class AuditRequest {

    private String traceId;
    private String method;
    private String path;
    private String clientIp;
    private String userAgent;

    /** @return 关联请求日志、消息和审计记录的追踪标识，无法采集时为 null */
    public @Nullable String getTraceId() {
        return traceId;
    }

    /** @param traceId 操作发生时的追踪标识 */
    public void setTraceId(@Nullable String traceId) {
        this.traceId = traceId;
    }

    /** @return HTTP 方法或消息命令类型，系统任务无法采集时为 null */
    public @Nullable String getMethod() {
        return method;
    }

    /** @param method HTTP 方法或消息命令类型 */
    public void setMethod(@Nullable String method) {
        this.method = method;
    }

    /** @return HTTP 路径或消息目的地，系统任务无法采集时为 null */
    public @Nullable String getPath() {
        return path;
    }

    /** @param path HTTP 路径或消息目的地 */
    public void setPath(@Nullable String path) {
        this.path = path;
    }

    /** @return 当前请求上下文解析出的客户端地址，无法采集时为 null */
    public @Nullable String getClientIp() {
        return clientIp;
    }

    /** @param clientIp 当前请求上下文解析出的客户端地址 */
    public void setClientIp(@Nullable String clientIp) {
        this.clientIp = clientIp;
    }

    /** @return 调用端 User-Agent 摘要，非 HTTP 调用或未提供时为 null */
    public @Nullable String getUserAgent() {
        return userAgent;
    }

    /** @param userAgent 调用端 User-Agent 摘要 */
    public void setUserAgent(@Nullable String userAgent) {
        this.userAgent = userAgent;
    }
}
