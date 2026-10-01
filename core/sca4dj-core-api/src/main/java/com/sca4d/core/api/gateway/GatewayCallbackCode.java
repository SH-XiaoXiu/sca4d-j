package com.sca4d.core.api.gateway;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 使用稳定节点链标识跨实例发现的稳定回调，保留独立领域类型。 */
public final class GatewayCallbackCode extends TreeCode {

    /** 从代码声明或序列化边界解析编码，拒绝通配符和超过 128 字符的名称。 */
    public GatewayCallbackCode(@NotNull String text) {
        this(TreeCode.of(text));
    }

    /** 从已经校验的稳定编码构造领域名称。 */
    public GatewayCallbackCode(@NotNull Code code) {
        super(code);
        if (hasNode(TreeCode.ofSegment("*")) || format(NameFormat.COLON).length() > 128) {
            throw new IllegalArgumentException("GatewayCallbackCode 必须是最多 128 字符的固定编码");
        }
    }

    @Override
    protected @NotNull GatewayCallbackCode withNodes(@NotNull List<String> nodes) {
        return new GatewayCallbackCode(super.withNodes(nodes));
    }

    @Override
    public @NotNull GatewayCallbackCode child(@NotNull String node) {
        return (GatewayCallbackCode) super.child(node);
    }

    @Override
    public @NotNull GatewayCallbackCode child(@NotNull TreeCode node) {
        return (GatewayCallbackCode) super.child(node);
    }

    @Override
    public @NotNull GatewayCallbackCode append(@NotNull Code code) {
        return (GatewayCallbackCode) super.append(code);
    }
}
