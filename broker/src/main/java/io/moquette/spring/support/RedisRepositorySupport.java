package io.moquette.spring.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 18:41
 */
public class RedisRepositorySupport implements RepositorySupport {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * redis-key prefix
     * default: "moquette:"
     */
    @Getter
    @Setter
    @NonNull
    private String keyPrefix = "moquette:";

    /**
     * redis subscription value key prefix
     * redis final key prefix: keyPrefix + subscriptionPrefix
     */
    @Getter
    @Setter
    @NonNull
    private String subscriptionPrefix = "subscription:";

    /**
     * redis retained set key prefix
     * suffix: topic
     * redis final key prefix: keyPrefix + retainedPrefix
     */
    @Getter
    @Setter
    @NonNull
    private String retainedPrefix = "retained:";

    /**
     * redis queue hash key prefix
     * redis final key prefix: keyPrefix + queuePrefix
     */
    @Getter
    @Setter
    @NonNull
    private String queuePrefix = "queue:";

    /**
     * provide a serialization method  for redis value
     */
    @Setter
    private ObjectMapper objectMapper = null;

    public RedisRepositorySupport(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public RedisRepositorySupport(RedisTemplate<?, ?> redisTemplate) {
        this(redisTemplate.getConnectionFactory());
    }

    public RedisRepositorySupport(RedisConnectionFactory redisConnectionFactory) {
        this(new StringRedisTemplate(redisConnectionFactory));
    }

    public ObjectMapper getObjectMapper() {
        if (this.objectMapper == null) {
            this.objectMapper = new ObjectMapper();
        }
        return this.objectMapper;
    }

    public StringRedisTemplate getTemplate() {
        return this.stringRedisTemplate;
    }
}
