package com.sca4d.openapi.client.mapper;

import com.sca4d.core.api.name.code.TreeCode;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.openapi.client.OpenApiClient;
import com.sca4d.openapi.client.OpenApiClientStatus;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读写 OpenAPI Client 事实与 Operation 关系，不判断业务开放权限。 */
public interface OpenApiClientMapper {
    /** 插入 Client 与 IAM Account 的稳定绑定。 */
    @CheckReturnValue
    int insertClient(
            @Param("clientId") @NotNull String clientId,
            @Param("accountId") long accountId,
            @Param("displayName") @NotNull String displayName,
            @Param("status") @NotNull OpenApiClientStatus status);

    /** @return 按 Client ID 或唯一 Account 标识匹配的 Client；调用方必须提供一个标识 */
    @CheckReturnValue
    @Nullable
    OpenApiClient selectClient(
            @Param("clientId") @Nullable String clientId,
            @Param("accountId") @Nullable Long accountId,
            @Param("forUpdate") boolean forUpdate);

    /** @return Client 的全部或指定 Operation，按编码排序 */
    @CheckReturnValue
    @NotNull
    List<TreeCode> selectOperations(
            @Param("clientId") @NotNull String clientId,
            @Param("operation") @Nullable TreeCode operation);

    /** 删除 Client 当前完整 Operation 关系。 */
    int deleteOperations(@Param("clientId") @NotNull String clientId);

    /** 插入一个非空批次的 Operation 关系，返回实际新增数。 */
    @CheckReturnValue
    int insertOperations(
            @Param("clientId") @NotNull String clientId,
            @Param("operations") @NotNull List<TreeCode> operations);

    /** 执行 Client 状态字段的明确写入意图。 */
    @CheckReturnValue
    int updateStatus(
            @Param("clientId") @NotNull String clientId,
            @Param("status") @NotNull Patch<OpenApiClientStatus> status);
}
