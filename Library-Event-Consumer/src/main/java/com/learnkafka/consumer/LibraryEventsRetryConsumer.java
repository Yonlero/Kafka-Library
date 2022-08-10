package com.learnkafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.learnkafka.service.LibraryEventsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class LibraryEventsRetryConsumer {
    private final LibraryEventsService libraryEventsService;

    public LibraryEventsRetryConsumer(LibraryEventsService libraryEventsService) {
        this.libraryEventsService = libraryEventsService;
    }

    @KafkaListener(topics = {"${topics.retry}"}, groupId = "retry-listener-group")
    public void onMessage(ConsumerRecord<UUID, String> consumerRecord) throws JsonProcessingException {
        log.info("ConsumerRecord in Retry Consumer: {}", consumerRecord);
        consumerRecord.headers().forEach(header -> log.info("Key : {} , values : {}", header.key(), new String(header.value())));
        libraryEventsService.processLibraryEvent(consumerRecord);
    }
}