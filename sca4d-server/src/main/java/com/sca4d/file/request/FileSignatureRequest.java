package com.sca4d.file.request;

import com.sca4d.file.signature.FileSignatureItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 批量文件签名请求。 */
@Data
public class FileSignatureRequest {
    @Valid
    @NotEmpty(message = "{validation.file-signature.items-required}")
    @Size(max = 100, message = "{validation.file-signature.items-limit}")
    private List<FileSignatureItem> items;
}
