package com.sca4d.core.api.name.code;

import com.sca4d.core.api.name.NameFormat;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 表示对象携带的一个固定属性值，例如 {@code INTENT:READ}。
 *
 * <p>属性值位于某条 {@link AttributeAxis} 的轴根之下，可以直接用在业务条件判断中。
 * {@code Attribute} 与节点相同的 {@code TreeCode} 不相等，需要跨类型比较时使用 {@link #sameCode(Code)}。
 */
public class Attribute extends TreeCode {

    /** 从代码声明或序列化边界解析编码，拒绝通配符和超过 128 字符的名称。 */
    public Attribute(@NotNull String text) {
        this(TreeCode.of(text));
    }

    /** 从已经校验的稳定编码构造属性。 */
    public Attribute(@NotNull Code code) {
        super(code);
        if (hasNode(TreeCode.ofSegment("*")) || format(NameFormat.COLON).length() > 128) {
            throw new IllegalArgumentException("Attribute 必须是最多 128 字符的固定编码");
        }
    }

    @Override
    protected @NotNull Attribute withNodes(@NotNull List<String> nodes) {
        return new Attribute(super.withNodes(nodes));
    }

    @Override
    public @NotNull Attribute child(@NotNull String node) {
        return (Attribute) super.child(node);
    }

    @Override
    public @NotNull Attribute child(@NotNull TreeCode node) {
        return (Attribute) super.child(node);
    }

    @Override
    public @NotNull Attribute append(@NotNull Code code) {
        return (Attribute) super.append(code);
    }
}
