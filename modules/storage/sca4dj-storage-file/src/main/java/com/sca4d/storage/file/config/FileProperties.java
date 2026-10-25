package com.sca4d.storage.file.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * 配置默认存储实现、公开传输地址和文件清理过程。
 */
@ConfigurationProperties("sca4dj.storage")
public class FileProperties {
    private final Backend backend = new Backend();

    private @NotNull URI transferBaseUrl = URI.create("http://localhost:8080");

    private @NotNull Duration cleanupInterval = Duration.ofMinutes(10);

    private @NotNull Duration deleteLease = Duration.ofMinutes(2);

    private @NotNull Duration deleteRetryDelay = Duration.ofMinutes(10);

    /** 使用默认保留期、清理间隔和删除租约创建文件配置。 */
    public FileProperties() {
    }

    /**
     * 新文件使用的默认存储实现标识。
     * @return 默认存储实现标识
     */
    public @NotNull String getDefaultBackend() {
        return backend.defaultId;
    }

    /**
     * 设置新文件使用的默认存储实现。
     * @param defaultBackend 已注册的存储实现标识
     */
    public void setDefaultBackend(@NotNull String defaultBackend) {
        backend.setDefaultId(defaultBackend);
    }

    /** @return 存储 backend 资源配置 */
    public @NotNull Backend getBackend() {
        return backend;
    }

    /**
     * App 文件传输路由的公开根地址。
     * @return 绝对公开根地址
     */
    public @NotNull URI getTransferBaseUrl() {
        return transferBaseUrl;
    }

    /**
     * 设置 App 文件传输路由的公开根地址。
     *
     * @param transferBaseUrl 包含协议和主机的绝对地址
     * @throws IllegalArgumentException 地址没有协议或主机时抛出
     */
    public void setTransferBaseUrl(@NotNull URI transferBaseUrl) {
        URI checked = Objects.requireNonNull(transferBaseUrl, "文件传输公开地址不能为空");
        if (!checked.isAbsolute()
                || checked.getHost() == null
                || checked.getRawQuery() != null
                || checked.getRawFragment() != null) {
            throw new IllegalArgumentException("文件传输公开地址必须包含协议和主机");
        }
        this.transferBaseUrl = checked;
    }

    /**
     * 文件清理任务固定延迟。
     * @return 正数清理间隔
     */
    public @NotNull Duration getCleanupInterval() {
        return cleanupInterval;
    }

    /**
     * 设置文件清理任务固定延迟。
     * @param cleanupInterval 正数清理间隔
     */
    public void setCleanupInterval(@NotNull Duration cleanupInterval) {
        this.cleanupInterval = requirePositive(cleanupInterval, "文件清理间隔");
    }

    /**
     * 文件删除租约时间。
     * @return 正数租约时间
     */
    public @NotNull Duration getDeleteLease() {
        return deleteLease;
    }

    /**
     * 设置文件删除租约时间。
     * @param deleteLease 正数租约时间
     */
    public void setDeleteLease(@NotNull Duration deleteLease) {
        this.deleteLease = requirePositive(deleteLease, "文件删除租约");
    }

    /**
     * 删除失败后的重试间隔。
     * @return 正数重试间隔
     */
    public @NotNull Duration getDeleteRetryDelay() {
        return deleteRetryDelay;
    }

    /**
     * 设置删除失败后的重试间隔。
     * @param deleteRetryDelay 正数重试间隔
     */
    public void setDeleteRetryDelay(@NotNull Duration deleteRetryDelay) {
        this.deleteRetryDelay = requirePositive(deleteRetryDelay, "文件删除重试间隔");
    }

    /** 校验配置时长为正数。 */
    private static @NotNull Duration requirePositive(@NotNull Duration value, @NotNull String name) {
        Duration checked = Objects.requireNonNull(value, name + "不能为空");
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException(name + "必须为正数");
        }
        return checked;
    }

    /** 配置新文件使用的默认 backend。 */
    public static class Backend {

        private @NotNull String defaultId = "minio";

        /** @return 新文件使用的默认 backend ID */
        public @NotNull String getDefault() {
            return defaultId;
        }

        /** @param value 已注册的默认 backend ID */
        public void setDefault(@NotNull String value) {
            setDefaultId(value);
        }

        /** @return 新文件使用的默认 backend ID */
        public @NotNull String getDefaultId() {
            return defaultId;
        }

        /** @param value 已注册的默认 backend ID */
        public void setDefaultId(@NotNull String value) {
            String checked = Objects.requireNonNull(value, "默认存储实现标识不能为空").trim();
            if (checked.isEmpty()) {
                throw new IllegalArgumentException("默认存储实现标识不能为空");
            }
            defaultId = checked;
        }
    }
}
