package com.sca4d.schedule;

import com.sca4d.common.exception.BizErrorCode;
import com.sca4d.common.exception.CodeRange;
import com.sca4d.common.exception.ResponseCode;
import org.jetbrains.annotations.NotNull;

/** Schedule 创建、修改、同步和执行操作使用的错误码。 */
@CodeRange(start = 3300, end = 3399)
public enum ScheduleErrorCode implements BizErrorCode {
    /** 指定 Schedule 不存在或已经删除。 */
    SCHEDULE_NOT_FOUND(3300, "error.schedule.not-found", ResponseCode.NOT_FOUND),
    /** Schedule 定义缺少必填字段或组合无效。 */
    SCHEDULE_INVALID(3301, "error.schedule.invalid", ResponseCode.BAD_REQUEST),
    /** Schedule 已完成、失败或删除，不能再修改。 */
    SCHEDULE_TERMINAL(3302, "error.schedule.terminal", ResponseCode.CONFLICT),
    /** 替换 Schedule 时试图修改已经确定的任务类型。 */
    SCHEDULE_TASK_TYPE_IMMUTABLE(3303, "error.schedule.task-type-immutable", ResponseCode.CONFLICT),
    /** Schedule 版本与 If-Match 提交的版本不一致。 */
    SCHEDULE_VERSION_CHANGED(3304, "error.schedule.version-changed", ResponseCode.PRECONDITION_FAILED),
    /** 修改请求没有提供 If-Match 版本。 */
    SCHEDULE_IF_MATCH_REQUIRED(3305, "error.schedule.if-match-required", ResponseCode.PRECONDITION_REQUIRED),
    /** Schedule 使用了没有注册的任务类型。 */
    SCHEDULE_TASK_TYPE_UNKNOWN(3306, "error.schedule.task-type-unknown", ResponseCode.BAD_REQUEST),
    /** Schedule 内容版本没有对应的升级或解析实现。 */
    SCHEDULE_PAYLOAD_VERSION_UNKNOWN(3307, "error.schedule.payload-version-unknown", ResponseCode.CONFLICT),
    /** 指定 Schedule 执行记录不存在。 */
    SCHEDULE_EXECUTION_NOT_FOUND(3308, "error.schedule.execution-not-found", ResponseCode.NOT_FOUND),
    /** Schedule 当前状态不允许人工执行。 */
    SCHEDULE_EXECUTION_NOT_ALLOWED(3309, "error.schedule.execution-not-allowed", ResponseCode.CONFLICT),
    /** 数据库定义同步到 Quartz 失败。 */
    SCHEDULE_SYNCHRONIZATION_FAILED(3310, "error.schedule.synchronization-failed", ResponseCode.INTERNAL_SERVICE_ERROR);

    private final int code;
    private final String messageKey;
    private final ResponseCode category;

    ScheduleErrorCode(int code, String messageKey, ResponseCode category) {
        this.code = code;
        this.messageKey = messageKey;
        this.category = category;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public @NotNull String getMessageKey() {
        return messageKey;
    }

    @Override
    public @NotNull ResponseCode getCategory() {
        return category;
    }
}
