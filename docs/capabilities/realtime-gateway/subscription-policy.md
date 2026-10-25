# WebRealtimeSubscriptionPolicy

源码：`modules/web-realtime/sca4dj-web-realtime/src/main/java/com/sca4d/web/realtime/stomp/WebRealtimeSubscriptionPolicy.java`

`supports(destination)` 选择负责的 STOMP `SUBSCRIBE` destination。`authorize(call, destination)` 接收当前帧专用的 Call。该 Call 已经认证 Session，并已选择 `principal-id` 对应的业务 Principal。`authorize` 检查权限后，返回 Resource 或 Topic 目标。

没有策略负责、返回其他目标类型，或授权抛出异常时，Spring 返回 STOMP `ERROR`，模块不登记订阅。

## 示例

以下示例对应工作区文档协作应用。成员订阅某个工作区的文档变化时，策略检查该成员是否属于此工作区，通过后返回 Topic 目标。

```java
import com.sca4d.core.api.call.Call;
import com.sca4d.core.api.webrealtime.WebRealtimeTarget;
import com.sca4d.web.realtime.stomp.WebRealtimeSubscriptionPolicy;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceTopicSubscriptionPolicy implements WebRealtimeSubscriptionPolicy {

    private static final String PREFIX = "/topic/workspaces/";

    private final WorkspaceMemberAccess memberAccess;

    public WorkspaceTopicSubscriptionPolicy(WorkspaceMemberAccess memberAccess) {
        this.memberAccess = memberAccess;
    }

    @Override
    public boolean supports(String destination) {
        return destination.startsWith(PREFIX);
    }

    @Override
    public WebRealtimeTarget authorize(Call call, String destination) {
        long workspaceId = Long.parseLong(destination.substring(PREFIX.length()));
        // 应用检查当前 Principal 是否为该 Workspace 的 Member，不是则抛出异常。
        memberAccess.requireMember(call, workspaceId);
        return WebRealtimeTarget.topic("workspace:" + workspaceId);
    }
}
```

`WorkspaceMemberAccess` 是应用自己的权限服务，不属于 Web Realtime 模块。`requireMember` 在成员关系不成立时抛出异常，此时订阅不会被登记。

订阅 destination 与 Topic 字符串分开定义。destination 由浏览器提供，策略把它转换为服务端定义的 Topic。发布端使用同一个 Topic 字符串，见 [实时通信规则](/capabilities/realtime-gateway/development)。
## 扩展方式

具体应用实现 `WebRealtimeSubscriptionPolicy` 并注册为 Spring Bean。模块注入全部策略，并对每个 `SUBSCRIBE` 按 `supports(destination)` 选择策略。没有策略返回 `true` 时，订阅失败。应用应保证各策略的 `supports` 条件互不重叠。
