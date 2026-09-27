package com.sca4d.iot.api.schedule;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;

/** 保存设备定时触发后需要提交的物模型 command 和输入。 */
@SuppressWarnings("ClassCanBeRecord") // 项目统一使用普通 class，不使用 Java record。
public class DeviceScheduledCommand {

    private final @NotNull String commandCode;

    private final @NotNull Map<String, Object> inputs;

    /** 创建由当前物模型在每次触发时重新校验的 command。 */
    public DeviceScheduledCommand(
            @NotNull String commandCode,
            @NotNull Map<String, Object> inputs) {
        this.commandCode = TextUtils.requireText(commandCode, "设备定时 command code");
        this.inputs = Map.copyOf(Objects.requireNonNull(inputs, "设备定时 command 输入不能为空"));
    }

    /** @return 物模型 command code */
    public @NotNull String commandCode() {
        return commandCode;
    }

    /** @return 不可修改 command 输入 */
    public @NotNull @Unmodifiable Map<String, Object> inputs() {
        return inputs;
    }
}
