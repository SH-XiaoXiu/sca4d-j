package com.sca4d.app.server.platform.request;

import com.sca4d.core.spring.validation.Req;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 接收 Platform Account 角色的完整替换集合。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public class ReplaceAccountRolesRequest {

    @Req.NotNull
    private @Nullable List<@Req.NotNull @NotNull Long> roleIds = new ArrayList<>();

    /** 创建供 JSON 反序列化使用的空请求。 */
    public ReplaceAccountRolesRequest() {
    }

    /**
     * 返回替换后的角色标识集合。
     *
     * @return 新角色标识集合
     */
    public @NotNull @Unmodifiable List<@NotNull Long> getRoleIds() {
        return List.copyOf(Objects.requireNonNull(roleIds, "角色标识不能为空"));
    }

    /**
     * 设置替换后的角色标识集合。
     *
     * @param roleIds 新角色标识集合
     */
    public void setRoleIds(@Nullable List<@NotNull Long> roleIds) {
        this.roleIds = roleIds == null ? null : List.copyOf(roleIds);
    }
}
