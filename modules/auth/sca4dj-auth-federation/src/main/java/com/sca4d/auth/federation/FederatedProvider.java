package com.sca4d.auth.federation;

import com.sca4d.core.api.code.TreeCode;
import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 保存一个 Platform 范围内可配置的外部登录 Provider。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class FederatedProvider {

    /**
     * Provider 管理接口和审计记录使用的资源类型。
     */
    public static final @NotNull TreeCode RESOURCE_TYPE = TreeCode.ofSlash("AUTH/FEDERATED_PROVIDER");

    /**
     * 供 Java 注解参数使用的资源类型文本。
     */
    public static final @NotNull String RESOURCE_TYPE_VALUE = "AUTH/FEDERATED_PROVIDER";

    private final long id;

    private final @NotNull String code;

    private final @NotNull FederatedProviderProtocol protocol;

    private final @NotNull FederatedProviderStatus status;

    private final boolean loginPageVisible;

    private final int displayOrder;

    private final @NotNull FederatedUnlinkedIdentityAction unlinkedIdentityAction;

    private final boolean autoLinkVerifiedEmail;

    private final @NotNull Map<String, Map<String, String>> i18n;

    private final @Nullable String iconReference;

    private final @NotNull Version version;

    /**
     * 创建外部登录 Provider。
     */
    public FederatedProvider(
            long id,
            @NotNull String code,
            @NotNull FederatedProviderProtocol protocol,
            @NotNull FederatedProviderStatus status,
            boolean loginPageVisible,
            int displayOrder,
            @NotNull FederatedUnlinkedIdentityAction unlinkedIdentityAction,
            boolean autoLinkVerifiedEmail,
            @NotNull Map<String, Map<String, String>> i18n,
            @Nullable String iconReference,
            @NotNull Version version) {
        if (id <= 0 || displayOrder < 0) {
            throw new IllegalArgumentException("Provider 标识和排序必须有效");
        }
        this.id = id;
        this.code = TextUtils.requireText(code, "Provider 编码");
        this.protocol = Objects.requireNonNull(protocol, "Provider 协议不能为空");
        this.status = Objects.requireNonNull(status, "Provider 状态不能为空");
        this.loginPageVisible = loginPageVisible;
        this.displayOrder = displayOrder;
        this.unlinkedIdentityAction = Objects.requireNonNull(unlinkedIdentityAction, "未绑定身份策略不能为空");
        this.autoLinkVerifiedEmail = autoLinkVerifiedEmail;
        this.i18n = copyI18n(i18n);
        this.iconReference = iconReference == null || iconReference.isBlank() ? null : iconReference.strip();
        this.version = Objects.requireNonNull(version, "Provider 版本不能为空");
    }

    /**
     * @return 数据库标识
     */
    public long id() {
        return id;
    }

    /**
     * @return 不可变 Provider 编码
     */
    public @NotNull String code() {
        return code;
    }

    /**
     * @return Provider 协议
     */
    public @NotNull FederatedProviderProtocol protocol() {
        return protocol;
    }

    /**
     * @return 启用状态
     */
    public @NotNull FederatedProviderStatus status() {
        return status;
    }

    /**
     * @return 是否允许登录页展示
     */
    public boolean loginPageVisible() {
        return loginPageVisible;
    }

    /**
     * @return 登录页排序
     */
    public int displayOrder() {
        return displayOrder;
    }

    /**
     * @return 未绑定身份处理策略
     */
    public @NotNull FederatedUnlinkedIdentityAction unlinkedIdentityAction() {
        return unlinkedIdentityAction;
    }

    /**
     * @return 是否尝试使用已验证邮箱自动关联
     */
    public boolean autoLinkVerifiedEmail() {
        return autoLinkVerifiedEmail;
    }

    /**
     * @return 按语言代码保存的 name 和 buttonText 文本
     */
    public @NotNull @Unmodifiable Map<String, Map<String, String>> i18n() {
        return Map.copyOf(i18n);
    }

    /**
     * @return 内置图标键或应用文件引用
     */
    public @Nullable String iconReference() {
        return iconReference;
    }

    /**
     * @return 乐观并发版本
     */
    public @NotNull Version version() {
        return version;
    }

    /**
     * 复制多语言字段，避免调用方在对象创建后修改内部 Map。
     */
    private static @NotNull Map<String, Map<String, String>> copyI18n(
            @NotNull Map<String, Map<String, String>> source) {
        return source.entrySet()
                .stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey,
                                entry -> Map.copyOf(entry.getValue())));
    }
}
