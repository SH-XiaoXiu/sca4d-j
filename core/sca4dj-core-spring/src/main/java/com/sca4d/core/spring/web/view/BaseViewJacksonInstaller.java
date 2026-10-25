package com.sca4d.core.spring.web.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sca4d.core.api.i18n.TextLocalizer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** 在 MVC HandlerAdapter 初始化后把 BaseView 模块安装到实际使用的 Jackson converter。 */
public class BaseViewJacksonInstaller implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(BaseViewJacksonInstaller.class);

    private final RequestMappingHandlerAdapter handlerAdapter;

    private final TextLocalizer localizer;

    /**
     * 创建在 Spring 单例初始化完成后安装 BaseView Jackson 模块的对象。
     *
     * @param handlerAdapter MVC 实际用于处理请求的 RequestMappingHandlerAdapter
     * @param localizer 应用文本本地化入口
     */
    public BaseViewJacksonInstaller(
            @NotNull RequestMappingHandlerAdapter handlerAdapter,
            @NotNull TextLocalizer localizer) {
        this.handlerAdapter = Objects.requireNonNull(handlerAdapter, "RequestMappingHandlerAdapter 不能为空");
        this.localizer = Objects.requireNonNull(localizer, "TextLocalizer 不能为空");
    }

    /** 把模块注册到 HandlerAdapter 当前持有的全部 Jackson mapper，每个实例只注册一次。 */
    @Override
    public void afterSingletonsInstantiated() {
        Set<ObjectMapper> installed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (HttpMessageConverter<?> converter : handlerAdapter.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                ObjectMapper mapper = jackson.getObjectMapper();
                if (installed.add(mapper)) {
                    mapper.registerModule(new BaseViewJacksonModule(localizer));
                }
            }
        }
        LOG.debug("BaseView Jackson 模块已安装 | mapperCount={}", installed.size());
    }
}
