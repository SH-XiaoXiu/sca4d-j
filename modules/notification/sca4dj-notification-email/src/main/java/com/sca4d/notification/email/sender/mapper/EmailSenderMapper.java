package com.sca4d.notification.email.sender.mapper;

import com.sca4d.core.api.concurrent.Version;
import com.sca4d.core.api.persistence.Patch;
import com.sca4d.notification.email.sender.SmtpSecurity;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 读写 SMTP 配置的最小 SQL 形状。 */
public interface EmailSenderMapper {

    /** 按可选标识和启用状态读取未删除 SMTP 配置。 */
    @CheckReturnValue
    @NotNull
    List<@NotNull EmailSenderState> select(
            @Param("senderId") @Nullable Long senderId,
            @Param("enabled") @Nullable Boolean enabled);

    /**
     * 新增默认停用的 SMTP 配置。
     *
     * @return 新增行数
     */
    int insert(
            @Param("senderId") long senderId,
            @Param("name") @NotNull String name,
            @Param("host") @NotNull String host,
            @Param("port") int port,
            @Param("security") @NotNull SmtpSecurity security,
            @Param("username") @Nullable String username,
            @Param("passwordKeyId") @Nullable String passwordKeyId,
            @Param("passwordCiphertext") byte @Nullable [] passwordCiphertext,
            @Param("fromAddress") @NotNull String fromAddress,
            @Param("fromName") @Nullable String fromName,
            @Param("replyTo") @Nullable String replyTo);

    /**
     * 按预期版本原子替换 SMTP 地址、认证和发件资料。
     *
     * @param expectedVersion 预期版本
     * @return 修改行数；SMTP 配置不存在或版本不匹配时为零
     */
    @CheckReturnValue
    int update(
            @Param("senderId") long senderId,
            @Param("name") @NotNull Patch<String> name,
            @Param("host") @NotNull Patch<String> host,
            @Param("port") @NotNull Patch<Integer> port,
            @Param("security") @NotNull Patch<SmtpSecurity> security,
            @Param("username") @NotNull Patch<String> username,
            @Param("passwordKeyId") @NotNull Patch<String> passwordKeyId,
            @Param("passwordCiphertext") @NotNull Patch<byte[]> passwordCiphertext,
            @Param("fromAddress") @NotNull Patch<String> fromAddress,
            @Param("fromName") @NotNull Patch<String> fromName,
            @Param("replyTo") @NotNull Patch<String> replyTo,
            @Param("enabled") @NotNull Patch<Boolean> enabled,
            @Param("expectedVersion") @NotNull Version expectedVersion);

    /** @return 按预期版本逻辑删除停用配置的行数 */
    @CheckReturnValue
    int markDeleted(
            @Param("senderId") long senderId,
            @Param("expectedVersion") @NotNull Version expectedVersion);
}
