package com.sca4d.iam.bootstrap;

import com.sca4d.authentication.SessionService;
import com.sca4d.common.auth.PasswordCredential;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.config.properties.Sca4djBootstrapProperties;
import com.sca4d.entity.IamPrincipalEntity;
import com.sca4d.iam.account.AccountProvisioningService;
import com.sca4d.iam.account.ProvisionedAccount;
import com.sca4d.iam.principal.PrincipalService;
import com.sca4d.iam.repository.IamDefaultsRepository;
import com.sca4d.iam.repository.IamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 应用启动时同步代码权限目录，并按配置创建首个平台管理员。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IamBootstrap {

    private final PermissionCatalog permissionCatalog;
    private final IamDefaultsRepository iamDefaultsRepository;
    private final IamRepository iamRepository;
    private final SessionService sessionService;
    private final PrincipalService principalService;
    private final AccountProvisioningService accountProvisioningService;
    private final Sca4djBootstrapProperties properties;

    /**
     * 在应用已完成依赖装配后同步权限并按部署配置创建首个管理员。
     *
     * <p>不存在 PLATFORM Principal 才允许创建首个管理员。停用的管理员仍然保留其 PLATFORM Principal，
     * 因此重启不会重新创建或重新启用人工停用的 ROOT 绑定。
     *
     * @param event Spring 容器就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void synchronize(ApplicationReadyEvent event) {
        long startedAt = System.nanoTime();
        log.info("IAM 启动同步开始 | permissionCount={}", permissionCatalog.items().size());
        iamDefaultsRepository.lockSynchronization();
        iamRepository.synchronizePermissionCatalog(permissionCatalog);
        iamDefaultsRepository.synchronizeDefaults();
        Long administratorRoleId = iamRepository.requirePlatformAdministratorRoleId();
        sessionService.refreshOnlineAfterCommit();
        if (principalService.hasPlatformPrincipal()
                || !StringUtils.hasText(properties.getPlatformAdministratorIdentifier())
                || !StringUtils.hasText(properties.getPlatformAdministratorPassword())) {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.info(
                    "IAM 启动同步已写入事务 | administratorCreated={} permissionCount={} durationMs={}",
                    false,
                    permissionCatalog.items().size(),
                    durationMs);
            return;
        }
        ProvisionedAccount account = accountProvisioningService.create(
                properties.getPlatformAdministratorIdentifier(),
                new PasswordCredential(properties.getPlatformAdministratorPassword()),
                com.sca4d.common.auth.AccountScopeType.GLOBAL,
                null,
                properties.getPlatformAdministratorDisplayName(),
                null,
                null,
                null,
                null,
                null);
        IamPrincipalEntity administrator = principalService.createPlatform(
                account.getSubject().getId(), properties.getPlatformAdministratorDisplayName());
        iamRepository.replacePrincipalRoles(administrator.getId(), List.of(administratorRoleId));
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info(
                "IAM 启动同步已写入事务 | administratorCreated={} permissionCount={} durationMs={}",
                true,
                permissionCatalog.items().size(),
                durationMs);
    }
}
