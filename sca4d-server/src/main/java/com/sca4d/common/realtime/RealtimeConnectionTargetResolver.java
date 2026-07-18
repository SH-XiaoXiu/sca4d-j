package com.sca4d.common.realtime;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 当前身份主体到实时连接目标的解析器。
 */
public interface RealtimeConnectionTargetResolver {

    /**
     * 解析当前身份主体可绑定的低层实时目标。
     *
     * @param subject 登录身份
     * @param principal 当前主体
     * @return 连接目标列表
     */
    @NotNull
    List<RealtimeTarget> resolve(@Nullable Subject subject, @Nullable Principal principal);
}
