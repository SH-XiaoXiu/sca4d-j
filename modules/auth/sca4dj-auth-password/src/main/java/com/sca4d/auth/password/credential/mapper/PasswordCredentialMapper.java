package com.sca4d.auth.password.credential.mapper;

import com.sca4d.auth.password.credential.PasswordCredential;
import com.sca4d.core.api.concurrent.Version;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.Nullable;

/**
 * 定义 Password 方法自己拥有的密码凭据 SQL 形状，由 `PasswordCredentialRepository` 调用。
 *
 * <p>MyBatis 在运行时为此接口创建动态代理，源码见
 * <a href="https://github.com/mybatis/mybatis-3/blob/mybatis-3.5.19/src/main/java/org/apache/ibatis/binding/MapperProxy.java">MapperProxy</a>。
 */
public interface PasswordCredentialMapper {

    /**
     * 写入密码摘要；账号已经有密码凭据时不修改原记录。
     *
     * @param accountId 密码凭据所属账号
     * @param passwordHash Argon2id 不可逆摘要
     * @return 新写入记录数；账号已有凭据时为 {@code 0}
     */
    int insertCredential(@Param("accountId") Long accountId, @Param("passwordHash") String passwordHash);

    /**
     * 按账号标识查询密码凭据。
     *
     * @param accountId 密码凭据所属账号
     * @return 密码凭据；账号尚未设置密码时为 {@code null}
     */
    @Nullable
    PasswordCredential selectByAccountId(@Param("accountId") Long accountId);

    /**
     * 按乐观版本替换密码摘要。
     *
     * @param accountId 密码凭据所属账号
     * @param passwordHash 新 Argon2id 摘要
     * @param expectedVersion 预期版本
     * @return 版本匹配并更新的行数
     */
    int updateCredentialIfVersion(
            @Param("accountId") Long accountId,
            @Param("passwordHash") String passwordHash,
            @Param("expectedVersion") Version expectedVersion);

    /**
     * 删除账号保存的密码摘要。
     *
     * @param accountId 密码凭据所属账号
     * @return 删除行数；账号没有密码凭据时为 {@code 0}
     */
    int deleteByAccountId(@Param("accountId") Long accountId);
}
