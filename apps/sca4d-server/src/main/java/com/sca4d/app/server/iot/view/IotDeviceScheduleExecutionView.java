package com.sca4d.app.server.iot.view;

import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.spring.web.view.BaseView;
import com.sca4d.core.spring.web.view.Localize;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.message.IotFailureCode;
import com.sca4d.iot.api.schedule.DeviceScheduleExecution;
import com.sca4d.iot.api.schedule.DeviceScheduleExecutionStatus;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/** 返回一个计划时间生成下行、过期或失败的结果。 */
@SuppressWarnings("unused")
public class IotDeviceScheduleExecutionView {

    private final @NotNull DeviceScheduleExecutionStatus status;

    private final @Nullable Failure failure;

    private final @NotNull Instant plannedAt;

    private final @NotNull Instant triggeredAt;

    private final @Nullable Long messageId;

    private final long executionId;

    private final long scheduleId;

    /** 从模块 Device 定时执行结果创建 HTTP 视图。 */
    private IotDeviceScheduleExecutionView(@NotNull DeviceScheduleExecution execution) {
        this.executionId = execution.executionId();
        this.scheduleId = execution.scheduleId();
        this.plannedAt = execution.plannedAt();
        this.triggeredAt = execution.triggeredAt();
        this.status = execution.status();
        this.messageId = execution.messageId().isPresent() ? execution.messageId().getAsLong() : null;
        this.failure = execution.failureCode().map(Failure::from).orElse(null);
    }

    /** @return Device 定时执行 HTTP 视图 */
    @CheckReturnValue
    public static @NotNull IotDeviceScheduleExecutionView from(
            @NotNull DeviceScheduleExecution execution) {
        return new IotDeviceScheduleExecutionView(
                Objects.requireNonNull(execution, "Device 定时执行结果不能为空"));
    }

    /** @return 执行记录标识 */
    public long getExecutionId() {
        return executionId;
    }

    /** @return 定时规则标识 */
    public long getScheduleId() {
        return scheduleId;
    }

    /** @return 规则计算出的计划时间 */
    public @NotNull Instant getPlannedAt() {
        return plannedAt;
    }

    /** @return Timer 实际处理时间 */
    public @NotNull Instant getTriggeredAt() {
        return triggeredAt;
    }

    /** @return 下行生成结果 */
    public @NotNull DeviceScheduleExecutionStatus getStatus() {
        return status;
    }

    /** @return 已生成的下行 messageId */
    public @Nullable Long getMessageId() {
        return messageId;
    }

    /** @return 稳定失败 code 及当前请求 Locale 的消息；成功或过期时为空 */
    public @Nullable Failure getFailure() {
        return failure;
    }

    /** 返回调度执行的稳定失败 code 和本地化消息。 */
    public static class Failure extends BaseView {

        private final @NotNull MessageText localizedMessage;

        private final @NotNull String code;

        @Localize(source = "messageText")
        private final @NotNull String message;

        /** 创建已校验错误码的失败响应。 */
        private Failure(@NotNull String code, @NotNull String messageKey) {
            this.code = code;
            this.message = messageKey;
            this.localizedMessage = MessageText.of(messageKey);
        }

        /** @return 可持久化且跨版本稳定的失败 code */
        public @NotNull String getCode() {
            return code;
        }

        /** @return 当前请求 Locale 对应的失败消息 */
        public @NotNull String getMessage() {
            return message;
        }

        /** @return 静态消息目录中的失败文本 */
        @SuppressWarnings("ProtectedMemberInFinalClass")
        protected @NotNull MessageText messageText() {
            return localizedMessage;
        }

        /** 解析模块、内置或合法扩展失败 code；拒绝输出未校验的自由文本。 */
        private static @NotNull Failure from(@NotNull String code) {
            for (IotErrorCode errorCode : IotErrorCode.values()) {
                if (errorCode.code().equals(code)) {
                    return new Failure(errorCode.code(), errorCode.messageKey());
                }
            }
            for (IotFailureCode failureCode : IotFailureCode.builtIns()) {
                if (failureCode.code().equals(code)) {
                    return new Failure(failureCode.code(), failureCode.messageKey());
                }
            }
            IotFailureCode extension = IotFailureCode.of(code, "failure.iot.unknown");
            return new Failure(extension.code(), extension.messageKey());
        }
    }
}
