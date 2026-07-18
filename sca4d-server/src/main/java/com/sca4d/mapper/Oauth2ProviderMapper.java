package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.IamOauth2ProviderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

/** OAuth2 Provider 配置新增和替换使用的 SQL Mapper。 */
@Mapper
public interface Oauth2ProviderMapper extends BaseMapper<IamOauth2ProviderEntity> {

    /** @return 实际写入行数，只能为 1 */
    int insertConfiguration(@Param("configuration") @NotNull IamOauth2ProviderEntity configuration);

    /**
     * 替换 OAuth2 端点、Client、scope 和 Profile 映射。
     *
     * @param secretChanged true 时同时替换三个 Client Secret 密文列
     * @return 配置存在时更新 1 行，否则为 0
     */
    int updateConfiguration(
            @Param("configuration") @NotNull IamOauth2ProviderEntity configuration,
            @Param("secretChanged") boolean secretChanged);
}
