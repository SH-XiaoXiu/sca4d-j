package com.sca4d.system.request;

import com.sca4d.common.validation.BizId;
import com.sca4d.common.validation.BizIdMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 批量读取字典共用选项的查询参数。 */
public class DictionaryOptionsRequest {

    @NotEmpty(message = "{validation.dictionary.codes-required}")
    @Size(max = 50, message = "{validation.dictionary.codes-limit}")
    private List<@BizId(mode = BizIdMode.NAMESPACED_CODE, max = 64) String> codes;

    /** @return 需要批量读取的固定字典类型编码 */
    public @NotNull List<String> getCodes() {
        return codes == null ? List.of() : List.copyOf(codes);
    }

    /** @param codes 需要批量读取的固定字典类型编码 */
    public void setCodes(@NotNull List<String> codes) {
        this.codes = List.copyOf(codes);
    }
}
