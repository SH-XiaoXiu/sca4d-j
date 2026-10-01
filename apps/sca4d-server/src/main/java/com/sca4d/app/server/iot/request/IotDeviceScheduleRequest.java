package com.sca4d.app.server.iot.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 接收创建或完整替换 Device 定时规则的 command、触发时间和有效期。 */
@SuppressWarnings("unused") // Jackson 通过 setter 绑定 HTTP 请求字段。
public class IotDeviceScheduleRequest {

    private long version;

    private @NotBlank @Size(max = 128) String commandCode;

    private @Req.NotNull @Size(max = 64) Map<String, Object> inputs = Map.of();

    private @Nullable Instant executeAt;

    private @Nullable @Size(max = 256) String cronExpression;

    private @Nullable @Size(max = 64) String zoneId;

    private @Positive long validForMillis;

    /** @return 完整替换时要求的当前版本；创建时忽略 */
    public long getVersion() {
        return version;
    }

    /** @param version 完整替换时要求的当前版本 */
    public void setVersion(long version) {
        this.version = version;
    }

    /** @return 物模型 command code */
    public String getCommandCode() {
        return commandCode;
    }

    /** @param commandCode 物模型 command code */
    public void setCommandCode(String commandCode) {
        this.commandCode = commandCode;
    }

    /** @return 完整 command 输入 */
    public @NotNull @Unmodifiable Map<String, Object> getInputs() {
        return Map.copyOf(inputs);
    }

    /** @param inputs 完整 command 输入 */
    public void setInputs(@Nullable Map<String, Object> inputs) {
        this.inputs = inputs == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }

    /** @return 单次绝对执行时间；Cron 规则为空 */
    public @Nullable Instant getExecuteAt() {
        return executeAt;
    }

    /** @param executeAt 单次绝对执行时间 */
    public void setExecuteAt(@Nullable Instant executeAt) {
        this.executeAt = executeAt;
    }

    /** @return 六字段 Cron；单次规则为空 */
    public @Nullable String getCronExpression() {
        return cronExpression;
    }

    /** @param cronExpression 六字段 Cron */
    public void setCronExpression(@Nullable String cronExpression) {
        this.cronExpression = cronExpression;
    }

    /** @return Cron IANA 时区；单次规则为空 */
    public @Nullable String getZoneId() {
        return zoneId;
    }

    /** @param zoneId Cron IANA 时区 */
    public void setZoneId(@Nullable String zoneId) {
        this.zoneId = zoneId;
    }

    /** @return 从计划时间开始计算的下行有效毫秒数 */
    public long getValidForMillis() {
        return validForMillis;
    }

    /** @param validForMillis 从计划时间开始计算的下行有效毫秒数 */
    public void setValidForMillis(long validForMillis) {
        this.validForMillis = validForMillis;
    }
}
