package io.moquette.broker;

import io.moquette.spring.support.RedisRepositorySupport;

import java.util.Set;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 19:46
 */
public class RedisQueueRepository implements IQueueRepository{

    public RedisQueueRepository(RedisRepositorySupport redisRepositorySupport) {

    }

    @Override
    public Set<String> listQueueNames() {
        return null;
    }

    @Override
    public boolean containsQueue(String clientId) {
        return false;
    }

    @Override
    public SessionMessageQueue<SessionRegistry.EnqueuedMessage> getOrCreateQueue(String clientId) {
        return null;
    }

    @Override
    public void close() {

    }
}
