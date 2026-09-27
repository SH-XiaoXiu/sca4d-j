package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.resource.Resource;
import org.jetbrains.annotations.NotNull;

/**
 * Resource 的无框架 Session wire 编解码契约。
 *
 * @param <R> 当前 Codec 可以编码和恢复的 Resource 类型
 */
public interface ResourceCodec<R extends Resource> {
    /**
     * 读取载荷中用于选择 Codec 的类型标识。
     *
     * @return Session wire 中跨版本保持不变的 Resource 类型标识
     */
    @NotNull
    TreeCode type();

    /**
     * 读取编码前用于拒绝不匹配实现类的类型。
     *
     * @return 当前 Codec 处理的精确 Resource 类型
     */
    Class<R> valueType();

    /**
     * 把 Resource 编码为 Session wire 载荷。
     *
     * @param value 待编码的 Resource
     * @return 编码后的字节载荷
     */
    byte[] encode(R value);

    /**
     * 从 Session wire 载荷恢复 Resource。
     *
     * @param payload 编码后的字节载荷
     * @return 恢复出的 Resource
     */
    R decode(byte[] payload);
}
