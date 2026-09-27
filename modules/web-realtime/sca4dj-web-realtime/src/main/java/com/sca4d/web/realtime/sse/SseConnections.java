package com.sca4d.web.realtime.sse;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.connection.ConnectionId;
import com.sca4d.web.realtime.config.WebRealtimeProperties;
import com.sca4d.web.realtime.connection.BrowserWrite;
import com.sca4d.web.realtime.connection.LocalConnection;
import com.sca4d.web.realtime.connection.LocalConnectionRegistry;
import com.sca4d.web.realtime.connection.NodeRouter;
import com.sca4d.web.realtime.connection.RedisConnectionRegistry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 建立 SSE 连接，并用有界队列串行写出浏览器信封和心跳。 */
@ApiStatus.Internal
public class SseConnections implements WebRealtimeSseConnector {

    private static final Logger LOG = LoggerFactory.getLogger(SseConnections.class);

    private static final Object HEARTBEAT = new Object();

    private final @NotNull RedisConnectionRegistry shared;

    private final @NotNull LocalConnectionRegistry local;

    private final @NotNull NodeRouter nodeTransport;

    private final @NotNull WebRealtimeProperties properties;

    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();

    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor();

    private final Set<SseConnection> connections = ConcurrentHashMap.newKeySet();

    /** 创建使用共享索引和当前节点临时传输的 SSE 连接入口。 */
    public SseConnections(
            @NotNull RedisConnectionRegistry shared,
            @NotNull LocalConnectionRegistry local,
            @NotNull NodeRouter nodeTransport,
            @NotNull WebRealtimeProperties properties) {
        this.shared = Objects.requireNonNull(shared, "Web Realtime 共享索引不能为空");
        this.local = Objects.requireNonNull(local, "本机连接注册表不能为空");
        this.nodeTransport = Objects.requireNonNull(nodeTransport, "RabbitMQ 节点传输不能为空");
        this.properties = Objects.requireNonNull(properties, "Web Realtime 配置不能为空");
        heartbeats.scheduleAtFixedRate(
                this::sendHeartbeats,
                properties.getHeartbeatInterval().toMillis(),
                properties.getHeartbeatInterval().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /** 建立连接，先登记本机句柄，再原子写入 Redis 自动身份目标。 */
    @Override
    public @NotNull SseEmitter connect(@NotNull Session session) {
        // 先创建本机有界写出句柄，再原子登记 Redis 自动身份目标。
        Session checkedSession = Objects.requireNonNull(session, "Session 不能为空");
        ConnectionId connectionId = new ConnectionId(UUID.randomUUID().toString());
        SseEmitter emitter = new SseEmitter(properties.getSseConnectionTimeout().toMillis());
        SseConnection connection = new SseConnection(connectionId, emitter);
        local.add(connection);
        try {
            shared.register(connectionId, nodeTransport.nodeId(), checkedSession);
        } catch (RuntimeException exception) {
            connection.close();
            throw exception;
        }
        connections.add(connection);

        // Spring MVC 的所有终止回调都进入同一个幂等关闭流程。
        emitter.onCompletion(connection::close);
        emitter.onTimeout(connection::close);
        emitter.onError(error -> connection.close());
        connection.start();
        connection.heartbeat();
        LOG.debug(
                "SSE 连接已登记 | connectionId={} sessionId={} accountId={} connectionCount={}",
                connectionId,
                checkedSession.id(),
                checkedSession.account().accountId(),
                connections.size());
        return emitter;
    }

    /** 关闭全部 SSE 连接以及写出和心跳执行器。 */
    @Override
    public void close() {
        Set.copyOf(connections).forEach(SseConnection::close);
        heartbeats.shutdownNow();
        writers.shutdownNow();
    }

    /** 给全部 SSE 连接加入心跳；队列已满时连接会关闭。 */
    private void sendHeartbeats() {
        connections.forEach(SseConnection::heartbeat);
    }

    /** 保存一个 SseEmitter 和固定容量写出队列。 */
    private class SseConnection implements LocalConnection {

        private final @NotNull ConnectionId id;

        private final @NotNull SseEmitter emitter;

        private final ArrayBlockingQueue<Object> queue = new ArrayBlockingQueue<>(
                properties.getConnectionQueueCapacity());

        private final AtomicBoolean closed = new AtomicBoolean();

        private volatile Future<?> writer;

        /** 创建尚未开始写出的本机 SSE 连接。 */
        private SseConnection(@NotNull ConnectionId id, @NotNull SseEmitter emitter) {
            this.id = id;
            this.emitter = emitter;
        }

        /** @return 当前 SSE 连接标识 */
        @Override
        public @NotNull ConnectionId id() {
            return id;
        }

        /** 启动独立虚拟线程串行写出任务。 */
        private void start() {
            writer = writers.submit(this::write);
        }

        /** 加入浏览器投递；慢连接队列已满时立即关闭。 */
        @Override
        public void offer(@NotNull BrowserWrite delivery) {
            if (!closed.get() && !queue.offer(Objects.requireNonNull(delivery, "连接投递不能为空"))) {
                LOG.warn(
                        "SSE 慢连接队列已满，连接已关闭 | connectionId={} queueCapacity={}",
                        id,
                        properties.getConnectionQueueCapacity());
                close();
            }
        }

        /** 加入 SSE 心跳；队列已满时立即关闭。 */
        private void heartbeat() {
            if (!closed.get() && !queue.offer(HEARTBEAT)) {
                LOG.warn(
                        "SSE 慢连接无法接收心跳，连接已关闭 | connectionId={} queueCapacity={}",
                        id,
                        properties.getConnectionQueueCapacity());
                close();
            }
        }

        /** 持续串行写出心跳或统一浏览器信封。 */
        private void write() {
            try {
                while (!closed.get()) {
                    // 单一虚拟线程严格保持心跳和业务事件的入队顺序。
                    Object item = queue.take();
                    if (item == HEARTBEAT) {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                        continue;
                    }
                    BrowserWrite delivery = (BrowserWrite) item;
                    emitter.send(
                            SseEmitter.event()
                                    .name(delivery.envelope().getEventCode())
                                    .data(delivery.envelope(), MediaType.APPLICATION_JSON));
                    if (delivery.closeAfter()) {
                        close();
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException | IllegalStateException exception) {
                LOG.debug(
                        "SSE 连接写出结束 | connectionId={} errorType={}",
                        id,
                        exception.getClass().getSimpleName());
                close();
            }
        }

        /** 幂等关闭 SseEmitter 并删除本机与 Redis 索引。 */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            // 先停止新投递并删除共享索引，再结束写出线程和 HTTP 响应。
            connections.remove(this);
            try {
                local.remove(id);
            } catch (RuntimeException exception) {
                LOG.warn(
                        "SSE 连接共享索引删除失败，将由租约过期清理 | connectionId={} errorType={}",
                        id,
                        exception.getClass().getSimpleName());
            }
            Future<?> currentWriter = writer;
            if (currentWriter != null) {
                currentWriter.cancel(true);
            }
            emitter.complete();
        }
    }
}
