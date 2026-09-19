package com.sca4d.app.server.controllers;

import com.sca4d.core.api.annotation.AnonymousAccess;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.storage.FileContent;
import com.sca4d.core.api.storage.FileErrorCode;
import com.sca4d.core.api.storage.FileService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Objects;

/** 执行本地目录存储签发的匿名短期 PUT 和 GET 请求。 */
@RestController
@Hidden
@AnonymousAccess
@RequestMapping("/files/transfers")
public class LocalFileTransferController {

    private final @NotNull FileService files;

    /**
     * 创建本地文件传输 Controller。
     *
     * @param files 文件服务
     */
    public LocalFileTransferController(@NotNull FileService files) {
        this.files = Objects.requireNonNull(files, "文件服务不能为空");
    }

    /**
     * 接收本地目录存储的签名上传请求。
     *
     * @param token 短期 PUT Token
     * @param request 原始 HTTP 请求
     * @return 204 空响应
     */
    @PutMapping("/uploads")
    public @NotNull ResponseEntity<Void> upload(
            @RequestParam @NotBlank @Size(max = 4096) @NotNull String token,
            @NotNull HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            throw new BusinessException(FileErrorCode.CONTENT_MISMATCH);
        }
        try {
            files.receiveUpload(
                    token,
                    contentType,
                    request.getContentLengthLong(),
                    request.getInputStream());
        } catch (IOException exception) {
            throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 返回本地目录存储中由短期 GET Token 授权的文件内容。
     *
     * @param token 短期 GET Token
     * @return 带精确媒体类型和字节数的文件流
    */
    @GetMapping("/objects")
    @SuppressWarnings("resource") // Spring 在响应写出完成后关闭 InputStreamResource 持有的文件流。
    public @NotNull ResponseEntity<InputStreamResource> access(
            @RequestParam @NotBlank @Size(max = 4096) @NotNull String token) {
        FileContent content;
        try {
            content = files.openAccess(token);
        } catch (IOException exception) {
            throw new BusinessException(FileErrorCode.BACKEND_UNAVAILABLE, exception);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.sizeBytes())
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(content.stream()));
    }
}
