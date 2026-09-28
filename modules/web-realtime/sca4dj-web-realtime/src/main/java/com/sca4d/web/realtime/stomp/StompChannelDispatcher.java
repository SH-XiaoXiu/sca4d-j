package com.sca4d.web.realtime.stomp;

import com.sca4d.web.realtime.config.WebRealtimeProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 在 Spring 通道分派前预留容量，按连接串行执行一条消息的全部订阅处理器。
 *
 * <p>同一实例必须同时作为通道的首个拦截器和 Executor，且不得再启用 Spring 的顺序队列。
 * 数量和载荷预算包含正在执行的消息；超限关闭对应连接，断连时丢弃尚未执行的消息。
 */
@ApiStatus.Internal
public final class StompChannelDispatcher implements ChannelInterceptor, TaskExecutor, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(StompChannelDispatcher.class);

    private final @NotNull String direction;

    private final @NotNull WebSocketSessions sockets;

    private final int capacity;

    private final int maxBytes;

    private final int connectionCapacity;

    private final int connectionMaxBytes;

    private final @NotNull ThreadPoolExecutor workers;

    private final @NotNull Map<String, SessionQueue> sessions = new HashMap<>();

    private final @NotNull ThreadLocal<Submission> submitting = new ThreadLocal<>();

    private final @NotNull Consumer<String> closeListener = this::disconnected;

    /** 以下状态只在当前对象的监视器内读写。 */
    private int pending;

    private long pendingBytes;

    private boolean stopped;

    /** 创建某一方向独立的容量预算、固定工作线程和断连清理监听。 */
    public StompChannelDispatcher(
            @NotNull String direction,
            @NotNull WebRealtimeProperties.Stomp properties,
            @NotNull WebSocketSessions sockets) {
        this.direction = Objects.requireNonNull(direction, "STOMP 通道方向不能为空");
        this.sockets = Objects.requireNonNull(sockets, "WebSocket 连接句柄不能为空");
        capacity = properties.getChannelQueueCapacity();
        maxBytes = properties.getChannelQueueMaxBytes();
        connectionCapacity = properties.getConnectionQueueCapacity();
        connectionMaxBytes = properties.getConnectionQueueMaxBytes();
        workers = new ThreadPoolExecutor(
                properties.getChannelWorkerCount(),
                properties.getChannelWorkerCount(),
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                Thread.ofPlatform().name("sca4dj-stomp-" + direction + "-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        sockets.addCloseListener(closeListener);
    }

    /** 在任何业务拦截器和执行器提交之前占用预算，失败时不创建排队任务。 */
    @Override
    public @Nullable Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        if (sessionId == null) {
            throw new MessageDeliveryException("STOMP 通道消息缺少 Session ID");
        }
        // Transport 关闭后的 DISCONNECT 必须同步交给 broker，不能因业务队列已满而遗漏清理。
        boolean cleanup = SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) == SimpMessageType.DISCONNECT
                && !sockets.accepts(sessionId);
        long bytes = payloadBytes(message.getPayload());
        synchronized (this) {
            SessionQueue queue = sessions.get(sessionId);
            if (cleanup) {
                submitting.set(new Submission(null, 0, submitting.get()));
                return message;
            }
            // 关闭后到达的 broker ACK 和事件已经没有接收者，不再入队或重复触发关闭。
            if (stopped || !sockets.accepts(sessionId)) {
                return null;
            }
            if (pending < capacity
                    && bytes <= maxBytes - pendingBytes
                    && (queue == null
                            || !queue.closed
                                    && queue.pending < connectionCapacity
                                    && bytes <= connectionMaxBytes - queue.bytes)
                    && bytes <= connectionMaxBytes) {
                if (queue == null) {
                    queue = new SessionQueue(sessionId);
                    sessions.put(sessionId, queue);
                }
                Submission submission = new Submission(queue, bytes, submitting.get());
                queue.messages.add(submission);
                queue.pending++;
                queue.bytes += bytes;
                pending++;
                pendingBytes += bytes;
                submitting.set(submission);
                return message;
            }
            LOG.warn(
                    "STOMP 通道容量不足，停止接收该连接消息 | direction={} sessionId={} pending={} pendingBytes={}",
                    direction,
                    sessionId,
                    pending,
                    pendingBytes);
        }
        sockets.overloaded(sessionId);
        // 不抛 RejectedExecutionException，避免 ExecutorSubscribableChannel 回退到调用线程执行。
        throw new MessageDeliveryException("STOMP 通道拒绝消息，连接已关闭");
    }

    /** 收集 Spring 为当前消息创建的处理任务，等待所有 preSend 检查及任务提交完成。 */
    @Override
    public void execute(@NotNull Runnable command) {
        Submission submission = submitting.get();
        if (submission == null) {
            throw new MessageDeliveryException("STOMP Executor 必须与通道容量拦截器一起使用");
        }
        submission.tasks.add(Objects.requireNonNull(command, "STOMP 处理任务不能为空"));
    }

    /** 恢复嵌套调用的提交上下文，成功时使消息可执行，失败时归还预留容量。 */
    @Override
    public void afterSendCompletion(
            @NotNull Message<?> message,
            @NotNull MessageChannel channel,
            boolean sent,
            @Nullable Exception exception) {
        Submission submission = submitting.get();
        if (submission == null) {
            return;
        }
        if (submission.previous == null) {
            submitting.remove();
        } else {
            submitting.set(submission.previous);
        }
        submission.previous = null;
        if (submission.queue == null) {
            if (sent && exception == null) {
                submission.tasks.forEach(Runnable::run);
            }
            return;
        }
        boolean scheduled;
        synchronized (this) {
            SessionQueue queue = submission.queue;
            if (submission.released) {
                submission.tasks.clear();
                return;
            }
            if (!sent || exception != null || queue.closed) {
                queue.messages.remove(submission);
                release(submission);
            } else {
                submission.ready = true;
            }
            scheduled = schedule(queue);
        }
        if (!scheduled) {
            sockets.overloaded(submission.queue.sessionId);
        }
    }

    /** 为连接安排一次分派；一次只执行一条消息，让其他连接也能取得工作线程。 */
    private boolean schedule(SessionQueue queue) {
        if (queue.running) {
            return true;
        }
        Submission first = queue.messages.peek();
        if (first == null) {
            sessions.remove(queue.sessionId, queue);
        } else if (first.ready && !stopped && !queue.closed) {
            queue.running = true;
            queue.scheduled = () -> dispatch(queue);
            try {
                workers.execute(queue.scheduled);
            } catch (RejectedExecutionException exception) {
                queue.running = false;
                queue.scheduled = null;
                disconnected(queue.sessionId);
                LOG.warn(
                        "STOMP 工作线程拒绝分派，已清理连接队列 | direction={} sessionId={}",
                        direction,
                        queue.sessionId);
                return false;
            }
        }
        return true;
    }

    /** 执行同一消息的订阅处理器，并在任何结束路径上归还容量。 */
    private void dispatch(SessionQueue queue) {
        Submission submission;
        synchronized (this) {
            queue.scheduled = null;
            submission = queue.messages.poll();
            if (submission == null) {
                queue.running = false;
                sessions.remove(queue.sessionId, queue);
                return;
            }
        }
        try {
            for (Runnable task : submission.tasks) {
                if (queue.closed) {
                    break;
                }
                task.run();
            }
        } catch (RuntimeException exception) {
            LOG.warn(
                    "STOMP 消息处理失败，连接已关闭 | direction={} sessionId={} errorType={}",
                    direction,
                    queue.sessionId,
                    exception.getClass().getSimpleName());
            sockets.overloaded(queue.sessionId);
        } finally {
            boolean scheduled;
            synchronized (this) {
                release(submission);
                queue.running = false;
                scheduled = schedule(queue);
            }
            if (!scheduled) {
                sockets.overloaded(queue.sessionId);
            }
        }
    }

    /** 幂等归还一个消息的预算；调用方必须持有当前对象的监视器。 */
    private void release(Submission submission) {
        if (submission.released) {
            return;
        }
        submission.released = true;
        submission.tasks.clear();
        pending--;
        pendingBytes -= submission.bytes;
        SessionQueue queue = Objects.requireNonNull(submission.queue);
        queue.pending--;
        queue.bytes -= submission.bytes;
    }

    /** 断连立即释放未执行消息，正在执行的处理器自行完成并归还预算。 */
    private synchronized void disconnected(String sessionId) {
        SessionQueue queue = sessions.get(sessionId);
        if (queue == null) {
            return;
        }
        queue.closed = true;
        // 已断开的连接不能继续占用线程池的待调度槽，否则反复重连可耗尽线程池队列。
        if (queue.scheduled != null && workers.remove(queue.scheduled)) {
            queue.scheduled = null;
            queue.running = false;
        }
        Submission submission;
        while ((submission = queue.messages.poll()) != null) {
            // 尚在发送线程收集任务的列表由 afterSendCompletion 清理，避免并发修改 ArrayList。
            if (!submission.ready) {
                submission.released = true;
                pending--;
                pendingBytes -= submission.bytes;
                queue.pending--;
                queue.bytes -= submission.bytes;
            } else {
                release(submission);
            }
        }
        if (!queue.running) {
            sessions.remove(sessionId, queue);
        }
    }

    /** 停止接受消息，释放排队引用并中断工作线程。 */
    @Override
    public void close() {
        synchronized (this) {
            stopped = true;
            List.copyOf(sessions.keySet()).forEach(this::disconnected);
            workers.shutdownNow();
            sessions.clear();
        }
        sockets.removeCloseListener(closeListener);
    }

    /** 计算通道保留的载荷字节；无载荷的协议控制消息仍占用消息数量预算。 */
    private static long payloadBytes(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes.length;
        }
        if (payload instanceof ByteBuffer bytes) {
            return bytes.remaining();
        }
        if (payload instanceof String text) {
            // 一个 UTF-16 code unit 在 UTF-8 中最多占 3 字节，按上界预留避免低估非 ASCII 消息。
            return (long) text.length() * 3;
        }
        throw new MessageDeliveryException("STOMP 通道只接受字节或文本载荷");
    }

    /** 保存一条连接已预留的消息及其运行状态。 */
    private static final class SessionQueue {
        private final String sessionId;

        private final ArrayDeque<Submission> messages = new ArrayDeque<>();

        private int pending;

        private long bytes;

        private boolean running;

        private @Nullable Runnable scheduled;

        private volatile boolean closed;

        private SessionQueue(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    /** 保存一次通道发送的任务集合和嵌套提交上下文。 */
    private static final class Submission {
        private final @Nullable SessionQueue queue;

        private final long bytes;

        private final List<Runnable> tasks = new ArrayList<>();

        private @Nullable Submission previous;

        private boolean ready;

        private boolean released;

        private Submission(@Nullable SessionQueue queue, long bytes, @Nullable Submission previous) {
            this.queue = queue;
            this.bytes = bytes;
            this.previous = previous;
        }
    }
}
