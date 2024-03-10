package com.neu.bigdata.service;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.CompletableFuture;

/**
 * @author Ruolin Li
 * @Date 2023-11-25
 */
@Service
public class KafkaPub {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.topic.name}")
    private String topic;

    public CompletableFuture<SendResult<String, String>> publish(String operation, String message) {

        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, operation, message);

        CompletableFuture<SendResult<String, String>> completableFuture = kafkaTemplate.send(producerRecord);

        // Using whenComplete to handle both success and failure
        completableFuture.whenComplete((result, exception) -> {
            if (result != null) {
                // Handle success
                logger.info("Sent message=[" + message +
                            "] with offset=[" + result.getRecordMetadata().offset() + "]");
                    completableFuture.complete(result);
            } else {
                // Handle failure
                logger.error("Unable to send message=["
                        + message + "] due to : " + exception.getMessage());
            }
        });

        return completableFuture;
    }
}
