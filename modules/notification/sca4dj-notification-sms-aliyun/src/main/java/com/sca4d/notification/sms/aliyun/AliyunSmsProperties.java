package com.sca4d.notification.sms.aliyun;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** 保存 Provider `credentialRef` 对应的阿里云 SMS 部署凭据和 Endpoint。 */
@ConfigurationProperties("sca4dj.notification.sms.aliyun")
public final class AliyunSmsProperties {

    private @NotNull Map<String, Credential> credentials = new LinkedHashMap<>();

    /** @return 凭据引用到部署凭据的映射 */
    public @NotNull Map<String, Credential> getCredentials() {
        return Map.copyOf(credentials);
    }

    /** @param credentials 从环境或配置注入且不得写入数据库的凭据映射 */
    public void setCredentials(@NotNull Map<String, Credential> credentials) {
        this.credentials = new LinkedHashMap<>(credentials);
    }

    /** 保存一个阿里云账号的 AccessKey 和服务 Endpoint。 */
    public static final class Credential {
        private String accessKeyId;

        private String accessKeySecret;

        private String endpoint = "dysmsapi.aliyuncs.com";

        private String regionId = "cn-hangzhou";

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegionId() {
            return regionId;
        }

        public void setRegionId(String regionId) {
            this.regionId = regionId;
        }
    }
}
