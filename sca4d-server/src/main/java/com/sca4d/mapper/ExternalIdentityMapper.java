package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.IamExternalIdentityEntity;
import com.sca4d.identity.external.ExternalIdentityProfile;
import com.sca4d.identity.external.repository.ExternalIdentityAccountRow;
import com.sca4d.identity.external.repository.ExternalIdentityRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** ExternalIdentity 绑定、认证资料更新和账号状态查询 Mapper。 */
@Mapper
public interface ExternalIdentityMapper extends BaseMapper<IamExternalIdentityEntity> {

    /**
     * 按 Provider external subject 或 ExternalIdentity 主键读取完整绑定。
     *
     * @return 未删除绑定及 Provider、Account、Subject 当前状态；不存在时为 null
     */
    @Nullable
    ExternalIdentityRow selectLink(
            @Nullable @Param("providerId") Long providerId,
            @Nullable @Param("externalSubject") String externalSubject,
            @Nullable @Param("identityId") Long identityId);

    /**
     * 在 Provider、Account、Subject 均可认证且作用域一致时创建绑定。
     *
     * @return 所有条件满足时插入 1 行，否则为 0
     */
    int insertLink(@Param("identity") @NotNull IamExternalIdentityEntity identity);

    /**
     * 在绑定、Provider、Account、Subject 仍可认证时更新最近一次上游资料。
     *
     * @return 绑定及版本仍匹配时更新 1 行，否则为 0
     */
    int updateAuthentication(
            @Param("identityId") @NotNull Long identityId,
            @Param("providerId") @NotNull Long providerId,
            @Param("version") @NotNull Long version,
            @Param("profile") @NotNull ExternalIdentityProfile profile);

    /** @return 指定 Account 当前全部 ExternalIdentity，按 Provider code 和主键排序 */
    @NotNull
    List<ExternalIdentityAccountRow> selectByAccount(
            @Param("accountId") @NotNull Long accountId);

    /** @return Account、版本和未删除状态同时匹配时逻辑删除 1 行，否则为 0 */
    int deleteLink(
            @Param("identityId") @NotNull Long identityId,
            @Param("accountId") @NotNull Long accountId,
            @Param("version") @NotNull Long version);

    /** @return 排除指定绑定后，Account 是否仍有 ACTIVE Provider 对应的 ExternalIdentity */
    boolean existsOtherActiveByAccount(
            @Param("accountId") @NotNull Long accountId,
            @Param("excludedIdentityId") @NotNull Long excludedIdentityId);
}
