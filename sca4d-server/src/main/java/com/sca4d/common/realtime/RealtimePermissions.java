package com.sca4d.common.realtime;

import com.sca4d.common.permission.PermissionModule;
import com.sca4d.common.permission.PermissionNode;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * 实时通信权限。
 */
@Component
public class RealtimePermissions implements PermissionModule {

    private static final String REALTIME = "realtime";
    private static final String SUBSCRIPTION = REALTIME + ":subscription";
    private static final String SSE = REALTIME + ":sse";

    /** 读取当前 Subject 和 Principal 实时订阅信息的权限编码。 */
    public static final String SUBSCRIPTION_READ = SUBSCRIPTION + ":read";
    /** 建立 SSE 长连接的权限编码。 */
    public static final String SSE_CONNECT = SSE + ":connect";

    @Override
    public @NotNull PermissionNode permissionTree() {
        return PermissionNode.module(REALTIME, "permission.realtime", 200,
                PermissionNode.resource(SUBSCRIPTION, "permission.realtime.subscription", 100,
                        PermissionNode.action(SUBSCRIPTION_READ, "permission.realtime.subscription.read", 100)),
                PermissionNode.resource(SSE, "permission.realtime.sse", 200,
                        PermissionNode.action(SSE_CONNECT, "permission.realtime.sse.connect", 100)));
    }
}
