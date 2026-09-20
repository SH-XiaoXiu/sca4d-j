package com.sca4d.core.api.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.zip.CRC32;
import java.util.zip.CRC32C;
import java.util.zip.Checksum;

/** 提供不访问外部状态的文本、值、集合、字节、JSON 和标识数据处理。 */
public final class DataOps {

    private static final int DEFAULT_ID_LENGTH = 12;

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 禁止创建无状态工具类。 */
    private DataOps() {
    }

    /**
     * 把输入值转换为文本并开始链式处理。
     *
     * @param value 待处理值；为空时保留空值，否则使用 {@link String#valueOf(Object)} 转换
     * @return 包含转换结果的新文本处理对象
     */
    @CheckReturnValue
    public static @NotNull TextPipe s(@Nullable Object value) {
        return new TextPipe(value == null ? null : String.valueOf(value));
    }

    /**
     * 保留输入值并开始链式转换或断言。
     *
     * @param value 待处理值，可以为空
     * @param <T> 输入值类型
     * @return 包含输入值的新通用值处理对象
     */
    @CheckReturnValue
    public static <T> @NotNull ValuePipe<T> v(@Nullable T value) {
        return new ValuePipe<>(value);
    }

    /**
     * 解析 JSON；null 和空白表示没有 JSON，其他语法错误立即失败。
     *
     * @param json JSON 文本
     * @return 不可变 JSON 处理对象
     */
    @CheckReturnValue
    public static @NotNull JsonPipe j(@Nullable String json) {
        return new JsonPipe(parse(json, null), Set.of());
    }

    /**
     * 解析 JSON 字节；null 和空数组表示没有 JSON，其他语法错误立即失败。
     *
     * @param json JSON 原始字节；方法复制数组后解析
     * @return 不可变 JSON 处理对象
     */
    @CheckReturnValue
    public static @NotNull JsonPipe json(byte @Nullable [] json) {
        return new JsonPipe(parse(json), Set.of());
    }

    /**
     * 复制输入集合并开始链式集合处理。
     *
     * @param values 待处理集合；为空时按空集合处理
     * @param <T> 集合元素类型
     * @return 保持输入顺序的新序列处理对象
     */
    @CheckReturnValue
    public static <T> @NotNull SeqPipe<T> seq(@Nullable Collection<? extends T> values) {
        return new SeqPipe<>(values == null ? List.of() : new ArrayList<>(values));
    }

    /**
     * 复制输入数组并开始带边界检查的字节读取和校验计算。
     *
     * @param values 待读取的非空字节
     * @return 保存当前字节快照的处理对象
     */
    @CheckReturnValue
    public static @NotNull BytePipe bytes(byte @NotNull [] values) {
        return new BytePipe(values);
    }

    /**
     * 创建指定固定长度的字节构建器。
     *
     * @param length 输出字节数，不能为负数
     * @return 初始内容全部为零的字节构建器
     */
    @CheckReturnValue
    public static @NotNull ByteBuilder bytes(int length) {
        return new ByteBuilder(length);
    }

    /**
     * 开始构建一个 JSON 对象。
     *
     * @return 不包含字段的新 JSON 对象处理器
     */
    @CheckReturnValue
    public static @NotNull JsonObjectPipe obj() {
        return new JsonObjectPipe(JsonNodeFactory.instance.objectNode());
    }

    /**
     * 创建只生成随机部分的短 ID 处理器。
     *
     * @return 不带前缀的新短 ID 处理器
     */
    @CheckReturnValue
    public static @NotNull IdPipe id() {
        return new IdPipe(null);
    }

    /**
     * 创建可以在随机部分前拼接固定文本的短 ID 处理器。
     *
     * @param prefix ID 前缀；为空或空白时不添加前缀
     * @return 保存指定前缀的新短 ID 处理器
     */
    @CheckReturnValue
    public static @NotNull IdPipe id(@Nullable String prefix) {
        return new IdPipe(prefix);
    }

    /**
     * 两个 Long 都为空时返回空，否则把空值按零求和。
     *
     * @param left 左侧加数；为空时按零计算
     * @param right 右侧加数；为空时按零计算
     * @return 两个参数都为空时为空，否则返回求和结果
     */
    public static @Nullable Long add(@Nullable Long left, @Nullable Long right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0L : left) + (right == null ? 0L : right);
    }

    /**
     * 判断集合是否没有元素。
     *
     * @param values 待检查集合，可以为空
     * @return 集合为空或没有元素时为 {@code true}
     */
    public static boolean nil(@Nullable Collection<?> values) {
        return values == null || values.isEmpty();
    }

    /**
     * 创建包含阶段编码和可选 JSON detail 的摘要对象。
     *
     * @param stageCode 写入 {@code stageCode} 字段的阶段编码，可以为空
     * @param detailJson 写入 {@code detail} 字段的 JSON；为空或空白时省略该字段
     * @return 至少包含 {@code stageCode} 字段的 JSON 对象文本
     * @throws DataAssertionException detail 不是合法 JSON 时抛出
     */
    public static @NotNull String summary(
            @Nullable String stageCode,
            @Nullable String detailJson) {
        return obj().put("stageCode", stageCode)
                .json("detail", detailJson)
                .req("summary")
                .get();
    }

    /**
     * 保留绝对 HTTP URL，否则去除连接处的多余斜杠后拼接基础 URL 与相对路径。
     *
     * @param baseUrl 相对路径使用的基础 URL；path 已是绝对 HTTP URL 时忽略
     * @param path 绝对 HTTP URL 或待拼接的相对路径
     * @param baseField 基础 URL 为空时写入异常的字段名
     * @param pathField 路径为空时写入异常的字段名
     * @return 原始绝对 HTTP URL，或以一个斜杠连接的 URL
     * @throws DataAssertionException path 为空，或相对 path 对应的 baseUrl 为空时抛出
     */
    public static @NotNull String resourceUrl(
            @Nullable String baseUrl,
            @Nullable String path,
            @NotNull String baseField,
            @NotNull String pathField) {
        String cleanPath = s(path).strip().req(pathField).get();
        if (isAbsoluteHttpUrl(cleanPath)) {
            return cleanPath;
        }
        String cleanBase = s(baseUrl).strip().req(baseField).get();
        cleanBase = cleanBase.replaceFirst("/+$", "");
        cleanPath = cleanPath.replaceFirst("^/+", "");
        return cleanBase + "/" + cleanPath;
    }

    /** 严格解析非空 JSON，并把语法错误转换为数据断言失败。 */
    private static @Nullable JsonNode parse(@Nullable String json, @Nullable String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new DataAssertionException(field, "JSON 语法无效", null, exception);
        }
    }

    /** 严格解析非空 JSON 字节，并把语法错误转换为数据断言失败。 */
    private static @Nullable JsonNode parse(byte @Nullable [] json) {
        if (json == null || json.length == 0) {
            return null;
        }
        try {
            return JSON.readTree(json.clone());
        } catch (IOException exception) {
            throw new DataAssertionException(null, "JSON 语法无效", null, exception);
        }
    }

    /** 把普通值按内部 Jackson 规则转换为独立 JSON 节点。 */
    private static @NotNull JsonNode tree(@Nullable Object value, @Nullable String field) {
        try {
            JsonNode node = JSON.valueToTree(value);
            return node.deepCopy();
        } catch (IllegalArgumentException exception) {
            throw new DataAssertionException(field, "值无法序列化为 JSON", null, exception);
        }
    }

    /** 把 JSON 节点序列化为文本。 */
    private static @NotNull String stringify(@NotNull JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new DataAssertionException(null, "JSON 无法序列化", null, exception);
        }
    }

    /** 把 JSON 节点序列化为字节。 */
    private static byte @NotNull [] stringifyBytes(@NotNull JsonNode node) {
        try {
            return JSON.writeValueAsBytes(node);
        } catch (JsonProcessingException exception) {
            throw new DataAssertionException(null, "JSON 无法序列化", null, exception);
        }
    }

    /** 把 JSON 节点递归复制为不可修改的 Map、List 或标量。 */
    private static @Nullable Object plainValue(@NotNull JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (node instanceof ObjectNode object) {
            return plainObject(object);
        }
        if (node.isArray()) {
            List<Object> result = new ArrayList<>(node.size());
            node.elements().forEachRemaining(value -> result.add(plainValue(value)));
            return Collections.unmodifiableList(result);
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            return node.canConvertToLong() ? node.longValue() : node.bigIntegerValue();
        }
        if (node.isFloatingPointNumber()) {
            return node.decimalValue();
        }
        if (node.isBinary()) {
            try {
                return node.binaryValue();
            } catch (IOException exception) {
                throw new DataAssertionException(null, "JSON binary 值无法读取", null, exception);
            }
        }
        return node.asText();
    }

    /** 把 JSON 对象递归复制为保持字段顺序的不可修改 Map。 */
    private static @NotNull Map<String, Object> plainObject(@NotNull ObjectNode object) {
        Map<String, Object> result = new LinkedHashMap<>();
        object.properties().forEach(entry -> result.put(entry.getKey(), plainValue(entry.getValue())));
        return Collections.unmodifiableMap(result);
    }

    /** 判断字符串是否为绝对 HTTP 或 HTTPS URI。 */
    private static boolean isAbsoluteHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** 不可变文本处理对象。 */
    public static final class TextPipe {

        private final @Nullable String value;

        private TextPipe(@Nullable String value) {
            this.value = value;
        }

        /**
         * 去除当前文本首尾的 Unicode 空白字符。
         *
         * @return 保留空值或包含去除结果的新对象
         */
        public @NotNull TextPipe strip() {
            return new TextPipe(value == null ? null : value.strip());
        }

        /**
         * 使用 {@link Locale#ROOT} 把当前文本转换为小写。
         *
         * @return 保留空值或包含小写文本的新对象
         */
        public @NotNull TextPipe lower() {
            return new TextPipe(value == null ? null : value.toLowerCase(Locale.ROOT));
        }

        /**
         * 使用 {@link Locale#ROOT} 把当前文本转换为大写。
         *
         * @return 保留空值或包含大写文本的新对象
         */
        public @NotNull TextPipe upper() {
            return new TextPipe(value == null ? null : value.toUpperCase(Locale.ROOT));
        }

        /**
         * 从文本开头截取不超过指定 UTF-16 长度的内容。
         *
         * @param maxLength 允许保留的最大长度；非正数产生空文本
         * @return 当前值为空时保留空值，否则返回包含截取结果的新对象
         */
        public @NotNull TextPipe cut(int maxLength) {
            if (maxLength <= 0 || value == null) {
                return new TextPipe(maxLength <= 0 ? "" : null);
            }
            return new TextPipe(value.length() <= maxLength ? value : value.substring(0, maxLength));
        }

        /**
         * 把空白文本转换为空值，非空白文本保持原样。
         *
         * @return 包含转换结果的新对象
         */
        public @NotNull TextPipe nil() {
            return new TextPipe(has() ? value : null);
        }

        /**
         * 在当前文本为空或空白时使用指定替代值。
         *
         * @param defaultValue 当前文本没有内容时使用的值，可以为空
         * @return 包含当前非空白文本或替代值的新对象
         */
        public @NotNull TextPipe or(@Nullable String defaultValue) {
            return new TextPipe(has() ? value : defaultValue);
        }

        /**
         * 要求当前文本非空白。
         *
         * @param field 断言失败时写入异常的字段名
         * @return 当前对象
         * @throws DataAssertionException 当前文本为空或空白时抛出
         */
        public @NotNull TextPipe req(@NotNull String field) {
            if (!has()) {
                throw new DataAssertionException(field, "文本不能为空", null);
            }
            return this;
        }

        /**
         * 判断当前值是否包含至少一个非空白字符。
         *
         * @return 当前值非空且不全是空白时为 {@code true}
         */
        public boolean has() {
            return value != null && !value.isBlank();
        }

        /**
         * 读取用于必填文本输出的值。
         *
         * @return 当前文本；空值转换为空字符串
         */
        public @NotNull String get() {
            return value == null ? "" : value;
        }

        /**
         * 读取可以省略的文本值。
         *
         * @return 非空白文本；空值和空白文本返回空值
         */
        public @Nullable String opt() {
            return has() ? value : null;
        }
    }

    /**
     * 不可变通用值处理对象。
     *
     * @param <T> 当前值类型
     */
    public static final class ValuePipe<T> {

        private final @Nullable T value;

        private ValuePipe(@Nullable T value) {
            this.value = value;
        }

        /**
         * 空值保持为空，非空值才调用映射函数。
         *
         * @param mapper 只接收非空当前值的转换函数
         * @param <R> 转换结果类型
         * @return 包含映射结果的新对象；当前值为空时不调用 mapper
         */
        public <R> @NotNull ValuePipe<R> map(@NotNull Function<? super T, ? extends R> mapper) {
            Objects.requireNonNull(mapper, "值映射函数不能为空");
            return new ValuePipe<>(value == null ? null : mapper.apply(value));
        }

        /**
         * 使用文本转换入口处理当前值。
         *
         * @return 保留空值或包含当前值文本形式的新对象
         */
        public @NotNull TextPipe str() {
            return s(value);
        }

        /**
         * 尝试把数字或文本形式的当前值转换为 BigDecimal。
         *
         * @return 包含转换结果的新对象；空值或无法转换时包含空值
         */
        public @NotNull ValuePipe<BigDecimal> dec() {
            return new ValuePipe<>(decimal(value));
        }

        /**
         * 尝试把数字或文本形式的当前值转换为 Long。
         *
         * @return 包含转换结果的新对象；空值或无法转换时包含空值
         */
        public @NotNull ValuePipe<Long> lng() {
            Long result = null;
            if (value instanceof Number number) {
                result = number.longValue();
            } else if (value != null) {
                try {
                    result = Long.valueOf(String.valueOf(value).strip());
                } catch (NumberFormatException ignored) {
                    // 转换方法通过空值表达无法转换，由 req/min 等断言决定是否失败。
                }
            }
            return new ValuePipe<>(result);
        }

        /**
         * 把布尔值、数字或受支持的英文文本转换为 Boolean。
         *
         * @return 包含转换结果的新对象；空值或无法识别时包含空值
         */
        public @NotNull ValuePipe<Boolean> bool() {
            Boolean result = null;
            if (value instanceof Boolean bool) {
                result = bool;
            } else if (value instanceof Number number) {
                result = number.intValue() != 0;
            } else if (value != null) {
                String text = String.valueOf(value).strip().toLowerCase(Locale.ROOT);
                if (Set.of("true", "yes", "y", "1", "on").contains(text)) {
                    result = true;
                } else if (Set.of("false", "no", "n", "0", "off").contains(text)) {
                    result = false;
                }
            }
            return new ValuePipe<>(result);
        }

        /**
         * 要求当前值非空。
         *
         * @param field 断言失败时写入异常的字段名
         * @return 当前对象
         * @throws DataAssertionException 当前值为空时抛出
         */
        @SuppressWarnings("unused")
        public @NotNull ValuePipe<T> req(@NotNull String field) {
            if (value == null) {
                throw new DataAssertionException(field, "值不能为空", null);
            }
            return this;
        }

        /**
         * 要求当前值非空且满足谓词。
         *
         * @param predicate 只接收非空当前值的检查条件
         * @param field 断言失败时写入异常的字段名
         * @param message 断言失败时使用且不包含敏感值的原因
         * @return 当前对象
         * @throws DataAssertionException 当前值为空或不满足条件时抛出
         */
        @SuppressWarnings("UnusedReturnValue")
        public @NotNull ValuePipe<T> ok(
                @NotNull Predicate<? super T> predicate,
                @NotNull String field,
                @NotNull String message) {
            Objects.requireNonNull(predicate, "值断言函数不能为空");
            if (value == null || !predicate.test(value)) {
                throw new DataAssertionException(field, message, null);
            }
            return this;
        }

        /**
         * 要求当前值可转换为不小于指定下限的 BigDecimal。
         *
         * @param minimum 允许的最小值
         * @param field 断言失败时写入异常的字段名
         * @return 包含 BigDecimal 转换结果的新对象
         * @throws DataAssertionException 当前值无法转换或小于下限时抛出
         */
        public @NotNull ValuePipe<BigDecimal> min(
                @NotNull BigDecimal minimum,
                @NotNull String field) {
            Objects.requireNonNull(minimum, "最小值不能为空");
            BigDecimal current = decimal(value);
            if (current == null || current.compareTo(minimum) < 0) {
                throw new DataAssertionException(field, "值不能小于 " + minimum, null);
            }
            return new ValuePipe<>(current);
        }

        /**
         * 要求当前值属于指定引用类型。
         *
         * @param type 当前值必须属于的引用类型
         * @param field 断言失败时写入异常的字段名
         * @param <R> 检查通过后的值类型
         * @return 包含类型转换结果的新对象
         * @throws DataAssertionException 当前值为空或不属于指定类型时抛出
         */
        public <R> @NotNull ValuePipe<R> as(
                @NotNull Class<R> type,
                @NotNull String field) {
            Objects.requireNonNull(type, "目标类型不能为空");
            if (!type.isInstance(value)) {
                throw new DataAssertionException(field, "值类型必须是 " + type.getSimpleName(), null);
            }
            return new ValuePipe<>(type.cast(value));
        }

        /**
         * 在当前值为空时使用指定替代值。
         *
         * @param defaultValue 当前值为空时返回的值，可以为空
         * @return 当前非空值或替代值
         */
        public @Nullable T or(@Nullable T defaultValue) {
            return value == null ? defaultValue : value;
        }

        /**
         * 读取不执行额外断言或转换的值。
         *
         * @return 当前值，可以为空
         */
        public @Nullable T get() {
            return value;
        }

        /** 安全尝试转换 BigDecimal。 */
        private static @Nullable BigDecimal decimal(@Nullable Object value) {
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                try {
                    return new BigDecimal(number.toString());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            if (value == null) {
                return null;
            }
            try {
                String text = String.valueOf(value).strip();
                return text.isEmpty() ? null : new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    /**
     * 不可变集合处理对象。
     *
     * @param <T> 当前元素类型
     */
    public static final class SeqPipe<T> {

        private final List<T> values;

        @SuppressWarnings("Java9CollectionFactory")
        private SeqPipe(List<T> values) {
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        /**
         * 要求集合至少包含一个元素。
         *
         * @param field 断言失败时写入异常的字段名
         * @return 当前对象
         * @throws DataAssertionException 当前集合没有元素时抛出
         */
        public @NotNull SeqPipe<T> req(@NotNull String field) {
            if (values.isEmpty()) {
                throw new DataAssertionException(field, "集合不能为空", null);
            }
            return this;
        }

        /**
         * 按当前顺序映射全部元素。
         *
         * @param mapper 接收每个元素的转换函数
         * @param <R> 转换后的元素类型
         * @return 包含全部映射结果的新集合处理对象
         */
        public <R> @NotNull SeqPipe<R> map(@NotNull Function<? super T, ? extends R> mapper) {
            Objects.requireNonNull(mapper, "集合映射函数不能为空");
            List<R> mapped = new ArrayList<>(values.size());
            values.forEach(value -> mapped.add(mapper.apply(value)));
            return new SeqPipe<>(mapped);
        }

        /**
         * 按当前顺序保留满足条件的元素。
         *
         * @param predicate 判断元素是否保留的条件
         * @return 包含过滤结果的新集合处理对象
         */
        public @NotNull SeqPipe<T> filter(@NotNull Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate, "集合过滤函数不能为空");
            return new SeqPipe<>(values.stream().filter(predicate).toList());
        }

        /**
         * 读取创建当前对象时复制的元素。
         *
         * @return 保持顺序的不可修改列表
         */
        public @Unmodifiable @NotNull List<T> list() {
            return values;
        }

        /**
         * 计算当前列表包含的元素数量。
         *
         * @return 当前元素数
         */
        public long size() {
            return values.size();
        }
    }

    /** 保存字节快照并按明确偏移读取数值、文本和校验值。 */
    public static final class BytePipe {

        private final byte @NotNull [] values;

        private BytePipe(byte @NotNull [] values) {
            this.values = Objects.requireNonNull(values, "字节数据不能为空").clone();
        }

        /**
         * 返回当前字节数。
         *
         * @return 创建处理对象时复制的字节数量
         */
        public int size() {
            return values.length;
        }

        /**
         * 要求当前数组具有指定长度。
         *
         * @param expected 期望长度，不能为负数
         * @param field 失败时写入异常的字段名
         * @return 当前处理对象
         * @throws IllegalArgumentException expected 为负数时抛出
         * @throws DataAssertionException 当前长度不等于 expected 时抛出
         */
        public @NotNull BytePipe requireSize(int expected, @NotNull String field) {
            if (expected < 0) {
                throw new IllegalArgumentException("字节长度不能为负数");
            }
            if (values.length != expected) {
                throw new DataAssertionException(field, "字节长度必须等于 " + expected, null);
            }
            return this;
        }

        /**
         * 要求当前数组长度位于闭区间内。
         *
         * @param minimum 最小长度，不能为负数
         * @param maximum 最大长度，不能小于 minimum
         * @param field 失败时写入异常的字段名
         * @return 当前处理对象
         * @throws IllegalArgumentException 长度区间无效时抛出
         * @throws DataAssertionException 当前长度不在区间内时抛出
         */
        public @NotNull BytePipe requireSizeBetween(
                int minimum,
                int maximum,
                @NotNull String field) {
            if (minimum < 0 || maximum < minimum) {
                throw new IllegalArgumentException("字节长度区间无效");
            }
            if (values.length < minimum || values.length > maximum) {
                throw new DataAssertionException(
                        field,
                        "字节长度必须位于 " + minimum + " 到 " + maximum + " 之间",
                        null);
            }
            return this;
        }

        /**
         * 读取一个无符号字节。
         *
         * @param offset 从零开始的字节偏移
         * @param field 越界时写入异常的字段名
         * @return 0 到 255 之间的值
         */
        public int unsignedByte(int offset, @NotNull String field) {
            requireRange(values.length, offset, 1, field);
            return Byte.toUnsignedInt(values[offset]);
        }

        /**
         * 按指定字节序读取有符号 short。
         *
         * @param offset 从零开始的首字节偏移
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 两个字节表示的 short
         */
        public short readShort(int offset, @NotNull ByteOrder order, @NotNull String field) {
            return buffer(offset, Short.BYTES, order, field).getShort();
        }

        /**
         * 按指定字节序读取无符号 short。
         *
         * @param offset 从零开始的首字节偏移
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 0 到 65535 之间的值
         */
        public int readUnsignedShort(
                int offset,
                @NotNull ByteOrder order,
                @NotNull String field) {
            return Short.toUnsignedInt(readShort(offset, order, field));
        }

        /**
         * 按指定字节序读取有符号 int。
         *
         * @param offset 从零开始的首字节偏移
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 四个字节表示的 int
         */
        public int readInt(int offset, @NotNull ByteOrder order, @NotNull String field) {
            return buffer(offset, Integer.BYTES, order, field).getInt();
        }

        /**
         * 按指定字节序读取无符号 int。
         *
         * @param offset 从零开始的首字节偏移
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 0 到 4294967295 之间的值
         */
        public long readUnsignedInt(
                int offset,
                @NotNull ByteOrder order,
                @NotNull String field) {
            return Integer.toUnsignedLong(readInt(offset, order, field));
        }

        /**
         * 按指定字节序读取有符号 long。
         *
         * @param offset 从零开始的首字节偏移
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 八个字节表示的 long
         */
        public long readLong(int offset, @NotNull ByteOrder order, @NotNull String field) {
            return buffer(offset, Long.BYTES, order, field).getLong();
        }

        /**
         * 复制指定范围并创建独立字节处理对象。
         *
         * @param offset 从零开始的首字节偏移
         * @param length 复制的字节数
         * @param field 越界时写入异常的字段名
         * @return 只包含指定范围的新处理对象
         */
        public @NotNull BytePipe slice(int offset, int length, @NotNull String field) {
            requireRange(values.length, offset, length, field);
            return new BytePipe(Arrays.copyOfRange(values, offset, offset + length));
        }

        /**
         * 使用严格字符解码器转换全部字节。
         *
         * @param charset 字符集
         * @param field 解码失败时写入异常的字段名
         * @return 解码后的文本处理对象
         * @throws DataAssertionException 字节不能按指定字符集完整解码时抛出
         */
        public @NotNull TextPipe text(@NotNull Charset charset, @NotNull String field) {
            try {
                return s(
                        Objects.requireNonNull(charset, "字符集不能为空")
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(values))
                                .toString());
            } catch (CharacterCodingException exception) {
                throw new DataAssertionException(field, "字节无法按指定字符集解码", null, exception);
            }
        }

        /**
         * 返回小写十六进制文本。
         *
         * @return 每个字节使用两个字符表示的文本
         */
        public @NotNull String hex() {
            return HexFormat.of().formatHex(values);
        }

        /**
         * 计算指定范围全部字节的八位异或值。
         *
         * @param offset 从零开始的首字节偏移
         * @param length 参与计算的字节数
         * @param field 越界时写入异常的字段名
         * @return 0 到 255 之间的异或值
         */
        public int xor8(int offset, int length, @NotNull String field) {
            requireRange(values.length, offset, length, field);
            int result = 0;
            for (int index = offset; index < offset + length; index++) {
                result ^= Byte.toUnsignedInt(values[index]);
            }
            return result;
        }

        /**
         * 计算指定范围全部字节的八位累加和。
         *
         * @param offset 从零开始的首字节偏移
         * @param length 参与计算的字节数
         * @param field 越界时写入异常的字段名
         * @return 0 到 255 之间的累加和
         */
        public int sum8(int offset, int length, @NotNull String field) {
            requireRange(values.length, offset, length, field);
            int result = 0;
            for (int index = offset; index < offset + length; index++) {
                result = (result + Byte.toUnsignedInt(values[index])) & 0xFF;
            }
            return result;
        }

        /**
         * 使用 JDK CRC32 算法计算指定范围。
         *
         * @param offset 从零开始的首字节偏移
         * @param length 参与计算的字节数
         * @param field 越界时写入异常的字段名
         * @return 无符号 32 位 CRC32 值
         */
        public long crc32(int offset, int length, @NotNull String field) {
            return checksum(new CRC32(), offset, length, field);
        }

        /**
         * 使用 JDK CRC32C 算法计算指定范围。
         *
         * @param offset 从零开始的首字节偏移
         * @param length 参与计算的字节数
         * @param field 越界时写入异常的字段名
         * @return 无符号 32 位 CRC32C 值
         */
        public long crc32c(int offset, int length, @NotNull String field) {
            return checksum(new CRC32C(), offset, length, field);
        }

        /**
         * 返回当前字节的防御性副本。
         *
         * @return 当前字节快照的独立副本
         */
        public byte @NotNull [] get() {
            return values.clone();
        }

        /** 创建限制在指定范围和字节序内的只读视图。 */
        private @NotNull ByteBuffer buffer(
                int offset,
                int length,
                ByteOrder order,
                String field) {
            requireRange(values.length, offset, length, field);
            return ByteBuffer.wrap(values, offset, length)
                    .slice()
                    .order(Objects.requireNonNull(order, "字节序不能为空"));
        }

        /** 使用指定 JDK Checksum 计算范围。 */
        private long checksum(Checksum checksum, int offset, int length, String field) {
            requireRange(values.length, offset, length, field);
            checksum.update(values, offset, length);
            return checksum.getValue();
        }
    }

    /** 在固定长度数组中按明确偏移写入数值并生成防御性副本。 */
    public static final class ByteBuilder {

        private final byte @NotNull [] values;

        private ByteBuilder(int length) {
            if (length < 0) {
                throw new IllegalArgumentException("字节长度不能为负数");
            }
            this.values = new byte[length];
        }

        /**
         * 写入一个无符号字节。
         *
         * @param offset 从零开始的写入偏移
         * @param value 0 到 255 之间的值
         * @param field 越界或数值无效时写入异常的字段名
         * @return 当前构建器
         */
        public @NotNull ByteBuilder writeUnsignedByte(
                int offset,
                int value,
                @NotNull String field) {
            requireRange(values.length, offset, 1, field);
            if (value < 0 || value > 0xFF) {
                throw new DataAssertionException(field, "无符号字节必须位于 0 到 255 之间", null);
            }
            values[offset] = (byte) value;
            return this;
        }

        /**
         * 按指定字节序写入 short。
         *
         * @param offset 从零开始的首字节偏移
         * @param value 待写入值
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 当前构建器
         */
        public @NotNull ByteBuilder writeShort(
                int offset,
                short value,
                @NotNull ByteOrder order,
                @NotNull String field) {
            buffer(offset, Short.BYTES, order, field).putShort(value);
            return this;
        }

        /**
         * 按指定字节序写入无符号 short。
         *
         * @param offset 从零开始的首字节偏移
         * @param value 0 到 65535 之间的值
         * @param order 大端或小端字节序
         * @param field 越界或数值无效时写入异常的字段名
         * @return 当前构建器
         */
        public @NotNull ByteBuilder writeUnsignedShort(
                int offset,
                int value,
                @NotNull ByteOrder order,
                @NotNull String field) {
            if (value < 0 || value > 0xFFFF) {
                throw new DataAssertionException(field, "无符号 short 必须位于 0 到 65535 之间", null);
            }
            return writeShort(offset, (short) value, order, field);
        }

        /**
         * 按指定字节序写入 int。
         *
         * @param offset 从零开始的首字节偏移
         * @param value 待写入值
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 当前构建器
         */
        public @NotNull ByteBuilder writeInt(
                int offset,
                int value,
                @NotNull ByteOrder order,
                @NotNull String field) {
            buffer(offset, Integer.BYTES, order, field).putInt(value);
            return this;
        }

        /**
         * 按指定字节序写入 long。
         *
         * @param offset 从零开始的首字节偏移
         * @param value 待写入值
         * @param order 大端或小端字节序
         * @param field 越界时写入异常的字段名
         * @return 当前构建器
         */
        public @NotNull ByteBuilder writeLong(
                int offset,
                long value,
                @NotNull ByteOrder order,
                @NotNull String field) {
            buffer(offset, Long.BYTES, order, field).putLong(value);
            return this;
        }

        /**
         * 计算当前内容指定范围的八位异或值。
         *
         * @param offset 从零开始的首字节偏移
         * @param length 参与计算的字节数
         * @param field 越界时写入异常的字段名
         * @return 0 到 255 之间的异或值
         */
        public int xor8(int offset, int length, @NotNull String field) {
            return new BytePipe(values).xor8(offset, length, field);
        }

        /**
         * 计算当前内容指定范围的八位累加和。
         *
         * @param offset 从零开始的首字节偏移
         * @param length 参与计算的字节数
         * @param field 越界时写入异常的字段名
         * @return 0 到 255 之间的累加和
         */
        public int sum8(int offset, int length, @NotNull String field) {
            return new BytePipe(values).sum8(offset, length, field);
        }

        /**
         * 返回构建结果的防御性副本。
         *
         * @return 当前构建结果的独立副本
         */
        @CheckReturnValue
        public byte @NotNull [] get() {
            return values.clone();
        }

        /** 创建限制在指定范围和字节序内的可写视图。 */
        private @NotNull ByteBuffer buffer(
                int offset,
                int length,
                ByteOrder order,
                String field) {
            requireRange(values.length, offset, length, field);
            return ByteBuffer.wrap(values, offset, length)
                    .slice()
                    .order(Objects.requireNonNull(order, "字节序不能为空"));
        }
    }

    /** 检查 offset 和 length 表示的范围没有负数、整数溢出或越界。 */
    private static void requireRange(int size, int offset, int length, String field) {
        if (offset < 0 || length < 0 || offset > size - length) {
            throw new DataAssertionException(field, "字节范围越界", null);
        }
    }

    /** 不可变 JSON 对象构建器。 */
    public static final class JsonObjectPipe {

        private final ObjectNode value;

        private JsonObjectPipe(ObjectNode value) {
            this.value = value.deepCopy();
        }

        /**
         * 写入按内部 Jackson 规则序列化的字段值。
         *
         * @param key 去除首尾空白后使用的顶层字段名
         * @param fieldValue 待序列化值；空值写为 JSON null
         * @return 包含新字段且不修改当前对象的新处理对象
         * @throws DataAssertionException 字段值无法序列化时抛出
         */
        public @NotNull JsonObjectPipe put(@NotNull String key, @Nullable Object fieldValue) {
            String field = fieldName(key);
            ObjectNode copy = value.deepCopy();
            copy.set(field, tree(fieldValue, field));
            return new JsonObjectPipe(copy);
        }

        /**
         * 仅在字段值非空时写入。
         *
         * @param key 去除首尾空白后使用的顶层字段名
         * @param fieldValue 待序列化值；为空时忽略本次写入
         * @return 字段值为空时返回当前对象，否则返回写入字段的新对象
         * @throws DataAssertionException 字段值无法序列化时抛出
         */
        public @NotNull JsonObjectPipe opt(@NotNull String key, @Nullable Object fieldValue) {
            return fieldValue == null ? this : put(key, fieldValue);
        }

        /**
         * 空白 JSON 忽略，非空 JSON 必须语法正确，可以是任意 JSON 类型。
         *
         * @param key 去除首尾空白后使用的顶层字段名
         * @param json 待解析的 JSON；为空或空白时忽略本次写入
         * @return 没有 JSON 时返回当前对象，否则返回写入解析结果的新对象
         * @throws DataAssertionException JSON 语法无效时抛出
         */
        public @NotNull JsonObjectPipe json(@NotNull String key, @Nullable String json) {
            String field = fieldName(key);
            if (json == null || json.isBlank()) {
                return this;
            }
            ObjectNode copy = value.deepCopy();
            copy.set(field, Objects.requireNonNull(parse(json, field)));
            return new JsonObjectPipe(copy);
        }

        /**
         * 要求对象至少包含一个字段。
         *
         * @param field 断言失败时写入异常的字段名
         * @return 当前对象
         * @throws DataAssertionException 当前对象不包含字段时抛出
         */
        public @NotNull JsonObjectPipe req(@NotNull String field) {
            if (value.isEmpty()) {
                throw new DataAssertionException(field, "JSON 对象不能为空", null);
            }
            return this;
        }

        /**
         * 序列化当前对象。
         *
         * @return JSON 对象文本
         */
        public @NotNull String get() {
            return stringify(value);
        }

        /**
         * 序列化当前对象为独立 JSON 字节。
         *
         * @return 当前 JSON 对象字节的独立副本
         */
        public byte @NotNull [] bytes() {
            return stringifyBytes(value);
        }
    }

    /** 不可变短 ID 生成器。 */
    public static final class IdPipe {

        private final @Nullable String prefix;

        private IdPipe(@Nullable String prefix) {
            this.prefix = prefix;
        }

        /**
         * 生成包含默认长度随机部分的新 ID。
         *
         * @return 12 位随机文本，可选前缀不计入长度
         */
        public @NotNull String get() {
            return get(DEFAULT_ID_LENGTH);
        }

        /**
         * 生成包含指定长度随机部分的新 ID。
         *
         * @param length 随机部分长度，必须为正数
         * @return 指定长度的随机文本，可选前缀不计入长度
         * @throws IllegalArgumentException length 不是正数时抛出
         */
        public @NotNull String get(int length) {
            if (length <= 0) {
                throw new IllegalArgumentException("ID 长度必须为正数");
            }
            StringBuilder id = new StringBuilder(length);
            while (id.length() < length) {
                id.append(UUID.randomUUID().toString().replace("-", ""));
            }
            String result = id.substring(0, length);
            return prefix == null || prefix.isBlank() ? result : prefix + result;
        }
    }

    /** 不可变 JSON 解析和结构编辑对象。 */
    public static final class JsonPipe {

        private final @Nullable JsonNode value;

        private final Set<String> protectedFields;

        private JsonPipe(@Nullable JsonNode value, Set<String> protectedFields) {
            this.value = value == null ? null : value.deepCopy();
            this.protectedFields = Collections.unmodifiableSet(new LinkedHashSet<>(protectedFields));
        }

        /**
         * 要求当前 JSON 为对象。
         *
         * @param field 断言失败时写入异常的字段名
         * @return 当前对象
         * @throws DataAssertionException 当前值为空或不是 JSON 对象时抛出
         */
        public @NotNull JsonPipe req(@NotNull String field) {
            object(field);
            return this;
        }

        /**
         * 读取对象的指定顶层字段并转换为文本处理对象。
         *
         * @param field 去除首尾空白后读取的顶层字段名
         * @return 字段缺失或为 JSON null 时包含空值，否则包含字段文本
         * @throws DataAssertionException 当前值为空或不是 JSON 对象时抛出
         */
        public @NotNull TextPipe text(@NotNull String field) {
            JsonNode node = object(field).get(fieldName(field));
            if (node == null || node.isNull()) {
                return s(null);
            }
            return s(node.isTextual() ? node.textValue() : node.asText());
        }

        /**
         * 读取对象的指定顶层字段并转换为 BigDecimal 处理对象。
         *
         * @param field 去除首尾空白后读取的顶层字段名
         * @return 字段是可转换标量时包含结果，否则包含空值
         * @throws DataAssertionException 当前值为空或不是 JSON 对象时抛出
         */
        public @NotNull ValuePipe<BigDecimal> dec(@NotNull String field) {
            JsonNode node = object(field).get(fieldName(field));
            if (node == null || node.isNull() || !node.isValueNode()) {
                return v(null);
            }
            return v(node.isNumber() ? node.decimalValue() : node.asText()).dec();
        }

        /**
         * 按点分路径读取必须存在的 JSON 对象。
         *
         * @param path 不包含空字段名的点分路径
         * @return 只包含目标对象的新 JSON 处理对象
         * @throws DataAssertionException 路径缺失、值为 null 或不是对象时抛出
         */
        public @NotNull JsonPipe requiredObject(@NotNull String path) {
            JsonNode node = requiredNode(path);
            if (!node.isObject()) {
                throw new DataAssertionException(path, "JSON 字段必须是对象", null);
            }
            return new JsonPipe(node, Set.of());
        }

        /**
         * 按点分路径读取必须存在的非空文本。
         *
         * @param path 不包含空字段名的点分路径
         * @return JSON 中保存的原始文本
         * @throws DataAssertionException 路径缺失、值为 null、不是文本或文本为空白时抛出
         */
        public @NotNull String requiredText(@NotNull String path) {
            JsonNode node = requiredNode(path);
            if (!node.isTextual() || node.textValue().isBlank()) {
                throw new DataAssertionException(path, "JSON 字段必须是非空文本", null);
            }
            return node.textValue();
        }

        /**
         * 按点分路径读取必须存在的布尔值。
         *
         * @param path 不包含空字段名的点分路径
         * @return JSON boolean
         * @throws DataAssertionException 路径缺失、值为 null 或不是 boolean 时抛出
         */
        public @NotNull Boolean requiredBoolean(@NotNull String path) {
            JsonNode node = requiredNode(path);
            if (!node.isBoolean()) {
                throw new DataAssertionException(path, "JSON 字段必须是 boolean", null);
            }
            return node.booleanValue();
        }

        /**
         * 按点分路径读取必须存在且不丢失精度的十进制数。
         *
         * @param path 不包含空字段名的点分路径
         * @return JSON 数值的 BigDecimal 表示
         * @throws DataAssertionException 路径缺失、值为 null 或不是数值时抛出
         */
        public @NotNull BigDecimal requiredDecimal(@NotNull String path) {
            JsonNode node = requiredNode(path);
            if (!node.isNumber()) {
                throw new DataAssertionException(path, "JSON 字段必须是数值", null);
            }
            return node.decimalValue();
        }

        /**
         * 按点分路径读取可以精确表示为 int 的整数。
         *
         * @param path 不包含空字段名的点分路径
         * @return JSON 整数
         * @throws DataAssertionException 路径缺失、值为 null、不是整数或超出 int 范围时抛出
         */
        public int requiredInteger(@NotNull String path) {
            JsonNode node = requiredNode(path);
            if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                throw new DataAssertionException(path, "JSON 字段必须是 int", null);
            }
            return node.intValue();
        }

        /**
         * 按点分路径读取可以精确表示为 long 的整数。
         *
         * @param path 不包含空字段名的点分路径
         * @return JSON 整数
         * @throws DataAssertionException 路径缺失、值为 null、不是整数或超出 long 范围时抛出
         */
        public long requiredLong(@NotNull String path) {
            JsonNode node = requiredNode(path);
            if (!node.isIntegralNumber() || !node.canConvertToLong()) {
                throw new DataAssertionException(path, "JSON 字段必须是 long", null);
            }
            return node.longValue();
        }

        /**
         * 把当前 JSON 对象递归复制为普通 Java 值。
         *
         * @return 保持字段顺序且所有 Map 和 List 都不可修改的对象值
         * @throws DataAssertionException 当前值不是 JSON 对象时抛出
         */
        public @Unmodifiable @NotNull Map<String, Object> objectValues() {
            return plainObject(object(null));
        }

        /**
         * 把当前 JSON 对象映射为指定 Bean；任何映射失败都抛出数据契约异常。
         *
         * @param type Jackson 创建的目标引用类型
         * @param field 断言失败时写入异常的字段名
         * @param <T> 映射结果类型
         * @return 包含映射结果的新通用值处理对象
         * @throws DataAssertionException 当前值不是对象或无法映射为指定类型时抛出
         */
        public <T> @NotNull ValuePipe<T> as(
                @NotNull Class<T> type,
                @NotNull String field) {
            Objects.requireNonNull(type, "JSON 映射类型不能为空");
            ObjectNode object = object(field);
            try {
                return v(JSON.treeToValue(object, type));
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw new DataAssertionException(
                        field,
                        "JSON 无法映射为 " + type.getSimpleName(),
                        null,
                        exception);
            }
        }

        /**
         * 保护指定顶层字段，后续 merge 不得改变已有值。
         *
         * @param fields 需要保护的字段名；空数组、空值和空白元素均忽略
         * @return 包含新增受保护字段且 JSON 内容不变的新对象
         */
        public @NotNull JsonPipe pub(@Nullable String... fields) {
            Set<String> copy = new LinkedHashSet<>(protectedFields);
            if (fields != null) {
                for (String field : fields) {
                    if (field != null && !field.isBlank()) {
                        copy.add(field.strip());
                    }
                }
            }
            return new JsonPipe(value, copy);
        }

        /**
         * 递归合并非空 JSON 对象 patch，其他节点由 patch 覆盖。
         *
         * @param patchJson 待合并对象；为空或空白时不修改当前值
         * @return 没有 patch 时返回当前对象，否则返回合并后的新对象
         * @throws DataAssertionException 当前值或 patch 不是对象、patch 语法无效或受保护字段被修改时抛出
         */
        public @NotNull JsonPipe merge(@Nullable String patchJson) {
            ObjectNode base = object(null);
            if (patchJson == null || patchJson.isBlank()) {
                return this;
            }
            JsonNode patchValue = parse(patchJson, null);
            if (!(patchValue instanceof ObjectNode patch)) {
                throw new DataAssertionException(null, "JSON patch 必须是对象", null);
            }
            for (String field : protectedFields) {
                JsonNode current = base.get(field);
                JsonNode replacement = patch.get(field);
                if (current != null && replacement != null && !current.equals(replacement)) {
                    throw new DataAssertionException(field, "受保护 JSON 字段不能被覆盖", null);
                }
            }
            ObjectNode merged = base.deepCopy();
            mergeObject(merged, patch);
            return new JsonPipe(merged, protectedFields);
        }

        /**
         * 按点分路径写入按内部 Jackson 规则序列化的值。
         *
         * @param path 不包含空字段名的点分路径
         * @param fieldValue 待序列化值；空值写为 JSON null
         * @return 创建缺失中间对象并写入值的新处理对象
         * @throws DataAssertionException 当前值不是对象、路径中间值不是对象或字段值无法序列化时抛出
         */
        public @NotNull JsonPipe put(@NotNull String path, @Nullable Object fieldValue) {
            return putNode(path, tree(fieldValue, path));
        }

        /**
         * 按点分路径写入任意合法 JSON 值。
         *
         * @param path 不包含空字段名的点分路径
         * @param json 待解析并写入的非空白 JSON
         * @return 创建缺失中间对象并写入解析结果的新处理对象
         * @throws DataAssertionException JSON 为空、语法无效、当前值不是对象或路径中间值不是对象时抛出
         */
        public @NotNull JsonPipe putJson(@NotNull String path, @NotNull String json) {
            if (json.isBlank()) {
                throw new DataAssertionException(path, "写入的 JSON 不能为空", null);
            }
            return putNode(path, Objects.requireNonNull(parse(json, path)));
        }

        /**
         * 删除点分路径字段；目标缺失时不修改结果。
         *
         * @param path 不包含空字段名的点分路径
         * @return 字段删除后的新处理对象
         * @throws DataAssertionException 当前值或已有路径中间值不是对象时抛出
         */
        public @NotNull JsonPipe remove(@NotNull String path) {
            String[] parts = path(path);
            ObjectNode copy = object(path).deepCopy();
            ObjectNode parent = parent(copy, parts, false, path);
            if (parent != null) {
                parent.remove(parts[parts.length - 1]);
            }
            return new JsonPipe(copy, protectedFields);
        }

        /**
         * 原子重命名字段；先完成目标写入，再删除来源。
         *
         * @param from 待读取和删除的点分路径
         * @param to 待写入的点分路径
         * @return 路径相同或来源缺失时返回当前对象，否则返回重命名后的新对象
         * @throws IllegalArgumentException 来源和目标路径互为前缀时抛出
         * @throws DataAssertionException 当前值或已有路径中间值不是对象时抛出
         */
        public @NotNull JsonPipe rename(@NotNull String from, @NotNull String to) {
            String[] sourceParts = path(from);
            String[] targetParts = path(to);
            if (List.of(sourceParts).equals(List.of(targetParts))) {
                return this;
            }
            if (prefix(sourceParts, targetParts) || prefix(targetParts, sourceParts)) {
                throw new IllegalArgumentException("JSON 重命名来源和目标路径不能相互包含");
            }
            ObjectNode base = object(from);
            JsonNode source = read(base, sourceParts, from);
            if (source == null) {
                return this;
            }
            ObjectNode copy = base.deepCopy();
            write(copy, targetParts, source.deepCopy(), to);
            ObjectNode sourceParent = parent(copy, sourceParts, false, from);
            if (sourceParent != null) {
                sourceParent.remove(sourceParts[sourceParts.length - 1]);
            }
            return new JsonPipe(copy, protectedFields);
        }

        /**
         * 编辑当前对象或指定点分路径子对象，缺失路径会创建对象。
         *
         * @param path 待编辑对象的点分路径；为空或空白时编辑当前对象
         * @param action 接收独立子对象并返回编辑结果的函数
         * @return action 处理完成且写回指定路径的新对象
         * @throws DataAssertionException 当前值、目标值或编辑结果不是对象时抛出
         */
        public @NotNull JsonPipe edit(
                @Nullable String path,
                @NotNull UnaryOperator<JsonPipe> action) {
            Objects.requireNonNull(action, "JSON 对象编辑函数不能为空");
            ObjectNode base = object(path);
            if (path == null || path.isBlank()) {
                JsonPipe edited = Objects.requireNonNull(action.apply(this), "JSON 对象编辑结果不能为空");
                edited.object(null);
                return edited;
            }
            String[] parts = path(path);
            JsonNode current = read(base, parts, path);
            ObjectNode child;
            if (current == null) {
                child = JsonNodeFactory.instance.objectNode();
            } else if (current instanceof ObjectNode objectNode) {
                child = objectNode;
            } else {
                throw new DataAssertionException(path, "目标 JSON 必须是对象", null);
            }
            JsonPipe edited = Objects.requireNonNull(
                    action.apply(new JsonPipe(child, Set.of())),
                    "JSON 对象编辑结果不能为空");
            ObjectNode result = edited.object(path);
            ObjectNode copy = base.deepCopy();
            write(copy, parts, result.deepCopy(), path);
            return new JsonPipe(copy, protectedFields);
        }

        /**
         * 编辑数组中的对象元素；标量和 null 元素保持原值。
         *
         * @param path 待读取数组的点分路径
         * @param action 依次接收每个对象元素并返回编辑结果的函数
         * @return 路径缺失时返回当前对象，否则返回写回新数组的对象
         * @throws DataAssertionException 当前值不是对象、目标不是数组或编辑结果不是对象时抛出
         */
        public @NotNull JsonPipe list(
                @NotNull String path,
                @NotNull UnaryOperator<JsonPipe> action) {
            Objects.requireNonNull(action, "JSON 数组编辑函数不能为空");
            String[] parts = path(path);
            ObjectNode base = object(path);
            JsonNode current = read(base, parts, path);
            if (current == null) {
                return this;
            }
            if (!(current instanceof ArrayNode array)) {
                throw new DataAssertionException(path, "目标 JSON 必须是数组", null);
            }
            ArrayNode editedArray = array.deepCopy();
            for (int index = 0; index < editedArray.size(); index++) {
                JsonNode item = editedArray.get(index);
                if (!(item instanceof ObjectNode objectItem)) {
                    continue;
                }
                JsonPipe edited = Objects.requireNonNull(
                        action.apply(new JsonPipe(objectItem, Set.of())),
                        "JSON 数组元素编辑结果不能为空");
                editedArray.set(index, edited.object(path).deepCopy());
            }
            ObjectNode copy = base.deepCopy();
            write(copy, parts, editedArray, path);
            return new JsonPipe(copy, protectedFields);
        }

        /**
         * 序列化当前 JSON。
         *
         * @return 当前 JSON 文本；没有 JSON 时返回空值
         */
        public @Nullable String get() {
            return value == null ? null : stringify(value);
        }

        /**
         * 序列化当前 JSON 为独立字节。
         *
         * @return 没有 JSON 时返回空值，否则返回当前 JSON 字节的独立副本
         */
        public byte @Nullable [] bytes() {
            return value == null ? null : stringifyBytes(value);
        }

        /**
         * 仅在没有 JSON 时创建空对象；已有非对象 JSON 直接失败。
         *
         * @return 没有 JSON 时返回包含空对象的新处理对象，否则返回当前对象
         * @throws DataAssertionException 当前值存在但不是 JSON 对象时抛出
         */
        public @NotNull JsonPipe orObj() {
            if (value == null) {
                return new JsonPipe(JsonNodeFactory.instance.objectNode(), protectedFields);
            }
            object(null);
            return this;
        }

        /** 写入节点并返回新处理对象。 */
        private @NotNull JsonPipe putNode(String path, JsonNode node) {
            String[] parts = path(path);
            ObjectNode copy = object(path).deepCopy();
            write(copy, parts, node.deepCopy(), path);
            return new JsonPipe(copy, protectedFields);
        }

        /** 按点分路径读取必须存在且不为 JSON null 的节点。 */
        private @NotNull JsonNode requiredNode(String path) {
            String[] parts = path(path);
            JsonNode node = read(object(path), parts, path);
            if (node == null || node.isNull()) {
                throw new DataAssertionException(path, "JSON 字段不能为空", null);
            }
            return node;
        }

        /** 要求当前值是对象。 */
        private @NotNull ObjectNode object(@Nullable String field) {
            if (!(value instanceof ObjectNode object)) {
                throw new DataAssertionException(field, "JSON 必须是对象", null);
            }
            return object;
        }

        /** 读取路径；缺失返回 null，已有错误中间类型直接失败。 */
        private static @Nullable JsonNode read(
                ObjectNode root,
                String[] parts,
                String field) {
            JsonNode current = root;
            for (String part : parts) {
                if (!(current instanceof ObjectNode object)) {
                    throw new DataAssertionException(field, "JSON 路径中间节点必须是对象", null);
                }
                current = object.get(part);
                if (current == null) {
                    return null;
                }
            }
            return current;
        }

        /** 找到字段父对象；缺失时按 create 决定创建或返回 null。 */
        private static @Nullable ObjectNode parent(
                ObjectNode root,
                String[] parts,
                boolean create,
                String field) {
            ObjectNode current = root;
            for (int index = 0; index < parts.length - 1; index++) {
                JsonNode next = current.get(parts[index]);
                if (next == null) {
                    if (!create) {
                        return null;
                    }
                    ObjectNode child = JsonNodeFactory.instance.objectNode();
                    current.set(parts[index], child);
                    current = child;
                    continue;
                }
                if (!(next instanceof ObjectNode child)) {
                    throw new DataAssertionException(field, "JSON 路径中间节点必须是对象", null);
                }
                current = child;
            }
            return current;
        }

        /** 写入字段，允许创建缺失中间对象但不覆盖错误类型。 */
        private static void write(
                ObjectNode root,
                String[] parts,
                JsonNode node,
                String field) {
            ObjectNode parent = Objects.requireNonNull(parent(root, parts, true, field));
            parent.set(parts[parts.length - 1], node);
        }

        /** 递归合并对象。 */
        private static void mergeObject(ObjectNode target, ObjectNode patch) {
            patch.properties().forEach(entry -> {
                JsonNode current = target.get(entry.getKey());
                JsonNode replacement = entry.getValue();
                if (current instanceof ObjectNode currentObject
                        && replacement instanceof ObjectNode replacementObject) {
                    mergeObject(currentObject, replacementObject);
                } else {
                    target.set(entry.getKey(), replacement.deepCopy());
                }
            });
        }
    }

    /** 校验 JSON 字段名非空。 */
    private static @NotNull String fieldName(@NotNull String field) {
        Objects.requireNonNull(field, "JSON 字段名不能为空");
        String clean = field.strip();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("JSON 字段名不能为空");
        }
        return clean;
    }

    /** 解析并校验点分路径的每个字段名。 */
    private static @NotNull String[] path(@NotNull String path) {
        Objects.requireNonNull(path, "JSON 路径不能为空");
        String[] parts = path.split("\\.", -1);
        if (parts.length == 0) {
            throw new IllegalArgumentException("JSON 路径不能为空");
        }
        for (int index = 0; index < parts.length; index++) {
            parts[index] = parts[index].strip();
            if (parts[index].isEmpty()) {
                throw new IllegalArgumentException("JSON 路径字段名不能为空: " + path);
            }
        }
        return parts;
    }

    /** 判断一个完整路径是否是另一路径的真前缀。 */
    private static boolean prefix(String[] candidate, String[] path) {
        if (candidate.length >= path.length) {
            return false;
        }
        for (int index = 0; index < candidate.length; index++) {
            if (!candidate[index].equals(path[index])) {
                return false;
            }
        }
        return true;
    }
}
