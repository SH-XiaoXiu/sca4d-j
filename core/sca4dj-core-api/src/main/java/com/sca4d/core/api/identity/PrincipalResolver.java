package com.sca4d.core.api.identity;

import java.util.Optional;

/**
 * 按标识解析业务主体（Principal）。
 *
 * @param <P> 当前实现负责的业务主体类型
 */
public interface PrincipalResolver<P extends Principal> {

    /**
     * 尝试解析业务主体。
     *
     * @param principalId 业务主体标识
     * @return 当前实现拥有该标识时返回业务主体，否则返回空
     */
    Optional<P> resolve(long principalId);
}
