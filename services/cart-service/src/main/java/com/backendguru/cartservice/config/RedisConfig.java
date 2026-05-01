package com.backendguru.cartservice.config;

import com.backendguru.cartservice.cart.Cart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Profile("!test")
public class RedisConfig {

  @Bean
  public RedisTemplate<String, Cart> cartRedisTemplate(RedisConnectionFactory cf) {
    RedisTemplate<String, Cart> tpl = new RedisTemplate<>();
    tpl.setConnectionFactory(cf);
    tpl.setKeySerializer(new StringRedisSerializer());

    ObjectMapper om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    Jackson2JsonRedisSerializer<Cart> ser = new Jackson2JsonRedisSerializer<>(om, Cart.class);
    tpl.setValueSerializer(ser);

    tpl.afterPropertiesSet();
    return tpl;
  }
}
