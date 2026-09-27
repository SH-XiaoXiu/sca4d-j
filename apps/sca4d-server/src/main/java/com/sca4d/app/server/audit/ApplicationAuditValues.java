package com.sca4d.app.server.audit;

import com.sca4d.app.server.authentication.request.ReplaceAuthenticationSettingsRequest;
import com.sca4d.app.server.files.request.CreateFileUploadRequest;
import com.sca4d.app.server.platform.request.CreatePlatformAccountRequest;
import com.sca4d.app.server.platform.request.CreateRoleRequest;
import com.sca4d.app.server.platform.request.ReplaceAccountRolesRequest;
import com.sca4d.app.server.platform.request.ReplaceAvatarRequest;
import com.sca4d.app.server.platform.request.ReplaceProfileRequest;
import com.sca4d.app.server.platform.request.ReplaceRoleI18nRequest;
import com.sca4d.app.server.platform.request.ReplaceRolePermissionsRequest;
import com.sca4d.app.server.platform.request.UpdatePlatformAccountRequest;
import com.sca4d.app.server.platform.request.UpdateRoleStatusRequest;
import com.sca4d.app.server.platform.view.PlatformAccountView;
import com.sca4d.app.server.system.request.CreateDictionaryOptionRequest;
import com.sca4d.app.server.system.request.CreateDictionaryRequest;
import com.sca4d.app.server.system.request.UpdateDictionaryOptionRequest;
import com.sca4d.app.server.system.request.UpdateDictionaryRequest;
import com.sca4d.core.api.audit.AuditInvocation;
import com.sca4d.core.api.audit.AuditPayload;
import com.sca4d.core.api.audit.AuditPayloadFactory;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.spring.web.ApiResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 提供 Sca4d Server 审计声明使用的安全 payload 工厂。 */
public final class ApplicationAuditValues {
    private ApplicationAuditValues() {
    }

    /** 从已支持的 App 修改请求中选择允许保存的状态、标识集合和字段名。 */
    @Component
    public static final class MutationDetails implements AuditPayloadFactory {
        /** 创建无状态解析器。 */
        public MutationDetails() {
        }

        /**
         * 只读取已知请求类型的安全字段；密码、验证码、Passkey proof 和个人资料值不会进入结果。
         *
         * @param invocation 当前调用快照
         * @return 可以写入审计 payload 的字段
         */
        @Override
        public @NotNull AuditPayload create(@NotNull AuditInvocation invocation) {
            Object[] arguments = invocation.arguments();
            for (Object argument : arguments) {
                if (argument instanceof CreatePlatformAccountRequest) {
                    if (invocation.auditResult() == AuditResult.SUCCESS) {
                        ApiResponse<?> response = invocation.getResult(ApiResponse.class);
                        if (!(response.getData() instanceof PlatformAccountView result)) {
                            throw new IllegalStateException("账号创建审计缺少 Platform Account 返回数据");
                        }
                        return new AuditDetails(
                                Map.of(
                                        "fields",
                                        List.of("username", "password", "displayName", "email", "phoneNumber"),
                                        "accountId",
                                        result.getId(),
                                        "status",
                                        result.getStatus().name()));
                    }
                    return new AuditDetails(
                            Map.of(
                                    "fields",
                                    List.of("username", "password", "displayName", "email", "phoneNumber")));
                }
                if (argument instanceof UpdatePlatformAccountRequest request) {
                    return new AuditDetails(Map.of("status", request.getStatus().name()));
                }
                if (argument instanceof ReplaceAccountRolesRequest request) {
                    return new AuditDetails(Map.of("roleIds", request.getRoleIds()));
                }
                if (argument instanceof ReplaceAvatarRequest request) {
                    return new AuditDetails(Map.of("fileId", request.getFileId()));
                }
                if (argument instanceof CreateRoleRequest request) {
                    return new AuditDetails(Map.of("permissionCodes", request.getPermissionCodes()));
                }
                if (argument instanceof UpdateRoleStatusRequest request) {
                    return new AuditDetails(Map.of("status", request.getStatus().name()));
                }
                if (argument instanceof ReplaceRoleI18nRequest request) {
                    return new AuditDetails(
                            Map.of(
                                    "fields",
                                    List.of("i18n"),
                                    "locales",
                                    request.getI18n()
                                            .locales()
                                            .stream()
                                            .map(Locale::toLanguageTag)
                                            .toList()));
                }
                if (argument instanceof ReplaceRolePermissionsRequest request) {
                    return new AuditDetails(Map.of("permissionCodes", request.getPermissionCodes()));
                }
                if (argument instanceof ReplaceProfileRequest) {
                    return new AuditDetails(
                            Map.of(
                                    "fields",
                                    List.of("displayName", "email", "phoneNumber")));
                }
                if (argument instanceof ReplaceAuthenticationSettingsRequest request) {
                    List<Map<String, Object>> methods = request.getMethods()
                            .stream()
                            .map(
                                    method -> Map.<String, Object>of(
                                            "methodCode",
                                            method.getMethodCode(),
                                            "loginStartEnabled",
                                            method.isLoginStartEnabled(),
                                            "loginAdditionalEnabled",
                                            method.isLoginAdditionalEnabled(),
                                            "sensitiveActionEnabled",
                                            method.isSensitiveActionEnabled()))
                            .toList();
                    return new AuditDetails(
                            Map.of(
                                    "loginFactorCount",
                                    request.getLoginFactorCount(),
                                    "sensitiveActionFactorCount",
                                    request.getSensitiveActionFactorCount(),
                                    "reusableAuthenticationMaximumAgeSeconds",
                                    request.getReusableAuthenticationMaximumAgeSeconds(),
                                    "methods",
                                    methods));
                }
            }
            throw new IllegalStateException("审计详情解析器不支持当前修改请求");
        }
    }

    /** 保存字典写操作的请求摘要和可展示的稳定错误码。 */
    @Component
    public static final class DictionaryMutationDetails implements AuditPayloadFactory {
        /** 创建无状态解析器。 */
        public DictionaryMutationDetails() {
        }

        /**
         * 只保存字典键、选项值和管理字段；拒绝时附加错误码和消息键，不保存异常文本或请求外数据。
         *
         * @param invocation 当前调用快照
         * @return 可以写入审计 payload 的字典摘要
         */
        @Override
        public @NotNull AuditPayload create(@NotNull AuditInvocation invocation) {
            Map<String, Object> values = new LinkedHashMap<>();
            Object[] arguments = invocation.arguments();
            if (arguments.length > 0 && arguments[0] instanceof String key) {
                values.put("dictionaryKey", key);
            }
            if (arguments.length > 1 && arguments[1] instanceof String optionValue) {
                values.put("optionValue", optionValue);
            }
            for (Object argument : arguments) {
                if (argument instanceof CreateDictionaryRequest request) {
                    values.put("dictionaryKey", request.getKey());
                    values.put("label", request.getLabel());
                    values.put("enabled", request.isEnabled());
                    values.put("sortOrder", request.getSortOrder());
                } else if (argument instanceof UpdateDictionaryRequest request) {
                    values.put("label", request.getLabel());
                    values.put("enabled", request.isEnabled());
                    values.put("sortOrder", request.getSortOrder());
                    values.put("version", request.getVersion());
                } else if (argument instanceof CreateDictionaryOptionRequest request) {
                    values.put("label", request.getLabel());
                    values.put("i18n", request.getI18n().toJson());
                    values.put("enabled", request.isEnabled());
                    values.put("sortOrder", request.getSortOrder());
                } else if (argument instanceof UpdateDictionaryOptionRequest request) {
                    values.put("label", request.getLabel());
                    values.put("i18n", request.getI18n().toJson());
                    values.put("enabled", request.isEnabled());
                    values.put("sortOrder", request.getSortOrder());
                    values.put("version", request.getVersion());
                } else if (argument instanceof Number number && arguments.length > 1) {
                    values.putIfAbsent("version", number.longValue());
                }
            }
            if (invocation.error() instanceof BusinessException exception) {
                values.put("errorCode", exception.errorCode().code());
                values.put("errorMessageKey", exception.errorCode().messageKey());
            }
            return new AuditDetails(values);
        }
    }

    /** 从文件上传请求中选择媒体类型和字节数，不读取原始文件名。 */
    @Component
    public static final class FileUploadDetails implements AuditPayloadFactory {
        /** 创建无状态解析器。 */
        public FileUploadDetails() {
        }

        /** {@inheritDoc} */
        @Override
        public @NotNull AuditPayload create(@NotNull AuditInvocation invocation) {
            Object[] arguments = invocation.arguments();
            if (arguments.length == 0 || !(arguments[0] instanceof CreateFileUploadRequest request)) {
                throw new IllegalStateException("文件上传审计详情缺少上传请求");
            }
            return new AuditDetails(
                    Map.of(
                            "contentType",
                            request.getContentType().trim().toLowerCase(Locale.ROOT),
                            "sizeBytes",
                            request.getSizeBytes()));
        }
    }
}
