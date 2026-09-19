package com.sca4d.notification.sms.aliyun;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.region.ParsedPhoneNumber;
import com.sca4d.core.api.region.Region;
import com.sca4d.notification.sms.SmsDeliveryStatus;
import com.sca4d.notification.sms.SmsProvider;
import com.sca4d.notification.sms.SmsProviderBinding;
import com.sca4d.notification.sms.SmsTransport;
import com.sca4d.notification.sms.SmsTransportResult;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** 使用阿里云 V2 Java SDK 提交国内或账号已开通的国际 SMS 模板请求。 */
public final class AliyunSmsTransport implements SmsTransport {

    /** 数据库 Provider 使用的适配器类型。 */
    public static final String TYPE = "ALIYUN";

    private static final Region CHINA_MAINLAND = new Region("CN");

    private final AliyunSmsProperties properties;

    private final ObjectMapper objectMapper;

    private final Map<String, com.aliyun.dysmsapi20170525.Client> clients = new ConcurrentHashMap<>();

    /** 创建阿里云 SMS Transport。 */
    public AliyunSmsTransport(@NotNull AliyunSmsProperties properties, @NotNull ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "阿里云 SMS 配置不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
    }

    @Override
    public @NotNull String providerType() {
        return TYPE;
    }

    @Override
    public @NotNull SmsTransportResult send(
            @NotNull SmsProvider provider,
            @NotNull SmsProviderBinding binding,
            @NotNull ParsedPhoneNumber destination,
            @NotNull Map<@NotNull String, @NotNull String> parameters) {
        SmsProvider checkedProvider = Objects.requireNonNull(provider, "SMS Provider 不能为空");
        SmsProviderBinding checkedBinding = Objects.requireNonNull(binding, "SMS Provider Binding 不能为空");
        ParsedPhoneNumber checkedDestination = Objects.requireNonNull(destination, "SMS 目的地不能为空");
        String phoneNumber = checkedDestination.value();
        String providerDestination = CHINA_MAINLAND.equals(checkedDestination.region())
                ? phoneNumber.substring(3)
                : phoneNumber;
        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(providerDestination)
                .setSignName(checkedBinding.signName())
                .setTemplateCode(checkedBinding.providerTemplateCode())
                .setTemplateParam(write(parameters));
        try {
            var response = client(checkedProvider.credentialRef()).sendSmsWithOptions(request, new RuntimeOptions());
            var body = response.getBody();
            String code = body == null ? null : body.getCode();
            SmsDeliveryStatus status = "OK".equalsIgnoreCase(code)
                    ? SmsDeliveryStatus.ACCEPTED
                    : SmsDeliveryStatus.REJECTED;
            return new SmsTransportResult(
                    status,
                    body == null ? null : body.getRequestId(),
                    body == null ? null : body.getBizId(),
                    status == SmsDeliveryStatus.REJECTED ? code : null);
        } catch (Exception exception) {
            return new SmsTransportResult(
                    SmsDeliveryStatus.UNKNOWN,
                    null,
                    null,
                    exception.getClass().getSimpleName());
        }
    }

    /** 按部署凭据引用创建并缓存 SDK Client。 */
    private com.aliyun.dysmsapi20170525.Client client(String credentialRef) {
        return clients.computeIfAbsent(credentialRef, ignored -> {
            AliyunSmsProperties.Credential credential = properties.getCredentials().get(credentialRef);
            if (credential == null) {
                throw new IllegalStateException("阿里云 SMS 凭据引用没有部署配置: " + credentialRef);
            }
            try {
                Config config = new Config()
                        .setAccessKeyId(required(credential.getAccessKeyId(), "AccessKey ID"))
                        .setAccessKeySecret(required(credential.getAccessKeySecret(), "AccessKey Secret"))
                        .setEndpoint(required(credential.getEndpoint(), "Endpoint"))
                        .setRegionId(required(credential.getRegionId(), "Region ID"));
                return new com.aliyun.dysmsapi20170525.Client(config);
            } catch (Exception exception) {
                throw new IllegalStateException("阿里云 SMS Client 创建失败", exception);
            }
        });
    }

    private String write(Map<String, String> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("阿里云 SMS 模板参数无法编码", exception);
        }
    }

    private static String required(String value, String name) {
        String checked = Objects.requireNonNull(value, name + "不能为空").strip();
        if (checked.isEmpty())
            throw new IllegalStateException(name + "不能为空");
        return checked;
    }
}
