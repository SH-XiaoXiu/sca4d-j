package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.IamOidcProviderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** OIDC Provider 配置新增和替换使用的 SQL Mapper。 */
@Mapper
public interface OidcProviderMapper extends BaseMapper<IamOidcProviderEntity> {

    /** @return 实际写入行数，只能为 1 */
    int insertConfiguration(@Param("configuration") @NotNull IamOidcProviderEntity configuration);

    /**
     * 替换 OIDC Discovery、Client 和 scope 配置。
     *
     * @param secretChanged true 时同时替换三个 Client Secret 密文列
     * @return 配置存在时更新 1 行，否则为 0
     */
    int updateConfiguration(
            @Param("providerId") @NotNull Long providerId,
            @Param("issuer") @NotNull String issuer,
            @Param("clientId") @NotNull String clientId,
            @Param("clientSecretKeyId") @Nullable String clientSecretKeyId,
            @Param("clientSecretNonce") byte @Nullable [] clientSecretNonce,
            @Param("clientSecretCiphertext") byte @Nullable [] clientSecretCiphertext,
            @Param("secretChanged") boolean secretChanged,
            @Param("scopes") @NotNull String scopes);
}
