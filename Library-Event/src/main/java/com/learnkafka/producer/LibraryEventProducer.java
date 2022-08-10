package com.learnkafka.producer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.domain.LibraryEvent;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public record LibraryEventProducer(
        KafkaTemplate<UUID, String> kafkaTemplate,
        ObjectMapper objectMapper) {

    public void sendLibraryEvent(LibraryEvent libraryEvent) throws JsonProcessingException {
        UUID key = UUID.randomUUID();
        String value = objectMapper.writeValueAsString(libraryEvent);
        ListenableFuture<SendResult<UUID, String>> listenableFuture = kafkaTemplate.sendDefault(key, value);
        listenableCallBack(key, value, listenableFuture);
    }

    public void sendLibraryEvent_Approach2(LibraryEvent libraryEvent) throws JsonProcessingException {
        UUID key = libraryEvent.getLibraryEventId();

        String value = objectMapper.writeValueAsString(libraryEvent);

        ListenableFuture<SendResult<UUID, String>> listenableFuture = kafkaTemplate.send(new ProducerRecord<UUID, String>("library-events", key, value));
        listenableCallBack(key, value, listenableFuture);

    }

    public SendResult<UUID, String> sendLibraryEventSynchronous(LibraryEvent libraryEvent) throws Exception {
        UUID key = UUID.randomUUID();
        String value = objectMapper.writeValueAsString(libraryEvent);
        SendResult<UUID, String> sendResult = null;

        try {
            sendResult = kafkaTemplate.sendDefault(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("InterruptedException/ExecutionException Sending the message and the exception is {}",
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Exception Sending the message and the exception is {}", e.getMessage());
            throw e;
        }

        return sendResult;
    }

    private void listenableCallBack(UUID key, String value, ListenableFuture<SendResult<UUID, String>> listenableFuture) {
        listenableFuture.addCallback(new ListenableFutureCallback<SendResult<UUID, String>>() {
            @Override
            public void onFailure(Throwable ex) {
                handleFailure(key, value, ex);
            }

            @Override
            public void onSuccess(SendResult<UUID, String> result) {
                handleSuccess(key, value, result);
            }
        });
    }

    private ProducerRecord<UUID, String> buildProducerRecord(UUID key, String value, String topic) {
        List<Header> recordHeaders = List.of(new RecordHeader("event-source", "scanner".getBytes(StandardCharsets.UTF_8)));
        return new ProducerRecord<UUID, String>(topic, key, value);
    }

    private void handleFailure(UUID key, String value, Throwable ex) {
        log.error("Error Sending the Message and the exception is {}", ex.getMessage());
        try {
            throw ex;
        } catch (Throwable throwable) {
            log.error("Error is OnFailure: {}", throwable.getMessage());
        }
    }

    private void handleSuccess(UUID key, String value, SendResult<UUID, String> result) {
        log.info("Message Sent SuccessFully for the key: {} and the value is {} partition is {}", key, value,
                result.getRecordMetadata().partition());
    }
}