# IotTimescale

源码：`modules/iot/sca4dj-iot-timescale/src/main/java/com/sca4d/iot/config/IotTimescale.java`

IotTimescale 是限定 IoT 历史库 MyBatis 会话对象和 Mapper 的 Spring Qualifier。历史会话只加载 IoT 历史 Mapper，不与默认会话混用。

`IotTimescale.DATASOURCE` 的值为 `iot-history`，对应具体应用在 `spring.datasource.dynamic.datasource` 中配置的历史库名称。TimescaleTelemetryConsumer、TimescaleEventConsumer、TimescaleDeviceHistoryStore 和 TimescaleRetentionTask 使用 `IotTimescale.DATASOURCE` 选择数据源路由，并在独立事务中访问历史库。

IotTimescale 限定符不选择 namespace。运行时命名空间用于 Redis、消息和其他共享资源隔离。独立关系数据库通过 DataSource 与配置前缀隔离。

## 示例

以下配置把历史库作为动态数据源的一个条目。主库与历史库由同一个动态路由 DataSource 管理。数据库地址和凭据从部署环境读取：

```yaml
spring:
  datasource:
    dynamic:
      primary: main
      strict: true
      datasource:
        main:
          url: ${MAIN_DATABASE_URL}
          username: ${MAIN_DATABASE_USERNAME}
          password: ${MAIN_DATABASE_PASSWORD}
        iot-history:
          url: ${IOT_HISTORY_DATABASE_URL}
          username: ${IOT_HISTORY_DATABASE_USERNAME}
          password: ${IOT_HISTORY_DATABASE_PASSWORD}
```

应用代码在注入点使用 `@IotTimescale`，选择历史库的会话对象。该注解是 Qualifier，不接受参数：

```java
import com.sca4d.iot.config.IotTimescale;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Component;

@Component
public class IotHistorySessionHolder {

    private final SqlSessionTemplate historySession;

    public IotHistorySessionHolder(@IotTimescale SqlSessionTemplate historySession) {
        this.historySession = historySession;
    }

    public SqlSessionTemplate session() {
        return historySession;
    }
}
```

`@IotTimescale` 只选择 IoT 历史库的 Bean。不带该注解的 `SqlSessionTemplate` 仍表示默认会话。
## 扩展方式

应用通过配置 `spring.datasource.dynamic.datasource.iot-history` 提供历史库连接。应用需要在历史库中执行 IoT 历史结构迁移，迁移文件位置与执行方式见 [IoT TimescaleDB 历史模块](/capabilities/iot/timescale)。应用不需要为历史库另建 Repository，模块已经提供历史访问实现。
