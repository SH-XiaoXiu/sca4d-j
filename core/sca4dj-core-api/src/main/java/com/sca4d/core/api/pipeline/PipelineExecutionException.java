package com.sca4d.core.api.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** 标明 Pipeline 中实际失败的阶段，同时保留原始异常。 */
public class PipelineExecutionException extends RuntimeException {

    /** 抛出原始异常或产生非法输出的阶段名称。 */
    private final @NotNull String stageCode;

    /**
     * 创建阶段执行失败结果。
     *
     * @param stageCode 发生失败的稳定阶段名称
     * @param cause 阶段抛出的原始异常
     * @throws NullPointerException 阶段名称或原始异常为空时抛出
     */
    public PipelineExecutionException(@NotNull String stageCode, @NotNull RuntimeException cause) {
        super(
                "Pipeline 阶段执行失败: " + Objects.requireNonNull(stageCode, "Pipeline 阶段名称不能为空"),
                Objects.requireNonNull(cause, "Pipeline 阶段原始异常不能为空"));
        this.stageCode = stageCode;
    }

    /**
     * 返回失败阶段。
     *
     * @return 发生失败的稳定阶段名称
     */
    public @NotNull String stageCode() {
        return stageCode;
    }
}
