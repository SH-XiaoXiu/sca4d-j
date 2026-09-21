package com.sca4d.web.realtime.connection;

import com.sca4d.core.api.connection.ConnectionId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** 表示当前 JVM 无法迁移的一个 SSE 或 WebSocket 句柄。 */
@ApiStatus.Internal
public interface LocalConnection {

    /** @return 部署范围内唯一的连接标识 */
    @NotNull
    ConnectionId id();

    /** 把一次投递加入有界写出缓冲；缓冲已满时实现关闭连接。 */
    void offer(@NotNull BrowserWrite delivery);

    /** 关闭连接句柄并删除共享索引。 */
    void close();
}
