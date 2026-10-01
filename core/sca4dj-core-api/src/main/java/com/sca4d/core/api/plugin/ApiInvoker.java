package com.sca4d.core.api.plugin;

import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 在当前线程调用已公开操作；实现必须校验目标、参数和活动代次。 */
@FunctionalInterface
public interface ApiInvoker {
    /**
     * 在当前线程调用一次公开操作，原样保留目标抛出的异常；void 操作返回 null。
     * 调用不自动改变身份、事务传播或数据源，不序列化参数和结果。
     *
     * @param target 服务或扩展目标，包含契约编码及可选实现选择编码
     * @param operation 契约中已声明的稳定操作编码
     * @param arguments 按契约方法声明顺序排列的参数，仅允许平台和共享类型
     * @return 目标方法结果，空值是否允许由契约声明决定
     * @throws Throwable 目标调用抛出的异常或目标不可用、参数和结果不符合契约的失败
     */
    @Nullable
    Object invoke(
            @NotNull ApiTarget target,
            @NotNull TreeCode operation,
            Object @NotNull [] arguments) throws Throwable;
}
