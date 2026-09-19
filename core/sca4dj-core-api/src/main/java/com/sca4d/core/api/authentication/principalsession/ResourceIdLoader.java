package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/** 为 Session 中完整装载的 Principal 批量贡献数字 Resource 标识集合。 */
public interface ResourceIdLoader {

    /**
     * 返回输入 Principal 的数字 Resource 标识贡献；未贡献的 Principal 可以省略。
     *
     * @param principals 本次建立 Session 时完整装载的 Principal
     * @return 按 Principal 标识分组的数字 Resource 标识集合；没有贡献时返回空映射
     */
    @NotNull
    Map<@NotNull Long, ? extends Collection<@NotNull ResourceIdSet>> load(
            @NotNull Collection<? extends @NotNull Principal> principals);
}
