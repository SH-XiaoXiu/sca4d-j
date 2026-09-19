package com.sca4d.notification.sms;

import com.sca4d.core.api.region.ParsedPhoneNumber;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** 使用一个 Provider 实例和平台模板绑定提交短信请求。 */
public interface SmsTransport {
    /** @return 当前适配器负责的 Provider 类型，例如 `ALIYUN` */
    @NotNull
    String providerType();

    /** 提交短信并返回 Provider 对本次请求的确定结果。 */
    @NotNull
    SmsTransportResult send(
            @NotNull SmsProvider provider,
            @NotNull SmsProviderBinding binding,
            @NotNull ParsedPhoneNumber destination,
            @NotNull Map<@NotNull String, @NotNull String> parameters);
}
