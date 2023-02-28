package io.moquette.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.moquette.broker.ISubscriptionsRepository;
import io.moquette.broker.subscriptions.Subscription;
import io.moquette.spring.support.RedisRepositorySupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashSet;
import java.util.Set;

/**
 * 订阅缓存（Redis实现）
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 15:55
 */
@Slf4j
public class RedisSubscriptionRepository implements ISubscriptionsRepository {

    private final StringRedisTemplate redisTemplate;

    /**
     * key format: keyPrefix + clientId + ":" + topic
     */
    private final String keyPrefix;
    private final ObjectMapper objectMapper;

    public RedisSubscriptionRepository(RedisRepositorySupport redisRepositorySupport) {
        this.redisTemplate = redisRepositorySupport.getTemplate();
        // add subscription prefix
        this.keyPrefix = redisRepositorySupport.getKeyPrefix() + "subscription:";
        this.objectMapper = redisRepositorySupport.getObjectMapper();
    }

    @SneakyThrows
    @Override
    public Set<Subscription> listAllSubscriptions() {
        String keys = keyPrefix + ".*";
        Set<String> jsons = redisTemplate.keys(keys);
        Set<Subscription> subscriptions = new HashSet<>();
        if (jsons == null) {
            return subscriptions;
        }
        for (String json : jsons) {
            Subscription subscription = objectMapper.readValue(json, Subscription.class);
            subscriptions.add(subscription);
        }
        return subscriptions;
    }

    @SneakyThrows
    @Override
    public void addNewSubscription(Subscription subscription) {
        String key = keyPrefix + subscription.getClientId() + ":" + subscription.getTopicFilter().getTopic();
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(subscription));
    }

    @Override
    public void removeSubscription(String topic, String clientId) {
        String key = keyPrefix + clientId + ":" + topic;
        redisTemplate.delete(key);
    }
}
