package com.sca4d.web.realtime.connection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sca4d.core.api.connection.ConnectionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

/** 验证本地节点传输只把消息交给当前 JVM 持有的连接。 */
class LocalNodeRouterTest {

    @Test
    @DisplayName("Delivers Current Node Connections Without RabbitMQ")
    void deliversCurrentNodeConnectionsWithoutRabbitMq() {
        LocalConnectionRegistry connections = mock(LocalConnectionRegistry.class);
        LocalNodeRouter transport = new LocalNodeRouter(connections);
        ConnectionId connectionId = new ConnectionId("connection-1");
        BrowserWrite delivery = delivery();

        transport.send(transport.nodeId(), List.of(connectionId), delivery);

        verify(connections).deliver(connectionId, delivery);
    }

    @Test
    @DisplayName("Ignores Leases Owned By Another Node")
    void ignoresLeasesOwnedByAnotherNode() {
        LocalConnectionRegistry connections = mock(LocalConnectionRegistry.class);
        LocalNodeRouter transport = new LocalNodeRouter(connections);

        transport.send("another-node", List.of(new ConnectionId("connection-1")), delivery());

        verifyNoInteractions(connections);
    }

    /** 创建不要求写出后关闭连接的浏览器投递。 */
    private static BrowserWrite delivery() {
        return new BrowserWrite(
                new BrowserEnvelope(
                        "test.changed",
                        1,
                        Instant.parse("2026-07-30T00:00:00Z"),
                        JsonNodeFactory.instance.objectNode()),
                false);
    }
}
