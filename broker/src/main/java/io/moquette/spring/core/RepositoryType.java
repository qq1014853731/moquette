package io.moquette.spring.core;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/28 18:32
 */
public enum RepositoryType {

    /**
     * 内存用作缓存仓库
     */
    MEMORY,

    /**
     * H2用作缓存仓库
     */
    H2,

    /**
     * redis用作缓存仓库
     */
    REDIS,

}
