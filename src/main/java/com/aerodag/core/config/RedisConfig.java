package com.aerodag.core.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

  @Bean
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new StringRedisSerializer());
    return template;
  }

  @Bean("blockingRedisConnectionFactory")
  public LettuceConnectionFactory blockingRedisConnectionFactory(
      @Value("${spring.data.redis.host:localhost}") String host,
      @Value("${spring.data.redis.port:6379}") int port,
      @Value("${spring.data.redis.password:}") String password) {

    RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration(host, port);
    if (password != null && !password.isBlank()) {
      serverConfig.setPassword(password);
    }

    LettuceClientConfiguration clientConfig =
        LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(10)).build();

    return new LettuceConnectionFactory(serverConfig, clientConfig);
  }

  @Bean("blockingRedisTemplate")
  public RedisTemplate<String, String> blockingRedisTemplate(
      @org.springframework.beans.factory.annotation.Qualifier("blockingRedisConnectionFactory")
          LettuceConnectionFactory factory) {

    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);
    template.setKeySerializer(new StringRedisSerializer());
    template.setValueSerializer(new StringRedisSerializer());
    return template;
  }
}
