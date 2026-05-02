package com.backendguru.notificationservice.config;

import com.backendguru.common.event.OrderConfirmedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
@EnableKafka
public class KafkaConfig {

  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id:notification-service}")
  private String groupId;

  @Bean
  ConsumerFactory<String, OrderConfirmedEvent> orderConfirmedConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    ObjectMapper om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    JsonDeserializer<OrderConfirmedEvent> jd = new JsonDeserializer<>(OrderConfirmedEvent.class, om, false);
    jd.addTrustedPackages("com.backendguru.common.event");
    jd.setUseTypeMapperForKey(false);

    return new DefaultKafkaConsumerFactory<>(
        props,
        new StringDeserializer(),
        new ErrorHandlingDeserializer<>(jd));
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEvent>
      orderConfirmedKafkaListenerContainerFactory(
          ConsumerFactory<String, OrderConfirmedEvent> orderConfirmedConsumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, OrderConfirmedEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(orderConfirmedConsumerFactory);
    factory.setConcurrency(1);
    factory.setAutoStartup(false);
    return factory;
  }
}
