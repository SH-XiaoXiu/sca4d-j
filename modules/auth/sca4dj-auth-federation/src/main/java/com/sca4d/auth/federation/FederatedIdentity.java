package com.sca4d.auth.federation;

import com.sca4d.core.api.name.code.TreeCode;

import java.time.Instant;
import java.util.Objects;

/**
 * 保存 Provider 与本地 Account 之间的外部身份绑定。
 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public final class FederatedIdentity {

    /**
     * 外部身份绑定接口和审计记录使用的资源类型。
     */
    public static final TreeCode RESOURCE_TYPE = TreeCode.ofSlash("AUTH/FEDERATED_IDENTITY");

    /**
     * 供 Java 注解参数使用的资源类型文本。
     */
    public static final String RESOURCE_TYPE_VALUE = "AUTH/FEDERATED_IDENTITY";

    private final long id;

    private final long providerId;

    private final long accountId;

    private final FederatedProfile profile;

    private final long version;

    private final Instant linkedAt;

    /**
     * 创建外部身份绑定。
     */
    public FederatedIdentity(
            long id,
            long providerId,
            long accountId,
            FederatedProfile profile,
            long version,
            Instant linkedAt) {
        if (id <= 0 || providerId <= 0 || accountId <= 0 || version < 0)
            throw new IllegalArgumentException("外部身份标识无效");
        this.id = id;
        this.providerId = providerId;
        this.accountId = accountId;
        this.profile = Objects.requireNonNull(profile, "外部资料不能为空");
        this.version = version;
        this.linkedAt = Objects.requireNonNull(linkedAt, "绑定时间不能为空");
    }

    /**
     * @return 绑定标识
     */
    public long id() {
        return id;
    }

    /**
     * @return Provider 标识
     */
    public long providerId() {
        return providerId;
    }

    /**
     * @return 本地 Account 标识
     */
    public long accountId() {
        return accountId;
    }

    /**
     * @return 最近一次登录返回的资料快照
     */
    public FederatedProfile profile() {
        return profile;
    }

    /**
     * @return 绑定版本
     */
    public long version() {
        return version;
    }

    /**
     * @return 首次绑定时间
     */
    public Instant linkedAt() {
        return linkedAt;
    }
}
