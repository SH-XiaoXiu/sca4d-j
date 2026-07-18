package com.sca4d.controllers;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.auth.annotation.AuthPrincipal;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.file.FileService;
import com.sca4d.file.storage.FileContent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 文件上传和本地内容传输接口。 */
@RestController
@ConditionalOnProperty(prefix = "sca4dj.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileTransportController {

    private final FileService service;

    /**
     * 把请求体写入指定文件的临时上传位置。
     *
     * @param fileId 已创建且处于 UPLOADING 状态的文件主键
     * @param principal 当前上传文件的 Principal
     * @param request 提供文件字节、长度和 Content-Type 的 HTTP 请求
     * @return 空成功响应
     * @throws IOException 读取请求体或写入存储失败时抛出
     */
    @PutMapping("/uploads/{fileId}/content")
    @AuthPrincipal
    public @NotNull ApiResponse<Void> upload(
            @PathVariable @NotNull Long fileId,
            @AuthPrincipal @NotNull Principal principal,
            @NotNull HttpServletRequest request)
            throws IOException {
        long size = request.getContentLengthLong();
        service.writeUpload(
                fileId,
                principal.requirePrincipalId(),
                request.getInputStream(),
                size,
                request.getContentType());
        return ApiResponse.ok();
    }

    /**
     * 校验存储中的实际文件信息，并把上传状态改为 READY。
     *
     * @param fileId 已写入临时上传位置的文件主键
     * @param principal 当前上传文件的 Principal
     * @return 空成功响应
     */
    @PutMapping("/uploads/{fileId}/completion")
    @AuthPrincipal
    public @NotNull ApiResponse<Void> completeUpload(
            @PathVariable @NotNull Long fileId, @AuthPrincipal @NotNull Principal principal) {
        service.completeUpload(fileId, principal.requirePrincipalId());
        return ApiResponse.ok();
    }

    /**
     * 使用短期读取授权下载文件内容。
     *
     * @param fileId 授权中绑定的文件主键
     * @param token 文件读取授权
     * @return 带实际媒体类型、文件名和字节数的下载响应
     */
    @GetMapping("/{fileId}/content")
    @AnonymousAccess
    public @NotNull ResponseEntity<InputStreamResource> content(
            @PathVariable @NotNull Long fileId, @RequestParam @NotNull String token) {
        FileContent content = service.openContent(fileId, token);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.getContentType());
        } catch (IllegalArgumentException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.getSizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.getFilename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new InputStreamResource(content.getInputStream()));
    }
}
