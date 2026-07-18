package com.sca4d.system;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.PrincipalType;
import com.sca4d.common.validation.ValidationPatterns;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** 菜单可以引用的代码注册前端路由。 */
public final class NavigationRoute {

    private static final String TENANT_ID_TOKEN = "{tenantId}";

    private final String code;
    private final String pathTemplate;
    private final Set<PrincipalType> principalTypes;
    private final String permissionCode;
    private final MenuIconName defaultIcon;

    /**
     * 注册一个允许进入平台共用菜单的前端路由。
     *
     * <p>路由编码由所属业务模块扩展。路径模板只允许固定路径和 {@code {tenantId}}，数据库菜单不能提供组件名或任意路径。
     *
     * @param code 跨前后端固定的点分路由编码
     * @param pathTemplate 以斜杠开头的固定路径或租户路径模板
     * @param principalTypes 可以进入该路由的 Principal 类型
     * @param permissionCode 路由要求的代码权限；工作台等无功能权限入口可以为空
     * @param defaultIcon 新建菜单时使用的默认图标含义
     */
    public NavigationRoute(
            @NotNull String code,
            @NotNull String pathTemplate,
            @NotNull Set<PrincipalType> principalTypes,
            @Nullable String permissionCode,
            @NotNull MenuIconName defaultIcon) {
        if (!ValidationPatterns.isNamespacedCode(code)) {
            throw new IllegalArgumentException("导航路由编码格式错误: " + code);
        }
        if (!pathTemplate.startsWith("/")
                || pathTemplate.replace(TENANT_ID_TOKEN, "").contains("{")
                || pathTemplate.replace(TENANT_ID_TOKEN, "").contains("}")) {
            throw new IllegalArgumentException("导航路由路径模板格式错误: " + pathTemplate);
        }
        Set<PrincipalType> checkedTypes = Set.copyOf(principalTypes);
        if (checkedTypes.isEmpty() || checkedTypes.contains(PrincipalType.SYSTEM)) {
            throw new IllegalArgumentException("导航路由必须声明非 SYSTEM Principal 类型: " + code);
        }
        if (pathTemplate.contains(TENANT_ID_TOKEN) && !checkedTypes.equals(Set.of(PrincipalType.TENANT))) {
            throw new IllegalArgumentException("租户路径模板只能由 TENANT Principal 使用: " + code);
        }
        if (permissionCode != null && permissionCode.isBlank()) {
            throw new IllegalArgumentException("导航路由权限编码不能为空白: " + code);
        }
        this.code = code;
        this.pathTemplate = pathTemplate;
        this.principalTypes = checkedTypes;
        this.permissionCode = permissionCode;
        this.defaultIcon = defaultIcon;
    }

    /** @return 所属模块注册的固定路由编码 */
    public @NotNull String getCode() {
        return code;
    }

    /** @return 不包含组件名的前端固定路径模板 */
    public @NotNull String getPathTemplate() {
        return pathTemplate;
    }

    /** @return 可以进入该路由的 Principal 类型集合 */
    public @NotNull Set<PrincipalType> getPrincipalTypes() {
        return principalTypes;
    }

    /** @return 路由绑定的代码权限；无功能权限入口返回 null */
    public @Nullable String getPermissionCode() {
        return permissionCode;
    }

    /** @return 使用 navigation. 加路由编码形成的默认标题消息键 */
    public @NotNull String getDefaultTitleKey() {
        return "navigation." + code;
    }

    /** @return 新建菜单使用的默认图标含义 */
    public @NotNull MenuIconName getDefaultIcon() {
        return defaultIcon;
    }

    /**
     * 把代码路径模板解析为当前 Principal 的最终导航路径。
     *
     * @param principal 当前请求已经选择的执行主体
     * @return 不包含数据库自由输入的最终前端路径
     * @throws IllegalArgumentException Principal 类型或租户数据与路由不匹配时抛出
     */
    public @NotNull String pathFor(@NotNull Principal principal) {
        if (!principalTypes.contains(principal.getPrincipalType())) {
            throw new IllegalArgumentException("当前 Principal 不能解析导航路由: " + code);
        }
        if (!pathTemplate.contains(TENANT_ID_TOKEN)) {
            return pathTemplate;
        }
        Long tenantId = principal.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("租户导航路由缺少 tenantId: " + code);
        }
        return pathTemplate.replace(TENANT_ID_TOKEN, tenantId.toString());
    }
}
