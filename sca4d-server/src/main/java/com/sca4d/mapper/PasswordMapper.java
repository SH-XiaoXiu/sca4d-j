package com.sca4d.mapper;

import com.sca4d.entity.IamCredentialEntity;
import com.sca4d.entity.IamPasswordEntity;
import com.sca4d.iam.password.repository.PasswordRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** PASSWORD Credential 公共记录、协议摘要和验证查询 Mapper。 */
@Mapper
public interface PasswordMapper {

    /** @param credential 已确定 Account、类型和初始状态的公共 Credential */
    void insertCredential(@Param("credential") @NotNull IamCredentialEntity credential);

    /** @param password Credential 主键和 Argon2id 编码摘要 */
    void insertPassword(@Param("password") @NotNull IamPasswordEntity password);

    /**
     * 读取指定 Account 的 PASSWORD Credential、Account 和 Subject 状态。
     *
     * @param accountId 已由认证入口解析的 Account 主键
     * @return 验证需要的当前行；没有 PASSWORD Credential 时为 {@code null}
     */
    @Nullable
    PasswordRow selectPassword(@Param("accountId") @NotNull Long accountId);

    /**
     * 原子替换 Password 摘要并推进公共 Credential 版本。
     *
     * @param credentialId PASSWORD Credential 主键
     * @param accountId 所属 Account 主键
     * @param passwordHash 新 Argon2id 编码摘要
     * @param version 调用方读取的 Credential 版本
     * @return 公共 Credential 与协议子表都命中时为 1
     */
    int replacePassword(
            @Param("credentialId") @NotNull Long credentialId,
            @Param("accountId") @NotNull Long accountId,
            @Param("passwordHash") @NotNull String passwordHash,
            @Param("version") @NotNull Long version);
}
