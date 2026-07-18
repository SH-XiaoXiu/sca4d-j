package com.sca4d.config.properties;

import com.sca4d.file.storage.FileStorageProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;

/** 文件上传、访问、存储选择和后台清理配置。 */
@ConfigurationProperties(prefix = "sca4dj.file")
public class Sca4djFileProperties {

    private boolean enabled = true;
    private FileStorageProvider storage = FileStorageProvider.MINIO;
    private DataSize maxFileSize = DataSize.ofMegabytes(20);
    private Duration uploadTtl = Duration.ofMinutes(10);
    private Duration readyTtl = Duration.ofHours(24);
    private Duration accessTtl = Duration.ofMinutes(5);
    private Duration grantTtl = Duration.ofHours(24);
    private Duration cleanupInterval = Duration.ofMinutes(10);
    private Duration cleanupLease = Duration.ofMinutes(30);
    private int cleanupBatchSize = 100;
    private String grantSecret = "sca4dj-local-file-grant-secret-change-me";
    private Path localRoot = Path.of("./file-data");

    /** @return 是否注册文件上传和访问服务 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled 是否注册可用的文件服务 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 新文件默认写入的存储提供方 */
    public @NotNull FileStorageProvider getStorage() {
        return storage;
    }

    /** @param storage 新文件默认写入的存储提供方 */
    public void setStorage(@NotNull FileStorageProvider storage) {
        this.storage = storage;
    }

    /** @return 单文件允许上传的最大字节数 */
    public @NotNull DataSize getMaxFileSize() {
        return maxFileSize;
    }

    /** @param maxFileSize 单文件允许上传的最大字节数 */
    public void setMaxFileSize(@NotNull DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    /** @return 客户端完成临时对象上传的有效期 */
    public @NotNull Duration getUploadTtl() {
        return uploadTtl;
    }

    /** @param uploadTtl 客户端完成临时对象上传的有效期 */
    public void setUploadTtl(@NotNull Duration uploadTtl) {
        this.uploadTtl = uploadTtl;
    }

    /** @return 已上传但未被业务消费的文件保留时间 */
    public @NotNull Duration getReadyTtl() {
        return readyTtl;
    }

    /** @param readyTtl 已上传但未被业务消费的文件保留时间 */
    public void setReadyTtl(@NotNull Duration readyTtl) {
        this.readyTtl = readyTtl;
    }

    /** @return 文件读取地址或授权的有效期 */
    public @NotNull Duration getAccessTtl() {
        return accessTtl;
    }

    /** @param accessTtl 文件读取地址或授权的有效期 */
    public void setAccessTtl(@NotNull Duration accessTtl) {
        this.accessTtl = accessTtl;
    }

    /** @return 上传文件绑定业务资源的授权有效期 */
    public @NotNull Duration getGrantTtl() {
        return grantTtl;
    }

    /** @param grantTtl 上传文件绑定业务资源的授权有效期 */
    public void setGrantTtl(@NotNull Duration grantTtl) {
        this.grantTtl = grantTtl;
    }

    /** @return 当前定时实现两轮文件清理之间的间隔 */
    public @NotNull Duration getCleanupInterval() {
        return cleanupInterval;
    }

    /** @param cleanupInterval 当前定时实现两轮文件清理之间的间隔 */
    public void setCleanupInterval(@NotNull Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    /** @return 删除处理节点失联后允许其他节点重新抢占的等待时间 */
    public @NotNull Duration getCleanupLease() {
        return cleanupLease;
    }

    /**
     * 设置文件删除处理权的有效期。
     *
     * @param cleanupLease 正数租约时长；对象删除必须在该时间内完成或由后续清理轮次重试
     * @throws IllegalArgumentException 租约不是正数时抛出
     */
    public void setCleanupLease(@NotNull Duration cleanupLease) {
        if (cleanupLease.isZero() || cleanupLease.isNegative()) {
            throw new IllegalArgumentException("文件清理租约必须大于 0");
        }
        this.cleanupLease = cleanupLease;
    }

    /** @return 单个节点每轮最多扫描和抢占的文件数 */
    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    /**
     * 设置单个节点每轮最多扫描和抢占的文件数。
     *
     * @param cleanupBatchSize 正整数批次上限
     * @throws IllegalArgumentException 批次上限不是正数时抛出
     */
    public void setCleanupBatchSize(int cleanupBatchSize) {
        if (cleanupBatchSize <= 0) {
            throw new IllegalArgumentException("文件清理批次大小必须大于 0");
        }
        this.cleanupBatchSize = cleanupBatchSize;
    }

    /** @return 文件上传消费与读取授权使用的签名密钥 */
    public @NotNull String getGrantSecret() {
        return grantSecret;
    }

    /** @param grantSecret 文件上传消费与读取授权使用的签名密钥 */
    public void setGrantSecret(@NotNull String grantSecret) {
        this.grantSecret = grantSecret;
    }

    /** @return LOCAL 存储提供方使用的对象根目录 */
    public @NotNull Path getLocalRoot() {
        return localRoot;
    }

    /** @param localRoot LOCAL 存储提供方使用的对象根目录 */
    public void setLocalRoot(@NotNull Path localRoot) {
        this.localRoot = localRoot;
    }
}
