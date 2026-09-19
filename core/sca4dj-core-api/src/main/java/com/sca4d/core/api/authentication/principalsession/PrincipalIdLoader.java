package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * 为 Session 提供一种不在建立阶段完整装载的 Principal 标识。
 *
 * @param <P> 请求期由业务选择器解析的 Principal 类型
 */
public interface PrincipalIdLoader<P extends Principal> {

    /**
     * 判断当前实现是否为指定认证身份提供 Principal 标识。
     *
     * @param identity 已恢复 Subject 和 Account 的身份
     * @return 当前实现负责该身份时返回 {@code true}
     */
    boolean supports(@NotNull SessionIdentity identity);

    /**
     * 返回请求期候选 Principal 必须精确匹配的 Java 类型。
     *
     * @return 当前 Loader 负责的 Principal Java 类型
     */
    @NotNull
    Class<P> principalType();

    /**
     * 读取建立 Session 时存在且有效的 Principal 标识。
     *
     * @param identity 已恢复 Subject 和 Account 的身份
     * @return 可以为空、元素互不重复的正数 Principal 标识
     */
    @NotNull
    Collection<@NotNull Long> load(@NotNull SessionIdentity identity);
}
