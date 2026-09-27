package com.sca4d.core.api.flow;

import com.sca4d.core.api.concurrent.OptimisticLockException;
import com.sca4d.core.api.concurrent.Version;
import org.jetbrains.annotations.CheckReturnValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 创建、推进和单次消费 {@link FlowInstance}。
 *
 * <p>根据 {@link FlowEvent} 选择唯一转换，并通过 {@link FlowStore} 乐观更新实例。
 */
public class FlowEngine {

    private static final Logger LOG = LoggerFactory.getLogger(FlowEngine.class);

    private final FlowCatalog catalog;

    private final FlowStore store;

    /**
     * 创建操作实例的引擎。
     *
     * @param catalog 定义目录
     * @param store 实例存储
     */
    public FlowEngine(FlowCatalog catalog, FlowStore store) {
        this.catalog = Objects.requireNonNull(catalog, "流程定义目录不能为空");
        this.store = Objects.requireNonNull(store, "流程存储不能为空");
    }

    /**
     * 创建实例。
     *
     * @param flowId 新实例标识
     * @param definitionName 定义名称
     * @param definitionVersion 定义版本
     * @param data 初始数据
     * @param startedAt 开始时间
     * @return 已保存的初始实例
     */
    @CheckReturnValue
    public FlowInstance start(
            FlowId flowId,
            String definitionName,
            int definitionVersion,
            Map<String, ?> data,
            Instant startedAt) {
        // 根据定义创建起始节点快照；过期时间从调用方传入的开始时间计算。
        FlowDefinition definition = catalog.loadByNameAndVersion(definitionName, definitionVersion);
        FlowInstance instance = new FlowInstance(
                flowId,
                definition.name(),
                definition.version(),
                definition.graph().initialNodeId(),
                data,
                startedAt,
                startedAt.plus(definition.timeToLive()),
                new Version(0),
                false);
        if (!store.create(instance)) {
            throw new IllegalStateException("流程实例标识已经存在: " + flowId);
        }
        LOG.trace(
                "流程已创建 | flowId={} definition={}:{} node={}",
                flowId,
                definition.name(),
                definition.version(),
                instance.currentNodeId());
        return instance;
    }

    /**
     * 使用已确认事件推进实例。
     *
     * @param flowId 实例标识
     * @param event 已确认事件
     * @param advancedAt 推进时间
     * @return 进入下一节点的新快照
     */
    @CheckReturnValue
    public FlowInstance advance(FlowId flowId, FlowEvent event, Instant advancedAt) {
        // 过期或已经消费的实例不能再推进，防止已失效状态写回存储。
        FlowInstance current = activeInstance(flowId, advancedAt);
        FlowDefinition definition = catalog.loadByNameAndVersion(current.definitionName(), current.definitionVersion());
        if (definition.graph().node(current.currentNodeId()).type() == FlowNodeType.TERMINAL) {
            throw new IllegalStateException("终态流程不能继续推进: " + flowId);
        }
        // 先选择唯一转换，再以当前版本作为条件写入下一节点，冲突时不返回伪造的成功结果。
        FlowTransition transition = definition.graph().selectTransition(current, event);
        FlowInstance next = current.moveTo(transition.to());
        if (!store.update(current.version(), next)) {
            throw new OptimisticLockException("流程已经被其他调用推进: " + flowId);
        }
        LOG.trace(
                "流程已推进 | flowId={} from={} to={} event={} version={}",
                flowId,
                current.currentNodeId(),
                next.currentNodeId(),
                event.type(),
                next.version().value());
        return next;
    }

    /**
     * 单次消费已经到达终点的实例结果。
     *
     * @param flowId 实例标识
     * @param consumedAt 消费时间
     * @return 标记为已消费的新快照
     */
    @CheckReturnValue
    public FlowInstance consume(FlowId flowId, Instant consumedAt) {
        // 只有到达终点的实例可以消费；消费写入后同一结果不能再次使用。
        FlowInstance current = activeInstance(flowId, consumedAt);
        FlowDefinition definition = catalog.loadByNameAndVersion(current.definitionName(), current.definitionVersion());
        if (definition.graph().node(current.currentNodeId()).type() != FlowNodeType.TERMINAL) {
            throw new IllegalStateException("流程尚未完成，不能消费: " + flowId);
        }
        FlowInstance consumed = current.consume();
        if (!store.update(current.version(), consumed)) {
            throw new OptimisticLockException("流程结果已经被其他调用消费: " + flowId);
        }
        LOG.trace("流程结果已消费 | flowId={} version={}", flowId, consumed.version().value());
        return consumed;
    }

    private FlowInstance activeInstance(FlowId flowId, Instant instant) {
        Objects.requireNonNull(flowId, "流程实例标识不能为空");
        Objects.requireNonNull(instant, "流程操作时间不能为空");
        FlowInstance instance = store.find(flowId)
                .orElseThrow(() -> new IllegalArgumentException("流程实例不存在: " + flowId));
        if (instance.isExpiredAt(instant)) {
            throw new IllegalStateException("流程实例已经过期: " + flowId);
        }
        if (instance.isConsumed()) {
            throw new IllegalStateException("流程结果已经消费: " + flowId);
        }
        return instance;
    }
}
