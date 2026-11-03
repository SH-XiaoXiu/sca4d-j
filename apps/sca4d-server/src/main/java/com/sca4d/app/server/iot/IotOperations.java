package com.sca4d.app.server.iot;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.OperationAxes;
import com.sca4d.core.api.permission.OperationCode;
import com.sca4d.core.api.permission.OperationModule;
import com.sca4d.core.api.permission.OperationNode;
import com.sca4d.iam.base.rbac.PlatformRbacRepository;
import com.sca4d.iam.rbac.RbacGrantable;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/** 声明 Sca4d Server 的 IoT Product、Device 和设备操作权限。 */
@Component
public class IotOperations implements OperationModule {

    /** 创建、修改、启停 Device 和管理凭据。 */
    public static final @NotNull String DEVICES_MANAGE_VALUE = "IOT:DEVICES:MANAGE";

    /** 修改 desired state 和提交 command。 */
    public static final @NotNull String DEVICES_OPERATE_VALUE = "IOT:DEVICES:OPERATE";

    /** 读取 Device、连接、状态和历史。 */
    public static final @NotNull String DEVICES_READ_VALUE = "IOT:DEVICES:READ";

    /** 创建、修改和启停 Product，并发布物模型。 */
    public static final @NotNull String PRODUCTS_MANAGE_VALUE = "IOT:PRODUCTS:MANAGE";

    /** 读取 Product 和物模型。 */
    public static final @NotNull String PRODUCTS_READ_VALUE = "IOT:PRODUCTS:READ";

    public static final @NotNull OperationCode PRODUCTS_READ = new OperationCode(PRODUCTS_READ_VALUE);

    public static final @NotNull OperationCode PRODUCTS_MANAGE = new OperationCode(PRODUCTS_MANAGE_VALUE);

    public static final @NotNull OperationCode DEVICES_READ = new OperationCode(DEVICES_READ_VALUE);

    public static final @NotNull OperationCode DEVICES_MANAGE = new OperationCode(DEVICES_MANAGE_VALUE);

    public static final @NotNull OperationCode DEVICES_OPERATE = new OperationCode(DEVICES_OPERATE_VALUE);

    private static final @NotNull TreeCode ROOT = TreeCode.ofSegment("IOT");

    private static final @NotNull TreeCode PRODUCTS = TreeCode.ofSegment("PRODUCTS");

    private static final @NotNull TreeCode DEVICES = TreeCode.ofSegment("DEVICES");

    private static final @NotNull com.sca4d.core.api.name.code.Attribute GRANTABLE = RbacGrantable
            .of(PlatformRbacRepository.PLATFORM_DOMAIN);

    /** @return IoT 模块、Product 和 Device 三层 Operation 目录 */
    @Override
    @CheckReturnValue
    public @NotNull OperationNode.Group operationTree() {
        return OperationNode.root(
                ROOT,
                200,
                OperationNode.group(
                        PRODUCTS,
                        100,
                        OperationNode.leaf(
                                Action.READ,
                                100,
                                OperationAxes.Intent.READ,
                                OperationAxes.Nature.FUNCTION,
                                GRANTABLE),
                        OperationNode.leaf(
                                Action.MANAGE,
                                200,
                                OperationAxes.Intent.UPDATE,
                                OperationAxes.Nature.FUNCTION,
                                GRANTABLE)),
                OperationNode.group(
                        DEVICES,
                        200,
                        OperationNode.leaf(
                                Action.READ,
                                100,
                                OperationAxes.Intent.READ,
                                OperationAxes.Nature.FUNCTION,
                                GRANTABLE),
                        OperationNode.leaf(
                                Action.MANAGE,
                                200,
                                OperationAxes.Intent.UPDATE,
                                OperationAxes.Nature.FUNCTION,
                                GRANTABLE),
                        OperationNode.leaf(
                                Action.OPERATE,
                                300,
                                OperationAxes.Intent.OPERATE,
                                OperationAxes.Nature.FUNCTION,
                                GRANTABLE)));
    }
}
