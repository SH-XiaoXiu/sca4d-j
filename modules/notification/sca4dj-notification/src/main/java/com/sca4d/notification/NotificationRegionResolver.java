package com.sca4d.notification;

import com.sca4d.core.api.notification.NotificationRecipient;
import com.sca4d.core.api.region.Region;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** 按内部接收人读取具体应用定义的业务区域。 */
@FunctionalInterface
public interface NotificationRegionResolver {

    /** @return 接收人的业务区域；当前应用没有区域信息时为空 */
    @NotNull
    Optional<Region> resolve(@NotNull NotificationRecipient recipient);
}
