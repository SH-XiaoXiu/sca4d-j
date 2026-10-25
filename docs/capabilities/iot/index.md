# IoT

本目录说明 IoT 功能：设备定义与生命周期、运行状态与历史查询、设备定时，以及 EMQX 与 MQTT 接入。IoT API 与具体接入实现分开。具体应用只选择需要的模块并配置基础设施。适用读者为开发设备接入、设备业务和历史数据功能的开发者。

## 设备与查询

- [IoT 基础模块](/capabilities/iot/iot)
- [设备生命周期](/capabilities/iot/device-lifecycle)
- [设备运行查询](/capabilities/iot/device-runtime-query)
- [设备历史查询](/capabilities/iot/device-history-query)
- [设备调度](/capabilities/iot/device-scheduling)

具体应用的业务设备资料由具体应用自己的文档说明，不在本目录列出。

## 接入与历史数据

- [EMQX 设备接入模块](/capabilities/iot/emqx)
- [MQTT 设备协议](/capabilities/iot/mqtt-device-protocol)
- [HMAC 请求签名](/capabilities/iot/hmac-request-signing)
- [TimescaleDB 历史模块](/capabilities/iot/timescale)
- [TimescaleDB 配置](/capabilities/iot/timescale-configuration)
