package com.sca4d.app.server.controllers.iot;

import cn.hutool.core.util.StrUtil;
import com.sca4d.app.server.iot.IotDeviceCredentials;
import com.sca4d.app.server.iot.IotDeviceDetails;
import com.sca4d.app.server.iot.IotOperations;
import com.sca4d.app.server.iot.request.DesiredStateRequest;
import com.sca4d.app.server.iot.request.IotDeviceBindingRequest;
import com.sca4d.app.server.iot.request.IotDeviceConnectionRequest;
import com.sca4d.app.server.iot.request.IotDeviceDetailsRequest;
import com.sca4d.app.server.iot.request.IotDeviceRequest;
import com.sca4d.app.server.iot.request.IotStatusRequest;
import com.sca4d.app.server.iot.view.IotDesiredStateView;
import com.sca4d.app.server.iot.view.IotDeviceCredentialView;
import com.sca4d.app.server.iot.view.IotDeviceView;
import com.sca4d.app.server.iot.view.IotDownlinkMessageView;
import com.sca4d.app.server.iot.view.IotSecretView;
import com.sca4d.app.server.platform.PlatformPrincipalSelection;
import com.sca4d.app.server.service.IotApplicationService;
import com.sca4d.auth.x509.X509CredentialRegistration;
import com.sca4d.core.api.annotation.RequirePermission;
import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.query.Page;
import com.sca4d.core.api.query.PageRequest;
import com.sca4d.core.spring.web.ApiResponse;
import com.sca4d.core.spring.web.annotation.CurrentPrincipal;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.DeviceAccessMethod;
import com.sca4d.iot.api.ProductSelector;
import com.sca4d.iot.api.connection.DeviceConnectionBinding;
import com.sca4d.iot.api.connection.DeviceConnectionProtocol;
import com.sca4d.iot.device.DeviceBindingChange;
import com.sca4d.iot.device.DeviceDetailsChange;
import com.sca4d.iot.device.DeviceRegistration;
import com.sca4d.iot.message.DesiredStateChange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 提供 Sca4d Server 的 Device 管理、凭据、desired 和 command HTTP 接口。 */
@RestController
@ConditionalOnProperty(prefix = "app.iot.capabilities", name = "api", havingValue = "true", matchIfMissing = true)
@RequestMapping("/iot/devices")
@Tag(name = "IoT Devices")
@CurrentPrincipal(selection = PlatformPrincipalSelection.class)
public class IotDeviceController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final @NotNull IotApplicationService iot;

    /** @param iot IoT 应用服务 */
    public IotDeviceController(@NotNull IotApplicationService iot) {
        this.iot = Objects.requireNonNull(iot, "IoT 应用服务不能为空");
    }

    /** @return 分页 Device、连接身份和启停状态 */
    @GetMapping
    @Operation(summary = "查询 IoT Device")
    @RequirePermission(operation = IotOperations.DEVICES_READ)
    public @NotNull ApiResponse<Page<IotDeviceView>> findAll(@NotNull PageRequest page) {
        return ApiResponse.ok(iot.findDevices(page).map(IotDeviceView::from));
    }

    /** @return 指定 Device、连接身份和启停状态 */
    @GetMapping("/{deviceId}")
    @Operation(summary = "查询 IoT Device 详情")
    @RequirePermission(operation = IotOperations.DEVICES_READ)
    public @NotNull ApiResponse<IotDeviceView> get(@PathVariable long deviceId) {
        return ApiResponse.ok(IotDeviceView.from(iot.deviceById(deviceId)));
    }

    /** @return 新建 Device、可选 Account 和一次性 Secret */
    @PostMapping
    @Operation(summary = "创建 IoT Device")
    @RequirePermission(operation = IotOperations.DEVICES_MANAGE)
    public @NotNull ApiResponse<IotDeviceCredentialView> create(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotNull String idempotencyKey,
            @Valid @RequestBody @NotNull IotDeviceRequest request) {
        DeviceRegistration registration = new DeviceRegistration(
                ProductSelector.database(request.getProductId()),
                request.getCode(),
                request.getSerialNumber(),
                request.getDisplayName(),
                request.getRemark(),
                request.getThingModelVersion(),
                request.getProtocolCode(),
                connection(
                        request.getConnectionProtocol(),
                        request.getConnectionIdentityType(),
                        request.getConnectionIdentity()),
                request.getAccessMethods()
                        .stream()
                        .map(DeviceAccessMethod::parse)
                        .collect(Collectors.toUnmodifiableSet()));
        IotDeviceCredentials created = iot.createDevice(
                idempotencyKey,
                registration,
                certificate(request.getCertificateDerBase64()));
        long deviceId = created.device().id();
        IotDeviceDetails details = iot.deviceById(deviceId);
        return ApiResponse.created(
                IotDeviceCredentialView.from(
                        created,
                        details.profile()),
                URI.create("/iot/devices/" + deviceId));
    }

    /** @return 修改展示资料后的 Device */
    @PutMapping("/{deviceId}/details")
    @Operation(summary = "修改 IoT Device 展示资料")
    @RequirePermission(operation = IotOperations.DEVICES_MANAGE)
    public @NotNull ApiResponse<IotDeviceView> updateDetails(
            @PathVariable long deviceId,
            @Valid @RequestBody @NotNull IotDeviceDetailsRequest request) {
        iot.updateDeviceDetails(
                deviceId,
                new DeviceDetailsChange(
                        request.getDisplayName(),
                        request.getRemark()));
        return ApiResponse.ok(IotDeviceView.from(iot.deviceById(deviceId)));
    }

    /** @return 切换物模型和设备协议后的 Device */
    @PutMapping("/{deviceId}/binding")
    @Operation(summary = "切换 IoT Device 物模型和协议")
    @RequirePermission(operation = IotOperations.DEVICES_MANAGE)
    public @NotNull ApiResponse<IotDeviceView> rebind(
            @PathVariable long deviceId,
            @Valid @RequestBody @NotNull IotDeviceBindingRequest request) {
        iot.rebindDevice(
                deviceId,
                new DeviceBindingChange(
                        request.getThingModelVersion(),
                        request.getProtocolCode()));
        return ApiResponse.ok(IotDeviceView.from(iot.deviceById(deviceId)));
    }

    /** @return 连接绑定和接入方式修改结果，以及本次可选新签发 Secret */
    @PutMapping("/{deviceId}/connection")
    @Operation(summary = "修改 IoT Device 连接绑定和接入方式")
    @RequirePermission(operation = IotOperations.DEVICES_MANAGE)
    public @NotNull ApiResponse<IotDeviceCredentialView> changeConnection(
            @PathVariable long deviceId,
            @Valid @RequestBody @NotNull IotDeviceConnectionRequest request) {
        IotDeviceCredentials changed = iot.changeDeviceConnection(
                deviceId,
                connection(
                        request.getConnectionProtocol(),
                        request.getConnectionIdentityType(),
                        request.getConnectionIdentity()),
                request.getAccessMethods()
                        .stream()
                        .map(DeviceAccessMethod::parse)
                        .collect(Collectors.toUnmodifiableSet()),
                certificate(request.getCertificateDerBase64()));
        IotDeviceDetails details = iot.deviceById(deviceId);
        return ApiResponse.ok(
                IotDeviceCredentialView.from(
                        changed,
                        details.profile()));
    }

    /** @return 新 Shared Secret Credential version 和只显示一次的明文 */
    @PostMapping("/{deviceId}/shared-secret/reset")
    @Operation(summary = "重置 IoT Device Shared Secret")
    @RequirePermission(operation = IotOperations.DEVICES_MANAGE)
    public @NotNull ApiResponse<IotSecretView> resetSharedSecret(@PathVariable long deviceId) {
        return ApiResponse.ok(IotSecretView.from(iot.resetDeviceSharedSecret(deviceId)));
    }

    /** @return 新 HMAC Credential version 和只显示一次的 Key */
    @PostMapping("/{deviceId}/hmac-key/reset")
    @Operation(summary = "重置 IoT Device HMAC Key")
    @RequirePermission(operation = IotOperations.DEVICES_MANAGE)
    public @NotNull ApiResponse<IotSecretView> resetHmacKey(@PathVariable long deviceId) {
        return ApiResponse.ok(IotSecretView.from(iot.resetDeviceHmacKey(deviceId)));
    }

    /** @return 启用或停用后的 Device */
    @PatchMapping("/{deviceId}/status")
    @Operation(summary = "修改 IoT Device 状态")
    @RequirePermission(operation = IotOperations.DEVICES_MANAGE)
    public @NotNull ApiResponse<IotDeviceView> changeStatus(
            @PathVariable long deviceId,
            @Valid @RequestBody @NotNull IotStatusRequest request) {
        iot.changeDeviceStatus(deviceId, request.getStatus());
        return ApiResponse.ok(IotDeviceView.from(iot.deviceById(deviceId)));
    }

    /** @return desired 当前序号和本次生成的状态同步消息 */
    @PutMapping("/{deviceId}/desired")
    @Operation(summary = "修改 IoT Device desired state")
    @RequirePermission(operation = IotOperations.DEVICES_OPERATE)
    public @NotNull ApiResponse<IotDesiredStateView> changeDesired(
            @PathVariable long deviceId,
            @Valid @RequestBody @NotNull DesiredStateRequest request) {
        return ApiResponse.ok(
                IotDesiredStateView.from(
                        iot.changeDesired(
                                deviceId,
                                new DesiredStateChange(request.getSet(), request.getClear()))));
    }

    /** @return 新建 command 的标准下行消息 */
    @PostMapping("/{deviceId}/commands/{commandCode}")
    @Operation(summary = "提交 IoT Device command")
    @RequirePermission(operation = IotOperations.DEVICES_OPERATE)
    public @NotNull ApiResponse<IotDownlinkMessageView> submitCommand(
            @PathVariable long deviceId,
            @PathVariable @NotNull String commandCode,
            @RequestBody @NotNull @Size(max = 64) Map<String, Object> inputs) {
        return ApiResponse.ok(
                IotDownlinkMessageView.from(
                        iot.submitCommand(deviceId, commandCode, inputs)));
    }

    /** 解码可选 X.509 DER Base64；格式错误时返回统一输入错误。 */
    private static @Nullable X509CredentialRegistration certificate(@Nullable String base64) {
        if (base64 == null) {
            return null;
        }
        try {
            return new X509CredentialRegistration(Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
    }

    /** 把全空或全有的 HTTP 连接字段转换为可选 Device 直接连接绑定。 */
    private static @Nullable DeviceConnectionBinding connection(
            @Nullable String protocol,
            @Nullable String identityType,
            @Nullable String identity) {
        boolean protocolMissing = StrUtil.isBlank(protocol);
        boolean identityTypeMissing = StrUtil.isBlank(identityType);
        boolean identityMissing = StrUtil.isBlank(identity);
        if (protocolMissing && identityTypeMissing && identityMissing) {
            return null;
        }
        if (protocolMissing || identityTypeMissing || identityMissing) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT);
        }
        try {
            return new DeviceConnectionBinding(
                    new DeviceConnectionProtocol(protocol),
                    identityType,
                    identity);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(IotErrorCode.INVALID_INPUT, exception);
        }
    }
}
