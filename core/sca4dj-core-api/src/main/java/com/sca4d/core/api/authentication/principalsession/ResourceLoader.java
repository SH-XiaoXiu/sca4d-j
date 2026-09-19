package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.resource.Resource;

import java.util.Collection;
import java.util.Map;

/** 为 Session 中的 Principal 批量贡献静态授权资源。 */
public interface ResourceLoader {

    /**
     * 返回输入 Principal 的资源贡献；未贡献资源的 Principal 可以省略。
     *
     * @param principals 本次建立 Session 时可选择的全部 Principal
     * @return 按 Principal 标识分组的静态授权资源；没有贡献时返回空映射
     */
    Map<Long, ? extends Collection<? extends Resource>> load(Collection<? extends Principal> principals);
}
