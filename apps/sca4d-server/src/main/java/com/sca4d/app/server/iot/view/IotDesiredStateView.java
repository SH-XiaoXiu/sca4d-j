package com.sca4d.app.server.iot.view;

import com.sca4d.iot.message.DesiredStateResult;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;

/** 返回 desired 当前 desiredSequence 和本次实际生成的状态同步消息。 */
@SuppressWarnings("unused")
public class IotDesiredStateView {

    private final @NotNull @Unmodifiable List<IotDownlinkMessageView> messages;

    private final long desiredSequence;

    /** 从公共 desired 修改结果创建 HTTP 视图。 */
    private IotDesiredStateView(@NotNull DesiredStateResult result) {
        this.desiredSequence = result.desiredSequence();
        this.messages = result.messages().stream().map(IotDownlinkMessageView::from).toList();
    }

    /** @return desired 修改 HTTP 结果 */
    @CheckReturnValue
    public static @NotNull IotDesiredStateView from(@NotNull DesiredStateResult result) {
        return new IotDesiredStateView(Objects.requireNonNull(result, "desired 修改结果不能为空"));
    }

    /** @return Device 当前 desired sequence */
    public long getDesiredSequence() {
        return desiredSequence;
    }

    /** @return 本次实际变化生成的状态同步消息 */
    public @NotNull @Unmodifiable List<IotDownlinkMessageView> getMessages() {
        return List.copyOf(messages);
    }
}
