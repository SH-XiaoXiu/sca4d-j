package com.sca4d.core.api.authentication.principalsession;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.authentication.SessionIdentity;
import com.sca4d.core.api.identity.Principal;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 认领 {@link SessionIdentity}，并读取建立 {@link Session} 所需的 Principal。 */
public interface PrincipalLoader {

    /**
     * 判断当前实现是否负责指定身份。
     *
     * @param identity 已恢复 Subject 和 Account 的身份
     * @return 当前实现负责该身份时为 {@code true}
     */
    boolean supports(@NotNull SessionIdentity identity);

    /**
     * 读取当前 Account 可以建立 `Session` 的全部 Principal。
     *
     * @param identity 已恢复 Subject 和 Account 的身份
     * @return 非空且标识互不重复的 Principal 列表
     */
    @NotNull
    List<? extends @NotNull Principal> load(@NotNull SessionIdentity identity);
}
