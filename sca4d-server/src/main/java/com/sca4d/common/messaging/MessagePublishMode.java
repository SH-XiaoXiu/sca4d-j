package com.sca4d.common.messaging;

/**
 * 消息发布模式。
 */
public enum MessagePublishMode {

    /** 立即发送，不承诺可靠。 */
    DIRECT,

    /** 当前事务提交后发送。 */
    AFTER_COMMIT,

    /** 写入 Outbox 后由后台任务可靠投递。 */
    OUTBOX
}
