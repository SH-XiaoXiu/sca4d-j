package com.sca4d.storage.local;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Objects;

/** 配置本地目录存储写入文件记录的 backendId、命名空间和磁盘根目录。 */
@ConfigurationProperties("sca4dj.storage.local")
public class LocalStorageProperties {
    private boolean enabled = true;

    private @NotNull String backendId = "local";

    private @NotNull String container = "files";

    private @NotNull Path root = Path.of("docker-data/files");

    /** 创建使用本地开发默认值的配置。 */
    public LocalStorageProperties() {
    }

    /**
     * 返回是否注册本地存储实现。
     *
     * @return 启用时为 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否注册本地存储实现。
     *
     * @param enabled 启用时为 {@code true}
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回写入文件记录的存储实现标识。
     *
     * @return 非空白存储实现标识
     */
    public @NotNull String getBackendId() {
        return backendId;
    }

    /**
     * 设置写入文件记录的存储实现标识。
     *
     * @param backendId 非空白存储实现标识
     */
    public void setBackendId(@NotNull String backendId) {
        this.backendId = requireText(backendId, "本地存储实现标识");
    }

    /**
     * 返回本地存储使用的命名空间。
     *
     * @return 非空白命名空间
     */
    public @NotNull String getContainer() {
        return container;
    }

    /**
     * 设置本地存储使用的命名空间。
     *
     * @param container 非空白命名空间
     */
    public void setContainer(@NotNull String container) {
        this.container = requireText(container, "本地存储命名空间");
    }

    /**
     * 返回本地对象的磁盘根目录。
     *
     * @return 本地对象根目录
     */
    public @NotNull Path getRoot() {
        return root;
    }

    /**
     * 设置本地对象的磁盘根目录。
     *
     * @param root 本地对象根目录
     */
    public void setRoot(@NotNull Path root) {
        this.root = Objects.requireNonNull(root, "本地存储根目录不能为空");
    }

    private static @NotNull String requireText(@NotNull String value, @NotNull String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").trim();
        if (checked.isEmpty())
            throw new IllegalArgumentException(name + "不能为空");
        return checked;
    }
}
