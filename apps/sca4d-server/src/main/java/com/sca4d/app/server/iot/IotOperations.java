package com.sca4d.app.server.iot;

import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.base.rbac.PlatformRbacRepository;
import com.sca4d.iam.rbac.RbacDomain;
import com.sca4d.iam.rbac.RbacOperationModule;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 声明 Sca4d Server 的 IoT Product、Device 和设备操作权限。 */
@Component
public final class IotOperations implements RbacOperationModule {

    private static final @NotNull String MODULE = "iot";

    private static final @NotNull String PRODUCTS_RESOURCE = "products";

    private static final @NotNull String DEVICES_RESOURCE = "devices";

    /** 读取 Product 和物模型。 */
    public static final @NotNull String PRODUCTS_READ = MODULE + ':' + PRODUCTS_RESOURCE + ':' + Action.READ_VALUE;

    /** 创建、修改和启停 Product，并发布物模型。 */
    public static final @NotNull String PRODUCTS_MANAGE = MODULE + ':' + PRODUCTS_RESOURCE + ':' + Action.MANAGE_VALUE;

    /** 读取 Device、连接、状态和历史。 */
    public static final @NotNull String DEVICES_READ = MODULE + ':' + DEVICES_RESOURCE + ':' + Action.READ_VALUE;

    /** 创建、修改、启停 Device 和管理凭据。 */
    public static final @NotNull String DEVICES_MANAGE = MODULE + ':' + DEVICES_RESOURCE + ':' + Action.MANAGE_VALUE;

    /** 修改 desired state 和提交 command。 */
    public static final @NotNull String DEVICES_OPERATE = MODULE + ':' + DEVICES_RESOURCE + ':' + Action.OPERATE_VALUE;

    /** @return IoT 模块、Product 和 Device 三层 Operation 目录 */
    @Override
    @CheckReturnValue
    public @NotNull OperationNode.Module operationTree() {
        return OperationNode.module(
                "iot",
                "operation.iot",
                200,
                OperationNode.resource(
                        PRODUCTS_RESOURCE,
                        "operation.iot.products",
                        100,
                        OperationNode.action(Action.READ_VALUE, "operation.iot.products.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.iot.products.manage", 200)),
                OperationNode.resource(
                        DEVICES_RESOURCE,
                        "operation.iot.devices",
                        200,
                        OperationNode.action(Action.READ_VALUE, "operation.iot.devices.read", 100),
                        OperationNode.action(Action.MANAGE.value(), "operation.iot.devices.manage", 200),
                        OperationNode.action(Action.OPERATE.value(), "operation.iot.devices.operate", 300)));
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Set<@NotNull RbacDomain> domains() {
        return Set.of(PlatformRbacRepository.PLATFORM_DOMAIN);
    }
}
