package com.sca4d.common.messaging.outbox;

import lombok.RequiredArgsConstructor;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;

/**
 * 消息 Outbox 投递的当前调度实现。
 */
@RequiredArgsConstructor
@DisallowConcurrentExecution
public class MessageOutboxDispatcher implements Job {

    private final MessageOutboxDeliveryService deliveryService;

    /**
     * 按配置周期触发一轮到期 Outbox 消息投递。
     */
    public void dispatch() {
        deliveryService.dispatchDueMessages();
    }

    /** @param context Quartz 单次触发上下文 */
    @Override
    public void execute(JobExecutionContext context) {
        dispatch();
    }
}
