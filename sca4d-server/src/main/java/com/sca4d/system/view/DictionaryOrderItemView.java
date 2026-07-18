package com.sca4d.system.view;

import com.sca4d.system.dictionary.DictionaryOrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

/** 完整顺序编辑器使用的轻量字典条目视图。 */
@Data
@Accessors(chain = true)
@Schema(description = "字典排序条目")
public class DictionaryOrderItemView {

    private Long id;
    private String label;
    private String description;
    private Long version;

    /**
     * 把已本地化的排序结果转换为 HTTP 视图。
     *
     * @param item 当前展示顺序中的字典类型或字典项
     * @return 只包含拖拽展示和乐观提交所需字段的视图
     */
    public static @NotNull DictionaryOrderItemView from(@NotNull DictionaryOrderItem item) {
        return new DictionaryOrderItemView()
                .setId(item.getId())
                .setLabel(item.getLabel())
                .setDescription(item.getDescription())
                .setVersion(item.getVersion());
    }
}
