package com.sca4d.mail.security;

import com.sca4d.common.security.EncryptedSecret;
import com.sca4d.mail.MailSender;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 建立一次 SMTP 连接需要的公开配置和可选加密密码。 */
@Getter
@AllArgsConstructor
public class MailSenderConfiguration {

    private final @NotNull MailSender sender;
    private final @Nullable EncryptedSecret password;
}
