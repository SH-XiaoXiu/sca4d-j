package com.sca4d.notification.sms;

import com.sca4d.core.api.region.ParsedPhoneNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** 使用一个 Provider 实例和平台模板绑定提交短信请求。 */
public interface SmsProviderClient {
    /** @return 当前适配器负责的 Provider 类型，例如 `ALIYUN` */
    @NotNull
    String providerType();

    /** 提交短信并返回 Provider 对本次请求的确定结果。 */
    @NotNull
    SmsProviderResult send(
            @NotNull SmsProvider provider,
            @NotNull SmsProviderBinding binding,
            @NotNull ParsedPhoneNumber destination,
            @NotNull Map<@NotNull String, @NotNull String> parameters);

    /** 查询已经提交的短信是否最终送达。查询不得再次提交短信。 */
    @NotNull
    SmsProviderResult query(
            @NotNull SmsProvider provider,
            @NotNull SmsProviderBinding binding,
            @NotNull ParsedPhoneNumber destination,
            @Nullable String requestId,
            @Nullable String bizId);
}
