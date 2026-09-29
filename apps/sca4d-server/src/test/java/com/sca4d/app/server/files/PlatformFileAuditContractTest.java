package com.sca4d.app.server.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.app.server.audit.ApplicationAuditValues;
import com.sca4d.app.server.audit.AuditDetails;
import com.sca4d.app.server.controllers.FileController;
import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.files.request.CreateFileAccessUrlsRequest;
import com.sca4d.app.server.files.request.CreateFileUploadRequest;
import com.sca4d.app.server.platform.PlatformAccountRecord;
import com.sca4d.app.server.service.PlatformAccountApplicationService;
import com.sca4d.audit.config.AuditAutoConfiguration;
import com.sca4d.audit.recording.mapper.AuditMapper;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.audit.AuditCallInterceptor;
import com.sca4d.core.api.audit.AuditPayloadFactory;
import com.sca4d.core.api.audit.AuditRecord;
import com.sca4d.core.api.audit.AuditRecorder;
import com.sca4d.core.api.audit.AuditResolverLookup;
import com.sca4d.core.api.audit.AuditResult;
import com.sca4d.core.api.audit.AuditTargetResolver;
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.call.CallExecution;
import com.sca4d.core.api.call.CallLifecycle;
import com.sca4d.core.api.call.CallValueResolverLookup;
import com.sca4d.core.api.call.SPICallValueResolver;
import com.sca4d.core.api.concurrent.DistributedLock;
import com.sca4d.core.api.concurrent.IdempotencyStore;
import com.sca4d.core.api.concurrent.RateLimiter;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.identity.IdentityCallInterceptor;
import com.sca4d.core.api.identity.SystemPrincipal;
import com.sca4d.core.api.identity.SystemPrincipals;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.permission.Permission;
import com.sca4d.core.api.permission.PermissionAssessment;
import com.sca4d.core.api.permission.PermissionCallInterceptor;
import com.sca4d.core.api.permission.PermissionDecision;
import com.sca4d.core.api.permission.PermissionPolicy;
import com.sca4d.core.api.resource.Resource;
import com.sca4d.core.api.storage.FileAccessUrl;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileUpload;
import com.sca4d.core.api.storage.StoredFile;
import com.sca4d.core.spring.call.CallHolder;
import com.sca4d.core.spring.config.CoreJacksonAutoConfiguration;
import com.sca4d.core.spring.config.CoreSpringAutoConfiguration;
import com.sca4d.core.spring.config.InstanceProperties;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.iam.base.PlatformAccountEntry;
import com.sca4d.iam.identity.IdentityStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** 验证文件 Controller 在调用业务服务前执行资源权限，并只审计文件写操作。 */
class PlatformFileAuditContractTest {

    static {
        SystemPrincipals.publish(
                Map.of(
                        SystemPrincipals.MAIN,
                        new SystemPrincipal(1L, SystemPrincipals.MAIN)));
    }

    private static final Instant NOW = Instant.parse("2026-08-07T00:00:00Z");

    private PlatformFileService files;

    private PlatformAccountApplicationService accounts;

    private FileController controller;

    private List<Permission> assessedPermissions;

    private List<AuditRecord> records;

    private CallLifecycle lifecycle;

    @BeforeEach
    void createExecutor() {
        files = mock(PlatformFileService.class);
        accounts = mock(PlatformAccountApplicationService.class);
        controller = new FileController(files, accounts);
        lifecycle = createLifecycle(null);
    }

    @Test
    @DisplayName("Spring 组合核心与审计模块后执行资源权限并选择应用审计实现")
    void appliesPermissionsAndApplicationAuditThroughSpring() {
        new WebApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                CoreJacksonAutoConfiguration.class,
                                JacksonAutoConfiguration.class,
                                WebMvcAutoConfiguration.class,
                                AuditAutoConfiguration.class,
                                CoreSpringAutoConfiguration.class))
                .withPropertyValues(
                        "spring.application.name=sca4d-test",
                        "sca4dj.i18n.supported-locales[0]=zh-CN",
                        "sca4dj.i18n.default-locale=zh-CN")
                .withBean("auditMapper", AuditMapper.class, () -> mock(AuditMapper.class))
                .withBean(InstanceProperties.class, InstanceProperties::new)
                .withBean(Clock.class, () -> Clock.fixed(NOW, ZoneOffset.UTC))
                .withBean(RateLimiter.class, () -> mock(RateLimiter.class))
                .withBean(IdempotencyStore.class, () -> mock(IdempotencyStore.class))
                .withBean(DistributedLock.class, () -> mock(DistributedLock.class))
                .withBean("applicationAudit", AuditRecorder.class, () -> records::add)
                .withBean(FileResourceResolver.class, FileResourceResolver::new)
                .withBean(FileController.class, () -> controller)
                .withBean(PermissionPolicy.class, () -> new PermissionPolicy() {
                    @Override
                    public boolean supports(@NotNull Resource resource) {
                        return StoredFile.RESOURCE_TYPE.equals(resource.type());
                    }

                    @Override
                    public @NotNull PermissionDecision decide(@NotNull Call call, @NotNull Permission permission) {
                        assessedPermissions.add(permission);
                        return PermissionDecision.allow();
                    }
                })
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CallHolder.run(
                            Call.system("assembled-audit", NOW),
                            () -> context.getBean(FileController.class).completeUpload(101L, 1L));
                    assertThat(assessedPermissions).singleElement()
                            .satisfies(permission -> assertThat(permission.resource().id()).isEqualTo("101"));
                    assertThat(records).singleElement()
                            .satisfies(
                                    record -> assertThat(record.operation().id())
                                            .isEqualTo("STORAGE/FILE/UPLOAD/COMPLETE"));
                });
    }

    @Test
    @DisplayName("Records Avatar Upload At The Controller Against The Target User Profile")
    void recordsAvatarUploadAgainstTheTargetUserProfile() throws Exception {
        long principalId = 41L;
        long targetAccountId = 51L;
        long currentPrincipalId = 52L;
        PlatformAccountRecord target = account(targetAccountId, principalId);
        CreateFileUploadRequest request = uploadRequest(targetAccountId);
        FileUpload upload = new FileUpload(
                61L,
                "PUT",
                URI.create("https://files.example.test/uploads/61"),
                Map.of("Content-Type", "image/png"),
                NOW.plusSeconds(600));
        when(accounts.loadByAccountId(targetAccountId)).thenReturn(target);
        when(
                files.createAvatarUpload(
                        eq(principalId),
                        argThat(uploader -> uploader.principalId() == currentPrincipalId),
                        eq(request.getFilename()),
                        eq("image/png"),
                        eq(request.getSizeBytes())))
                .thenReturn(upload);
        Method method = FileController.class.getMethod(
                "createUpload",
                CreateFileUploadRequest.class,
                Long.class);

        Object result = execute(
                Call.system("avatar-upload", NOW),
                method,
                new Object[] {request, currentPrincipalId},
                () -> controller.createUpload(request, currentPrincipalId));

        assertThat(result).isInstanceOf(ApiResponse.class);
        assertThat(assessedPermissions)
                .singleElement()
                .satisfies(permission -> {
                    assertThat(permission.resource().type()).isEqualTo(PlatformUserProfile.RESOURCE_TYPE);
                    assertThat(permission.resource().id()).isEqualTo(Long.toString(principalId));
                    assertThat(permission.action()).isEqualTo(Action.WRITE);
                });
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.operation().id()).isEqualTo("STORAGE/FILE/UPLOAD/CREATE");
            assertThat(record.target()).hasValueSatisfying(targetResource -> {
                assertThat(targetResource.type()).isEqualTo(PlatformUserProfile.RESOURCE_TYPE);
                assertThat(targetResource.id()).isEqualTo(Long.toString(principalId));
            });
            assertThat(record.result()).isEqualTo(AuditResult.SUCCESS);
            assertThat(record.payload()).hasValueSatisfying(payload -> {
                assertThat(payload).isInstanceOf(AuditDetails.class);
                assertThat(((AuditDetails) payload).getValues())
                        .isEqualTo(Map.of("contentType", "image/png", "sizeBytes", 128L));
            });
        });
        verify(files).createAvatarUpload(
                eq(principalId),
                argThat(uploader -> uploader.principalId() == currentPrincipalId),
                eq(request.getFilename()),
                eq("image/png"),
                eq(request.getSizeBytes()));
    }

    @Test
    @DisplayName("Records Upload Completion At The Controller Against The File")
    void recordsUploadCompletionAgainstTheFile() throws Exception {
        long fileId = 71L;
        long currentPrincipalId = 72L;
        Method method = FileController.class.getMethod("completeUpload", long.class, Long.class);

        execute(
                Call.system("avatar-completion", NOW),
                method,
                new Object[] {fileId, currentPrincipalId},
                () -> controller.completeUpload(fileId, currentPrincipalId));

        assertThat(assessedPermissions)
                .singleElement()
                .satisfies(permission -> {
                    assertThat(permission.resource().type()).isEqualTo(StoredFile.RESOURCE_TYPE);
                    assertThat(permission.resource().id()).isEqualTo(Long.toString(fileId));
                    assertThat(permission.action()).isEqualTo(Action.WRITE);
                });
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.operation().id()).isEqualTo("STORAGE/FILE/UPLOAD/COMPLETE");
            assertThat(record.target()).hasValueSatisfying(targetResource -> {
                assertThat(targetResource.type()).isEqualTo(StoredFile.RESOURCE_TYPE);
                assertThat(targetResource.id()).isEqualTo(Long.toString(fileId));
            });
            assertThat(record.result()).isEqualTo(AuditResult.SUCCESS);
        });
        verify(files).completeUpload(
                eq(fileId),
                argThat(uploader -> uploader.principalId() == currentPrincipalId));
    }

    @Test
    @DisplayName("Assesses Every File And Creates Access Urls In One Service Call Without Audit")
    void createsBatchAccessUrlsWithoutAudit() throws Exception {
        CreateFileAccessUrlsRequest request = accessRequest(81L, 82L, 81L);
        List<FileAccessUrl> accessUrls = List.of(
                new FileAccessUrl(81L, URI.create("https://files.example.test/81"), NOW.plusSeconds(600)),
                new FileAccessUrl(82L, URI.create("https://files.example.test/82"), NOW.plusSeconds(600)));
        when(files.createAccessUrls(List.of(81L, 82L))).thenReturn(accessUrls);
        Method method = FileController.class.getMethod(
                "createAccessUrls",
                CreateFileAccessUrlsRequest.class);

        execute(
                Call.system("avatar-access", NOW),
                method,
                new Object[] {request},
                () -> controller.createAccessUrls(request));

        assertThat(assessedPermissions)
                .extracting(permission -> permission.resource().id())
                .containsExactly("81", "82");
        assertThat(assessedPermissions)
                .allSatisfy(permission -> assertThat(permission.action()).isEqualTo(Action.READ));
        verify(files).createAccessUrls(eq(List.of(81L, 82L)));
        assertThat(records).isEmpty();
    }

    @Test
    @DisplayName("Does Not Invoke The File Service When Any Batch Resource Is Denied")
    void doesNotCreateBatchAccessUrlsWhenAnyFileIsDenied() throws Exception {
        lifecycle = createLifecycle("82");
        CreateFileAccessUrlsRequest request = accessRequest(81L, 82L, 83L);
        Method method = FileController.class.getMethod(
                "createAccessUrls",
                CreateFileAccessUrlsRequest.class);

        assertThatThrownBy(
                () -> execute(
                        Call.system("avatar-access-denied", NOW),
                        method,
                        new Object[] {request},
                        () -> controller.createAccessUrls(request)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(FileErrorCode.NOT_FOUND));

        assertThat(assessedPermissions)
                .extracting(permission -> permission.resource().id())
                .containsExactly("81", "82");
        verifyNoInteractions(files);
        assertThat(records).isEmpty();
    }

    /** 创建可以拒绝指定资源标识的固定调用生命周期。 */
    private CallLifecycle createLifecycle(@Nullable String deniedId) {
        assessedPermissions = new ArrayList<>();
        records = new ArrayList<>();
        PermissionPolicy applicationFiles = new PermissionPolicy() {
            @Override
            public boolean supports(Resource resource) {
                return PlatformUserProfile.RESOURCE_TYPE.equals(resource.type())
                        || StoredFile.RESOURCE_TYPE.equals(resource.type());
            }

            @Override
            public PermissionDecision decide(Call call, Permission permission) {
                assessedPermissions.add(permission);
                if (permission.resource().id().equals(deniedId)) {
                    throw new BusinessException(FileErrorCode.NOT_FOUND);
                }
                return PermissionDecision.allow();
            }
        };
        PlatformUserProfileResourceResolver profileResolver = new PlatformUserProfileResourceResolver(accounts);
        FileResourceResolver fileResolver = new FileResourceResolver();
        CallValueResolverLookup resolvers = resolverType -> {
            if (resolverType == PlatformUserProfileResourceResolver.class) {
                return profileResolver;
            }
            if (resolverType == FileResourceResolver.class) {
                return fileResolver;
            }
            throw new IllegalArgumentException("未知测试解析器: " + resolverType.getName());
        };
        AuditResolverLookup auditResolvers = new AuditResolverLookup() {
            @Override
            public @NotNull AuditTargetResolver targetResolver(
                    @NotNull Class<? extends AuditTargetResolver> resolverType) {
                if (resolverType == PlatformUserProfileResourceResolver.class) {
                    return profileResolver;
                }
                throw new IllegalArgumentException("未知测试审计目标解析器: " + resolverType.getName());
            }

            @Override
            public @NotNull AuditPayloadFactory payloadFactory(
                    @NotNull Class<? extends AuditPayloadFactory> factoryType) {
                if (factoryType == ApplicationAuditValues.FileUploadDetails.class) {
                    return new ApplicationAuditValues.FileUploadDetails();
                }
                throw new IllegalArgumentException("未知测试审计 payload 工厂: " + factoryType.getName());
            }
        };
        SPICallValueResolver expressions = (expression, execution) -> {
            throw new AssertionError("文件审计测试不使用注解表达式");
        };
        return new CallLifecycle(
                List.of(
                        new IdentityCallInterceptor(),
                        new PermissionCallInterceptor(new PermissionAssessment(List.of(applicationFiles)), resolvers, expressions),
                        new AuditCallInterceptor(
                                List.of(records::add),
                                Audit.Mode.DIRECT,
                                auditResolvers,
                                expressions,
                                Clock.fixed(NOW, ZoneOffset.UTC))));
    }

    /** 建立当前 Controller 方法执行并交给固定调用生命周期。 */
    private <T> T execute(
            Call call,
            Method method,
            Object[] arguments,
            Callable<T> invocation) throws Exception {
        return lifecycle.execute(
                new CallExecution(call, FileController.class, method, arguments, ApiResponse.class),
                invocation);
    }

    /** 创建测试使用的完整 Platform Account 结果。 */
    private static PlatformAccountRecord account(long accountId, long principalId) {
        return new PlatformAccountRecord(
                new PlatformAccountEntry(
                        accountId,
                        11L,
                        principalId,
                        "file-test-user",
                        IdentityStatus.ACTIVE,
                        NOW,
                        NOW,
                        false),
                new PlatformUserProfile(
                        principalId,
                        "File Test User",
                        null,
                        null,
                        null,
                        NOW,
                        NOW));
    }

    /** 创建媒体信息完整的头像上传请求。 */
    private static CreateFileUploadRequest uploadRequest(long targetAccountId) {
        CreateFileUploadRequest request = new CreateFileUploadRequest();
        request.setTargetAccountId(targetAccountId);
        request.setFilename("private-avatar-name.png");
        request.setContentType(" Image/PNG ");
        request.setSizeBytes(128L);
        return request;
    }

    /** 创建包含指定文件标识的批量读取请求。 */
    private static CreateFileAccessUrlsRequest accessRequest(Long... fileIds) {
        CreateFileAccessUrlsRequest request = new CreateFileAccessUrlsRequest();
        request.setFileIds(List.of(fileIds));
        return request;
    }
}
