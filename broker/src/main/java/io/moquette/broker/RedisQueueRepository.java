package io.moquette.broker;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.moquette.spring.support.RedisRepositorySupport;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 19:46
 */
public class RedisQueueRepository implements IQueueRepository {

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
        Set<String> keys = redisTemplate.keys(keyPrefix + ".*");
        return keys == null ? new HashSet<>() : keys.stream().map(key -> StrUtil.removePrefix(key, keyPrefix)).collect(Collectors.toSet());
    }

    @Override
    public boolean containsQueue(String clientId) {
        Boolean hasKey = redisTemplate.hasKey(keyPrefix + clientId);
        return hasKey == null || hasKey;
    }

    @SneakyThrows
    @Override
    public SessionMessageQueue<SessionRegistry.EnqueuedMessage> getOrCreateQueue(String clientId) {

    }

    @Override
    public void close() {
    }
}
