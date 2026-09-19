package com.sca4d.iam.base;

import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.Contract;

/** 表示平台接口授权和数据归属使用的业务主体。 */
public final class PlatformPrincipal implements Principal {

    private final long id;

    /**
     * 创建平台业务主体。
     *
     * @param id 业务主体标识
     */
    public PlatformPrincipal(long id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @Override
    @Contract(pure = true)
    public long principalId() {
        return id;
    }

}
