package com.sca4d.auth.session;

import com.sca4d.auth.session.codec.PrincipalIdLoaders;
import com.sca4d.auth.session.codec.PrincipalLoaders;
import com.sca4d.auth.session.codec.ResourceIdLoaders;
import com.sca4d.auth.session.codec.ResourceLoaders;
import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.authentication.principalsession.PrincipalIdSet;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 使用当前身份、Principal 和 Resource 重建已经失效的 Session，并保留原认证和有效期。 */
public final class SessionRefreshService {

    private final SessionIdentityLoadService identities;

    private final PrincipalLoaders principals;

    private final PrincipalIdLoaders principalIds;

    private final ResourceLoaders resources;

    private final ResourceIdLoaders resourceIds;

    /**
     * 创建 Session 刷新服务。
     *
     * @param identities 重新读取 Account 和 Subject 的服务
     * @param principals 重新读取全部可选择 Principal 的服务
     * @param principalIds 重新读取请求期解析的 Principal 标识
     * @param resources 重新读取全部 Principal Resource 的服务
     * @param resourceIds 重新读取完整 Principal 的数字 Resource 标识
     */
    public SessionRefreshService(
            @NotNull SessionIdentityLoadService identities,
            @NotNull PrincipalLoaders principals,
            @NotNull PrincipalIdLoaders principalIds,
            @NotNull ResourceLoaders resources,
            @NotNull ResourceIdLoaders resourceIds) {
        this.identities = Objects.requireNonNull(identities, "Session 身份读取服务不能为空");
        this.principals = Objects.requireNonNull(principals, "Session Principal 读取服务不能为空");
        this.principalIds = Objects.requireNonNull(principalIds, "Session Principal ID 读取服务不能为空");
        this.resources = Objects.requireNonNull(resources, "Session Resource 读取服务不能为空");
        this.resourceIds = Objects.requireNonNull(resourceIds, "Session Resource ID 读取服务不能为空");
    }

    /**
     * 重新读取身份和 Resource，并保留原 Session ID、认证记录、签发时间和过期时间。
     *
     * @param previous Token-Session 中解码得到的旧 Session
     * @return 使用当前身份和 Resource 创建的新 Session
     */
    @CheckReturnValue
    public @NotNull Session refresh(@NotNull Session previous) {
        Session checked = Objects.requireNonNull(previous, "旧 Session 不能为空");
        SessionIdentity identity = identities.load(checked.account());
        List<Principal> loadedPrincipals = principals.load(identity);
        List<PrincipalIdSet<?>> loadedPrincipalIds = principalIds.load(identity);
        return new Session(
                checked.id(),
                identity.subject(),
                identity.account(),
                loadedPrincipals,
                loadedPrincipalIds,
                checked.authentications(),
                resources.load(loadedPrincipals),
                resourceIds.load(loadedPrincipals),
                checked.issuedAt(),
                checked.expiresAt());
    }
}
