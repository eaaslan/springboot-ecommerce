package com.backendguru.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  @Value("${app.rabbit.exchange}")
  private String exchangeName;

  @Value("${app.rabbit.dlx}")
  private String dlxName;

  @Value("${app.rabbit.queue}")
  private String queueName;

  @Value("${app.rabbit.dlq}")
  private String dlqName;

  @Value("${app.rabbit.routing-key}")
  private String routingKey;

  @Bean
  TopicExchange orderEventsExchange() {
    return new TopicExchange(exchangeName, true, false);
  }

  @Bean
  TopicExchange orderEventsDlx() {
    return new TopicExchange(dlxName, true, false);
  }

  @Bean
  Queue notificationQueue() {
    return QueueBuilder.durable(queueName)
        .withArguments(
            Map.of(
                "x-dead-letter-exchange", dlxName,
                "x-dead-letter-routing-key", routingKey))
        .build();
  }

  @Bean
  Queue notificationDlq() {
    return QueueBuilder.durable(dlqName).build();
  }

  @Bean
  Binding notificationBinding(Queue notificationQueue, TopicExchange orderEventsExchange) {
    return BindingBuilder.bind(notificationQueue).to(orderEventsExchange).with(routingKey);
  }

  @Bean
  Binding notificationDlqBinding(Queue notificationDlq, TopicExchange orderEventsDlx) {
    return BindingBuilder.bind(notificationDlq).to(orderEventsDlx).with(routingKey);
  }

  @Bean
  MessageConverter jsonMessageConverter() {
    ObjectMapper om = new ObjectMapper();
    om.registerModule(new JavaTimeModule());
    om.disable(
        com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return new Jackson2JsonMessageConverter(om);
  }

  @Bean
  RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter conv) {
    RabbitTemplate t = new RabbitTemplate(cf);
    t.setMessageConverter(conv);
    return t;
  }
}
