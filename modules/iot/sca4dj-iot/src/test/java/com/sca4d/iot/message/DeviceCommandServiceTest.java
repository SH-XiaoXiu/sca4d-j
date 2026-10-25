package com.sca4d.iot.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.data.DataKey;
import com.sca4d.core.api.data.DataValues;
import com.sca4d.core.api.error.exception.BusinessException;
import com.sca4d.core.api.i18n.MessageText;
import com.sca4d.core.api.id.Id;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.message.DeviceDownlink;
import com.sca4d.iot.api.message.DeviceDownlinkKind;
import com.sca4d.iot.api.message.DeviceDownlinkStatus;
import com.sca4d.iot.api.model.ThingCommand;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.policy.NoAcknowledgementPolicy;
import com.sca4d.iot.message.policy.NoRetryPolicy;
import com.sca4d.iot.model.ThingValueValidator;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 验证 Device command 类型化入口复用当前属性校验和可靠下行事务。 */
class DeviceCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T08:00:00Z");

    private static final DataKey<String> NET_ID = DataKey.of("netId", String.class);

    private static final ThingCommand SET_NET_ID = ThingCommand.builder("setNetId")
            .input(
                    NET_ID,
                    ThingValueType.stringType()
                            .maximumLength(4)
                            .pattern("[0-9a-f]{4}")
                            .build())
            .acknowledgementPolicy(new NoAcknowledgementPolicy())
            .retryPolicy(new NoRetryPolicy())
            .build();

    private final DeviceRepository devices = mock(DeviceRepository.class);

    private final ProductDefinitionCatalog products = mock(ProductDefinitionCatalog.class);

    private final DownlinkRepository downlinks = mock(DownlinkRepository.class);

    private final ThingModelRepository models = mock(ThingModelRepository.class);

    private final IdGenerator ids = mock(IdGenerator.class);

    private final DeviceChangeNotifier changes = mock(DeviceChangeNotifier.class);

    private final DeviceDeliveryScheduler publisher = mock(DeviceDeliveryScheduler.class);

    private final DeviceCommandService service = new DeviceCommandService(
            devices,
            products,
            downlinks,
            models,
            new ThingValueValidator(),
            ids,
            Clock.fixed(NOW, ZoneOffset.UTC),
            changes,
            publisher);

    @Test
    void submitsTypedCommandUsingBoundThingModel() {
        prepareDevice();
        when(ids.nextId()).thenReturn(Id.of(91L));
        Duration timeout = Duration.ofSeconds(30);

        DeviceDownlink result = service.submit(
                81L,
                SET_NET_ID,
                DataValues.builder().put(NET_ID, "12af").build(),
                null,
                timeout);

        assertThat(result.targetCode()).isEqualTo("setNetId");
        assertThat(result.data()).containsExactly(Map.entry("netId", "12af"));
        assertThat(result.acknowledgementTimeout()).isEqualTo(timeout);
        verify(downlinks).add(result);
        verify(publisher).publishDispatch(91L, 1, null);
        verify(changes).changed(81L, DeviceChangeArea.DOWNLINK, NOW);
    }

    @Test
    void rejectsMissingTypedInputBeforeReadingDevice() {
        assertThatThrownBy(
                () -> service.submit(
                        81L,
                        SET_NET_ID,
                        DataValues.builder().build(),
                        null,
                        Duration.ofSeconds(30)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(IotErrorCode.INVALID_INPUT));

        verifyNoInteractions(devices, downlinks, publisher);
    }

    @Test
    void rejectsValueThatViolatesBoundThingModel() {
        prepareDevice();

        assertThatThrownBy(
                () -> service.submit(
                        81L,
                        SET_NET_ID,
                        DataValues.builder().put(NET_ID, "12AF").build(),
                        null,
                        Duration.ofSeconds(30)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(IotErrorCode.INVALID_INPUT));

        verifyNoInteractions(downlinks, publisher);
    }

    @Test
    void cancelsOnlyUnfinishedCommands() {
        DeviceDownlink message = mock(DeviceDownlink.class);
        when(message.kind()).thenReturn(DeviceDownlinkKind.COMMAND);
        when(message.status()).thenReturn(DeviceDownlinkStatus.PENDING);
        when(message.deviceId()).thenReturn(81L);
        when(downlinks.find(91L)).thenReturn(Optional.of(message));
        when(downlinks.complete(91L, DeviceDownlinkStatus.CANCELLED, null, NOW)).thenReturn(true);
        assertThat(service.cancel(91L)).isTrue();
        when(message.status()).thenReturn(DeviceDownlinkStatus.CANCELLED);
        assertThat(service.cancel(91L)).isFalse();
        when(message.kind()).thenReturn(DeviceDownlinkKind.DESIRED);
        assertThatThrownBy(() -> service.cancel(91L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.cancel(92L)).isInstanceOf(BusinessException.class);
    }

    /** 准备绑定代码 Product 和类型化命令的启用前置数据。 */
    private void prepareDevice() {
        Device device = new Device(
                81L,
                ProductBinding.code("gateway"),
                "gateway-81",
                null,
                "Gateway",
                null,
                1,
                1,
                "gateway-v1",
                null,
                Set.of());
        ProductDefinition product = new ProductDefinition(
                "gateway",
                MessageText.of("gateway"),
                Set.of(),
                Set.of("gateway-v1"));
        ThingModelPresentation presentation = mock(ThingModelPresentation.class);
        when(presentation.model()).thenReturn(
                ThingModel.builder("gateway", 1).command(SET_NET_ID).build());
        when(devices.find(81L)).thenReturn(Optional.of(device));
        when(products.definition(device.product())).thenReturn(product);
        when(models.find("gateway", 1)).thenReturn(Optional.of(presentation));
    }
}
