package com.sca4d.core.api.job;

import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.runtime.InstanceId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 返回一次广播固定的目标及各目标持久化 Run；无记录广播不返回 Run。 */
public class JobDispatch {

    private final @NotNull Id id;

    private final @NotNull Map<InstanceId, Id> runs;

    private final @NotNull Set<InstanceId> targets;

    /**
     * 保存提交时固定的目标快照和已创建的持久化运行。
     *
     * @param id 本次广播标识
     * @param targets 提交时固定的目标实例
     * @param runs 已创建的 Run；无记录广播或跳过的目标不在其中
     */
    public JobDispatch(@NotNull Id id, @NotNull Set<InstanceId> targets, @NotNull Map<InstanceId, Id> runs) {
        this.id = Objects.requireNonNull(id, "广播标识不能为空");
        this.targets = Set.copyOf(Objects.requireNonNull(targets, "广播目标不能为空"));
        this.runs = Map.copyOf(Objects.requireNonNull(runs, "广播运行不能为空"));
        if (this.targets.isEmpty() || !this.targets.containsAll(this.runs.keySet())) {
            throw new IllegalArgumentException("广播目标不能为空，且必须包含全部运行目标");
        }
    }

    /** @return 一次广播的标识 */
    public @NotNull Id id() {
        return id;
    }

    /** @return 按目标实例索引的持久化 Run */
    public @NotNull @Unmodifiable Map<InstanceId, Id> runs() {
        return runs;
    }

    /** @return 触发时固定的全部目标实例；未创建 Run 的目标仍保留在集合中 */
    public @NotNull @Unmodifiable Set<InstanceId> targets() {
        return targets;
    }
}
