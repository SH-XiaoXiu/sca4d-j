package com.sca4d.app.server.platform;

import com.sca4d.app.server.service.PlatformUserProfileService;
import com.sca4d.core.api.identity.AccountIdentifierType;
import com.sca4d.iam.base.PlatformIdentityService;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 在 IAM bootstrap 完成后为默认 Platform Account 补齐示例 App 用户资料。 */
@Component
@Order(200)
@ConditionalOnProperty(prefix = "sca4dj.iam.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class PlatformUserProfileBootstrap implements ApplicationRunner {

    private final @NotNull PlatformIdentityService identities;

    private final @NotNull PlatformUserProfileService profiles;

    private final @NotNull String username;

    /**
     * 创建 bootstrap 资料任务。
     *
     * @param identities Platform 身份服务
     * @param profiles App 用户资料服务
     * @param username bootstrap 用户名
     */
    public PlatformUserProfileBootstrap(
            @NotNull PlatformIdentityService identities,
            @NotNull PlatformUserProfileService profiles,
            @Value("${sca4dj.iam.bootstrap.username:admin}") @NotNull String username) {
        this.identities = Objects.requireNonNull(identities, "Platform 身份服务不能为空");
        this.profiles = Objects.requireNonNull(profiles, "Platform 用户资料服务不能为空");
        this.username = Objects.requireNonNull(username, "bootstrap 用户名不能为空");
    }

    /**
     * 为 bootstrap Account 关联的 Principal 创建缺失的默认资料。
     *
     * @param arguments 应用启动参数
     */
    @Override
    public void run(@NotNull ApplicationArguments arguments) {
        identities.resolvePlatformAccount(AccountIdentifierType.USERNAME, username)
                .ifPresent(
                        account -> identities.activePrincipals(account.accountId())
                                .forEach(principal -> profiles.initialize(principal.principalId(), username)));
    }
}
