package com.sky.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.Arrays;

@Configuration
@EnableCaching // 开启缓存
public class CacheConfig {

    /**
     * L1 本地缓存（Spring自带，无任何依赖）
     */
    @Bean
    public ConcurrentMapCacheManager localCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        // 设置缓存名称（和你的@Cacheable(value="aiRecommend")对应）
        cacheManager.setCacheNames(Arrays.asList("aiRecommend"));
        return cacheManager;
    }

    /**
     * 组合：L1本地 + L2 Redis = 二级缓存
     */
    @Bean
    public CompositeCacheManager cacheManager(
            ConcurrentMapCacheManager localCacheManager,
            RedisCacheManager redisCacheManager
    ) {
        CompositeCacheManager composite = new CompositeCacheManager();
        // 顺序：先本地，再Redis
        composite.setCacheManagers(Arrays.asList(
                localCacheManager,
                redisCacheManager
        ));
        return composite;
    }
}