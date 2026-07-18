package com.sca4d.authentication.me;

import com.sca4d.common.auth.Account;
import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Session;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.iam.profile.DefaultProfile;
import com.sca4d.system.NavigationMenu;
import com.sca4d.tenant.TenantMembership;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 当前登录接口需要的认证运行时和 DEFAULT Profile 快照。
 *
 * <p>该对象隔离请求适配层的 {@code CurrentRequest}，向 Controller 与 View 转换层传递已经确定的认证数据、
 * DEFAULT Profile 头像关系和版本，不包含 HTTP、STOMP 或持久化框架类型。
 */
public class MeSnapshot {

    private final Subject subject;
    private final Account account;
    private final DefaultProfile defaultProfile;
    private final Principal principal;
    private final Session session;
    private final SessionPermission permission;
    private final List<AvailablePrincipal> availablePrincipals;
    private final List<TenantMembership> tenants;
    private final List<NavigationMenu> navigation;

    /**
     * 使用当前请求已经确定的认证数据创建快照。
     *
     * @param subject 当前已认证的 Subject
     * @param account 本次认证固定使用的 Account
     * @param defaultProfile 当前 Subject 对应的 DEFAULT Profile
     * @param principal 当前已选 Principal；尚未选择时为 {@code null}
     * @param session 当前有效 Session；SYSTEM 执行时为 {@code null}
     * @param permission 当前 Principal 的角色和功能权限快照
     * @param availablePrincipals 当前 Subject 全部未删除主体，供客户端发起后续主体选择
     * @param tenants 当前 Subject 已加入的租户成员关系
     * @param navigation 服务端按当前 Principal 与权限过滤的最终导航树
     */
    public MeSnapshot(
            @NotNull Subject subject,
            @NotNull Account account,
            @NotNull DefaultProfile defaultProfile,
            @Nullable Principal principal,
            @Nullable Session session,
            @NotNull SessionPermission permission,
            @NotNull List<AvailablePrincipal> availablePrincipals,
            @NotNull List<TenantMembership> tenants,
            @NotNull List<NavigationMenu> navigation) {
        this.subject = subject;
        this.account = account;
        this.defaultProfile = defaultProfile;
        this.principal = principal;
        this.session = session;
        this.permission = permission;
        this.availablePrincipals = List.copyOf(availablePrincipals);
        this.tenants = List.copyOf(tenants);
        this.navigation = List.copyOf(navigation);
    }

    /**
     * 获取当前已认证 Subject。
     *
     * @return 当前请求已经确认的登录身份
     */
    public @NotNull Subject getSubject() {
        return subject;
    }

    /**
     * 获取本次认证固定使用的 Account。
     *
     * @return 当前 Session 的登录账号，不随请求级 Principal 选择变化
     */
    public @NotNull Account getAccount() {
        return account;
    }

    /**
     * 获取当前 Subject 对应的 DEFAULT Profile。
     *
     * @return 包含头像关系和版本的默认资料
     */
    public @NotNull DefaultProfile getDefaultProfile() {
        return defaultProfile;
    }

    /**
     * 获取当前请求已选择的 Principal。
     *
     * @return 当前执行主体；尚未选择时为 {@code null}
     */
    public @Nullable Principal getPrincipal() {
        return principal;
    }

    /**
     * 获取当前认证会话。
     *
     * @return 当前 Session；SYSTEM 执行时为 {@code null}
     */
    public @Nullable Session getSession() {
        return session;
    }

    /**
     * 获取当前 Principal 的角色和功能权限快照。
     *
     * @return 当前请求完成权限解析后的会话权限
     */
    public @NotNull SessionPermission getPermission() {
        return permission;
    }

    /**
     * 获取当前 Subject 可参与主体选择的全部 Principal。
     *
     * @return 创建快照时复制且之后不可修改的主体列表
     */
    public @NotNull List<AvailablePrincipal> getAvailablePrincipals() {
        return availablePrincipals;
    }

    /**
     * 获取当前 Subject 已加入的租户成员关系。
     *
     * @return 创建快照时复制且之后不可修改的租户成员关系列表
     */
    public @NotNull List<TenantMembership> getTenants() {
        return tenants;
    }

    /**
     * 获取当前 Principal 可以直接渲染的导航树。
     *
     * @return 已完成显隐、Principal 类型、权限、语言和租户路径解析的不可变导航
     */
    public @NotNull List<NavigationMenu> getNavigation() {
        return navigation;
    }
}
