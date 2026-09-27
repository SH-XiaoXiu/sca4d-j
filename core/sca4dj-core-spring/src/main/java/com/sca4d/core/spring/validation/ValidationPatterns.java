package com.sca4d.core.spring.validation;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/** 保存跨业务固定复用的文本格式规则。 */
@SuppressWarnings("unused")
public class ValidationPatterns {

    /** 业务编码：字母或数字开头，后续允许字母、数字、下划线和中划线。 */
    public static final String BIZ_CODE = "^[A-Za-z0-9][A-Za-z0-9_-]*$";

    /** 点分命名空间编码：小写字母开头，分段允许小写字母、数字、点、下划线和中划线。 */
    public static final String NAMESPACED_CODE = "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$";

    /** 展示名称：不允许控制字符。 */
    public static final String DISPLAY_NAME = "^[^\\p{Cntrl}]+$";

    /** 普通安全文本：不允许控制字符。 */
    public static final String SAFE_TEXT = "^[^\\p{Cntrl}]*$";

    /** MAC 地址：使用冒号分隔六组十六进制字节。 */
    public static final String MAC_ADDRESS = "(?i)^([0-9A-F]{2}:){5}[0-9A-F]{2}$";

    private static final Pattern BIZ_CODE_PATTERN = Pattern.compile(BIZ_CODE);

    private static final Pattern NAMESPACED_CODE_PATTERN = Pattern.compile(NAMESPACED_CODE);

    /** 禁止创建只保存格式规则的对象。 */
    private ValidationPatterns() {
    }

    /**
     * 检查完整文本是否符合业务编码格式。
     *
     * @param value 待判断文本
     * @return 符合 BIZ_CODE 时为 {@code true}
     */
    public static boolean isBizCode(@NotNull String value) {
        return BIZ_CODE_PATTERN.matcher(value).matches();
    }

    /**
     * 检查完整文本是否符合点分命名空间编码格式。
     *
     * @param value 待判断文本
     * @return 符合 NAMESPACED_CODE 时为 {@code true}
     */
    public static boolean isNamespacedCode(@NotNull String value) {
        return NAMESPACED_CODE_PATTERN.matcher(value).matches();
    }
}
