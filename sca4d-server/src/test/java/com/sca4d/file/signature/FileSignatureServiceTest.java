package com.sca4d.file.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.common.auth.ForbiddenException;
import com.sca4d.common.auth.Subject;
import com.sca4d.common.exception.BizException;
import com.sca4d.common.exception.CommonErrorCode;
import com.sca4d.common.permission.SessionPermission;
import com.sca4d.common.request.CurrentRequest;
import com.sca4d.config.properties.Sca4djFileProperties;
import com.sca4d.file.FileAccessUrl;
import com.sca4d.file.FileService;
import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.FileUpload;
import com.sca4d.file.FileUploadMethod;
import com.sca4d.file.FileUploadMode;
import com.sca4d.file.storage.FileUploadInstruction;
import com.sca4d.test.TestSession;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 验证批量文件签名按场景分组校验并签发读取或上传授权。 */
class FileSignatureServiceTest {

    @Test
    void shouldAuthorizeWholeBatchBeforeCreatingUpload() {
        FileService fileService = mock(FileService.class);
        FileGrantSigner grantSigner = mock(FileGrantSigner.class);
        FileSignaturePolicy deniedPolicy = new FileSignaturePolicy() {
            @Override
            public @NotNull Set<String> scenes() {
                return Set.of("product.product.cover");
            }

            @Override
            public void authorize(@NotNull CurrentRequest auth, @NotNull List<FileSignatureItem> items) {
                throw new BizException(CommonErrorCode.FORBIDDEN);
            }
        };
        FileSignatureService service = service(fileService, grantSigner, deniedPolicy);

        assertThatThrownBy(() -> service.sign(List.of(upload("first"), upload("second")), auth()))
                .isInstanceOf(BizException.class);
        verify(fileService, never()).createUpload(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    void shouldRejectRequestIdsThatDuplicateAfterNormalization() {
        FileService fileService = mock(FileService.class);
        FileGrantSigner grantSigner = mock(FileGrantSigner.class);
        FileSignatureService service = service(fileService, grantSigner, new RecordingPolicy());

        assertThatThrownBy(() -> service.sign(List.of(upload("same"), upload(" same ")), auth()))
                .isInstanceOfSatisfying(BizException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.file.request-id-duplicate"));
        verify(fileService, never()).createUpload(anyLong(), anyString(), any(), anyLong());
    }

    @Test
    void shouldReturnReadAndUploadResultsAfterPolicyApproval() {
        FileService fileService = mock(FileService.class);
        FileGrantSigner grantSigner = mock(FileGrantSigner.class);
        RecordingPolicy policy = new RecordingPolicy();
        FileSignatureService service = service(fileService, grantSigner, policy);
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
        when(fileService.createAccessUrls(List.of(10L)))
                .thenReturn(List.of(new FileAccessUrl().setFileId(10L).setUrl("/read").setExpiresAt(expiresAt)));
        when(fileService.createUpload(1L, "a.png", "image/png", 10L))
                .thenReturn(new FileUpload()
                        .setFileId(20L)
                        .setInstruction(new FileUploadInstruction()
                                .setMode(FileUploadMode.DIRECT)
                                .setMethod(FileUploadMethod.PUT)
                                .setUrl("/upload")
                                .setExpiresAt(expiresAt)));
        when(grantSigner.issueUpload(anyLong(), anyLong(), anyString(), any(), any()))
                .thenReturn("grant");

        var result = service.sign(List.of(read(), upload("upload")), auth());

        assertThat(policy.items).hasSize(2);
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getFiles()).extracting("fileId").containsExactly(10L);
        assertThat(result.getItems().get(1).getUploadGrant()).isEqualTo("grant");
    }

    @Test
    void shouldRejectAuthenticatedRequestWhenSessionHasNoDefaultPrincipal() {
        FileService fileService = mock(FileService.class);
        FileGrantSigner grantSigner = mock(FileGrantSigner.class);
        FileSignatureService service = service(fileService, grantSigner, new RecordingPolicy());
        Subject subject = new Subject(1L, "Subject 1");
        CurrentRequest request = CurrentRequest.authenticated(TestSession.withAuthorization(
                subject, List.of(), Map.<Long, SessionPermission>of()));

        assertThatThrownBy(() -> service.sign(List.of(upload("upload")), request))
                .isInstanceOfSatisfying(ForbiddenException.class, exception -> assertThat(exception.getMessageKey())
                        .isEqualTo("error.authentication.default-principal-unavailable"));
        verify(fileService, never()).createUpload(anyLong(), anyString(), any(), anyLong());
    }

    /**
     * 使用指定策略和测试依赖创建文件签名服务。
     *
     * @param fileService 返回文件状态和归属的测试替身
     * @param grantSigner 上传授权签名测试替身
     * @param policy 当前用例注册的业务签名策略
     * @return 使用默认文件配置的签名服务
     */
    private @NotNull FileSignatureService service(
            @NotNull FileService fileService,
            @NotNull FileGrantSigner grantSigner,
            @NotNull FileSignaturePolicy policy) {
        return new DefaultFileSignatureService(
                new FileSignatureRegistry(List.of(policy)),
                fileService,
                grantSigner,
                new Sca4djFileProperties());
    }

    /**
     * 创建商品封面上传签名意图。
     *
     * @param requestId 当前批次内用于关联响应的请求编码
     * @return 使用固定商品资源和文件元数据的上传意图
     */
    private @NotNull FileSignatureItem upload(@NotNull String requestId) {
        FileSignatureItem item = new FileSignatureItem();
        item.setRequestId(requestId);
        item.setOperation(FileSignatureOperation.UPLOAD);
        item.setScene("product.product.cover");
        item.setResourceId("100");
        item.setOriginalFilename("a.png");
        item.setContentType("image/png");
        item.setSizeBytes(10L);
        return item;
    }

    /**
     * 创建读取既有商品封面的签名意图。
     *
     * @return 指向固定商品资源和文件主键的读取意图
     */
    private @NotNull FileSignatureItem read() {
        FileSignatureItem item = new FileSignatureItem();
        item.setRequestId("read");
        item.setOperation(FileSignatureOperation.READ);
        item.setScene("product.product.cover");
        item.setResourceId("100");
        item.setFileId(10L);
        return item;
    }

    /**
     * 创建包含 DEFAULT Principal 的已认证测试请求。
     *
     * @return 包含当前 Subject 和 Principal 的文件签名请求
     */
    private @NotNull CurrentRequest auth() {
        return CurrentRequest.authenticated(TestSession.of(1L));
    }

    /** 记录整批授权输入，验证签名服务只在策略通过后创建文件授权。 */
    private static class RecordingPolicy implements FileSignaturePolicy {
        private final List<FileSignatureItem> items = new ArrayList<>();

        @Override
        public @NotNull Set<String> scenes() {
            return Set.of("product.product.cover");
        }

        @Override
        public void authorize(@NotNull CurrentRequest auth, @NotNull List<FileSignatureItem> items) {
            this.items.addAll(items);
        }
    }
}
