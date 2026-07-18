package com.sca4d.system.view;

import com.sca4d.common.dictionary.DictionarySource;
import com.sca4d.system.DictionaryKind;
import com.sca4d.system.DictionaryStatus;
import com.sca4d.system.dictionary.ManagedDictionary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/** 包含字典管理、排序和 409 恢复所需完整数据的类型视图。 */
@Data
@Accessors(chain = true)
@Schema(description = "字典类型管理视图")
public class ManagedDictionaryView {

    private Long id;
    private Long tenantId;
    private String code;
    private DictionaryKind kind;
    private DictionarySource source;
    private String labelKey;
    private Map<String, String> labels;
    private String label;
    private DictionaryStatus status;
    private Integer sortOrder;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /**
     * 从 ManagedDictionary 构造字典类型管理响应。
     *
     * @param result 已限定管理范围并完成本地化的字典结果
     * @return 包含原始多语言内容、排序和版本的视图
     */
    public static @NotNull ManagedDictionaryView from(@NotNull ManagedDictionary result) {
        return new ManagedDictionaryView()
                .setId(result.getId())
                .setTenantId(result.getTenantId())
                .setCode(result.getCode())
                .setKind(result.getKind())
                .setSource(result.getSource())
                .setLabelKey(result.getLabelKey())
                .setLabels(result.getLabels())
                .setLabel(result.getLabel())
                .setStatus(result.getStatus())
                .setSortOrder(result.getSortOrder())
                .setVersion(result.getVersion())
                .setCreatedAt(result.getCreatedAt())
                .setUpdatedAt(result.getUpdatedAt());
    }
}
