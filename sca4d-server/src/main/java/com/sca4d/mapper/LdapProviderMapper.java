package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.IamLdapProviderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;

/** LDAP Provider 配置新增和替换使用的 SQL Mapper。 */
@Mapper
public interface LdapProviderMapper extends BaseMapper<IamLdapProviderEntity> {

    /** @return 实际写入行数，只能为 1 */
    int insertConfiguration(@Param("configuration") @NotNull IamLdapProviderEntity configuration);

    /**
     * 替换 LDAP 地址、搜索和属性映射配置。
     *
     * @param passwordChanged true 时同时替换或清空三个服务账号密码密文列
     * @return 配置存在时更新 1 行，否则为 0
     */
    int updateConfiguration(
            @Param("configuration") @NotNull IamLdapProviderEntity configuration,
            @Param("passwordChanged") boolean passwordChanged);
}
