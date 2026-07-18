package com.sca4d.config;

import com.sca4d.common.i18n.LocalizedMessages;
import com.sca4d.common.permission.PermissionCatalog;
import com.sca4d.system.NavigationRouteCatalog;
import com.sca4d.system.NavigationRouteContributor;
import com.sca4d.system.view.AuditPayloadViewFactory;
import com.sca4d.system.view.AuditPayloadViewRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 系统模块配置。
 */
@Configuration
public class SystemConfig {

    /**
     * 注册平台菜单可以引用的代码路由目录。
     *
     * @param contributors 各业务模块拥有的菜单入口路由
     * @param permissionCatalog 代码权限权威目录
     * @param messages 后端消息资源解析入口
     * @return 已完成唯一性和权限引用校验的导航路由目录
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull NavigationRouteCatalog navigationRouteCatalog(
            @NotNull List<NavigationRouteContributor> contributors,
            @NotNull PermissionCatalog permissionCatalog,
            @NotNull LocalizedMessages messages) {
        return new NavigationRouteCatalog(contributors, permissionCatalog, messages);
    }

    /**
     * 注册审计载荷视图构造注册表。
     *
     * @param factories 按审计内容类型创建管理端视图的工厂集合
     * @return 审计载荷视图构造注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public @NotNull AuditPayloadViewRegistry auditPayloadViewRegistry(@NotNull List<AuditPayloadViewFactory<?>> factories) {
        return new AuditPayloadViewRegistry(factories);
    }
}
