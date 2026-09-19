package com.sca4d.core.api.flow;

import com.sca4d.core.api.concurrent.Version;

import java.util.Optional;

/** 保存 {@link FlowInstance}，并按 {@link Version} 执行乐观更新。 */
public interface FlowStore {

    /**
     * 首次保存实例。
     *
     * @param instance 初始实例
     * @return 标识尚不存在并保存成功时返回 {@code true}
     */
    boolean create(FlowInstance instance);

    /**
     * 查找实例。
     *
     * @param flowId 实例标识
     * @return 找到的实例快照
     */
    Optional<FlowInstance> find(FlowId flowId);

    /**
     * 按预期版本原子更新实例。
     *
     * @param expectedVersion 读取实例时的版本
     * @param instance 版本已经递增的新快照
     * @return 版本匹配且更新成功时返回 {@code true}
     */
    boolean update(Version expectedVersion, FlowInstance instance);
}
