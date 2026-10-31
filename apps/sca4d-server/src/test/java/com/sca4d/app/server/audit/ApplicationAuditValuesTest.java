package com.sca4d.app.server.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sca4d.app.server.authentication.request.PasskeyRegistrationRequest;
import com.sca4d.app.server.authentication.request.TotpCredentialRequest;
import com.sca4d.app.server.controllers.PasskeyController;
import com.sca4d.app.server.controllers.PasswordController;
import com.sca4d.app.server.controllers.TotpController;
import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.files.request.CreateFileUploadRequest;
import com.sca4d.app.server.platform.PlatformAccountRecord;
import com.sca4d.app.server.platform.request.CreatePlatformAccountRequest;
import com.sca4d.app.server.platform.request.ReplacePasswordRequest;
import com.sca4d.app.server.platform.view.PlatformAccountView;
import com.sca4d.app.server.system.SystemDictionaryErrorCode;
import com.sca4d.app.server.system.request.CreateDictionaryRequest;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.audit.AuditInvocation;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.audit.NoAuditPayloadFactory;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallOutcome;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.iam.base.PlatformAccountEntry;
import com.sca4d.iam.identity.IdentityStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 验证 Sca4d Server 审计 payload 不保存凭据、文件名和个人资料实际值。 */
class ApplicationAuditValuesTest {
    private static final Call ALL = new Call("audit-test", Instant.EPOCH);

    @Test
    @DisplayName("账号创建被拒绝时只保存字段名，不读取返回值")
    void deniedAccountCreationStoresOnlyFieldNames() {
        CreatePlatformAccountRequest request = new CreatePlatformAccountRequest();
        request.setUsername("private-user");
        request.setInitialPassword("Secret-password-123");
        request.setDisplayName("Private display name");
        request.setEmail("private@example.com");
        request.setPhoneNumber("+8613800000000");

        AuditDetails details = (AuditDetails) new ApplicationAuditValues.MutationDetails()
                .create(invocation(AuditResult.DENIED, null, CreatePlatformAccountRequest.class, request));

        assertThat(details.getValues())
                .containsOnlyKeys("fields")
                .doesNotContainValue(request.getUsername())
                .doesNotContainValue(request.getInitialPassword())
                .doesNotContainValue(request.getDisplayName())
                .doesNotContainValue(request.getEmail())
                .doesNotContainValue(request.getPhoneNumber());
        assertThat(details.getValues().toString())
                .doesNotContain(
                        request.getUsername(),
                        request.getInitialPassword(),
                        request.getDisplayName(),
                        request.getEmail(),
                        request.getPhoneNumber());
    }

    @Test
    @DisplayName("账号创建成功审计保存新账号标识和状态")
    void successfulAccountCreationStoresResultSummary() {
        CreatePlatformAccountRequest request = new CreatePlatformAccountRequest();
        request.setUsername("private-user");
        PlatformAccountView result = PlatformAccountView.from(
                new PlatformAccountRecord(
                        new PlatformAccountEntry(
                                42L,
                                7L,
                                9L,
                                "private-user",
                                IdentityStatus.ACTIVE,
                                Instant.EPOCH,
                                Instant.EPOCH,
                                false),
                        new PlatformUserProfile(
                                9L,
                                "Private display name",
                                "private@example.com",
                                null,
                                null,
                                Instant.EPOCH,
                                Instant.EPOCH)));

        AuditDetails details = (AuditDetails) new ApplicationAuditValues.MutationDetails()
                .create(
                        invocation(
                                AuditResult.SUCCESS,
                                ApiResponse.created(result),
                                CreatePlatformAccountRequest.class,
                                request));

        assertThat(details.getValues())
                .containsEntry("accountId", 42L)
                .containsEntry("status", "ACTIVE")
                .containsEntry("fields", List.of("username", "password", "displayName", "email", "phoneNumber"));
        assertThat(details.getValues().toString())
                .doesNotContain("private@example.com", "Private display name");
    }

    @Test
    @DisplayName("文件上传审计保存媒体摘要但不保存原始文件名")
    void fileUploadOmitsOriginalFilename() {
        CreateFileUploadRequest request = new CreateFileUploadRequest();
        request.setTargetAccountId(31L);
        request.setFilename("private-avatar-name.png");
        request.setContentType(" Image/PNG ");
        request.setSizeBytes(4096L);
        AuditDetails details = (AuditDetails) new ApplicationAuditValues.FileUploadDetails()
                .create(invocation(request, 21L));

        assertThat(details.getValues())
                .containsExactlyInAnyOrderEntriesOf(Map.of("contentType", "image/png", "sizeBytes", 4096L));
        assertThat(details.getValues().toString()).doesNotContain("private-avatar-name.png");
    }

    @Test
    @DisplayName("字典创建拒绝时保存键和稳定错误码")
    void deniedDictionaryCreationStoresConflictSummary() throws Exception {
        CreateDictionaryRequest request = new CreateDictionaryRequest();
        request.setKey("iam.identity-status");
        request.setLabel("Duplicate");
        Method method = ApplicationAuditValuesTest.class.getDeclaredMethod(
                "dictionaryInvocation",
                CreateDictionaryRequest.class);
        CallExecution execution = execution(method, request);
        execution.recordOutcome(
                CallOutcome.denied(new BusinessException(SystemDictionaryErrorCode.DICTIONARY_KEY_EXISTS)));
        AuditInvocation invocation = new AuditInvocation(execution, AuditResult.DENIED);

        AuditDetails details = (AuditDetails) new ApplicationAuditValues.DictionaryMutationDetails()
                .create(invocation);

        assertThat(details.getValues())
                .containsEntry("dictionaryKey", "iam.identity-status")
                .containsEntry("errorCode", "3100")
                .containsEntry("errorMessageKey", "error.system-dictionary.dictionary-key-exists");
    }

    @Test
    @DisplayName("密码、TOTP 和 Passkey 写操作不声明审计 payload")
    void credentialMutationsDoNotDeclarePayloadFactory() throws NoSuchMethodException {
        assertThat(
                payloadFactory(
                        PasswordController.class,
                        "replace",
                        Long.class,
                        ReplacePasswordRequest.class))
                .isEqualTo(NoAuditPayloadFactory.class);
        assertThat(
                payloadFactory(
                        TotpController.class,
                        "activate",
                        Long.class,
                        TotpCredentialRequest.class))
                .isEqualTo(NoAuditPayloadFactory.class);
        assertThat(
                payloadFactory(
                        TotpController.class,
                        "replace",
                        Long.class,
                        TotpCredentialRequest.class))
                .isEqualTo(NoAuditPayloadFactory.class);
        assertThat(
                payloadFactory(
                        PasskeyController.class,
                        "register",
                        Account.class,
                        PasskeyRegistrationRequest.class))
                .isEqualTo(NoAuditPayloadFactory.class);
    }

    /** 返回目标 Controller 方法声明的审计 payload 工厂。 */
    private static Class<?> payloadFactory(
            Class<?> controller,
            String methodName,
            Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = controller.getMethod(methodName, parameterTypes);
        return method.getAnnotation(Audit.class).payloadFactory();
    }

    /** 创建供 payload 工厂读取的调用快照。 */
    private static AuditInvocation invocation(Object... arguments) {
        return invocation(AuditResult.SUCCESS, null, ApplicationAuditValuesTest.class, arguments);
    }

    /** 创建带有业务返回值的调用快照。 */
    private static AuditInvocation invocation(
            AuditResult auditResult,
            Object result,
            Class<?> owner,
            Object... arguments) {
        try {
            Method method = owner == CreatePlatformAccountRequest.class
                    ? ApplicationAuditValuesTest.class.getDeclaredMethod(
                            "accountInvocation",
                            CreatePlatformAccountRequest.class)
                    : ApplicationAuditValuesTest.class.getDeclaredMethod(
                            "fileInvocation",
                            CreateFileUploadRequest.class,
                            Long.class);
            CallExecution execution = execution(method, arguments);
            if (auditResult == AuditResult.SUCCESS) {
                execution.recordOutcome(CallOutcome.success(result));
            } else if (auditResult == AuditResult.DENIED) {
                execution.recordOutcome(CallOutcome.denied(new IllegalStateException("测试调用被拒绝")));
            } else {
                execution.recordOutcome(CallOutcome.failed(new IllegalStateException("测试调用失败")));
            }
            return new AuditInvocation(execution, auditResult);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    /** 创建供审计 payload 工厂读取的已声明方法执行。 */
    private static CallExecution execution(Method method, Object... arguments) {
        return new CallExecution(
                ALL,
                ApplicationAuditValuesTest.class,
                method,
                arguments,
                method.getGenericReturnType());
    }

    @SuppressWarnings("unused")
    private void accountInvocation(CreatePlatformAccountRequest request) {
    }

    @SuppressWarnings("unused")
    private void fileInvocation(CreateFileUploadRequest request, Long uploaderPrincipalId) {
    }

    @SuppressWarnings("unused")
    private void dictionaryInvocation(CreateDictionaryRequest request) {
    }
}
