package com.code.aigateway.infra.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类。
 * <p>
 * 统一定义字符串模板和对象模板，
 * 便于运行时配置缓存、版本号和脏标记等场景复用。
 * </p>
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * 字符串类型 RedisTemplate。
     * <p>
     * 适用于简单 KV、版本号、锁标记等纯字符串场景。
     * </p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return 字符串模板
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        log.info("[Redis 配置] 初始化 StringRedisTemplate 成功");
        return redisTemplate;
    }

    /**
     * 对象类型 RedisTemplate。
     * <p>
     * key 使用字符串序列化，value 使用 Jackson JSON 序列化，
     * 便于缓存对象结构数据。
     * </p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return 对象模板
     */
    @Bean
    public RedisTemplate<String, Object> objectRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> jsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        // Jackson2JsonRedisSerializer 需要显式注入 ObjectMapper，确保 Object 类型可以正确序列化。
        jsonRedisSerializer.setObjectMapper(createObjectMapper());

        redisTemplate.setConnectionFactory(connectionFactory);
        // key 始终采用可读的字符串格式，便于问题定位。
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);
        // value 使用 JSON 序列化，兼顾可读性与通用性。
        redisTemplate.setValueSerializer(jsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jsonRedisSerializer);
        redisTemplate.afterPropertiesSet();

        log.info("[Redis 配置] 初始化 RedisTemplate<String, Object> 成功");
        return redisTemplate;
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 启用类型信息，确保 Object 模板在反序列化时能够恢复具体类型。
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }
}
