package com.sca4d.common.realtime.sse;

import cn.hutool.core.util.IdUtil;
import com.sca4d.common.realtime.RealtimeEnvelope;
import com.sca4d.common.realtime.RealtimeTarget;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SSE 本机连接注册表。
 */
public class SseConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseConnectionRegistry.class);

    private final ConcurrentMap<String, SseEmitter> connections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<RealtimeTarget>> connectionTargets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> targetIndex = new ConcurrentHashMap<>();

    /**
     * 注册 SSE 连接。
     *
     * @param emitter SSE emitter
     * @param targets 订阅目标
     * @return 连接 ID
     */
    public @NotNull String register(@NotNull SseEmitter emitter, @NotNull Collection<RealtimeTarget> targets) {
        String connectionId = IdUtil.fastSimpleUUID();
        List<RealtimeTarget> targetList = List.copyOf(targets);
        connections.put(connectionId, emitter);
        connectionTargets.put(connectionId, targetList);
        for (RealtimeTarget target : targetList) {
            targetIndex.computeIfAbsent(target.routingKey(), key -> ConcurrentHashMap.newKeySet()).add(connectionId);
        }
        log.trace(
                "SSE 本机连接已注册 | connectionId={} targetCount={} connectionCount={}",
                connectionId,
                targetList.size(),
                connections.size());
        return connectionId;
    }

    /**
     * 移除 SSE 连接。
     *
     * @param connectionId 连接 ID
     */
    public void remove(@NotNull String connectionId) {
        connections.remove(connectionId);
        List<RealtimeTarget> targets = connectionTargets.remove(connectionId);
        if (targets == null) {
            return;
        }
        for (RealtimeTarget target : targets) {
            Set<String> connectionIds = targetIndex.get(target.routingKey());
            if (connectionIds != null) {
                connectionIds.remove(connectionId);
                if (connectionIds.isEmpty()) {
                    targetIndex.remove(target.routingKey(), connectionIds);
                }
            }
        }
        log.trace(
                "SSE 本机连接已移除 | connectionId={} targetCount={} connectionCount={}",
                connectionId,
                targets.size(),
                connections.size());
    }

    /**
     * 向本机连接投递实时消息。
     *
     * @param routingKey RabbitMQ routing key
     * @param envelope 实时消息载荷
     */
    public void send(@NotNull String routingKey, @NotNull RealtimeEnvelope<?> envelope) {
        Set<String> connectionIds = targetIndex.get(routingKey);
        if (connectionIds == null || connectionIds.isEmpty()) {
            log.trace("SSE 实时消息没有本机订阅连接 | event={} routingKey={}", envelope.getEvent(), routingKey);
            return;
        }
        int deliveredCount = 0;
        int removedCount = 0;
        for (String connectionId : connectionIds) {
            SseEmitter emitter = connections.get(connectionId);
            if (emitter == null) {
                continue;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(envelope.getId())
                        .name(envelope.getEvent())
                        .data(envelope));
                deliveredCount++;
            } catch (IOException | IllegalStateException ignored) {
                remove(connectionId);
                emitter.complete();
                removedCount++;
            }
        }
        log.trace(
                "SSE 实时消息本机投递完成 | event={} deliveredCount={} removedCount={} routingKey={}",
                envelope.getEvent(),
                deliveredCount,
                removedCount,
                routingKey);
    }

    /**
     * 发送心跳。
     */
    public void heartbeat() {
        int sentCount = 0;
        int removedCount = 0;
        for (var entry : connections.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event().name("heartbeat").comment("heartbeat"));
                sentCount++;
            } catch (IOException | IllegalStateException ignored) {
                remove(entry.getKey());
                entry.getValue().complete();
                removedCount++;
            }
        }
        log.trace(
                "SSE 本机连接心跳完成 | sentCount={} removedCount={} connectionCount={}",
                sentCount,
                removedCount,
                connections.size());
    }
}
