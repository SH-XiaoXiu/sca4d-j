package com.sca4d.app.server.files;

import com.sca4d.app.server.entity.PlatformUserProfile;
import com.sca4d.app.server.service.PlatformUserProfileService;
import com.sca4d.core.api.resource.ResourceReference;
import com.sca4d.core.api.storage.FileAccessUrl;
import com.sca4d.core.api.storage.FileService;
import com.sca4d.core.api.storage.FileUpload;
import com.sca4d.core.api.storage.FileUploadIntent;
import com.sca4d.core.api.storage.FileUse;
import com.sca4d.iam.base.PlatformPrincipal;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 执行示例 App 的头像文件申请、完成和读取流程。 */
@Service
public class PlatformFileService {
    private final @NotNull FileService files;

    /**
     * 创建使用公共文件服务的 App 文件服务。
     *
     * @param files 公共文件服务
     */
    public PlatformFileService(@NotNull FileService files) {
        this.files = Objects.requireNonNull(files, "文件服务不能为空");
    }

    /**
     * 为目标 Platform 用户资料创建头像上传申请。
     *
     * @param targetPrincipalId 头像所属的 Platform Principal 标识
     * @param uploader 执行上传的当前 Platform Principal
     * @param filename 原始文件名
     * @param contentType 已校验的媒体类型
     * @param sizeBytes 精确正数字节数
     * @return 客户端必须原样执行的上传请求
     */
    @CheckReturnValue
    public @NotNull FileUpload createAvatarUpload(
            long targetPrincipalId,
            @NotNull PlatformPrincipal uploader,
            @NotNull String filename,
            @NotNull String contentType,
            long sizeBytes) {
        return files.createUpload(
                Objects.requireNonNull(uploader, "上传 Principal 不能为空"),
                new FileUploadIntent(
                        new FileUse(
                                PlatformUserProfileService.PROFILE_AVATAR,
                                new ResourceReference(
                                        PlatformUserProfile.RESOURCE_TYPE,
                                        Long.toString(targetPrincipalId))),
                        filename,
                        contentType,
                        sizeBytes));
    }

    /**
     * 确认当前 Platform Principal 上传的文件内容已经写入并转为可绑定状态。
     *
     * @param fileId 正数文件标识
     * @param uploader 当前 Platform Principal
     */
    public void completeUpload(long fileId, @NotNull PlatformPrincipal uploader) {
        files.completeUpload(fileId, Objects.requireNonNull(uploader, "上传 Principal 不能为空"));
    }

    /**
     * 为一组已经通过 App 权限检查的头像文件生成短期地址。
     *
     * @param fileIds 一至一百个去重后的正数文件标识
     * @return 与输入文件标识顺序对应的短期读取地址
     */
    @CheckReturnValue
    public @NotNull @Unmodifiable List<@NotNull FileAccessUrl> createAccessUrls(
            @NotNull Collection<@NotNull Long> fileIds) {
        return files.createAccessUrls(fileIds);
    }
}
