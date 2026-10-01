package com.sca4d.core.api.delivery;

import com.sca4d.core.api.id.Id;
import org.jetbrains.annotations.NotNull;

/** 推进一次具体业务投递，并返回所属组件可以持久化和继续处理的结果。 */
public interface Delivery<R> {

    /** @return 当前投递动作的业务结果 */
    @NotNull
    DeliveryOutcome deliver(@NotNull Id sourceMessageId, @NotNull R request);
}
