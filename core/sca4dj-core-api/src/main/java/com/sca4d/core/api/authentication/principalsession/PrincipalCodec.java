package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

/**
 * Principal 的无框架 Session wire 编解码契约。
 *
 * @param <P> 当前 Codec 可以编码和恢复的 Principal 类型
 */
public interface PrincipalCodec<P extends Principal> {
    /**
     * 读取载荷中用于选择 Codec 的类型标识。
     *
     * @return Session wire 中跨版本保持不变的 Principal 类型标识
     */
    @NotNull
    TreeCode type();

    /** @return 当前 Principal payload 的正数结构版本 */
    default int version() {
        return 1;
    }

    /**
     * 读取编码前用于拒绝不匹配实现类的类型。
     *
     * @return 当前 Codec 处理的精确 Principal 类型
     */
    Class<P> valueType();

    /**
     * 把 Principal 编码为 Session wire 载荷。
     *
     * @param value 待编码的 Principal
     * @return 编码后的字节载荷
     */
    byte[] encode(P value);

    /**
     * 从 Session wire 载荷恢复 Principal。
     *
     * @param payload 编码后的字节载荷
     * @return 恢复出的 Principal
     */
    P decode(byte[] payload);
}
