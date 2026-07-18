package com.sca4d.system;

import com.sca4d.common.auth.PrincipalType;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 应用工作台和系统管理模块可进入共用菜单的代码路由。 */
@Component
public class SystemNavigationRoutes implements NavigationRouteContributor {

    /**
     * 返回所有业务 Principal 共用的工作台及 PLATFORM 系统管理入口。
     *
     * @return 工作台、字典、菜单、邮件发送配置、系统任务和操作日志固定路由
     */
    @Override
    public @NotNull List<NavigationRoute> navigationRoutes() {
        return List.of(
                new NavigationRoute(
                        "workspace.dashboard",
                        "/dashboard",
                        Set.of(PrincipalType.DEFAULT, PrincipalType.PLATFORM, PrincipalType.TENANT),
                        null,
                        MenuIconName.of("LayoutDashboard")),
                new NavigationRoute(
                        "system.dictionaries",
                        "/system/dictionaries",
                        Set.of(PrincipalType.PLATFORM),
                        SystemPermissions.DICTIONARIES_READ,
                        MenuIconName.of("BookOpenText")),
                new NavigationRoute(
                        "system.menus",
                        "/system/menus",
                        Set.of(PrincipalType.PLATFORM),
                        SystemPermissions.MENUS_READ,
                        MenuIconName.of("PanelLeft")),
                new NavigationRoute(
                        "system.scheduled-jobs",
                        "/system/scheduled-jobs",
                        Set.of(PrincipalType.PLATFORM),
                        SystemPermissions.SCHEDULED_JOBS_READ,
                        MenuIconName.of("ServerCog")),
                new NavigationRoute(
                        "system.mail-senders",
                        "/system/mail-senders",
                        Set.of(PrincipalType.PLATFORM),
                        SystemPermissions.MAIL_SENDERS_READ,
                        MenuIconName.of("MailCheck")),
                new NavigationRoute(
                        "system.mail-templates",
                        "/system/mail-templates",
                        Set.of(PrincipalType.PLATFORM),
                        SystemPermissions.MAIL_TEMPLATES_READ,
                        MenuIconName.of("FileText")),
                new NavigationRoute(
                        "system.identity-providers",
                        "/system/identity-providers",
                        Set.of(PrincipalType.PLATFORM),
                        SystemPermissions.IDENTITY_PROVIDERS_READ,
                        MenuIconName.of("Network")),
                new NavigationRoute(
                        "system.operation-logs",
                        "/system/operation-logs",
                        Set.of(PrincipalType.PLATFORM),
                        SystemPermissions.OPERATION_LOGS_READ,
                        MenuIconName.of("ScrollText")));
    }
}
