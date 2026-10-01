package com.sca4d.web.realtime.connection;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sca4d.core.api.authentication.SessionInvalidationListener;
import com.sca4d.core.api.event.Event;
import com.sca4d.core.api.event.EventType;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.webrealtime.WebRealtimeMessage;
import com.sca4d.core.api.webrealtime.WebRealtimePublisher;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import com.sca4d.web.realtime.stomp.WebRealtimeProtocol;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Objects;

/** 把应用实时事件转换成 WebRealtimeMessage 并交给固定 MessageBus。 */
public class DefaultWebRealtimePublisher implements WebRealtimePublisher, SessionInvalidationListener {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWebRealtimePublisher.class);

    private final @NotNull WebRealtimeProperties properties;

    private final @NotNull Clock clock;

    private final @NotNull MessageBus messages;

    /**
     * @param messages Web Realtime 消息总线
     * @param properties Web Realtime 配置
     * @param clock Session 变化事件的时间来源
     */
    public DefaultWebRealtimePublisher(
            @NotNull MessageBus messages,
            @NotNull WebRealtimeProperties properties,
            @NotNull Clock clock) {
        this.messages = Objects.requireNonNull(messages, "MessageBus 不能为空");
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        this.clock = Objects.requireNonNull(clock, "Web Realtime 时钟不能为空");
    }

    @Override
    public @NotNull DispatchReceipt publish(
            @NotNull WebRealtimeTarget target,
            @NotNull Event<?> event,
            @NotNull DispatchPolicy policy) {
        Event<?> checkedEvent = Objects.requireNonNull(event, "Web Realtime 事件不能为空");
        return messages.dispatch(
                new WebRealtimeMessage(
                        target,
                        checkedEvent,
                        false,
                        checkedEvent.occurredAt().plus(properties.getNodeMessageTtl())),
                policy);
    }

    @Override
    public void invalidated(long accountId) {
        Event<ObjectNode> event = new Event<>(
                new EventType<>(
                        WebRealtimeProtocol.AUTHENTICATION_SESSION_CHANGED_EVENT,
                        1,
                        ObjectNode.class),
                null,
                clock.instant(),
                JsonNodeFactory.instance.objectNode());
        DispatchPolicy policy = TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()
                        ? DispatchPolicy.afterCommit()
                        : DispatchPolicy.immediate();
        try {
            DispatchReceipt receipt = messages.dispatch(
                    new WebRealtimeMessage(
                            WebRealtimeTarget.account(accountId),
                            event,
                            true,
                            event.occurredAt().plus(properties.getNodeMessageTtl())),
                    policy);
            LOG.trace(
                    "Session 变化的 Web Realtime 消息已登记 | accountId={} dispatchStatus={}",
                    accountId,
                    receipt.status());
        } catch (RuntimeException exception) {
            LOG.warn(
                    "Session 变化的 Web Realtime 消息登记失败，客户端后续请求仍会读取最新状态 | accountId={}",
                    accountId,
                    exception);
        }
    }
}
