package com.sca4d.notification;

import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * 通知权限。
 */
@Component
public class NotificationPermissions implements PermissionModule {

    private static final String NOTIFICATION = "notification";
    private static final String MESSAGES = NOTIFICATION + ":messages";

    /** 查看当前 Principal 通知消息的权限。 */
    public static final String MESSAGES_READ = MESSAGES + ":read";

    /** 更新当前 Principal 通知已读状态的权限。 */
    public static final String MESSAGES_UPDATE = MESSAGES + ":update";

    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module(NOTIFICATION, "permission.notification", 400,
                PermissionNode.resource(MESSAGES, "permission.notification.messages", 100,
                        PermissionNode.action(MESSAGES_READ, "permission.notification.messages.read", 100),
                        PermissionNode.action(MESSAGES_UPDATE, "permission.notification.messages.update", 200)));
    }
}
