package com.sca4d.iot.api;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/** 声明 IoT 管理 API、设备接入能力及其共同系统身份。 */
public class IotCapabilities {

    /** IoT 管理、设备状态和历史查询 API。 */
    public static final String API = "IOT:API";

    /** 供注解声明的 IOT 系统身份编码。 */
    public static final String SYSTEM_IDENTITY_VALUE = "IOT";

    /** IoT 接入监听器和 Broker 回调。 */
    public static final String TRANSPORT = "IOT:TRANSPORT";

    /** IoT 后台状态扫描、下行清理和定时规则执行。 */
    public static final String WORKER = "IOT:WORKER";

    /** IoT 各实例共享的系统自主执行身份。 */
    public static final @NotNull TreeCode SYSTEM_IDENTITY = TreeCode.ofSegment(SYSTEM_IDENTITY_VALUE);

    private IotCapabilities() {
    }
}
