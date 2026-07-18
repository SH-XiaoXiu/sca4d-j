package com.sca4d.iam.account;

import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** Account 认证材料、MFA、恢复码和 Session 管理权限目录。 */
@Component
public class AccountSecurityPermissions implements PermissionModule {

    /** 读取 Account 安全设置。 */
    public static final String SECURITY_READ = "account.security.read";
    /** 管理 Credential。 */
    public static final String CREDENTIALS_MANAGE = "account.credentials.manage";
    /** 重置 MFA。 */
    public static final String MFA_RESET = "account.mfa.reset";
    /** 重签或撤销恢复码。 */
    public static final String RECOVERY_MANAGE = "account.recovery.manage";
    /** 读取 Account Session。 */
    public static final String SESSIONS_READ = "account.sessions.read";
    /** 撤销 Account Session。 */
    public static final String SESSIONS_REVOKE = "account.sessions.revoke";

    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module("account", "permission.account", 15,
                PermissionNode.resource("account.security", "permission.account.security", 10,
                        PermissionNode.action(SECURITY_READ, "permission.account.security.read", 10)),
                PermissionNode.resource("account.credentials", "permission.account.credentials", 20,
                        PermissionNode.action(CREDENTIALS_MANAGE, "permission.account.credentials.manage", 10)),
                PermissionNode.resource("account.mfa", "permission.account.mfa", 30,
                        PermissionNode.action(MFA_RESET, "permission.account.mfa.reset", 10)),
                PermissionNode.resource("account.recovery", "permission.account.recovery", 40,
                        PermissionNode.action(RECOVERY_MANAGE, "permission.account.recovery.manage", 10)),
                PermissionNode.resource("account.sessions", "permission.account.sessions", 50,
                        PermissionNode.action(SESSIONS_READ, "permission.account.sessions.read", 10),
                        PermissionNode.action(SESSIONS_REVOKE, "permission.account.sessions.revoke", 20)));
    }
}
