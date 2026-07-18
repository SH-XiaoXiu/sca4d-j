package com.sca4d.config.properties;

import com.sca4d.common.messaging.MessageRetryPolicy;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 消息发布配置。
 */
@ConfigurationProperties(prefix = "sca4dj.messaging")
public class Sca4djMessagingProperties {

    private boolean enabled = true;
    private String source = "sca4d-j";
    private Rabbit rabbit = new Rabbit();
    private Outbox outbox = new Outbox();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public @NotNull String getSource() {
        return source;
    }

    public void setSource(@NotNull String source) {
        this.source = source;
    }

    public @NotNull Rabbit getRabbit() {
        return rabbit;
    }

    public void setRabbit(@NotNull Rabbit rabbit) {
        this.rabbit = rabbit;
    }

    public @NotNull Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(@NotNull Outbox outbox) {
        this.outbox = outbox;
    }

    /**
     * 创建默认发布重试策略。
     *
     * @return 默认发布重试策略
     */
    public @NotNull MessageRetryPolicy defaultRetryPolicy() {
        return new MessageRetryPolicy()
                .setMaxAttempts(outbox.getMaxAttempts())
                .setInitialBackoff(outbox.getInitialBackoff())
                .setMaxBackoff(outbox.getMaxBackoff());
    }

    /**
     * RabbitMQ 配置。
     */
    public static class Rabbit {

        private Duration confirmTimeout = Duration.ofSeconds(5);
        private boolean delayedExchangeEnabled = false;
        private boolean declareDefaultTopology = true;
        private String defaultExchange = "sca4dj.event";
        private String defaultQueue = "sca4dj.event.default";
        private String defaultRoutingKey = "#";
        private String deadLetterExchange = "sca4dj.event.dlx";
        private String deadLetterQueue = "sca4dj.event.dlq";
        private String deadLetterRoutingKey = "dead";

        public @NotNull Duration getConfirmTimeout() {
            return confirmTimeout;
        }

        public void setConfirmTimeout(@NotNull Duration confirmTimeout) {
            this.confirmTimeout = confirmTimeout;
        }

        public boolean isDelayedExchangeEnabled() {
            return delayedExchangeEnabled;
        }

        public void setDelayedExchangeEnabled(boolean delayedExchangeEnabled) {
            this.delayedExchangeEnabled = delayedExchangeEnabled;
        }

        public boolean isDeclareDefaultTopology() {
            return declareDefaultTopology;
        }

        public void setDeclareDefaultTopology(boolean declareDefaultTopology) {
            this.declareDefaultTopology = declareDefaultTopology;
        }

        public @NotNull String getDefaultExchange() {
            return defaultExchange;
        }

        public void setDefaultExchange(@NotNull String defaultExchange) {
            this.defaultExchange = defaultExchange;
        }

        public @NotNull String getDefaultQueue() {
            return defaultQueue;
        }

        public void setDefaultQueue(@NotNull String defaultQueue) {
            this.defaultQueue = defaultQueue;
        }

        public @NotNull String getDefaultRoutingKey() {
            return defaultRoutingKey;
        }

        public void setDefaultRoutingKey(@NotNull String defaultRoutingKey) {
            this.defaultRoutingKey = defaultRoutingKey;
        }

        public @NotNull String getDeadLetterExchange() {
            return deadLetterExchange;
        }

        public void setDeadLetterExchange(@NotNull String deadLetterExchange) {
            this.deadLetterExchange = deadLetterExchange;
        }

        public @NotNull String getDeadLetterQueue() {
            return deadLetterQueue;
        }

        public void setDeadLetterQueue(@NotNull String deadLetterQueue) {
            this.deadLetterQueue = deadLetterQueue;
        }

        public @NotNull String getDeadLetterRoutingKey() {
            return deadLetterRoutingKey;
        }

        public void setDeadLetterRoutingKey(@NotNull String deadLetterRoutingKey) {
            this.deadLetterRoutingKey = deadLetterRoutingKey;
        }
    }

    /**
     * Outbox 配置。
     */
    public static class Outbox {

        private boolean enabled = true;
        private Duration pollInterval = Duration.ofSeconds(3);
        private int batchSize = 50;
        private int maxAttempts = 10;
        private Duration initialBackoff = Duration.ofSeconds(5);
        private Duration maxBackoff = Duration.ofMinutes(5);
        private Duration sendingTimeout = Duration.ofMinutes(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public @NotNull Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(@NotNull Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public @NotNull Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(@NotNull Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public @NotNull Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(@NotNull Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }

        public @NotNull Duration getSendingTimeout() {
            return sendingTimeout;
        }

        public void setSendingTimeout(@NotNull Duration sendingTimeout) {
            this.sendingTimeout = sendingTimeout;
        }
    }
}
