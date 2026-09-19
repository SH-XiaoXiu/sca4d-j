package com.sca4d.iot.emqx.protocol;

/** 区分 MQTT 上行报文进入 reported state 还是业务 ACK 处理。 */
public enum MqttUplinkKind {
    /** 把厂商状态报文转换为物模型 reported state。 */
    REPORTED,
    /** 把厂商响应报文转换为标准 `DeviceAcknowledgement`。 */
    ACKNOWLEDGEMENT
}
