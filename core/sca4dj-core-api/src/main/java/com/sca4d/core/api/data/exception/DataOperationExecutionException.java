package com.sca4d.core.api.data.exception;

import com.sca4d.core.api.data.DataOperation;
import org.jetbrains.annotations.NotNull;

/** 表示 DataOperation 代码或其依赖在执行时发生了非数据契约异常。 */
public class DataOperationExecutionException extends RuntimeException {

    /** 抛出原始异常的节点名称。 */
    private final String operationName;

    /** 失败节点原本应写入的输出键名称。 */
    private final String outputKey;

    /**
     * 创建保留原始原因和节点位置的执行失败。
     *
     * @param operation 失败节点
     * @param cause 原始异常
     */
    public DataOperationExecutionException(@NotNull DataOperation<?> operation, @NotNull RuntimeException cause) {
        super("数据操作执行失败: " + operation.name(), cause);
        operationName = operation.name();
        outputKey = operation.output().name();
    }

    /**
     * 抛出原始异常的节点名称。
     *
     * @return 失败节点名称
     */
    public @NotNull String operationName() {
        return operationName;
    }

    /**
     * 失败节点原本应写入的键。
     *
     * @return 失败节点的输出键名称
     */
    public @NotNull String outputKey() {
        return outputKey;
    }
}
