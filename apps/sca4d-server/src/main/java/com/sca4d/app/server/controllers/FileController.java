package com.sca4d.app.server.controllers;

import com.sca4d.app.server.audit.ApplicationAuditValues;
import com.sca4d.app.server.files.FileResourceResolver;
import com.sca4d.app.server.files.PlatformFileService;
import com.sca4d.app.server.files.PlatformUserProfileResourceResolver;
import com.sca4d.app.server.files.request.CreateFileAccessUrlsRequest;
import com.sca4d.app.server.files.request.CreateFileUploadRequest;
import com.sca4d.app.server.files.view.FileAccessUrlView;
import com.sca4d.app.server.files.view.FileUploadView;
import com.sca4d.app.server.platform.PlatformAccountRecord;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.PlatformAccountApplicationService;
import com.sca4d.core.api.annotation.Audit;
import com.sca4d.core.api.annotation.AuditTarget;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.permission.Action;
import com.sca4d.core.api.storage.FileAccessUrl;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileUpload;
import com.sca4d.core.api.storage.StoredFile;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iam.base.PlatformPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 为示例 App 的 Platform 用户头像提供存储无关的上传和读取接口。 */
@RestController
@RequestMapping("/files")
@Tag(name = "Files")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class FileController {

    private static final long AVATAR_MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> AVATAR_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final @NotNull PlatformFileService files;

    private final @NotNull PlatformAccountApplicationService accounts;

    /**
     * 创建 Platform 头像文件入口。
     *
     * @param files App 文件服务
     * @param accounts Platform Account 应用服务
     */
    public FileController(
            @NotNull PlatformFileService files,
            @NotNull PlatformAccountApplicationService accounts) {
        this.files = Objects.requireNonNull(files, "文件服务不能为空");
        this.accounts = Objects.requireNonNull(accounts, "Platform Account 应用服务不能为空");
    }

    /**
     * 为本人或管理员可维护的 Platform Account 头像申请上传地址。
     *
     * @param request 目标 Account 和精确内容信息
     * @param currentPrincipalId 当前 Platform Principal 标识
     * @return 客户端必须原样执行的上传请求
     */
    @PostMapping("/uploads")
    @Operation(summary = "申请文件上传地址")
    @RequirePermission(resourceResolver = PlatformUserProfileResourceResolver.class, action = Action.WRITE_VALUE)
    @Audit(operation = "STORAGE/FILE/UPLOAD/CREATE", targetResolver = PlatformUserProfileResourceResolver.class, payloadFactory = ApplicationAuditValues.FileUploadDetails.class)
    public @NotNull ApiResponse<FileUploadView> createUpload(
            @RequestBody @NotNull CreateFileUploadRequest request,
            @CurrentPrincipal @NotNull Long currentPrincipalId) {
        PlatformAccountRecord targetAccount = accounts.loadByAccountId(request.getTargetAccountId());

        String contentType = request.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!AVATAR_CONTENT_TYPES.contains(contentType)
                || request.getSizeBytes() > AVATAR_MAX_SIZE_BYTES) {
            throw new BusinessException(FileErrorCode.INVALID);
        }
        FileUpload upload = files.createAvatarUpload(
                targetAccount.account().principalId(),
                new PlatformPrincipal(currentPrincipalId),
                request.getFilename(),
                contentType,
                request.getSizeBytes());
        return ApiResponse.created(FileUploadView.from(upload));
    }

    /**
     * 确认当前 Platform Principal 负责的头像文件已经上传完成。
     *
     * @param fileId 文件标识
     * @param currentPrincipalId 当前 Platform Principal 标识
     * @return 空成功响应
     */
    @PostMapping("/uploads/{fileId}/completion")
    @Operation(summary = "完成文件上传")
    @RequirePermission(resourceResolver = FileResourceResolver.class, action = Action.WRITE_VALUE)
    @Audit(operation = "STORAGE/FILE/UPLOAD/COMPLETE")
    public @NotNull ApiResponse<Void> completeUpload(
            @AuditTarget(resourceType = StoredFile.RESOURCE_TYPE_VALUE) @PathVariable long fileId,
            @CurrentPrincipal @NotNull Long currentPrincipalId) {
        files.completeUpload(fileId, new PlatformPrincipal(currentPrincipalId));
        return ApiResponse.noContent();
    }

    /**
     * 为当前账号有权读取的头像文件批量生成短期地址。
     *
     * <p>任一文件不存在、场景不符或无读取权限时，整批使用同一个文件不存在结果失败。
     *
     * @param request 一至一百个文件标识
     * @return 与去重后文件标识对应的短期读取地址
     */
    @PostMapping("/access-urls")
    @Operation(summary = "批量生成文件读取地址")
    @RequirePermission(resourceResolver = FileResourceResolver.class, action = Action.READ_VALUE)
    public @NotNull ApiResponse<List<@NotNull FileAccessUrlView>> createAccessUrls(
            @RequestBody @NotNull CreateFileAccessUrlsRequest request) {
        List<Long> fileIds = request.getFileIds().stream().distinct().toList();
        List<FileAccessUrl> accessUrls;
        try {
            accessUrls = files.createAccessUrls(fileIds);
        } catch (BusinessException exception) {
            throw hiddenFileFailure(exception);
        }
        return ApiResponse.ok(accessUrls.stream().map(FileAccessUrlView::from).toList());
    }

    /** 隐藏批量读取失败对应的具体文件和底层原因。 */
    private static @NotNull BusinessException hiddenFileFailure(@NotNull BusinessException failure) {
        return new BusinessException(FileErrorCode.NOT_FOUND, failure);
    }
}
