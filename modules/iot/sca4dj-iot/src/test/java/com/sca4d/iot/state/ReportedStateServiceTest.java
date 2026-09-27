package com.sca4d.iot.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sca4d.core.api.error.BusinessException;
import com.sca4d.core.api.id.IdGenerator;
import com.sca4d.core.api.messaging.DispatchPolicy;
import com.sca4d.core.api.messaging.DispatchReceipt;
import com.sca4d.core.api.messaging.MessageBus;
import com.sca4d.core.api.id.Id;
import com.sca4d.iot.IotErrorCode;
import com.sca4d.iot.api.Device;
import com.sca4d.iot.api.ProductBinding;
import com.sca4d.iot.api.ProductDefinition;
import com.sca4d.iot.api.change.DeviceChangeArea;
import com.sca4d.iot.api.message.DeviceAcknowledgement;
import com.sca4d.iot.api.model.ThingModel;
import com.sca4d.iot.api.model.ThingModelPresentation;
import com.sca4d.iot.api.model.ThingModelRepository;
import com.sca4d.iot.api.model.ThingProperty;
import com.sca4d.iot.api.model.ThingValueType;
import com.sca4d.iot.api.state.ReportedMergeMode;
import com.sca4d.iot.api.state.ReportedStateStore;
import com.sca4d.iot.api.state.ReportedStateUpdate;
import com.sca4d.iot.api.state.ReportedStateWriteResult;
import com.sca4d.iot.change.DeviceChangeNotifier;
import com.sca4d.iot.device.DeviceRepository;
import com.sca4d.iot.message.DesiredConvergence;
import com.sca4d.iot.message.DownlinkRepository;
import com.sca4d.iot.model.ThingValueValidator;
import com.sca4d.iot.product.ProductDefinitionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 验证 reported 输入保护和 desired 收敛 ACK 的可靠消息边界。 */
class ReportedStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");

    @Test
    @DisplayName("reported 属性超限时不读取 Device 绑定或物模型")
    void rejectsOversizedReportedBeforeIo() {
        DeviceRepository devices = mock(DeviceRepository.class);
        ProductDefinitionCatalog products = mock(ProductDefinitionCatalog.class);
        ReportedStateService service = service(
                devices,
                products,
                mock(ReportedStateStore.class),
                mock(DownlinkRepository.class),
                mock(ThingModelRepository.class),
                mock(ThingValueValidator.class),
                mock(MessageBus.class),
                mock(DeviceChangeNotifier.class),
                1);
        ReportedStateUpdate update = new ReportedStateUpdate(
                "status",
                ReportedMergeMode.PATCH,
                Set.of("power", "temperature"),
                Map.of("power", true),
                null,
                null,
                NOW);

        assertThatThrownBy(() -> service.accept(device(), update, List.of()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(IotErrorCode.INVALID_INPUT));

        verifyNoInteractions(devices, products);
    }

    @Test
    @DisplayName("reported 收敛通过可靠事件请求 ACK 状态转换")
    void publishesReliableAcknowledgementRequestForConvergence() {
        Device device = device();
        DeviceRepository devices = mock(DeviceRepository.class);
        when(devices.isCurrentBinding(device.id(), device.bindingGeneration())).thenReturn(true);
        ProductDefinitionCatalog products = mock(ProductDefinitionCatalog.class);
        ProductDefinition product = mock(ProductDefinition.class);
        when(product.code()).thenReturn("relay");
        when(products.definition(device.product())).thenReturn(product);
        ThingProperty property = mock(ThingProperty.class);
        ThingValueType valueType = mock(ThingValueType.class);
        when(property.valueType()).thenReturn(valueType);
        ThingModel model = mock(ThingModel.class);
        when(model.properties()).thenReturn(Map.of("power", property));
        when(model.events()).thenReturn(Map.of());
        ThingModelPresentation presentation = mock(ThingModelPresentation.class);
        when(presentation.model()).thenReturn(model);
        ThingModelRepository models = mock(ThingModelRepository.class);
        when(models.find("relay", device.thingModelVersion())).thenReturn(Optional.of(presentation));
        ThingValueValidator validator = mock(ThingValueValidator.class);
        when(validator.validate(valueType, true)).thenReturn(true);
        ReportedStateStore reportedStates = mock(ReportedStateStore.class);
        when(reportedStates.update(anyLong(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(new ReportedStateWriteResult(ReportedStateWriteResult.Outcome.ACCEPTED, 7));
        DownlinkRepository downlinks = mock(DownlinkRepository.class);
        when(downlinks.convergeDesired(anyLong(), anyLong(), any(), any()))
                .thenReturn(List.of(new DesiredConvergence("power", 99)));
        MessageBus messages = mock(MessageBus.class);
        when(messages.dispatch(any(), any()))
                .thenReturn(new DispatchReceipt(Id.of(1000), DispatchReceipt.Status.OUTBOX_ENLISTED));
        DeviceChangeNotifier changes = mock(DeviceChangeNotifier.class);
        ReportedStateService service = service(
                devices,
                products,
                reportedStates,
                downlinks,
                models,
                validator,
                messages,
                changes,
                10);
        ReportedStateUpdate update = new ReportedStateUpdate(
                "status",
                ReportedMergeMode.PATCH,
                Set.of("power"),
                Map.of("power", true),
                "device-sequence",
                "0001",
                NOW.minusSeconds(1));

        ReportedStateResult result = service.accept(device, update, List.of());

        assertThat(result.outcome()).isEqualTo(ReportedStateResult.Outcome.ACCEPTED);
        ArgumentCaptor<DeviceAcknowledgement> message = ArgumentCaptor.forClass(DeviceAcknowledgement.class);
        verify(messages).dispatch(message.capture(), argThat(policy -> policy.mode() == DispatchPolicy.Mode.OUTBOX));
        assertThat(message.getValue().code()).isEqualTo(DeviceAcknowledgement.CODE);
        assertThat(message.getValue().messageId()).isEqualTo(99);
        assertThat(message.getValue().source()).isEqualTo("REPORTED");
        verify(changes).changed(device.id(), EnumSet.of(DeviceChangeArea.REPORTED), NOW);
    }

    /** 创建只替换 reported 属性数量上限的测试服务。 */
    private static ReportedStateService service(
            DeviceRepository devices,
            ProductDefinitionCatalog products,
            ReportedStateStore reportedStates,
            DownlinkRepository downlinks,
            ThingModelRepository models,
            ThingValueValidator validator,
            MessageBus messages,
            DeviceChangeNotifier changes,
            int maxReportedProperties) {
        return new ReportedStateService(
                devices,
                products,
                reportedStates,
                downlinks,
                models,
                validator,
                mock(IdGenerator.class),
                messages,
                Clock.fixed(NOW, ZoneOffset.UTC),
                changes,
                maxReportedProperties,
                10);
    }

    /** 创建使用固定代码 Product 和绑定代次的测试 Device。 */
    private static Device device() {
        return new Device(
                10,
                ProductBinding.code("relay"),
                "relay-10",
                null,
                "Relay 10",
                null,
                3,
                1,
                "relay-v1",
                null,
                Set.of());
    }
}
