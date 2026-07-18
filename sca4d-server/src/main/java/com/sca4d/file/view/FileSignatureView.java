package com.sca4d.file.view;

import com.sca4d.file.signature.FileSignatureResult;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 批量文件签名结果。 */
@Data
@Accessors(chain = true)
public class FileSignatureView {
    private List<FileSignatureItemView> items;

    /** @return 保持请求顺序的批量文件签名响应 */
    public static @NotNull FileSignatureView from(@NotNull FileSignatureResult result) {
        return new FileSignatureView().setItems(result.getItems().stream()
                .map(FileSignatureItemView::from)
                .toList());
    }
}
