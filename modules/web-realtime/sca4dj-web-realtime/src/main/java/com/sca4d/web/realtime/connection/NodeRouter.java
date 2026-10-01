package com.sca4d.web.realtime.connection;

import com.sca4d.core.api.connection.ConnectionId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 标识当前 Web Realtime 节点，并把浏览器事件交给持有目标连接的节点。 */
@ApiStatus.Internal
public interface NodeRouter {

    /** @return 当前 JVM 登记连接时使用的唯一节点标识 */
    @NotNull
    String nodeId();

    /**
     * 把同一浏览器事件发送给指定节点持有的连接。
     *
     * @param destinationNodeId Redis 连接索引记录的目标节点
     * @param connectionIds 目标节点持有的连接
     * @param delivery 已转换为浏览器信封的投递内容
     */
    void send(
            @NotNull String destinationNodeId,
            @NotNull List<@NotNull ConnectionId> connectionIds,
            @NotNull BrowserWrite delivery);
}
