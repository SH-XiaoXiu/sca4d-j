package com.sca4d.config.properties;

import com.sca4d.common.audit.AuditMode;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 审计启用状态、记录时机、载荷长度和敏感字段配置。 */
@ConfigurationProperties(prefix = "sca4dj.audit")
public class Sca4djAuditProperties {

    private boolean enabled = true;
    private boolean recordSuccess = true;
    private boolean recordFailure = true;
    private AuditMode mode = AuditMode.ASYNC;
    private Async async = new Async();
    private int maxPayloadLength = 64000;
    private List<String> sensitiveFields = new ArrayList<>(List.of("password", "token", "secret", "salt", "authorization"));

    /** @return 是否启用声明式审计切面 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否启用声明式审计切面 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 是否记录成功完成的业务操作 */
    public boolean isRecordSuccess() {
        return recordSuccess;
    }

    /** @param recordSuccess 是否记录成功完成的业务操作 */
    public void setRecordSuccess(boolean recordSuccess) {
        this.recordSuccess = recordSuccess;
    }

    /** @return 是否记录抛出异常的业务操作 */
    public boolean isRecordFailure() {
        return recordFailure;
    }

    /** @param recordFailure 是否记录抛出异常的业务操作 */
    public void setRecordFailure(boolean recordFailure) {
        this.recordFailure = recordFailure;
    }

    /** @return 全局审计执行模式 */
    public @NotNull AuditMode getMode() {
        return mode;
    }

    /** @param mode 全局审计执行模式 */
    public void setMode(@NotNull AuditMode mode) {
        this.mode = mode;
    }

    /** @return 异步审计线程池配置 */
    public @NotNull Async getAsync() {
        return async;
    }

    /** @param async 异步审计线程池配置 */
    public void setAsync(@NotNull Async async) {
        this.async = async;
    }

    /** @return 触发业务载荷截断的审计详情字符数，非正数表示不截断 */
    public int getMaxPayloadLength() {
        return maxPayloadLength;
    }

    /** @param maxPayloadLength 触发业务载荷截断的审计详情字符数，非正数表示不截断 */
    public void setMaxPayloadLength(int maxPayloadLength) {
        this.maxPayloadLength = maxPayloadLength;
    }

    /** @return 用于递归匹配审计载荷属性名的敏感字段片段副本 */
    public @NotNull List<String> getSensitiveFields() {
        return List.copyOf(sensitiveFields);
    }

    /** @param sensitiveFields 用于不区分大小写包含匹配的敏感字段片段 */
    public void setSensitiveFields(@NotNull List<String> sensitiveFields) {
        this.sensitiveFields = new ArrayList<>(sensitiveFields);
    }

    /** 异步审计线程池配置。 */
    public static class Async {

        private int workerCount = 2;
        private int queueCapacity = 1024;
        private Duration shutdownAwait = Duration.ofSeconds(10);

        /** @return 固定审计工作线程数 */
        public int getWorkerCount() {
            return workerCount;
        }

        /** @param workerCount 固定审计工作线程数 */
        public void setWorkerCount(int workerCount) {
            this.workerCount = workerCount;
        }

        /** @return 等待执行的审计任务上限 */
        public int getQueueCapacity() {
            return queueCapacity;
        }

        /** @param queueCapacity 等待执行的审计任务上限 */
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        /** @return 应用关闭时等待审计任务完成的最长时间 */
        public @NotNull Duration getShutdownAwait() {
            return shutdownAwait;
        }

        /** @param shutdownAwait 应用关闭时等待审计任务完成的最长时间 */
        public void setShutdownAwait(@NotNull Duration shutdownAwait) {
            this.shutdownAwait = shutdownAwait;
        }
    }
}
