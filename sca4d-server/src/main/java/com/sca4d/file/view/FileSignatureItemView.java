package com.sca4d.file.view;

import com.sca4d.file.FileSignatureOperation;
import com.sca4d.file.signature.FileSignatureItemResult;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 单项文件签名结果。 */
@Data
@Accessors(chain = true)
public class FileSignatureItemView {
    private String requestId;
    private FileSignatureOperation operation;
    private List<FileAccessUrlView> files;
    private FileUploadView upload;

    /** @return 单项签名结果；读取操作返回文件地址，上传操作返回上传信息 */
    public static @NotNull FileSignatureItemView from(
            @NotNull FileSignatureItemResult result) {
        FileSignatureItemView view = new FileSignatureItemView()
                .setRequestId(result.getRequestId())
                .setOperation(result.getOperation());
        if (!result.getFiles().isEmpty()) {
            view.setFiles(result.getFiles().stream()
                    .map(FileAccessUrlView::from)
                    .toList());
        }
        if (result.getUpload() != null) {
            view.setUpload(FileUploadView.from(
                    result.getUpload(), result.getUploadGrant()));
        }
        return view;
    }
}
