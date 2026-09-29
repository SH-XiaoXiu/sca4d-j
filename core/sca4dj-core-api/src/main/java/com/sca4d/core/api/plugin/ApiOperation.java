package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/** 保存一次公开调用的参数与返回类型，供动态入口检查。 */
public final class ApiOperation {
    private final @NotNull TreeCode code;

    private final @NotNull List<Class<?>> parameters;

    private final @NotNull Class<?> resultType;

    private final @NotNull List<Boolean> nullableParameters;

    private final boolean nullableResult;

    /** 保存擦除后的 Java 类型和空值约定；参数类型与空值声明必须按顺序一一对应。 */
    public ApiOperation(
            @NotNull TreeCode code,
            @NotNull List<Class<?>> parameters,
            @NotNull Class<?> resultType,
            @NotNull List<Boolean> nullableParameters,
            boolean nullableResult) {
        this.code = Objects.requireNonNull(code);
        this.parameters = List.copyOf(parameters);
        this.resultType = Objects.requireNonNull(resultType);
        this.nullableParameters = List.copyOf(nullableParameters);
        this.nullableResult = nullableResult;
        if (parameters.size() != nullableParameters.size()) {
            throw new IllegalArgumentException("参数空值声明数量不一致");
        }
    }

    /** 返回稳定操作编码。 */
    public @NotNull TreeCode code() {
        return code;
    }

    /** 返回按声明顺序排列的参数类型。 */
    public @NotNull List<Class<?>> parameters() {
        return parameters;
    }

    /** 返回结果类型，void 表示没有结果。 */
    public @NotNull Class<?> resultType() {
        return resultType;
    }

    /** 返回各参数是否允许为空。 */
    public @NotNull List<Boolean> nullableParameters() {
        return nullableParameters;
    }

    /** 返回是否允许空结果。 */
    public boolean nullableResult() {
        return nullableResult;
    }
}
