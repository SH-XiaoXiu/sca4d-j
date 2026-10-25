package com.sca4d.iot.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.i18n.LocalizedFields;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.Product;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.ProductSelector;
import com.sca4d.iot.api.ProductSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 验证数据库和代码 Product 解析、绑定结果及 code 冲突规则。 */
class ProductDefinitionCatalogTest {

    private static final ProductDefinition CODE_PRODUCT = new ProductDefinition(
            "mqtt-debug",
            MessageText.of("product.mqtt-debug.display-name"),
            Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")),
            Set.of("mqtt-debug-v1"));

    @Test
    @DisplayName("代码 Product 解析为不带数据库标识的稳定绑定")
    void resolvesCodeProduct() {
        ProductRepository database = mock(ProductRepository.class);
        when(database.find("mqtt-debug")).thenReturn(Optional.empty());
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(database, List.of(CODE_PRODUCT));

        ProductResolution resolution = catalog.resolve(ProductSelector.code("mqtt-debug"));

        assertThat(resolution.binding().source()).isEqualTo(ProductSource.CODE);
        assertThat(resolution.binding().code()).isEqualTo("mqtt-debug");
        assertThat(resolution.binding().databaseProductId()).isEmpty();
        assertThat(resolution.definition()).isSameAs(CODE_PRODUCT);
    }

    @Test
    @DisplayName("数据库 Product 创建解析会读取记录并保存标识与 code")
    void resolvesDatabaseProductForCreation() {
        ProductRepository database = mock(ProductRepository.class);
        Product product = new Product(
                41,
                "gateway",
                i18n("网关"),
                Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")),
                Set.of("gateway-v1"));
        when(database.find(41)).thenReturn(Optional.of(product));
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(database, List.of(CODE_PRODUCT));

        ProductResolution resolution = catalog.resolve(ProductSelector.database(41));

        assertThat(resolution.binding().source()).isEqualTo(ProductSource.DATABASE);
        assertThat(resolution.binding().code()).isEqualTo("gateway");
        assertThat(resolution.binding().databaseProductId()).hasValue(41);
        assertThat(resolution.definition().protocolCodes()).containsExactly("gateway-v1");
        verify(database).find(41);
    }

    @Test
    @DisplayName("数据库 Product code 与代码目录冲突时拒绝创建和解析")
    void rejectsCodeCollisionsAcrossSources() {
        ProductRepository database = mock(ProductRepository.class);
        Product conflicting = new Product(
                42,
                "mqtt-debug",
                i18n("冲突 Product"),
                Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")),
                Set.of("gateway-v1"));
        when(database.find(42)).thenReturn(Optional.of(conflicting));
        when(database.find("mqtt-debug")).thenReturn(Optional.of(conflicting));
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(database, List.of(CODE_PRODUCT));

        assertThatThrownBy(() -> catalog.resolve(ProductSelector.database(42)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(IotErrorCode.IDENTITY_CONFLICT));
        assertThatThrownBy(() -> catalog.resolve(ProductSelector.code("mqtt-debug")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(IotErrorCode.IDENTITY_CONFLICT));
    }

    @Test
    @DisplayName("数据库 Product 绑定的 code 与当前记录不一致时拒绝使用")
    void rejectsStaleDatabaseBindingCode() {
        ProductRepository database = mock(ProductRepository.class);
        Product product = new Product(
                43,
                "gateway",
                i18n("网关"),
                Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")),
                Set.of("gateway-v1"));
        when(database.find(43)).thenReturn(Optional.of(product));
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(database, List.of(CODE_PRODUCT));

        assertThatThrownBy(() -> catalog.definition(ProductBinding.database(43, "stale-code")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(IotErrorCode.STATE_CONFLICT));
    }

    @Test
    @DisplayName("按 code 查询并按 code 列出数据库和代码 Product")
    void findsProductsAcrossSources() {
        ProductRepository database = mock(ProductRepository.class);
        Product gateway = new Product(
                44,
                "gateway",
                i18n("网关"),
                Set.of(DeviceAccessMethod.parse("ACCOUNT:AUTHENTICATION/SHARED_SECRET")),
                Set.of("gateway-v1"));
        when(database.find("gateway")).thenReturn(Optional.of(gateway));
        when(database.find("mqtt-debug")).thenReturn(Optional.empty());
        when(database.findAll()).thenReturn(List.of(gateway));
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(database, List.of(CODE_PRODUCT));

        assertThat(catalog.find("gateway")).get()
                .extracting(value -> value.binding().source())
                .isEqualTo(ProductSource.DATABASE);
        assertThat(catalog.find("mqtt-debug")).get()
                .extracting(value -> value.binding().source())
                .isEqualTo(ProductSource.CODE);
        assertThat(catalog.findAll())
                .extracting(value -> value.binding().code())
                .containsExactly("gateway", "mqtt-debug");
    }

    @Test
    @DisplayName("列出全部 Product 时拒绝数据库和代码目录存在重复 code")
    void rejectsCatalogCollision() {
        ProductRepository database = mock(ProductRepository.class);
        Product conflicting = new Product(
                45,
                "mqtt-debug",
                i18n("冲突 Product"),
                Set.of(),
                Set.of("mqtt-debug-v1"));
        when(database.findAll()).thenReturn(List.of(conflicting));
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(database, List.of(CODE_PRODUCT));

        assertThatThrownBy(catalog::findAll)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(IotErrorCode.IDENTITY_CONFLICT));
    }

    @Test
    @DisplayName("双来源目录按稳定 code 精确归并数据库窗口")
    void mergesARequestedPageWithoutLoadingAllDatabaseProducts() {
        ProductRepository database = mock(ProductRepository.class);
        Product alpha = product(51, "alpha");
        Product charlie = product(52, "charlie");
        Product echo = product(53, "echo");
        ProductDefinition bravo = new ProductDefinition(
                "bravo",
                MessageText.of("product.bravo.display-name"),
                Set.of(),
                Set.of("test-v1"));
        ProductDefinition delta = new ProductDefinition(
                "delta",
                MessageText.of("product.delta.display-name"),
                Set.of(),
                Set.of("test-v1"));
        when(database.count()).thenReturn(3L);
        when(database.findCodeWindow(0, 4)).thenReturn(List.of(alpha, charlie, echo));
        ProductDefinitionCatalog catalog = new ProductDefinitionCatalog(database, List.of(bravo, delta));

        Page<ProductResolution> page = catalog.findPage(new PageRequest(2, 2));

        assertThat(page.getItems())
                .extracting(value -> value.binding().code())
                .containsExactly("charlie", "delta");
        assertThat(page.getTotal()).isEqualTo(5);
        verify(database).findCodeWindow(0, 4);
    }

    /** 创建只包含展示字段的测试数据库 Product。 */
    private static Product product(long id, String code) {
        return new Product(id, code, i18n(code), Set.of(), Set.of("test-v1"));
    }

    /** 创建测试 Product 的简体中文展示字段。 */
    private static LocalizedFields i18n(String displayName) {
        return LocalizedFields.of(
                Map.of(
                        Locale.SIMPLIFIED_CHINESE,
                        Map.of("displayName", displayName)));
    }
}
