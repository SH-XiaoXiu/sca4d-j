package com.sca4d.web.realtime.config;

import com.sca4d.web.realtime.stomp.StompBrokerMode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 配置 Web Realtime 节点传输、租约、SSE、WebSocket 票据、帧限制和 Origin。 */
@ConfigurationProperties("sca4dj.web-realtime")
public class WebRealtimeProperties {

    private Duration leaseRenewInterval = Duration.ofSeconds(30);

    private Duration leaseDuration = Duration.ofSeconds(90);

    private final Node node = new Node();

    private int routingBatchSize = 500;

    private Duration nodeMessageTtl = Duration.ofSeconds(30);

    private Duration heartbeatInterval = Duration.ofSeconds(25);

    private Duration sseConnectionTimeout = Duration.ofMinutes(30);

    private int connectionQueueCapacity = 64;

    private Duration ticketTtl = Duration.ofSeconds(10);

    private Duration stompConnectTimeout = Duration.ofSeconds(10);

    private int inboundFrameMaxBytes = 64 * 1024;

    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

    private final Stomp stomp = new Stomp();

    /** 创建使用方案默认值的 Web Realtime 配置。 */
    public WebRealtimeProperties() {
    }

    /** @return 连接租约续期间隔 */
    public Duration getLeaseRenewInterval() {
        return leaseRenewInterval;
    }

    /** @param value 正数连接租约续期间隔 */
    public void setLeaseRenewInterval(Duration value) {
        leaseRenewInterval = positive(value, "连接租约续期间隔");
    }

    /** @return 连接租约有效时间 */
    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    /** @param value 正数连接租约有效时间 */
    public void setLeaseDuration(Duration value) {
        leaseDuration = positiveMillis(value, "连接租约有效时间");
    }

    /** @return 单实例本地投递或 RabbitMQ 跨节点投递 */
    public NodeRouterMode getNodeTransport() {
        return node.transport;
    }

    /** @param value `LOCAL` 或 `RABBIT` */
    public void setNodeTransport(NodeRouterMode value) {
        node.setTransport(value);
    }

    /** @return 节点投递资源配置 */
    public Node getNode() {
        return node;
    }

    /** @return 每条节点消息最多包含的 Connection 数 */
    public int getRoutingBatchSize() {
        return routingBatchSize;
    }

    /** @param value 正数节点路由批量 */
    public void setRoutingBatchSize(int value) {
        routingBatchSize = positive(value, "节点路由批量");
    }

    /** @return RabbitMQ 节点临时消息 TTL */
    public Duration getNodeMessageTtl() {
        return nodeMessageTtl;
    }

    /** @param value 正数节点消息 TTL */
    public void setNodeMessageTtl(Duration value) {
        nodeMessageTtl = positiveIntMillis(value, "节点消息 TTL");
    }

    /** @return SSE 心跳间隔 */
    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    /** @param value 正数 SSE 心跳间隔 */
    public void setHeartbeatInterval(Duration value) {
        heartbeatInterval = positiveMillis(value, "SSE 心跳间隔");
    }

    /** @return 单次 SSE 连接最长时间 */
    public Duration getSseConnectionTimeout() {
        return sseConnectionTimeout;
    }

    /** @param value 正数 SSE 连接最长时间 */
    public void setSseConnectionTimeout(Duration value) {
        sseConnectionTimeout = positiveMillis(value, "SSE 连接超时");
    }

    /** @return 每个连接的待写出消息容量 */
    public int getConnectionQueueCapacity() {
        return connectionQueueCapacity;
    }

    /** @param value 正数连接队列容量 */
    public void setConnectionQueueCapacity(int value) {
        connectionQueueCapacity = positive(value, "连接队列容量");
    }

    /** @return 一次性 WebSocket 票据有效时间 */
    public Duration getTicketTtl() {
        return ticketTtl;
    }

    /** @param value 正数票据有效时间 */
    public void setTicketTtl(Duration value) {
        ticketTtl = positiveMillis(value, "WebSocket 票据有效时间");
    }

    /** @return WebSocket 建立后等待 STOMP CONNECT 的时间 */
    public Duration getStompConnectTimeout() {
        return stompConnectTimeout;
    }

    /** @param value 正数 STOMP CONNECT 等待时间 */
    public void setStompConnectTimeout(Duration value) {
        stompConnectTimeout = positiveIntMillis(value, "STOMP CONNECT 等待时间");
    }

    /** @return 单个 STOMP 入站帧的最大字节数 */
    public int getInboundFrameMaxBytes() {
        return inboundFrameMaxBytes;
    }

    /** @param value 正数 STOMP 入站帧最大字节数 */
    public void setInboundFrameMaxBytes(int value) {
        inboundFrameMaxBytes = positive(value, "STOMP 入站帧大小");
    }

    /** @return 允许建立 WebSocket 的完整 Origin 列表 */
    public List<String> getAllowedOrigins() {
        return List.copyOf(allowedOrigins);
    }

    /** @param value 非空的完整 Origin 列表 */
    public void setAllowedOrigins(List<String> value) {
        if (value == null || value.isEmpty() || value.stream().anyMatch(item -> item == null || item.isBlank())) {
            throw new IllegalArgumentException("WebSocket allowed-origins 不能为空");
        }
        allowedOrigins = new ArrayList<>(value);
    }

    /** @return Spring STOMP broker 模式、写出限制和 relay 连接配置 */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring Boot must bind nested properties through this live final configuration object")
    public Stomp getStomp() {
        return stomp;
    }

    /** 校验时长是正数。 */
    private static Duration positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return value;
    }

    /** 校验整数是正数。 */
    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return value;
    }

    /** 校验时长转换为毫秒后仍是正数。 */
    private static Duration positiveMillis(Duration value, String name) {
        Duration checked = positive(value, name);
        try {
            long millis = checked.toMillis();
            if (millis < 1) {
                throw new IllegalArgumentException(name + "不能短于 1 毫秒");
            }
            return checked;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + "超过毫秒表示范围", exception);
        }
    }

    /** 校验时长可无损传给 Spring、RabbitMQ 或 Socket 的 int 毫秒参数。 */
    private static Duration positiveIntMillis(Duration value, String name) {
        Duration checked = positiveMillis(value, name);
        if (checked.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + "不能超过 " + Integer.MAX_VALUE + " 毫秒");
        }
        return checked;
    }

    /** 配置 Spring STOMP broker 模式、写出缓冲和 RabbitMQ STOMP relay。 */
    public static class Stomp {

        private StompBrokerMode mode = StompBrokerMode.NODE_ROUTED;

        private int sendBufferMaxBytes = 256 * 1024;

        private Duration sendTimeLimit = Duration.ofSeconds(10);

        private final Relay relay = new Relay();

        /** @return Spring STOMP broker 模式，默认由当前节点处理订阅 */
        public StompBrokerMode getMode() {
            return mode;
        }

        /** @param value `NODE_ROUTED` 或 `BROKER_RELAY` */
        public void setMode(StompBrokerMode value) {
            mode = Objects.requireNonNull(value, "STOMP broker 模式不能为空");
        }

        /** @return 每条 WebSocket 连接允许缓冲的最大待发送字节数 */
        public int getSendBufferMaxBytes() {
            return sendBufferMaxBytes;
        }

        /** @param value 正数待发送缓冲字节数 */
        public void setSendBufferMaxBytes(int value) {
            sendBufferMaxBytes = positive(value, "STOMP 发送缓冲大小");
        }

        /** @return 单次 WebSocket 发送允许占用的最长时间 */
        public Duration getSendTimeLimit() {
            return sendTimeLimit;
        }

        /** @param value 正数 WebSocket 发送时间限制 */
        public void setSendTimeLimit(Duration value) {
            sendTimeLimit = positiveIntMillis(value, "STOMP 发送时间限制");
        }

        /** @return RabbitMQ STOMP plugin relay 连接配置 */
        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Spring Boot must bind nested properties through this live final configuration object")
        public Relay getRelay() {
            return relay;
        }
    }

    /** 配置单实例或 RabbitMQ 节点投递方式。 */
    public static class Node {

        private NodeRouterMode transport = NodeRouterMode.LOCAL;

        /** @return 节点使用的本地或 RabbitMQ 投递模式 */
        public NodeRouterMode getTransport() {
            return transport;
        }

        /** @param transport 节点投递模式 */
        public void setTransport(NodeRouterMode transport) {
            this.transport = Objects.requireNonNull(transport, "节点传输模式不能为空");
        }
    }

    /** 配置 `BROKER_RELAY` 模式连接 RabbitMQ STOMP plugin 的地址与凭据。 */
    public static class Relay {

        private String host = "localhost";

        private int port = 61613;

        private String clientLogin = "guest";

        private String clientPasscode = "guest";

        private String systemLogin = "guest";

        private String systemPasscode = "guest";

        private String virtualHost = "/";

        private Duration startupConnectTimeout = Duration.ofSeconds(3);

        /** @return RabbitMQ STOMP plugin 主机 */
        public String getHost() {
            return host;
        }

        /** @param value 非空白 RabbitMQ 主机 */
        public void setHost(String value) {
            host = text(value, "STOMP relay 主机");
        }

        /** @return RabbitMQ STOMP plugin TCP 端口 */
        public int getPort() {
            return port;
        }

        /** @param value 1 到 65535 的 TCP 端口 */
        public void setPort(int value) {
            if (value < 1 || value > 65_535) {
                throw new IllegalArgumentException("STOMP relay 端口必须在 1 到 65535 之间");
            }
            port = value;
        }

        /** @return 浏览器 relay 连接使用的登录名 */
        public String getClientLogin() {
            return clientLogin;
        }

        /** @param value 非空白浏览器 relay 登录名 */
        public void setClientLogin(String value) {
            clientLogin = text(value, "STOMP relay client login");
        }

        /** @return 浏览器 relay 连接使用的口令 */
        public String getClientPasscode() {
            return clientPasscode;
        }

        /** @param value 非空浏览器 relay 口令 */
        public void setClientPasscode(String value) {
            clientPasscode = secret(value, "STOMP relay client passcode");
        }

        /** @return Spring 系统 relay 连接使用的登录名 */
        public String getSystemLogin() {
            return systemLogin;
        }

        /** @param value 非空白 Spring 系统 relay 登录名 */
        public void setSystemLogin(String value) {
            systemLogin = text(value, "STOMP relay system login");
        }

        /** @return Spring 系统 relay 连接使用的口令 */
        public String getSystemPasscode() {
            return systemPasscode;
        }

        /** @param value 非空 Spring 系统 relay 口令 */
        public void setSystemPasscode(String value) {
            systemPasscode = secret(value, "STOMP relay system passcode");
        }

        /** @return RabbitMQ virtual host */
        public String getVirtualHost() {
            return virtualHost;
        }

        /** @param value 非空白 RabbitMQ virtual host */
        public void setVirtualHost(String value) {
            virtualHost = text(value, "STOMP relay virtual host");
        }

        /** @return 启动时探测 STOMP relay TCP 端口的超时时间 */
        public Duration getStartupConnectTimeout() {
            return startupConnectTimeout;
        }

        /** @param value 正数启动探测超时时间 */
        public void setStartupConnectTimeout(Duration value) {
            startupConnectTimeout = positiveIntMillis(value, "STOMP relay 启动连接超时");
        }

        /** 校验非空白普通配置文本。 */
        private static String text(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + "不能为空");
            }
            return value;
        }

        /** 校验不在异常中回显的非空凭据。 */
        private static String secret(String value, String name) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException(name + "不能为空");
            }
            return value;
        }
    }
}
