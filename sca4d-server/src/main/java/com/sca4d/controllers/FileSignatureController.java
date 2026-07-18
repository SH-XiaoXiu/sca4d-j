package com.sca4d.controllers;

import com.sca4d.common.auth.annotation.AnonymousAccess;
import com.sca4d.common.concurrent.ratelimit.RateLimit;
import com.sca4d.common.request.CurrentRequestHolder;
import com.sca4d.common.web.ApiResponse;
import com.sca4d.file.request.FileSignatureRequest;
import com.sca4d.file.signature.FileSignatureResult;
import com.sca4d.file.signature.FileSignatureService;
import com.sca4d.file.view.FileSignatureView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 共用文件签名接口。 */
@RestController
@ConditionalOnProperty(prefix = "sca4dj.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/files/signatures")
@RequiredArgsConstructor
public class FileSignatureController {

    private final FileSignatureService fileSignatureService;

    /**
     * 对整批文件读取和上传意图完成业务授权并签发结果。
     *
     * @param request 已通过传输层格式校验的批量签名请求
     * @return 与请求项顺序一致的访问地址或上传授权
     */
    @PostMapping
    @AnonymousAccess
    @RateLimit(capacity = 300, refillTokens = 300, timeWindowSeconds = 60)
    public @NotNull ApiResponse<FileSignatureView> sign(@Valid @RequestBody @NotNull FileSignatureRequest request) {
        FileSignatureResult result = fileSignatureService.sign(request.getItems(), CurrentRequestHolder.current());
        return ApiResponse.ok(FileSignatureView.from(result));
    }
}
