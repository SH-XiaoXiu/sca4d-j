package com.sca4d.file.signature;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 批量文件签名结果。
 */
@Data
@Accessors(chain = true)
public class FileSignatureResult {

    private List<FileSignatureItemResult> items = List.of();
}
