package com.sca4d.core.api.authentication;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 使用稳定节点链标识需要认证的业务动作，并保留独立领域类型。 */
public final class AuthenticationActionCode extends TreeCode {

    /** 从代码声明或序列化边界解析编码，拒绝通配符和超过 128 字符的名称。 */
    public AuthenticationActionCode(@NotNull String text) {
        this(TreeCode.of(text));
    }

    /** 从已经校验的稳定编码构造领域名称。 */
    public AuthenticationActionCode(@NotNull Code code) {
        super(code);
        if (hasNode(TreeCode.ofSegment("*")) || format(NameFormat.COLON).length() > 128) {
            throw new IllegalArgumentException("AuthenticationActionCode 必须是最多 128 字符的固定编码");
        }
    }

    @Override
    protected @NotNull AuthenticationActionCode withNodes(@NotNull List<String> nodes) {
        return new AuthenticationActionCode(super.withNodes(nodes));
    }

    @Override
    public @NotNull AuthenticationActionCode child(@NotNull String node) {
        return (AuthenticationActionCode) super.child(node);
    }

    @Override
    public @NotNull AuthenticationActionCode child(@NotNull TreeCode node) {
        return (AuthenticationActionCode) super.child(node);
    }

    @Override
    public @NotNull AuthenticationActionCode append(@NotNull Code code) {
        return (AuthenticationActionCode) super.append(code);
    }
}
