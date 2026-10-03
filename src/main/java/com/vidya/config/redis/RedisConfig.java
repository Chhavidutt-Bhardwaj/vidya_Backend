package com.vidya.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for tenant registry caching and future use cases.
 *
 * <p>Phase 01 uses Redis for:
 * <ul>
 *   <li>Tenant registry cache — {@link com.vidya.tenant.registry.TenantRegistryServiceImpl}</li>
 * </ul>
 *
 * <p>Phase 02+ intended uses:
 * <ul>
 *   <li>OTP storage (time-limited)</li>
 *   <li>Rate limiting (sliding window)</li>
 *   <li>Distributed locks (Redisson)</li>
 *   <li>Session storage</li>
 *   <li>Subscription entitlement cache</li>
 * </ul>
 *
 * <p>Uses Lettuce as the Redis client (Spring Boot default; supports
 * reactive and connection pooling out of the box).
 */
@Configuration
public class RedisConfig {

    @Value("${vidya.redis.host:localhost}")
    private String host;

    @Value("${vidya.redis.port:6379}")
    private int port;

    @Value("${vidya.redis.password:}")
    private String password;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Keys as UTF-8 strings
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Values as JSON
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
