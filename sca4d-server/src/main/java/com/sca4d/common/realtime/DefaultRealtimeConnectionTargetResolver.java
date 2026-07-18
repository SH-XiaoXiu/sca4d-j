package com.sca4d.common.realtime;

import com.sca4d.common.auth.Principal;
import com.sca4d.common.auth.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认实时连接目标解析器。
 */
public class DefaultRealtimeConnectionTargetResolver implements RealtimeConnectionTargetResolver {

    @Override
    public @NotNull List<RealtimeTarget> resolve(
            @Nullable Subject subject, @Nullable Principal principal) {
        List<RealtimeTarget> targets = new ArrayList<>();
        if (subject != null) {
            targets.add(RealtimeTarget.subject(subject.getSubjectId()));
            targets.add(RealtimeTarget.topic(RealtimeRoutes.CLIENT_STATE_TOPIC));
        }
        if (principal != null) {
            targets.add(RealtimeTarget.principal(principal.getPrincipalType(), principal.requirePrincipalId()));
            if (principal.getTenantId() != null) {
                targets.add(RealtimeTarget.tenant(principal.getTenantId()));
            }
        }
        return List.copyOf(targets);
    }
}
