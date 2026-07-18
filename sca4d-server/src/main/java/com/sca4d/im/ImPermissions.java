package com.sca4d.im;

import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * IM 权限。
 */
@Component
public class ImPermissions implements PermissionModule {

    private static final String IM = "im";
    private static final String CONVERSATIONS = IM + ":conversations";
    private static final String MESSAGES = IM + ":messages";

    /** 读取当前 Principal 所属会话的权限编码。 */
    public static final String CONVERSATIONS_READ = CONVERSATIONS + ":read";
    /** 创建即时消息会话的权限编码。 */
    public static final String CONVERSATIONS_CREATE = CONVERSATIONS + ":create";
    /** 读取会话消息的权限编码。 */
    public static final String MESSAGES_READ = MESSAGES + ":read";
    /** 在所属会话中发送消息的权限编码。 */
    public static final String MESSAGES_CREATE = MESSAGES + ":create";
    /** 撤回当前 Principal 已发送消息的权限编码。 */
    public static final String MESSAGES_UPDATE = MESSAGES + ":update";

    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module(IM, "permission.im", 300,
                PermissionNode.resource(CONVERSATIONS, "permission.im.conversations", 100,
                        PermissionNode.action(CONVERSATIONS_READ, "permission.im.conversations.read", 100),
                        PermissionNode.action(CONVERSATIONS_CREATE, "permission.im.conversations.create", 200)),
                PermissionNode.resource(MESSAGES, "permission.im.messages", 200,
                        PermissionNode.action(MESSAGES_READ, "permission.im.messages.read", 100),
                        PermissionNode.action(MESSAGES_CREATE, "permission.im.messages.create", 200),
                        PermissionNode.action(MESSAGES_UPDATE, "permission.im.messages.update", 300)));
    }
}
