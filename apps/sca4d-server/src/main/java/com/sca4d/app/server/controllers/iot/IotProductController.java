package com.sca4d.app.server.controllers.iot;

import com.sca4d.app.server.iot.IotOperations;
import com.sca4d.app.server.iot.request.IotI18nRequest;
import com.sca4d.app.server.iot.request.IotProductChangeRequest;
import com.sca4d.app.server.iot.request.IotProductRequest;
import com.sca4d.app.server.iot.request.IotStatusRequest;
import com.sca4d.app.server.iot.request.IotThingModelRequest;
import com.sca4d.app.server.iot.view.IotProductCatalogView;
import com.sca4d.app.server.iot.view.IotProductI18nView;
import com.sca4d.app.server.iot.view.IotProductView;
import com.sca4d.app.server.iot.view.IotThingModelI18nView;
import com.sca4d.app.server.iot.view.IotThingModelView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.IotApplicationService;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.product.ProductChange;
import com.sca4d.iot.product.ProductRegistration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 提供 Sca4d Server 的 IoT Product 管理和整份物模型 JSON 发布接口。 */
@RestController
@ConditionalOnProperty(prefix = "app.iot.capabilities", name = "api", havingValue = "true", matchIfMissing = true)
@RequestMapping("/iot/products")
@Tag(name = "IoT Products")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class IotProductController {

    private final @NotNull IotApplicationService iot;

    /** @param iot Sca4d Server 的 IoT 应用服务 */
    public IotProductController(@NotNull IotApplicationService iot) {
        this.iot = Objects.requireNonNull(iot, "IoT 应用服务不能为空");
    }

    /** @return 分页 Product 及其启停状态 */
    @GetMapping
    @Operation(summary = "查询 IoT Product")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<Page<IotProductView>> findAll(@NotNull PageRequest page) {
        return ApiResponse.ok(iot.findProducts(page).map(IotProductView::from));
    }

    /** @return 双来源 Product 和应用安装的接入方式、协议目录 */
    @GetMapping("/catalog")
    @Operation(summary = "查询 IoT Product 管理目录")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<IotProductCatalogView> findCatalog(@NotNull PageRequest page) {
        return ApiResponse.ok(
                IotProductCatalogView.from(
                        iot.findProductCatalog(page),
                        iot.findSupportedProductAccessMethods(),
                        iot.findInstalledDeviceProtocols()));
    }

    /** @return 任意来源 Product 按版本递增的全部 ThingModel */
    @GetMapping("/catalog/{productCode}/thing-models")
    @Operation(summary = "查询 IoT Product 目录物模型")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<List<IotThingModelView>> findCatalogThingModels(
            @PathVariable @NotNull String productCode) {
        return ApiResponse.ok(
                iot.findCatalogThingModels(productCode)
                        .stream()
                        .map(IotThingModelView::from)
                        .toList());
    }

    /** @return 任意来源 Product 指定版本的 ThingModel */
    @GetMapping("/catalog/{productCode}/thing-models/{version}")
    @Operation(summary = "查询 IoT Product 目录物模型详情")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<IotThingModelView> getCatalogThingModel(
            @PathVariable @NotNull String productCode,
            @PathVariable int version) {
        return ApiResponse.ok(
                IotThingModelView.from(iot.catalogThingModelByCodeVersion(productCode, version)));
    }

    /** @return 指定 Product 及其启停状态 */
    @GetMapping("/{productId}")
    @Operation(summary = "查询 IoT Product 详情")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<IotProductView> get(@PathVariable long productId) {
        return ApiResponse.ok(IotProductView.from(iot.productById(productId)));
    }

    /** @return 新建且默认停用的 Product */
    @PostMapping
    @Operation(summary = "创建 IoT Product")
    @RequirePermission(operation = IotOperations.PRODUCTS_MANAGE)
    public @NotNull ApiResponse<IotProductView> create(
            @Valid @RequestBody @NotNull IotProductRequest request) {
        long productId = iot.createProduct(
                new ProductRegistration(
                        request.getCode(),
                        request.getI18n(),
                        accessMethods(request.getAccessMethods()),
                        request.getProtocolCodes()))
                .id();
        return ApiResponse.created(
                IotProductView.from(iot.productById(productId)),
                URI.create("/iot/products/" + productId));
    }

    /** @return 修改后的停用 Product */
    @PutMapping("/{productId}")
    @Operation(summary = "修改 IoT Product")
    @RequirePermission(operation = IotOperations.PRODUCTS_MANAGE)
    public @NotNull ApiResponse<IotProductView> update(
            @PathVariable long productId,
            @Valid @RequestBody @NotNull IotProductChangeRequest request) {
        iot.updateProduct(
                productId,
                new ProductChange(
                        accessMethods(request.getAccessMethods()),
                        request.getProtocolCodes()));
        return ApiResponse.ok(IotProductView.from(iot.productById(productId)));
    }

    /** @return Product 保存的全部 Locale 和展示字段 */
    @GetMapping("/{productId}/i18n")
    @Operation(summary = "查询 IoT Product 完整多语言字段")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<IotProductI18nView> findI18n(@PathVariable long productId) {
        return ApiResponse.ok(IotProductI18nView.from(iot.productById(productId).product()));
    }

    /** @return 完整替换后的 Product 多语言字段 */
    @PutMapping("/{productId}/i18n")
    @Operation(summary = "替换 IoT Product 完整多语言字段")
    @RequirePermission(operation = IotOperations.PRODUCTS_MANAGE)
    public @NotNull ApiResponse<IotProductI18nView> replaceI18n(
            @PathVariable long productId,
            @Valid @RequestBody @NotNull IotI18nRequest request) {
        return ApiResponse.ok(
                IotProductI18nView.from(
                        iot.replaceProductI18n(productId, request.getI18n())));
    }

    /** @return 发布成功或幂等命中的 ThingModel */
    @PostMapping(value = "/{productId}/thing-models", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "发布 ThingModel 技术定义和完整多语言字段")
    @RequirePermission(operation = IotOperations.PRODUCTS_MANAGE)
    public @NotNull ApiResponse<IotThingModelView> publishThingModel(
            @PathVariable long productId,
            @Valid @RequestBody @NotNull IotThingModelRequest request) {
        return ApiResponse.ok(
                IotThingModelView.from(
                        iot.publishThingModel(
                                productId,
                                request.getModel(),
                                request.getI18n())));
    }

    /** @return Product 按版本递增的全部已发布 ThingModel */
    @GetMapping("/{productId}/thing-models")
    @Operation(summary = "查询 IoT Product 的 ThingModel")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<List<IotThingModelView>> findThingModels(
            @PathVariable long productId) {
        return ApiResponse.ok(
                iot.findThingModels(productId)
                        .stream()
                        .map(IotThingModelView::from)
                        .toList());
    }

    /** @return Product 指定版本的 ThingModel */
    @GetMapping("/{productId}/thing-models/{version}")
    @Operation(summary = "查询 IoT ThingModel 详情")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<IotThingModelView> getThingModel(
            @PathVariable long productId,
            @PathVariable int version) {
        return ApiResponse.ok(
                IotThingModelView.from(
                        iot.thingModelByProductVersion(productId, version)));
    }

    /** @return 指定 ThingModel 版本保存的全部 Locale 和展示路径 */
    @GetMapping("/{productId}/thing-models/{version}/i18n")
    @Operation(summary = "查询 IoT ThingModel 完整多语言字段")
    @RequirePermission(operation = IotOperations.PRODUCTS_READ)
    public @NotNull ApiResponse<IotThingModelI18nView> findThingModelI18n(
            @PathVariable long productId,
            @PathVariable int version) {
        return ApiResponse.ok(
                IotThingModelI18nView.from(
                        iot.thingModelByProductVersion(productId, version)));
    }

    /** @return 完整替换后的 ThingModel 多语言字段 */
    @PutMapping("/{productId}/thing-models/{version}/i18n")
    @Operation(summary = "替换 IoT ThingModel 完整多语言字段")
    @RequirePermission(operation = IotOperations.PRODUCTS_MANAGE)
    public @NotNull ApiResponse<IotThingModelI18nView> replaceThingModelI18n(
            @PathVariable long productId,
            @PathVariable int version,
            @Valid @RequestBody @NotNull IotI18nRequest request) {
        return ApiResponse.ok(
                IotThingModelI18nView.from(
                        iot.replaceThingModelI18n(productId, version, request.getI18n())));
    }

    /** @return 启用或停用后的 Product */
    @PatchMapping("/{productId}/status")
    @Operation(summary = "修改 IoT Product 状态")
    @RequirePermission(operation = IotOperations.PRODUCTS_MANAGE)
    public @NotNull ApiResponse<IotProductView> changeStatus(
            @PathVariable long productId,
            @Valid @RequestBody @NotNull IotStatusRequest request) {
        iot.changeProductStatus(productId, request.getStatus());
        return ApiResponse.ok(IotProductView.from(iot.productById(productId)));
    }

    /** 把 HTTP code 集合转换成 IoT 的可扩展接入方式值类型。 */
    private static @NotNull Set<DeviceAccessMethod> accessMethods(@NotNull Set<String> codes) {
        return codes.stream().map(DeviceAccessMethod::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
