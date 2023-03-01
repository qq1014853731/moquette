package io.moquette.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.moquette.broker.ISubscriptionsRepository;
import io.moquette.broker.subscriptions.Subscription;
import io.moquette.spring.support.RedisRepositorySupport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
     * key format: keyPrefix + clientId
     */
    private final String keyPrefix;
    private final ObjectMapper objectMapper;

    public RedisSubscriptionRepository(RedisRepositorySupport redisRepositorySupport) {
        this.redisTemplate = redisRepositorySupport.getTemplate();
        // add subscription prefix
        this.keyPrefix = redisRepositorySupport.getKeyPrefix() + redisRepositorySupport.getSubscriptionPrefix();
        this.objectMapper = redisRepositorySupport.getObjectMapper();
    }

    @SneakyThrows
    @Override
    public Set<Subscription> listAllSubscriptions() {
        String keyPattern = keyPrefix + ".*";
        Set<String> keys = redisTemplate.keys(keyPattern);
        Set<Subscription> subscriptions = new HashSet<>();
        if (keys == null) {
            return subscriptions;
        }
        for (String key : keys) {
            Set<Object> topics = redisTemplate.boundHashOps(key).keys();
            if (topics == null || topics.isEmpty()){
                continue;
            }
            List<Object> jsons = redisTemplate.boundHashOps(key).multiGet(topics);
            if (jsons == null || jsons.isEmpty()) {
                continue;
            }
            for (Object json : jsons) {
                Subscription subscription = objectMapper.readValue(Objects.toString(json), Subscription.class);
                subscriptions.add(subscription);
            }
        }
        return subscriptions;
    }

    @SneakyThrows
    @Override
    public void addNewSubscription(Subscription subscription) {
        String clientId = subscription.getClientId();
        String topic = subscription.getTopicFilter().getTopic();
        String key = keyPrefix + clientId;
        redisTemplate.boundHashOps(key).put(topic, objectMapper.writeValueAsString(subscription));
    }

    @Override
    public void removeSubscription(String topic, String clientId) {
        String key = keyPrefix + clientId;
        redisTemplate.boundHashOps(key).delete(topic);
    }
}
