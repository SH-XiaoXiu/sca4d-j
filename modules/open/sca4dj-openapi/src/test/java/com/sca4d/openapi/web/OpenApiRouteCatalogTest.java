package com.sca4d.openapi.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.permission.OperationCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.stream.Stream;

/** 验证 OpenAPI 路由目录在完成 Spring 单例初始化后才读取 MVC 映射。 */
class OpenApiRouteCatalogTest {

    @Test
    @DisplayName("Reads MVC Mapping Lazily")
    void readsMvcMappingLazily() {
        ObjectProvider<RequestMappingHandlerMapping> mappings = mock(ObjectProvider.class);
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mappings.getObject()).thenReturn(mapping);
        ObjectProvider<OpenApiRouteRegistrar> registrars = mock(ObjectProvider.class);
        when(registrars.orderedStream()).thenReturn(Stream.empty());

        OpenApiRouteCatalog catalog = new OpenApiRouteCatalog(
                mappings,
                mock(OperationCatalog.class),
                registrars);

        verifyNoInteractions(mappings);
        catalog.initialize();
        verify(mappings).getObject();
    }
}
