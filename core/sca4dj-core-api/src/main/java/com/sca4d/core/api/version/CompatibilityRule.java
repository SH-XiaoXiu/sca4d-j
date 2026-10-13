package com.sca4d.core.api.version;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 某客户端产品版本范围可使用的一个能力契约及其入口。 */
public final class CompatibilityRule {
    private final CompatibilityStatus status;

    private final String adapter;

    private final String capability;

    private final String client;

    private final String maximumVersion;

    private final String minimumVersion;

    private final String path;

    private final int contract;

    /** 版本范围为左闭右开；入口是应用内路径，可含路径参数模板。 */
    public CompatibilityRule(
            String client,
            String capability,
            String minimumVersion,
            String maximumVersion,
            int contract,
            CompatibilityStatus status,
            String adapter,
            String path) {
        this.client = Objects.requireNonNull(client);
        this.capability = Objects.requireNonNull(capability);
        this.minimumVersion = Objects.requireNonNull(minimumVersion);
        this.maximumVersion = Objects.requireNonNull(maximumVersion);
        this.contract = contract;
        this.status = Objects.requireNonNull(status);
        this.adapter = Objects.requireNonNull(adapter);
        this.path = Objects.requireNonNull(path);
        if (client.isBlank()
                || capability.isBlank()
                || contract <= 0
                || adapter.isBlank()
                || status == CompatibilityStatus.UNKNOWN
                || compareVersions(minimumVersion, maximumVersion) >= 0
                || !path.startsWith("/")
                || path.startsWith("//")
                || path.contains(":")
                || path.contains("\\")
                || path.contains("..")
                || path.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("兼容规则无效");
        }
    }

    /** @return 客户端名称 */
    @JsonProperty
    public String client() {
        return client;
    }

    /** @return 能力标识 */
    @JsonProperty
    public String capability() {
        return capability;
    }

    /** @return 包含的最小产品版本 */
    @JsonProperty
    public String minimumVersion() {
        return minimumVersion;
    }

    /** @return 不包含的最大产品版本 */
    @JsonProperty
    public String maximumVersion() {
        return maximumVersion;
    }

    /** @return 契约版本 */
    @JsonProperty
    public int contract() {
        return contract;
    }

    /** @return 支持状态 */
    @JsonProperty
    public CompatibilityStatus status() {
        return status;
    }

    /** @return 应用注册的适配器标识 */
    @JsonProperty
    public String adapter() {
        return adapter;
    }

    /** @return 客户端可选择的应用内入口 */
    @JsonProperty
    public String path() {
        return path;
    }

    /** @return 客户端版本是否落在声明范围内 */
    public boolean matches(@NotNull ClientVersion version, String capability) {
        return client.equals(version.name())
                && this.capability.equals(capability)
                && version.releaseVersion() != null
                && compareVersions(version.releaseVersion(), minimumVersion) >= 0
                && compareVersions(version.releaseVersion(), maximumVersion) < 0;
    }

    /** @return 两个三段整数版本的比较结果，不按字符串排序 */
    public static int compareVersions(String left, String right) {
        var a = numbers(left);
        var b = numbers(right);
        for (int index = 0; index < 3; index++) {
            int result = Long.compare(a[index], b[index]);
            if (result != 0)
                return result;
        }
        return 0;
    }

    private static long[] numbers(String value) {
        if (!value.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))
            throw new IllegalArgumentException("产品版本必须为三段非负整数");
        var parts = value.split("\\.");
        return new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
    }
}
