package com.sca4d.common.concurrent.ratelimit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 在 WebSocket 等非 Servlet 方法调用期间保存协议实现限流数据的线程持有器。 */
public final class RateLimitContextHolder {

    private static final ThreadLocal<RateLimitContext> HOLDER = new ThreadLocal<>();

    /** 禁止创建纯静态线程上下文持有器。 */
    private RateLimitContextHolder() {
    }

    /**
     * 安装当前协议调用的限流上下文。
     *
     * @param context 当前帧或任务的不可共享请求数据快照
     */
    public static void set(@NotNull RateLimitContext context) {
        HOLDER.set(context);
    }

    /**
     * 读取当前协议实现已经安装的限流上下文。
     *
     * @return 普通 HTTP 调用或当前线程未处理受支持协议时返回 null
     */
    public static @Nullable RateLimitContext currentOrNull() {
        return HOLDER.get();
    }

    /**
     * 清理当前线程的协议限流数据，必须在实现 finally 或完成回调中执行。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
