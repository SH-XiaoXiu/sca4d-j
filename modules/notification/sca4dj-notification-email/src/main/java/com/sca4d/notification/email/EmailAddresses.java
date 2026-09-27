package com.sca4d.notification.email;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/** 校验并规范 Email 通知配置和解析结果使用的邮箱地址。 */
public class EmailAddresses {

    private EmailAddresses() {
    }

    /**
     * @param value 单个邮箱地址，不允许显示名或地址列表
     * @return JavaMail 解析后的稳定地址文本
     * @throws IllegalArgumentException 地址为空、过长或语法无效时抛出
     */
    @Contract(pure = true)
    public static @NotNull String require(@NotNull String value) {
        if (value.isBlank() || value.length() > 320) {
            throw new IllegalArgumentException("邮箱地址必须为 1 到 320 个字符");
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            if (!address.toString().equals(address.getAddress()) || address.getAddress().indexOf('@') <= 0) {
                throw new IllegalArgumentException("邮箱地址不能包含显示名");
            }
            return address.getAddress();
        } catch (AddressException exception) {
            throw new IllegalArgumentException("邮箱地址格式不正确", exception);
        }
    }
}
