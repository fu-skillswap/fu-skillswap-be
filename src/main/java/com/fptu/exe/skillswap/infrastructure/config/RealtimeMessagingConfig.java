package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeDlqErrorLogger;
import com.fptu.exe.skillswap.infrastructure.realtime.RealtimeDlqRejectingRecoverer;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(RealtimeOutboxProperties.class)
public class RealtimeMessagingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "application.realtime.outbox", name = "enabled", havingValue = "true")
    TopicExchange realtimeOutboxExchange(RealtimeOutboxProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.realtime.outbox", name = "enabled", havingValue = "true")
    TopicExchange realtimeDeadLetterExchange(RealtimeOutboxProperties properties) {
        return new TopicExchange(properties.getDeadLetterExchange(), true, false);
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.realtime.outbox", name = "enabled", havingValue = "true")
    Declarables realtimeQueuesAndBindings(RealtimeOutboxProperties properties) {
        Queue chatQueue = new Queue(properties.getChatQueue(), true, false, false, Map.of(
                "x-single-active-consumer", true,
                "x-dead-letter-exchange", properties.getDeadLetterExchange(),
                "x-dead-letter-routing-key", properties.getChatDlqRoutingKey()
        ));
        Queue notificationQueue = new Queue(properties.getNotificationQueue(), true, false, false, Map.of(
                "x-dead-letter-exchange", properties.getDeadLetterExchange(),
                "x-dead-letter-routing-key", properties.getNotificationDlqRoutingKey()
        ));
        Queue chatDlqQueue = new Queue(properties.getChatDlqQueue(), true);
        Queue notificationDlqQueue = new Queue(properties.getNotificationDlqQueue(), true);
        TopicExchange mainExchange = realtimeOutboxExchange(properties);
        TopicExchange dlx = realtimeDeadLetterExchange(properties);
        return new Declarables(
                chatQueue,
                notificationQueue,
                chatDlqQueue,
                notificationDlqQueue,
                mainExchange,
                dlx,
                BindingBuilder.bind(chatQueue).to(mainExchange).with(properties.getChatRoutingPattern()),
                BindingBuilder.bind(notificationQueue).to(mainExchange).with(properties.getNotificationRoutingPattern()),
                BindingBuilder.bind(chatDlqQueue).to(dlx).with(properties.getChatDlqRoutingKey()),
                BindingBuilder.bind(notificationDlqQueue).to(dlx).with(properties.getNotificationDlqRoutingKey())
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.realtime.outbox", name = "enabled", havingValue = "true")
    SimpleRabbitListenerContainerFactory chatRealtimeListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RealtimeOutboxProperties properties,
            RealtimeDlqErrorLogger errorLogger
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1_000L, 2.0, 5_000L)
                .recoverer(new RealtimeDlqRejectingRecoverer(errorLogger, properties.getChatDlqQueue()))
                .build());
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "application.realtime.outbox", name = "enabled", havingValue = "true")
    SimpleRabbitListenerContainerFactory notificationRealtimeListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            RealtimeOutboxProperties properties,
            RealtimeDlqErrorLogger errorLogger
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1_000L, 2.0, 5_000L)
                .recoverer(new RealtimeDlqRejectingRecoverer(errorLogger, properties.getNotificationDlqQueue()))
                .build());
        return factory;
    }
}
