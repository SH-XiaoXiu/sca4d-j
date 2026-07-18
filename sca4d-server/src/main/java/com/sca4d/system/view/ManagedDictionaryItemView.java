package com.sca4d.system.view;

import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.common.dictionary.DictionaryTone;
import com.sca4d.system.DictionaryStatus;
import com.sca4d.system.dictionary.ManagedDictionaryItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/** 包含字典项管理、排序和 409 恢复所需完整数据的视图。 */
@Data
@Accessors(chain = true)
@Schema(description = "字典项管理视图")
public class ManagedDictionaryItemView {

    private Long id;
    private Long dictionaryId;
    private String value;
    private DictionarySource source;
    private String labelKey;
    private Map<String, String> labels;
    private String label;
    private DictionaryTone tone;
    private Boolean selectable;
    private DictionaryStatus status;
    private Integer sortOrder;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * 从 ManagedDictionaryItem 构造字典项管理响应。
     *
     * @param result 已限定父字典并完成本地化的字典项结果
     * @return 包含原始多语言内容、排序和版本的视图
     */
    public static @NotNull ManagedDictionaryItemView from(@NotNull ManagedDictionaryItem result) {
        return new ManagedDictionaryItemView()
                .setId(result.getId())
                .setDictionaryId(result.getDictionaryId())
                .setValue(result.getValue())
                .setSource(result.getSource())
                .setLabelKey(result.getLabelKey())
                .setLabels(result.getLabels())
                .setLabel(result.getLabel())
                .setTone(result.getTone())
                .setSelectable(result.isSelectable())
                .setStatus(result.getStatus())
                .setSortOrder(result.getSortOrder())
                .setVersion(result.getVersion())
                .setCreatedAt(result.getCreatedAt())
                .setUpdatedAt(result.getUpdatedAt());
    }
}
