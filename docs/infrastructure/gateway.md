# 可选网关

`sca4d-gateway` 是独立的 Spring Cloud Gateway 应用，用于统一入口、负载均衡、主动健康检查和 upstream 文件热更新。主后端可以不经过网关独立运行。

默认端口为 `8088`。网关通过 DiscoveryClient 向 Spring Cloud Gateway 提供健康实例，不访问业务数据库，也不承担后端权限判断。启动命令见[日常开发命令](/handbook/daily-development.md)。

## upstream 配置

默认文件为 `sca4d-gateway/config/gateway-upstreams.yml`：

```yaml
sca4dj:
  gateway:
    upstreams:
      sca4d-app:
        health-path: /actuator/health/readiness
        connect-timeout: 2s
        response-timeout: 10s
        healthy-threshold: 2
        unhealthy-threshold: 3
        instances:
          - id: app-local
            uri: http://127.0.0.1:8080
            enabled: true
            draining: false
```

`GatewayUpstreamFileLoader` 校验配置并生成快照。文件变化后，Registry 加载新快照；格式错误时继续使用上一个有效版本。

## 健康检查

网关主动请求每个实例的 health path。连续成功与失败通过 `GatewayUpstreamHealthState` 推进状态，DiscoveryClient 只返回已经启用、未 draining 且健康的实例。

新增实例时先确认 readiness，再写入 upstream 文件。下线实例可以先设置 `draining: true`，等待网关停止分配新请求后关闭进程。

## 配置项

```yaml
sca4dj:
  gateway:
    upstreams-file: config/gateway-upstreams.yml
    reload-interval-millis: 5000
    health-interval-millis: 5000
```

主应用通过可信代理配置解析客户端地址，网关无需把权限逻辑复制到路由层。

## 失败结果

- upstream 文件不存在、YAML 无法解析、serviceId 重复、URI 非法或服务没有实例时，新快照不会生效。
- 健康检查连续失败达到 `unhealthy-threshold` 后，实例停止接收新请求。
- 连续成功达到 `healthy-threshold` 后，实例重新进入可用列表。
- 一个 serviceId 没有可用实例时，Gateway 返回无可用上游错误，不转发到 disabled、draining 或未通过健康检查的实例。

upstream 文件热更新只改变路由目标，不修改主应用认证、权限或业务数据。
