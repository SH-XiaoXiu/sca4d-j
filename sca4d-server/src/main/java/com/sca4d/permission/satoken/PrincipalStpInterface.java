package com.sca4d.permission.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.common.request.CurrentRequestHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向 Sa-Token 暴露当前请求已经解析的 Principal 权限。
 */
@Component
public class PrincipalStpInterface implements StpInterface {

    @Override
    public @NotNull List<String> getPermissionList(@Nullable Object loginId, @Nullable String loginType) {
        SessionPermission permission = currentPermission();
        return permission == null ? List.of() : List.copyOf(permission.getPermissionCodes());
    }

    @Override
    public @NotNull List<String> getRoleList(@Nullable Object loginId, @Nullable String loginType) {
        SessionPermission permission = currentPermission();
        return permission == null ? List.of() : List.copyOf(permission.getRoleCodes());
    }

    /**
     * 读取与当前请求最终 Principal 主键严格匹配的授权快照。
     *
     * @return 缺少请求、Principal、Principal 主键或匹配授权时返回 null
     */
    private @Nullable SessionPermission currentPermission() {
        CurrentRequest request = CurrentRequestHolder.currentOrNull();
        if (request == null || request.getPrincipal() == null) {
            return null;
        }
        Long principalId = request.getPrincipal().getPrincipalId();
        if (principalId == null) {
            return null;
        }
        SessionPermission permission = request.getSessionPermission();
        if (permission == null
                || !principalId.equals(permission.getPrincipalId())) {
            return null;
        }
        return permission;
    }
}
