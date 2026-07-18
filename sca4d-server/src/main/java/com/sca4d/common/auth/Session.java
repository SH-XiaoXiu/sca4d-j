package com.sca4d.common.auth;

import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.permission.SessionPermission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 已认证 Subject 的运行时会话句柄。
 *
 * <p>实现负责维护底层 token 状态。调用 {@link #invalidate()} 必须幂等，不得把可执行 Java 对象写入 Redis。
 */
public interface Session {

    /**
     * 获取非敏感会话标识。
     *
     * @return 会话标识
     */
    @NotNull
    String getSessionId();

    /**
     * 获取 token 名称。
     *
     * @return token 名称
     */
    @NotNull
    String getTokenName();

    /**
     * 获取 token 值。
     *
     * <p>该值只供认证公共组件使用，对外 View 不得返回。
     *
     * @return token 值
     */
    @NotNull
    String getTokenValue();

    /** @return 认证前置已经恢复的登录 Subject */
    @NotNull
    Subject getSubject();

    /** @return 本次认证固定使用的登录 Account */
    @NotNull
    Account getAccount();

    /** @return 当前 Subject 顺序固定且不可变的可选择 Principal */
    @NotNull
    List<Principal> getPrincipals();

    /** @return 以 Principal 主键索引且不可变的角色权限 */
    @NotNull
    Map<Long, SessionPermission> getPermissions();

    /**
     * 读取会话中指定 Principal 的授权。
     *
     * @param principalId 当前请求已经选择的 Principal 主键
     * @return 与该 Principal 严格绑定的角色权限
     * @throws ForbiddenException 会话不包含该 Principal 授权时抛出
     */
    default @NotNull SessionPermission requirePermission(@NotNull Long principalId) {
        SessionPermission permission = getPermissions().get(principalId);
        if (permission == null || !principalId.equals(permission.getPrincipalId())) {
            throw new ForbiddenException(CommonErrorCode.AUTHENTICATION_PRINCIPAL_AUTHORIZATION_MISSING);
        }
        return permission;
    }

    /**
     * 获取所属 Subject 主键。
     *
     * @return Subject 主键
     */
    default @NotNull Long getSubjectId() {
        return getSubject().getSubjectId();
    }

    /**
     * 获取本次认证使用的 Account 主键。
     *
     * @return Account 主键
     */
    default @NotNull Long getAccountId() {
        return getAccount().getAccountId();
    }

    /**
     * 获取设备标识。
     *
     * @return 设备标识
     */
    @Nullable
    String getDeviceId();

    /**
     * 获取终端名称。
     *
     * @return 终端名称
     */
    @Nullable
    String getTerminalName();

    /**
     * 获取登录时间。
     *
     * @return 登录时间
     */
    @Nullable
    Instant getLoginTime();

    /**
     * 获取过期时间。
     *
     * @return 过期时间
     */
    @Nullable
    Instant getExpireTime();

    /**
     * 使当前会话对应的 token 失效。
     *
     * <p>该操作是显式认证命令，不是词法作用域资源释放，不使用 try-with-resources。
     */
    void invalidate();
}
