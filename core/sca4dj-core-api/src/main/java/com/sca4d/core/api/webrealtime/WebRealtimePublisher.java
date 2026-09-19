package com.sca4d.core.api.webrealtime;

import com.sca4d.core.api.event.Event;
import org.jetbrains.annotations.NotNull;

/** 向浏览器实时连接发布允许丢失且不回放的消息。 */
public interface WebRealtimePublisher {

    /**
     * 发布实时消息；存在活跃事务时在提交后发布，否则立即发布。
     *
     * @param target 由共享索引解析的目标
     * @param event 发送给浏览器的业务事件
     */
    void publish(@NotNull WebRealtimeTarget target, @NotNull Event<?> event);

    /**
     * 立即发布已经提交完成或不依赖事务结果的实时消息。
     *
     * @param target 由共享索引解析的目标
     * @param event 发送给浏览器的业务事件
     */
    void publishNow(@NotNull WebRealtimeTarget target, @NotNull Event<?> event);
}
