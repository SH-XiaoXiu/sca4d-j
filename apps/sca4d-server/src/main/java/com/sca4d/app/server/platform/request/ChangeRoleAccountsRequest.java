package com.sca4d.app.server.platform.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.Positive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 接收一个 Platform 角色需要批量增加和移除的 Account 标识。 */
@SuppressWarnings("unused") // Jackson 通过 JavaBean setter 反序列化请求。
public final class ChangeRoleAccountsRequest {

    @Req.NotNull
    private @Nullable List<@Positive @NotNull Long> addAccountIds = new ArrayList<>();

    @Req.NotNull
    private @Nullable List<@Positive @NotNull Long> removeAccountIds = new ArrayList<>();

    /** 创建供 JSON 反序列化填充的空请求。 */
    public ChangeRoleAccountsRequest() {
    }

    /** @return 需要增加目标角色的 Account 标识 */
    public @NotNull @Unmodifiable List<@NotNull Long> getAddAccountIds() {
        return List.copyOf(Objects.requireNonNull(addAccountIds, "添加 Account 标识不能为空"));
    }

    /** @param addAccountIds 需要增加目标角色的 Account 标识 */
    public void setAddAccountIds(@Nullable List<@NotNull Long> addAccountIds) {
        this.addAccountIds = addAccountIds == null ? null : List.copyOf(addAccountIds);
    }

    /** @return 需要移除目标角色的 Account 标识 */
    public @NotNull @Unmodifiable List<@NotNull Long> getRemoveAccountIds() {
        return List.copyOf(Objects.requireNonNull(removeAccountIds, "移除 Account 标识不能为空"));
    }

    /** @param removeAccountIds 需要移除目标角色的 Account 标识 */
    public void setRemoveAccountIds(@Nullable List<@NotNull Long> removeAccountIds) {
        this.removeAccountIds = removeAccountIds == null ? null : List.copyOf(removeAccountIds);
    }
}
