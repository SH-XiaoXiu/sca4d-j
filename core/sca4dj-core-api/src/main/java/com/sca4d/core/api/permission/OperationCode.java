package com.sca4d.core.api.permission;

import com.sca4d.core.api.name.NameFormat;
import com.sca4d.core.api.name.code.Code;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 表示一个 {@link Operation} 的固定编码，例如 {@code ORDERS:INVOICES:READ}。
 *
 * <p>编码至少有两个节点，总长度不超过 128 字符。最后一个节点是动词（{@link Action}），前面的节点是它所属的分组路径，
 * 路径深度不限。通配符节点只能出现在最后一段，用于表示某个分组下的全部访问入口，
 * 例如 {@code CATALOG:ACCESS:*}；其他位置出现 {@code *} 时拒绝。
 * 与节点相同的 {@code TreeCode} 不相等，需要跨类型比较时使用 {@link #sameCode(Code)}。
 */
public class OperationCode extends TreeCode {

    /**
     * 解析冒号分隔的操作键文本。
     *
     * @param text 例如 {@code ORDERS:INVOICES:READ}
     * @throws IllegalArgumentException 文本不是冒号编码、少于两个节点、超过 128 字符，或通配节点不在最后时抛出
     */
    public OperationCode(@NotNull String text) {
        this(TreeCode.ofColon(text));
    }

    /**
     * 从已经校验的稳定编码构造操作编码。
     *
     * @throws IllegalArgumentException 少于两个节点、超过 128 字符，或通配节点不在最后时抛出
     */
    public OperationCode(@NotNull Code code) {
        super(code);
        List<String> nodes = nodes();
        if (nodes.size() < 2) {
            throw new IllegalArgumentException("OperationCode 至少需要两个节点: " + this);
        }
        if (nodes.subList(0, nodes.size() - 1).contains("*")) {
            throw new IllegalArgumentException("OperationCode 的通配节点只能是最后一个节点: " + this);
        }
        if (format(NameFormat.COLON).length() > 128) {
            throw new IllegalArgumentException("OperationCode 不能超过 128 字符");
        }
    }

    /**
     * @return 最后一个节点，例如 {@code ORDERS:INVOICES:READ} 返回 {@code READ}；通配编码返回 {@code *}
     */
    @Contract(pure = true)
    public @NotNull TreeCode verb() {
        return TreeCode.ofSegment(nodes().getLast());
    }

    /**
     * @return 最后一个节点是通配符 {@code *} 时为 {@code true}
     */
    @Contract(pure = true)
    public boolean isWildcard() {
        return "*".equals(nodes().getLast());
    }

    @Override
    protected @NotNull OperationCode withNodes(@NotNull List<String> nodes) {
        return new OperationCode(super.withNodes(nodes));
    }

    @Override
    public @NotNull OperationCode child(@NotNull String node) {
        return (OperationCode) super.child(node);
    }

    @Override
    public @NotNull OperationCode child(@NotNull TreeCode node) {
        return (OperationCode) super.child(node);
    }

    @Override
    public @NotNull OperationCode append(@NotNull Code code) {
        return (OperationCode) super.append(code);
    }
}
