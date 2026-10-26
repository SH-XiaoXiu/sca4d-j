package com.sca4d.core.spring.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.i18n.LocaleCatalog;
import com.sca4d.core.api.i18n.TextLocalizer;
import com.sca4d.core.spring.config.HttpInputValidationProperties;
import com.sca4d.core.spring.validation.NoDefaultValidation;
import com.sca4d.core.spring.web.exception.HttpInputValidationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 验证 HttpInputValidationAspect 对 Controller 参数执行 Bean Validation 与类型默认约束，显式约束会替换默认约束，并在循环对象图中停止递归。 */
@SuppressWarnings("unused") // Controller 方法和请求字段由测试通过反射读取。
class HttpInputValidationAspectTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final HttpInputValidationProperties properties = properties();

    private final HttpInputValidationAspect aspect = new HttpInputValidationAspect(
            validator(),
            localizer(),
            properties);

    @Test
    @DisplayName("Validates Request Body Constraints Without Valid Annotation")
    void validatesRequestBodyConstraintsWithoutValidAnnotation() {
        ExplicitRequest request = new ExplicitRequest();
        request.name = " ";

        assertThatThrownBy(() -> invoke("body", new Class<?>[] {ExplicitRequest.class}, request))
                .isInstanceOf(ConstraintViolationException.class)
                .satisfies(
                        exception -> assertThat(((ConstraintViolationException) exception).getConstraintViolations())
                                .extracting(violation -> violation.getPropertyPath().toString())
                                .containsExactly("name"));
    }

    @Test
    @DisplayName("Applies Type Defaults To Scalar Fields Containers And Elements")
    void appliesTypeDefaultsToScalarFieldsContainersAndElements() {
        DefaultRequest request = new DefaultRequest();
        request.count = 0;
        request.text = "12345";
        request.ids = Arrays.asList(1L, 0L, 3L);

        assertThatThrownBy(() -> invoke("defaults", new Class<?>[] {DefaultRequest.class}, request))
                .isInstanceOf(HttpInputValidationException.class)
                .satisfies(
                        exception -> assertThat(((HttpInputValidationException) exception).violations())
                                .extracting(FieldViolation::getField)
                                .containsExactly("count", "text", "ids", "ids[1]"));
    }

    @Test
    @DisplayName("Lets Explicit Constraints Completely Replace Type Defaults")
    void letsExplicitConstraintsCompletelyReplaceTypeDefaults() throws Throwable {
        OverrideRequest request = new OverrideRequest();
        request.maxOnly = -1;
        request.zero = 0;
        request.text = "12345678";
        request.ids = Arrays.asList(0L, 1L, 2L);
        request.unrestricted = "123456789";

        assertThat(invoke("overrides", new Class<?>[] {OverrideRequest.class}, request)).isEqualTo("ok");
    }

    @Test
    @DisplayName("Validates External Scalar Parameters By Type")
    void validatesExternalScalarParametersByType() {
        assertThatThrownBy(
                () -> invoke(
                        "scalar",
                        new Class<?>[] {int.class, String.class},
                        0,
                        "12345"))
                .isInstanceOf(HttpInputValidationException.class)
                .satisfies(
                        exception -> assertThat(((HttpInputValidationException) exception).violations())
                                .extracting(FieldViolation::getField)
                                .containsExactly("count", "query"));
    }

    @Test
    @DisplayName("Stops Recursive Defaults At Repeated Object Identity")
    void stopsRecursiveDefaultsAtRepeatedObjectIdentity() throws Throwable {
        CyclicRequest request = new CyclicRequest();
        request.value = 1;
        request.next = request;

        assertThat(invoke("cyclic", new Class<?>[] {CyclicRequest.class}, request)).isEqualTo("ok");
    }

    @Test
    @DisplayName("Stops Recursive Defaults At Self Referencing Containers")
    void stopsRecursiveDefaultsAtSelfReferencingContainers() throws Throwable {
        List<Object> list = new SelfReferencingList();
        Object[] array = new Object[1];
        array[0] = array;

        assertThat(invoke("containers", new Class<?>[] {List.class, Object[].class}, list, array))
                .isEqualTo("ok");
    }

    @Test
    @DisplayName("Rejects Non Positive Configured Limits")
    void rejectsNonPositiveConfiguredLimits() {
        assertThatThrownBy(() -> properties.setMaxStringLength(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setMaxContainerSize(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    /** 通过 mock 的 ProceedingJoinPoint 调用 aspect.validate，参数类型由 parameterTypes 指定；校验通过时目标方法返回 "ok"。 */
    private Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) throws Throwable {
        Method method = TestController.class.getDeclaredMethod(methodName, parameterTypes);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(joinPoint.proceed()).thenReturn("ok");
        return aspect.validate(joinPoint);
    }

    /** 创建字符串最大长度为 4、容器最大元素数为 2 的校验配置，使默认约束在小数据量时即可触发。 */
    private static HttpInputValidationProperties properties() {
        HttpInputValidationProperties result = new HttpInputValidationProperties();
        result.setMaxStringLength(4);
        result.setMaxContainerSize(2);
        return result;
    }

    /** 返回共享 ValidatorFactory 创建的 Validator，工厂在 closeValidatorFactory 中关闭。 */
    private static Validator validator() {
        return VALIDATOR_FACTORY.getValidator();
    }

    /** 创建只包含 zh-CN 校验消息的 TextLocalizer，供默认约束的违规信息使用。 */
    private static TextLocalizer localizer() {
        Locale locale = Locale.forLanguageTag("zh-CN");
        Map<String, String> messages = Map.of(
                "validation.positive",
                "必须大于 0",
                "validation.string-size",
                "长度不能超过 {0}",
                "validation.container-size",
                "数量不能超过 {0}");
        return new TextLocalizer(
                new LocaleCatalog(List.of(locale), locale),
                (key, requested, arguments) -> Optional.ofNullable(messages.get(key))
                        .map(pattern -> new MessageFormat(pattern, requested).format(arguments)));
    }

    /** 提供测试使用的无业务 Controller 方法。 */
    @RestController
    private static class TestController {

        public String body(@RequestBody ExplicitRequest request) {
            return "ok";
        }

        public String defaults(@RequestBody DefaultRequest request) {
            return "ok";
        }

        public String overrides(@RequestBody OverrideRequest request) {
            return "ok";
        }

        public String scalar(
                @RequestParam int count,
                @RequestParam(name = "query") String text) {
            return "ok";
        }

        public String cyclic(@RequestBody CyclicRequest request) {
            return "ok";
        }

        public String containers(@RequestBody List<Object> list, @RequestBody Object[] array) {
            return "ok";
        }
    }

    /** 保存只有显式 Bean Validation 约束的请求。 */
    private static class ExplicitRequest {

        @NotBlank
        private String name;
    }

    /** 保存需要全部类型默认规则检查的请求。 */
    private static class DefaultRequest {

        private long count;

        private String text;

        private List<Long> ids = new ArrayList<>();
    }

    /** 保存显式覆盖默认范围及大小的请求。 */
    private static class OverrideRequest {

        @Max(10)
        private long maxOnly;

        @PositiveOrZero
        private long zero;

        @Size(max = 8)
        private String text;

        @Size(max = 3)
        private List<@PositiveOrZero Long> ids = new ArrayList<>();

        @NoDefaultValidation
        private String unrestricted;
    }

    /** 保存用于验证循环对象图终止条件的请求。 */
    private static class CyclicRequest {

        private long value;

        private CyclicRequest next;
    }

    /** 提供只有一个元素且该元素是集合自身的循环输入。 */
    private static class SelfReferencingList extends AbstractList<Object> {

        @Override
        public Object get(int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException(index);
            }
            return this;
        }

        @Override
        public int size() {
            return 1;
        }
    }
}
