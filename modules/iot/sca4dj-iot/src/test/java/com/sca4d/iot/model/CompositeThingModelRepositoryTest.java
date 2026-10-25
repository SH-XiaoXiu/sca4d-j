package com.sca4d.iot.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.persistence.DatabaseThingModelRepository;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import com.sca4d.iot.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 验证静态定义和数据库中的属性定义按 Product code 进入唯一 Repository。 */
class CompositeThingModelRepositoryTest {

    @Test
    @DisplayName("代码 Product 只读取静态物模型并拒绝运行时发布")
    void routesCodeProductToStaticModels() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.find("mqtt-debug")).thenReturn(Optional.empty());
        ProductDefinition definition = new ProductDefinition(
                "mqtt-debug",
                MessageText.of("product.mqtt-debug.display-name"),
                Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")),
                Set.of("mqtt-debug-v1"));
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(products, List.of(definition));
        ThingModel model = ThingModel.builder("mqtt-debug", 1).build();
        ThingModelPresentation presentation = ThingModelPresentation.messages(
                model,
                Map.of("/displayName", MessageText.of("thing-model.mqtt-debug.display-name")));
        DatabaseThingModelRepository databaseModels = mock(DatabaseThingModelRepository.class);
        CompositeThingModelRepository repository = new CompositeThingModelRepository(
                catalog,
                new StaticThingModelRepository(List.of(), List.of(presentation)),
                databaseModels);

        assertThat(repository.find("mqtt-debug", 1)).containsSame(presentation);
        assertThat(repository.findPublished("mqtt-debug")).containsExactly(presentation);
        assertThatThrownBy(() -> repository.publish(model, i18n("MQTT 调试")))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(databaseModels, never()).find("mqtt-debug", 1);
    }

    @Test
    @DisplayName("未被代码目录占用的 Product code 使用数据库物模型")
    void routesDatabaseProductToDatabaseModels() {
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(
                mock(ProductRepository.class),
                List.of());
        ThingModel model = ThingModel.builder("gateway", 1).build();
        ThingModelPresentation presentation = ThingModelPresentation.translated(
                model,
                i18n("网关"));
        DatabaseThingModelRepository databaseModels = mock(DatabaseThingModelRepository.class);
        when(databaseModels.find("gateway", 1)).thenReturn(Optional.of(presentation));
        CompositeThingModelRepository repository = new CompositeThingModelRepository(
                catalog,
                new StaticThingModelRepository(List.of()),
                databaseModels);

        assertThat(repository.find("gateway", 1)).containsSame(presentation);

        verify(databaseModels).find("gateway", 1);
    }

    /** 创建只包含属性名称的简体中文动态翻译。 */
    private static LocalizedFields i18n(String displayName) {
        return LocalizedFields.of(
                Map.of(
                        Locale.SIMPLIFIED_CHINESE,
                        Map.of("/displayName", displayName)));
    }
}
