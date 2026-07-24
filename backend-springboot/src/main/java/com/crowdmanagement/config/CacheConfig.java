package com.crowdmanagement.config;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis-backed caching for read-heavy, rarely-changing lookups (locations,
 * counters). Queue/live data is intentionally NOT cached here since it needs
 * to stay real-time (it goes over the /ws WebSocket + REST polling instead).
 *
 * Cache names and TTLs:
 *   - "locations": list of all locations             -> app.cache.locations-ttl-seconds
 *   - "counters":  counters per location              -> app.cache.counters-ttl-seconds
 *
 * Local dev without Docker/Redis: set CACHE_TYPE=simple (in-memory,
 * no eviction by TTL) via an environment variable or
 * -Dspring.cache.type=simple to skip Redis-backed caching. The
 * RedisConnectionFactory bean below is still created either way (Lettuce
 * connects lazily, so this alone won't fail startup even if no Redis server
 * is reachable) - only the cacheManager bean is conditional on
 * spring.cache.type=redis.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Explicit connection factory instead of relying on Spring Boot's
     * spring.data.redis.url autoconfiguration property: when REDIS_URL is
     * unset (plain local `mvn spring-boot:run`, no Docker), Spring Boot's
     * own url-based autoconfiguration throws "Invalid Redis URL ''" if that
     * property is present but blank. Parsing it ourselves lets us cleanly
     * fall back to spring.data.redis.host/port instead.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory(
        @Value("${REDIS_URL:}") String redisUrl,
        @Value("${spring.data.redis.host:localhost}") String host,
        @Value("${spring.data.redis.port:6379}") int port
    ) {
        if (redisUrl != null && !redisUrl.isBlank()) {
            URI uri = URI.create(redisUrl);
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());
            if (uri.getUserInfo() != null) {
                String[] userInfoParts = uri.getUserInfo().split(":", 2);
                String password = userInfoParts.length == 2 ? userInfoParts[1] : userInfoParts[0];
                if (!password.isBlank()) {
                    config.setPassword(password);
                }
            }
            return new LettuceConnectionFactory(config);
        }
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = false)
    public RedisCacheManager cacheManager(
        RedisConnectionFactory connectionFactory,
        @Value("${app.cache.locations-ttl-seconds:60}") long locationsTtlSeconds,
        @Value("${app.cache.counters-ttl-seconds:30}") long countersTtlSeconds
    ) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(60))
            .disableCachingNullValues()
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            );

        Map<String, RedisCacheConfiguration> perCacheConfig = new HashMap<>();
        perCacheConfig.put("locations", defaultConfig.entryTtl(Duration.ofSeconds(locationsTtlSeconds)));
        perCacheConfig.put("counters", defaultConfig.entryTtl(Duration.ofSeconds(countersTtlSeconds)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(perCacheConfig)
            .build();
    }
}
