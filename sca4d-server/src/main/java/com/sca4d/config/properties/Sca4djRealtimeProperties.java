package com.sca4d.config.properties;

import com.sca4d.common.realtime.RealtimeRoutes;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 实时通信配置。
 */
@ConfigurationProperties(prefix = "sca4dj.realtime")
public class Sca4djRealtimeProperties {

    private boolean enabled = true;
    private WebSocket websocket = new WebSocket();
    private Sse sse = new Sse();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @NotNull WebSocket getWebsocket() {
        return websocket;
    }

    public void setWebsocket(@NotNull WebSocket websocket) {
        this.websocket = websocket;
    }

    public @NotNull Sse getSse() {
        return sse;
    }

    public void setSse(@NotNull Sse sse) {
        this.sse = sse;
    }

    /**
     * WebSocket / STOMP 配置。
     */
    public static class WebSocket {

        private boolean enabled = true;
        private String endpoint = RealtimeRoutes.WS_ENDPOINT;
        private boolean sockJsEnabled = true;
        private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));
        private List<String> applicationDestinationPrefixes = new ArrayList<>(List.of(RealtimeRoutes.APP_PREFIX));
        private List<String> brokerDestinationPrefixes = new ArrayList<>(List.of(RealtimeRoutes.TOPIC_PREFIX, RealtimeRoutes.QUEUE_PREFIX));
        private String userDestinationPrefix = RealtimeRoutes.USER_PREFIX;
        private String userDestinationBroadcast = RealtimeRoutes.USER_DESTINATION_BROADCAST;
        private String userRegistryBroadcast = RealtimeRoutes.USER_REGISTRY_BROADCAST;
        private BrokerRelay brokerRelay = new BrokerRelay();
        private FrameRateLimit frameRateLimit = new FrameRateLimit();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public @NotNull String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(@NotNull String endpoint) {
            this.endpoint = endpoint;
        }

        public boolean isSockJsEnabled() {
            return sockJsEnabled;
        }

        public void setSockJsEnabled(boolean sockJsEnabled) {
            this.sockJsEnabled = sockJsEnabled;
        }

        public @NotNull List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(@NotNull List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public @NotNull List<String> getApplicationDestinationPrefixes() {
            return applicationDestinationPrefixes;
        }

        public void setApplicationDestinationPrefixes(@NotNull List<String> applicationDestinationPrefixes) {
            this.applicationDestinationPrefixes = applicationDestinationPrefixes;
        }

        public @NotNull List<String> getBrokerDestinationPrefixes() {
            return brokerDestinationPrefixes;
        }

        public void setBrokerDestinationPrefixes(@NotNull List<String> brokerDestinationPrefixes) {
            this.brokerDestinationPrefixes = brokerDestinationPrefixes;
        }

        public @NotNull String getUserDestinationPrefix() {
            return userDestinationPrefix;
        }

        public void setUserDestinationPrefix(@NotNull String userDestinationPrefix) {
            this.userDestinationPrefix = userDestinationPrefix;
        }

        public @NotNull String getUserDestinationBroadcast() {
            return userDestinationBroadcast;
        }

        public void setUserDestinationBroadcast(@NotNull String userDestinationBroadcast) {
            this.userDestinationBroadcast = userDestinationBroadcast;
        }

        public @NotNull String getUserRegistryBroadcast() {
            return userRegistryBroadcast;
        }

        public void setUserRegistryBroadcast(@NotNull String userRegistryBroadcast) {
            this.userRegistryBroadcast = userRegistryBroadcast;
        }

        public @NotNull BrokerRelay getBrokerRelay() {
            return brokerRelay;
        }

        public void setBrokerRelay(@NotNull BrokerRelay brokerRelay) {
            this.brokerRelay = brokerRelay;
        }

        public @NotNull FrameRateLimit getFrameRateLimit() {
            return frameRateLimit;
        }

        public void setFrameRateLimit(@NotNull FrameRateLimit frameRateLimit) {
            this.frameRateLimit = frameRateLimit;
        }
    }

    /**
     * STOMP broker relay 配置。
     */
    public static class BrokerRelay {

        private boolean enabled = true;
        private String host = "localhost";
        private int port = 61613;
        private String clientLogin = "guest";
        private String clientPasscode = "guest";
        private String systemLogin = "guest";
        private String systemPasscode = "guest";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public @NotNull String getHost() {
            return host;
        }

        public void setHost(@NotNull String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public @NotNull String getClientLogin() {
            return clientLogin;
        }

        public void setClientLogin(@NotNull String clientLogin) {
            this.clientLogin = clientLogin;
        }

        public @NotNull String getClientPasscode() {
            return clientPasscode;
        }

        public void setClientPasscode(@NotNull String clientPasscode) {
            this.clientPasscode = clientPasscode;
        }

        public @NotNull String getSystemLogin() {
            return systemLogin;
        }

        public void setSystemLogin(@NotNull String systemLogin) {
            this.systemLogin = systemLogin;
        }

        public @NotNull String getSystemPasscode() {
            return systemPasscode;
        }

        public void setSystemPasscode(@NotNull String systemPasscode) {
            this.systemPasscode = systemPasscode;
        }
    }

    /**
     * STOMP 帧级限流配置。
     */
    public static class FrameRateLimit {

        private boolean enabled = true;
        private Duration window = Duration.ofSeconds(10);
        private long connectCapacity = 20;
        private long subscribeCapacity = 120;
        private long sendCapacity = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public @NotNull Duration getWindow() {
            return window;
        }

        public void setWindow(@NotNull Duration window) {
            this.window = window;
        }

        public long getConnectCapacity() {
            return connectCapacity;
        }

        public void setConnectCapacity(long connectCapacity) {
            this.connectCapacity = connectCapacity;
        }

        public long getSubscribeCapacity() {
            return subscribeCapacity;
        }

        public void setSubscribeCapacity(long subscribeCapacity) {
            this.subscribeCapacity = subscribeCapacity;
        }

        public long getSendCapacity() {
            return sendCapacity;
        }

        public void setSendCapacity(long sendCapacity) {
            this.sendCapacity = sendCapacity;
        }
    }

    /**
     * SSE 配置。
     */
    public static class Sse {

        private boolean enabled = true;
        private String exchange = "sca4dj.realtime.sse";
        private Duration timeout = Duration.ofMinutes(30);
        private Duration heartbeatInterval = Duration.ofSeconds(25);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public @NotNull String getExchange() {
            return exchange;
        }

        public void setExchange(@NotNull String exchange) {
            this.exchange = exchange;
        }

        public @NotNull Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(@NotNull Duration timeout) {
            this.timeout = timeout;
        }

        public @NotNull Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        public void setHeartbeatInterval(@NotNull Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }
    }
}
