package com.sca4d.common.permission;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 当前主体会话授权数据。
 *
 * <p>该对象随完整 Session 写入 Subject 共享的 Sa-Token Account-Session。Role 是 RBAC 授权聚合，Permission
 * 表示当前 Session 可以执行的操作。
 */
public class SessionPermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long principalId;
    private Set<String> roleCodes = Set.of();
    private Set<String> permissionCodes = Set.of();

    /** 创建供会话存储反序列化逐字段填充的空授权对象。 */
    public SessionPermission() {
    }

    /**
     * 创建当前主体会话授权数据。
     *
     * @param principalId Principal 主键
     * @param roleCodes IAM 角色关系装载出的角色编码集合
     * @param permissionCodes 代码权限目录同步并由 IAM 角色关系装载出的权限编码集合
     */
    public SessionPermission(
            @NotNull Long principalId,
            @Nullable Set<String> roleCodes,
            @Nullable Set<String> permissionCodes) {
        this.principalId = principalId;
        setRoleCodes(roleCodes);
        setPermissionCodes(permissionCodes);
    }

    /** @return 当前授权所属 Principal 主键，反序列化未完成时为 null */
    public @Nullable Long getPrincipalId() {
        return principalId;
    }

    /** @param principalId 当前授权唯一所属的 Principal 主键 */
    public void setPrincipalId(@NotNull Long principalId) {
        this.principalId = principalId;
    }

    /** @return 不可变角色编码集合 */
    public @NotNull Set<String> getRoleCodes() {
        return roleCodes;
    }

    /**
     * @param roleCodes 当前 Principal 聚合得到的角色编码集合，历史空值按空集合处理
     * @throws IllegalArgumentException 集合包含空白编码时抛出
     */
    public void setRoleCodes(@Nullable Set<String> roleCodes) {
        Set<String> codes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        if (codes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("角色编码不能包含空白值");
        }
        this.roleCodes = codes;
    }

    /** @return 不包含通配符的不可变功能权限编码集合 */
    public @NotNull Set<String> getPermissionCodes() {
        return permissionCodes;
    }

    /**
     * @param permissionCodes 当前 Principal 聚合得到的功能权限编码集合，历史空值按空集合处理
     * @throws IllegalArgumentException 集合包含空白编码或通配权限时抛出
     */
    public void setPermissionCodes(@Nullable Set<String> permissionCodes) {
        Set<String> codes = permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
        if (codes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("权限编码不能包含空白值");
        }
        if (codes.contains("*")) {
            throw new IllegalArgumentException("SessionPermission 禁止使用通配权限");
        }
        this.permissionCodes = codes;
    }
}
