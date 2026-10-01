package com.sca4d.iot.emqx.config;

import static com.sca4d.core.api.name.NameFormat.COLON;

import com.sca4d.core.api.name.Key;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.UUID;

/** 保存具体应用连接 EMQX MQTT 和 Management API 所选择的运行参数。 */
@ConfigurationProperties("sca4dj.iot.emqx")
public class EmqxProperties {

    private boolean enabled;

    private String host = "localhost";

    private int port = 1884;

    private boolean tls;

    private @Nullable String clientId;

    private @Nullable String username;

    private @Nullable String password;

    private String sharedSubscriptionGroup = "sca4dj-iot";

    private Duration operationTimeout = Duration.ofSeconds(10);

    private int inboundWorkerCount = 4;

    private int inboundQueueCapacity = 64;

    private int inboundMaximumAttempts = 3;

    private Duration inboundRetryDelay = Duration.ofSeconds(5);

    private @Nullable String managementBaseUrl;

    private @Nullable String managementApiKey;

    private @Nullable String managementSecret;

    private Duration reconciliationInterval = Duration.ofMinutes(10);

    private int managementPageSize = 100;

    /** @return 当前实例是否启用 EMQX 接入 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用 MQTT 消费、发布和管理 API */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return EMQX 内部 MQTT listener 主机 */
    public String getHost() {
        return host;
    }

    /** @param host EMQX 内部 MQTT listener 主机 */
    public void setHost(String host) {
        this.host = host;
    }

    /** @return EMQX 内部 MQTT listener 端口 */
    public int getPort() {
        return port;
    }

    /** @param port EMQX 内部 MQTT listener 端口 */
    public void setPort(int port) {
        this.port = port;
    }

    /** @return MQTT 内部连接是否使用系统默认 TLS trust store */
    public boolean isTls() {
        return tls;
    }

    /** @param tls 是否启用 MQTT TLS */
    public void setTls(boolean tls) {
        this.tls = tls;
    }

    /** @return 具体实例全局唯一 MQTT clientId；自动配置完成前可以为空 */
    public @Nullable String getClientId() {
        return clientId;
    }

    /** @param clientId 具体实例全局唯一 MQTT clientId */
    public void setClientId(@Nullable String clientId) {
        this.clientId = clientId;
    }

    /**
     * 为未显式配置 Client ID 的当前应用实例生成带 App 隔离名称的值。
     *
     */
    void initializeClientId() {
        if (clientId == null || clientId.isBlank()) {
            clientId = Key.of(TreeCode.ofColon("IOT:EMQX:CLIENT"), UUID.randomUUID().toString()).format(COLON);
        }
    }

    /** @return 内部 listener 可选用户名 */
    public @Nullable String getUsername() {
        return username;
    }

    /** @param username 内部 listener 可选用户名 */
    public void setUsername(@Nullable String username) {
        this.username = username;
    }

    /** @return 内部 listener 可选密码；只使用 username 作为 ACL 身份时可以为空 */
    public @Nullable String getPassword() {
        return password;
    }

    /** @param password 内部 listener 可选密码 */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    /** @return MQTT 共享订阅组名，不包含 `$share/` */
    public String getSharedSubscriptionGroup() {
        return sharedSubscriptionGroup;
    }

    /** @param sharedSubscriptionGroup MQTT 共享订阅组名 */
    public void setSharedSubscriptionGroup(String sharedSubscriptionGroup) {
        this.sharedSubscriptionGroup = sharedSubscriptionGroup;
    }

    /** @return MQTT 操作以及 Management API 连接和读取超时 */
    public Duration getOperationTimeout() {
        return operationTimeout;
    }

    /** @param operationTimeout MQTT 操作以及 Management API 连接和读取超时 */
    public void setOperationTimeout(Duration operationTimeout) {
        this.operationTimeout = operationTimeout;
    }

    /** @return MQTT 上行保持同一 Topic 顺序的串行分片数量 */
    public int getInboundWorkerCount() {
        return inboundWorkerCount;
    }

    /** @param inboundWorkerCount MQTT 上行串行分片数量 */
    public void setInboundWorkerCount(int inboundWorkerCount) {
        this.inboundWorkerCount = inboundWorkerCount;
    }

    /** @return 每个 MQTT 上行分片允许等待的消息数量 */
    public int getInboundQueueCapacity() {
        return inboundQueueCapacity;
    }

    /** @param inboundQueueCapacity 每个 MQTT 上行分片的固定等待容量 */
    public void setInboundQueueCapacity(int inboundQueueCapacity) {
        this.inboundQueueCapacity = inboundQueueCapacity;
    }

    /** @return 未明确拒绝的 MQTT 上行最大处理次数 */
    public int getInboundMaximumAttempts() {
        return inboundMaximumAttempts;
    }

    /** @param inboundMaximumAttempts 未明确拒绝的 MQTT 上行最大处理次数 */
    public void setInboundMaximumAttempts(int inboundMaximumAttempts) {
        this.inboundMaximumAttempts = inboundMaximumAttempts;
    }

    /** @return MQTT 上行相邻处理尝试的等待时间 */
    public Duration getInboundRetryDelay() {
        return inboundRetryDelay;
    }

    /** @param inboundRetryDelay MQTT 上行相邻处理尝试的等待时间 */
    public void setInboundRetryDelay(Duration inboundRetryDelay) {
        this.inboundRetryDelay = inboundRetryDelay;
    }

    /**
     * CONNECT 声明的 MQTT 5 Receive Maximum。
     *
     * <p>全部未确认消息可能属于同一个 Topic。每个分片除等待队列外还会处理一条消息，因此当前值等于
     * 等待容量时，ACK 后立即补发的消息仍有一个队列位置。
     *
     * @return 当前 worker 和每分片队列共同允许的未确认消息数
     */
    @CheckReturnValue
    @Contract(pure = true)
    public int inboundReceiveMaximum() {
        return inboundQueueCapacity;
    }

    /** @return EMQX Management API base URL */
    public @Nullable String getManagementBaseUrl() {
        return managementBaseUrl;
    }

    /** @param managementBaseUrl EMQX Management API base URL */
    public void setManagementBaseUrl(@Nullable String managementBaseUrl) {
        this.managementBaseUrl = managementBaseUrl;
    }

    /** @return EMQX Management API key */
    public @Nullable String getManagementApiKey() {
        return managementApiKey;
    }

    /** @param managementApiKey EMQX Management API key */
    public void setManagementApiKey(@Nullable String managementApiKey) {
        this.managementApiKey = managementApiKey;
    }

    /** @return EMQX Management API secret */
    public @Nullable String getManagementSecret() {
        return managementSecret;
    }

    /** @param managementSecret EMQX Management API secret */
    public void setManagementSecret(@Nullable String managementSecret) {
        this.managementSecret = managementSecret;
    }

    /** @return 使用 Management API 修正 Connection 投影的间隔 */
    public Duration getReconciliationInterval() {
        return reconciliationInterval;
    }

    /** @param reconciliationInterval 使用 Management API 修正 Connection 投影的正数间隔 */
    public void setReconciliationInterval(Duration reconciliationInterval) {
        this.reconciliationInterval = reconciliationInterval;
    }

    /** @return Management API 单页 client 数量 */
    public int getManagementPageSize() {
        return managementPageSize;
    }

    /** @param managementPageSize Management API 单页 client 数量 */
    public void setManagementPageSize(int managementPageSize) {
        this.managementPageSize = managementPageSize;
    }

    /** 只在启用 EMQX 时校验所有连接和管理 API 必需参数。 */
    public void validate() {
        if (!enabled)
            return;
        requireText(host, "sca4dj.iot.emqx.host");
        requireText(clientId, "sca4dj.iot.emqx.client-id");
        requireText(sharedSubscriptionGroup, "sca4dj.iot.emqx.shared-subscription-group");
        requireText(managementBaseUrl, "sca4dj.iot.emqx.management-base-url");
        requireText(managementApiKey, "sca4dj.iot.emqx.management-api-key");
        requireText(managementSecret, "sca4dj.iot.emqx.management-secret");
        if (port < 1 || port > 65535)
            throw new IllegalStateException("sca4dj.iot.emqx.port 不合法");
        if (managementPageSize < 1 || managementPageSize > 1000) {
            throw new IllegalStateException("sca4dj.iot.emqx.management-page-size 必须在 1 到 1000 之间");
        }
        requirePositive(operationTimeout, "sca4dj.iot.emqx.operation-timeout");
        if (inboundWorkerCount < 1 || inboundWorkerCount > 32) {
            throw new IllegalStateException("sca4dj.iot.emqx.inbound-worker-count 必须在 1 至 32 之间");
        }
        if (inboundQueueCapacity < 1 || inboundQueueCapacity > 1024) {
            throw new IllegalStateException("sca4dj.iot.emqx.inbound-queue-capacity 必须在 1 至 1024 之间");
        }
        if (inboundMaximumAttempts < 1 || inboundMaximumAttempts > 20) {
            throw new IllegalStateException("sca4dj.iot.emqx.inbound-maximum-attempts 必须在 1 至 20 之间");
        }
        requirePositive(inboundRetryDelay, "sca4dj.iot.emqx.inbound-retry-delay");
        requirePositive(reconciliationInterval, "sca4dj.iot.emqx.reconciliation-interval");
        if (password != null && username == null) {
            throw new IllegalStateException("EMQX 内部 MQTT password 不能在 username 缺失时单独配置");
        }
    }

    /** 要求配置文本存在且不是空白。 */
    private static void requireText(@Nullable String value, String property) {
        if (value == null || value.isBlank())
            throw new IllegalStateException(property + " 必须显式配置");
    }

    /** 要求配置时长为正数。 */
    private static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(property + " 必须为正数时长");
        }
    }
}
