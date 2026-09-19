package com.sca4d.web.realtime.sse;

import com.sca4d.core.api.authentication.Session;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 使用已经认证的 Session 建立由共享索引路由的 Server-Sent Events 连接。 */
public interface WebRealtimeSseConnector extends AutoCloseable {

    /**
     * 建立自动订阅当前 Account、Subject、Principal、Session 和 Connection 的 SSE 连接。
     *
     * @param session 已认证 Session
     * @return 已登记共享索引并开始串行写出的 SseEmitter
     */
    @CheckReturnValue
    @NotNull
    SseEmitter connect(@NotNull Session session);

    /** 关闭当前进程的全部 SSE 连接、心跳和写出任务。 */
    @Override
    void close();
}
