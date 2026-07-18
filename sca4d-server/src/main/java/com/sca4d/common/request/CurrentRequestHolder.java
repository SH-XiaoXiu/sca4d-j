package com.sca4d.common.request;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 当前请求持有器。
 *
 * <p>基于 ThreadLocal 保存当前请求。Filter 必须在请求结束时清理，避免线程复用时串入后续请求。
 */
public final class CurrentRequestHolder {

    private static final ThreadLocal<CurrentRequest> HOLDER = new ThreadLocal<>();

    private CurrentRequestHolder() {
    }

    /**
     * 安装当前请求。
     *
     * @param request 当前请求
     */
    public static void set(@NotNull CurrentRequest request) {
        HOLDER.set(request);
    }

    /**
     * 获取当前请求。
     *
     * @return 当前请求；当前线程没有进入请求处理时返回 null
     */
    public static @Nullable CurrentRequest currentOrNull() {
        return HOLDER.get();
    }

    /**
     * 获取当前请求。
     *
     * @return 当前请求
     * @throws IllegalStateException 当前线程没有已安装请求时抛出
     */
    public static @NotNull CurrentRequest current() {
        CurrentRequest request = HOLDER.get();
        if (request == null) {
            throw new IllegalStateException("当前线程没有已安装请求");
        }
        return request;
    }

    /**
     * 清理当前请求。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
