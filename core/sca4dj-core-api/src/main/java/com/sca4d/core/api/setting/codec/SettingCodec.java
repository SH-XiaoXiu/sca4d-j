package com.sca4d.core.api.setting.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.sca4d.core.api.codec.VersionedCodec;
import com.sca4d.core.api.data.DataOps;
import com.sca4d.core.api.name.code.TreeCode;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** 负责一个设置值与数据库文本之间的双向转换和校验。 */
public interface SettingCodec<T> extends VersionedCodec<T, String> {

    /**
     * 把已经通过业务校验的设置值转换为数据库文本。
     *
     * @param value 设置值
     * @return 可持久化文本
     * @throws IllegalArgumentException 值不符合当前设置限制时抛出
     */
    @NotNull
    String encode(@NotNull T value);

    /**
     * 把数据库或初始化节点提供的文本转换为设置值。
     *
     * @param value 持久化文本
     * @return 已校验的设置值
     * @throws IllegalArgumentException 文本格式或取值范围不合法时抛出
     */
    @NotNull
    T decode(@NotNull String value);

    /**
     * 把类型化值转换为 HTTP 使用的 JSON 节点。
     *
     * <p>标量 Codec 默认返回包含持久化文本的 JSON 字符串；文档 Codec 可以覆盖当前方法返回对象或数组。
     *
     * @param value 设置值
     * @return 独立 JSON 节点
     */
    default @NotNull JsonNode encodeJson(@NotNull T value) {
        return TextNode.valueOf(encode(value));
    }

    /**
     * 把 HTTP JSON 节点转换为类型化值。
     *
     * <p>标量 Codec 默认只接受 JSON 字符串；文档 Codec 可以覆盖当前方法读取对象或数组。
     *
     * @param value 请求中的 JSON 值
     * @return 已完成格式和取值校验的类型化值
     */
    default @NotNull T decodeJson(@NotNull JsonNode value) {
        JsonNode checked = Objects.requireNonNull(value, "设置 JSON 值不能为空");
        if (!checked.isTextual()) {
            throw new IllegalArgumentException("标量设置必须使用 JSON 字符串");
        }
        return decode(checked.textValue());
    }

    /**
     * 返回管理端选择输入控件时使用的稳定值类型。
     *
     * @return 例如 {@code string}、{@code duration}、{@code integer} 或 {@code long}
     */
    @NotNull
    String presentationType();

    /**
     * 创建限制字符数的普通文本编解码器。
     *
     * @param maximumLength 最大字符数
     * @return 清除首尾空白并拒绝空值的文本编解码器
     */
    static @NotNull SettingCodec<String> text(int maximumLength) {
        if (maximumLength < 1) {
            throw new IllegalArgumentException("设置文本最大长度必须为正数");
        }
        return new SettingCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("SETTING:TEXT");
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public @NotNull Class<String> valueType() {
                return String.class;
            }

            @Override
            public @NotNull String encode(@NotNull String value) {
                String checked = Objects.requireNonNull(value, "设置文本不能为空").trim();
                if (checked.isEmpty() || checked.length() > maximumLength) {
                    throw new IllegalArgumentException("设置文本长度必须在 1 到 " + maximumLength + " 之间");
                }
                return checked;
            }

            @Override
            public @NotNull String decode(@NotNull String value) {
                return encode(value);
            }

            @Override
            public @NotNull String presentationType() {
                return "string";
            }
        };
    }

    /** @return 使用 ISO-8601 文本并只接受正数的 Duration 编解码器 */
    static @NotNull SettingCodec<Duration> positiveDuration() {
        return new SettingCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("SETTING:POSITIVE_DURATION");
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public @NotNull Class<Duration> valueType() {
                return Duration.class;
            }

            @Override
            public @NotNull String encode(@NotNull Duration value) {
                Duration checked = Objects.requireNonNull(value, "设置时长不能为空");
                if (checked.isZero() || checked.isNegative()) {
                    throw new IllegalArgumentException("设置时长必须为正数");
                }
                return checked.toString();
            }

            @Override
            public @NotNull Duration decode(@NotNull String value) {
                try {
                    Duration parsed = Duration.parse(Objects.requireNonNull(value, "设置时长文本不能为空"));
                    encode(parsed);
                    return parsed;
                } catch (DateTimeParseException exception) {
                    throw new IllegalArgumentException("设置时长必须使用 ISO-8601 格式", exception);
                }
            }

            @Override
            public @NotNull String presentationType() {
                return "duration";
            }
        };
    }

    /** @return 只接受正数的 Integer 编解码器 */
    static @NotNull SettingCodec<Integer> positiveInteger() {
        return new SettingCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("SETTING:POSITIVE_INTEGER");
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public @NotNull Class<Integer> valueType() {
                return Integer.class;
            }

            @Override
            public @NotNull String encode(@NotNull Integer value) {
                Integer checked = Objects.requireNonNull(value, "整数设置不能为空");
                if (checked < 1) {
                    throw new IllegalArgumentException("整数设置必须为正数");
                }
                return checked.toString();
            }

            @Override
            public @NotNull Integer decode(@NotNull String value) {
                try {
                    Integer parsed = Integer.valueOf(Objects.requireNonNull(value, "整数设置文本不能为空").trim());
                    encode(parsed);
                    return parsed;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("整数设置格式不正确", exception);
                }
            }

            @Override
            public @NotNull String presentationType() {
                return "integer";
            }
        };
    }

    /** @return 只接受正数的 Long 编解码器 */
    static @NotNull SettingCodec<Long> positiveLong() {
        return new SettingCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("SETTING:POSITIVE_LONG");
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public @NotNull Class<Long> valueType() {
                return Long.class;
            }

            @Override
            public @NotNull String encode(@NotNull Long value) {
                Long checked = Objects.requireNonNull(value, "长整数设置不能为空");
                if (checked < 1L) {
                    throw new IllegalArgumentException("长整数设置必须为正数");
                }
                return checked.toString();
            }

            @Override
            public @NotNull Long decode(@NotNull String value) {
                try {
                    Long parsed = Long.valueOf(Objects.requireNonNull(value, "长整数设置文本不能为空").trim());
                    encode(parsed);
                    return parsed;
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("长整数设置格式不正确", exception);
                }
            }

            @Override
            public @NotNull String presentationType() {
                return "long";
            }
        };
    }

    /**
     * 创建只接受 JSON object 且限制规范编码字节数的文档编解码器。
     *
     * @param maximumBytes UTF-8 规范 JSON 最大字节数
     * @return 返回独立 JsonNode 副本的文档编解码器
     */
    static @NotNull SettingCodec<JsonNode> jsonObject(int maximumBytes) {
        if (maximumBytes < 2) {
            throw new IllegalArgumentException("JSON 设置最大字节数不能小于 2");
        }
        return new SettingCodec<>() {
            @Override
            public @NotNull TreeCode code() {
                return TreeCode.of("SETTING:JSON_OBJECT");
            }

            @Override
            public int version() {
                return 1;
            }

            @Override
            public @NotNull Class<JsonNode> valueType() {
                return JsonNode.class;
            }

            @Override
            public @NotNull String encode(@NotNull JsonNode value) {
                // 先确认根节点是 object 并复制，再按 UTF-8 字节数判断是否超过上限，超限时不生成文本。
                JsonNode checked = requireObject(value);
                String encoded = checked.toString();
                if (encoded.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
                    throw new IllegalArgumentException("JSON 设置超过最大字节数: " + maximumBytes);
                }
                return encoded;
            }

            @Override
            public @NotNull JsonNode decode(@NotNull String value) {
                // 解析结果为空或根节点不是 object 时拒绝，数组和标量不能作为 JSON 设置保存。
                JsonNode node = DataOps.j(Objects.requireNonNull(value, "JSON 设置文本不能为空")).node();
                if (node == null) {
                    throw new IllegalArgumentException("JSON 设置必须是 object");
                }
                return requireObject(node);
            }

            // 两个 Json 方法都先 encode 再 decode，返回值经过与持久化文本相同的 object 校验和字节数限制。
            @Override
            public @NotNull JsonNode encodeJson(@NotNull JsonNode value) {
                return decode(encode(value));
            }

            @Override
            public @NotNull JsonNode decodeJson(@NotNull JsonNode value) {
                return decode(encode(value));
            }

            @Override
            public @NotNull String presentationType() {
                return "object";
            }

            /** 确认节点是 JSON object 并返回独立副本。 */
            private @NotNull JsonNode requireObject(@NotNull JsonNode value) {
                JsonNode checked = Objects.requireNonNull(value, "JSON 设置不能为空");
                if (!checked.isObject()) {
                    throw new IllegalArgumentException("JSON 设置根节点必须是 object");
                }
                return checked.deepCopy();
            }
        };
    }
}
