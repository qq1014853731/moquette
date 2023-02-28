package io.moquette.broker;

import io.moquette.broker.subscriptions.Topic;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.Data;

import java.io.Serializable;

@Data
public class RetainedMessage implements Serializable{

    private Topic topic;
    private MqttQoS qos;
    private byte[] payload;

    public RetainedMessage() {}

    public RetainedMessage(Topic topic, MqttQoS qos, byte[] payload) {
        this.topic = topic;
        this.qos = qos;
        this.payload = payload;
    }

    public Topic getTopic() {
        return topic;
    }

    public MqttQoS qosLevel() {
        return qos;
    }

    public byte[] getPayload() {
        return payload;
    }
}
