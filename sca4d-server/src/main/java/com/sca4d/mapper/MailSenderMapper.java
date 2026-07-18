package com.sca4d.mapper;

import com.mybatisflex.core.BaseMapper;
import com.sca4d.entity.SysMailSenderEntity;
import com.sca4d.mail.MailSenderStatus;
import com.sca4d.mail.SmtpSecurity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** SMTP 配置写入、乐观更新、状态修改和逻辑删除 SQL Mapper。 */
@Mapper
public interface MailSenderMapper extends BaseMapper<SysMailSenderEntity> {

    /** @return 实际写入行数，只能为 1 */
    int insertSender(@Param("sender") @NotNull SysMailSenderEntity sender);

    /**
     * 按主键和版本更新 SMTP 地址、发件资料及可选密码密文。
     *
     * @param passwordChanged 是否同时替换或清空三个密码列
     * @return 实际更新行数，只能为 0 或 1
     */
    int updateSender(
            @Param("senderId") @NotNull Long senderId,
            @Param("name") @NotNull String name,
            @Param("host") @NotNull String host,
            @Param("port") int port,
            @Param("security") @NotNull SmtpSecurity security,
            @Param("username") @Nullable String username,
            @Param("passwordKeyId") @Nullable String passwordKeyId,
            @Param("passwordNonce") byte @Nullable [] passwordNonce,
            @Param("passwordCiphertext") byte @Nullable [] passwordCiphertext,
            @Param("passwordChanged") boolean passwordChanged,
            @Param("fromAddress") @NotNull String fromAddress,
            @Param("fromName") @Nullable String fromName,
            @Param("replyTo") @Nullable String replyTo,
            @Param("version") long version);

    /** @return 当前状态和版本同时匹配时更新 1 行，否则为 0 */
    int updateStatus(
            @Param("senderId") @NotNull Long senderId,
            @Param("expectedStatus") @NotNull MailSenderStatus expectedStatus,
            @Param("nextStatus") @NotNull MailSenderStatus nextStatus,
            @Param("version") long version);

    /** @return 主键和版本匹配时逻辑删除 1 行，否则为 0 */
    int markDeleted(@Param("senderId") @NotNull Long senderId, @Param("version") long version);
}
