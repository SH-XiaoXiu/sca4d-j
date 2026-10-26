package com.sca4d.core.spring.identity;

import com.sca4d.core.api.authentication.Session;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.identity.Account;
import com.sca4d.core.api.identity.Principal;
import com.sca4d.core.api.identity.Subject;
import com.sca4d.core.spring.web.HttpErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;

import java.lang.reflect.Modifier;
import java.util.Map;

/** 把 Web 选择输入转换成选择器声明的参数类型并调用选择器。 */
public class PrincipalSelectionInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(PrincipalSelectionInvoker.class);

    public PrincipalSelectionInvoker() {
    }

    /**
     * 转换选择输入并调用业务身份选择器。
     *
     * @param selection 当前接口声明的选择器
     * @param session 当前会话；没有时为空
     * @param subject 当前主体；没有时为空
     * @param account 当前账号；没有时为空
     * @param input 当前 HTTP 请求和 Controller 参数形成的选择输入
     * @return 选择出的业务身份；选择器无法确定时为空
     * @throws BusinessException 参数类型是接口或抽象类，或属性绑定失败时抛出，错误码为 BAD_REQUEST；JavaBean 实例化失败按选择器配置错误直接向上传播
     */
    @SuppressWarnings("unchecked")
    public @Nullable Principal select(
            @NotNull PrincipalSelection<?> selection,
            @Nullable Session session,
            @Nullable Subject subject,
            @Nullable Account account,
            @NotNull PrincipalSelectionInput input) {
        PrincipalSelection<Object> typedSelection = (PrincipalSelection<Object>) selection;
        Object parameters = parameters(typedSelection.parameterType(), input);
        return typedSelection.select(session, subject, account, parameters);
    }

    /** 把选择输入保留为 Map，或绑定到具有无参构造方法和可写属性的 JavaBean。 */
    private @NotNull Object parameters(
            @NotNull Class<?> parameterType,
            @NotNull PrincipalSelectionInput input) {
        // Map 选择器直接读取 MVC 已经解析的输入结构，不再创建新的参数对象。
        if (Map.class.equals(parameterType)) {
            LOG.trace("业务身份选择参数保留 Map | propertyCount={}", input.values().size());
            return input.values();
        }

        // 类型化选择器只能声明可实例化的 JavaBean；输入中出现 JavaBean 没有的属性名时，绑定失败并抛出 BAD_REQUEST。
        if (parameterType.isInterface() || Modifier.isAbstract(parameterType.getModifiers())) {
            throw new BusinessException(
                    HttpErrorCode.BAD_REQUEST,
                    new IllegalArgumentException("业务身份选择参数类型必须是可实例化 JavaBean: " + parameterType.getName()));
        }

        // 使用 JavaBean 属性绑定已经由 MVC 解析的值，不读取或修改 HTTP ObjectMapper 配置。
        // 实例化失败是选择器声明的配置错误，不属于请求输入，直接向上传播。
        Object target = BeanUtils.instantiateClass(parameterType);
        DataBinder binder = new DataBinder(target);
        binder.setIgnoreUnknownFields(false);
        try {
            binder.bind(new MutablePropertyValues(input.values()));
        } catch (BeansException exception) {
            throw new BusinessException(HttpErrorCode.BAD_REQUEST, exception);
        }
        // 只报告失败字段名，不把绑定结果中的请求原值带入异常文本。
        if (binder.getBindingResult().hasErrors()) {
            throw new BusinessException(
                    HttpErrorCode.BAD_REQUEST,
                    new IllegalArgumentException(
                            "业务身份选择参数绑定失败: " + parameterType.getName() + " fields="
                                    + binder.getBindingResult()
                                            .getFieldErrors()
                                            .stream()
                                            .map(FieldError::getField)
                                            .toList()));
        }
        LOG.trace(
                "业务身份选择参数已绑定 | parameterType={} propertyCount={}",
                parameterType.getName(),
                input.values().size());
        return target;
    }
}
