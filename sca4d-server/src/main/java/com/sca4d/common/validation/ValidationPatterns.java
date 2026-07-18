package com.sca4d.common.validation;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * 通用校验正则。
 *
 * <p>这里只放跨业务固定复用的格式规则。带业务含义的格式应由业务模块自行定义。
 */
public final class ValidationPatterns {

    /** 业务编码：字母或数字开头，后续允许字母、数字、下划线和中划线。 */
    public static final String BIZ_CODE = "^[A-Za-z0-9][A-Za-z0-9_-]*$";

    /** 点分命名空间编码：小写字母开头，分段允许小写字母、数字，分隔符允许点、下划线和中划线。 */
    public static final String NAMESPACED_CODE = "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$";

    /** 展示名称：不允许控制字符。 */
    public static final String DISPLAY_NAME = "^[^\\p{Cntrl}]+$";

    /** 安全文本：不允许控制字符。 */
    public static final String SAFE_TEXT = "^[^\\p{Cntrl}]*$";

    /** 中国大陆手机号。 */
    public static final String PHONE_NUMBER = "^1[3-9]\\d{9}$";

    /** MAC 地址，使用冒号分隔。 */
    public static final String MAC_ADDRESS = "(?i)^([0-9A-F]{2}:){5}[0-9A-F]{2}$";

    private static final Pattern BIZ_CODE_PATTERN = Pattern.compile(BIZ_CODE);
    private static final Pattern NAMESPACED_CODE_PATTERN = Pattern.compile(NAMESPACED_CODE);

    /**
     * 判断文本是否符合项目共用业务编码格式。
     *
     * @param value 待判断的非空文本
     * @return 符合 {@link #BIZ_CODE} 时返回 {@code true}
     */
    public static boolean isBizCode(@NotNull String value) {
        return BIZ_CODE_PATTERN.matcher(value).matches();
    }

    /**
     * 判断文本是否符合项目共用点分命名空间编码格式。
     *
     * @param value 待判断的非空文本
     * @return 符合 {@link #NAMESPACED_CODE} 时返回 {@code true}
     */
    public static boolean isNamespacedCode(@NotNull String value) {
        return NAMESPACED_CODE_PATTERN.matcher(value).matches();
    }

    private ValidationPatterns() {
    }
}
