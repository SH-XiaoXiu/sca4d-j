package com.sca4d.core.api.name.code;

import com.sca4d.core.api.name.NameFormat;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** 为稳定 Code 增加层级追加、连续节点查询和前缀匹配；输出格式不参与层级语义。 */
public class TreeCode extends Code {

    /** 使用另一个稳定编码构造专属领域 TreeCode。 */
    protected TreeCode(@NotNull Code code) {
        super(code);
    }

    /** 从已拆分的稳定节点构造编码。 */
    protected TreeCode(@NotNull List<String> nodes) {
        super(nodes);
    }

    /** 解析单节点或一种分隔符组成的编码，接受点、斜杠或冒号；混用、空节点和空白拒绝。 */
    public static @NotNull TreeCode of(@NotNull String text) {
        String value = Objects.requireNonNull(text, "TreeCode 不能为空");
        char separator = '\0';
        for (NameFormat format : NameFormat.values()) {
            if (value.indexOf(format.separator()) >= 0) {
                if (separator != '\0') {
                    throw new IllegalArgumentException("TreeCode 输入不能混用分隔符");
                }
                separator = format.separator();
            }
        }
        return new TreeCode(
                separator == '\0'
                        ? List.of(value)
                        : List.of(value.split(Pattern.quote(String.valueOf(separator)), -1)));
    }

    /** 解析斜杠文本；单节点也允许，输入格式只用于校验。 */
    public static @NotNull TreeCode ofSlash(@NotNull String text) {
        return parseFormat(text, NameFormat.SLASH);
    }

    /** 解析冒号文本；单节点也允许，输入格式只用于校验。 */
    public static @NotNull TreeCode ofColon(@NotNull String text) {
        return parseFormat(text, NameFormat.COLON);
    }

    /** 解析点式文本；单节点也允许，输入格式只用于校验。 */
    public static @NotNull TreeCode ofDot(@NotNull String text) {
        return parseFormat(text, NameFormat.DOT);
    }

    /** 校验并创建一个稳定单节点。 */
    public static @NotNull TreeCode ofSegment(@NotNull String text) {
        TreeCode code = of(text);
        if (code.size() != 1) {
            throw new IllegalArgumentException("稳定单节点不能包含分隔符");
        }
        return code;
    }

    /** 校验文本使用的分隔符，并恢复相同的逻辑节点链。 */
    private static @NotNull TreeCode parseFormat(@NotNull String text, @NotNull NameFormat format) {
        for (NameFormat candidate : NameFormat.values()) {
            if (candidate != format && text.indexOf(candidate.separator()) >= 0) {
                throw new IllegalArgumentException("TreeCode 输入格式不匹配");
            }
        }
        return of(text);
    }

    /** 返回保持当前领域类型的新稳定编码；专属子类覆盖此方法。 */
    protected @NotNull TreeCode withNodes(@NotNull List<String> nodes) {
        return new TreeCode(nodes);
    }

    /** 追加一个代码声明的稳定单节点。 */
    public @NotNull TreeCode child(@NotNull String node) {
        return child(ofSegment(node));
    }

    /** 追加一个已经校验的稳定单节点。 */
    public @NotNull TreeCode child(@NotNull TreeCode node) {
        if (node.size() != 1) {
            throw new IllegalArgumentException("child 只接受一个稳定节点");
        }
        return append(node);
    }

    /** 追加另一段稳定编码，返回保持领域类型的新编码。 */
    public @NotNull TreeCode append(@NotNull Code code) {
        List<String> values = new ArrayList<>(nodes());
        values.addAll(Objects.requireNonNull(code).nodes());
        return withNodes(values);
    }

    /** 判断是否位于给定稳定根编码之下，包含根编码自身。 */
    @Contract(pure = true)
    public boolean isUnder(@NotNull Code root) {
        List<String> values = nodes();
        List<String> prefix = Objects.requireNonNull(root).nodes();
        return values.size() >= prefix.size() && values.subList(0, prefix.size()).equals(prefix);
    }

    /** 判断任意位置是否包含给定的连续稳定节点；通配符在此方法中按字面量比较。 */
    @Contract(pure = true)
    public boolean hasNode(@NotNull Code code) {
        List<String> values = nodes();
        List<String> expected = Objects.requireNonNull(code).nodes();
        for (int offset = 0; offset <= values.size() - expected.size(); offset++) {
            if (values.subList(offset, offset + expected.size()).equals(expected)) {
                return true;
            }
        }
        return false;
    }
}
