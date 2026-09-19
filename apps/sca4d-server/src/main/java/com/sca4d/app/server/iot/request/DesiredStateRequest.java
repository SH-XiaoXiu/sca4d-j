package com.sca4d.app.server.iot.request;

import com.sca4d.core.spring.validation.Req;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/** 接收一次 desired 修改中明确设置和明确清除的 property code。 */
@SuppressWarnings("unused")
public final class DesiredStateRequest {

    @Req.NotNull
    @Size(max = 1000)
    private Map<String, Object> set = Map.of();

    @Req.NotNull
    @Size(max = 1000)
    private Set<String> clear = Set.of();

    /** 创建供 Jackson 绑定的空请求。 */
    public DesiredStateRequest() {
    }

    /** @return 本次明确设置的 property 值 */
    public Map<String, Object> getSet() {
        return Map.copyOf(set);
    }

    /** @param set 本次明确设置的 property 值 */
    public void setSet(@Nullable Map<String, Object> set) {
        this.set = set == null ? null : Map.copyOf(set);
    }

    /** @return 本次明确停止维持的 property code */
    public Set<String> getClear() {
        return Set.copyOf(clear);
    }

    /** @param clear 本次明确停止维持的 property code */
    public void setClear(@Nullable Set<String> clear) {
        this.clear = clear == null ? null : Set.copyOf(clear);
    }
}
