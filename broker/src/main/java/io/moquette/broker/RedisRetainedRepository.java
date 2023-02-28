package io.moquette.broker;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.moquette.broker.subscriptions.Topic;
import io.moquette.spring.support.RedisRepositorySupport;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 19:47
 */
public class RedisRetainedRepository implements IRetainedRepository {
    private final StringRedisTemplate redisTemplate;

    /**
     * key format: keyPrefix + clientId + ":" + topic
     */
    private final String keyPrefix;
    private final ObjectMapper objectMapper;

    public RedisRetainedRepository(RedisRepositorySupport redisRepositorySupport) {
        this.redisTemplate = redisRepositorySupport.getTemplate();
        // add retained prefix
        this.keyPrefix = redisRepositorySupport.getKeyPrefix() + "retained:";
        this.objectMapper = redisRepositorySupport.getObjectMapper();
    }

    @Override
    public void cleanRetained(Topic topic) {
        String key = keyPrefix + topic.getTopic();
        redisTemplate.delete(key);
    }

    @SneakyThrows
    @Override
    public void retain(Topic topic, MqttPublishMessage msg) {
        String key = keyPrefix + topic.getTopic();
        final ByteBuf payload = msg.content();
        byte[] rawPayload = new byte[payload.readableBytes()];
        payload.getBytes(0, rawPayload);
        final RetainedMessage toStore = new RetainedMessage(topic, msg.fixedHeader().qosLevel(), rawPayload);
        redisTemplate.opsForSet().add(key, objectMapper.writeValueAsString(toStore));
    }

    @Override
    public boolean isEmpty() {
        Set<String> keys = redisTemplate.keys(keyPrefix + ".*");
        return CollUtil.isEmpty(keys);
    }

    @SneakyThrows
    @Override
    public List<RetainedMessage> retainedOnTopic(String topic) {
        String key = keyPrefix + topic;
        Set<String> jsons = redisTemplate.opsForSet().members(key);
        List<RetainedMessage> retainedMessages = new ArrayList<>();
        if (jsons == null) {
            return retainedMessages;
        }
        for (String json : jsons) {
            RetainedMessage retainedMessage = objectMapper.readValue(json, RetainedMessage.class);
            retainedMessages.add(retainedMessage);
        }
        return retainedMessages;
    }

    public static void main(String[] args) {
    }
}
