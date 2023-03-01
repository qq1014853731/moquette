package io.moquette.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/3/1 09:37
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RedisQueue extends AbstractSessionMessageQueue<SessionRegistry.EnqueuedMessage> {

    private String queueName;

    private final String key;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public RedisQueue(RedisQueueRepository redisQueueRepository, String queueName) {
        this.queueName = queueName;
        this.objectMapper = redisQueueRepository.getObjectMapper();
        this.redisTemplate = redisQueueRepository.getRedisTemplate();
        this.key = redisQueueRepository.getKeyPrefix() + queueName;
    }

    @SneakyThrows
    @Override
    public void enqueue(SessionRegistry.EnqueuedMessage message) {
        // push item to queue
        redisTemplate.boundListOps(key).rightPush(objectMapper.writeValueAsString(message));
    }

    @SneakyThrows
    @Override
    public SessionRegistry.EnqueuedMessage dequeue() {
        // pop one queue item
        String json = redisTemplate.boundListOps(key).rightPop();
        return objectMapper.readValue(json, SessionRegistry.PublishedMessage.class);
    }

    @Override
    public boolean isEmpty() {
        Long size = redisTemplate.boundListOps(key).size();
        return size == null || size == 0;
    }

    @Override
    public void closeAndPurge() {
        // TODO all key
        redisTemplate.delete(key);
    }
}
