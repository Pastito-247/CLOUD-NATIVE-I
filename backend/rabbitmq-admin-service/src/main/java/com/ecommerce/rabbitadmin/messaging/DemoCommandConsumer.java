package com.ecommerce.rabbitadmin.messaging;

import com.ecommerce.rabbitadmin.config.DemoTopologyConfig;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class DemoCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(DemoCommandConsumer.class);
    private static final int MAX_RETRIES = 3;

    private final RabbitTemplate rabbitTemplate;

    public DemoCommandConsumer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = DemoTopologyConfig.ORDER_QUEUE, ackMode = "MANUAL")
    public void onOrder(Message message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(DemoTopologyConfig.ORDER_QUEUE, message, channel, tag);
    }

    @RabbitListener(queues = DemoTopologyConfig.PAYMENT_QUEUE, ackMode = "MANUAL")
    public void onPayment(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(DemoTopologyConfig.PAYMENT_QUEUE, message, channel, tag);
    }

    @RabbitListener(queues = DemoTopologyConfig.RECEIPT_QUEUE, ackMode = "MANUAL")
    public void onReceipt(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(DemoTopologyConfig.RECEIPT_QUEUE, message, channel, tag);
    }

    @RabbitListener(queues = DemoTopologyConfig.CATALOG_QUEUE, ackMode = "MANUAL")
    public void onCatalog(Message message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(DemoTopologyConfig.CATALOG_QUEUE, message, channel, tag);
    }

    private void handle(String queue, Message message, Channel channel, long tag) throws IOException {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            if (shouldFail(message, payload)) {
                throw new IllegalStateException("Fallo simulado EFT");
            }
            log.info("[EFT-COMMAND] queue={} routingKey={} payload={}",
                    queue,
                    message.getMessageProperties().getReceivedRoutingKey(),
                    payload);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            int retry = retryCount(message);
            if (retry < MAX_RETRIES) {
                Message retryMessage = MessageBuilder.fromMessage(message)
                        .setHeader("x-retry-count", retry + 1)
                        .build();
                rabbitTemplate.send("", queue, retryMessage);
                channel.basicAck(tag, false);
                log.warn("[EFT-RETRY] queue={} intento={}/{} payload={}",
                        queue, retry + 1, MAX_RETRIES, payload);
            } else {
                log.error("[EFT-DLQ] reintentos agotados en {}; enviando a DLX tuki.dlx", queue, ex);
                channel.basicNack(tag, false, false);
            }
        }
    }

    private boolean shouldFail(Message message, String payload) {
        Object flag = message.getMessageProperties().getHeaders().get("x-demo-fail");
        return Boolean.TRUE.equals(flag) || payload.toLowerCase().contains("fail");
    }

    private int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get("x-retry-count");
        return value instanceof Number number ? number.intValue() : 0;
    }
}
