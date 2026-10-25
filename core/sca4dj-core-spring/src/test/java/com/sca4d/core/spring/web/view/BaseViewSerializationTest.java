package com.sca4d.core.spring.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.sca4d.core.api.data.exception.DataAssertionException;
import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.LocalizableText;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.i18n.TextLocalizer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 检查 BaseView 最终 JSON 定制、本地化、失败原子性和 MVC mapper 安装行为。 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "unused"})
class BaseViewSerializationTest {

    private static final Locale ZH_CN = Locale.forLanguageTag("zh-CN");

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Keeps Ordinary Bean Json Without Customization")
    void keepsOrdinaryBeanJsonWithoutCustomization() throws Exception {
        JsonNode json = json(mapper(), new PlainView("alpha", 2));

        assertThat(json.toString()).isEqualTo("{\"name\":\"alpha\",\"count\":2}");
    }

    @Test
    @DisplayName("Runs Actions In Registration Order And Render Last")
    void runsActionsInRegistrationOrderAndRenderLast() throws Exception {
        JsonNode json = json(mapper(), new OrderedView());

        assertThat(json.get("trace").asText()).isEqualTo("action-2");
        assertThat(json.get("finalValue").asText()).isEqualTo("render");
        assertThat(json.has("value")).isFalse();
    }

    @Test
    @DisplayName("Customizes Derived Nested Merged And Array Fields")
    void customizesDerivedNestedMergedAndArrayFields() throws Exception {
        JsonNode json = json(mapper(), new StructuralView());

        assertThat(json.get("derived").asText()).isEqualTo("alpha:2");
        assertThat(json.has("secret")).isFalse();
        assertThat(json.has("legacyName")).isFalse();
        assertThat(json.get("displayName").asText()).isEqualTo("Ada");
        assertThat(json.at("/profile/name").asText()).isEqualTo("Ada Lovelace");
        assertThat(json.at("/profile/meta/left").asInt()).isEqualTo(1);
        assertThat(json.at("/profile/meta/right").asInt()).isEqualTo(2);
        assertThat(json.at("/items/0/label").asText()).isEqualTo("one");
        assertThat(json.at("/items/1").asInt()).isEqualTo(2);
        assertThat(json.at("/items/2").isNull()).isTrue();
    }

    @Test
    @DisplayName("Uses Current Mapper Naming And Custom Serialization")
    void usesCurrentMapperNamingAndCustomSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        SimpleModule values = new SimpleModule();
        values.addSerializer(CustomValue.class, new CustomValueSerializer());
        mapper.registerModule(values);
        mapper.registerModule(new BaseViewJacksonModule(localizer()));

        JsonNode json = json(mapper, new MapperAwareView());

        assertThat(json.get("ordinary_name").asText()).isEqualTo("ordinary");
        assertThat(json.get("custom").asText()).isEqualTo("custom:value");
    }

    @Test
    @DisplayName("Preserves Direct Root Changes With Later ViewJson Operations")
    void preservesDirectRootChangesWithLaterViewJsonOperations() throws Exception {
        JsonNode json = json(mapper(), new RootView());

        assertThat(json.get("direct").asText()).isEqualTo("root");
        assertThat(json.at("/nested/value").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Localizes Before Actions And Render")
    void localizesBeforeActionsAndRender() throws Exception {
        LocaleContextHolder.setLocale(ZH_CN);

        JsonNode overridden = json(mapper(), new LocalizedOverrideView("greeting"));
        JsonNode removed = json(mapper(), new LocalizedRemoveView("greeting"));

        assertThat(overridden.has("name")).isFalse();
        assertThat(overridden.get("displayName").asText()).isEqualTo("rendered");
        assertThat(removed.has("name")).isFalse();
    }

    @Test
    @DisplayName("Honors JsonProperty JsonInclude And Hidden Source Method")
    void honorsJsonPropertyJsonIncludeAndHiddenSourceMethod() throws Exception {
        LocaleContextHolder.setLocale(ZH_CN);
        ObjectMapper mapper = mapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

        JsonNode json = json(mapper, new AnnotatedView("greeting"));

        assertThat(json.get("localized_name").asText()).isEqualTo("zh-CN:greeting");
        assertThat(json.has("nameText")).isFalse();
        assertThat(json.has("alwaysNull")).isTrue();
        assertThat(json.get("alwaysNull").isNull()).isTrue();
        assertThat(json.has("ignoredNull")).isFalse();
    }

    @Test
    @DisplayName("Localizes Dynamic Fields Without Exposing Source Data")
    void localizesDynamicFieldsWithoutExposingSourceData() throws Exception {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("en-US"));
        JsonNode json = json(
                mapper(),
                new DynamicLocalizedView(
                        LocalizedFields.of(
                                Map.of(
                                        ZH_CN,
                                        Map.of("name", "默认名称"),
                                        Locale.forLanguageTag("en-US"),
                                        Map.of("name", "English name")))));

        assertThat(json.get("name").asText()).isEqualTo("English name");
        assertThat(json.has("localizedFields")).isFalse();
    }

    @Test
    @DisplayName("Rejects Invalid Localize Metadata")
    void rejectsInvalidLocalizeMetadata() {
        assertThatThrownBy(() -> mapper().writeValueAsString(new NonFinalLocalizedView()))
                .isInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("final String");
        assertThatThrownBy(() -> mapper().writeValueAsString(new InvalidSourceView()))
                .isInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("protected");
        assertThatThrownBy(() -> mapper().writeValueAsString(new DuplicateTargetView()))
                .hasStackTraceContaining("name");
        assertThatThrownBy(() -> mapper().writeValueAsString(new InvalidMixedLocalizeView()))
                .hasStackTraceContaining("必须且只能");
        assertThatThrownBy(() -> mapper().writeValueAsString(new MissingDynamicFieldsView()))
                .hasRootCauseInstanceOf(DataAssertionException.class)
                .hasStackTraceContaining("LocalizedFields");
    }

    @Test
    @DisplayName("Reports Null Source As Data Contract Failure")
    void reportsNullSourceAsDataContractFailure() {
        assertThatThrownBy(() -> mapper().writeValueAsString(new NullSourceView()))
                .hasRootCauseInstanceOf(DataAssertionException.class)
                .hasStackTraceContaining("source 不能返回 null");
    }

    @Test
    @DisplayName("Preserves Runtime Failure From Localize Source")
    void preservesRuntimeFailureFromLocalizeSource() {
        assertThatThrownBy(() -> mapper().writeValueAsString(new ThrowingSourceView()))
                .hasRootCauseInstanceOf(SourceException.class);
    }

    @Test
    @DisplayName("Invalid Json And Action Failures Write No Partial Json")
    void invalidJsonAndActionFailuresWriteNoPartialJson() throws Exception {
        assertNoPartialOutput(new InvalidJsonView(), DataAssertionException.class);
        assertNoPartialOutput(new FailingActionView(), ViewException.class);
    }

    @Test
    @DisplayName("Installer Updates Actual MVC Converter Mapper")
    void installerUpdatesActualMvcConverterMapper() throws Exception {
        LocaleContextHolder.setLocale(ZH_CN);
        ObjectMapper mapper = new ObjectMapper();
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(mapper);
        RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
        adapter.setMessageConverters(List.of(converter));

        new BaseViewJacksonInstaller(adapter, localizer()).afterSingletonsInstantiated();

        JsonNode json = json(mapper, new AnnotatedView("greeting"));
        assertThat(json.get("localized_name").asText()).isEqualTo("zh-CN:greeting");
    }

    /** 创建安装 BaseView 模块的测试 mapper。 */
    private static ObjectMapper mapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new BaseViewJacksonModule(localizer()));
        return mapper;
    }

    /** 把测试 View 序列化并读取为树。 */
    private static JsonNode json(ObjectMapper mapper, BaseView view) throws JsonProcessingException {
        return mapper.readTree(mapper.writeValueAsString(view));
    }

    /** 验证失败发生在实际 generator 写出任何 token 之前。 */
    private static void assertNoPartialOutput(
            BaseView view,
            Class<? extends Throwable> rootCause) throws IOException {
        ObjectMapper mapper = mapper();
        StringWriter output = new StringWriter();
        JsonGenerator generator = mapper.getFactory().createGenerator(output);

        assertThatThrownBy(() -> mapper.writeValue(generator, view))
                .satisfies(exception -> assertThat(hasCause(exception, rootCause)).isTrue());
        generator.flush();
        assertThat(output.toString()).isEmpty();
    }

    /** 检查 Jackson 包装后的完整异常链。 */
    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    /** 创建按 Locale 和消息键返回可观察结果的测试本地化器。 */
    private static TextLocalizer localizer() {
        return new TextLocalizer(
                new LocaleCatalog(List.of(ZH_CN), ZH_CN),
                (key, locale, arguments) -> Optional.of(locale.toLanguageTag() + ":" + key));
    }

    /** 不注册任何 json 动作或 render 的普通视图，用于验证无定制时输出与普通 Bean 相同的字段。 */
    private static class PlainView extends BaseView {

        private final String name;

        private final int count;

        private PlainView(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }

    /** 注册两个 json 动作并在 render 中写入 finalValue；动作按注册顺序执行，render 最后执行并覆盖动作写入的 finalValue。 */
    private static class OrderedView extends BaseView {

        private final String value = "bean";

        private OrderedView() {
            json(value -> value.put("trace", "action-1"));
            json(value -> value.put("trace", "action-2").rename("value", "finalValue"));
        }

        @Override
        protected void render(@NotNull ViewJson json) {
            json.put("finalValue", "render");
        }

        public String getValue() {
            return value;
        }
    }

    /** 通过 json 动作派生、删除、重命名、合并和编辑字段的视图；secret 与 legacyName 是被动作处理的源字段。 */
    private static class StructuralView extends BaseView {

        private final String name = "alpha";

        private final String secret = "hidden";

        private final String legacyName = "Ada";

        private final Map<String, Object> profile;

        private final List<Object> items;

        private StructuralView() {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("left", 1);
            profile = new LinkedHashMap<>();
            profile.put("name", "Ada");
            profile.put("meta", meta);
            items = new ArrayList<>();
            items.add(Map.of("name", "one"));
            items.add(2);
            items.add(null);
            json(
                    value -> value.put("derived", name + ":2")
                            .remove("secret")
                            .rename("legacyName", "displayName")
                            .mergeJson("{\"profile\":{\"meta\":{\"right\":2}}}")
                            .merge(Map.of("profile", Map.of("merged", true)))
                            .opt("optional", null)
                            .put("merged", true));
            json(
                    value -> value.edit("profile", profileJson -> profileJson.put("name", "Ada Lovelace"))
                            .list("items", item -> item.rename("name", "label")));
        }

        public String getName() {
            return name;
        }

        public String getSecret() {
            return secret;
        }

        public String getLegacyName() {
            return legacyName;
        }

        public Map<String, Object> getProfile() {
            return profile;
        }

        public List<Object> getItems() {
            return items;
        }
    }

    /** 注册输出 CustomValue 的 json 动作；ordinaryName 用于验证 mapper 的 SNAKE_CASE 命名策略同样作用于普通字段。 */
    private static class MapperAwareView extends BaseView {

        private final String ordinaryName = "ordinary";

        private MapperAwareView() {
            json(value -> value.put("custom", new CustomValue("value")));
        }

        public String getOrdinaryName() {
            return ordinaryName;
        }
    }

    /** 测试用自定义值，由 CustomValueSerializer 决定最终 JSON 形式。 */
    @SuppressWarnings("ClassCanBeRecord")
    private static class CustomValue {

        private final String value;

        private CustomValue(String value) {
            this.value = value;
        }
    }

    /** 把 CustomValue 写为 custom:{value} 字符串，验证 mapper 中注册的序列化器会被 BaseView 输出使用。 */
    private static class CustomValueSerializer extends StdSerializer<CustomValue> {

        private CustomValueSerializer() {
            super(CustomValue.class);
        }

        @Override
        public void serialize(
                CustomValue value,
                JsonGenerator generator,
                SerializerProvider provider)
                throws IOException {
            generator.writeString("custom:" + value.value);
        }
    }

    /** 在 json 动作中先直接修改根对象，再通过点路径写入嵌套字段；用于验证直接修改的根对象内容在后续 ViewJson 操作后仍被保留。 */
    private static class RootView extends BaseView {

        private final String name = "root";

        private RootView() {
            json(value -> {
                value.root().put("direct", "root");
                value.put("nested.value", 1);
                value.root().withObject("nested").put("direct", true);
                value.put("nested.value", 2);
            });
        }

        public String getName() {
            return name;
        }
    }

    /** 带 @Localize 字段的基类，name 的值由子类实现的 nameText() 返回的 LocalizableText 决定。 */
    private abstract static class LocalizedView extends BaseView {

        private final String key;

        @Localize(source = "nameText")
        private final String name;

        private LocalizedView(String key) {
            this.key = key;
            name = key;
        }

        protected LocalizableText nameText() {
            return MessageText.of(key);
        }

        public String getName() {
            return name;
        }
    }

    /** 在 json 动作中把本地化字段 name 重命名为 displayName，并在 render 中写入 displayName；用于验证动作先于 render 执行。 */
    private static class LocalizedOverrideView extends LocalizedView {

        private LocalizedOverrideView(String key) {
            super(key);
            json(value -> value.rename("name", "displayName"));
        }

        @Override
        protected void render(@NotNull ViewJson json) {
            json.put("displayName", "rendered");
        }
    }

    /** 在 json 动作中删除本地化字段 name，用于验证动作可以移除本地化后的字段。 */
    private static class LocalizedRemoveView extends LocalizedView {

        private LocalizedRemoveView(String key) {
            super(key);
            json(value -> value.remove("name"));
        }
    }

    /** 同时使用 @JsonProperty、@JsonInclude 和 @JsonIgnore 的视图，用于验证注解对输出字段名、null 值和忽略规则的影响。 */
    private static class AnnotatedView extends BaseView {

        private final String key;

        @JsonProperty("localized_name")
        @Localize(source = "nameText")
        private final String name;

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private final String alwaysNull = null;

        @JsonIgnore
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private final String ignoredNull = null;

        private AnnotatedView(String key) {
            this.key = key;
            name = key;
        }

        protected LocalizableText nameText() {
            return MessageText.of(key);
        }

        public String getName() {
            return name;
        }

        @SuppressWarnings("ConstantValue")
        public String getAlwaysNull() {
            return alwaysNull;
        }

        @SuppressWarnings("ConstantValue")
        public String getIgnoredNull() {
            return ignoredNull;
        }
    }

    /** 通过 @Localize(key) 读取构造时传入的 LocalizedFields，输出结果随当前 Locale 变化。 */
    private static class DynamicLocalizedView extends BaseView {

        @Localize(key = "name")
        private final String name = "placeholder";

        private DynamicLocalizedView(LocalizedFields localizedFields) {
            super(localizedFields);
        }

        public String getName() {
            return name;
        }
    }

    /** 同时声明 source 与 key 的非法 @Localize 组合，用于验证序列化时拒绝该声明。 */
    private static class InvalidMixedLocalizeView extends BaseView {

        @Localize(source = "nameText", key = "name")
        private final String name = "placeholder";

        protected LocalizableText nameText() {
            return MessageText.of("name");
        }

        public String getName() {
            return name;
        }
    }

    /** 声明 @Localize(key) 但构造时没有提供 LocalizedFields，用于验证缺少动态字段数据时序列化失败。 */
    private static class MissingDynamicFieldsView extends BaseView {

        @Localize(key = "name")
        private final String name = "placeholder";

        public String getName() {
            return name;
        }
    }

    /** 字段 name 不是 final 的 @Localize 视图，用于验证序列化时拒绝非 final 的本地化字段。 */
    private static class NonFinalLocalizedView extends BaseView {

        @Localize(source = "nameText")
        private String name = "value";

        protected LocalizableText nameText() {
            return MessageText.of("name");
        }

        public String getName() {
            return name;
        }
    }

    /** 用 @Localize 指向的 source 方法 nameText() 声明为 public，用于验证 source 方法不是 protected 时序列化失败。 */
    private static class InvalidSourceView extends BaseView {

        @Localize(source = "nameText")
        private final String name = "value";

        public LocalizableText nameText() {
            return MessageText.of("name");
        }

        public String getName() {
            return name;
        }
    }

    /** 两个字段都映射到 JSON 属性 name，用于验证重复目标属性名时序列化失败。 */
    private static class DuplicateTargetView extends BaseView {

        @JsonProperty("name")
        @Localize(source = "firstText")
        private final String first = "first";

        @JsonProperty("name")
        @Localize(source = "secondText")
        private final String second = "second";

        protected LocalizableText firstText() {
            return MessageText.of("first");
        }

        protected LocalizableText secondText() {
            return MessageText.of("second");
        }

        public String getFirst() {
            return first;
        }

        public String getSecond() {
            return second;
        }
    }

    /** nameText() 返回 null，用于验证本地化源返回 null 时按数据契约失败。 */
    private static class NullSourceView extends BaseView {

        @Localize(source = "nameText")
        private final String name = "placeholder";

        protected LocalizableText nameText() {
            return null;
        }

        public String getName() {
            return name;
        }
    }

    /** nameText() 抛出 SourceException，用于验证本地化源的运行时异常原样传播到序列化调用方。 */
    private static class ThrowingSourceView extends BaseView {

        @Localize(source = "nameText")
        private final String name = "placeholder";

        protected LocalizableText nameText() {
            throw new SourceException();
        }

        public String getName() {
            return name;
        }
    }

    /** json 动作写入非法 JSON 片段 not-json，用于验证合并失败时整个视图不输出部分 JSON。 */
    private static class InvalidJsonView extends BaseView {

        private final String name = "value";

        private InvalidJsonView() {
            json(value -> value.put("before", true).mergeJson("not-json"));
        }

        public String getName() {
            return name;
        }
    }

    /** 第一个 json 动作成功写入 before，第二个动作抛出 ViewException；用于验证后续动作失败时已执行的动作也不输出部分 JSON。 */
    private static class FailingActionView extends BaseView {

        private final String name = "value";

        private FailingActionView() {
            json(value -> value.put("before", true));
            json(value -> {
                throw new ViewException();
            });
        }

        public String getName() {
            return name;
        }
    }

    /** 由 ThrowingSourceView 的 nameText() 抛出的测试运行时异常，用于确认异常来源。 */
    private static class SourceException extends RuntimeException {
    }

    /** 由 FailingActionView 的第二个 json 动作抛出的测试运行时异常。 */
    private static class ViewException extends RuntimeException {
    }
}
