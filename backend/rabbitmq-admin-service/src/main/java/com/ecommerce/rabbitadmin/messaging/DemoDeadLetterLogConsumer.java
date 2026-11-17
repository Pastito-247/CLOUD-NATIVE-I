package com.ecommerce.rabbitadmin.messaging;

import com.ecommerce.rabbitadmin.config.DemoTopologyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class DemoDeadLetterLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(DemoDeadLetterLogConsumer.class);

    @RabbitListener(queues = DemoTopologyConfig.ORDER_DLQ)
    public void onOrderDlq(Message message) {
        log("order", message);
    }

    @RabbitListener(queues = DemoTopologyConfig.PAYMENT_DLQ)
    public void onPaymentDlq(Message message) {
        log("payment", message);
    }

    @RabbitListener(queues = DemoTopologyConfig.RECEIPT_DLQ)
    public void onReceiptDlq(Message message) {
        log("receipt", message);
    }

    @RabbitListener(queues = DemoTopologyConfig.CATALOG_DLQ)
    public void onCatalogDlq(Message message) {
        log("catalog", message);
    }

    private void log(String domain, Message message) {
        log.error("[EFT-DLQ:{}] messageId={} routingKey={} retryCount={} xDeath={} payload={}",
                domain,
                message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getReceivedRoutingKey(),
                message.getMessageProperties().getHeaders().get("x-retry-count"),
                message.getMessageProperties().getHeaders().get("x-death"),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
