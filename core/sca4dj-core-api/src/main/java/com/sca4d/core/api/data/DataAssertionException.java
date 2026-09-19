package com.sca4d.core.api.data;

import com.sca4d.core.api.utils.TextUtils;
import org.jetbrains.annotations.Nullable;

/** 表示普通数据操作或 DataGraph 值没有满足已声明的数据契约。 */
public final class DataAssertionException extends RuntimeException {

    private static final int MAX_SAFE_SAMPLE_LENGTH = 256;

    /** 无法定位具体字段时为空。 */
    private final @Nullable String field;

    /** 断言发生在节点执行前时为空。 */
    private final @Nullable String operationName;

    /** 断言发生在节点执行前时为空。 */
    private final @Nullable String outputKey;

    /** 调用方没有提供可公开样本时为空。 */
    private final @Nullable String safeSample;

    /**
     * 创建不携带数据值的契约失败。
     *
     * @param message 不包含敏感值的失败原因
     */
    public DataAssertionException(String message) {
        this(message, null);
    }

    /**
     * 创建携带调用方确认已脱敏短文本的契约失败。
     *
     * @param message 不包含敏感值的失败原因
     * @param safeSample 已脱敏且不超过 256 个字符的样本；不需要时为空
     */
    public DataAssertionException(String message, @Nullable String safeSample) {
        this(null, null, null, message, safeSample, null);
    }

    /**
     * 创建带字段位置的数据契约失败。
     *
     * @param field 失败字段；无法定位时为空
     * @param message 不包含敏感值的失败原因
     * @param safeSample 已脱敏且不超过 256 个字符的样本；不需要时为空
     */
    public DataAssertionException(
            @Nullable String field,
            String message,
            @Nullable String safeSample) {
        this(field, null, null, message, safeSample, null);
    }

    /**
     * 创建保留原始原因和字段位置的数据契约失败。
     *
     * @param field 失败字段；无法定位时为空
     * @param message 不包含敏感值的失败原因
     * @param safeSample 已脱敏且不超过 256 个字符的样本；不需要时为空
     * @param cause 原始失败原因
     */
    public DataAssertionException(
            @Nullable String field,
            String message,
            @Nullable String safeSample,
            @Nullable Throwable cause) {
        this(field, null, null, message, safeSample, cause);
    }

    /** 创建由执行器补充节点位置的契约失败。 */
    private DataAssertionException(
            @Nullable String field,
            @Nullable String operationName,
            @Nullable String outputKey,
            String message,
            @Nullable String safeSample,
            @Nullable Throwable cause) {
        super(TextUtils.requireText(message, "数据契约失败原因"), cause);
        if (safeSample != null && safeSample.length() > MAX_SAFE_SAMPLE_LENGTH) {
            throw new IllegalArgumentException("安全样本不能超过 256 个字符");
        }
        this.field = field;
        this.operationName = operationName;
        this.outputKey = outputKey;
        this.safeSample = safeSample;
    }

    /**
     * 读取触发断言的输入或 JSON 字段。
     *
     * @return 失败字段；无法定位时为空
     */
    public @Nullable String field() {
        return field;
    }

    /**
     * 读取执行器附加的失败节点名称。
     *
     * @return 失败节点名称；执行节点前失败时为空
     */
    public @Nullable String operationName() {
        return operationName;
    }

    /**
     * 读取执行器附加的失败节点输出键。
     *
     * @return 失败节点的输出键；执行节点前失败时为空
     */
    public @Nullable String outputKey() {
        return outputKey;
    }

    /**
     * 读取允许写入诊断信息的脱敏样本。
     *
     * @return 调用方显式提供的已脱敏样本；没有时为空
     */
    public @Nullable String safeSample() {
        return safeSample;
    }

    /**
     * 为节点内部报告的契约失败补充节点位置。
     *
     * @param operation 当前节点
     * @return 包含节点名称和输出键的新异常
     */
    DataAssertionException at(DataOperation<?> operation) {
        return new DataAssertionException(
                field,
                operation.name(),
                operation.output().name(),
                getMessage(),
                safeSample,
                this);
    }
}
