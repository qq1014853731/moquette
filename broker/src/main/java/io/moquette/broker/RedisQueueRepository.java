package io.moquette.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.moquette.spring.support.RedisRepositorySupport;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 19:46
 */
public class RedisQueueRepository implements IQueueRepository {


    private static final Map<String, RedisQueue> CACHE = new HashMap<>();

    @Getter
    private final StringRedisTemplate redisTemplate;

    /**
     * key format: keyPrefix + clientId(ququeName)
     */
    @Getter
    private final String keyPrefix;

    @Getter
    private final ObjectMapper objectMapper;

    public RedisQueueRepository(RedisRepositorySupport redisRepositorySupport) {
        this.redisTemplate = redisRepositorySupport.getTemplate();
        this.keyPrefix = redisRepositorySupport.getKeyPrefix() + redisRepositorySupport.getQueuePrefix();
        this.objectMapper = redisRepositorySupport.getObjectMapper();
    }

    @Override
    public Set<String> listQueueNames() {
        return CACHE.keySet();
    }

    @Override
    public boolean containsQueue(String clientId) {
        return CACHE.containsKey(clientId);
    }


    @SneakyThrows
    @Override
    public SessionMessageQueue<SessionRegistry.EnqueuedMessage> getOrCreateQueue(String clientId) {
        return CACHE.computeIfAbsent(clientId, cid ->  new RedisQueue(redisTemplate, objectMapper, keyPrefix + cid) );
    }

    @Override
    public void close() {
        for (RedisQueue redisQueue : CACHE.values()) {
            redisQueue.closeAndPurge();
        }
        CACHE.clear();
    }
}
