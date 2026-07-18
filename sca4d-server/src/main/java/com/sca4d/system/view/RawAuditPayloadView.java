package com.sca4d.system.view;

import com.sca4d.common.audit.RawAuditPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * 原始审计载荷视图。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RawAuditPayloadView {

    private Map<String, Object> values;

    /** @return 防御性复制原始审计字段后的管理端视图 */
    public static @NotNull RawAuditPayloadView from(@NotNull RawAuditPayload payload) {
        return new RawAuditPayloadView(Map.copyOf(payload.getValues()));
    }
}
