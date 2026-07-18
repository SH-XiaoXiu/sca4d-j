package com.sca4d.im.realtime;

import com.sca4d.common.realtime.RealtimePublishRequest;
import com.sca4d.common.realtime.RealtimePublisher;
import com.sca4d.common.realtime.RealtimeTarget;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * IM 实时事件发布器。
 */
@Component
@RequiredArgsConstructor
public class ImRealtimePublisher {

    private static final String USER_QUEUE = "/queue/im";
    private static final String EVENT_MESSAGE_CREATED = "im.message.created";
    private static final String EVENT_MESSAGE_REVOKED = "im.message.revoked";
    private static final String EVENT_CONVERSATION_UPDATED = "im.conversation.updated";
    private static final String EVENT_READ_UPDATED = "im.read.updated";

    private final @NotNull ObjectProvider<RealtimePublisher> realtimePublisher;

    /**
     * 发布会话更新事件。
     *
     * @param subjectIds 会话成员 Subject 主键
     * @param conversationId 会话主键
     */
    public void conversationUpdated(
            @NotNull Collection<Long> subjectIds, @NotNull Long conversationId) {
        publish(subjectIds, EVENT_CONVERSATION_UPDATED, new ImRealtimePayload(conversationId, null, null));
    }

    /**
     * 发布消息创建事件。
     *
     * @param subjectIds 会话成员 Subject 主键
     * @param conversationId 会话主键
     * @param messageId 消息主键
     */
    public void messageCreated(
            @NotNull Collection<Long> subjectIds,
            @NotNull Long conversationId,
            @NotNull Long messageId) {
        ImRealtimePayload payload = new ImRealtimePayload(conversationId, messageId, null);
        publish(subjectIds, EVENT_MESSAGE_CREATED, payload);
        publish(subjectIds, EVENT_CONVERSATION_UPDATED, payload);
    }

    /**
     * 发布消息撤回事件。
     *
     * @param subjectIds 会话成员 Subject 主键
     * @param conversationId 会话主键
     * @param messageId 消息主键
     */
    public void messageRevoked(
            @NotNull Collection<Long> subjectIds,
            @NotNull Long conversationId,
            @NotNull Long messageId) {
        ImRealtimePayload payload = new ImRealtimePayload(conversationId, messageId, null);
        publish(subjectIds, EVENT_MESSAGE_REVOKED, payload);
        publish(subjectIds, EVENT_CONVERSATION_UPDATED, payload);
    }

    /**
     * 发布已读状态更新事件。
     *
     * @param subjectIds 会话成员 Subject 主键
     * @param conversationId 会话主键
     * @param messageId 消息主键
     */
    public void readUpdated(
            @NotNull Collection<Long> subjectIds,
            @NotNull Long conversationId,
            @NotNull Long messageId) {
        publish(subjectIds, EVENT_READ_UPDATED, new ImRealtimePayload(conversationId, messageId, 0L));
    }

    /**
     * 将同一业务事件投递给全部唯一成员 Subject。
     *
     * <p>该方法不查询数据库；成员集合由调用方一次读取。发布次数严格等于成员中的唯一非空 Subject 数。
     *
     * @param subjectIds 已由调用方批量读取的会话成员 Subject 主键
     * @param event IM 模块定义并与前端共享的固定事件编码
     * @param payload 当前事件载荷
     */
    private void publish(
            @NotNull Collection<Long> subjectIds,
            @NotNull String event,
            @NotNull ImRealtimePayload payload) {
        RealtimePublisher publisher = realtimePublisher.getIfAvailable();
        if (publisher == null) {
            return;
        }
        LinkedHashSet<Long> uniqueSubjectIds = new LinkedHashSet<>();
        for (Long subjectId : subjectIds) {
            if (subjectId != null) {
                uniqueSubjectIds.add(subjectId);
            }
        }
        // 纯内存去重后，每个唯一 Subject 恰好发布 1 次；消息创建或撤回的两个不同事件分别执行本规则。
        for (Long subjectId : uniqueSubjectIds) {
            publisher.publish(RealtimePublishRequest.websocketUserQueue(
                    RealtimeTarget.subject(subjectId), USER_QUEUE, event, payload));
        }
    }
}
