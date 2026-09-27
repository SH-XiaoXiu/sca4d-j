package com.sca4d.auth.federation.provider.mapper;

import com.sca4d.auth.federation.FederatedProviderProtocol;
import com.sca4d.auth.federation.FederatedProviderStatus;
import com.sca4d.auth.federation.FederatedUnlinkedIdentityAction;
import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** 保存 MyBatis 从联邦 Provider 表读取的持久化状态。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class ProviderState {

    private final long id;

    private final @NotNull String code;

    private final @NotNull FederatedProviderProtocol protocol;

    private final @NotNull FederatedProviderStatus status;

    private final boolean loginPageVisible;

    private final int displayOrder;

    private final @NotNull FederatedUnlinkedIdentityAction unlinkedIdentityAction;

    private final boolean autoLinkVerifiedEmail;

    private final @NotNull String i18n;

    private final @Nullable String iconReference;

    private final @NotNull Version version;

    /** 创建 Provider 持久化状态。 */
    public ProviderState(
            long id,
            @NotNull String code,
            @NotNull FederatedProviderProtocol protocol,
            @NotNull FederatedProviderStatus status,
            boolean loginPageVisible,
            int displayOrder,
            @NotNull FederatedUnlinkedIdentityAction unlinkedIdentityAction,
            boolean autoLinkVerifiedEmail,
            @NotNull String i18n,
            @Nullable String iconReference,
            @NotNull Version version) {
        if (id <= 0 || displayOrder < 0) {
            throw new IllegalArgumentException("联邦 Provider 的标识和排序必须有效");
        }
        this.id = id;
        this.code = Objects.requireNonNull(code, "联邦 Provider 编码不能为空");
        this.protocol = Objects.requireNonNull(protocol, "联邦 Provider 协议不能为空");
        this.status = Objects.requireNonNull(status, "联邦 Provider 状态不能为空");
        this.loginPageVisible = loginPageVisible;
        this.displayOrder = displayOrder;
        this.unlinkedIdentityAction = Objects.requireNonNull(unlinkedIdentityAction, "未绑定身份策略不能为空");
        this.autoLinkVerifiedEmail = autoLinkVerifiedEmail;
        this.i18n = Objects.requireNonNull(i18n, "联邦 Provider 多语言字段不能为空");
        this.iconReference = iconReference;
        this.version = Objects.requireNonNull(version, "联邦 Provider 版本不能为空");
    }

    /** @return 数据库标识 */
    public long id() {
        return id;
    }

    /** @return Provider 编码 */
    public @NotNull String code() {
        return code;
    }

    /** @return 协议类型 */
    public @NotNull FederatedProviderProtocol protocol() {
        return protocol;
    }

    /** @return Provider 状态 */
    public @NotNull FederatedProviderStatus status() {
        return status;
    }

    /** @return 是否显示在登录页 */
    public boolean loginPageVisible() {
        return loginPageVisible;
    }

    /** @return 登录页排序 */
    public int displayOrder() {
        return displayOrder;
    }

    /** @return 未绑定身份策略 */
    public @NotNull FederatedUnlinkedIdentityAction unlinkedIdentityAction() {
        return unlinkedIdentityAction;
    }

    /** @return 是否按已验证邮箱自动关联 */
    public boolean autoLinkVerifiedEmail() {
        return autoLinkVerifiedEmail;
    }

    /** @return JSONB 多语言字段 */
    public @NotNull String i18n() {
        return i18n;
    }

    /** @return 图标引用 */
    public @Nullable String iconReference() {
        return iconReference;
    }

    /** @return 乐观并发版本 */
    public @NotNull Version version() {
        return version;
    }
}
